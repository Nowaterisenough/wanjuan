package io.wanjuan.app.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.data.entities.BookProgress
import io.wanjuan.app.help.book.BookCatalogUpdate
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.sync.merge.BookSyncMerge
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.remote.SyncRemoteFile
import io.wanjuan.app.sync.remote.SyncRemoteStore
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestBookshelfRefreshInstrumented {
    private val replicas = arrayListOf<Replica>()
    private val remote = MemoryRemote()

    @After
    fun tearDown() = replicas.forEach { it.db.close() }

    @Test
    fun snapshotRetriesKeepComponentVersionsAndQueueIdentity() {
        val a = replica("a")
        a.db.bookDao.insert(book())
        a.capture()
        val first = a.pendingBook()
        val firstId = a.db.syncOutboxDao.pending().single().id
        a.time = 900L

        a.capture()

        val retried = a.pendingBook()
        assertEquals(first.shelfUpdatedAt, retried.shelfUpdatedAt)
        assertEquals(first.catalogUpdatedAt, retried.catalogUpdatedAt)
        assertEquals(first.progressUpdatedAt, retried.progressUpdatedAt)
        assertEquals(firstId, a.db.syncOutboxDao.pending().single().id)
    }

    @Test
    fun fullRoomPipelineMergesNewCatalogWithNewRemoteProgress() = runBlocking {
        val a = replica("a")
        val b = replica("b")
        a.db.bookDao.insert(book())
        assertTrue(a.sync().isSuccess)
        assertTrue(b.sync().isSuccess)

        a.time = 300L
        a.db.bookDao.update(a.db.bookDao.all.single().copy(durChapterIndex = 99, syncTime = 300, durChapterTime = 300))
        assertTrue(a.sync().isSuccess)
        b.time = 400L
        b.db.bookDao.update(b.db.bookDao.all.single().copy(totalChapterNum = 160, lastCheckTime = 400))

        assertTrue(b.sync().isSuccess)
        assertTrue(a.sync().isSuccess)

        for (replica in listOf(a, b)) {
            val result = replica.db.bookDao.all.single()
            assertEquals(99, result.durChapterIndex)
            assertEquals(300L, result.syncTime)
            assertEquals(160, result.totalChapterNum)
            assertEquals(0, replica.db.syncOutboxDao.count())
        }
        val uploaded = GSON.fromJsonObject<SyncBookPayload>(remote.objects.values.single()).getOrThrow()
        assertEquals(99, uploaded.book.durChapterIndex)
        assertEquals(160, uploaded.book.totalChapterNum)
    }

    @Test
    fun drainUploadsMoreThanFiftyObjectsInOneRun() = runBlocking {
        val a = replica("a")
        repeat(120) { a.db.bookDao.insert(book("book-$it")) }
        a.capture()

        val result = SyncResult.Mutable()
        a.repository.flushOutbox(remote, result)

        assertEquals(120, result.uploaded)
        assertEquals(120, remote.objects.size)
        assertEquals(0, result.pending)
        assertTrue(result.toResult().isSuccess)
    }

    @Test
    fun failedFirstBatchDoesNotStarveLaterItemsOrRetryForever() = runBlocking {
        val a = replica("a")
        repeat(120) { a.db.bookDao.insert(book("book-$it")) }
        a.capture()
        remote.failIds += a.db.syncOutboxDao.pending(50).map { it.objectId }

        val result = SyncResult.Mutable()
        a.repository.flushOutbox(remote, result)

        assertEquals(50, result.failed)
        assertEquals(70, result.uploaded)
        assertEquals(50, result.pending)
        assertFalse(result.toResult().isSuccess)
        assertTrue(a.db.syncOutboxDao.pending(100).all { it.attemptCount == 1 })
        remote.failIds.clear()
        a.capture()
        val retried = SyncResult.Mutable()
        a.repository.flushOutbox(remote, retried)
        assertEquals(50, retried.uploaded)
        assertTrue(retried.toResult().isSuccess)
    }

    @Test
    fun progressResponseCannotRollbackReadingOrOverwriteNewGroup() {
        val a = replica("a")
        val stale = book().copy(syncTime = 100)
        a.db.bookDao.insert(stale.copy(group = 8, customTag = "new", syncTime = 300, durChapterIndex = 60))

        a.books.applyProgress(stale, BookProgress("Book", "Author", 20, 0, 200, "old"))
        assertEquals(60, a.db.bookDao.all.single().durChapterIndex)
        a.books.applyProgress(stale, BookProgress("Book", "Author", 70, 0, 400, "new"))

        val result = a.db.bookDao.all.single()
        assertEquals(70, result.durChapterIndex)
        assertEquals(400L, result.syncTime)
        assertEquals(8L, result.group)
        assertEquals("new", result.customTag)
    }

    @Test
    fun deletedBookIsNotResurrectedByCatalogRequest() {
        val a = replica("a")
        val before = book()
        a.db.bookDao.insert(before)
        a.db.bookDao.delete(before)

        assertNull(BookCatalogUpdate.save(a.db, before, before.copy(totalChapterNum = 150), emptyList()))
        assertEquals(0, a.db.bookDao.allBookCount)
    }

    @Test
    fun uploadAcknowledgementDoesNotResurrectADeletedBook() = runBlocking {
        val a = replica("a")
        val book = book()
        a.db.bookDao.insert(book)
        a.capture()
        remote.beforeUpload = {
            remote.beforeUpload = null
            a.db.bookDao.delete(book)
            a.time = 500
            a.books.enqueueBookDelete(book)
        }

        val result = SyncResult.Mutable()
        a.repository.flushOutbox(remote, result)

        assertEquals(0, a.db.bookDao.allBookCount)
        assertEquals("delete", a.db.syncOutboxDao.pending().single().operation)
        assertEquals(1, result.pending)
    }

    @Test
    fun uploadCompletionKeepsChangesMadeDuringUploadPending() = runBlocking {
        val a = replica("a")
        a.db.bookGroupDao.insert(BookGroup(1, "New group", syncId = "group-new"))
        a.db.bookDao.insert(book())
        a.capture()
        remote.beforeUpload = {
            remote.beforeUpload = null
            a.time = 400
            a.db.bookDao.update(a.db.bookDao.all.single().copy(group = 1))
        }

        val result = SyncResult.Mutable()
        a.repository.flushOutbox(remote, result)

        assertEquals(1L, a.db.bookDao.all.single().group)
        assertEquals(1, result.pending)
        assertTrue(a.db.syncMetadataDao.get(SyncObjectType.Book, SyncIds.bookId(book()))!!.dirty)
        val final = SyncResult.Mutable()
        a.repository.flushOutbox(remote, final)
        assertTrue(final.toResult().isSuccess)
        val uploaded = GSON.fromJsonObject<SyncBookPayload>(remote.objects.values.single()).getOrThrow()
        assertEquals(listOf("group-new"), uploaded.book.groupSyncIds)
    }

    private fun replica(id: String): Replica = Replica(id).also { replicas += it }

    private fun book(url: String = "book") = Book(
        bookUrl = url, origin = "source", name = "Book $url", author = "Author",
        totalChapterNum = 120, durChapterIndex = 79, syncTime = 100,
        durChapterTime = 100, lastCheckTime = 100, latestChapterTime = 100
    )

    private inner class Replica(id: String) {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        var time = 100L
        val clock = object : SyncClock { override fun now() = time }
        val client = WebDavSyncClient({ "http://127.0.0.1/" }, { Authorization("test", "test") })
        val groups = BookGroupSyncCoordinator(RoomBookGroupSyncStore(db))
        val repository: SyncRepository = SyncRepository(
            db, client, clock, onBookUploaded = { books.applyUploadedBook(it) }, deviceIdProvider = { id }
        )
        val books: BookshelfSyncCoordinator by lazy {
            BookshelfSyncCoordinator(client, repository, clock, { id }, groups,
                BookshelfObjectApplier(RoomBookshelfSyncStore(db)), db)
        }
        private val reconciler = SyncLocalReconciler(
            RoomSyncSnapshotSource(db, clock, { id }, groups), RoomSyncReconcileStore(db),
            clock, { id }, setOf(SyncObjectType.Book)
        )
        fun capture() = reconciler.capture()
        fun pendingBook() = GSON.fromJsonObject<SyncBookPayload>(db.syncOutboxDao.pending().single().payloadJson).getOrThrow()
        suspend fun sync() = SyncOrchestrator(
            remote, SyncCaptureAction { capture() },
            SyncPullAction { SyncPullEngine(remote, RoomSyncPullStore(db), listOf(bookSyncPullHandler(books))).pullAll(it) },
            SyncFlushAction { repository.flushOutbox(remote, it) }
        ).sync()
    }

    private class MemoryRemote : SyncRemoteStore {
        val objects = linkedMapOf<String, String>()
        val failIds = hashSetOf<String>()
        var beforeUpload: (() -> Unit)? = null
        override suspend fun ensureDirs() = Unit
        override suspend fun list(relativeDir: String) = objects.keys.filter { it.startsWith("$relativeDir/") }
            .map { SyncRemoteFile(it, it.substringAfterLast('/'), 0) }
        override suspend fun downloadJson(relativePath: String) = objects[relativePath]
        override suspend fun uploadJson(relativePath: String, json: String) {
            beforeUpload?.invoke()
            if (relativePath.substringAfterLast('/').removeSuffix(".json") in failIds) error("Upload failed")
            objects[relativePath] = json
        }
    }
}
