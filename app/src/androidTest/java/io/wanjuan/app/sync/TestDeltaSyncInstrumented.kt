package io.wanjuan.app.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.DatabaseMigrations
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.RssSource
import io.wanjuan.app.data.entities.RuleSub
import io.wanjuan.app.sync.delta.*
import io.wanjuan.app.sync.remote.*
import io.wanjuan.app.sync.local.SyncDeltaPayloadStore
import io.wanjuan.app.sync.local.SyncDeltaPayloadCodec
import io.wanjuan.app.utils.GSON
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestDeltaSyncInstrumented {
    private val replicas = arrayListOf<Replica>()
    private val remote = Remote()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun close() { replicas.forEach { it.db.close() } }

    @Test fun migrationPreservesExistingOutboxAndAddsDurableCursor() {
        val name = "delta-migration-95-97"
        createOldDatabase(name, 95)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("INSERT INTO sync_outbox (objectType,objectId,operation,createdAt,attemptCount,versionTimestamp,versionDeviceId) VALUES ('book','pending','delete',123,0,123,'device')")
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*DatabaseMigrations.migrations).build()
        migrated.openHelper.writableDatabase.apply {
            query("select count(*) from sync_outbox where objectId = 'pending'").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            query("select count(*) from sync_delta_cursor").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
        migrated.close()
        context.deleteDatabase(name)
    }

    @Test fun migrationRescuesOversizedV96RowsWithoutReadingThemThroughCursorWindow() {
        val name = "delta-migration-large-96-97"
        createOldDatabase(name, 96)
        val large = "{\"value\":\"" + "\u4e66\ud83d\udcd6".repeat(180000) + "\"}"
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("""INSERT INTO sync_delta_state (target,objectType,objectId,remoteJson,observedJson,pendingId,pendingJson,
                pendingDelete,pendingCreate,inFlightChange,inFlightValue) VALUES ('test','book','large',?,?,'pending',?,0,0,null,null)""",
                arrayOf(large, large, large))
            old.execSQL("INSERT INTO sync_delta_cursor VALUES ('test',123,'existing-block',1)")
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.migrations).build()
        val raw = db.syncDeltaDao.states("test")
        assertTrue(raw.single().remoteJson!!.startsWith(SyncDeltaPayloadCodec.PREFIX))
        val decoded = SyncDeltaPayloadStore(db.syncDeltaDao).decode(raw).single()
        assertEquals(large, decoded.remoteJson)
        assertEquals(large, decoded.pendingJson)
        assertEquals("pending", decoded.pendingId)
        assertEquals(123L, db.syncDeltaDao.cursor("test")!!.timestamp)
        db.close()
        context.deleteDatabase(name)
    }

    private fun createOldDatabase(name: String, version: Int) {
        context.deleteDatabase(name)
        // Build the exported old schema directly: the test runtime's Room bundle serializer
        // is incompatible with its transitive kotlinx.serialization runtime.
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("io.wanjuan.app.data.AppDatabase/$version.json").bufferedReader().use { it.readText() }
        val schema = GSON.fromJson(json, JsonObject::class.java).getAsJsonObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            schema.getAsJsonArray("entities").forEach { entry ->
                val table = entry.asJsonObject
                val tableName = table["tableName"].asString
                old.execSQL(table["createSql"].asString.replace("\${TABLE_NAME}", tableName))
                table.getAsJsonArray("indices")?.forEach { index ->
                    old.execSQL(index.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", tableName))
                }
            }
            schema.getAsJsonArray("views").forEach { entry ->
                val view = entry.asJsonObject
                old.execSQL(view["createSql"].asString.replace("\${VIEW_NAME}", view["viewName"].asString))
            }
            schema.getAsJsonArray("setupQueries").forEach { old.execSQL(it.asString) }
            old.version = version
        }
    }

    @Test fun largePayloadsRoundTripThroughRealRoomWithoutOversizedStateRows() = runBlocking {
        val a = replica(); val b = replica()
        val large = "\u4e66\ud83d\udcd6".repeat(180000)
        val book = Book(bookUrl = "large-book", origin = "source", name = "Large", customIntro = large)
        a.db.bookDao.insert(book)
        var result = a.engine.flush()
        repeat(10) { if (result.pending > 0) result = a.engine.flush() }
        assertEquals(0, result.pending)
        b.engine.pull()
        assertEquals(large, b.db.bookDao.getBook(book.bookUrl)!!.customIntro)
        assertEquals(0, a.engine.flush().uploaded)
        assertEquals(0, b.engine.flush().uploaded)
        assertTrue(a.db.syncDeltaDao.states("target").all { (it.remoteJson?.length ?: 0) < 40000 })
    }

    @Test fun roomProgressCatalogMetadataAndDeletionUseOneDeltaPipeline() = runBlocking {
        val a = replica(); val b = replica()
        val books = (1..20).map { Book(bookUrl = "book-$it", origin = "source", name = "Book $it", author = "Author",
            totalChapterNum = 120, durChapterIndex = 1, syncTime = 100, lastCheckTime = 100, durChapterTime = 100) }
        a.db.bookDao.insert(*books.toTypedArray())
        a.engine.flush(); b.engine.pull()
        assertEquals(20, b.db.bookDao.all.size)
        repeat(3) { round ->
            a.db.bookDao.all.forEach { a.db.bookDao.update(it.copy(lastCheckTime = 900L + round,
                durChapterTime = 900L + round, lastCheckCount = round, type = it.type or BookType.updateError)) }
            assertEquals(0, a.engine.flush().uploaded)
        }
        val first = a.db.bookDao.all.first()
        a.db.bookDao.update(first.copy(durChapterIndex = 10, durChapterPos = 20, syncTime = 1000))
        assertEquals(1, a.engine.flush().uploaded)
        b.engine.pull()
        assertEquals(10, b.db.bookDao.getBook(first.bookUrl)!!.durChapterIndex)
        assertEquals(0, b.engine.flush().uploaded)
        a.db.bookDao.update(a.db.bookDao.getBook(first.bookUrl)!!.copy(totalChapterNum = 121, latestChapterTitle = "New chapter"))
        assertEquals(1, a.engine.flush().uploaded)
        b.engine.pull()
        assertEquals(121, b.db.bookDao.getBook(first.bookUrl)!!.totalChapterNum)
        assertTrue(a.books.deleteLocalBook(a.db.bookDao.getBook(first.bookUrl)!!))
        a.engine.flush(); b.engine.pull()
        assertNull(b.db.bookDao.getBook(first.bookUrl))
        assertEquals(0, b.engine.flush().uploaded)
    }

    @Test fun groupsAndOrderPreserveDeviceLocalBitsAndDoNotEcho() = runBlocking {
        val a = replica(); val b = replica()
        a.db.bookGroupDao.insert(BookGroup(1, "First", order = 1, syncId = "g1"), BookGroup(2, "Second", order = 0, syncId = "g2"))
        b.db.bookGroupDao.insert(BookGroup(1, "Local", syncId = "local"))
        a.db.bookDao.insert(Book(bookUrl = "book", origin = "source", name = "Book", group = 3))
        a.engine.flush(); b.engine.pull(); b.engine.flush(); a.engine.pull()
        assertEquals(setOf("g1", "g2"), b.groups.localMaskToRemoteGroupIds(b.db.bookDao.all.single().group).toSet())
        val first = a.db.bookGroupDao.all.first { it.syncId == "g1" }
        a.db.bookGroupDao.update(first.copy(groupName = "Renamed"))
        a.engine.flush(); b.engine.pull()
        assertEquals(a.db.bookGroupDao.all.sortedBy { it.order }.map { it.syncId },
            b.db.bookGroupDao.all.sortedBy { it.order }.map { it.syncId })
        assertEquals(0, b.engine.flush().uploaded)
    }

    @Test fun sourcesRssAndSubscriptionsShareCaptureAndCursor() = runBlocking {
        val a = replica(); val b = replica()
        a.db.bookSourceDao.insert(BookSource(bookSourceUrl = "https://example.test/source", bookSourceName = "Source", customOrder = 7))
        a.db.rssSourceDao.insert(RssSource(sourceUrl = "https://example.test/rss", sourceName = "RSS"))
        a.db.ruleSubDao.insert(RuleSub(name = "Subscription", url = "https://example.test/sub"))
        a.engine.flush(); b.engine.pull()
        assertEquals("Source", b.db.bookSourceDao.all.single().bookSourceName)
        assertEquals("RSS", b.db.rssSourceDao.all.single().sourceName)
        assertEquals("Subscription", b.db.ruleSubDao.all.single().name)
        a.db.bookSourceDao.update(a.db.bookSourceDao.all.single().copy(enabled = false))
        a.db.rssSourceDao.update(a.db.rssSourceDao.all.single().copy(sourceName = "Edited RSS"))
        a.db.ruleSubDao.update(a.db.ruleSubDao.all.single().copy(name = "Edited sub"))
        assertEquals(3, a.engine.flush().uploaded)
        b.engine.pull()
        assertFalse(b.db.bookSourceDao.all.single().enabled)
        assertEquals("Edited RSS", b.db.rssSourceDao.all.single().sourceName)
        assertEquals("Edited sub", b.db.ruleSubDao.all.single().name)
        assertEquals(0, b.engine.flush().uploaded)
    }

    @Test fun reopeningEngineKeepsCursorAndOnlyReadsHeadWhenUnchanged() = runBlocking {
        val a = replica()
        a.db.bookDao.insert(Book(bookUrl = "book", origin = "source", name = "Book"))
        a.engine.flush()
        val before = a.db.syncDeltaDao.cursor("target")!!
        remote.reads.clear()
        val restarted = DeltaEngine(a.local, DeltaRemote(remote), "target", a.clock)
        assertEquals(0, restarted.pull().downloaded)
        assertEquals(before, a.db.syncDeltaDao.cursor("target"))
        assertEquals(listOf(DeltaRemote.HEAD), remote.reads)
    }

    @Test fun addingThenRemovingUnpublishedBookCancelsBothChanges() = runBlocking {
        val a = replica()
        a.engine.pull()
        val book = Book(bookUrl = "temporary", origin = "source", name = "Temporary")
        a.db.bookDao.insert(book)
        a.engine.capture()
        a.books.deleteLocalBook(book)
        assertEquals(0, a.engine.flush().uploaded)
        assertFalse(a.engine.hasPending())
    }

    @Test fun missingCachedBookIsRestoredWithoutPublishingDeletion() = runBlocking {
        val a = replica()
        val book = Book(bookUrl = "book", origin = "source", name = "Book")
        a.db.bookDao.insert(book); a.engine.flush()
        a.db.bookDao.delete(book)
        a.engine.pull()
        assertNotNull(a.db.bookDao.getBook(book.bookUrl))
        assertEquals(0, a.engine.flush().uploaded)
    }

    @Test fun replacingBookUrlDeletesOldIdentityWithoutLosingTheNewBook() = runBlocking {
        val a = replica(); val b = replica()
        val old = Book(bookUrl = "old", origin = "source", name = "Book", author = "Author")
        a.db.bookDao.insert(old); a.engine.flush(); b.engine.pull()
        val replacement = old.copy(bookUrl = "new", origin = "other-source")
        a.db.bookDao.replace(old, replacement)
        a.engine.flush(); b.engine.pull()
        assertNull(b.db.bookDao.getBook(old.bookUrl))
        assertNotNull(b.db.bookDao.getBook(replacement.bookUrl))
        assertEquals(0, b.engine.flush().uploaded)
    }

    private fun replica() = Replica().also { replicas += it }
    private inner class Replica {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val clock = object : SyncClock { override fun now() = 1000L }
        val groups = BookGroupSyncCoordinator(RoomBookGroupSyncStore(db))
        val repository = SyncRepository(db, remote, clock, deviceIdProvider = { "device" })
        val books = BookshelfSyncCoordinator(remote, repository, clock, { "device" }, groups,
            BookshelfObjectApplier(RoomBookshelfSyncStore(db)), db)
        // Source coordinators are adapted to this replica rather than the application's database.
        private val handlers = productionSyncPullHandlers(groups, books,
            BookSourceSyncCoordinator(remote, repository, clock, { "device" }, db),
            RssSourceSyncCoordinator(remote, repository, clock, { "device" }, RssSourceSyncApplier(RoomRssSourceSyncStore(db))),
            RuleSubSyncCoordinator(remote, repository, clock, { "device" }, db))
        val local = RoomDeltaLocal(db, RoomSyncSnapshotSource(db, clock, { "device" }, groups, trackBookVersions = false), books, handlers)
        val engine = DeltaEngine(local, DeltaRemote(remote), "target", clock)
    }

    private class Remote : SyncRemoteStore {
        val objects = linkedMapOf<String, String>()
        val reads = arrayListOf<String>()
        override suspend fun ensureDirs() = Unit
        override suspend fun list(relativeDir: String): List<SyncRemoteFile> = emptyList()
        override suspend fun downloadJson(relativePath: String): String? { reads += relativePath; return objects[relativePath] }
        override suspend fun uploadJson(relativePath: String, json: String) { objects[relativePath] = json }
        override suspend fun readForUpdate(path: String) = SyncRemoteRead(downloadJson(path))
        override suspend fun compareAndSetJson(path: String, previous: SyncRemoteRead, json: String): Boolean {
            if (objects[path] != previous.json) return false
            objects[path] = json
            return true
        }
    }
}
