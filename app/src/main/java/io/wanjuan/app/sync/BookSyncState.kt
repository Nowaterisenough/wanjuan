package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.mapper.BookSyncMapper
import io.wanjuan.app.sync.merge.BookSyncMerge
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncVersion

/** Component fingerprints retain the version across captures and failed upload retries. */
class BookSyncState(
    private val db: AppDatabase,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String,
    private val groups: BookGroupSyncCoordinator
) {
    fun capture(
        book: Book,
        newLocalBook: Boolean = true,
        repairAcknowledgedSnapshot: Boolean = false
    ): SyncBookPayload {
        val deviceId = deviceIdProvider()
        val payload = BookSyncMapper.toBookPayload(
            book, deviceId, 0L, 0L,
            groupSyncIds = groups.localMaskToRemoteGroupIds(book.group)
        )
        val known = db.syncMetadataDao.get(SyncObjectType.Book, payload.bookSyncId)
        val bookHash = SyncPayloadHash.book(payload)
        val unchangedSinceSync = known?.lastSyncedHash == bookHash
        val initialShelfTime = when {
            unchangedSinceSync -> maxOf(known!!.localUpdatedAt, known.remoteUpdatedAt)
            newLocalBook -> clock.now()
            else -> 0L
        }
        var shelf = componentVersion(
            Shelf, payload.bookSyncId, BookSyncMerge.shelfHash(payload.book),
            initialShelfTime,
            useDetectionTime = true
        )
        val catalog = componentVersion(
            Catalog, payload.bookSyncId, BookSyncMerge.catalogHash(payload.book),
            book.lastCheckTime
        )
        val progress = componentVersion(
            Progress, payload.bookSyncId, BookSyncMerge.progressHash(payload.book),
            payload.progressUpdatedAt
        )
        // Older clients could acknowledge a book while dropping its stable group IDs.
        val observed = db.syncMetadataDao.get(Snapshot, payload.bookSyncId)
        if (repairAcknowledgedSnapshot && known?.dirty == false && known.lastSyncedHash != null &&
            !unchangedSinceSync && observed?.lastSyncedHash == bookHash
        ) {
            shelf = SyncVersion(maxOf(clock.now(), shelf.timestamp + 1), deviceId)
            save(Shelf, payload.bookSyncId, BookSyncMerge.shelfHash(payload.book), shelf)
        }
        save(Snapshot, payload.bookSyncId, bookHash, maxOf(shelf, catalog, progress))
        return payload.copy(
            updatedByDeviceId = shelf.deviceId,
            shelfUpdatedAt = shelf.timestamp,
            catalogUpdatedAt = catalog.timestamp,
            progressUpdatedAt = progress.timestamp,
            shelfUpdatedByDeviceId = shelf.deviceId,
            catalogUpdatedByDeviceId = catalog.deviceId,
            progressUpdatedByDeviceId = progress.deviceId
        )
    }

    fun record(payload: SyncBookPayload) {
        save(Shelf, payload.bookSyncId, BookSyncMerge.shelfHash(payload.book), BookSyncMerge.shelfVersion(payload))
        save(Catalog, payload.bookSyncId, BookSyncMerge.catalogHash(payload.book), BookSyncMerge.catalogVersion(payload))
        save(Progress, payload.bookSyncId, BookSyncMerge.progressHash(payload.book), BookSyncMerge.progressVersion(payload))
        save(Snapshot, payload.bookSyncId, SyncPayloadHash.book(payload), BookSyncMerge.version(payload))
    }

    private fun componentVersion(
        type: String, id: String, hash: String, initialTime: Long,
        useDetectionTime: Boolean = false
    ): SyncVersion {
        val previous = db.syncMetadataDao.get(type, id)
        val previousVersion = SyncVersion(
            previous?.localUpdatedAt ?: 0L, previous?.localUpdatedByDeviceId.orEmpty()
        )
        if (previous?.lastSyncedHash == hash) return previousVersion
        val timestamp = if (previous?.lastSyncedHash != null) {
            maxOf(if (useDetectionTime) clock.now() else initialTime, previousVersion.timestamp + 1)
        } else {
            maxOf(initialTime, previousVersion.timestamp)
        }
        val version = SyncVersion(timestamp, deviceIdProvider())
        save(type, id, hash, version)
        return version
    }

    private fun save(type: String, id: String, hash: String, version: SyncVersion) {
        db.syncMetadataDao.insert(SyncMetadata(
            objectType = type,
            objectId = id,
            localUpdatedAt = version.timestamp,
            localUpdatedByDeviceId = version.deviceId,
            lastSyncedHash = hash
        ))
    }

    private companion object {
        const val Shelf = "bookShelf"
        const val Catalog = "bookCatalog"
        const val Progress = "bookProgress"
        const val Snapshot = "bookSnapshot"
    }
}
