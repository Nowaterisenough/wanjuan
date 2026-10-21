package io.wanjuan.app.sync

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.data.entities.BookSource
import org.junit.Assert.*
import org.junit.Test

class TestSyncChangeFingerprint {
    private val book = Book(bookUrl = "https://example.test/book", name = "Novel", origin = "source", group = 1L)
    private val group = BookGroup(groupId = 1L, groupName = "Favorites")
    private val source = BookSource(bookSourceUrl = "https://example.test", bookSourceName = "Source")

    private fun hash(books: List<Book> = listOf(book), groups: List<BookGroup> = listOf(group),
                     sources: List<BookSource> = listOf(source)) = SyncChangeFingerprint.of(books, groups, sources)

    @Test
    fun additionsSourceChangesAndMembershipChangesTrigger() {
        val initial = hash()
        assertNotEquals(initial, hash(books = emptyList()))
        assertNotEquals(initial, hash(books = listOf(book, book.copy(bookUrl = "other"))))
        assertNotEquals(initial, hash(books = listOf(book.copy(origin = "other-source", bookUrl = "other-url"))))
        assertNotEquals(initial, hash(books = listOf(book.copy(group = 2L))))
        assertNotEquals(initial, hash(books = listOf(book.copy(order = 10))))
    }

    @Test
    fun groupAndSourceEditsAdditionsDeletionsAndSortTrigger() {
        val initial = hash()
        assertNotEquals(initial, hash(groups = emptyList()))
        assertNotEquals(initial, hash(groups = listOf(group.copy(groupName = "Other"))))
        assertNotEquals(initial, hash(groups = listOf(group.copy(order = 10))))
        assertNotEquals(initial, hash(groups = listOf(group, group.copy(groupId = 2L))))
        assertNotEquals(initial, hash(sources = emptyList()))
        assertNotEquals(initial, hash(sources = listOf(source.copy(bookSourceName = "Edited"))))
        assertNotEquals(initial, hash(sources = listOf(source.copy(enabled = false))))
        assertNotEquals(initial, hash(sources = listOf(source.copy(customOrder = 10))))
    }

    @Test
    fun progressAndCatalogWritesDoNotTriggerAdditionalFullScans() {
        assertEquals(hash(), hash(books = listOf(book.copy(durChapterIndex = 10, durChapterPos = 100,
            durChapterTime = 10000, syncTime = 10000, totalChapterNum = 100,
            lastCheckTime = 10000, lastCheckCount = 10, latestChapterTitle = "Latest"))))
    }
}
