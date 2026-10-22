package io.wanjuan.app.sync.delta

import io.wanjuan.app.constant.BookType
import io.wanjuan.app.sync.SyncIdentity
import io.wanjuan.app.sync.SyncPullHandler
import io.wanjuan.app.sync.SyncRemoteCandidate
import io.wanjuan.app.sync.model.SyncBookGroupPayload
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.remote.SyncRemoteStore
import io.wanjuan.app.utils.GSON

/** One-time, read-only import. New clients publish only the v2 journal after cutover. */
class LegacyDeltaReader(private val remote: SyncRemoteStore, private val handlers: List<SyncPullHandler>) {
    suspend fun read(): Map<SyncIdentity, String> {
        val index = remote.snapshot(handlers.flatMap { it.directories }.distinct())
        val winners = linkedMapOf<SyncIdentity, SyncRemoteCandidate>()
        handlers.forEach { handler ->
            handler.directories.forEach { directory ->
                index[directory].orEmpty().forEach { file ->
                    if (handler.identity(file) == null) return@forEach
                    val json = requireNotNull(remote.readJson(file).json) { "Legacy sync object disappeared during import" }
                    val candidate = handler.parse(file, json)
                    val previous = winners[candidate.identity]
                    if (previous == null || candidate.version > previous.version ||
                        (candidate.version == previous.version && candidate.deleteVersion != null)) {
                        winners[candidate.identity] = candidate
                    }
                }
            }
        }
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
