package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RoomSyncChangeSource(
    private val db: AppDatabase,
    private val onInvalidated: (Set<String>) -> Unit = {}
) {
    fun fingerprints(): Flow<String> = flow {
        var books = ""
        var groups = ""
        var sources = ""
        db.invalidationTracker.createFlow("books", "book_groups", "book_sources").collect { tables ->
            onInvalidated(tables)
            val fingerprint = db.runInTransaction<String> {
                if ("books" in tables) books = SyncChangeFingerprint.books(db.bookDao.all)
                if ("book_groups" in tables) groups = SyncChangeFingerprint.groups(db.bookGroupDao.all)
                if ("book_sources" in tables) sources = SyncChangeFingerprint.sources(db.bookSourceDao.all)
                SyncCanonicalJson.hash(listOf(books, groups, sources))
            }
            emit(fingerprint)
        }
    }
}
