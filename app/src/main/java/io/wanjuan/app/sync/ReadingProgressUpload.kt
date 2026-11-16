package io.wanjuan.app.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Acquire background protection while the reader is still visible, before waiting for Room. */
internal fun startReadingProgressUpload(
    scope: CoroutineScope,
    saved: Deferred<Unit>,
    protect: (Job) -> Unit,
    upload: suspend () -> Unit,
    onFailure: (Exception) -> Unit
): Job {
    val task = scope.launch(start = CoroutineStart.LAZY) {
        try {
            saved.await()
            upload()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
        }
    }
    try {
        protect(task)
    } catch (error: Exception) {
        // Keep the normal persisted queue usable if the OS denies foreground protection.
        onFailure(error)
    }
    task.start()
    return task
}
