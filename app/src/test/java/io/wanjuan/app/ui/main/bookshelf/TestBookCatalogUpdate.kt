package io.wanjuan.app.ui.main.bookshelf

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.help.book.BookCatalogUpdate
import org.junit.Assert.assertEquals
import org.junit.Test

class TestBookCatalogUpdate {
    @Test
    fun slowCatalogRequestPreservesConcurrentRegroupingAndReading() {
        val before = Book(bookUrl = "book", group = 1, customTag = "old", syncTime = 100)
        val current = before.copy(group = 2, customTag = "new", order = 9, durChapterIndex = 42, syncTime = 300)
        val fetched = before.copy(totalChapterNum = 100, latestChapterTitle = "last", lastCheckTime = 400)

        val result = BookCatalogUpdate.merge(current, before, fetched)

        assertEquals(2L, result.group)
        assertEquals("new", result.customTag)
        assertEquals(9, result.order)
        assertEquals(42, result.durChapterIndex)
        assertEquals(300L, result.syncTime)
        assertEquals(100, result.totalChapterNum)
        assertEquals("last", result.latestChapterTitle)
    }

    @Test
    fun fetchedMetadataOnlyReplacesFieldsUnchangedSinceRequestStarted() {
        val before = Book(bookUrl = "book", name = "old", intro = "old intro")
        val current = before.copy(name = "edited locally")
        val fetched = before.copy(name = "source name", intro = "source intro")

        val result = BookCatalogUpdate.merge(current, before, fetched)

        assertEquals("edited locally", result.name)
        assertEquals("source intro", result.intro)
    }
}
