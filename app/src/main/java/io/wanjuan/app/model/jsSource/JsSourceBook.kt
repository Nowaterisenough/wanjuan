package io.wanjuan.app.model.jsSource

import io.wanjuan.app.R
import io.wanjuan.app.constant.BookSourceType
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.SearchBook
import io.wanjuan.app.exception.ContentEmptyException
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.exception.TocEmptyException
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.help.ConcurrentRateLimiter
import io.wanjuan.app.help.book.addType
import io.wanjuan.app.help.book.isWebFile
import io.wanjuan.app.help.book.removeAllBookType
import io.wanjuan.app.help.source.getBookType
import io.wanjuan.app.model.Debug
import io.wanjuan.app.model.webBook.BookChapterList
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext

object JsSourceBook {

    suspend fun searchAwait(
        source: BookSource,
        key: String,
        page: Int? = 1,
        filter: ((name: String, author: String, kind: String?) -> Boolean)? = null,
    ): ArrayList<SearchBook> {
        val engine = JsSourceEngine(source, coroutineContext)
        val json = engine.callFunction("search", listOf("key" to key, "page" to (page ?: 1)))
        Debug.log(source.bookSourceUrl, json.orEmpty(), state = 10)
        val books = JsSourceMarshaller.parseSearchBooks(json, source)
        if (filter != null) {
            books.removeAll { !filter(it.name, it.author, it.kind) }
        }
        Debug.log(source.bookSourceUrl, "◇JS源搜索完成,共${books.size}条")
        return books
    }

    suspend fun exploreAwait(
        source: BookSource,
        url: String,
        page: Int? = 1,
    ): ArrayList<SearchBook> {
        val engine = JsSourceEngine(source, coroutineContext)
        val json = engine.callFunction("explore", listOf("url" to url, "page" to (page ?: 1)))
        Debug.log(source.bookSourceUrl, json.orEmpty(), state = 10)
        val books = JsSourceMarshaller.parseSearchBooks(json, source)
        Debug.log(source.bookSourceUrl, "◇JS源发现完成,共${books.size}条")
        return books
    }

    suspend fun getBookInfoAwait(
        source: BookSource,
        book: Book,
        canReName: Boolean,
    ): Book {
        book.removeAllBookType()
        book.addType(source.getBookType())
        val engine = JsSourceEngine(source, coroutineContext)
        val json = engine.callFunctionIfExists("getBookInfo", listOf("book" to book))
        Debug.log(source.bookSourceUrl, json.orEmpty(), state = 20)
        JsSourceMarshaller.mergeBookInfo(book, json, source, canReName)
        if (source.bookSourceType == BookSourceType.file) {
            book.addType(source.getBookType())
        }
        if (!book.isWebFile && book.tocUrl.isBlank()) {
            book.tocUrl = book.bookUrl
        }
        if (book.isWebFile && book.downloadUrls.isNullOrEmpty()) {
            throw NoStackTraceException("下载链接为空")
        }
        return book
    }

    suspend fun getChapterListAwait(
        source: BookSource,
        book: Book,
    ): Result<List<BookChapter>> {
        book.removeAllBookType()
        book.addType(source.getBookType())
        val context = coroutineContext
        return runCatching {
            val engine = JsSourceEngine(source, context)
            val json = engine.callFunction("getChapters", listOf("book" to book))
            Debug.log(source.bookSourceUrl, json.orEmpty(), state = 30)
            val chapters = JsSourceMarshaller.parseChapters(json, book, source)
            if (chapters.isEmpty()) {
                throw TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
            }
            BookChapterList.updateBookTocInfo(book, chapters)
            Debug.log(source.bookSourceUrl, "◇JS源目录完成,共${chapters.size}章")
            chapters
        }.onFailure {
            context.ensureActive()
        }
    }

    suspend fun getContentAwait(
        source: BookSource,
        book: Book,
        chapter: BookChapter,
        nextChapterUrl: String? = null,
        needSave: Boolean = true,
    ): String {
        if (chapter.isVolume && chapter.url.startsWith(chapter.title)) {
            Debug.log(source.bookSourceUrl, "⇒一级目录正文不解析")
            return ""
        }
        val engine = JsSourceEngine(source, coroutineContext)
        val content = engine.callFunction(
            "getContent",
            listOf(
                "chapter" to chapter,
                "book" to book,
                "nextChapterUrl" to nextChapterUrl,
            ),
        ).orEmpty()
        if (!chapter.isVolume && content.isBlank()) {
            throw ContentEmptyException("内容为空")
        }
        Debug.log(source.bookSourceUrl, content, state = 40)
        if (needSave) {
            BookHelp.saveContent(source, book, chapter, content)
        }
        return content
    }

}
