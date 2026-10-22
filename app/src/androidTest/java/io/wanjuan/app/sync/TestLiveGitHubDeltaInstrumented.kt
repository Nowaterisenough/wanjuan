package io.wanjuan.app.sync

import android.os.Bundle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.sync.delta.*
import io.wanjuan.app.sync.remote.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Explicit opt-in only. The real credential stays on the host; a scoped gateway authenticates requests. */
@RunWith(AndroidJUnit4::class)
class TestLiveGitHubDeltaInstrumented {
    @Test fun migrateRealLegacyDataThenPublishOnlyToIsolatedDirectory() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Live gateway arguments were not supplied", !args.getString("liveApiBase").isNullOrBlank())
        val apiBase = requireNotNull(args.getString("liveApiBase")).toHttpUrl()
        require(apiBase.host == "10.0.2.2" || apiBase.host == "127.0.0.1") { "Live tests require a local scoped gateway" }
        val owner = requireNotNull(args.getString("liveOwner"))
        val repo = requireNotNull(args.getString("liveRepo"))
        val branch = requireNotNull(args.getString("liveBranch"))
        val sourceDirectory = requireNotNull(args.getString("liveSourceDirectory"))
        val testDirectory = requireNotNull(args.getString("liveTestDirectory"))
        val gatewayKey = requireNotNull(args.getString("liveGatewayKey"))
        require(branch.startsWith("codex/sync-check-") && testDirectory.startsWith("_sync_probe_"))
        val cacheRoot = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "live-sync-${UUID.randomUUID()}")
        val replicas = arrayListOf<Replica>()
        try {
            withTimeout(15 * 60_000L) {
                val readClient = GitHubSyncClient(GitHubSyncConfig(owner, repo, gatewayKey, branch, sourceDirectory), apiBase = apiBase)
                val imported = Replica(readClient, cacheRoot, migrate = true).also { replicas += it }
                val started = System.nanoTime()
                val result = imported.engine.pull()
                assertEquals(0, result.failed)
                assertTrue(imported.db.syncDeltaDao.cursor("live")!!.legacyImported)
                assertTrue(imported.db.bookDao.all.isNotEmpty())
                report("Legacy import completed: books=${imported.db.bookDao.all.size}, " +
                    "sources=${imported.db.bookSourceDao.all.size}, downloaded=${result.downloaded}, " +
                    "elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
                assertEquals(1, imported.migrationRuns)
                val repeated = imported.engine.pull()
                assertEquals(0, repeated.downloaded)
                assertEquals(1, imported.migrationRuns)
                report("Repeated pull completed without repeating legacy import")

                val writeClient = GitHubSyncClient(GitHubSyncConfig(owner, repo, gatewayKey, branch, testDirectory), apiBase = apiBase)
                val a = Replica(writeClient, cacheRoot, migrate = false).also { replicas += it }
                val runId = UUID.randomUUID().toString()
                val probe = Book(bookUrl = "diagnostic://$runId", origin = "diagnostic-source", name = "Sync diagnostic $runId")
                a.db.bookDao.insert(probe)
                assertTrue(a.engine.flush().uploaded > 0)
                val current = a.db.bookDao.getBook(probe.bookUrl)!!
                a.db.bookDao.update(current.copy(customTag = "changed-field-only"))
                assertEquals(1, a.engine.flush().uploaded)
                val remote = DeltaRemote(writeClient)
                val change = remote.block(remote.head().second).changes.single()
                assertEquals(listOf(listOf("b", "h")), change.fields.map { it.path })
                report("Real GitHub field-delta publication verified")

                val large = "\u4e66\ud83d\udcd6".repeat(180000)
                assertTrue(large.toByteArray(Charsets.UTF_8).size > 1024 * 1024)
                a.db.bookDao.update(a.db.bookDao.getBook(probe.bookUrl)!!.copy(customIntro = large))
                assertEquals(1, a.engine.flush().uploaded)
                val secondClient = GitHubSyncClient(GitHubSyncConfig(owner, repo, gatewayKey, branch, testDirectory), apiBase = apiBase)
                val b = Replica(secondClient, cacheRoot, migrate = false).also { replicas += it }
                assertTrue(b.engine.pull().downloaded > 0)
                val received = b.db.bookDao.getBook(probe.bookUrl)!!
                assertEquals("changed-field-only", received.customTag)
                assertEquals(DeltaRemote.checksum(large.toByteArray()), DeltaRemote.checksum(received.customIntro!!.toByteArray()))
                assertEquals(0, a.engine.flush().uploaded)
                assertEquals(0, b.engine.flush().uploaded)
                report("Real GitHub >1MiB chunk round-trip and no-op publication verified")
            }
        } finally {
            replicas.forEach { it.db.close() }
            cacheRoot.deleteRecursively()
        }
    }

    private fun report(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "LIVE_SYNC: $message\n") })
    }

    private inner class Replica(remote: SyncRemoteStore, cacheRoot: File, migrate: Boolean) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        private val clock = SystemSyncClock
        private val groups = BookGroupSyncCoordinator(RoomBookGroupSyncStore(db))
        private val repository = SyncRepository(db, remote, clock, deviceIdProvider = { "live-test" })
        private val books = BookshelfSyncCoordinator(remote, repository, clock, { "live-test" }, groups,
            BookshelfObjectApplier(RoomBookshelfSyncStore(db)), db)
        private val handlers = productionSyncPullHandlers(groups, books,
            BookSourceSyncCoordinator(remote, repository, clock, { "live-test" }, db),
            RssSourceSyncCoordinator(remote, repository, clock, { "live-test" }, RssSourceSyncApplier(RoomRssSourceSyncStore(db))),
            RuleSubSyncCoordinator(remote, repository, clock, { "live-test" }, db))
        private var lastReported = -1
        private val progress = SyncProgressTracker { state ->
            if (state.phase == SyncPhase.MigratingReading && state.completed / 100 > lastReported) {
                lastReported = state.completed / 100
                report("Migration read ${state.completed}/${state.total}, network=${state.downloaded}, cache=${state.reused}")
            }
        }.apply { begin() }
        var migrationRuns = 0
        private val snapshots = RoomSyncSnapshotSource(db, clock, { "live-test" }, groups)
        private val local = RoomDeltaLocal(db, RoomSyncSnapshotSource(db, clock, { "live-test" }, groups, false), books, handlers)
        val engine = DeltaEngine(local, DeltaRemote(remote), "live", clock, legacy = {
            migrationRuns++
            if (!migrate) emptyMap() else LegacySyncImporter(
                CachingSyncRemoteStore(remote, FileSyncRemoteContentCache(cacheRoot)), RoomSyncPullStore(db), handlers,
                prepare = { SyncLocalReconciler(snapshots, RoomSyncReconcileStore(db), clock, { "live-test" }, SyncObjectSelection.all).capture() },
                progress = progress).read()
        }, progress = progress)
    }
}
