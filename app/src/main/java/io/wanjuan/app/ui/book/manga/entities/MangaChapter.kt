package io.wanjuan.app.ui.book.manga.entities

import io.wanjuan.app.data.entities.BookChapter

data class MangaChapter(
    val chapter: BookChapter,
    val pages: List<BaseMangaPage>,
    val imageCount: Int
)
