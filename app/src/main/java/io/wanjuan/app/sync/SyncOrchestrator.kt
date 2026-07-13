package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.remote.SyncRemoteStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface SyncCaptureAction {
    suspend fun capture(result: SyncResult.Mutable)
}

fun interface SyncPullAction {
    suspend fun pull(result: SyncResult.Mutable)
}

fun interface SyncFlushAction {
    suspend fun flush(result: SyncResult.Mutable)
}

class SyncOrchestrator(
    private val remoteStore: SyncRemoteStore,
    private val captureAction: SyncCaptureAction,
    private val pullAction: SyncPullAction,
    private val flushAction: SyncFlushAction
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncResult = mutex.withLock {
        val result = SyncResult.Mutable()
        try {
            remoteStore.ensureDirs()
            captureAction.capture(result)
            pullAction.pull(result)
            flushAction.flush(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            result.fail(e.localizedMessage ?: e.javaClass.simpleName)
        }
        result.toResult()
    }
}
