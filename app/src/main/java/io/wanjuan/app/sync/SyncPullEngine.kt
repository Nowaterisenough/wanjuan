package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.merge.SyncConflictResolver
import io.wanjuan.app.sync.merge.SyncWinner
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.sync.remote.SyncRemoteFile
import io.wanjuan.app.sync.remote.SyncRemoteStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class SyncIdentity(
    val objectType: String,
    val objectId: String
)

data class SyncRemoteCandidate(
    val identity: SyncIdentity,
    val path: String,
    val contentHash: String,
    val objectVersion: SyncVersion?,
    val deleteVersion: SyncVersion?,
    val payloadJson: String,
    val lastModifiedAt: Long
) {
    val version: SyncVersion
        get() = objectVersion ?: requireNotNull(deleteVersion)
}

enum class SyncApplyOutcome {
    Inserted,
    Updated,
    Deleted,
    Skipped
}

interface SyncPullHandler {
    val directories: List<String>
    val usesModifiedTimeMarker: Boolean
        get() = true

    fun identity(file: SyncRemoteFile): SyncIdentity?

    fun parse(file: SyncRemoteFile, json: String): SyncRemoteCandidate

    fun applyRemote(candidate: SyncRemoteCandidate): SyncApplyOutcome
}

interface SyncPullStore {
    fun metadata(identity: SyncIdentity): SyncMetadata?

    fun recordRemote(candidate: SyncRemoteCandidate, applied: Boolean)

    fun discardOutbox(identity: SyncIdentity)
}

class SyncPullEngine(
    private val remoteStore: SyncRemoteStore,
    private val pullStore: SyncPullStore,
    private val handlers: List<SyncPullHandler>
) {

    suspend fun pullAll(result: SyncResult.Mutable) {
        for (handler in handlers) {
            for (directory in handler.directories) {
                currentCoroutineContext().ensureActive()
                val files = remoteStore.list(directory)
                for (file in files) {
                    currentCoroutineContext().ensureActive()
                    pullFile(handler, file, result)
                }
            }
        }
    }

    private suspend fun pullFile(
        handler: SyncPullHandler,
        file: SyncRemoteFile,
        result: SyncResult.Mutable
    ) {
        val identity = handler.identity(file) ?: run {
            result.skipped += 1
            return
        }
        val metadata = pullStore.metadata(identity)
        if (handler.usesModifiedTimeMarker &&
            file.lastModifiedAt > 0L &&
            metadata?.remoteFileModifiedAt == file.lastModifiedAt
        ) {
            result.skipped += 1
            return
        }

        try {
            val json = requireNotNull(remoteStore.downloadJson(file.path)) {
                "Remote file is missing: ${file.path}"
            }
            result.downloaded += 1
            val candidate = handler.parse(file, json)
            require(candidate.identity == identity) {
                "Remote identity changed while parsing ${file.path}"
            }

            val current = pullStore.metadata(identity)
            val alreadyApplied = current?.lastSyncedHash == candidate.contentHash && when {
                candidate.objectVersion != null ->
                    current.currentObjectVersion() == candidate.objectVersion
                candidate.deleteVersion != null ->
                    current.localDeleteVersion() == candidate.deleteVersion
                else -> false
            }
            if (alreadyApplied) {
                result.skipped += 1
                pullStore.recordRemote(candidate, applied = false)
                return
            }
            val winner = SyncConflictResolver.choose(
                localObject = current.currentObjectVersion(),
                remoteObject = candidate.objectVersion,
                localDelete = current.localDeleteVersion(),
                remoteDelete = candidate.deleteVersion
            )
            val remoteWins = winner == SyncWinner.RemoteObject ||
                winner == SyncWinner.RemoteDelete
            if (remoteWins) {
                when (handler.applyRemote(candidate)) {
                    SyncApplyOutcome.Inserted -> result.inserted += 1
                    SyncApplyOutcome.Updated -> result.updated += 1
                    SyncApplyOutcome.Deleted -> result.deleted += 1
                    SyncApplyOutcome.Skipped -> result.skipped += 1
                }
                pullStore.discardOutbox(identity)
            } else {
                result.skipped += 1
            }
            pullStore.recordRemote(candidate, remoteWins)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            result.fail(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    private fun SyncMetadata?.currentObjectVersion(): SyncVersion? {
        this ?: return null
        val local = localUpdatedAt.takeIf { it > 0L }
            ?.let { SyncVersion(it, localUpdatedByDeviceId.orEmpty()) }
        val remote = remoteUpdatedAt.takeIf { it > 0L }
            ?.let { SyncVersion(it, remoteUpdatedByDeviceId.orEmpty()) }
        return listOfNotNull(local, remote).maxOrNull()
    }

    private fun SyncMetadata?.localDeleteVersion(): SyncVersion? {
        this ?: return null
        val timestamp = deletedAt ?: return null
        return SyncVersion(timestamp, deletedByDeviceId.orEmpty())
    }
}

class RoomSyncPullStore(
    private val db: AppDatabase
) : SyncPullStore {

    override fun metadata(identity: SyncIdentity): SyncMetadata? =
        db.syncMetadataDao.get(identity.objectType, identity.objectId)

    override fun recordRemote(candidate: SyncRemoteCandidate, applied: Boolean) {
        db.runInTransaction {
            val identity = candidate.identity
            val old = metadata(identity) ?: SyncMetadata(
                objectType = identity.objectType,
                objectId = identity.objectId
            )
            db.syncMetadataDao.insert(mergePullMetadata(old, candidate, applied))
        }
    }

    override fun discardOutbox(identity: SyncIdentity) {
        db.syncOutboxDao.deleteForObject(identity.objectType, identity.objectId)
    }
}

internal fun mergePullMetadata(
    old: SyncMetadata,
    candidate: SyncRemoteCandidate,
    applied: Boolean
): SyncMetadata {
    val remoteObject = candidate.objectVersion
    val remoteDelete = candidate.deleteVersion
    val previousRemoteObject = old.remoteUpdatedAt.takeIf { it > 0L }
        ?.let { SyncVersion(it, old.remoteUpdatedByDeviceId.orEmpty()) }
    val newestRemoteObject = listOfNotNull(previousRemoteObject, remoteObject).maxOrNull()
    return old.copy(
        remoteUpdatedAt = newestRemoteObject?.timestamp ?: old.remoteUpdatedAt,
        remoteUpdatedByDeviceId = newestRemoteObject?.deviceId ?: old.remoteUpdatedByDeviceId,
        deletedAt = when {
            applied && remoteObject != null -> null
            applied && remoteDelete != null -> remoteDelete.timestamp
            else -> old.deletedAt
        },
        deletedByDeviceId = when {
            applied && remoteObject != null -> null
            applied && remoteDelete != null -> remoteDelete.deviceId
            else -> old.deletedByDeviceId
        },
        dirty = if (applied) false else old.dirty,
        lastSyncedHash = if (applied) candidate.contentHash else old.lastSyncedHash,
        remoteFileModifiedAt = candidate.lastModifiedAt.takeIf { it > 0L }
            ?: old.remoteFileModifiedAt
    )
}
