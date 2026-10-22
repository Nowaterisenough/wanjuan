package io.wanjuan.app.sync.delta

import io.wanjuan.app.sync.*
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.sync.remote.*
import io.wanjuan.app.utils.GSON
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TestLegacySyncImporter {
    @Test fun hundredsOfLegacyObjectsAreReadOnceAndProgressIsVisible() = runBlocking {
        val remote = Remote(599)
        val states = arrayListOf<SyncProgress>()
        val progress = SyncProgressTracker { states += it }.apply { begin() }
        val imported = importer(remote, Cache(), progress).read()
        assertEquals(599, imported.size)
        assertEquals(1, remote.indexRequests)
        assertEquals(599, remote.reads.values.sum())
        assertTrue(remote.reads.values.all { it == 1 })
        assertTrue(states.any { it.phase == SyncPhase.MigratingIndex })
        assertTrue(states.any { it.phase == SyncPhase.MigratingReading && it.completed > 0 && it.total == 599 })
        assertEquals(599, progress.snapshot().downloaded)
    }

    @Test fun failedMigrationRetriesOnlyUncachedFiles() = runBlocking {
        val remote = Remote(50).apply { failing = "books/book-4.json" }
        val cache = Cache()
        assertTrue(runCatching { importer(remote, cache).read() }.isFailure)
        assertEquals(2, remote.reads["books/book-4.json"])
        remote.failing = null
        val states = arrayListOf<SyncProgress>()
        val progress = SyncProgressTracker { states += it }.apply { begin() }
        assertEquals(50, importer(remote, cache, progress).read().size)
        assertEquals(3, remote.reads["books/book-4.json"])
        assertTrue(remote.reads.filterKeys { it != "books/book-4.json" }.values.all { it == 1 })
        assertEquals(1, progress.snapshot().downloaded)
        assertEquals(49, progress.snapshot().reused)
    }

    @Test fun alreadyAppliedObjectsStillEnterMigrationBaseline() = runBlocking {
        val remote = Remote(1)
        val metadata = SyncMetadata(SyncObjectType.Book, "book-0", remoteFileModifiedAt = 123)
        val handler = handler()
        val store = object : SyncPullStore {
            override fun metadata(identity: SyncIdentity) = metadata
            override fun recordRemote(candidate: SyncRemoteCandidate, applied: Boolean) = Unit
            override fun discardOutbox(identity: SyncIdentity) = Unit
        }
        val reader = LegacyDeltaReader()
        val noRevision = object : SyncRemoteStore by remote {
            override suspend fun list(relativeDir: String) = remote.list(relativeDir).map { it.copy(revision = null) }
            override suspend fun snapshot(directories: List<String>, onProgress: (Int, Int) -> Unit) =
                directories.associateWith { list(it) }
        }
        SyncPullEngine(noRevision, store, listOf(handler), onCandidate = reader::accept).pullAll(io.wanjuan.app.sync.model.SyncResult.Mutable())
        assertEquals(1, reader.snapshots().size)
    }

    private fun importer(remote: Remote, cache: Cache, progress: SyncProgressTracker = SyncProgressTracker()): LegacySyncImporter {
        val store = object : SyncPullStore {
            override fun metadata(identity: SyncIdentity): SyncMetadata? = null
            override fun recordRemote(candidate: SyncRemoteCandidate, applied: Boolean) = Unit
            override fun discardOutbox(identity: SyncIdentity) = Unit
        }
        return LegacySyncImporter(CachingSyncRemoteStore(remote, cache), store, listOf(handler()), {}, progress)
    }

    private fun handler() = object : SyncPullHandler {
        override val directories = listOf("books")
        override fun identity(file: SyncRemoteFile) = SyncIdentity(SyncObjectType.Book, file.path.substringAfterLast('/').removeSuffix(".json"))
        override fun parse(file: SyncRemoteFile, json: String): SyncRemoteCandidate {
            val payload = GSON.fromJson(json, SyncBookPayload::class.java)
            return SyncRemoteCandidate(identity(file), file.path, SyncPayloadHash.book(payload),
                SyncVersion(100, "remote"), null, json, file.lastModifiedAt)
        }
        override fun applyRemote(candidate: SyncRemoteCandidate) = SyncApplyOutcome.Updated
    }

    private class Remote(count: Int) : SyncRemoteStore {
        private val objects = (0 until count).associate { index ->
            "books/book-$index.json" to GSON.toJson(SyncBookPayload("book-$index", SyncBook(bookUrl = "book-$index"), 100, 100, updatedByDeviceId = "remote"))
        }
        val reads = java.util.concurrent.ConcurrentHashMap<String, Int>()
        var indexRequests = 0
        var failing: String? = null
        override suspend fun ensureDirs() = Unit
        override suspend fun list(relativeDir: String): List<SyncRemoteFile> {
            indexRequests++
            return objects.keys.map { SyncRemoteFile(it, it.substringAfterLast('/'), 123, "version-1") }
        }
        override suspend fun downloadJson(relativePath: String): String? {
            reads.merge(relativePath, 1, Int::plus)
            if (relativePath == failing) throw IOException("Injected read failure")
            return objects[relativePath]
        }
        override suspend fun readJson(file: SyncRemoteFile) = SyncRemoteRead(downloadJson(file.path), "version-1")
        override suspend fun uploadJson(relativePath: String, json: String): Unit = error("Migration must be read-only")
    }

    private class Cache : SyncRemoteContentCache {
        private val values = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, String>()
        override fun read(path: String, revision: String) = values[path to revision]
        override fun write(path: String, revision: String, json: String) { values[path to revision] = json }
        override fun remove(path: String) { values.keys.removeIf { it.first == path } }
    }
}
