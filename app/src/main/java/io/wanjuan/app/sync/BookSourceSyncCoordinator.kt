package io.wanjuan.app.sync

import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.BookSourcePart
import io.wanjuan.app.help.AppCacheManager
import io.wanjuan.app.help.config.DiscoverSourceUseConfig
import io.wanjuan.app.help.config.SourceConfig
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.mapper.BookSourceSyncMapper
import io.wanjuan.app.sync.merge.SyncMerge
import io.wanjuan.app.sync.model.SyncBookSource
import io.wanjuan.app.sync.model.SyncBookSourcePayload
import io.wanjuan.app.sync.model.SyncDeleteKeyPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.remote.WebDavSyncClient

class BookSourceSyncCoordinator(
    private val client: WebDavSyncClient,
    private val repository: SyncRepository,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String
) {

    fun enqueueSource(source: BookSource) {
        val payload = BookSourceSyncMapper.toPayload(source, deviceIdProvider(), clock.now())
        repository.markDirty(SyncObjectType.BookSource, payload.sourceHash, payload, "upsert")
    }

    fun enqueueDelete(sourceUrl: String) {
        val id = SyncIds.hashKey("book-source", sourceUrl)
        repository.markDirty(SyncObjectType.BookSource, id, SyncDeleteKeyPayload(sourceUrl), "delete")
    }

    fun enqueueOrder(items: List<BookSourcePart>) {
        val payload = SyncOrderPayload(
            updatedAt = clock.now(),
            updatedByDeviceId = deviceIdProvider(),
            items = items.sortedBy { it.customOrder }.map {
                SyncIds.hashKey("book-source", it.bookSourceUrl)
            }
        )
        repository.markDirty(SyncObjectType.BookSourceOrder, "bookSources", payload, "order")
    }

    suspend fun pushSource(source: BookSource) {
        val payload = BookSourceSyncMapper.toPayload(source, deviceIdProvider(), clock.now())
        recordLocalSourceClock(payload)
        client.upload("bookSources/${payload.sourceHash}.json", payload)
    }

    fun applyRemoteSource(payload: SyncBookSourcePayload) {
        var clearSourceVariables = false
        repository.applyRemote {
            appDb.runInTransaction {
                val dao = appDb.bookSourceDao
                val metadataDao = appDb.syncMetadataDao
                val metadata = metadataDao.get(SyncObjectType.BookSource, payload.sourceHash)
                val local = dao.getBookSource(payload.bookSourceUrl)
                val localUpdatedAt = maxOf(
                    local?.lastUpdateTime ?: 0L,
                    metadata?.localUpdatedAt ?: 0L,
                    metadata?.remoteUpdatedAt ?: 0L
                )
                if (SyncMerge.remoteWins(payload.sourceUpdatedAt, metadata?.deletedAt ?: 0L)) {
                    return@runInTransaction
                }

                val hasLocalClock = local != null || metadata != null
                if (!hasLocalClock || SyncMerge.remoteWins(localUpdatedAt, payload.sourceUpdatedAt)) {
                    val source = payload.bookSource.toBookSource().apply {
                        bookSourceUrl = payload.bookSourceUrl
                        lastUpdateTime = payload.sourceUpdatedAt
                    }
                    dao.insert(source)
                    appDb.cacheDao.deleteSourceVariables(source.bookSourceUrl)
                    clearSourceVariables = true
                    metadataDao.insert(
                        metadata.withRemoteClock(
                            objectId = payload.sourceHash,
                            remoteUpdatedAt = payload.sourceUpdatedAt,
                            updatedByDeviceId = payload.updatedByDeviceId
                        )
                    )
                }
            }
        }
        if (clearSourceVariables) {
            AppCacheManager.clearSourceVariables()
        }
    }

    suspend fun pushOrder(items: List<BookSourcePart>) {
        val payload = SyncOrderPayload(
            updatedAt = clock.now(),
            updatedByDeviceId = deviceIdProvider(),
            items = items.sortedBy { it.customOrder }.map {
                SyncIds.hashKey("book-source", it.bookSourceUrl)
            }
        )
        recordLocalOrderClock(payload)
        client.upload("order/bookSources.json", payload)
    }

    suspend fun pushDelete(sourceUrl: String) {
        pushDeleteById(
            id = SyncIds.hashKey("book-source", sourceUrl),
            sourceUrl = sourceUrl
        )
    }

    suspend fun pushDeleteById(id: String, sourceUrl: String? = null) {
        val deletedAt = clock.now()
        val deviceId = deviceIdProvider()
        recordLocalDeleteClock(
            id = id,
            deletedAt = deletedAt,
            deletedByDeviceId = deviceId
        )
        val payload = SyncTombstonePayload(
            objectType = SyncObjectType.BookSource,
            objectId = id,
            deletedAt = deletedAt,
            deletedByDeviceId = deviceId,
            objectKey = sourceUrl
        )
        client.upload("tombstones/bookSources/$id.json", payload)
    }

    fun applyRemoteDelete(payload: SyncTombstonePayload, sourceUrl: String? = null) {
        if (payload.objectType != SyncObjectType.BookSource) return
        var deletedSourceUrl: String? = null
        repository.applyRemote {
            appDb.runInTransaction {
                val metadataDao = appDb.syncMetadataDao
                val metadata = metadataDao.get(SyncObjectType.BookSource, payload.objectId)
                val localSourceUrl = sourceUrl
                    ?.takeIf { SyncIds.hashKey("book-source", it) == payload.objectId }
                    ?: payload.objectKey
                        ?.takeIf { SyncIds.hashKey("book-source", it) == payload.objectId }
                    ?: findSourceUrlByHash(payload.objectId)
                val source = localSourceUrl?.let { appDb.bookSourceDao.getBookSource(it) }
                val objectUpdatedAt = maxOf(
                    source?.lastUpdateTime ?: 0L,
                    metadata?.localUpdatedAt ?: 0L,
                    metadata?.remoteUpdatedAt ?: 0L
                )
                if (!SyncMerge.remoteWins(objectUpdatedAt, payload.deletedAt)) {
                    return@runInTransaction
                }
                if (localSourceUrl != null) {
                    deleteLocalSourceRows(localSourceUrl)
                    deletedSourceUrl = localSourceUrl
                }
                metadataDao.insert(
                    metadata.withRemoteDelete(
                        objectId = payload.objectId,
                        deletedAt = payload.deletedAt,
                        deletedByDeviceId = payload.deletedByDeviceId
                    )
                )
            }
        }
        deletedSourceUrl?.let { clearLocalSourceConfig(it) }
    }

    private fun deleteLocalSourceRows(sourceUrl: String) {
        appDb.bookSourceDao.delete(sourceUrl)
        appDb.cacheDao.deleteSourceVariables(sourceUrl)
    }

    private fun clearLocalSourceConfig(sourceUrl: String) {
        SourceConfig.removeSource(sourceUrl)
        DiscoverSourceUseConfig.removeSource(sourceUrl)
        AppCacheManager.clearSourceVariables()
    }

    private fun findSourceUrlByHash(sourceHash: String): String? {
        return appDb.bookSourceDao.allPart
            .firstOrNull { SyncIds.hashKey("book-source", it.bookSourceUrl) == sourceHash }
            ?.bookSourceUrl
    }

    private fun recordLocalSourceClock(payload: SyncBookSourcePayload) {
        appDb.runInTransaction {
            val metadataDao = appDb.syncMetadataDao
            val metadata = metadataDao.get(SyncObjectType.BookSource, payload.sourceHash)
            metadataDao.insert(
                metadata.withLocalClock(
                    objectId = payload.sourceHash,
                    localUpdatedAt = payload.sourceUpdatedAt,
                    updatedByDeviceId = payload.updatedByDeviceId
                )
            )
        }
    }

    private fun recordLocalOrderClock(payload: SyncOrderPayload) {
        appDb.runInTransaction {
            val metadataDao = appDb.syncMetadataDao
            val metadata = metadataDao.get(SyncObjectType.BookSourceOrder, "bookSources")
            metadataDao.insert(
                metadata?.let {
                    it.copy(
                        localUpdatedAt = maxOf(it.localUpdatedAt, payload.updatedAt),
                        updatedByDeviceId = payload.updatedByDeviceId
                    )
                } ?: SyncMetadata(
                    objectType = SyncObjectType.BookSourceOrder,
                    objectId = "bookSources",
                    localUpdatedAt = payload.updatedAt,
                    updatedByDeviceId = payload.updatedByDeviceId
                )
            )
        }
    }

    private fun recordLocalDeleteClock(
        id: String,
        deletedAt: Long,
        deletedByDeviceId: String
    ) {
        appDb.runInTransaction {
            val metadataDao = appDb.syncMetadataDao
            val metadata = metadataDao.get(SyncObjectType.BookSource, id)
            metadataDao.insert(
                metadata.withLocalDelete(
                    objectId = id,
                    deletedAt = deletedAt,
                    deletedByDeviceId = deletedByDeviceId
                )
            )
        }
    }

    private fun SyncMetadata?.withRemoteClock(
        objectId: String,
        remoteUpdatedAt: Long,
        updatedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            remoteUpdatedAt = maxOf(this.remoteUpdatedAt, remoteUpdatedAt),
            deletedAt = null,
            updatedByDeviceId = updatedByDeviceId
        ) ?: SyncMetadata(
            objectType = SyncObjectType.BookSource,
            objectId = objectId,
            remoteUpdatedAt = remoteUpdatedAt,
            updatedByDeviceId = updatedByDeviceId
        )
    }

    private fun SyncMetadata?.withLocalClock(
        objectId: String,
        localUpdatedAt: Long,
        updatedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            localUpdatedAt = maxOf(this.localUpdatedAt, localUpdatedAt),
            deletedAt = null,
            updatedByDeviceId = updatedByDeviceId
        ) ?: SyncMetadata(
            objectType = SyncObjectType.BookSource,
            objectId = objectId,
            localUpdatedAt = localUpdatedAt,
            updatedByDeviceId = updatedByDeviceId
        )
    }

    private fun SyncMetadata?.withRemoteDelete(
        objectId: String,
        deletedAt: Long,
        deletedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            remoteUpdatedAt = maxOf(this.remoteUpdatedAt, deletedAt),
            deletedAt = maxOf(this.deletedAt ?: 0L, deletedAt),
            updatedByDeviceId = deletedByDeviceId
        ) ?: SyncMetadata(
            objectType = SyncObjectType.BookSource,
            objectId = objectId,
            remoteUpdatedAt = deletedAt,
            deletedAt = deletedAt,
            updatedByDeviceId = deletedByDeviceId
        )
    }

    private fun SyncMetadata?.withLocalDelete(
        objectId: String,
        deletedAt: Long,
        deletedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            localUpdatedAt = maxOf(this.localUpdatedAt, deletedAt),
            deletedAt = maxOf(this.deletedAt ?: 0L, deletedAt),
            updatedByDeviceId = deletedByDeviceId
        ) ?: SyncMetadata(
            objectType = SyncObjectType.BookSource,
            objectId = objectId,
            localUpdatedAt = deletedAt,
            deletedAt = deletedAt,
            updatedByDeviceId = deletedByDeviceId
        )
    }

    private fun SyncBookSource.toBookSource(): BookSource {
        return BookSource(
            bookSourceUrl = bookSourceUrl,
            bookSourceName = bookSourceName,
            bookSourceGroup = bookSourceGroup,
            bookSourceType = bookSourceType,
            bookUrlPattern = bookUrlPattern,
            customOrder = customOrder,
            enabled = enabled,
            enabledExplore = enabledExplore,
            jsLib = jsLib,
            enabledCookieJar = enabledCookieJar,
            concurrentRate = concurrentRate,
            header = header,
            loginUrl = loginUrl,
            loginUi = loginUi,
            loginCheckJs = loginCheckJs,
            coverDecodeJs = coverDecodeJs,
            bookSourceComment = bookSourceComment,
            variableComment = variableComment,
            lastUpdateTime = lastUpdateTime,
            respondTime = respondTime,
            weight = weight,
            preDownloadNum = preDownloadNum,
            exploreUrl = exploreUrl,
            exploreScreen = exploreScreen,
            ruleExplore = ruleExplore,
            searchUrl = searchUrl,
            ruleSearch = ruleSearch,
            ruleBookInfo = ruleBookInfo,
            ruleToc = ruleToc,
            ruleContent = ruleContent,
            ruleReview = ruleReview,
            eventListener = eventListener,
            customButton = customButton
        )
    }
}
