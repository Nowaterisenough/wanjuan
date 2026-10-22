package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TestSyncProgress {
    @Test fun phaseTimingAndBookFailuresAreIndependentOfOverallElapsedTime() {
        val tracker = SyncProgressTracker()
        tracker.begin()
        tracker.pendingBooks(setOf("book"))
        tracker.phase(SyncPhase.Uploading)
        Thread.sleep(20)
        tracker.tick()
        val uploading = tracker.snapshot()
        tracker.phase(SyncPhase.Publishing)
        assertTrue(tracker.snapshot().phaseElapsedMillis < uploading.phaseElapsedMillis)
        assertTrue(tracker.phaseTimings().getValue(SyncPhase.Uploading) >= 20)
        tracker.booksStage(listOf("book"), 0.65f)
        tracker.finish(SyncResult(failed = 1, pending = 1))
        assertTrue(tracker.snapshot().books.getValue("book").failed)
        tracker.begin()
        tracker.booksStage(listOf("book"), 0.12f)
        assertFalse(tracker.snapshot().books.getValue("book").failed)
        tracker.pendingBooks(emptySet())
        assertTrue(tracker.snapshot().books.isEmpty())
    }
    @Test
    fun migrationProgressAndHeartbeatDoNotPretendToBeAnIndexScan() {
        val tracker = SyncProgressTracker()
        tracker.begin()
        tracker.phase(SyncPhase.CheckingCursor)
        Thread.sleep(10)
        tracker.tick()
        assertTrue(tracker.snapshot().elapsedMillis > 0)
        tracker.migration(SyncProgress(SyncPhase.ReadingChanges, completed = 40, total = 599, downloaded = 5, reused = 35))
        assertEquals(SyncPhase.MigratingReading, tracker.snapshot().phase)
        assertEquals(40, tracker.snapshot().completed)
        assertEquals(35, tracker.snapshot().reused)
        tracker.finish(SyncResult())
        val finished = tracker.snapshot()
        tracker.tick()
        assertEquals(finished, tracker.snapshot())
    }
    @Test
    fun reportsRealCountsForNetworkCacheAndUploads() {
        val states = mutableListOf<SyncProgress>()
        val tracker = SyncProgressTracker { states += it }
        tracker.begin()
        tracker.phase(SyncPhase.CheckingIndex, 1, 1)
        tracker.reading(3)
        tracker.read(fromCache = false)
        tracker.read(fromCache = true)
        tracker.read(fromCache = true)
        assertEquals(3, states.last().completed)
        assertEquals(3, states.last().total)
        assertEquals(1, states.last().downloaded)
        assertEquals(2, states.last().reused)
        tracker.upload(1, 2, 1, 0)
        assertTrue(states.last().running)
        tracker.finish(SyncResult(uploaded = 2, downloaded = 1))
        assertFalse(states.last().running)
        assertEquals(SyncPhase.Complete, states.last().phase)
        assertEquals(2, states.last().reused)
    }

    @Test
    fun failedAndCancelledSyncNeverLeaveProgressRunning() = runBlocking {
        val states = mutableListOf<SyncProgress>()
        val tracker = SyncProgressTracker { states += it }
        val remote = TestFakeSyncRemoteStore()
        val orchestrator = SyncOrchestrator(remote,
            SyncCaptureAction { throw CancellationException("cancel") }, SyncPullAction {}, SyncFlushAction {}, tracker)
        try { orchestrator.sync(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertFalse(states.last().running)
        assertEquals(SyncPhase.Failed, states.last().phase)
        tracker.begin()
        assertEquals(0, states.last().reused)
    }
}
