package io.wanjuan.app.sync.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavSyncPathTest {

    @Test
    fun davHrefUsesOnlyLeafFileName() {
        assertEquals(
            "books/a.json",
            syncRemotePath("books", "/dav/user/sync/v1/books/a.json")
        )
        assertEquals(
            "books/a.json",
            syncRemotePath("books", "https://dav.example/dav/user/sync/v1/books/a.json")
        )
        assertEquals("books/a.json", syncRemotePath("books", "a.json"))
    }

    @Test
    fun traversalLeafIsRejected() {
        val failure = runCatching { syncRemotePath("books", "..") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
