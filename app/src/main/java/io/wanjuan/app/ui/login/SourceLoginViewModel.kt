package io.wanjuan.app.ui.login

import android.app.Application
import android.content.Intent
import com.script.rhino.runScriptWithContext
import io.wanjuan.app.base.BaseViewModel
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BaseSource
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.book.ParagraphRuleJsExtensions
import io.wanjuan.app.model.AudioPlay
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.model.ReadManga
import io.wanjuan.app.model.VideoPlay
import io.wanjuan.app.utils.toastOnUi

class SourceLoginViewModel(application: Application) : BaseViewModel(application) {

    var source: BaseSource? = null
    var headerMap: Map<String, String> = emptyMap()
    var book: Book? = null
    var bookType: Int = 0
    var chapter: BookChapter? = null
    var loginInfo: MutableMap<String, String> = mutableMapOf()

    fun initData(intent: Intent, success: (bookSource: BaseSource) -> Unit, error: () -> Unit) {
        execute {
            bookType = intent.getIntExtra("bookType", 0)
            when (bookType) {
                BookType.text -> {
                    source = ReadBook.bookSource
                    book = ReadBook.book?.also {
                        chapter = appDb.bookChapterDao.getChapter(it.bookUrl, ReadBook.durChapterIndex)
                    }
                }

                BookType.audio -> {
                    source = AudioPlay.bookSource
                    book = AudioPlay.book
                    chapter = AudioPlay.durChapter
                }

                BookType.image -> {
                    source = ReadManga.bookSource
                    book = ReadManga.book?.also {
                        chapter = appDb.bookChapterDao.getChapter(it.bookUrl, ReadManga.durChapterIndex)
                    }
                }

                BookType.video -> {
                    source = VideoPlay.source
                    book = VideoPlay.book
                    chapter = VideoPlay.chapter
                }

                else -> {
                    val sourceKey = intent.getStringExtra("key")
                        ?: throw NoStackTraceException("没有参数")
                    val type = intent.getStringExtra("type")
                    source = when (type) {
                        "bookSource" ->  appDb.bookSourceDao.getBookSource(sourceKey)
                        "rssSource" -> appDb.rssSourceDao.getByKey(sourceKey)
                        "httpTts" -> appDb.httpTTSDao.get(sourceKey.toLong())
                        "paragraphRule" -> appDb.paragraphRuleDao.get(sourceKey.toLong())?.let { rule ->
                            ParagraphRuleJsExtensions(rule)
                        }
                        else -> null
                    }
                    val bookUrl = intent.getStringExtra("bookUrl")
                    book = bookUrl?.let {
                        appDb.bookDao.getBook(it) ?: appDb.searchBookDao.getSearchBook(it)?.toBook()
                    }
                }
            }
            headerMap = runScriptWithContext {
                source?.getHeaderMap(true) ?: emptyMap()
            }
            source?.let{ loginInfo = it.getLoginInfoMap() }
            source
        }.onSuccess {
            if (it != null) {
                success.invoke(it)
            } else {
                context.toastOnUi("未找到书源")
            }
        }.onError {
            error.invoke()
            AppLog.put("登录 UI 初始化失败\n$it", it, true)
        }
    }

}
