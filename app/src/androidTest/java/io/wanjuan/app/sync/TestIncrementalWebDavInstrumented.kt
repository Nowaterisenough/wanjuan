package io.wanjuan.app.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.elonen.NanoHTTPD
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.remote.CachingSyncRemoteStore
import io.wanjuan.app.sync.remote.FileSyncRemoteContentCache
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.ui.main.bookshelf.displayText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class TestIncrementalWebDavInstrumented {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun etagCacheReadsOnlyChangedBodiesEvenWithinSameModificationSecond() = runBlocking {
        val server = DavServer()
        val cache = File(context.cacheDir, "incremental-dav-test-${System.nanoTime()}")
        server.start()
        try {
            server.put("/sync/v1/books/a.json", "first")
            val raw = WebDavSyncClient(
                { "http://127.0.0.1:${server.listeningPort}/" }, { Authorization("test", "test") }
            )
            val client = CachingSyncRemoteStore(raw, FileSyncRemoteContentCache(cache))
            client.ensureDirs()
            server.lists.set(0)
            var file = client.snapshot(listOf("books", "tombstones/books", "books")).getValue("books").single()
            assertEquals(2, server.lists.get())
            assertFalse(client.readJson(file).fromCache)
            assertEquals(1, server.gets.get())
            file = client.snapshot(listOf("books")).getValue("books").single()
            assertTrue(client.readJson(file).fromCache)
            assertEquals(1, server.gets.get())
            server.put("/sync/v1/books/a.json", "other")
            file = client.snapshot(listOf("books")).getValue("books").single()
            assertEquals("other", client.readJson(file).json)
            assertEquals(2, server.gets.get())
            client.uploadJson("books/a.json", "local")
            file = client.snapshot(listOf("books")).getValue("books").single()
            assertTrue(client.readJson(file).fromCache)
            assertEquals(2, server.gets.get())
            assertFalse(server.directories.any { "assets" in it || "devices" in it || "rssSources" in it })
            val library = SyncAssetCoordinator(raw).remoteBookWebDav()!!
            assertTrue(library.getRemoteBookList(library.rootBookUrl).isEmpty())
            assertTrue("/sync/v1/assets/library/" in server.directories)
        } finally {
            server.stop()
            cache.deleteRecursively()
        }
    }

    @Test
    fun bookOnlyCaptureDoesNotLoadSourceRuleTables() {
        val sourceQueries = AtomicInteger()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> if (sql.lowercase().contains("from book_sources")) sourceQueries.incrementAndGet() },
                Executor { it.run() }).build()
        try {
            db.bookDao.insert(Book(bookUrl = "test-book", origin = "test", name = "Test"))
            db.bookSourceDao.insert(BookSource(bookSourceUrl = "https://example.test", bookSourceName = "Test"))
            sourceQueries.set(0)
            val snapshots = RoomSyncSnapshotSource(db, object : SyncClock { override fun now() = 100L }, { "device" },
                BookGroupSyncCoordinator(RoomBookGroupSyncStore(db))).currentSnapshots(setOf(SyncObjectType.Book, SyncObjectType.BookshelfOrder))
            assertEquals(setOf(SyncObjectType.Book, SyncObjectType.BookshelfOrder), snapshots.map { it.objectType }.toSet())
            assertEquals(0, sourceQueries.get())
        } finally { db.close() }
    }

    @Test
    fun progressPresentationContainsStageAndActualTransferCounts() {
        val reading = SyncProgress(SyncPhase.ReadingChanges, completed = 20, total = 500, downloaded = 1, reused = 19)
            .displayText(context)
        assertTrue(reading.contains("20/500"))
        assertTrue(reading.contains("1"))
        assertTrue(reading.contains("19"))
        val uploading = SyncProgress(SyncPhase.Uploading, completed = 2, total = 3).displayText(context)
        assertTrue(uploading.contains("2/3"))
    }

    private class DavServer : NanoHTTPD("127.0.0.1", 0) {
        private val bodies = ConcurrentHashMap<String, String>()
        private val versions = ConcurrentHashMap<String, Int>()
        val directories = ConcurrentHashMap.newKeySet<String>().apply { add("/") }
        val gets = AtomicInteger()
        val lists = AtomicInteger()
        fun put(path: String, body: String) {
            bodies[path] = body
            versions.merge(path, 1, Int::plus)
            val segments = path.substringBeforeLast('/').split('/').filter(String::isNotEmpty)
            var directory = "/"
            for (segment in segments) { directory += "$segment/"; directories += directory }
        }

        override fun serve(session: IHTTPSession): Response {
            val path = session.uri
            return when (session.method) {
                Method.PROPFIND -> {
                    val bytes = ByteArray(session.headers["content-length"]?.toIntOrNull() ?: 0)
                    DataInputStream(session.inputStream).readFully(bytes)
                    lists.incrementAndGet()
                    if (path !in directories) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "missing")
                    val entries = bodies.keys.filter { it.substringBeforeLast('/') + "/" == path }.sorted().joinToString("") {
                        """<d:response><d:href>$it</d:href><d:propstat><d:prop><d:resourcetype/><d:getetag>&quot;v${versions[it]}&quot;</d:getetag><d:getlastmodified>Thu, 22 Oct 2026 00:00:00 GMT</d:getlastmodified><d:getcontentlength>${bodies[it]!!.length}</d:getcontentlength></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"""
                    }
                    newFixedLengthResponse(Response.Status.MULTI_STATUS, "application/xml", """<d:multistatus xmlns:d="DAV:">$entries</d:multistatus>""")
                }
                Method.MKCOL -> {
                    directories += path
                    newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "")
                }
                Method.GET -> {
                    gets.incrementAndGet()
                    bodies[path]?.let { newFixedLengthResponse(Response.Status.OK, "application/json", it).apply {
                        addHeader("ETag", "\"v${versions[path]}\"")
                    } } ?: newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "missing")
                }
                Method.PUT -> {
                    val bytes = ByteArray(session.headers.getValue("content-length").toInt())
                    DataInputStream(session.inputStream).readFully(bytes)
                    put(path, bytes.toString(Charsets.UTF_8))
                    newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "").apply {
                        addHeader("ETag", "\"v${versions[path]}\"")
                    }
                }
                else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
            }
        }
    }
}
