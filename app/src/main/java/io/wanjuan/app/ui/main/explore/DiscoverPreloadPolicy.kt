package io.wanjuan.app.ui.main.explore

import kotlin.math.max

internal object DiscoverPreloadPolicy {

    private const val MIN_PREFETCH_ITEMS = 6
    private const val GRID_PREFETCH_ROWS = 2

    fun threshold(spanCount: Int): Int {
        return max(MIN_PREFETCH_ITEMS, spanCount.coerceAtLeast(1) * GRID_PREFETCH_ROWS)
    }

    fun shouldLoadMore(
        itemCount: Int,
        lastVisiblePosition: Int,
        spanCount: Int
    ): Boolean {
        if (itemCount <= 0 || lastVisiblePosition < 0) return false
        val remainingItems = (itemCount - 1 - lastVisiblePosition).coerceAtLeast(0)
        return remainingItems <= threshold(spanCount)
    }
}
