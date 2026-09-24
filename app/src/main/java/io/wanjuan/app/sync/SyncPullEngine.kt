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
import kotlinx.coroutines.delay
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
    Merged,
    Deleted,
    Skipped
}

interface SyncPullHandler {
    val directories: List<String>
    val usesModifiedTimeMarker: Boolean
        get() = true
    val mergesComponents: Boolean
        get() = false

    fun identity(file: SyncRemoteFile): SyncIdentity?

    /**
     * A persisted marker is only safe to skip when the corresponding local object still exists.
     * Handlers for objects that are not represented locally can keep the default behavior.
     */
    fun isLocalObjectPresent(identity: SyncIdentity): Boolean = true

    fun parse(file: SyncRemoteFile, json: String): SyncRemoteCandidate

    fun applyRemote(candidate: SyncRemoteCandidate): SyncApplyOutcome
}

interface SyncPullStore {
    fun runInTransaction(block: () -> Unit) = block()
    fun metadata(identity: SyncIdentity): SyncMetadata?

    fun recordRemote(candidate: SyncRemoteCandidate, applied: Boolean)

    fun discardOutbox(identity: SyncIdentity)
}

class SyncPullEngine(
    private val remoteStore: SyncRemoteStore,
    private val pullStore: SyncPullStore,
    private val handlers: List<SyncPullHandler>
) {

    private companion object {
        private const val MAX_PULL_ATTEMPTS = 2
        private const val PULL_RETRY_DELAY_MILLIS = 100L
    }

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
            handler.isLocalObjectPresent(identity) &&
            file.lastModifiedAt > 0L &&
            metadata?.remoteFileModifiedAt == file.lastModifiedAt
        ) {
            result.skipped += 1
            return
        }

        try {
            val candidate = downloadAndParse(handler, file, identity, result)

            pullStore.runInTransaction {
                val current = pullStore.metadata(identity)
                val alreadyApplied = handler.isLocalObjectPresent(identity) &&
                    current?.lastSyncedHash == candidate.contentHash && when {
                    candidate.objectVersion != null ->
                        current.currentObjectVersion() == candidate.objectVersion
                    candidate.deleteVersion != null ->
                        current.localDeleteVersion() == candidate.deleteVersion
                    else -> false
                }
                if (alreadyApplied) {
                    result.skipped += 1
                    pullStore.recordRemote(candidate, applied = false)
                    return@runInTransaction
                }
                val winner = SyncConflictResolver.choose(
                    localObject = current.currentObjectVersion(),
                    remoteObject = candidate.objectVersion,
                    localDelete = current.localDeleteVersion(),
                    remoteDelete = candidate.deleteVersion
                )
                val remoteWins = winner == SyncWinner.RemoteObject ||
                    winner == SyncWinner.RemoteDelete ||
                    (handler.mergesComponents && winner == SyncWinner.LocalObject && candidate.objectVersion != null)
                var fullyApplied = remoteWins
                if (remoteWins) {
                    when (handler.applyRemote(candidate)) {
                        SyncApplyOutcome.Inserted -> result.inserted += 1
                        SyncApplyOutcome.Updated -> result.updated += 1
                        SyncApplyOutcome.Merged -> {
                            result.updated += 1
                            fullyApplied = false
                        }
                        SyncApplyOutcome.Deleted -> result.deleted += 1
                        SyncApplyOutcome.Skipped -> result.skipped += 1
                    }
                    if (fullyApplied) pullStore.discardOutbox(identity)
                } else {
                    result.skipped += 1
                }
                pullStore.recordRemote(candidate, fullyApplied)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            result.fail(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    private suspend fun downloadAndParse(
        handler: SyncPullHandler,
        file: SyncRemoteFile,
        identity: SyncIdentity,
        result: SyncResult.Mutable
    ): SyncRemoteCandidate {
        var downloaded = false
        var lastError: Exception? = null
        repeat(MAX_PULL_ATTEMPTS) { attempt ->
            try {
                val json = requireNotNull(remoteStore.downloadJson(file.path)) {
                    "Remote file is missing: ${file.path}"
                }
                if (!downloaded) {
                    result.downloaded += 1
                    downloaded = true
                }
                val candidate = handler.parse(file, json)
                require(candidate.identity == identity) {
                    "Remote identity changed while parsing ${file.path}"
                }
                return candidate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt + 1 < MAX_PULL_ATTEMPTS) {
                    currentCoroutineContext().ensureActive()
                    delay(PULL_RETRY_DELAY_MILLIS)
                }
            }
        }
        throw requireNotNull(lastError)
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
    override fun runInTransaction(block: () -> Unit) = db.runInTransaction(block)

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
