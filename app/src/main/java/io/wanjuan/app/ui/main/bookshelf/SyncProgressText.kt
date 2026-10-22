package io.wanjuan.app.ui.main.bookshelf

import android.content.Context
import io.wanjuan.app.R
import io.wanjuan.app.sync.SyncPhase
import io.wanjuan.app.sync.SyncProgress

fun SyncProgress.displayText(context: Context): String = when (phase) {
    SyncPhase.Idle -> context.getString(R.string.sync_progress_idle)
    SyncPhase.Connecting -> context.getString(R.string.sync_progress_connecting)
    SyncPhase.Capturing -> context.getString(R.string.sync_progress_capturing)
    SyncPhase.CheckingIndex -> if (total > 0)
        context.getString(R.string.sync_progress_index_counts, completed, total)
        else context.getString(R.string.sync_progress_index)
    SyncPhase.ReadingChanges -> context.getString(R.string.sync_progress_reading, completed, total, downloaded, reused)
    SyncPhase.Applying -> context.getString(R.string.sync_progress_applying, completed)
    SyncPhase.Uploading -> context.getString(R.string.sync_progress_uploading, completed, total, failed)
    SyncPhase.Complete -> context.getString(R.string.sync_progress_complete, downloaded, uploaded, reused, elapsedMillis / 1000.0)
    SyncPhase.Failed -> context.getString(R.string.sync_incomplete, errorMessage ?:
        if (pending > 0) context.getString(R.string.sync_pending_count, pending) else context.getString(R.string.unknown_error))
}
