package io.wanjuan.app.sync.delta

import io.wanjuan.app.sync.*
import io.wanjuan.app.sync.local.SyncDeltaState
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.utils.GSON
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

/** One durable field-delta pipeline for every synchronized object, including reading progress. */
class DeltaEngine(
    private val local: DeltaLocal,
    private val remote: DeltaRemote,
    private val target: String,
    private val clock: SyncClock,
    private val legacy: suspend () -> Map<SyncIdentity, String> = { emptyMap() },
    private val progress: SyncProgressTracker = SyncProgressTracker()
) {
    private fun SyncDeltaState.identity() = SyncIdentity(objectType, objectId)
    private fun pending() = local.states(target).filter { it.pendingId != null || it.inFlightChange != null }
    fun hasPending(): Boolean = pending().isNotEmpty()

    fun capture(): Int {
        local.transaction {
            val current = local.snapshots()
            val states = local.states(target).associateBy { it.identity() }
            val deletions = local.deletions()
            for (id in states.keys + current.keys + deletions) {
                val previous = states[id] ?: SyncDeltaState(target, id.objectType, id.objectId)
                val value = current[id]
                if (value == previous.observedJson && id !in deletions) continue
                if (value == null && !local.canDelete(id)) {
                    val replaced = id.objectType == SyncObjectType.Book && previous.observedJson != null &&
                        current.any { (other, json) -> other.objectType == SyncObjectType.Book && other != id &&
                            DeltaCodec.sameBookTitle(previous.observedJson, json) }
                    if (!replaced) continue
                }
                val explicitDelete = id in deletions && !(previous.pendingCreate && previous.remoteJson == null && previous.inFlightChange == null)
                val same = previous.inFlightChange == null && !explicitDelete && (value == previous.remoteJson ||
                    (previous.remoteJson == null && value != null && DeltaCodec.isEmptyOrder(id.objectType, value)))
                local.save(previous.copy(
                    observedJson = value,
                    pendingId = if (same) null else UUID.randomUUID().toString(),
                    pendingJson = if (same) null else value,
                    pendingDelete = !same && value == null,
                    pendingCreate = !same && value != null && ((previous.pendingId != null && previous.pendingCreate) || previous.observedJson == null)
                ))
            }
            local.consumeLegacyQueue()
        }
        return pending().size
    }

    suspend fun pull(result: SyncResult.Mutable = SyncResult.Mutable()): SyncResult {
        progress.phase(SyncPhase.CheckingIndex)
        val (_, head) = remote.head()
        var cursor = local.cursor(target)
        if (!cursor.legacyImported) {
            importLegacy(legacy())
            cursor = local.cursor(target)
        }
        if (head.timestamp == 0L) {
            check(cursor.timestamp == 0L) { "Remote sync journal was removed; refusing to advance the cursor" }
            recoverMissingBooks()
            return result.toResult()
        }
        check(head.timestamp >= cursor.timestamp) { "Remote sync journal moved backwards" }
        val blocks = arrayListOf<Pair<DeltaHead, DeltaBlock>>()
        var next = head
        while (next.timestamp > cursor.timestamp) {
            currentCoroutineContext().ensureActive()
            val block = remote.block(next)
            blocks += next to block
            next = block.previous
            progress.read(fromCache = false)
            result.downloaded++
        }
        check(next.timestamp == cursor.timestamp && next.block == cursor.block) { "Remote sync journal does not match the saved cursor" }
        blocks.asReversed().forEach { (position, block) ->
            currentCoroutineContext().ensureActive()
            applyBlock(position, block, result)
            progress.applied()
        }
        recoverMissingBooks()
        result.pending = pending().size
        return result.toResult()
    }

    suspend fun flush(): SyncResult {
        val result = SyncResult.Mutable()
        progress.phase(SyncPhase.Capturing)
        if (!local.cursor(target).legacyImported) pull(result)
        if (capture() == 0) return result.toResult()
        repeat(6) {
            currentCoroutineContext().ensureActive()
            pull(result)
            capture()
            if (!hasPending()) return result.toResult().copy(pending = 0)
            val (revision, head) = remote.head()
            val cursor = local.cursor(target)
            if (head.timestamp != cursor.timestamp || head.block != cursor.block) return@repeat
            var bytes = 0L
            val queued = pending().take(64).takeWhile { state ->
                val include = bytes == 0L || bytes + (state.pendingJson?.length ?: 0) * 4L <= 2 * 1024 * 1024
                if (include) bytes += maxOf(1, state.pendingJson?.length ?: 0) * 4L
                include
            }
            if (queued.isEmpty()) return result.toResult().copy(pending = 0)
            val bootstrap = if (head.timestamp == 0L) local.states(target).mapNotNull { state ->
                state.remoteJson?.let { DeltaChange("bootstrap:${state.objectType}:${state.objectId}",
                    state.objectType, state.objectId, create = true, fields = DeltaCodec.fields(state.objectType, null, it)) }
            } else emptyList()
            val changes = queued.map { state ->
                state.inFlightChange?.let { GSON.fromJson(it, DeltaChange::class.java) } ?: DeltaChange(requireNotNull(state.pendingId), state.objectType, state.objectId,
                    delete = state.pendingDelete, create = state.pendingCreate || state.remoteJson == null,
                    fields = state.pendingJson?.let { DeltaCodec.fields(state.objectType, state.remoteJson, it) }.orEmpty())
            }
            // Keep an immutable operation until its publication is known. A user may undo an
            // edit while the response is lost; that undo must become a compensating delta.
            local.transaction {
                queued.zip(changes).forEach { (state, change) ->
                    if (state.inFlightChange == null) local.save(state.copy(
                        inFlightChange = GSON.toJson(change), inFlightValue = state.pendingJson))
                }
            }
            progress.upload(0, changes.size, result.uploaded, result.failed)
            val batches = arrayListOf<MutableList<DeltaChange>>()
            var batchBytes = 0
            (bootstrap + changes).forEach { change ->
                val size = GSON.toJson(change).toByteArray(Charsets.UTF_8).size
                if (batches.isEmpty() || batches.last().size >= 64 || batchBytes.toLong() + size > 2 * 1024 * 1024) {
                    batches += arrayListOf<DeltaChange>()
                    batchBytes = 0
                }
                batches.last() += change
                batchBytes += size
            }
            var published = head
            val prepared = batches.map { batch ->
                check(published.timestamp < Long.MAX_VALUE) { "Sync timestamp overflow" }
                val block = DeltaBlock(maxOf(clock.now(), published.timestamp + 1), published, batch)
                published = DeltaHead(block.timestamp, remote.writeBlock(block))
                published to block
            }
            if (remote.publish(revision, published)) {
                prepared.forEach { (position, block) -> applyBlock(position, block, result) }
                recoverMissingBooks()
                result.uploaded += changes.size
                result.pending = pending().size
                progress.upload(changes.size, changes.size, result.uploaded, result.failed)
                return result.toResult()
            }
        }
        error("Concurrent sync writes did not settle; local changes remain pending")
    }

    private fun importLegacy(objects: Map<SyncIdentity, String>) {
        local.transaction {
            capture()
            val states = local.states(target).associateBy { it.identity() }
            objects.forEach { (identity, json) ->
                val state = states[identity] ?: SyncDeltaState(target, identity.objectType, identity.objectId)
                // During upgrade identical old objects become the baseline, not a new upload.
                val desired = state.pendingJson ?: json
                val updated = state.copy(remoteJson = json,
                    pendingId = if (desired == json && !state.pendingDelete) null else state.pendingId,
                    pendingJson = if (desired == json) null else state.pendingJson,
                    pendingCreate = false)
                local.save(updated)
            }
            applyStates(local.states(target).filter { it.identity() in objects }, 1L, SyncResult.Mutable())
            observeApplied()
            local.saveCursor(local.cursor(target).copy(legacyImported = true))
        }
    }

    private fun applyBlock(head: DeltaHead, block: DeltaBlock, result: SyncResult.Mutable) {
        local.transaction {
            capture()
            val states = local.states(target).associateBy { it.identity() }.toMutableMap()
            val affected = linkedSetOf<SyncIdentity>()
            block.changes.forEach { change ->
                require(change.type in SyncObjectSelection.all && change.objectId.matches(Regex("[A-Za-z0-9_.-]+")) && change.id.isNotBlank()) { "Invalid delta identity" }
                val id = SyncIdentity(change.type, change.objectId)
                val old = states[id] ?: SyncDeltaState(target, id.objectType, id.objectId)
                val flight = old.inFlightChange?.let { GSON.fromJson(it, DeltaChange::class.java) }
                val cloud = when {
                    change.delete -> null
                    old.remoteJson == null && !change.create -> null // A stale edit must not resurrect a deleted object.
                    else -> DeltaCodec.normalize(id.objectType, DeltaJson.apply(old.remoteJson?.let(DeltaJson::parse), change.fields).toString())
                }
                val acknowledged = old.pendingId == change.id
                val discardEdit = change.delete && old.pendingId != null && !old.pendingCreate && !old.pendingDelete
                var pendingId = old.pendingId?.takeUnless { acknowledged || discardEdit }
                val flightAcknowledged = flight?.id == change.id
                val remainingFlight = old.inFlightChange.takeUnless { flightAcknowledged || discardEdit }
                var desired = when {
                    pendingId == null -> cloud
                    old.pendingDelete -> null
                    cloud == null && !old.pendingCreate -> null
                    else -> DeltaCodec.rebase(id.objectType, old.remoteJson, requireNotNull(old.pendingJson), cloud)
                }
                if (pendingId != null && !old.pendingDelete && flight != null && old.pendingId != flight.id &&
                    old.pendingJson != null && desired != null) {
                    desired = DeltaCodec.rebase(id.objectType, old.inFlightValue, old.pendingJson, desired)
                }
                if (desired == cloud && remainingFlight == null) pendingId = null
                val updated = old.copy(remoteJson = cloud, pendingId = pendingId,
                    pendingJson = desired.takeIf { pendingId != null },
                    pendingDelete = pendingId != null && old.pendingDelete,
                    pendingCreate = pendingId != null && old.pendingCreate && cloud == null,
                    inFlightChange = remainingFlight, inFlightValue = old.inFlightValue.takeIf { remainingFlight != null })
                local.save(updated)
                states[id] = updated
                affected += id
            }
            applyStates(affected.map { states.getValue(it) }, block.timestamp, result)
            observeApplied()
            local.saveCursor(local.cursor(target).copy(timestamp = head.timestamp, block = head.block, legacyImported = true))
        }
    }

    private fun applyStates(states: List<SyncDeltaState>, timestamp: Long, result: SyncResult.Mutable) {
        fun value(state: SyncDeltaState): String? = if (state.pendingId != null) state.pendingJson else state.remoteJson
        states.sortedWith(compareBy<SyncDeltaState> {
            when {
                value(it) == null -> 0
                it.objectType == SyncObjectType.BookGroup -> 1
                it.objectType == SyncObjectType.Book -> 3
                it.objectType in DeltaCodec.orderTypes -> 4
                else -> 2
            }
        }).forEach { state ->
            when (local.apply(state.identity(), value(state), timestamp)) {
                SyncApplyOutcome.Inserted -> result.inserted++
                SyncApplyOutcome.Deleted -> result.deleted++
                SyncApplyOutcome.Updated, SyncApplyOutcome.Merged -> result.updated++
                SyncApplyOutcome.Skipped -> result.skipped++
            }
        }
    }

    private fun observeApplied() {
        val current = local.snapshots()
        val states = local.states(target)
        states.forEach { state ->
            val observed = current[state.identity()]
            if (state.observedJson != observed) local.save(state.copy(observedJson = observed))
        }
        val known = states.mapTo(hashSetOf()) { it.identity() }
        current.filterKeys { it !in known }.forEach { (id, json) ->
            local.save(SyncDeltaState(target, id.objectType, id.objectId, observedJson = json))
        }
    }

    private fun recoverMissingBooks() {
        local.transaction {
            val states = local.states(target)
            val current = local.snapshots()
            val restored = hashSetOf<SyncIdentity>()
            val timestamp = maxOf(1L, local.cursor(target).timestamp)
            states.filter { it.objectType == SyncObjectType.Book && !it.pendingDelete && it.identity() !in current }
                .forEach { state ->
                    // A source replacement can span blocks: retry a previously colliding live
                    // book after the old source's deletion, without redownloading its content.
                    val value = if (state.pendingId != null) state.pendingJson else state.remoteJson
                    if (value != null && !local.canDelete(state.identity()) &&
                        local.apply(state.identity(), value, timestamp) == SyncApplyOutcome.Inserted) restored += state.identity()
                }
            if (restored.isNotEmpty()) {
                states.filter { it.objectType == SyncObjectType.BookshelfOrder }.forEach { state ->
                    val value = if (state.pendingId != null) state.pendingJson else state.remoteJson
                    if (value != null) { local.apply(state.identity(), value, timestamp); restored += state.identity() }
                }
                val applied = local.snapshots()
                states.filter { it.identity() in restored }.forEach { local.save(it.copy(observedJson = applied[it.identity()])) }
            }
        }
    }
}
