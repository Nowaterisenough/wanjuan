package io.wanjuan.app

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bumptech.glide.Priority
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import io.wanjuan.app.constant.AppPattern
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.SearchBook
import io.wanjuan.app.help.book.isAudio
import io.wanjuan.app.help.book.isImage
import io.wanjuan.app.help.book.isVideo
import io.wanjuan.app.help.book.isWebFile
import io.wanjuan.app.help.glide.OkHttpModelLoader
import io.wanjuan.app.help.glide.OkHttpStreamFetcher
import io.wanjuan.app.help.source.exploreKinds
import io.wanjuan.app.model.CheckSource
import io.wanjuan.app.model.ReadManga
import io.wanjuan.app.model.webBook.WebBook
import io.wanjuan.app.ui.about.AboutActivity
import io.wanjuan.app.ui.main.explore.DiscoverUrlRule
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.NetworkUtils
import io.wanjuan.app.utils.SvgUtils
import io.wanjuan.app.utils.fromJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in network audit. Reports contain measurements and failures, never chapter text or images. */
@RunWith(AndroidJUnit4::class)
class TestAllSourcesAuditInstrumented {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val output get() = File(instrumentation.targetContext.getExternalFilesDir(null), "source-audit").apply { mkdirs() }
    private val report = linkedMapOf<String, Any?>()
    private var reportFile: File? = null

    private fun save() { reportFile?.writeText(GSON.toJson(report)) }

    private fun stage(target: MutableMap<String, Any?>, value: String) {
        target["stage"] = value
        report["activeStage"] = value
        save()
    }

    // A host watchdog can kill a stalled script; persist its original source before insertion.
    private fun restore() {
        val key = File(output, "restore-key.txt")
        if (!key.exists()) return
        val original = File(output, "restore-source.json")
        if (original.exists()) {
            appDb.bookSourceDao.insert(GSON.fromJson(original.readText(), BookSource::class.java))
        } else {
            appDb.bookSourceDao.delete(key.readText())
        }
        key.delete()
        original.delete()
    }

    @Test
    fun restoreAuditSource() { restore() }

    @Test
    fun auditOneSource() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val index = args.getString("sourceIndex")?.toIntOrNull()
        assumeTrue("Live audit requires sourceIndex", index != null)
        checkNotNull(index)
        restore()
        val samples = instrumentation.context.assets.open("shareBookSource.json").use {
            GSON.fromJsonArray<BookSource>(it).getOrThrow()
        }
        val defaults = instrumentation.targetContext.assets.open("defaultData/bookSources.json").use {
            GSON.fromJsonArray<BookSource>(it).getOrThrow()
        }
        val sources = (samples + defaults).distinctBy { it.bookSourceUrl }
        val source = args.getString("sourceFile")?.let {
            GSON.fromJson(File(it).readText(), BookSource::class.java)
        } ?: sources[index]
        reportFile = File(output, "%03d.json".format(index))
        report.putAll(mapOf("index" to index, "source" to source.bookSourceName,
            "origin" to if (index < samples.size) "sample" else "bundled",
            "declaredType" to source.bookSourceType, "startedAt" to System.currentTimeMillis(),
            "status" to "running"))
        args.getString("auditProxy")?.let { expected ->
            val selected = io.wanjuan.app.help.http.okHttpClient.proxySelector
                .select(URI("https://example.com"))
            report["proxyRoutes"] = selected.map { it.toString() }
            save()
            check(selected.any {
                val address = it.address() as? InetSocketAddress
                it.type() == Proxy.Type.HTTP && address != null &&
                    "${address.hostString}:${address.port}" == expected
            }) { "Audit HTTP client does not use the requested proxy" }
        }
        save()
        val original = appDb.bookSourceDao.getBookSource(source.bookSourceUrl)
        File(output, "restore-source.json").delete()
        if (original != null) File(output, "restore-source.json").writeText(GSON.toJson(original))
        File(output, "restore-key.txt").writeText(source.bookSourceUrl)
        appDb.bookSourceDao.insert(source)
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        val previousBook = ReadManga.book
        try {
            withTimeout(args.getString("sourceTimeout")?.toLongOrNull() ?: 100_000L) {
                val attempts = arrayListOf<MutableMap<String, Any?>>()
                report["attempts"] = attempts
                val routes = arrayListOf<Pair<String, suspend () -> List<SearchBook>>>()
                val keyword = args.getString("keyword") ?: source.getCheckKeyword(CheckSource.keyword)
                report["searchKeyword"] = keyword
                if (!source.searchUrl.isNullOrBlank()) routes.add("search" to {
                    WebBook.searchBookAwait(source, keyword)
                })
                val categoryReport = linkedMapOf<String, Any?>()
                report["discovery"] = categoryReport
                if (args.getString("searchOnly") != "true" && !source.exploreUrl.isNullOrBlank()) {
                    stage(categoryReport, "categories")
                    try {
                        val kinds = withTimeout(25_000L) { source.exploreKinds() }
                        categoryReport["count"] = kinds.size
                        val pattern = args.getString("entryPattern")?.toRegex()
                        val entries = kinds.filter {
                            !it.url.isNullOrBlank() && !it.title.startsWith("ERROR:") &&
                                DiscoverUrlRule.script(it.url) == null &&
                                (pattern == null || pattern.containsMatchIn(it.url))
                        }.distinctBy { it.url }.take(2)
                        if (entries.isEmpty()) categoryReport["error"] =
                            kinds.firstOrNull { it.title.startsWith("ERROR:") }?.title ?: "No direct discovery entry"
                        entries.reversed().forEachIndexed { entryIndex, entry ->
                            routes.add(0, "discovery-${entries.size - entryIndex}" to {
                                WebBook.exploreBookAwait(source, DiscoverUrlRule.requestRule(entry.url!!), 1)
                            })
                        }
                    } catch (error: Exception) { categoryReport["error"] = error.stackTraceToString() }
                }
                check(routes.isNotEmpty()) { "No search or usable discovery rule" }
                for ((route, load) in routes) {
                    val attempt = linkedMapOf<String, Any?>("route" to route)
                    attempts.add(attempt)
                    try {
                        withTimeout(40_000L) {
                            stage(attempt, "list")
                            val books = withTimeout(20_000L) { load() }
                            attempt["books"] = books.size
                            check(books.isNotEmpty()) { "Empty book list" }
                            val results = arrayListOf<MutableMap<String, Any?>>()
                            attempt["samples"] = results
                            for (candidate in books.take(2)) {
                                val result = linkedMapOf<String, Any?>()
                                results.add(result)
                                try {
                                    withTimeout(28_000L) { inspectBook(source, candidate.toBook(), result) }
                                } catch (error: Exception) { result["error"] = error.stackTraceToString() }
                                save()
                                if (result["status"] == "readable") break
                            }
                        }
                    } catch (error: Exception) { attempt["error"] = error.stackTraceToString() }
                    save()
                    val results = attempts.flatMap { (it["samples"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty() }
                    if (results.any { it["status"] == "readable" }) {
                        report["status"] = "readable"
                        break
                    }
                }
                if (report["status"] != "readable") report["status"] = "unverified"
            }
        } catch (error: Exception) {
            report["error"] = error.stackTraceToString()
            report["status"] = "unverified"
        } finally {
            ReadManga.book = previousBook
            restore()
            report["finishedAt"] = System.currentTimeMillis()
            save()
            scenario.close()
        }
    }

    private suspend fun inspectBook(source: BookSource, book: Book, result: MutableMap<String, Any?>) {
        // Only keep URLs to aid rule repair; do not copy titles or downloaded prose into the report.
        result["bookUrl"] = book.bookUrl
        stage(result, "info")
        withTimeout(15_000L) { WebBook.getBookInfoAwait(source, book) }
        result["resolvedType"] = book.type
        if (book.isWebFile) {
            result["status"] = "download_link_only"
            return
        }
        stage(result, "chapters")
        val chapters = withTimeout(15_000L) { WebBook.getChapterListAwait(source, book).getOrThrow() }
            .filterNot { it.isVolume && it.url.startsWith(it.title) }
        result["chapters"] = chapters.size
        result["resolvedType"] = book.type
        check(chapters.isNotEmpty()) { "Empty chapter list" }
        val samples = arrayListOf<MutableMap<String, Any?>>()
        result["chapterSamples"] = samples
        for (chapter in chapters.take(2)) {
            val sample = linkedMapOf<String, Any?>("chapterIndex" to chapter.index, "chapterUrl" to chapter.url)
            samples.add(sample)
            try {
                stage(sample, "content")
                val content = withTimeout(18_000L) {
                    WebBook.getContentAwait(source, book, chapter,
                        chapters.getOrNull(chapters.indexOf(chapter) + 1)?.url, needSave = false)
                }
                sample["contentLength"] = content.length
                result["resolvedType"] = book.type
                check(content.isNotBlank()) { "Empty content" }
                val matcher = AppPattern.imgPattern.matcher(content)
                val images = arrayListOf<String>()
                while (matcher.find()) images.add(matcher.group(1).orEmpty())
                val text = Jsoup.parse(content).text().trim()
                sample["textLength"] = text.length
                if (book.isImage || (images.any { !it.startsWith("data:") } && text.length < 80)) {
                    check(images.isNotEmpty()) { "Image content has no image URLs" }
                    sample["kind"] = "image"
                    sample["imageCount"] = images.size
                    val imageSamples = arrayListOf<Map<String, Any?>>()
                    sample["images"] = imageSamples
                    ReadManga.book = book
                    for (url in listOf(images.first(), images[images.size / 2]).distinct()) {
                        stage(sample, "image")
                        imageSamples.add(inspectImage(source, NetworkUtils.getAbsoluteURL(chapter.url, url)))
                        save()
                    }
                    check(imageSamples.any { it["substantial"] == true }) {
                        "Images are only icons or thumbnails"
                    }
                } else if (book.isAudio || book.isVideo || Regex("magnet:|ed2k:|thunder:").containsMatchIn(text) ||
                    text.lines().filter { it.isNotBlank() }.all {
                        Regex("^(?:https?://|magnet:|ed2k:|thunder:)\\S+$").matches(it.trim())
                    }) {
                    sample["kind"] = if (book.isAudio) "audio" else "media_or_link"
                    check(Regex("https?://|magnet:|ed2k:|thunder:").containsMatchIn(content)) { "No media URL in content" }
                    sample["status"] = "media_link_only"
                    continue
                } else {
                    sample["kind"] = "text"
                    check(text.length >= 80) { "Content is too short to verify (${text.length} characters)" }
                    check(!Regex("(?i)<html|<title|<form|<!doctype").containsMatchIn(content)) { "Content contains a raw HTML page" }
                    check(text.length > 600 || !Regex("验证码|登录后|付费阅读|购买本章|暂无内容|内容加载失败|访问受限|未获取到视频|access denied|just a moment", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                        "Content appears to be an access/error placeholder"
                    }
                }
                sample["status"] = "readable"
                stage(sample, "complete")
            } catch (error: Exception) { sample["error"] = error.stackTraceToString() }
            save()
        }
        result["status"] = if (samples.isNotEmpty() && samples.all { it["status"] == "readable" }) "readable"
            else if (samples.any { it["status"] == "readable" }) "partial" else "unverified"
        stage(result, "complete")
    }

    private suspend fun inspectImage(source: BookSource, url: String): Map<String, Any?> = runInterruptible(Dispatchers.IO) {
        if (url.startsWith("data:image")) {
            val metadata = url.substringBefore(',')
            val payload = url.substringAfter(',')
            val bytes = if (metadata.endsWith(";base64")) Base64.decode(payload, Base64.DEFAULT)
                else android.net.Uri.decode(payload).toByteArray()
            return@runInterruptible measureImage(bytes)
        }
        val latch = CountDownLatch(1)
        var failure: Exception? = null
        var measurement: Map<String, Any?>? = null
        val fetcher = OkHttpStreamFetcher(GlideUrl(url), Options()
            .set(OkHttpModelLoader.sourceOriginOption, source.bookSourceUrl)
            .set(OkHttpModelLoader.mangaOption, true))
        try {
            fetcher.loadData(Priority.NORMAL, object : DataFetcher.DataCallback<InputStream> {
                override fun onDataReady(data: InputStream?) {
                    try {
                        val bytes = checkNotNull(data) { "Image decryption returned no data" }.readBytes()
                        measurement = measureImage(bytes)
                    } catch (error: Exception) { failure = error }
                    finally { latch.countDown() }
                }
                override fun onLoadFailed(e: Exception) { failure = e; latch.countDown() }
            })
            check(latch.await(20, TimeUnit.SECONDS)) { "Image request timed out" }
            failure?.let { throw it }
            checkNotNull(measurement)
        } finally { fetcher.cancel(); fetcher.cleanup() }
    }

    private fun measureImage(bytes: ByteArray): Map<String, Any?> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val svgSize = if (bounds.outWidth <= 0) SvgUtils.getSize(bytes.inputStream()) else null
        val width = svgSize?.width ?: bounds.outWidth
        val height = svgSize?.height ?: bounds.outHeight
        check(width > 0 && height > 0) { "Image is not decodable" }
        val bitmap = checkNotNull(if (svgSize != null) SvgUtils.createBitmap(bytes.inputStream(), 100)
            else BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = 8 })) { "Image pixel decoding failed" }
        bitmap.recycle()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (bytes.size < 25_000) {
            File(output, "small-images").apply { mkdirs() }.resolve("$hash.img").writeBytes(bytes)
        }
        return mapOf("bytes" to bytes.size, "width" to width, "height" to height,
            "decoded" to true, "sha256" to hash, "substantial" to (width >= 200 && height >= 200))
    }
}
