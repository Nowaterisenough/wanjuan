package io.wanjuan.app

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.constant.AppPattern
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.help.source.exploreKinds
import io.wanjuan.app.model.analyzeRule.AnalyzeUrl
import io.wanjuan.app.model.webBook.WebBook
import io.wanjuan.app.model.webBook.BookContent
import io.wanjuan.app.model.analyzeRule.AnalyzeRule
import io.wanjuan.app.ui.about.AboutActivity
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import java.io.File

/** Opt-in live checks report pipeline failures without displaying downloaded content. */
@RunWith(AndroidJUnit4::class)
class TestDiscoverSourceCompatibilityInstrumented {
    private var scenario: ActivityScenario<AboutActivity>? = null

    @After
    fun closeVerificationHost() {
        scenario?.close()
    }

    @Test
    fun galleryRulesKeepImageRequestHeaders() = runBlocking {
        val source = InstrumentationRegistry.getInstrumentation().context.assets.open("shareBookSource.json").use {
            GSON.fromJsonArray<BookSource>(it).getOrThrow().first { source -> source.bookSourceName == "小黄书" }
        }
        val url = "https://example.org/photo/sample.html"
        val book = Book(bookUrl = url, origin = source.bookSourceUrl, name = "Gallery fixture")
        val html = """<div class="photo-image"><div class="img" style="background-image:url('https://images.example.org/page.webp')"></div></div>"""
        val rule = AnalyzeRule(book, source).apply { setContent(html, url) }
        assertEquals(1, rule.getElements(source.getTocRule().chapterList.orEmpty()).size)
        assertEquals(BookType.image, book.type)
        val content = BookContent.analyzeContent(source, book, BookChapter(url = url, bookUrl = url),
            url, url, html, url, needSave = false)
        val matcher = AppPattern.imgPattern.matcher(content)
        assertTrue(matcher.find())
        val image = AnalyzeUrl(matcher.group(1).orEmpty(), source = source, ruleData = book)
        assertEquals("https://images.example.org/page.webp", image.url)
        assertEquals(url, image.headerMap["Referer"])
        assertTrue(!image.headerMap["User-Agent"].isNullOrBlank())
    }

    @Test
    fun inspectRequestedDiscoverySources() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val names = args.getString("sourceNames").orEmpty().split('|').filter { it.isNotBlank() }
        assumeTrue("Live checks require sourceNames", names.isNotEmpty())
        scenario = ActivityScenario.launch(AboutActivity::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "source-compat").apply { mkdirs() }
        val sources = instrumentation.context.assets.open("shareBookSource.json").use {
            GSON.fromJsonArray<BookSource>(it).getOrThrow()
        }.filter { it.bookSourceName in names }
        check(sources.size == names.size) { "Requested sources are missing from the sample" }
        val reports = arrayListOf<Map<String, Any?>>()
        for (source in sources) {
            val original = appDb.bookSourceDao.getBookSource(source.bookSourceUrl)
            val report = linkedMapOf<String, Any?>("source" to source.bookSourceName)
            reports.add(report)
            appDb.bookSourceDao.insert(source)
            try {
                withTimeout(120_000L) {
                    report["stage"] = "categories"
                    val kinds = source.exploreKinds()
                    report["categories"] = kinds
                    val pattern = args.getString("entryPattern")?.toRegex()
                    val entries = kinds.filter {
                        !it.url.isNullOrBlank() && !it.title.startsWith("ERROR:") &&
                            (pattern == null || pattern.containsMatchIn(it.url))
                    }.take(args.getString("entryLimit")?.toIntOrNull() ?: 1)
                    check(entries.isNotEmpty()) { kinds.firstOrNull()?.title ?: "No discovery entries" }
                    val results = arrayListOf<Map<String, Any?>>()
                    report["entries"] = results
                    for (entry in entries) {
                        val result = linkedMapOf<String, Any?>("url" to entry.url)
                        results.add(result)
                        try {
                            withTimeout(60_000L) {
                                result["stage"] = "list"
                                val books = WebBook.exploreBookAwait(source, entry.url!!, 1)
                                result["books"] = books.size
                                check(books.isNotEmpty()) { "Empty discovery list" }
                                val book = books.first().toBook()
                                result["bookUrl"] = book.bookUrl
                                result["initialType"] = book.type
                                result["stage"] = "info"
                                WebBook.getBookInfoAwait(source, book)
                                result["infoType"] = book.type
                                if (args.getString("captureHtml") == "true") {
                                    File(output, "detail.html").writeText(book.tocHtml.orEmpty())
                                }
                                result["stage"] = "chapters"
                                val chapters = WebBook.getChapterListAwait(source, book).getOrThrow()
                                result["chapters"] = chapters.size
                                result["resolvedType"] = book.type
                                val chapter = chapters.first { !it.isVolume }
                                result["stage"] = "content"
                                val content = WebBook.getContentAwait(source, book, chapter,
                                    chapters.getOrNull(1)?.url ?: chapter.url, needSave = false)
                                result["contentLength"] = content.length
                                val matcher = AppPattern.imgPattern.matcher(content)
                                if (matcher.find()) {
                                    result["stage"] = "image"
                                    val imageRequest = AnalyzeUrl(mUrl = matcher.group(1).orEmpty(),
                                        baseUrl = chapter.url, source = source, ruleData = book)
                                    result["imageUrl"] = imageRequest.url
                                    result["imageReferer"] = imageRequest.headerMap["Referer"]
                                    val bytes = imageRequest.getByteArrayAwait()
                                    result["imageBytes"] = bytes.size
                                    result["imageSignature"] = bytes.take(12).joinToString("") { "%02x".format(it) }
                                    if (bytes.take(256).toByteArray().decodeToString().trimStart().startsWith("<")) {
                                        result["imageResponseTitle"] = org.jsoup.Jsoup.parse(bytes.decodeToString()).title()
                                    }
                                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                    result["imageDimensions"] = listOf(options.outWidth, options.outHeight)
                                    check(options.outWidth > 0 && options.outHeight > 0) { "Image is not decodable" }
                                }
                                result["stage"] = "complete"
                            }
                        } catch (e: Exception) {
                            result["error"] = e.stackTraceToString()
                        }
                        File(output, "report.json").writeText(GSON.toJson(reports))
                    }
                }
            } catch (e: Exception) {
                report["error"] = e.stackTraceToString()
            } finally {
                if (original == null) appDb.bookSourceDao.delete(source.bookSourceUrl)
                else appDb.bookSourceDao.insert(original)
                File(output, "report.json").writeText(GSON.toJson(reports))
            }
        }
    }
}
