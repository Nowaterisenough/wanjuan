package io.wanjuan.app.ui.book.manga

import io.wanjuan.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadMangaOpenBookResolverTest {

    @Test
    fun `explicit missing bookUrl does not fall back to current book`() {
        val currentBook = Book(bookUrl = "current-url", name = "Current")

        val resolved = ReadMangaOpenBookResolver.resolve(
            requestedBookUrl = "old-url",
            findLastReadBook = { error("last read book should not be queried") },
            findBookByUrl = { null },
            currentBook = { currentBook }
        )

        assertNull(resolved)
    }

    @Test
    fun `empty bookUrl can fall back to current book when last read book is missing`() {
        val currentBook = Book(bookUrl = "current-url", name = "Current")

        val resolved = ReadMangaOpenBookResolver.resolve(
            requestedBookUrl = null,
            findLastReadBook = { null },
            findBookByUrl = { error("bookUrl lookup should not be queried") },
            currentBook = { currentBook }
        )

        assertEquals(currentBook, resolved)
    }
}
