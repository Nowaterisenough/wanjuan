package io.wanjuan.app.sync

import io.wanjuan.app.data.entities.RssSource
import io.wanjuan.app.sync.mapper.RssSourceSyncMapper
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.model.SyncVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSourceSyncBehavior {

    @Test
    fun rssSourceSupportsInsertUpdateDeleteAndCleanup() {
        val store = MemoryRssSourceStore()
        val applier = RssSourceSyncApplier(store)
        val initial = RssSource(
            sourceUrl = "https://rss.example",
            sourceName = "Initial",
            customOrder = 7
        )
        val updated = initial.copy(sourceName = "Updated", enabled = false)

        assertEquals(
            SyncApplyOutcome.Inserted,
            applier.applyRemote(RssSourceSyncMapper.toPayload(initial, SyncVersion(10L, "a")))
        )
        assertEquals(
            SyncApplyOutcome.Updated,
            applier.applyRemote(RssSourceSyncMapper.toPayload(updated, SyncVersion(20L, "a")))
        )
        assertEquals("Updated", store.sources.single().sourceName)
        assertFalse(store.sources.single().enabled)
        store.deletedCacheKeys.clear()
        store.memoryCacheCleared = false

        assertEquals(
            SyncApplyOutcome.Deleted,
            applier.applyRemoteDelete(
                SyncTombstonePayload(
                    objectType = SyncObjectType.RssSource,
                    objectId = SyncIds.rssSourceId(initial.sourceUrl),
                    objectKey = initial.sourceUrl,
                    deletedAt = 30L,
                    deletedByDeviceId = "a"
                )
            )
        )
        assertNull(store.sources.firstOrNull())
        assertEquals(listOf(initial.sourceUrl), store.deletedArticleOrigins)
        assertEquals(listOf(initial.sourceUrl), store.deletedCacheKeys)
        assertTrue(store.memoryCacheCleared)
    }

    @Test
    fun rssOrderAppendsLocalExtraItems() {
        val store = MemoryRssSourceStore(
            mutableListOf(
                RssSource(sourceUrl = "a", customOrder = 0),
                RssSource(sourceUrl = "b", customOrder = 1),
                RssSource(sourceUrl = "local", customOrder = 2)
            )
        )
        val applier = RssSourceSyncApplier(store)

        applier.applyRemoteOrder(
            SyncOrderPayload(
                updatedAt = 10L,
                updatedByDeviceId = "remote",
                items = listOf(SyncIds.rssSourceId("b"), SyncIds.rssSourceId("a"))
            )
        )

        assertEquals(
            listOf("b", "a", "local"),
            store.sources.sortedBy { it.customOrder }.map { it.sourceUrl }
        )
        assertEquals(listOf(0, 1, 2), store.sources.sortedBy { it.customOrder }.map { it.customOrder })
    }

    @Test
    fun sharedSourceOrderMergeKeepsUnknownRemoteIdsOutAndLocalExtrasAtEnd() {
        data class Item(val id: String, val order: Int)
        val merged = StableSyncOrder.merge(
            remoteIds = listOf("b", "missing", "a"),
            localItems = listOf(Item("a", 0), Item("b", 2), Item("local", 1)),
            idOf = Item::id,
            orderOf = Item::order
        )

        assertEquals(listOf("b", "a", "local"), merged.map { it.id })
    }

    private class MemoryRssSourceStore(
        val sources: MutableList<RssSource> = mutableListOf()
    ) : RssSourceSyncStore {
        val deletedArticleOrigins = mutableListOf<String>()
        val deletedCacheKeys = mutableListOf<String>()
        var memoryCacheCleared = false

        override fun allSources(): List<RssSource> = sources.toList()

        override fun source(sourceUrl: String): RssSource? =
            sources.firstOrNull { it.sourceUrl == sourceUrl }

        override fun upsertSource(source: RssSource) {
            sources.removeAll { it.sourceUrl == source.sourceUrl }
            sources += source
        }

        override fun deleteSource(sourceUrl: String) {
            sources.removeAll { it.sourceUrl == sourceUrl }
        }

        override fun deleteArticles(sourceUrl: String) {
            deletedArticleOrigins += sourceUrl
        }

        override fun deleteSourceCache(sourceUrl: String) {
            deletedCacheKeys += sourceUrl
        }

        override fun clearMemoryCache() {
            memoryCacheCleared = true
        }
    }
}
