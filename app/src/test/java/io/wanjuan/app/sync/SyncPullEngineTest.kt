package io.wanjuan.app.sync

import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.sync.remote.SyncRemoteFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPullEngineTest {

    @Test
    fun unchangedRemoteModifiedTimeSkipsSecondDownload() = runBlocking {
        val remote = FakeSyncRemoteStore().apply {
            put("books/book-a.json", "200|device-b|hash-remote", 10L)
        }
        val store = MemoryPullStore()
        val handler = TextPullHandler()
        val engine = SyncPullEngine(remote, store, listOf(handler))

        engine.pullAll(SyncResult.Mutable())
        engine.pullAll(SyncResult.Mutable())

        assertEquals(1, remote.downloads)
        assertEquals(1, handler.applied)
    }

    @Test
    fun zeroModifiedTimeAlwaysDownloadsButSkipsOlderRemoteVersion() = runBlocking {
        val remote = FakeSyncRemoteStore().apply {
            put("books/book-a.json", "100|device-a|hash-remote", 0L)
        }
        val store = MemoryPullStore().apply {
            putLocal("book", "book-a", SyncVersion(200L, "device-b"), hasOutbox = true)
        }
        val handler = TextPullHandler()
        val engine = SyncPullEngine(remote, store, listOf(handler))

        engine.pullAll(SyncResult.Mutable())
        engine.pullAll(SyncResult.Mutable())

        assertEquals(2, remote.downloads)
        assertEquals(0, handler.applied)
        assertTrue(store.hasOutbox("book", "book-a"))
    }

    @Test
    fun invalidPayloadDoesNotAdvanceRemoteMarker() = runBlocking {
        val remote = FakeSyncRemoteStore().apply {
            put("books/book-a.json", "invalid", 10L)
        }
        val store = MemoryPullStore()
        val result = SyncResult.Mutable()

        SyncPullEngine(remote, store, listOf(TextPullHandler())).pullAll(result)

        assertEquals(1, result.failed)
        assertEquals(0L, store.metadata("book", "book-a")?.remoteFileModifiedAt ?: 0L)
    }

    @Test
    fun newerRemoteObjectAppliesAndDiscardsLocalOutbox() = runBlocking {
        val remote = FakeSyncRemoteStore().apply {
            put("books/book-a.json", "300|device-b|hash-remote", 10L)
        }
        val store = MemoryPullStore().apply {
            putLocal("book", "book-a", SyncVersion(200L, "device-a"), hasOutbox = true)
        }
        val handler = TextPullHandler()

        SyncPullEngine(remote, store, listOf(handler)).pullAll(SyncResult.Mutable())

        assertEquals(1, handler.applied)
        assertFalse(store.hasOutbox("book", "book-a"))
        assertEquals("hash-remote", store.metadata("book", "book-a")?.lastSyncedHash)
    }

    @Test
    fun olderRemoteDeleteMarkerDoesNotReplaceNewerLocalDeleteVersion() {
        val old = SyncMetadata(
            objectType = "book",
            objectId = "book-a",
            deletedAt = 300L,
            deletedByDeviceId = "device-b",
            dirty = true
        )
        val candidate = SyncRemoteCandidate(
            identity = SyncIdentity("book", "book-a"),
            path = "tombstones/books/book-a.json",
            contentHash = "delete-hash",
            objectVersion = null,
            deleteVersion = SyncVersion(200L, "device-a"),
            payloadJson = "{}",
            lastModifiedAt = 10L
        )

        val merged = mergePullMetadata(old, candidate, applied = false)

        assertEquals(300L, merged.deletedAt)
        assertEquals("device-b", merged.deletedByDeviceId)
        assertTrue(merged.dirty)
        assertEquals(10L, merged.remoteFileModifiedAt)
    }

    @Test
    fun olderRemoteObjectCannotRollbackPreviouslyAppliedRemoteVersion() = runBlocking {
        val remote = FakeSyncRemoteStore().apply {
            put("books/book-a.json", "300|device-b|hash-new", 10L)
        }
        val store = MemoryPullStore()
        val handler = TextPullHandler()
        val engine = SyncPullEngine(remote, store, listOf(handler))
        engine.pullAll(SyncResult.Mutable())

        remote.put("books/book-a.json", "200|device-c|hash-old", 20L)
        engine.pullAll(SyncResult.Mutable())

        assertEquals(1, handler.applied)
        assertEquals(300L, store.metadata("book", "book-a")?.remoteUpdatedAt)
        assertEquals("hash-new", store.metadata("book", "book-a")?.lastSyncedHash)
    }

    @Test
    fun handlerCanDisableModifiedTimeSkippingWithoutReapplyingSameVersion() = runBlocking {
        val remote = FakeSyncRemoteStore().apply {
            put("books/book-a.json", "300|device-b|hash-new", 10L)
        }
        val store = MemoryPullStore()
        val handler = TextPullHandler(usesModifiedTimeMarker = false)
        val engine = SyncPullEngine(remote, store, listOf(handler))

        engine.pullAll(SyncResult.Mutable())
        engine.pullAll(SyncResult.Mutable())

        assertEquals(2, remote.downloads)
        assertEquals(1, handler.applied)
    }

    private class TextPullHandler(
        override val usesModifiedTimeMarker: Boolean = true
    ) : SyncPullHandler {
        var applied = 0
        override val directories: List<String> = listOf("books")

        override fun identity(file: SyncRemoteFile): SyncIdentity? {
            return file.displayName.removeSuffix(".json").takeIf { it.isNotEmpty() }
                ?.let { SyncIdentity("book", it) }
        }

        override fun parse(file: SyncRemoteFile, json: String): SyncRemoteCandidate {
            val parts = json.split('|')
            require(parts.size == 3) { "invalid payload" }
            val identity = requireNotNull(identity(file))
            return SyncRemoteCandidate(
                identity = identity,
                path = file.path,
                contentHash = parts[2],
                objectVersion = SyncVersion(parts[0].toLong(), parts[1]),
                deleteVersion = null,
                payloadJson = json,
                lastModifiedAt = file.lastModifiedAt
            )
        }

        override fun applyRemote(candidate: SyncRemoteCandidate): SyncApplyOutcome {
            applied += 1
            return SyncApplyOutcome.Updated
        }
    }

    private class MemoryPullStore : SyncPullStore {
        private val metadata = linkedMapOf<Pair<String, String>, SyncMetadata>()
        private val outbox = hashSetOf<Pair<String, String>>()

        override fun metadata(identity: SyncIdentity): SyncMetadata? =
            metadata[identity.objectType to identity.objectId]

        fun metadata(objectType: String, objectId: String): SyncMetadata? =
            metadata[objectType to objectId]

        override fun recordRemote(candidate: SyncRemoteCandidate, applied: Boolean) {
            val identity = candidate.identity
            val old = metadata(identity) ?: SyncMetadata(
                objectType = identity.objectType,
                objectId = identity.objectId
            )
            metadata[identity.objectType to identity.objectId] =
                mergePullMetadata(old, candidate, applied)
        }

        override fun discardOutbox(identity: SyncIdentity) {
            outbox.remove(identity.objectType to identity.objectId)
        }

        fun putLocal(
            objectType: String,
            objectId: String,
            version: SyncVersion,
            hasOutbox: Boolean
        ) {
            metadata[objectType to objectId] = SyncMetadata(
                objectType = objectType,
                objectId = objectId,
                localUpdatedAt = version.timestamp,
                dirty = hasOutbox,
                localUpdatedByDeviceId = version.deviceId
            )
            if (hasOutbox) outbox += objectType to objectId
        }

        fun hasOutbox(objectType: String, objectId: String): Boolean =
            objectType to objectId in outbox
    }
}
