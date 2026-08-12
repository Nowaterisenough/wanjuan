package io.wanjuan.app.ui.book.manga.entities

data class MangaContent(
    val chapterIndex: Int,
    val generation: Long,
    val pos: Int,
    val items: List<Any>,
    val curFinish: Boolean,
    val nextFinish: Boolean
)
