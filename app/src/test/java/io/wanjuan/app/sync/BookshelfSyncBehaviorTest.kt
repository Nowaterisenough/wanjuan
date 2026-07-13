package io.wanjuan.app.sync

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.sync.model.SyncBookGroupPayload
import io.wanjuan.app.sync.model.SyncOrderPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfSyncBehaviorTest {

    @Test
    fun sameLocalBitForDifferentGroupsIsRemappedByStableId() {
        val replicaA = MemoryBookGroupStore(
            groups = mutableListOf(group(1L, "A", "group-a"))
        )
        val replicaB = MemoryBookGroupStore(
            groups = mutableListOf(group(1L, "B", "group-b"))
        )
        val coordinatorA = BookGroupSyncCoordinator(replicaA)
        val coordinatorB = BookGroupSyncCoordinator(replicaB)

        coordinatorA.applyRemote(payload("group-b", "B", 1L))
        coordinatorB.applyRemote(payload("group-a", "A", 1L))

        val aBitOnA = coordinatorA.remoteGroupIdsToLocalMask(listOf("group-a"))
        val bBitOnA = coordinatorA.remoteGroupIdsToLocalMask(listOf("group-b"))
        val aBitOnB = coordinatorB.remoteGroupIdsToLocalMask(listOf("group-a"))
        val bBitOnB = coordinatorB.remoteGroupIdsToLocalMask(listOf("group-b"))
        assertNotEquals(aBitOnA, bBitOnA)
        assertNotEquals(aBitOnB, bBitOnB)
        assertEquals(listOf("group-a"), coordinatorA.localMaskToRemoteGroupIds(aBitOnA))
        assertEquals(listOf("group-b"), coordinatorB.localMaskToRemoteGroupIds(bBitOnB))
    }

    @Test
    fun deletingGroupClearsOnlyItsBookMembershipBit() {
        val store = MemoryBookGroupStore(
            groups = mutableListOf(
                group(1L, "A", "group-a"),
                group(2L, "B", "group-b")
            ),
            books = mutableListOf(Book(bookUrl = "book", group = 3L))
        )

        BookGroupSyncCoordinator(store).applyRemoteDelete("group-a")

        assertEquals(2L, store.books.single().group)
        assertEquals(listOf("group-b"), store.groups.map { it.syncId })
    }

    @Test
    fun remoteGroupOrderAppendsLocalExtrasStably() {
        val store = MemoryBookGroupStore(
            groups = mutableListOf(
                group(1L, "A", "group-a", order = 0),
                group(2L, "B", "group-b", order = 1),
                group(4L, "Local", "group-local", order = 2)
            )
        )

        BookGroupSyncCoordinator(store).applyRemoteOrder(
            SyncOrderPayload(100L, "device-b", listOf("group-b", "group-a"))
        )

        assertEquals(
            listOf("group-b", "group-a", "group-local"),
            store.groups.sortedBy { it.order }.map { it.syncId }
        )
        assertTrue(store.groups.map { it.order }.toSet().size == 3)
    }

    @Test
    fun remoteBookDeleteUsesStableIdAndOrderAppendsLocalExtras() {
        val remoteFirst = Book(bookUrl = "remote-first", origin = "source", order = 2)
        val remoteSecond = Book(bookUrl = "remote-second", origin = "source", order = 0)
        val localExtra = Book(bookUrl = "local-extra", origin = "source", order = 1)
        val store = MemoryBookshelfStore(
            mutableListOf(remoteFirst, remoteSecond, localExtra)
        )
        val applier = BookshelfObjectApplier(store)

        assertTrue(applier.applyRemoteDelete(SyncIds.bookId(remoteSecond)))
        applier.applyRemoteOrder(
            SyncOrderPayload(
                100L,
                "device-b",
                listOf(SyncIds.bookId(remoteFirst))
            )
        )

        assertEquals(
            listOf("remote-first", "local-extra"),
            store.books.sortedBy { it.order }.map { it.bookUrl }
        )
        assertEquals(listOf(0, 1), store.books.sortedBy { it.order }.map { it.order })
    }

    private fun group(id: Long, name: String, syncId: String, order: Int = 0) =
        BookGroup(groupId = id, groupName = name, order = order, syncId = syncId)

    private fun payload(syncId: String, name: String, legacyId: Long) =
        SyncBookGroupPayload(
            groupSyncId = syncId,
            legacyGroupId = legacyId,
            groupName = name,
            cover = null,
            order = 0,
            enableRefresh = true,
            show = true,
            bookSort = -1,
            onlyUpdateRead = false,
            updatedAt = 100L,
            updatedByDeviceId = "device-remote"
        )

    private class MemoryBookGroupStore(
        val groups: MutableList<BookGroup> = mutableListOf(),
        val books: MutableList<Book> = mutableListOf()
    ) : BookGroupSyncStore {
        override fun allGroups(): List<BookGroup> = groups.toList()

        override fun allBooks(): List<Book> = books.toList()

        override fun nextGroupId(): Long {
            var bit = 1L
            val used = groups.filter { it.groupId > 0L }.fold(0L) { acc, group ->
                acc or group.groupId
            }
            while (used and bit != 0L) bit = bit shl 1
            return bit
        }

        override fun insertGroup(group: BookGroup) {
            groups.removeAll { it.groupId == group.groupId }
            groups += group
        }

        override fun updateGroup(group: BookGroup) = insertGroup(group)

        override fun deleteGroup(group: BookGroup) {
            groups.removeAll { it.groupId == group.groupId }
        }

        override fun updateBook(book: Book) {
            books.removeAll { it.bookUrl == book.bookUrl }
            books += book
        }
    }

    private class MemoryBookshelfStore(
        val books: MutableList<Book>
    ) : BookshelfSyncStore {
        override fun allBooks(): List<Book> = books.toList()

        override fun insertBook(book: Book) {
            books.removeAll { it.bookUrl == book.bookUrl }
            books += book
        }

        override fun updateBook(book: Book) = insertBook(book)

        override fun deleteBook(book: Book) {
            books.removeAll { it.bookUrl == book.bookUrl }
        }
    }
}
