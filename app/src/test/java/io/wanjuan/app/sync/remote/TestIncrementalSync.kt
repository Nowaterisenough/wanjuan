package io.wanjuan.app.sync.remote

import io.wanjuan.app.sync.*
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.model.SyncVersion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TestIncrementalSync {
    @Test
    fun fiveHundredUnchangedObjectsUseNoBodyGetsAndOneMutationUsesOne() = runBlocking {
        val remote = Remote()
        repeat(500) { remote.put("books/$it.json", "100|body-$it") }
        val cache = Cache()
        val store = PullStore()
        val states = mutableListOf<SyncProgress>()
        val tracker = SyncProgressTracker { synchronized(states) { states += it } }
        val engine = SyncPullEngine(CachingSyncRemoteStore(remote, cache), store, listOf(Handler()), tracker)
        tracker.begin()
        engine.pullAll(SyncResult.Mutable())
        assertEquals(500, remote.bodyGets.get())
        remote.bodyGets.set(0)
        tracker.begin()
        val unchanged = SyncResult.Mutable()
        engine.pullAll(unchanged)
        assertEquals(0, remote.bodyGets.get())
        assertEquals(0, unchanged.downloaded)
        assertEquals(500, states.last().reused)
        remote.put("books/42.json", "200|edited")
        tracker.begin()
        val changed = SyncResult.Mutable()
        engine.pullAll(changed)
        assertEquals(1, remote.bodyGets.get())
        assertEquals(1, changed.downloaded)
        assertEquals(499, states.last().reused)
        assertEquals(3, remote.listRequests)
    }

    @Test
    fun unchangedCachedBookStillRestoresMissingLocalObject() = runBlocking {
        val remote = Remote().apply { put("books/a.json", "100|body") }
        val handler = Handler()
        val engine = SyncPullEngine(CachingSyncRemoteStore(remote, Cache()), PullStore(), listOf(handler))
        engine.pullAll(SyncResult.Mutable())
        handler.present = false
        remote.bodyGets.set(0)
        engine.pullAll(SyncResult.Mutable())
        assertEquals(0, remote.bodyGets.get())
        assertEquals(2, handler.applied)
    }

    @Test
    fun missingVersionNeverTrustsCacheOrCoarseModificationTime() = runBlocking {
        val remote = Remote().apply { put("books/a.json", "100|body"); versions = false }
        val engine = SyncPullEngine(CachingSyncRemoteStore(remote, Cache()), PullStore(), listOf(Handler()))
        engine.pullAll(SyncResult.Mutable())
        remote.put("books/a.json", "200|same-mtime")
        engine.pullAll(SyncResult.Mutable())
        assertEquals(2, remote.bodyGets.get())
    }

    @Test
    fun writeReceiptsWarmCacheWithoutExtraReadback() = runBlocking {
        val remote = Remote()
        val cached = CachingSyncRemoteStore(remote, Cache())
        cached.uploadJson("books/a.json", "100|body")
        val file = remote.list("books").single()
        assertTrue(cached.readJson(file).fromCache)
        assertEquals(0, remote.bodyGets.get())
        assertEquals("100|body", cached.downloadJson(file.path))
        assertEquals(1, remote.bodyGets.get()) // Direct conflict reads must never use a pull cache.
    }

    @Test
    fun diskCorruptionAndDifferentTargetsCannotHideChanges() {
        val root = Files.createTempDirectory("sync-cache-test").toFile()
        try {
            val first = FileSyncRemoteContentCache(root.resolve("first"))
            val second = FileSyncRemoteContentCache(root.resolve("second"))
            first.write("books/a.json", "v1", "original")
            assertEquals("original", first.read("books/a.json", "v1"))
            assertNull(first.read("books/a.json", "v2"))
            assertNull(second.read("books/a.json", "v1"))
            root.resolve("first").listFiles()!!.single().writeText("partial")
            assertNull(first.read("books/a.json", "v1"))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun cachedObjectStillCompetesWithNewerTombstone() = runBlocking {
        val remote = Remote().apply { put("books/a.json", "100|live") }
        val objectHandler = Handler(conflicts = true)
        val tombstones = Handler(directory = "tombstones/books", conflicts = true, deletion = true)
        val cached = CachingSyncRemoteStore(remote, Cache())
        val engine = SyncPullEngine(cached, PullStore(), listOf(objectHandler, tombstones))
        engine.pullAll(SyncResult.Mutable())
        remote.put("tombstones/books/a.json", "200|deleted")
        remote.bodyGets.set(0)
        val result = SyncResult.Mutable()
        engine.pullAll(result)
        assertEquals(1, remote.bodyGets.get())
        assertEquals(1, result.deleted)
        assertEquals(1, objectHandler.applied)
    }

    @Test
    fun omittedNewerLiveBookIsCheckedBeforeApplyingOlderTombstone() = runBlocking {
        val remote = Remote().apply {
            put("books/a.json", "300|live")
            put("tombstones/books/a.json", "200|deleted")
            omitLiveListing = true
        }
        val live = Handler(conflicts = true)
        val deleted = Handler(directory = "tombstones/books", conflicts = true, deletion = true)
        val result = SyncResult.Mutable()
        SyncPullEngine(CachingSyncRemoteStore(remote, Cache()), PullStore(), listOf(live, deleted)).pullAll(result)
        assertEquals(1, live.applied)
        assertEquals(0, result.deleted)
        assertEquals(2, remote.bodyGets.get())
    }

    @Test
    fun nonGroupedTombstoneAlsoVerifiesOmittedLiveObject() = runBlocking {
        val remote = Remote().apply {
            put("books/a.json", "300|live")
            put("tombstones/books/a.json", "200|deleted")
            omitLiveListing = true
        }
        val live = Handler()
        val deleted = Handler(directory = "tombstones/books", deletion = true)
        val result = SyncResult.Mutable()
        SyncPullEngine(CachingSyncRemoteStore(remote, Cache()), PullStore(), listOf(live, deleted)).pullAll(result)
        assertEquals(1, live.applied)
        assertEquals(0, result.deleted)
        assertEquals(2, remote.bodyGets.get())
    }

    private class Cache : SyncRemoteContentCache {
        val entries = ConcurrentHashMap<String, Pair<String, String>>()
        override fun read(path: String, revision: String) = entries[path]?.takeIf { it.first == revision }?.second
        override fun write(path: String, revision: String, json: String) { entries[path] = revision to json }
        override fun remove(path: String) { entries.remove(path) }
    }

    private class Remote : SyncRemoteStore {
        private val bodies = ConcurrentHashMap<String, String>()
        private val revisions = ConcurrentHashMap<String, Int>()
        val bodyGets = AtomicInteger()
        var listRequests = 0
        var versions = true
        var omitLiveListing = false
        private val written = ConcurrentHashMap<String, SyncRemoteRead>()
        fun put(path: String, json: String) { bodies[path] = json; revisions.merge(path, 1, Int::plus) }
        private fun revision(path: String) = if (versions) "v${revisions[path]}" else null
        override suspend fun ensureDirs() = Unit
        override suspend fun list(relativeDir: String): List<SyncRemoteFile> {
            listRequests++
            if (omitLiveListing && relativeDir == "books") return emptyList()
            return bodies.keys.filter { it.substringBeforeLast('/') == relativeDir }.sorted().map {
                SyncRemoteFile(it, it.substringAfterLast('/'), 1000L, revision(it))
            }
        }
        override suspend fun downloadJson(relativePath: String): String? { bodyGets.incrementAndGet(); return bodies[relativePath] }
        override suspend fun readJson(file: SyncRemoteFile) = SyncRemoteRead(downloadJson(file.path), revision(file.path))
        override suspend fun uploadJson(relativePath: String, json: String) { put(relativePath, json); written[relativePath] = SyncRemoteRead(json, revision(relativePath)) }
        override fun lastWrittenContent(relativePath: String) = written[relativePath]
    }

    private class Handler(
        val directory: String = "books", val conflicts: Boolean = false, val deletion: Boolean = false
    ) : SyncPullHandler {
        var applied = 0
        var present = true
        override val usesModifiedTimeMarker = false
        override val isTombstone get() = deletion
        override val directories = listOf(directory)
        override val conflictGroup get() = if (conflicts) "book" else null
        override fun identity(file: SyncRemoteFile) = SyncIdentity("book", file.displayName.removeSuffix(".json"))
        override fun isLocalObjectPresent(identity: SyncIdentity) = present
        override fun parse(file: SyncRemoteFile, json: String): SyncRemoteCandidate {
            val parts = json.split('|')
            val version = SyncVersion(parts[0].toLong(), "remote")
            return SyncRemoteCandidate(identity(file), file.path, parts[1], if (deletion) null else version,
                if (deletion) version else null, json, file.lastModifiedAt)
        }
        override fun applyRemote(candidate: SyncRemoteCandidate): SyncApplyOutcome {
            applied++
            present = !deletion
            return if (deletion) SyncApplyOutcome.Deleted else SyncApplyOutcome.Updated
        }
    }

    private class PullStore : SyncPullStore {
        val records = ConcurrentHashMap<SyncIdentity, SyncMetadata>()
        override fun metadata(identity: SyncIdentity) = records[identity]
        override fun discardOutbox(identity: SyncIdentity) = Unit
        override fun recordRemote(candidate: SyncRemoteCandidate, applied: Boolean) {
            val old = records[candidate.identity] ?: SyncMetadata(candidate.identity.objectType, candidate.identity.objectId)
            records[candidate.identity] = mergePullMetadata(old, candidate, applied)
        }
    }
}
