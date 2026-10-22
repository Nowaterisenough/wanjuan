package io.wanjuan.app.sync.delta

import io.wanjuan.app.sync.SyncIdentity
import io.wanjuan.app.sync.SyncPhase
import io.wanjuan.app.sync.SyncProgressTracker
import io.wanjuan.app.sync.SyncPullEngine
import io.wanjuan.app.sync.SyncPullHandler
import io.wanjuan.app.sync.SyncPullStore
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.remote.SyncRemoteStore

class LegacySyncImporter(
    private val remote: SyncRemoteStore,
    private val store: SyncPullStore,
    private val handlers: List<SyncPullHandler>,
    private val prepare: () -> Unit,
    private val progress: SyncProgressTracker
) {
    suspend fun read(): Map<SyncIdentity, String> {
        progress.phase(SyncPhase.MigratingPreparing)
        prepare()
        val reader = LegacyDeltaReader()
        val result = SyncResult.Mutable()
        val migrationProgress = SyncProgressTracker(progress::migration)
        SyncPullEngine(remote, store, handlers, migrationProgress, reader::accept).pullAll(result)
        check(result.failed == 0) { result.errorMessage ?: "Legacy sync import failed" }
        progress.phase(SyncPhase.MigratingApplying)
        return reader.snapshots()
    }
}
