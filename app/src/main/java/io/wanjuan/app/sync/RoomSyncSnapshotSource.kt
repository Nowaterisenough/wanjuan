package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.help.book.isNotShelf
import io.wanjuan.app.sync.mapper.BookGroupSyncMapper
import io.wanjuan.app.sync.mapper.BookSourceSyncMapper
import io.wanjuan.app.sync.mapper.BookSyncMapper
import io.wanjuan.app.sync.mapper.RssSourceSyncMapper
import io.wanjuan.app.sync.mapper.RuleSubSyncMapper
import io.wanjuan.app.sync.merge.BookSyncMerge
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncSnapshot
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.utils.GSON

class RoomSyncSnapshotSource(
    private val db: AppDatabase,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String,
    private val groupCoordinator: BookGroupSyncCoordinator,
    private val trackBookVersions: Boolean = true
) : SyncLocalSnapshotSource {
    private val bookState = BookSyncState(db, clock, deviceIdProvider, groupCoordinator)

    override fun currentSnapshots(): List<SyncSnapshot> = currentSnapshots(SyncObjectSelection.all)

    override fun currentSnapshots(objectTypes: Set<String>): List<SyncSnapshot> = buildList {
        visitPayloads(objectTypes) { type, id, payload, version, hash ->
            add(SyncSnapshot(type, id, hash(), GSON.toJson(payload), version))
        }
    }

    override fun currentPayloads(objectTypes: Set<String>): Map<SyncIdentity, String> = buildMap {
        visitPayloads(objectTypes) { type, id, payload, _, _ ->
            put(SyncIdentity(type, id), GSON.toJson(payload))
        }
    }

    private fun visitPayloads(
        objectTypes: Set<String>,
        emit: (String, String, Any, SyncVersion, () -> String) -> Unit
    ) {
        if (SyncObjectType.Book in objectTypes || SyncObjectType.BookGroup in objectTypes) groupCoordinator.ensureStableIds()
        val version = SyncVersion(clock.now(), deviceIdProvider())
        val groups = if (objectTypes.any { it == SyncObjectType.BookGroup || it == SyncObjectType.BookGroupOrder || it == SyncObjectType.Book })
            db.bookGroupDao.all.filter { it.groupId > 0L } else emptyList()
        val books = if (objectTypes.any { it == SyncObjectType.Book || it == SyncObjectType.BookshelfOrder })
            db.bookDao.all.filterNot { it.isNotShelf } else emptyList()
        val bookSources = if (objectTypes.any { it == SyncObjectType.BookSource || it == SyncObjectType.BookSourceOrder })
            db.bookSourceDao.all else emptyList()
        val rssSources = if (objectTypes.any { it == SyncObjectType.RssSource || it == SyncObjectType.RssSourceOrder })
            db.rssSourceDao.all else emptyList()
        val ruleSubs = if (objectTypes.any { it == SyncObjectType.RuleSub || it == SyncObjectType.RuleSubOrder })
            db.ruleSubDao.all else emptyList()
        fun order(type: String, id: String, items: List<String>) {
            val payload = SyncOrderPayload(version.timestamp, version.deviceId, items)
            emit(type, id, payload, version) { SyncPayloadHash.order(payload) }
        }
        if (SyncObjectType.BookGroup in objectTypes) groups.forEach { group ->
            val payload = BookGroupSyncMapper.toPayload(group, version)
            emit(SyncObjectType.BookGroup, group.syncId, payload, version) { SyncPayloadHash.bookGroup(payload) }
        }
        if (SyncObjectType.BookSource in objectTypes) bookSources.forEach { source ->
            val payload = BookSourceSyncMapper.toPayload(source, version.deviceId, version.timestamp)
            emit(SyncObjectType.BookSource, payload.sourceHash, payload, version) { SyncPayloadHash.bookSource(payload) }
        }
        if (SyncObjectType.RssSource in objectTypes) rssSources.forEach { source ->
            val payload = RssSourceSyncMapper.toPayload(source, version)
            emit(SyncObjectType.RssSource, payload.sourceHash, payload, version) { SyncPayloadHash.rssSource(payload) }
        }
        if (SyncObjectType.RuleSub in objectTypes) ruleSubs.forEach { sub ->
            val payload = RuleSubSyncMapper.toPayload(sub, version.deviceId, version.timestamp)
            emit(SyncObjectType.RuleSub, payload.ruleSubHash, payload, version) { SyncPayloadHash.ruleSub(payload) }
        }
        if (SyncObjectType.Book in objectTypes) books.forEach { book ->
            val payload = if (trackBookVersions) bookState.capture(book, repairAcknowledgedSnapshot = true)
                else BookSyncMapper.toBookPayload(book, "", 0, 0,
                    groupSyncIds = groups.filter { book.group and it.groupId != 0L }.sortedBy { it.order }
                        .map { it.syncId }.filter(String::isNotBlank))
            emit(SyncObjectType.Book, payload.bookSyncId, payload, BookSyncMerge.version(payload)) { SyncPayloadHash.book(payload) }
        }
        if (SyncObjectType.BookGroupOrder in objectTypes) order(
            SyncObjectType.BookGroupOrder,
            "bookGroups",
            groups.sortedBy { it.order }.map { it.syncId }
        )
        if (SyncObjectType.BookshelfOrder in objectTypes) order(
            SyncObjectType.BookshelfOrder,
            "bookshelf",
            books.sortedBy { it.order }.map(SyncIds::bookId)
        )
        if (SyncObjectType.BookSourceOrder in objectTypes) order(
            SyncObjectType.BookSourceOrder,
            "bookSources",
            bookSources.sortedBy { it.customOrder }.map(SyncIds::bookSourceId)
        )
        if (SyncObjectType.RssSourceOrder in objectTypes) order(
            SyncObjectType.RssSourceOrder,
            "rssSources",
            rssSources.sortedBy { it.customOrder }.map { SyncIds.rssSourceId(it.sourceUrl) }
        )
        if (SyncObjectType.RuleSubOrder in objectTypes) order(
            SyncObjectType.RuleSubOrder,
            "ruleSubs",
            ruleSubs.sortedBy { it.customOrder }.map(SyncIds::ruleSubId)
        )
    }
}
