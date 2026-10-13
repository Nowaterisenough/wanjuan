package io.wanjuan.app.ui.book.manga.recyclerview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestMangaPreloadWindow {
    @Test
    fun scrollingWithinOnePageDoesNotEnqueueTheSameTenDownloadsEveryFrame() {
        val window = MangaPreloadWindow()
        val pages: List<Any> = (0..100).toList()
        val enqueued = (0..59).flatMap { window.entering(pages, 3, 5, 10, true) }
        assertEquals((6..15).toList(), enqueued)
        assertEquals(listOf(16), window.entering(pages, 4, 6, 10, true))
    }

    @Test
    fun reversingAndReturningToAWindowAllowsLoadingAgain() {
        val window = MangaPreloadWindow()
        val pages: List<Any> = (0..100).toList()
        window.entering(pages, 10, 12, 3, true)
        assertEquals(listOf(9, 8, 7), window.entering(pages, 10, 12, 3, false))
        assertEquals(listOf(13, 14, 15), window.entering(pages, 10, 12, 3, true))
    }

    @Test
    fun chapterReplacementAndResumeResetTheWindow() {
        val window = MangaPreloadWindow()
        val oldPages: List<Any> = listOf("a", "b", "c")
        val newPages: List<Any> = listOf("x", "y", "z")
        assertEquals(listOf("b", "c"), window.entering(oldPages, 0, 0, 10, true))
        assertEquals(listOf("y", "z"), window.entering(newPages, 0, 0, 10, true))
        window.clear()
        assertEquals(listOf("y", "z"), window.entering(newPages, 0, 0, 10, true))
        assertTrue(window.entering(newPages, 0, 0, 10, false).isEmpty())
        assertTrue(window.entering(newPages, 2, 2, 10, true).isEmpty())
    }
}
