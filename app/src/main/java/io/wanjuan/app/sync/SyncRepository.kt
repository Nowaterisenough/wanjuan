package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.local.SyncOutbox
import io.wanjuan.app.sync.model.SyncBookGroupPayload
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncBookSourcePayload
import io.wanjuan.app.sync.model.SyncDeleteKeyPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.model.SyncRssSourcePayload
import io.wanjuan.app.sync.model.SyncRuleSubPayload
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.remote.SyncRemoteStore
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SyncRepository(
    private val db: AppDatabase,
    private val client: WebDavSyncClient,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String
) {

    fun markDirty(objectType: String, objectId: String, payload: Any, operation: String) {
        if (SyncScope.isApplyingRemote) return
        val now = clock.now()
        val deviceId = deviceIdProvider()
        val payloadJson = GSON.toJson(payload)
        db.runInTransaction {
            val metadata = db.syncMetadataDao.get(objectType, objectId)?.copy(
                localUpdatedAt = now,
                dirty = true,
                updatedByDeviceId = deviceId,
                localUpdatedByDeviceId = deviceId
            ) ?: SyncMetadata(
                objectType = objectType,
                objectId = objectId,
                localUpdatedAt = now,
                dirty = true,
                updatedByDeviceId = deviceId,
                localUpdatedByDeviceId = deviceId
            )
            db.syncMetadataDao.insert(metadata)
            db.syncOutboxDao.deleteForObject(objectType, objectId)
            db.syncOutboxDao.insert(
                SyncOutbox(
                    objectType = objectType,
                    objectId = objectId,
                    operation = operation,
                    payloadJson = payloadJson,
                    createdAt = now,
                    versionTimestamp = now,
                    versionDeviceId = deviceId
                )
            )
        }
    }

    suspend fun flushOutbox(upload: suspend (SyncOutbox) -> Unit) {
        drainOutbox { item ->
            currentCoroutineContext().ensureActive()
            try {
                upload(item)
                db.syncOutboxDao.delete(item.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                db.syncOutboxDao.markFailed(item.id, e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
    }

    suspend fun flushOutbox(remoteStore: SyncRemoteStore, result: SyncResult.Mutable) {
        drainOutbox { item ->
            currentCoroutineContext().ensureActive()
            try {
                val json = item.payloadJsonForUpload(deviceIdProvider)
                remoteStore.uploadJson(item.remotePath(), json)
                if (item.operation != "delete" && remoteStore is WebDavSyncClient) {
                    item.remoteTombstonePath()?.let { path ->
                        runCatching { remoteStore.delete(path) }
                    }
                }
                db.runInTransaction {
                    val isLatest = db.syncOutboxDao.latestForObject(item.objectType, item.objectId)?.id == item.id
                    db.syncOutboxDao.delete(item.id)
                    if (isLatest) db.syncMetadataDao.markClean(
                        item.objectType,
                        item.objectId,
                        if (item.operation == "delete") null else item.contentHash()
                    )
                }
                result.uploaded += 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                db.syncOutboxDao.markFailed(item.id, e.localizedMessage ?: e.javaClass.simpleName)
                result.fail(e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
        result.pending = db.syncOutboxDao.count()
    }

    private suspend fun drainOutbox(action: suspend (SyncOutbox) -> Unit) {
        // A fixed watermark processes every existing item once, including after a failed batch.
        val throughId = db.syncOutboxDao.lastId()
        var afterId = 0L
        while (true) {
            val batch = db.syncOutboxDao.pendingAfter(afterId, throughId)
            if (batch.isEmpty()) break
            for (item in batch) {
                currentCoroutineContext().ensureActive()
                if (db.syncOutboxDao.latestForObject(item.objectType, item.objectId)?.id == item.id) {
                    action(item)
                } else {
                    db.syncOutboxDao.delete(item.id)
                }
                afterId = item.id
            }
        }
    }

    suspend fun ensureRemoteReady() {
        client.ensureDirs()
    }

    fun <T> applyRemote(block: () -> T): T = SyncScope.remoteApply(block)

    private fun SyncOutbox.remotePath(): String = when (objectType) {
        SyncObjectType.Book -> if (operation == "delete") "tombstones/books/$objectId.json" else "books/$objectId.json"
        SyncObjectType.BookGroup -> if (operation == "delete") "tombstones/bookGroups/$objectId.json" else "bookGroups/$objectId.json"
        SyncObjectType.BookSource -> if (operation == "delete") "tombstones/bookSources/$objectId.json" else "bookSources/$objectId.json"
        SyncObjectType.RssSource -> if (operation == "delete") "tombstones/rssSources/$objectId.json" else "rssSources/$objectId.json"
        SyncObjectType.RuleSub -> if (operation == "delete") "tombstones/ruleSubs/$objectId.json" else "ruleSubs/$objectId.json"
        SyncObjectType.BookshelfOrder -> "order/bookshelf.json"
        SyncObjectType.BookGroupOrder -> "order/bookGroups.json"
        SyncObjectType.BookSourceOrder -> "order/bookSources.json"
        SyncObjectType.RssSourceOrder -> "order/rssSources.json"
        SyncObjectType.RuleSubOrder -> "order/ruleSubs.json"
        else -> error("Unsupported sync object type: $objectType")
    }

    private fun SyncOutbox.remoteTombstonePath(): String? = when (objectType) {
        SyncObjectType.Book -> "tombstones/books/$objectId.json"
        SyncObjectType.BookGroup -> "tombstones/bookGroups/$objectId.json"
        SyncObjectType.BookSource -> "tombstones/bookSources/$objectId.json"
        SyncObjectType.RssSource -> "tombstones/rssSources/$objectId.json"
        SyncObjectType.RuleSub -> "tombstones/ruleSubs/$objectId.json"
        else -> null
    }

    private fun SyncOutbox.contentHash(): String {
        val json = requireNotNull(payloadJson)
        return when (objectType) {
            SyncObjectType.Book -> SyncPayloadHash.book(
                GSON.fromJsonObject<SyncBookPayload>(json).getOrThrow()
            )
            SyncObjectType.BookGroup -> SyncPayloadHash.bookGroup(
                GSON.fromJsonObject<SyncBookGroupPayload>(json).getOrThrow()
            )
            SyncObjectType.BookSource -> SyncPayloadHash.bookSource(
                GSON.fromJsonObject<SyncBookSourcePayload>(json).getOrThrow()
            )
            SyncObjectType.RssSource -> SyncPayloadHash.rssSource(
                GSON.fromJsonObject<SyncRssSourcePayload>(json).getOrThrow()
            )
            SyncObjectType.RuleSub -> SyncPayloadHash.ruleSub(
                GSON.fromJsonObject<SyncRuleSubPayload>(json).getOrThrow()
            )
            SyncObjectType.BookshelfOrder,
            SyncObjectType.BookGroupOrder,
            SyncObjectType.BookSourceOrder,
            SyncObjectType.RssSourceOrder,
            SyncObjectType.RuleSubOrder -> SyncPayloadHash.order(
                GSON.fromJsonObject<SyncOrderPayload>(json).getOrThrow()
            )
            else -> error("Unsupported sync hash type: $objectType")
        }
    }
}

internal fun SyncOutbox.payloadJsonForUpload(deviceIdProvider: () -> String): String {
    val json = payloadJson?.takeIf { it.isNotBlank() }
    if (operation != "delete") {
        return requireNotNull(json) {
            "Missing sync payload: $objectType/$objectId"
        }
    }

    val tombstone = json?.let {
        runCatching { GSON.fromJsonObject<SyncTombstonePayload>(it).getOrThrow() }.getOrNull()
    }
    val isValidTombstone = runCatching {
        tombstone != null &&
            tombstone.objectType == objectType &&
            tombstone.objectId == objectId &&
            tombstone.deletedAt > 0L &&
            tombstone.deletedByDeviceId.isNotBlank()
    }.getOrDefault(false)
    if (isValidTombstone) return requireNotNull(json)

    val legacyObjectKey = json?.let {
        runCatching {
            GSON.fromJsonObject<SyncDeleteKeyPayload>(it).getOrThrow().key.takeIf(String::isNotBlank)
        }.getOrNull()
    }
    return GSON.toJson(
        SyncTombstonePayload(
            objectType = objectType,
            objectId = objectId,
            deletedAt = versionTimestamp.takeIf { it > 0L } ?: createdAt,
            deletedByDeviceId = versionDeviceId.takeIf(String::isNotBlank) ?: deviceIdProvider(),
            objectKey = legacyObjectKey
        )
    )
}
