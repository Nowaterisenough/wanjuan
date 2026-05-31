package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.local.SyncOutbox
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SyncRepository(
    private val db: AppDatabase,
    private val client: WebDavSyncClient,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String
) {

    fun markDirty(objectType: String, objectId: String, payload: Any?, operation: String) {
        if (SyncScope.isApplyingRemote) return
        val now = clock.now()
        val deviceId = deviceIdProvider()
        val payloadJson = payload?.let { GSON.toJson(it) }
        db.runInTransaction {
            val metadata = db.syncMetadataDao.get(objectType, objectId)?.copy(
                localUpdatedAt = now,
                dirty = true,
                updatedByDeviceId = deviceId
            ) ?: SyncMetadata(
                objectType = objectType,
                objectId = objectId,
                localUpdatedAt = now,
                dirty = true,
                updatedByDeviceId = deviceId
            )
            db.syncMetadataDao.insert(metadata)
            db.syncOutboxDao.insert(
                SyncOutbox(
                    objectType = objectType,
                    objectId = objectId,
                    operation = operation,
                    payloadJson = payloadJson,
                    createdAt = now
                )
            )
        }
    }

    suspend fun flushOutbox(upload: suspend (SyncOutbox) -> Unit) {
        val items = db.syncOutboxDao.pending(50)
        for (item in items) {
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

    suspend fun ensureRemoteReady() {
        client.ensureDirs()
    }

    fun applyRemote(block: () -> Unit) {
        SyncScope.remoteApply(block)
    }
}
