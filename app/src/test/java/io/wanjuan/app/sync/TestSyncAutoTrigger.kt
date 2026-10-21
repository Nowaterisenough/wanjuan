package io.wanjuan.app.sync

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TestSyncAutoTrigger {
    @Test
    fun batchesMutationsAndIgnoresInitialAndAcknowledgedRemoteState() = runBlocking {
        val changes = MutableStateFlow("initial")
        var pending = true
        var calls = 0
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            SyncAutoTrigger(40).observe(changes, { true }, { pending }, { calls++ }, { throw it })
        }
        try {
            delay(60)
            assertEquals(0, calls)
            changes.value = "source edit"
            delay(10)
            changes.value = "book added"
            delay(10)
            changes.value = "group changed"
            delay(100)
            assertEquals(1, calls)
            pending = false
            changes.value = "remote acknowledged"
            delay(100)
            assertEquals(1, calls)
        } finally { observer.cancel() }
    }

    @Test
    fun disabledSyncAndDuplicateFingerprintsDoNotRequestWork() = runBlocking {
        val changes = MutableStateFlow("initial")
        var enabled = false
        var captures = 0
        var calls = 0
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            SyncAutoTrigger(20).observe(changes, { enabled }, { captures++; true }, { calls++ }, { throw it })
        }
        try {
            delay(30)
            changes.value = "offline edit"
            delay(80)
            assertEquals(0, captures)
            enabled = true
            changes.value = "new edit"
            delay(80)
            changes.value = "new edit"
            delay(80)
            assertEquals(1, captures)
            assertEquals(1, calls)
        } finally { observer.cancel() }
    }
}
