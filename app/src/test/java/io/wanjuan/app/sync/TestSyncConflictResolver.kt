package io.wanjuan.app.sync

import io.wanjuan.app.sync.merge.SyncConflictResolver
import io.wanjuan.app.sync.merge.SyncWinner
import io.wanjuan.app.sync.model.SyncVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class TestSyncConflictResolver {

    @Test
    fun newerTimestampWins() {
        assertEquals(
            SyncWinner.RemoteObject,
            SyncConflictResolver.choose(
                localObject = SyncVersion(100L, "device-b"),
                remoteObject = SyncVersion(200L, "device-a"),
                localDelete = null,
                remoteDelete = null
            )
        )
    }

    @Test
    fun deviceIdBreaksEqualTimestampTie() {
        assertEquals(
            SyncWinner.RemoteObject,
            SyncConflictResolver.choose(
                localObject = SyncVersion(100L, "device-a"),
                remoteObject = SyncVersion(100L, "device-b"),
                localDelete = null,
                remoteDelete = null
            )
        )
    }

    @Test
    fun newerTombstoneWins() {
        assertEquals(
            SyncWinner.RemoteDelete,
            SyncConflictResolver.choose(
                localObject = SyncVersion(100L, "device-a"),
                remoteObject = null,
                localDelete = null,
                remoteDelete = SyncVersion(200L, "device-b")
            )
        )
    }

    @Test
    fun newerObjectRestoresDeletedItem() {
        assertEquals(
            SyncWinner.LocalObject,
            SyncConflictResolver.choose(
                localObject = SyncVersion(300L, "device-a"),
                remoteObject = null,
                localDelete = null,
                remoteDelete = SyncVersion(200L, "device-b")
            )
        )
    }

    @Test
    fun noCandidatesReturnsNone() {
        assertEquals(
            SyncWinner.None,
            SyncConflictResolver.choose(null, null, null, null)
        )
    }
}
