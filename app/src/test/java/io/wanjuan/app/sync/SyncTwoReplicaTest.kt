package io.wanjuan.app.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTwoReplicaTest {

    @Test
    fun firstSyncMergesObjectsFromBothDevicesAcrossAllManagedTypes() = runBlocking {
        val remote = FakeSyncRemoteStore()
        val a = TestSyncReplica("device-a", remote)
        val b = TestSyncReplica("device-b", remote)
        a.put("book", "book-a", "A book", 100L)
        a.put("bookSource", "source-a", "A source", 100L)
        a.put("ruleSub", "rule-a", "A rule", 100L)
        b.put("book", "book-b", "B book", 110L)
        b.put("rssSource", "rss-b", "B rss", 110L)
        b.put("bookGroup", "group-b", "B group", 110L)

        a.sync()
        b.sync()
        a.sync()

        assertEquals(a.values(), b.values())
        assertEquals(6, a.values().size)
    }

    @Test
    fun remoteModificationAndDeletionConvergeOnNextPull() = runBlocking {
        val remote = FakeSyncRemoteStore()
        val a = TestSyncReplica("device-a", remote)
        val b = TestSyncReplica("device-b", remote)
        a.put("book", "book", "v1", 100L)
        a.sync()
        b.sync()

        a.put("book", "book", "v2", 200L)
        a.sync()
        b.sync()
        assertEquals("v2", b.value("book", "book"))

        a.remove("book", "book", 300L)
        a.sync()
        b.sync()
        assertFalse(b.contains("book", "book"))
    }

    @Test
    fun timestampThenDeviceIdProducesDeterministicWinner() = runBlocking {
        val remote = FakeSyncRemoteStore()
        val a = TestSyncReplica("device-a", remote)
        val b = TestSyncReplica("device-b", remote)
        a.put("rssSource", "rss", "from-a", 500L)
        b.put("rssSource", "rss", "from-b", 500L)

        a.sync()
        b.sync()
        a.sync()

        assertEquals("from-b", a.value("rssSource", "rss"))
        assertEquals(a.values(), b.values())
    }

    @Test
    fun failedUploadKeepsOutboxUntilRetrySucceeds() = runBlocking {
        val remote = FakeSyncRemoteStore().apply { failUploads = true }
        val replica = TestSyncReplica("device-a", remote)
        replica.put("bookSource", "source", "value", 100L)

        val failed = replica.sync()
        assertFalse(failed.isSuccess)
        assertTrue(replica.hasPending("bookSource", "source"))

        remote.failUploads = false
        val retried = replica.sync()
        assertTrue(retried.isSuccess)
        assertFalse(replica.hasPending("bookSource", "source"))
    }
}
