package io.wanjuan.app.sync

import io.wanjuan.app.sync.merge.BookSyncMerge
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TestBookComponentMerge {
    @Test
    fun newerCatalogCannotRollbackNewerRemoteProgress() {
        val local = payload("a", shelf = 100, catalog = 400, progress = 100).copy(
            book = SyncBook(bookUrl = "book", totalChapterNum = 150, durChapterIndex = 79, syncTime = 100)
        )
        val remote = payload("b", shelf = 100, catalog = 200, progress = 300).copy(
            book = SyncBook(bookUrl = "book", totalChapterNum = 120, durChapterIndex = 99, syncTime = 300)
        )

        val result = BookSyncMerge.merge(local, remote)

        assertEquals(150, result.book.totalChapterNum)
        assertEquals(99, result.book.durChapterIndex)
        assertEquals(300L, result.book.syncTime)
        assertEquals("a", result.catalogUpdatedByDeviceId)
        assertEquals("b", result.progressUpdatedByDeviceId)
    }

    @Test
    fun newProgressDoesNotOverwriteNewerGroupsAndTags() {
        val local = payload("a", 400, 100, 100).copy(
            book = SyncBook(groupSyncIds = listOf("favorites"), customTag = "keep", order = 7)
        )
        val remote = payload("b", 200, 300, 500).copy(
            book = SyncBook(groupSyncIds = emptyList(), durChapterIndex = 12, syncTime = 500)
        )

        val result = BookSyncMerge.merge(local, remote)

        assertEquals(listOf("favorites"), result.book.groupSyncIds)
        assertEquals("keep", result.book.customTag)
        assertEquals(12, result.book.durChapterIndex)
        assertEquals(7, result.book.order)
        assertEquals(400L, result.shelfUpdatedAt)
    }

    @Test
    fun componentDeviceIdsSurviveRoundTripAndMakeTiesDeterministic() {
        val local = payload("a", 200, 300, 100)
        val remote = payload("b", 200, 100, 300)
        val merged = BookSyncMerge.merge(local, remote)
        val decoded = GSON.fromJsonObject<SyncBookPayload>(GSON.toJson(merged)).getOrThrow()

        assertEquals("b", decoded.shelfUpdatedByDeviceId)
        assertEquals("a", decoded.catalogUpdatedByDeviceId)
        assertEquals("b", decoded.progressUpdatedByDeviceId)
        assertEquals(BookSyncMerge.version(merged), BookSyncMerge.version(decoded))
    }

    @Test
    fun componentFingerprintsIgnoreUnrelatedChangesAndLocalGroupBits() {
        val base = SyncBook(group = 1, groupSyncIds = listOf("g"), syncTime = 100)
        assertEquals(BookSyncMerge.shelfHash(base), BookSyncMerge.shelfHash(
            base.copy(group = 8, order = 9, totalChapterNum = 100, durChapterIndex = 2, syncTime = 200)
        ))
        assertEquals(BookSyncMerge.progressHash(base), BookSyncMerge.progressHash(base.copy(durChapterTime = 900)))
        assertNotEquals(BookSyncMerge.shelfHash(base), BookSyncMerge.shelfHash(base.copy(customTag = "new")))
        assertNotEquals(BookSyncMerge.catalogHash(base), BookSyncMerge.catalogHash(base.copy(totalChapterNum = 100)))
    }

    private fun payload(device: String, shelf: Long, catalog: Long, progress: Long) =
        SyncBookPayload("book-id", SyncBook(syncTime = progress), shelf, catalog, progress, device)
}
