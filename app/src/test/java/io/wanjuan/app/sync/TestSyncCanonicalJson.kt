package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookGroupPayload
import io.wanjuan.app.sync.model.SyncBookPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TestSyncCanonicalJson {

    @Test
    fun objectKeyOrderDoesNotChangeHash() {
        val first = linkedMapOf(
            "name" to "book",
            "nested" to linkedMapOf("enabled" to true, "order" to 2)
        )
        val second = linkedMapOf(
            "nested" to linkedMapOf("order" to 2, "enabled" to true),
            "name" to "book"
        )

        assertEquals(SyncCanonicalJson.hash(first), SyncCanonicalJson.hash(second))
    }

    @Test
    fun arrayOrderStillChangesHash() {
        assertNotEquals(
            SyncCanonicalJson.hash(mapOf("items" to listOf("a", "b"))),
            SyncCanonicalJson.hash(mapOf("items" to listOf("b", "a")))
        )
    }

    @Test
    fun v2BookHashIgnoresDeviceLocalGroupBits() {
        val first = SyncBookPayload(
            bookSyncId = "book",
            book = SyncBook(bookUrl = "url", group = 1L, groupSyncIds = listOf("group-a")),
            shelfUpdatedAt = 1L,
            catalogUpdatedAt = 1L,
            updatedByDeviceId = "a"
        )
        val second = first.copy(book = first.book.copy(group = 8L, order = 42))

        assertEquals(SyncPayloadHash.book(first), SyncPayloadHash.book(second))
    }

    @Test
    fun groupHashIgnoresDeviceLocalLegacyBit() {
        val first = SyncBookGroupPayload(
            groupSyncId = "group-a",
            legacyGroupId = 1L,
            groupName = "A",
            cover = null,
            order = 0,
            enableRefresh = true,
            show = true,
            bookSort = -1,
            onlyUpdateRead = false,
            updatedAt = 1L,
            updatedByDeviceId = "a"
        )

        assertEquals(
            SyncPayloadHash.bookGroup(first),
            SyncPayloadHash.bookGroup(first.copy(legacyGroupId = 16L, order = 42))
        )
    }
}
