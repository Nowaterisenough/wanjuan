package io.wanjuan.app.sync

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class TestSyncChangeObservation {
    @Test fun catalogBatchesAreDeferredAndRefreshExitRechecksEvenWithoutAnotherDatabaseEdit() = runBlocking {
        withTimeout(5000) {
            val refreshing = MutableStateFlow(true)
            val changes = Channel<Int>()
            val consumed = Channel<Int>(Channel.UNLIMITED)
            val captures = Channel<Unit>(Channel.UNLIMITED)
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                changes.receiveAsFlow().onEach { consumed.send(it) }
                    .afterBookshelfRefresh(refreshing, 10).collect { captures.send(Unit) }
            }
            try {
                repeat(3) {
                    changes.send(it)
                    assertEquals(it, consumed.receive())
                    assertNull(withTimeoutOrNull(40) { captures.receive() })
                }
                // Both normal completion and cancellation clear the refresh flag in finally.
                refreshing.value = false
                captures.receive()
                assertNull(withTimeoutOrNull(40) { captures.receive() })
                changes.send(4)
                captures.receive()
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun refreshBeginningDuringDebounceDefersTheScheduledCapture() = runBlocking {
        withTimeout(5000) {
            val refreshing = MutableStateFlow(false)
            val changes = MutableStateFlow(0)
            val captures = Channel<Unit>(Channel.UNLIMITED)
            val job = launch { changes.afterBookshelfRefresh(refreshing, 50).collect { captures.send(Unit) } }
            try {
                captures.receive()
                changes.value = 1
                refreshing.value = true
                assertNull(withTimeoutOrNull(150) { captures.receive() })
                val resumed = async { captures.receive() }
                refreshing.value = false
                resumed.await()
            } finally { job.cancelAndJoin() }
        }
    }
}
