package io.wanjuan.app.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.elonen.NanoHTTPD
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.sync.mapper.BookGroupSyncMapper
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.defaultSharedPreferences
import io.wanjuan.app.utils.fromJsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class TestBookGroupSyncInstrumented {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MemoryBookServer()
    private lateinit var client: WebDavSyncClient
    private lateinit var a: Replica
    private lateinit var b: Replica

    @Before
    fun setUp() {
        server.start()
        client = WebDavSyncClient(
            rootUrlProvider = { "http://127.0.0.1:${server.listeningPort}/" },
            authorizationProvider = { Authorization("sync-test", "sync-test") }
        )
        a = Replica("device-a")
        b = Replica("device-b")
        a.db.bookGroupDao.insert(BookGroup(1L, "A only", syncId = "group-a"))
        b.db.bookGroupDao.insert(
            BookGroup(1L, "B first", syncId = "group-b1"),
            BookGroup(2L, "B second", syncId = "group-b2")
        )
    }

    @After
    fun tearDown() {
        if (::a.isInitialized) a.db.close()
        if (::b.isInitialized) b.db.close()
        server.stop()
    }

    @Test
    fun queuedNewBookKeepsMultipleGroupsAcrossDifferentLocalBits() {
        copyGroupsToA()
        val book = newBook(group = 3L)

        a.books.applyRemoteBook(b.enqueue(book))

        assertMembershipOnA(book, listOf("group-b1", "group-b2"))
        assertEquals(6L, a.db.bookDao.getBook(book.bookUrl)!!.group)
    }

    @Test
    fun directUploadKeepsMembershipWhenGroupDetailsArriveLater() = runBlocking {
        val book = newBook(group = 1L)

        b.books.pushBook(book)
        a.books.applyRemoteBook(remoteBook(book))
        val localBit = a.db.bookDao.getBook(book.bookUrl)!!.group
        copyGroupsToA()

        assertMembershipOnA(book, listOf("group-b1"))
        assertEquals(localBit, a.db.bookDao.getBook(book.bookUrl)!!.group)
        assertEquals("B first", a.db.bookGroupDao.getByID(localBit)?.groupName)
    }

    @Test
    fun firstProgressUploadKeepsGroupsAndLaterProgressPreservesRemoteGroups() = runBlocking {
        val preferences = context.defaultSharedPreferences
        val keys = listOf(PreferKey.webDavAccount, PreferKey.webDavPassword, PreferKey.syncBookProgress)
        val saved = preferences.all.filterKeys { it in keys }
        preferences.edit()
            .putString(PreferKey.webDavAccount, "sync-test")
            .putString(PreferKey.webDavPassword, "sync-test")
            .putBoolean(PreferKey.syncBookProgress, true)
            .commit()
        try {
            copyGroupsToA()
            val book = newBook(group = 1L).apply { syncTime = 200L }

            assertTrue(b.books.pushProgress(book))
            val first = remoteBook(book)
            a.books.applyRemoteBook(first)
            assertMembershipOnA(book, listOf("group-b1"))

            val regrouped = first.copy(book = first.book.copy(groupSyncIds = listOf("group-b2")))
            client.upload(bookPath(book), regrouped)
            book.syncTime = 300L
            book.durChapterIndex = 4
            assertTrue(b.books.pushProgress(book))
            val second = remoteBook(book)

            assertEquals(listOf("group-b2"), second.book.groupSyncIds)
            assertEquals(4, second.book.durChapterIndex)
            assertEquals(300L, second.progressUpdatedAt)
        } finally {
            preferences.edit().apply {
                keys.forEach { key ->
                    when (val value = saved[key]) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
        }
    }

    @Test
    fun resyncRepairsUngroupedBookAndPropagatesRegroupingAndExplicitRemoval() {
        copyGroupsToA()
        val book = newBook(group = 1L)
        a.db.bookDao.insert(book.copy(group = 0L))

        a.books.applyRemoteBook(b.enqueue(book))
        assertMembershipOnA(book, listOf("group-b1"))

        book.group = 2L
        a.books.applyRemoteBook(b.enqueue(book))
        assertMembershipOnA(book, listOf("group-b2"))

        book.group = 0L
        a.books.applyRemoteBook(b.enqueue(book))
        assertMembershipOnA(book, emptyList())
        assertEquals(0L, a.db.bookDao.getBook(book.bookUrl)!!.group)
    }

    @Test
    fun fullSyncReuploadsMembershipOmittedByAnOlderClient() = runBlocking {
        copyGroupsToA()
        val book = newBook(group = 1L)
        b.db.bookDao.insert(book)
        val queued = b.enqueue(book)
        val broken = queued.copy(book = queued.book.copy(groupSyncIds = emptyList()))
        client.upload(bookPath(book), broken)
        a.books.applyRemoteBook(remoteBook(book))
        b.db.syncOutboxDao.deleteAll()
        b.db.syncMetadataDao.markClean(SyncObjectType.Book, queued.bookSyncId, SyncPayloadHash.book(broken))
        assertMembershipOnA(book, emptyList())

        b.captureAndUploadBooks()
        a.books.applyRemoteBook(remoteBook(book))

        assertMembershipOnA(book, listOf("group-b1"))
    }

    private fun newBook(group: Long) = Book(
        bookUrl = "sync-test-book",
        origin = "sync-test-source",
        name = "Sync test book",
        author = "Sync test author",
        group = group
    )

    private fun copyGroupsToA() {
        b.db.bookGroupDao.all.forEach { group ->
            a.groups.applyRemote(BookGroupSyncMapper.toPayload(group, SyncVersion(100L, "device-b")))
        }
    }

    private fun assertMembershipOnA(book: Book, expected: List<String>) {
        val received = requireNotNull(a.db.bookDao.getBook(book.bookUrl))
        assertEquals(expected, a.groups.localMaskToRemoteGroupIds(received.group).sorted())
    }

    private fun bookPath(book: Book) = "books/${SyncIds.bookId(book)}.json"

    private suspend fun remoteBook(book: Book): SyncBookPayload =
        requireNotNull(client.download<SyncBookPayload>(bookPath(book)))

    private inner class Replica(private val deviceId: String) {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        private val clock = object : SyncClock {
            override fun now() = 100L
        }
        private val repository = SyncRepository(db, client, clock) { deviceId }
        val groups = BookGroupSyncCoordinator(RoomBookGroupSyncStore(db))
        val books = BookshelfSyncCoordinator(
            client = client,
            repository = repository,
            clock = clock,
            deviceIdProvider = { deviceId },
            groupCoordinator = groups,
            objectApplier = BookshelfObjectApplier(RoomBookshelfSyncStore(db)),
            db = db
        )

        fun enqueue(book: Book): SyncBookPayload {
            books.enqueueBook(book)
            val item = db.syncOutboxDao.pending(50).last { it.objectType == SyncObjectType.Book }
            return GSON.fromJsonObject<SyncBookPayload>(item.payloadJson).getOrThrow()
        }

        suspend fun captureAndUploadBooks() {
            val result = SyncLocalReconciler(
                snapshotSource = RoomSyncSnapshotSource(db, clock, { deviceId }, groups),
                store = RoomSyncReconcileStore(db),
                clock = clock,
                deviceIdProvider = { deviceId },
                managedObjectTypes = setOf(SyncObjectType.Book)
            ).capture()
            assertEquals(1, result.upserts)
            val upload = SyncResult.Mutable()
            repository.flushOutbox(client, upload)
            assertEquals(0, upload.failed)
            assertEquals(1, upload.uploaded)
        }
    }

    private class MemoryBookServer : NanoHTTPD("127.0.0.1", 0) {
        private val objects = ConcurrentHashMap<String, String>()

        override fun serve(session: IHTTPSession): Response = when (session.method) {
            Method.GET -> objects[session.uri]?.let {
                newFixedLengthResponse(Response.Status.OK, "application/json", it)
            } ?: newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Missing")
            Method.PUT -> {
                val bytes = ByteArray(session.headers.getValue("content-length").toInt())
                java.io.DataInputStream(session.inputStream).readFully(bytes)
                objects[session.uri] = bytes.toString(Charsets.UTF_8)
                newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "")
            }
            Method.DELETE -> {
                objects.remove(session.uri)
                newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")
            }
            else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
        }
    }
}
