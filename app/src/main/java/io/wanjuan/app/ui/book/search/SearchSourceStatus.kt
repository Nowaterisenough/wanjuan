package io.wanjuan.app.ui.book.search

import io.wanjuan.app.data.entities.BookSourcePart

enum class SearchSourceState {
    PENDING,
    FOUND,
    EMPTY,
    FAILED
}

data class SearchSourceStatus(
    val sourceUrl: String,
    val sourceName: String,
    val state: SearchSourceState,
    val resultCount: Int = 0,
    val errorMessage: String? = null
)

fun BookSourcePart.toSearchSourceStatus(
    state: SearchSourceState,
    resultCount: Int = 0,
    errorMessage: String? = null
): SearchSourceStatus {
    return SearchSourceStatus(
        sourceUrl = bookSourceUrl,
        sourceName = bookSourceName.ifBlank { bookSourceUrl },
        state = state,
        resultCount = resultCount.coerceAtLeast(0),
        errorMessage = errorMessage
    )
}
