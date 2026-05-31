package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookSourceSyncSourceTest {

    private val source = File("app/src/main/java/io/wanjuan/app/sync/BookSourceSyncCoordinator.kt").readText()

    @Test
    fun sourceSyncHasSourceOrderAndTombstonePaths() {
        assertTrue(source.contains("bookSources/"))
        assertTrue(source.contains("order/bookSources.json"))
        assertTrue(source.contains("tombstones/bookSources/"))
        assertTrue(source.contains("sourceUpdatedAt"))
    }

    @Test
    fun sourceSyncUsesDtoMapperAndRoomEntityInsert() {
        assertTrue(source.contains("SyncBookSource"))
        assertTrue(source.contains("toBookSource()"))
        assertTrue(source.contains("bookSourceName = bookSourceName"))
        assertTrue(source.contains("ruleSearch = ruleSearch"))
        assertTrue(source.contains("ruleContent = ruleContent"))
        assertTrue(source.contains("dao.insert(source)"))
        assertFalse(source.contains("bookSource: BookSource"))
    }

    @Test
    fun sourceSyncUsesStableIdsAndCacheOnlyDeletion() {
        assertTrue(source.contains("SyncIds.hashKey(\"book-source\", sourceUrl)"))
        assertTrue(source.contains("items.sortedBy { it.customOrder }"))
        assertTrue(source.contains("appDb.cacheDao.deleteSourceVariables(sourceUrl)"))
        assertTrue(source.contains("AppCacheManager.clearSourceVariables()"))
        assertTrue(source.contains("SourceConfig.removeSource(sourceUrl)"))
        assertTrue(source.contains("DiscoverSourceUseConfig.removeSource(sourceUrl)"))
        assertTrue(source.contains("SyncTombstonePayload("))
        assertTrue(source.contains("objectType = SyncObjectType.BookSource"))
        assertTrue(source.contains("recordLocalDeleteClock"))
        assertTrue(source.contains("recordLocalOrderClock"))
        assertTrue(source.contains("SyncObjectType.BookSourceOrder"))
        assertTrue(source.contains("if (payload.objectType != SyncObjectType.BookSource) return"))
        assertTrue(source.contains("applyRemoteDelete(payload: SyncTombstonePayload, sourceUrl: String? = null)"))
        assertTrue(source.contains("payload.deletedAt"))
        assertTrue(source.contains("objectKey = sourceUrl"))
        assertTrue(source.contains("payload.objectKey"))
        assertTrue(source.contains("findSourceUrlByHash(payload.objectId)"))
        assertTrue(source.contains("metadata?.deletedAt"))
        assertFalse(source.contains("bookDao"))
        assertFalse(source.contains("rss"))
        assertFalse(source.contains("Rss"))
    }
}
