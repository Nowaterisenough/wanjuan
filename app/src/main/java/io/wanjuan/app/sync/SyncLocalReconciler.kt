package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.local.SyncOutbox
import io.wanjuan.app.sync.model.SyncSnapshot
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.utils.GSON

fun interface SyncLocalSnapshotSource {
    fun currentSnapshots(): List<SyncSnapshot>
}

interface SyncReconcileStore {
    fun metadata(objectType: String, objectId: String): SyncMetadata?
    fun metadataForType(objectType: String): List<SyncMetadata>
    fun saveMetadata(metadata: SyncMetadata)
    fun replaceOutbox(item: SyncOutbox)
    fun runInTransaction(block: () -> Unit) = block()
}

data class SyncCaptureResult(
    val upserts: Int,
    val deletes: Int,
    val unchanged: Int
)

class SyncLocalReconciler(
    private val snapshotSource: SyncLocalSnapshotSource,
    private val store: SyncReconcileStore,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String,
    private val managedObjectTypes: Set<String>
) {

    fun capture(): SyncCaptureResult {
        var upserts = 0
        var deletes = 0
        var unchanged = 0
        store.runInTransaction {
            val snapshots = snapshotSource.currentSnapshots()
            val snapshotsByType = snapshots.groupBy { it.objectType }
            for (objectType in managedObjectTypes) {
                val current = snapshotsByType[objectType].orEmpty()
                val currentIds = current.mapTo(hashSetOf()) { it.objectId }
                current.forEach { snapshot ->
                    val metadata = store.metadata(snapshot.objectType, snapshot.objectId)
                    if (
                        metadata != null &&
                        !metadata.dirty &&
                        metadata.deletedAt == null &&
                        metadata.lastSyncedHash == snapshot.contentHash
                    ) {
                        unchanged += 1
                    } else {
                        queueUpsert(snapshot, metadata)
                        upserts += 1
                    }
                }
                store.metadataForType(objectType)
                    .asSequence()
                    .filter { it.objectId !in currentIds && it.deletedAt == null }
                    .forEach { metadata ->
                        queueDelete(metadata)
                        deletes += 1
                    }
            }
        }
        return SyncCaptureResult(upserts, deletes, unchanged)
    }

    private fun queueUpsert(snapshot: SyncSnapshot, metadata: SyncMetadata?) {
        store.replaceOutbox(
            SyncOutbox(
                objectType = snapshot.objectType,
                objectId = snapshot.objectId,
                operation = "upsert",
                payloadJson = snapshot.payloadJson,
                createdAt = snapshot.version.timestamp,
                versionTimestamp = snapshot.version.timestamp,
                versionDeviceId = snapshot.version.deviceId
            )
        )
        store.saveMetadata(
            metadata?.copy(
                localUpdatedAt = snapshot.version.timestamp,
                dirty = true,
                deletedAt = null,
                updatedByDeviceId = snapshot.version.deviceId,
                localUpdatedByDeviceId = snapshot.version.deviceId,
                deletedByDeviceId = null
            ) ?: SyncMetadata(
                objectType = snapshot.objectType,
                objectId = snapshot.objectId,
                localUpdatedAt = snapshot.version.timestamp,
                dirty = true,
                updatedByDeviceId = snapshot.version.deviceId,
                localUpdatedByDeviceId = snapshot.version.deviceId
            )
        )
    }

    private fun queueDelete(metadata: SyncMetadata) {
        val version = SyncVersion(clock.now(), deviceIdProvider())
        val tombstone = SyncTombstonePayload(
            objectType = metadata.objectType,
            objectId = metadata.objectId,
            deletedAt = version.timestamp,
            deletedByDeviceId = version.deviceId
        )
        store.replaceOutbox(
            SyncOutbox(
                objectType = metadata.objectType,
                objectId = metadata.objectId,
                operation = "delete",
                payloadJson = GSON.toJson(tombstone),
                createdAt = version.timestamp,
                versionTimestamp = version.timestamp,
                versionDeviceId = version.deviceId
            )
        )
        store.saveMetadata(
            metadata.copy(
                localUpdatedAt = version.timestamp,
                deletedAt = version.timestamp,
                dirty = true,
                updatedByDeviceId = version.deviceId,
                localUpdatedByDeviceId = version.deviceId,
                deletedByDeviceId = version.deviceId
            )
        )
    }
}

class RoomSyncReconcileStore(
    private val db: AppDatabase
) : SyncReconcileStore {
    override fun metadata(objectType: String, objectId: String): SyncMetadata? =
        db.syncMetadataDao.get(objectType, objectId)

    override fun metadataForType(objectType: String): List<SyncMetadata> =
        db.syncMetadataDao.allForType(objectType)

    override fun saveMetadata(metadata: SyncMetadata) {
        db.syncMetadataDao.insert(metadata)
    }

    override fun replaceOutbox(item: SyncOutbox) {
        val previous = db.syncOutboxDao.latestForObject(item.objectType, item.objectId)
        if (previous?.operation == item.operation && previous.payloadJson == item.payloadJson) return
        db.syncOutboxDao.deleteForObject(item.objectType, item.objectId)
        db.syncOutboxDao.insert(item)
    }

    override fun runInTransaction(block: () -> Unit) {
        db.runInTransaction(block)
    }
}
