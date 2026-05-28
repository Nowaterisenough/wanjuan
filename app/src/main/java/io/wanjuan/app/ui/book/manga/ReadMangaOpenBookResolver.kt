package io.wanjuan.app.ui.book.manga

import io.wanjuan.app.data.entities.Book

internal object ReadMangaOpenBookResolver {

    fun resolve(
        requestedBookUrl: String?,
        findLastReadBook: () -> Book?,
        findBookByUrl: (String) -> Book?,
        currentBook: () -> Book?
    ): Book? {
        return if (requestedBookUrl.isNullOrEmpty()) {
            findLastReadBook() ?: currentBook()
        } else {
            findBookByUrl(requestedBookUrl)
        }
    }
}
