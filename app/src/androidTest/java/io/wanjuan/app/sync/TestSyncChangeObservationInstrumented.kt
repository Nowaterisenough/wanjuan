package io.wanjuan.app.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.model.SyncObjectType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestSyncChangeObservationInstrumented {
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
