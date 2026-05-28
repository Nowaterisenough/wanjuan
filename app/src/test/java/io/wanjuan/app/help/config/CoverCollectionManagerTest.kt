package io.wanjuan.app.help.config

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.SearchBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverCollectionManagerTest {

    @Test
    fun coverCollectionUsesStableKeysForBookAndSearchBookFallbacks() {
        val book = Book(bookUrl = "", origin = "origin-a", name = "Name", author = "Author")
        val searchBook = SearchBook(bookUrl = "", origin = "origin-a", name = "Name", author = "Author")

        assertEquals("origin-a|Name|Author", CoverCollectionManager.coverKey(book))
        assertEquals("origin-a|Name|Author", CoverCollectionManager.coverKey(searchBook))
        assertEquals("url-a", CoverCollectionManager.coverKey(book.copy(bookUrl = "url-a")))
        assertEquals("url-b", CoverCollectionManager.coverKey(searchBook.copy(bookUrl = "url-b")))
    }

    @Test
    fun coverCollectionStableIndexNeverReturnsNegativeForMinHash() {
        val minHashKey = String(charArrayOf(2.toChar(), 13.toChar(), 0.toChar(), 9.toChar(), 30.toChar(), 12.toChar(), 2.toChar()))

        assertEquals(Int.MIN_VALUE, minHashKey.hashCode())
        val index = CoverCollectionManager.stableImageIndex(minHashKey, 5)

        assertTrue(index in 0 until 5)
    }

    @Test
    fun coverCollectionDirectoryNameStaysUniqueForDuplicateNames() {
        assertEquals("Same Name_id-a", CoverCollectionManager.buildDirName("Same Name", "id-a"))
        assertEquals("Same Name_id-b", CoverCollectionManager.buildDirName("Same Name", "id-b"))
    }
}
