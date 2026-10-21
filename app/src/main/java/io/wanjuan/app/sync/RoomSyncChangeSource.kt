package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSyncChangeSource(private val db: AppDatabase) {
    fun fingerprints(): Flow<String> =
        db.invalidationTracker.createFlow("books", "book_groups", "book_sources").map {
            db.runInTransaction<String> {
                SyncChangeFingerprint.of(db.bookDao.all, db.bookGroupDao.all, db.bookSourceDao.all)
            }
        }
}
