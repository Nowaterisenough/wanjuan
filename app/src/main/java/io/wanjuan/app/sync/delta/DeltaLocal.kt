package io.wanjuan.app.sync.delta

import io.wanjuan.app.sync.SyncApplyOutcome
import io.wanjuan.app.sync.SyncIdentity
import io.wanjuan.app.sync.local.SyncDeltaCursor
import io.wanjuan.app.sync.local.SyncDeltaState

interface DeltaLocal {
    fun transaction(action: () -> Unit)
    fun states(target: String): List<SyncDeltaState>
    fun save(state: SyncDeltaState)
    fun cursor(target: String): SyncDeltaCursor
    fun saveCursor(cursor: SyncDeltaCursor)
    fun snapshots(): Map<SyncIdentity, String>
    fun canDelete(identity: SyncIdentity): Boolean = true
    fun deletions(): Set<SyncIdentity> = emptySet()
    fun consumeLegacyQueue() = Unit
    fun apply(identity: SyncIdentity, json: String?, timestamp: Long): SyncApplyOutcome
}
