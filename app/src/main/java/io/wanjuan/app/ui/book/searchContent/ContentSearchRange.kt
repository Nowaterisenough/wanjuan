package io.wanjuan.app.ui.book.searchContent

import android.content.Context
import io.wanjuan.app.R
import io.wanjuan.app.data.entities.BookChapter

internal enum class ContentSearchRange(val label: Int) {
    ALL(R.string.search_scope_entire_book), BEFORE(R.string.search_scope_before), AFTER(R.string.search_scope_after), CURRENT(R.string.search_current_chapter);

    fun chapters(all: List<BookChapter>, current: Int): List<BookChapter> = when (this) {
        ALL -> all
        BEFORE -> all.filter { it.index <= current }.asReversed()
        AFTER -> all.filter { it.index >= current }
        CURRENT -> all.filter { it.index == current }
    }

    fun includes(chapter: Int, current: Int, position: Int, offset: Int) = chapter != current || when (this) {
        BEFORE -> position <= offset
        AFTER -> position >= offset
        else -> true
    }

    fun remember(context: Context) = context.getSharedPreferences("contentSearch", Context.MODE_PRIVATE)
        .edit().putInt("direction", ordinal).apply()

    companion object {
        fun saved(context: Context) = entries.getOrNull(context.getSharedPreferences("contentSearch", Context.MODE_PRIVATE)
            .getInt("direction", 0)) ?: ALL
    }
}
