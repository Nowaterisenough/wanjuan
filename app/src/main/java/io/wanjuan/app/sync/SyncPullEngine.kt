package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.merge.SyncConflictResolver
import io.wanjuan.app.sync.merge.SyncWinner
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.sync.remote.SyncRemoteFile
import io.wanjuan.app.sync.remote.SyncRemoteStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    fun prepare() = Unit
    val isTombstone: Boolean get() = false
    val directories: List<String>
    val conflictGroup: String?
        get() = null
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
    private val handlers: List<SyncPullHandler>,
    private val progress: SyncProgressTracker = SyncProgressTracker()
) {
    private var unlistedCompanions = emptyMap<SyncIdentity, SyncPullHandler>()

    private companion object {
        private const val MAX_PULL_ATTEMPTS = 2
        private const val PULL_RETRY_DELAY_MILLIS = 100L
    }

    suspend fun pullAll(result: SyncResult.Mutable) {
        progress.phase(SyncPhase.CheckingIndex)
        val snapshot = remoteStore.snapshot(handlers.flatMap { it.directories }.distinct()) { completed, total ->
            progress.phase(SyncPhase.CheckingIndex, completed, total)
        }
        progress.reading(snapshot.values.flatten().distinctBy { it.path }.size)
        handlers.forEach { it.prepare() }
        val liveIds = handlers.filterNot { it.isTombstone }.flatMap { handler ->
            handler.directories.flatMap { directory -> snapshot[directory].orEmpty().mapNotNull(handler::identity) }
        }.toSet()
        unlistedCompanions = buildMap {
            handlers.filter { it.isTombstone && it.conflictGroup == null }.forEach { tombstone ->
                tombstone.directories.flatMap { snapshot[it].orEmpty() }.forEach { file ->
                    val identity = tombstone.identity(file) ?: return@forEach
                    if (identity !in liveIds) handlers.firstOrNull {
                        !it.isTombstone && it.directories.size == 1 && it.identity(file)?.objectType == identity.objectType
                    }?.let { put(identity, it) }
                }
            }
        }
        val completedGroups = hashSetOf<String>()
        for (handler in handlers) {
            val group = handler.conflictGroup
            if (group != null) {
                if (completedGroups.add(group)) {
                    pullConflictGroup(handlers.filter { it.conflictGroup == group }, snapshot, result)
                }
                continue
            }
            for (directory in handler.directories.distinct()) {
                // Bound both network concurrency and retained source JSON, not just the number of coroutines.
                for (batch in snapshot[directory].orEmpty().chunked(32)) {
                    readCandidates(batch.map { handler to it }, result).forEach { outcome ->
                        outcome.fold(
                            onSuccess = { it?.let { candidate -> applyCandidate(handler, candidate, result) } },
                            onFailure = { result.fail(it.localizedMessage ?: it.javaClass.simpleName) }
                        )
                    }
                }
            }
        }
    }

    private suspend fun readCandidates(
        entries: List<Pair<SyncPullHandler, SyncRemoteFile>>,
        result: SyncResult.Mutable
    ): List<Result<SyncRemoteCandidate?>> = coroutineScope {
        val permits = Semaphore(4)
        entries.map { (handler, file) ->
            async {
                permits.withPermit {
                    val identity = handler.identity(file)
                    if (identity == null) {
                        synchronized(result) { result.skipped++ }
                        progress.read(fromCache = false, downloaded = false)
                        return@withPermit Result.success(null)
                    }
                    val metadata = pullStore.metadata(identity)
                    if (file.revision == null && handler.usesModifiedTimeMarker &&
                        handler.isLocalObjectPresent(identity) && file.lastModifiedAt > 0L &&
                        metadata?.remoteFileModifiedAt == file.lastModifiedAt
                    ) {
                        synchronized(result) { result.skipped++ }
                        progress.read(fromCache = false, downloaded = false)
                        return@withPermit Result.success(null)
                    }
                    try {
                        Result.success(downloadAndParse(handler, file, identity, result))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun pullConflictGroup(
        group: List<SyncPullHandler>,
        snapshot: Map<String, List<SyncRemoteFile>>,
        result: SyncResult.Mutable
    ) {
        val candidates = linkedMapOf<SyncIdentity, Pair<SyncPullHandler, SyncRemoteCandidate>>()
        val failed = hashSetOf<SyncIdentity>()
        val entries = group.flatMap { handler ->
            handler.directories.distinct().flatMap { directory -> snapshot[directory].orEmpty().map { handler to it } }
        }
        val liveHandler = group.firstOrNull { !it.isTombstone }
        if (liveHandler != null && group.any { it.isTombstone }) {
            val listedLiveIds = entries.filter { !it.first.isTombstone }.mapNotNull { (handler, file) -> handler.identity(file) }.toSet()
            val unpaired = entries.filter { it.first.isTombstone }.mapNotNull { (handler, file) -> handler.identity(file) }
                .filterNot { it in listedLiveIds }.distinct()
            for (identity in unpaired) {
                // A partial DAV listing must not let an old tombstone delete an omitted, newer live book.
                val path = "${liveHandler.directories.single().trimEnd('/')}/${identity.objectId}.json"
                val file = SyncRemoteFile(path, path.substringAfterLast('/'), 0L)
                progress.extraRead()
                var downloaded = false
                try {
                    val read = remoteStore.readJson(file)
                    val json = read.json ?: continue
                    result.downloaded++
                    downloaded = true
                    val candidate = liveHandler.parse(file, json)
                    require(candidate.identity == identity) { "Remote companion identity mismatch" }
                    candidates[identity] = liveHandler to candidate
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += identity
                    result.fail(e.localizedMessage ?: e.javaClass.simpleName)
                } finally {
                    progress.read(fromCache = false, downloaded = downloaded)
                }
            }
        }
        for (batch in entries.chunked(32)) {
            val outcomes = readCandidates(batch, result)
            batch.zip(outcomes).forEach { (entry, outcome) ->
                val (handler, file) = entry
                val identity = handler.identity(file) ?: return@forEach
                outcome.fold(onSuccess = { candidate ->
                    candidate ?: return@fold
                    val previous = candidates[identity]?.second
                    if (previous == null || candidate.version > previous.version ||
                        (candidate.version == previous.version && candidate.deleteVersion != null)
                    ) candidates[identity] = handler to candidate
                    if (previous != null) result.skipped++
                }, onFailure = {
                    failed += identity
                    result.fail(it.localizedMessage ?: it.javaClass.simpleName)
                })
            }
        }
        // Resolve both cached and changed object/tombstone files before touching Room.
        candidates.values.filter { it.second.identity !in failed }
            .sortedWith(compareByDescending<Pair<SyncPullHandler, SyncRemoteCandidate>> { it.second.deleteVersion != null }
                .thenByDescending { it.second.version })
            .forEach { (handler, candidate) ->
                currentCoroutineContext().ensureActive()
                applyCandidate(handler, candidate, result)
            }
    }

    private suspend fun applyCandidate(
        handler: SyncPullHandler,
        candidate: SyncRemoteCandidate,
        result: SyncResult.Mutable
    ) {
        val identity = candidate.identity
        try {
            val companion = unlistedCompanions[identity]
            if (candidate.deleteVersion != null && companion != null) {
                val path = "${companion.directories.single().trimEnd('/')}/${identity.objectId}.json"
                progress.extraRead()
                var downloaded = false
                val live = try {
                    val json = remoteStore.readJson(SyncRemoteFile(path, path.substringAfterLast('/'), 0L)).json
                    if (json == null) null else {
                        downloaded = true
                        result.downloaded++
                        companion.parse(SyncRemoteFile(path, path.substringAfterLast('/'), 0L), json).also {
                            require(it.identity == identity) { "Remote companion identity mismatch" }
                        }
                    }
                } finally { progress.read(fromCache = false, downloaded = downloaded) }
                if (live?.objectVersion != null && live.objectVersion > candidate.deleteVersion) {
                    result.skipped++
                    applyCandidate(companion, live, result)
                    return
                }
            }
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
                        SyncApplyOutcome.Skipped -> {
                            result.skipped += 1
                            if (candidate.objectVersion != null) return@runInTransaction
                        }
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
        } finally {
            progress.applied()
        }
    }

    private suspend fun downloadAndParse(
        handler: SyncPullHandler,
        file: SyncRemoteFile,
        identity: SyncIdentity,
        result: SyncResult.Mutable
    ): SyncRemoteCandidate {
        var downloaded = false
        var reused = false
        var lastError: Exception? = null
        try {
            repeat(MAX_PULL_ATTEMPTS) { attempt ->
                try {
                    val read = remoteStore.readJson(file)
                    val json = requireNotNull(read.json) { "Remote file is missing: ${file.path}" }
                    reused = read.fromCache && !downloaded
                    if (!downloaded && !read.fromCache) {
                        synchronized(result) { result.downloaded++ }
                        downloaded = true
                    }
                    val candidate = handler.parse(file, json)
                    require(candidate.identity == identity) { "Remote identity changed while parsing ${file.path}" }
                    return candidate
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (attempt + 1 < MAX_PULL_ATTEMPTS) {
                        currentCoroutineContext().ensureActive()
                        remoteStore.invalidateCached(file.path)
                        delay(PULL_RETRY_DELAY_MILLIS)
                    }
                }
            }
            throw requireNotNull(lastError)
        } finally {
            progress.read(fromCache = reused, downloaded = downloaded)
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
            val updated = mergePullMetadata(old, candidate, applied)
            if (updated != old) db.syncMetadataDao.insert(updated)
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
