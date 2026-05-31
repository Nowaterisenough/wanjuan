package io.wanjuan.app.sync

import io.wanjuan.app.sync.merge.SyncMerge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    @Test
    fun newerProgressTimestampWinsEvenWhenChapterIsEarlier() {
        assertTrue(SyncMerge.remoteProgressWins(localUpdatedAt = 1000L, remoteUpdatedAt = 2000L))
    }

    @Test
    fun olderProgressTimestampDoesNotWinWhenChapterIsLater() {
        assertFalse(SyncMerge.remoteProgressWins(localUpdatedAt = 2000L, remoteUpdatedAt = 1000L))
    }

    @Test
    fun progressWithoutRemoteTimestampDoesNotWin() {
        assertFalse(SyncMerge.remoteProgressWins(localUpdatedAt = 2000L, remoteUpdatedAt = 0L))
    }

    @Test
    fun deletionWinsWhenDeletedAfterObjectUpdate() {
        assertTrue(SyncMerge.tombstoneWins(objectUpdatedAt = 1000L, deletedAt = 2000L))
    }

    @Test
    fun objectWinsWhenUpdatedAfterDeletion() {
        assertFalse(SyncMerge.tombstoneWins(objectUpdatedAt = 3000L, deletedAt = 2000L))
    }

    @Test
    fun equalTimestampsPreferLocalToAvoidOscillation() {
        assertFalse(SyncMerge.remoteWins(localUpdatedAt = 1000L, remoteUpdatedAt = 1000L))
    }
}
