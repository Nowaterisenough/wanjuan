package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.RssSource
import io.wanjuan.app.help.AppCacheManager
import io.wanjuan.app.sync.mapper.RssSourceSyncMapper
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncRssSourcePayload
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.sync.remote.WebDavSyncClient

interface RssSourceSyncStore {
    fun allSources(): List<RssSource>

    fun source(sourceUrl: String): RssSource?

    fun upsertSource(source: RssSource)

    fun deleteSource(sourceUrl: String)

    fun deleteArticles(sourceUrl: String)

    fun deleteSourceCache(sourceUrl: String)

    fun clearMemoryCache()

    fun runInTransaction(block: () -> Unit) = block()
}

class RoomRssSourceSyncStore(
    private val db: AppDatabase = appDb
) : RssSourceSyncStore {
    override fun allSources(): List<RssSource> = db.rssSourceDao.all

    override fun source(sourceUrl: String): RssSource? = db.rssSourceDao.getByKey(sourceUrl)

    override fun upsertSource(source: RssSource) = db.rssSourceDao.insert(source)

    override fun deleteSource(sourceUrl: String) = db.rssSourceDao.delete(sourceUrl)

    override fun deleteArticles(sourceUrl: String) = db.rssArticleDao.delete(sourceUrl)

    override fun deleteSourceCache(sourceUrl: String) = db.cacheDao.deleteSourceVariables(sourceUrl)

    override fun clearMemoryCache() = AppCacheManager.clearSourceVariables()

    override fun runInTransaction(block: () -> Unit) = db.runInTransaction(block)
}

class RssSourceSyncApplier(
    private val store: RssSourceSyncStore = RoomRssSourceSyncStore()
) {
    fun applyRemote(payload: SyncRssSourcePayload): SyncApplyOutcome {
        require(payload.sourceHash == SyncIds.rssSourceId(payload.sourceUrl)) {
            "RSS source hash does not match source URL"
        }
        val existed = store.source(payload.sourceUrl) != null
        store.runInTransaction {
            store.upsertSource(RssSourceSyncMapper.toEntity(payload))
            store.deleteSourceCache(payload.sourceUrl)
        }
        store.clearMemoryCache()
        return if (existed) SyncApplyOutcome.Updated else SyncApplyOutcome.Inserted
    }

    fun applyRemoteDelete(payload: SyncTombstonePayload): SyncApplyOutcome {
        require(payload.objectType == SyncObjectType.RssSource) {
            "Unexpected tombstone type: ${payload.objectType}"
        }
        val sourceUrl = payload.objectKey
            ?.takeIf { SyncIds.rssSourceId(it) == payload.objectId }
            ?: store.allSources()
                .firstOrNull { SyncIds.rssSourceId(it.sourceUrl) == payload.objectId }
                ?.sourceUrl
            ?: return SyncApplyOutcome.Skipped
        store.runInTransaction {
            store.deleteSource(sourceUrl)
            store.deleteArticles(sourceUrl)
            store.deleteSourceCache(sourceUrl)
        }
        store.clearMemoryCache()
        return SyncApplyOutcome.Deleted
    }

    fun applyRemoteOrder(payload: SyncOrderPayload): SyncApplyOutcome {
        store.runInTransaction {
            val ordered = StableSyncOrder.merge(
                remoteIds = payload.items,
                localItems = store.allSources(),
                idOf = { SyncIds.rssSourceId(it.sourceUrl) },
                orderOf = RssSource::customOrder
            )
            ordered.forEachIndexed { index, source ->
                if (source.customOrder != index) {
                    source.customOrder = index
                    store.upsertSource(source)
                }
            }
        }
        return SyncApplyOutcome.Updated
    }
}

class RssSourceSyncCoordinator(
    private val client: WebDavSyncClient,
    private val repository: SyncRepository,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String,
    private val applier: RssSourceSyncApplier = RssSourceSyncApplier()
) {
    fun enqueueSource(source: RssSource) {
        val payload = RssSourceSyncMapper.toPayload(
            source,
            SyncVersion(clock.now(), deviceIdProvider())
        )
        repository.markDirty(SyncObjectType.RssSource, payload.sourceHash, payload, "upsert")
    }

    fun enqueueDelete(source: RssSource) {
        val id = SyncIds.rssSourceId(source.sourceUrl)
        repository.markDirty(
            SyncObjectType.RssSource,
            id,
            SyncTombstonePayload(
                objectType = SyncObjectType.RssSource,
                objectId = id,
                objectKey = source.sourceUrl,
                deletedAt = clock.now(),
                deletedByDeviceId = deviceIdProvider()
            ),
            "delete"
        )
    }

    fun enqueueOrder(sources: List<RssSource>) {
        val payload = orderPayload(sources)
        repository.markDirty(SyncObjectType.RssSourceOrder, "rssSources", payload, "order")
    }

    suspend fun pushSource(source: RssSource) {
        val payload = RssSourceSyncMapper.toPayload(
            source,
            SyncVersion(clock.now(), deviceIdProvider())
        )
        client.upload("rssSources/${payload.sourceHash}.json", payload)
    }

    suspend fun pushDelete(source: RssSource) {
        val id = SyncIds.rssSourceId(source.sourceUrl)
        client.upload(
            "tombstones/rssSources/$id.json",
            SyncTombstonePayload(
                objectType = SyncObjectType.RssSource,
                objectId = id,
                objectKey = source.sourceUrl,
                deletedAt = clock.now(),
                deletedByDeviceId = deviceIdProvider()
            )
        )
    }

    suspend fun pushOrder(sources: List<RssSource>) {
        client.upload("order/rssSources.json", orderPayload(sources))
    }

    fun applyRemoteSource(payload: SyncRssSourcePayload): SyncApplyOutcome =
        repository.applyRemote { applier.applyRemote(payload) }

    fun applyRemoteDelete(payload: SyncTombstonePayload): SyncApplyOutcome =
        repository.applyRemote { applier.applyRemoteDelete(payload) }

    fun applyRemoteOrder(payload: SyncOrderPayload): SyncApplyOutcome =
        repository.applyRemote { applier.applyRemoteOrder(payload) }

    private fun orderPayload(sources: List<RssSource>): SyncOrderPayload = SyncOrderPayload(
        updatedAt = clock.now(),
        updatedByDeviceId = deviceIdProvider(),
        items = sources.sortedBy { it.customOrder }.map { SyncIds.rssSourceId(it.sourceUrl) }
    )
}
