package io.wanjuan.app.sync.delta

import io.wanjuan.app.constant.BookType
import io.wanjuan.app.sync.SyncIdentity
import io.wanjuan.app.sync.SyncRemoteCandidate
import io.wanjuan.app.sync.model.SyncBookGroupPayload
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.utils.GSON

/** Collect the exact versions already read by the migration pull; never fetch them a second time. */
class LegacyDeltaReader {
    private val winners = linkedMapOf<SyncIdentity, SyncRemoteCandidate>()

    @Synchronized
    fun accept(candidate: SyncRemoteCandidate) {
        val previous = winners[candidate.identity]
        if (previous == null || candidate.version > previous.version ||
            (candidate.version == previous.version && candidate.deleteVersion != null)) {
            winners[candidate.identity] = candidate
        }
    }

    @Synchronized
    fun snapshots(): Map<SyncIdentity, String> {
        val groups = winners.values.filter { it.identity.objectType == SyncObjectType.BookGroup && it.deleteVersion == null }
            .map { GSON.fromJson(it.payloadJson, SyncBookGroupPayload::class.java) }
        return buildMap {
            winners.values.filter { it.deleteVersion == null }.forEach { candidate ->
                var json = candidate.payloadJson
                if (candidate.identity.objectType == SyncObjectType.Book) {
                    var book = GSON.fromJson(json, SyncBookPayload::class.java)
                    if (book.book.type and BookType.notShelf != 0) return@forEach
                    if (book.schemaVersion < 2) {
                        book = book.copy(schemaVersion = 2, book = book.book.copy(groupSyncIds = groups.filter {
                            it.legacyGroupId > 0 && book.book.group and it.legacyGroupId != 0L
                        }.map { it.groupSyncId }))
                    }
                    json = GSON.toJson(book)
                }
                put(candidate.identity, DeltaCodec.normalize(candidate.identity.objectType, json))
            }
        }
    }
}
