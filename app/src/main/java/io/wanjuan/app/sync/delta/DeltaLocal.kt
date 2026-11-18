package io.wanjuan.app.sync.delta

import io.wanjuan.app.sync.SyncApplyOutcome
import io.wanjuan.app.sync.SyncIdentity
import io.wanjuan.app.sync.local.SyncDeltaCursor
import io.wanjuan.app.sync.local.SyncDeltaState
import io.wanjuan.app.sync.model.SyncObjectType

interface DeltaLocal {
    fun transaction(action: () -> Unit)
    fun states(target: String): List<SyncDeltaState>
    fun states(target: String, objectTypes: Set<String>): List<SyncDeltaState> =
        states(target).filter { it.objectType in objectTypes }
    fun pending(target: String, limit: Int = Int.MAX_VALUE): List<SyncDeltaState> =
        states(target).filter { it.pendingId != null || it.inFlightChange != null }.take(limit)
    fun pendingCount(target: String): Int = pending(target).size
    fun pendingBookIds(target: String): Set<String> = pending(target)
        .filter { it.objectType == io.wanjuan.app.sync.model.SyncObjectType.Book }.mapTo(hashSetOf()) { it.objectId }
    fun save(state: SyncDeltaState)
    fun cursor(target: String): SyncDeltaCursor
    fun saveCursor(cursor: SyncDeltaCursor)
    fun snapshots(): Map<SyncIdentity, String>
    fun snapshots(objectTypes: Set<String>): Map<SyncIdentity, String> =
        snapshots().filterKeys { it.objectType in objectTypes }
    fun missingBookStates(target: String): List<SyncDeltaState> {
        val types = setOf(SyncObjectType.Book)
        val present = snapshots(types).keys
        return states(target, types).filter { !it.pendingDelete && SyncIdentity(it.objectType, it.objectId) !in present }
    }
    fun canDelete(identity: SyncIdentity): Boolean = true
    fun deletions(): Set<SyncIdentity> = emptySet()
    fun consumeLegacyQueue() = Unit
    fun apply(identity: SyncIdentity, json: String?, timestamp: Long): SyncApplyOutcome
}
