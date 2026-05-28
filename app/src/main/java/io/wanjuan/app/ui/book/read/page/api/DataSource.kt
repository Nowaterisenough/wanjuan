package io.wanjuan.app.ui.book.read.page.api

import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.ui.book.read.page.entities.TextChapter

interface DataSource {

    val pageIndex: Int get() = ReadBook.durPageIndex

    val currentChapter: TextChapter?

    val nextChapter: TextChapter?

    val prevChapter: TextChapter?

    val isScroll: Boolean

    fun hasNextChapter(): Boolean

    fun hasPrevChapter(): Boolean

    fun upContent(relativePosition: Int = 0, resetPageOffset: Boolean = true)

}