package io.wanjuan.app.ui.main.bookshelf

import android.content.Context
import io.wanjuan.app.R
import io.wanjuan.app.sync.SyncPhase
import io.wanjuan.app.sync.SyncProgress

fun SyncProgress.displayText(context: Context): String {
    val text = when (phase) {
        SyncPhase.Idle -> context.getString(R.string.sync_progress_idle)
        SyncPhase.Connecting -> context.getString(R.string.sync_progress_connecting)
        SyncPhase.Capturing -> context.getString(R.string.sync_progress_capturing)
        SyncPhase.CheckingIndex -> if (total > 0)
            context.getString(R.string.sync_progress_index_counts, completed, total)
            else context.getString(R.string.sync_progress_index)
        SyncPhase.CheckingCursor -> context.getString(R.string.sync_progress_cursor)
        SyncPhase.MigratingPreparing -> context.getString(R.string.sync_progress_migration_preparing)
        SyncPhase.MigratingIndex -> context.getString(R.string.sync_progress_migration_index)
        SyncPhase.MigratingReading -> context.getString(R.string.sync_progress_migration_reading, completed, total, downloaded, reused)
        SyncPhase.MigratingApplying -> if (total > 0)
            context.getString(R.string.sync_progress_migration_applying, completed, total)
            else context.getString(R.string.sync_progress_migration_applying_unknown, completed)
        SyncPhase.ReadingChanges -> if (total > 0)
            context.getString(R.string.sync_progress_reading, completed, total, downloaded, reused)
            else context.getString(R.string.sync_progress_reading_unknown, completed)
        SyncPhase.Applying -> context.getString(R.string.sync_progress_applying, completed)
        SyncPhase.PreparingUpload -> context.getString(R.string.sync_progress_preparing_upload)
        SyncPhase.Uploading -> context.getString(R.string.sync_progress_uploading, completed, total, failed)
        SyncPhase.Publishing -> context.getString(R.string.sync_progress_publishing)
        SyncPhase.Confirming -> context.getString(R.string.sync_progress_confirming)
        SyncPhase.Complete -> context.getString(R.string.sync_progress_complete, downloaded, uploaded, reused, elapsedMillis / 1000.0)
        SyncPhase.Failed -> context.getString(R.string.sync_incomplete, errorMessage ?:
            if (pending > 0) context.getString(R.string.sync_pending_count, pending) else context.getString(R.string.unknown_error))
    }
    return if (running) context.getString(R.string.sync_progress_elapsed, text,
        phaseElapsedMillis / 1000.0, elapsedMillis / 1000.0) else text
}
