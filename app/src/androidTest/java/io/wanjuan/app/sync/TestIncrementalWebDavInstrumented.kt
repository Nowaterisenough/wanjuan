package io.wanjuan.app.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.sync.model.SyncObjectType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class TestIncrementalWebDavInstrumented {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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

}
