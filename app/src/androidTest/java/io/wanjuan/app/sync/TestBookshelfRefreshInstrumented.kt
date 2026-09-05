package io.wanjuan.app.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.data.entities.BookProgress
import io.wanjuan.app.lib.webdav.Authorization
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
            db, client, clock, deviceIdProvider = { id }
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
