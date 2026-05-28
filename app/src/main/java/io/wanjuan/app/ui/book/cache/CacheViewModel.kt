package io.wanjuan.app.ui.book.cache

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.wanjuan.app.base.BaseViewModel
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.help.book.isLocal
import io.wanjuan.app.help.coroutine.Coroutine
import io.wanjuan.app.utils.sendValue
import kotlinx.coroutines.ensureActive
import kotlin.collections.set


class CacheViewModel(application: Application) : BaseViewModel(application) {
    val upAdapterLiveData = MutableLiveData<String>()

    private var loadChapterCoroutine: Coroutine<Unit>? = null
    val cacheChapters = hashMapOf<String, HashSet<String>>()

    fun loadCacheFiles(books: List<Book>) {
        loadChapterCoroutine?.cancel()
        loadChapterCoroutine = execute {
            books.forEach { book ->
                if (!book.isLocal && !cacheChapters.contains(book.bookUrl)) {
                    val chapterCaches = hashSetOf<String>()
                    val cacheNames = BookHelp.getChapterFiles(book)
                    if (cacheNames.isNotEmpty()) {
                        appDb.bookChapterDao.getChapterList(book.bookUrl).also {
                            book.totalChapterNum = it.size
                        }.forEach { chapter ->
                            if (
                                chapter.isVolume ||
                                BookHelp.getChapterCacheFileNames(book, chapter)
                                    .any(cacheNames::contains)
                            ) {
                                chapterCaches.add(chapter.url)
                            }
                        }
                    }
                    cacheChapters[book.bookUrl] = chapterCaches
                    upAdapterLiveData.sendValue(book.bookUrl)
                }
                ensureActive()
            }
        }
    }

}
