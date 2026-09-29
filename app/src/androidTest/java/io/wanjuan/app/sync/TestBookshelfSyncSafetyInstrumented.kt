package io.wanjuan.app.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.sync.mapper.BookSyncMapper
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncTombstonePayload
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
class TestBookshelfSyncSafetyInstrumented {
    private val replicas = arrayListOf<Replica>()
    private val remote = MemoryRemote()

    @After
    fun tearDown() = replicas.forEach { it.db.close() }

    @Test
    fun otherSourceAndItsTombstoneCannotReplaceLocalBookOrCascadeChapters() = runBlocking {
        val a = replica("a")
        val local = book("local").copy(durChapterIndex = 90, customTag = "keep")
        a.db.bookDao.insert(local)
        a.db.bookChapterDao.insert(BookChapter(url = "chapter", bookUrl = local.bookUrl, title = "Chapter"))
        assertTrue(a.sync().isSuccess)
        val other = local.copy(bookUrl = "other", origin = "other-source", durChapterIndex = 0)
        remote.put(payload(other, 200))
        remote.delete(other, 300)
        a.time = 400

        val result = a.sync()

        assertTrue(result.isSuccess)
        assertEquals(0, result.deleted)
        assertEquals(local.bookUrl, a.db.bookDao.all.single().bookUrl)
        assertEquals(90, a.db.bookDao.all.single().durChapterIndex)
        assertEquals("keep", a.db.bookDao.all.single().customTag)
        assertEquals(1, a.db.bookChapterDao.getChapterList(local.bookUrl).size)
        assertFalse(remote.hasDelete(local))
        assertTrue(a.sync().isSuccess)
        assertFalse(remote.hasDelete(local))
    }

    @Test
    fun sameUrlWithDifferentSourceCannotChangeLocalIdentity() = runBlocking {
        val a = replica("a")
        val local = book()
        a.db.bookDao.insert(local)
        assertTrue(a.sync().isSuccess)
        remote.put(payload(local.copy(origin = "other-source"), 300))

        assertTrue(a.sync().isSuccess)

        assertEquals(local.origin, a.db.bookDao.all.single().origin)
        assertFalse(remote.hasDelete(local))
    }

    @Test
    fun firstSyncDoesNotLetDeletedSourceBlockLiveAlternative() = runBlocking {
        val a = replica("a")
        val old = book("old")
        val live = old.copy(bookUrl = "live", origin = "new-source")
        remote.put(payload(old, 100))
        remote.put(payload(live, 200))
        remote.delete(old, 300)

        assertTrue(a.sync().isSuccess)

        assertEquals(live.bookUrl, a.db.bookDao.all.single().bookUrl)
    }

    @Test
    fun sourceReplacementFinishesInOneSyncEvenWhenOldSourceIsLocal() = runBlocking {
        val a = replica("a")
        val old = book("old")
        a.db.bookDao.insert(old)
        assertTrue(a.sync().isSuccess)
        val live = old.copy(bookUrl = "live", origin = "new-source")
        remote.put(payload(live, 200))
        remote.delete(old, 300)

        assertTrue(a.sync().isSuccess)

        assertEquals(live.bookUrl, a.db.bookDao.all.single().bookUrl)
    }

    @Test
    fun olderTombstoneCannotCascadeChaptersBeforeNewerRemoteObjectArrives() = runBlocking {
        val a = replica("a")
        val book = book()
        a.db.bookDao.insert(book)
        a.db.bookChapterDao.insert(BookChapter(url = "chapter", bookUrl = book.bookUrl, title = "Chapter"))
        assertTrue(a.sync().isSuccess)
        remote.delete(book, 200)
        remote.put(payload(book.copy(customTag = "restored"), 300))

        assertTrue(a.sync().isSuccess)

        assertEquals("restored", a.db.bookDao.all.single().customTag)
        assertEquals(1, a.db.bookChapterDao.getChapterList(book.bookUrl).size)
    }

    @Test
    fun unreadableTombstoneDefersItsBookInsteadOfApplyingIncompleteState() = runBlocking {
        val a = replica("a")
        val book = book()
        remote.put(payload(book, 100))
        remote.objects["tombstones/books/${SyncIds.bookId(book)}.json"] = "invalid"

        assertFalse(a.sync().isSuccess)

        assertEquals(0, a.db.bookDao.allBookCount)
        assertEquals(0, a.db.syncOutboxDao.count())
    }

    @Test
    fun remoteRenameCannotEvictAnotherLocalBook() = runBlocking {
        val a = replica("a")
        val first = book("first")
        val second = book("second").copy(name = "Second")
        a.db.bookDao.insert(first, second)
        assertTrue(a.sync().isSuccess)
        remote.put(payload(second.copy(name = first.name), 300))

        assertTrue(a.sync().isSuccess)

        assertEquals(2, a.db.bookDao.allBookCount)
        assertEquals("Second", a.db.bookDao.getBook(second.bookUrl)!!.name)
    }

    @Test
    fun missingBookIsRecoveredThroughCapturePullAndFlushWithoutPublishingADelete() = runBlocking {
        val a = replica("a")
        val book = book()
        a.db.bookDao.insert(book)
        assertTrue(a.sync().isSuccess)
        a.db.bookDao.delete(book)
        a.time = 900

        val result = a.sync()

        assertTrue(result.isSuccess)
        assertEquals(1, result.inserted)
        assertEquals(book.bookUrl, a.db.bookDao.all.single().bookUrl)
        assertFalse(remote.hasDelete(book))
        assertEquals(0, a.db.syncOutboxDao.count())
    }

    @Test
    fun stalePreviewCannotDeleteABookThatWasAddedToTheShelf() {
        val a = replica("a")
        val shelved = book()
        val preview = shelved.copy(type = shelved.type or BookType.notShelf)
        a.db.bookDao.insert(shelved)

        assertFalse(a.books.deleteLocalBook(preview))

        assertEquals(shelved, a.db.bookDao.all.single())
        assertEquals(0, a.db.syncOutboxDao.count())
    }

    @Test
    fun explicitDeleteSurvivesPullAndReachesOtherReplica() = runBlocking {
        val a = replica("a")
        val b = replica("b")
        val book = book()
        a.db.bookDao.insert(book)
        assertTrue(a.sync().isSuccess)
        assertTrue(b.sync().isSuccess)
        a.time = 300

        assertTrue(a.books.deleteLocalBook(book))
        assertEquals(300L, a.db.syncMetadataDao.get(SyncObjectType.Book, SyncIds.bookId(book))!!.deletedAt)
        assertTrue(a.sync().isSuccess)
        assertTrue(b.sync().isSuccess)

        assertEquals(0, a.db.bookDao.allBookCount)
        assertEquals(0, b.db.bookDao.allBookCount)
        assertTrue(remote.hasDelete(book))
    }

    @Test
    fun readdingIdenticalBookAdvancesVersionBeyondOldTombstone() = runBlocking {
        val a = replica("a")
        val b = replica("b")
        val book = book()
        a.db.bookDao.insert(book)
        assertTrue(a.sync().isSuccess)
        assertTrue(b.sync().isSuccess)
        a.time = 300
        assertTrue(a.books.deleteLocalBook(book))
        assertTrue(a.sync().isSuccess)
        assertTrue(b.sync().isSuccess)
        a.time = 400
        a.db.bookDao.insert(book)

        assertTrue(a.sync().isSuccess)
        assertTrue(b.sync().isSuccess)
        assertTrue(a.sync().isSuccess)

        assertEquals(book.bookUrl, a.db.bookDao.all.single().bookUrl)
        assertEquals(book.bookUrl, b.db.bookDao.all.single().bookUrl)
        assertTrue(remote.book(book).shelfUpdatedAt > 300)
    }

    @Test
    fun editDuringRemoteDownloadIsNotLostToAnOlderDelete() = runBlocking {
        val a = replica("a")
        val book = book()
        a.db.bookDao.insert(book)
        assertTrue(a.sync().isSuccess)
        remote.delete(book, 200)
        remote.beforeDownload = { path ->
            if (path.startsWith("tombstones/")) {
                a.time = 400
                a.db.bookDao.update(a.db.bookDao.all.single().copy(customTag = "edited-during-sync"))
                remote.beforeDownload = null
            }
        }

        assertTrue(a.sync().isSuccess)

        assertEquals("edited-during-sync", a.db.bookDao.all.single().customTag)
        assertEquals("edited-during-sync", remote.book(book).book.customTag)
        assertTrue(remote.book(book).shelfUpdatedAt > 200)
    }

    private fun replica(id: String) = Replica(id).also { replicas += it }

    private fun book(url: String = "book") = Book(
        bookUrl = url, origin = "source", name = "Shared title", author = "Author",
        type = BookType.text, lastCheckTime = 100, latestChapterTime = 100,
        durChapterTime = 100, syncTime = 100, totalChapterNum = 120
    )

    private fun payload(book: Book, time: Long): SyncBookPayload =
        BookSyncMapper.toBookPayload(book, "remote", time, time, groupSyncIds = emptyList())

    private inner class Replica(id: String) {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        var time = 100L
        private val clock = object : SyncClock { override fun now() = time }
        private val client = WebDavSyncClient({ "http://127.0.0.1/" }, { Authorization("test", "test") })
        private val groups = BookGroupSyncCoordinator(RoomBookGroupSyncStore(db))
        val repository = SyncRepository(db, client, clock, onBookUploaded = { books.applyUploadedBook(it) }, deviceIdProvider = { id })
        val books: BookshelfSyncCoordinator by lazy {
            BookshelfSyncCoordinator(client, repository, clock, { id }, groups, BookshelfObjectApplier(RoomBookshelfSyncStore(db)), db)
        }
        val snapshots = RoomSyncSnapshotSource(db, clock, { id }, groups)
        private val reconciler = SyncLocalReconciler(snapshots, RoomSyncReconcileStore(db), clock, { id }, setOf(SyncObjectType.Book))

        suspend fun sync() = SyncOrchestrator(
            remote, SyncCaptureAction { reconciler.capture() },
            SyncPullAction {
                SyncPullEngine(remote, RoomSyncPullStore(db), listOf(bookSyncPullHandler(books), bookSyncDeletePullHandler(books))).pullAll(it)
            },
            SyncFlushAction { repository.flushOutbox(remote, it) }
        ).sync()
    }

    private class MemoryRemote : SyncRemoteStore {
        val objects = linkedMapOf<String, String>()
        var beforeDownload: ((String) -> Unit)? = null

        fun put(payload: SyncBookPayload) {
            objects["books/${payload.bookSyncId}.json"] = GSON.toJson(payload)
        }

        fun delete(book: Book, time: Long) {
            val id = SyncIds.bookId(book)
            objects["tombstones/books/$id.json"] = GSON.toJson(SyncTombstonePayload(SyncObjectType.Book, id, time, "remote"))
        }

        fun hasDelete(book: Book) = "tombstones/books/${SyncIds.bookId(book)}.json" in objects

        fun book(book: Book) = GSON.fromJsonObject<SyncBookPayload>(objects.getValue("books/${SyncIds.bookId(book)}.json")).getOrThrow()

        override suspend fun ensureDirs() = Unit
        override suspend fun list(relativeDir: String) = objects.keys.filter { it.startsWith("$relativeDir/") }
            .map { SyncRemoteFile(it, it.substringAfterLast('/'), 0) }
        override suspend fun downloadJson(relativePath: String): String? {
            beforeDownload?.invoke(relativePath)
            return objects[relativePath]
        }
        override suspend fun uploadJson(relativePath: String, json: String) { objects[relativePath] = json }
    }
}
