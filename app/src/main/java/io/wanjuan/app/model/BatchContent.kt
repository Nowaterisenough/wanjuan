package io.wanjuan.app.model

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.model.analyzeRule.AnalyzeRule
import io.wanjuan.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.wanjuan.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.wanjuan.app.model.jsSource.JsSourceEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.mozilla.javascript.Wrapper

internal class BatchContent(private val chapters: List<BookChapter>) {
    private val results = linkedMapOf<Int, String>()
    private var open = true

    @Synchronized
    fun accept(identifier: Any?, text: String): Boolean {
        if (!open || text.isBlank()) return false
        val value = if (identifier is Wrapper) identifier.unwrap() else identifier
        val chapter = when (value) {
            is BookChapter -> chapters.firstOrNull { it === value }
            is CharSequence -> chapters.singleOrNull { it.url == value.toString() || it.getAbsoluteURL() == value.toString() }
            else -> null
        } ?: return false
        results[chapter.index] = text
        return true
    }

    @Synchronized private fun close(): Map<Int, String> {
        open = false
        return results.toMap()
    }

    companion object {
        suspend fun download(source: BookSource, book: Book, chapters: List<BookChapter>, saved: (BookChapter, String) -> Unit) {
            val ctx = currentCoroutineContext()
            val batch = BatchContent(chapters)
            val analyzer = AnalyzeRule(book, source).setCoroutineContext(ctx)
            analyzer.setBaseUrl(book.tocUrl.ifBlank { source.bookSourceUrl })
            analyzer.setChapter(chapters.first())
            try {
                if (!source.mainJs.isNullOrBlank()) {
                    JsSourceEngine(source, ctx, batch).callFunction("getContentBatch", listOf("chapters" to chapters, "book" to book))
                } else {
                    analyzer.batch = batch
                    val script = requireNotNull(source.ruleContent?.contentBatch).trim()
                    if (script.startsWith("@js:", true) || script.startsWith("<js>", true)) {
                        for (rule in analyzer.splitSourceRule(script)) {
                            require(rule.mode == AnalyzeRule.Mode.Js) { "批量正文需要 JavaScript 规则" }
                            analyzer.evalJS(rule.rule, chapters)
                        }
                    } else analyzer.evalJS(script, chapters)
                }
            } finally {
                val received = batch.close()
                analyzer.batch = null
                for (chapter in chapters) {
                    ctx.ensureActive()
                    var text = received[chapter.index] ?: continue
                    analyzer.setChapter(chapter)
                    source.ruleContent?.replaceRegex?.takeIf { it.isNotBlank() }?.let { text = analyzer.getString(it, text) }
                    if (text.isBlank()) continue
                    BookHelp.saveText(book, chapter, text)
                    BookHelp.saveImages(source, book, chapter, text, 1)
                    io.wanjuan.app.utils.postEvent(io.wanjuan.app.constant.EventBus.SAVE_CONTENT, book to chapter)
                    saved(chapter, text)
                }
            }
        }
    }
}
