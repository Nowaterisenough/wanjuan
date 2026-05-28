package io.wanjuan.app

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.AppPattern
import io.wanjuan.app.constant.BookSourceType
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.help.book.isWebFile
import io.wanjuan.app.model.CheckSource
import io.wanjuan.app.model.analyzeRule.AnalyzeUrl
import io.wanjuan.app.model.webBook.WebBook
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.NetworkUtils
import io.wanjuan.app.utils.SvgUtils
import io.wanjuan.app.utils.fromJsonArray
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@LargeTest
@RunWith(AndroidJUnit4::class)
class BookSourceSampleInstrumentedTest {

    private val sampleNames = listOf("绅士漫画", "禁漫")
    private val testedSourceUrls = linkedSetOf<String>()

    @After
    fun cleanUp() {
        testedSourceUrls.forEach {
            appDb.bookSourceDao.delete(it)
        }
    }

    @Test
    fun sampleSourcesCanSearchLoadTocContentAndImages() = runBlocking {
        val sources = loadSampleSources()
            .filter { it.bookSourceName in sampleNames }
            .sortedBy { sampleNames.indexOf(it.bookSourceName) }

        assertTrue(
            "tests/shareBookSource.json must contain ${sampleNames.joinToString()}",
            sources.map { it.bookSourceName }.containsAll(sampleNames)
        )

        sources.forEach { source ->
            appDb.bookSourceDao.insert(source)
            testedSourceUrls.add(source.bookSourceUrl)

            withTimeout(SOURCE_TIMEOUT_MILLIS) {
                assertSourcePipeline(source)
            }
        }
    }

    private fun loadSampleSources(): List<BookSource> {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets.open("shareBookSource.json").use {
            GSON.fromJsonArray<BookSource>(it).getOrThrow()
        }
    }

    private suspend fun assertSourcePipeline(source: BookSource) {
        val keyword = source.getCheckKeyword(CheckSource.keyword)
        val searchBooks = WebBook.searchBookAwait(source, keyword)
        assertFalse("${source.bookSourceName}: search returned no books", searchBooks.isEmpty())

        val book = searchBooks.first().toBook()
        if (book.tocUrl.isBlank()) {
            WebBook.getBookInfoAwait(source, book)
        }
        assertTrue("${source.bookSourceName}: bookUrl is blank after search/info", book.bookUrl.isNotBlank())

        if (book.isWebFile) {
            return
        }

        val chapters = WebBook.getChapterListAwait(source, book).getOrThrow()
            .filterNot { it.isVolume && it.url.startsWith(it.title) }
        assertFalse("${source.bookSourceName}: toc returned no readable chapters", chapters.isEmpty())

        val firstChapter = chapters.first()
        val nextChapterUrl = chapters.getOrNull(1)?.url ?: firstChapter.url
        val content = WebBook.getContentAwait(
            bookSource = source,
            book = book,
            bookChapter = firstChapter,
            nextChapterUrl = nextChapterUrl,
            needSave = false
        )
        assertTrue("${source.bookSourceName}: content is blank", content.isNotBlank())

        if (source.bookSourceType == BookSourceType.image || content.contains("<img", ignoreCase = true)) {
            assertFirstImageLoads(source, book, firstChapter, content)
        }
    }

    private suspend fun assertFirstImageLoads(
        source: BookSource,
        book: Book,
        chapter: BookChapter,
        content: String
    ) {
        val matcher = AppPattern.imgPattern.matcher(content)
        assertTrue("${source.bookSourceName}: image content has no img src", matcher.find())

        val imageUrl = NetworkUtils.getAbsoluteURL(chapter.url, matcher.group(1).orEmpty())
        assertTrue("${source.bookSourceName}: resolved image url is blank", imageUrl.isNotBlank())

        val bytes = AnalyzeUrl(
            mUrl = imageUrl,
            source = source,
            ruleData = book
        ).getByteArrayAwait()

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val isBitmap = options.outWidth > 0 && options.outHeight > 0
        val isSvg = SvgUtils.getSize(ByteArrayInputStream(bytes)) != null

        assertTrue("${source.bookSourceName}: first image is not decodable: $imageUrl", isBitmap || isSvg)
    }

    private companion object {
        const val SOURCE_TIMEOUT_MILLIS = 180_000L
    }
}
