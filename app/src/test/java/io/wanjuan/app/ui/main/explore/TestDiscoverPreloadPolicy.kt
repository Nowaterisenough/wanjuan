package io.wanjuan.app.ui.main.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDiscoverPreloadPolicy {

    @Test
    fun thresholdKeepsSixItemsForListsAndSmallGrids() {
        assertEquals(6, DiscoverPreloadPolicy.threshold(spanCount = 1))
        assertEquals(6, DiscoverPreloadPolicy.threshold(spanCount = 3))
    }

    @Test
    fun thresholdUsesTwoRowsForWideGrids() {
        assertEquals(8, DiscoverPreloadPolicy.threshold(spanCount = 4))
        assertEquals(14, DiscoverPreloadPolicy.threshold(spanCount = 7))
    }

    @Test
    fun listStartsLoadingWithSixItemsRemaining() {
        assertFalse(DiscoverPreloadPolicy.shouldLoadMore(100, 92, 1))
        assertTrue(DiscoverPreloadPolicy.shouldLoadMore(100, 93, 1))
    }

    @Test
    fun sevenColumnGridStartsLoadingWithTwoRowsRemaining() {
        assertFalse(DiscoverPreloadPolicy.shouldLoadMore(100, 84, 7))
        assertTrue(DiscoverPreloadPolicy.shouldLoadMore(100, 85, 7))
    }

    @Test
    fun emptyListAndInvalidVisiblePositionDoNotLoad() {
        assertFalse(DiscoverPreloadPolicy.shouldLoadMore(0, 0, 1))
        assertFalse(DiscoverPreloadPolicy.shouldLoadMore(20, -1, 1))
    }
}
