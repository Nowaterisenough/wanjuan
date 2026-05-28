package io.wanjuan.app.utils

import io.wanjuan.app.data.entities.BookChapter

fun BookChapter.internString() {
    title = title.intern()
    bookUrl = bookUrl.intern()
}
