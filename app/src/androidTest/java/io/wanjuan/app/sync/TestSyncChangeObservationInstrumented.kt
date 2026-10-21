package io.wanjuan.app.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.model.SyncObjectType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestSyncChangeObservationInstrumented {
    @Test
    fun roomChangesObserveBookSourceSwitchAndBothKindsOfGroups() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val signatures = Channel<String>(Channel.UNLIMITED)
        val job = launch {
            RoomSyncChangeSource(db).fingerprints().distinctUntilChanged().collect { signatures.send(it) }
        }
        try {
            var previous = withTimeout(5000) { signatures.receive() }
            suspend fun changed(action: () -> Unit) {
                action()
                val next = withTimeout(5000) { signatures.receive() }
                assertNotEquals(previous, next)
                previous = next
            }
            val book = Book(bookUrl = "test-book", origin = "test-source", name = "Test novel")
            changed { db.bookDao.insert(book) }
            changed { db.bookGroupDao.insert(BookGroup(1L, "Test group")) }
            changed { book.group = 1L; db.bookDao.update(book) }
            changed { db.bookGroupDao.insert(BookGroup(1L, "Renamed group")) }
            val source = BookSource(bookSourceUrl = "https://example.test", bookSourceName = "Test source")
            changed { db.bookSourceDao.insert(source) }
            changed { source.enabled = false; db.bookSourceDao.update(source) }
            changed { db.bookSourceDao.delete(source.bookSourceUrl) }
            changed {
                db.runInTransaction {
                    db.bookDao.delete(book)
                    db.bookDao.insert(book.copy(bookUrl = "replacement-book", origin = "replacement-source"))
                }
            }
            val replacement = db.bookDao.all.single()
            replacement.durChapterIndex = 3
            replacement.lastCheckTime = 100L
            db.bookDao.update(replacement)
            assertNull(withTimeoutOrNull(300) { signatures.receive() })
        } finally {
            job.cancel()
            db.close()
        }
    }

    @Test
    fun changingRemoteInvalidatesAcknowledgementsButPreservesComponentClocksAndTombstones() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.syncMetadataDao.insert(SyncMetadata(SyncObjectType.Book, "book", localUpdatedAt = 100,
                lastSyncedHash = "ack", remoteFileModifiedAt = 50))
            db.syncMetadataDao.insert(SyncMetadata("bookShelf", "book", localUpdatedAt = 100, lastSyncedHash = "component"))
            db.syncMetadataDao.insert(SyncMetadata(SyncObjectType.Book, "deleted", deletedAt = 200, dirty = true))
            db.syncMetadataDao.resetRemoteState(setOf(SyncObjectType.Book))
            val book = db.syncMetadataDao.get(SyncObjectType.Book, "book")!!
            assertTrue(book.dirty)
            assertNull(book.lastSyncedHash)
            assertEquals(0L, book.remoteFileModifiedAt)
            assertEquals(100L, book.localUpdatedAt)
            assertEquals("component", db.syncMetadataDao.get("bookShelf", "book")!!.lastSyncedHash)
            assertEquals(200L, db.syncMetadataDao.get(SyncObjectType.Book, "deleted")!!.deletedAt)
        } finally { db.close() }
    }
}
