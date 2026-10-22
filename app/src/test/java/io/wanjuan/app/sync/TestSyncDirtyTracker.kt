package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncObjectType
import org.junit.Assert.*
import org.junit.Test

class TestSyncDirtyTracker {
    @Test
    fun noMutationDoesNotRescanLocalObjects() {
        val tracker = SyncDirtyTracker()
        val captures = mutableListOf<Set<String>>()
        tracker.capture { captures += it }
        repeat(10) { tracker.capture { captures += it } }
        assertEquals(listOf(SyncObjectSelection.all), captures)
    }

    @Test
    fun sourceMutationDoesNotScanBooksOrOtherSources() {
        val tracker = SyncDirtyTracker(emptySet())
        tracker.markTables(setOf("book_sources"))
        tracker.capture { assertEquals(setOf(SyncObjectType.BookSource, SyncObjectType.BookSourceOrder), it) }
        tracker.capture { fail("An acknowledged selection must not be captured twice") }
    }

    @Test
    fun mutationDuringCaptureAndFailedCaptureRemainPending() {
        val tracker = SyncDirtyTracker(setOf(SyncObjectType.Book))
        tracker.capture { tracker.mark(setOf(SyncObjectType.Book)) }
        var calls = 0
        try { tracker.capture { calls++; error("retry") } } catch (_: IllegalStateException) { }
        tracker.capture { calls++ }
        tracker.capture { fail("Already captured") }
        assertEquals(2, calls)
    }

    @Test
    fun catalogFlushCanForceBooksWithoutRescanningSourceRules() {
        val tracker = SyncDirtyTracker(emptySet())
        tracker.capture(setOf(SyncObjectType.Book, SyncObjectType.BookshelfOrder)) {
            assertEquals(setOf(SyncObjectType.Book, SyncObjectType.BookshelfOrder), it)
        }
    }
}
