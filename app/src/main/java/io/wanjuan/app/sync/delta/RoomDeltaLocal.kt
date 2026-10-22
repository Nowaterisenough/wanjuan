package io.wanjuan.app.sync.delta

import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.sync.*
import io.wanjuan.app.sync.local.SyncDeltaCursor
import io.wanjuan.app.sync.local.SyncDeltaState
import io.wanjuan.app.sync.local.SyncDeltaPayloadStore
import io.wanjuan.app.sync.model.*
import io.wanjuan.app.sync.remote.SyncRemoteFile
import io.wanjuan.app.utils.GSON

class RoomDeltaLocal(
    private val db: AppDatabase,
    private val snapshots: SyncLocalSnapshotSource,
    private val books: BookshelfSyncCoordinator,
    private val handlers: List<SyncPullHandler>
) : DeltaLocal {
    private val payloads = SyncDeltaPayloadStore(db.syncDeltaDao)
    override fun transaction(action: () -> Unit) = db.runInTransaction(action)
    override fun states(target: String) = payloads.decode(db.syncDeltaDao.states(target))
    override fun save(state: SyncDeltaState) = payloads.save(state)
    override fun cursor(target: String) = db.syncDeltaDao.cursor(target) ?: SyncDeltaCursor(target)
    override fun saveCursor(cursor: SyncDeltaCursor) = db.syncDeltaDao.saveCursor(cursor)

    override fun snapshots(): Map<SyncIdentity, String> = snapshots.currentSnapshots().associate {
        SyncIdentity(it.objectType, it.objectId) to DeltaCodec.normalize(it.objectType, it.payloadJson)
    }

    override fun canDelete(identity: SyncIdentity): Boolean {
        if (identity.objectType != SyncObjectType.Book) return true
        if (db.syncOutboxDao.latestForObject(identity.objectType, identity.objectId)?.operation == "delete") return true
        return db.bookDao.all.any { SyncIds.bookId(it) == identity.objectId && it.type and BookType.notShelf != 0 }
    }

    override fun consumeLegacyQueue() = db.syncOutboxDao.deleteAll()

    override fun deletions(): Set<SyncIdentity> {
        val result = hashSetOf<SyncIdentity>()
        var after = 0L
        val through = db.syncOutboxDao.lastId()
        while (true) {
            val batch = db.syncOutboxDao.pendingAfter(after, through)
            if (batch.isEmpty()) break
            batch.filter { it.operation == "delete" }.forEach { result += SyncIdentity(it.objectType, it.objectId) }
            after = batch.last().id
        }
        return result
    }

    override fun apply(identity: SyncIdentity, json: String?, timestamp: Long): SyncApplyOutcome = SyncScope.remoteApply {
        if (identity.objectType == SyncObjectType.Book) {
            if (json == null) return@remoteApply books.applyDeltaDelete(identity.objectId)
            val payload = GSON.fromJson(DeltaCodec.payload(identity.objectType, json, timestamp), SyncBookPayload::class.java)
            val book = payload.book
            require(payload.bookSyncId == identity.objectId && SyncIds.bookId(io.wanjuan.app.data.entities.Book(
                bookUrl = book.bookUrl, origin = book.origin, name = book.name, author = book.author, originName = book.originName
            )) == identity.objectId) {
                "Delta book identity mismatch"
            }
            return@remoteApply books.applyDeltaBook(payload)
        }
        val tombstone = json == null
        val path = deltaObjectPath(identity, tombstone)
        val file = SyncRemoteFile(path, path.substringAfterLast('/'), timestamp)
        val handler = handlers.firstOrNull { it.isTombstone == tombstone && path.substringBeforeLast('/') in it.directories && it.identity(file) == identity }
            ?: return@remoteApply SyncApplyOutcome.Skipped
        val payload = if (json == null) GSON.toJson(SyncTombstonePayload(identity.objectType, identity.objectId, timestamp, "delta"))
            else DeltaCodec.preserveLocalFields(identity.objectType, DeltaCodec.payload(identity.objectType, json, timestamp),
                snapshots.currentSnapshots(setOf(identity.objectType)).firstOrNull { it.objectId == identity.objectId }?.payloadJson)
        handler.applyRemote(handler.parse(file, payload))
    }
}

internal fun deltaObjectPath(identity: SyncIdentity, tombstone: Boolean = false): String {
    val directory = when (identity.objectType) {
        SyncObjectType.Book -> "books"
        SyncObjectType.BookGroup -> "bookGroups"
        SyncObjectType.BookSource -> "bookSources"
        SyncObjectType.RssSource -> "rssSources"
        SyncObjectType.RuleSub -> "ruleSubs"
        in DeltaCodec.orderTypes -> "order"
        else -> error("Unsupported delta object")
    }
    return (if (tombstone) "tombstones/" else "") + "$directory/${identity.objectId}.json"
}
