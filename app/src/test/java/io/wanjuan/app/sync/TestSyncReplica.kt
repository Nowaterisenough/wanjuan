package io.wanjuan.app.sync

import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.local.SyncOutbox
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.model.SyncSnapshot
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.sync.remote.SyncRemoteFile
import io.wanjuan.app.sync.remote.SyncRemoteStore
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException

class TestSyncReplica(
    private val deviceId: String,
    private val remote: SyncRemoteStore
) {
    private data class Key(val type: String, val id: String)
    private data class Value(val text: String, val version: SyncVersion)
    private data class Payload(
        val objectType: String,
        val objectId: String,
        val value: String,
        val updatedAt: Long,
        val updatedByDeviceId: String
    )

    private val objects = linkedMapOf<Key, Value>()
    private val metadata = linkedMapOf<Key, SyncMetadata>()
    private val outbox = linkedMapOf<Key, SyncOutbox>()
    private val clock = MutableClock()
    private val managedTypes = setOf(
        "book",
        "bookSource",
        "rssSource",
        "ruleSub",
        "bookGroup"
    )
    private val stateStore = StateStore()
    private val reconciler = SyncLocalReconciler(
        snapshotSource = SyncLocalSnapshotSource { snapshots() },
        store = stateStore,
        clock = clock,
        deviceIdProvider = { deviceId },
        managedObjectTypes = managedTypes
    )
    private val pullEngine = SyncPullEngine(
        remoteStore = remote,
        pullStore = stateStore,
        handlers = managedTypes.map(::handler)
    )
    private val orchestrator = SyncOrchestrator(
        remoteStore = remote,
        captureAction = SyncCaptureAction { reconciler.capture() },
        pullAction = SyncPullAction(pullEngine::pullAll),
        flushAction = SyncFlushAction(::flush)
    )

    fun put(type: String, id: String, value: String, timestamp: Long) {
        require(type in managedTypes)
        clock.value = timestamp
        objects[Key(type, id)] = Value(value, SyncVersion(timestamp, deviceId))
    }

    fun remove(type: String, id: String, timestamp: Long) {
        clock.value = timestamp
        objects.remove(Key(type, id))
    }

    fun value(type: String, id: String): String? = objects[Key(type, id)]?.text

    fun contains(type: String, id: String): Boolean = Key(type, id) in objects

    fun values(): Map<String, String> = objects.entries.associate { (key, value) ->
        "${key.type}/${key.id}" to value.text
    }

    fun hasPending(type: String, id: String): Boolean = Key(type, id) in outbox

    suspend fun sync(): SyncResult = orchestrator.sync()

    private fun snapshots(): List<SyncSnapshot> = objects.map { (key, value) ->
        val payload = Payload(
            objectType = key.type,
            objectId = key.id,
            value = value.text,
            updatedAt = value.version.timestamp,
            updatedByDeviceId = value.version.deviceId
        )
        SyncSnapshot(
            objectType = key.type,
            objectId = key.id,
            contentHash = SyncCanonicalJson.hash(value.text),
            payloadJson = GSON.toJson(payload),
            version = value.version
        )
    }

    private fun handler(type: String): SyncPullHandler = object : SyncPullHandler {
        override val directories = listOf(directory(type), "tombstones/${directory(type)}")

        override fun identity(file: SyncRemoteFile): SyncIdentity? =
            file.displayName.removeSuffix(".json").takeIf { it.isNotBlank() }
                ?.let { SyncIdentity(type, it) }

        override fun parse(file: SyncRemoteFile, json: String): SyncRemoteCandidate {
            val identity = requireNotNull(identity(file))
            if (file.path.startsWith("tombstones/")) {
                val payload = GSON.fromJsonObject<SyncTombstonePayload>(json).getOrThrow()
                require(payload.objectType == type && payload.objectId == identity.objectId)
                return SyncRemoteCandidate(
                    identity = identity,
                    path = file.path,
                    contentHash = SyncCanonicalJson.hash(payload),
                    objectVersion = null,
                    deleteVersion = SyncVersion(payload.deletedAt, payload.deletedByDeviceId),
                    payloadJson = json,
                    lastModifiedAt = file.lastModifiedAt
                )
            }
            val payload = GSON.fromJsonObject<Payload>(json).getOrThrow()
            require(payload.objectType == type && payload.objectId == identity.objectId)
            return SyncRemoteCandidate(
                identity = identity,
                path = file.path,
                contentHash = SyncCanonicalJson.hash(payload.value),
                objectVersion = SyncVersion(payload.updatedAt, payload.updatedByDeviceId),
                deleteVersion = null,
                payloadJson = json,
                lastModifiedAt = file.lastModifiedAt
            )
        }

        override fun applyRemote(candidate: SyncRemoteCandidate): SyncApplyOutcome {
            val key = Key(candidate.identity.objectType, candidate.identity.objectId)
            if (candidate.deleteVersion != null) {
                val existed = objects.remove(key) != null
                return if (existed) SyncApplyOutcome.Deleted else SyncApplyOutcome.Skipped
            }
            val payload = GSON.fromJsonObject<Payload>(candidate.payloadJson).getOrThrow()
            val existed = key in objects
            objects[key] = Value(payload.value, requireNotNull(candidate.objectVersion))
            return if (existed) SyncApplyOutcome.Updated else SyncApplyOutcome.Inserted
        }
    }

    private suspend fun flush(result: SyncResult.Mutable) {
        outbox.values.toList().forEach { item ->
            try {
                remote.uploadJson(
                    if (item.operation == "delete") {
                        "tombstones/${directory(item.objectType)}/${item.objectId}.json"
                    } else {
                        "${directory(item.objectType)}/${item.objectId}.json"
                    },
                    requireNotNull(item.payloadJson)
                )
                val key = Key(item.objectType, item.objectId)
                outbox.remove(key)
                val old = metadata.getValue(key)
                metadata[key] = old.copy(
                    dirty = false,
                    lastSyncedHash = objects[key]?.let { SyncCanonicalJson.hash(it.text) }
                )
                result.uploaded += 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result.fail(e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
    }

    private fun directory(type: String): String = when (type) {
        "book" -> "books"
        "bookSource" -> "bookSources"
        "rssSource" -> "rssSources"
        "ruleSub" -> "ruleSubs"
        "bookGroup" -> "bookGroups"
        else -> error("Unknown type: $type")
    }

    private inner class StateStore : SyncReconcileStore, SyncPullStore {
        override fun runInTransaction(block: () -> Unit) = block()

        override fun metadata(objectType: String, objectId: String): SyncMetadata? =
            metadata[Key(objectType, objectId)]

        override fun metadata(identity: SyncIdentity): SyncMetadata? =
            metadata(identity.objectType, identity.objectId)

        override fun metadataForType(objectType: String): List<SyncMetadata> =
            metadata.values.filter { it.objectType == objectType }

        override fun saveMetadata(metadata: SyncMetadata) {
            this@TestSyncReplica.metadata[Key(metadata.objectType, metadata.objectId)] = metadata
        }

        override fun replaceOutbox(item: SyncOutbox) {
            outbox[Key(item.objectType, item.objectId)] = item
        }

        override fun recordRemote(candidate: SyncRemoteCandidate, applied: Boolean) {
            val key = Key(candidate.identity.objectType, candidate.identity.objectId)
            val old = metadata[key] ?: SyncMetadata(key.type, key.id)
            metadata[key] = old.copy(
                remoteUpdatedAt = candidate.objectVersion?.timestamp ?: old.remoteUpdatedAt,
                remoteUpdatedByDeviceId = candidate.objectVersion?.deviceId
                    ?: old.remoteUpdatedByDeviceId,
                deletedAt = when {
                    applied && candidate.objectVersion != null -> null
                    candidate.deleteVersion != null -> candidate.deleteVersion.timestamp
                    else -> old.deletedAt
                },
                deletedByDeviceId = when {
                    applied && candidate.objectVersion != null -> null
                    candidate.deleteVersion != null -> candidate.deleteVersion.deviceId
                    else -> old.deletedByDeviceId
                },
                dirty = if (applied) false else old.dirty,
                lastSyncedHash = if (applied) candidate.contentHash else old.lastSyncedHash,
                remoteFileModifiedAt = candidate.lastModifiedAt
            )
        }

        override fun discardOutbox(identity: SyncIdentity) {
            outbox.remove(Key(identity.objectType, identity.objectId))
        }
    }

    private class MutableClock(var value: Long = 1L) : SyncClock {
        override fun now(): Long = value
    }
}
