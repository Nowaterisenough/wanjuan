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
    private val groupCoordinator: BookGroupSyncCoordinator
) : SyncLocalSnapshotSource {
    private val bookState = BookSyncState(db, clock, deviceIdProvider, groupCoordinator)

    override fun currentSnapshots(): List<SyncSnapshot> = currentSnapshots(SyncObjectSelection.all)

    override fun currentSnapshots(objectTypes: Set<String>): List<SyncSnapshot> {
        if (SyncObjectType.Book in objectTypes || SyncObjectType.BookGroup in objectTypes) groupCoordinator.ensureStableIds()
        val version = SyncVersion(clock.now(), deviceIdProvider())
        val groups = if (objectTypes.any { it == SyncObjectType.BookGroup || it == SyncObjectType.BookGroupOrder })
            db.bookGroupDao.all.filter { it.groupId > 0L } else emptyList()
        val books = if (objectTypes.any { it == SyncObjectType.Book || it == SyncObjectType.BookshelfOrder })
            db.bookDao.all.filterNot { it.isNotShelf } else emptyList()
        val bookSources = if (objectTypes.any { it == SyncObjectType.BookSource || it == SyncObjectType.BookSourceOrder })
            db.bookSourceDao.all else emptyList()
        val rssSources = if (objectTypes.any { it == SyncObjectType.RssSource || it == SyncObjectType.RssSourceOrder })
            db.rssSourceDao.all else emptyList()
        val ruleSubs = if (objectTypes.any { it == SyncObjectType.RuleSub || it == SyncObjectType.RuleSubOrder })
            db.ruleSubDao.all else emptyList()
        return buildList {
            if (SyncObjectType.BookGroup in objectTypes) groups.forEach { group ->
                val payload = BookGroupSyncMapper.toPayload(group, version)
                add(snapshot(SyncObjectType.BookGroup, group.syncId, payload, SyncPayloadHash.bookGroup(payload), version))
            }
            if (SyncObjectType.BookSource in objectTypes) bookSources.forEach { source ->
                val payload = BookSourceSyncMapper.toPayload(source, version.deviceId, version.timestamp)
                add(snapshot(SyncObjectType.BookSource, payload.sourceHash, payload, SyncPayloadHash.bookSource(payload), version))
            }
            if (SyncObjectType.RssSource in objectTypes) rssSources.forEach { source ->
                val payload = RssSourceSyncMapper.toPayload(source, version)
                add(snapshot(SyncObjectType.RssSource, payload.sourceHash, payload, SyncPayloadHash.rssSource(payload), version))
            }
            if (SyncObjectType.RuleSub in objectTypes) ruleSubs.forEach { sub ->
                val payload = RuleSubSyncMapper.toPayload(sub, version.deviceId, version.timestamp)
                add(snapshot(SyncObjectType.RuleSub, payload.ruleSubHash, payload, SyncPayloadHash.ruleSub(payload), version))
            }
            if (SyncObjectType.Book in objectTypes) books.forEach { book ->
                val payload = bookState.capture(book, repairAcknowledgedSnapshot = true)
                add(snapshot(SyncObjectType.Book, payload.bookSyncId, payload, SyncPayloadHash.book(payload), BookSyncMerge.version(payload)))
            }
            if (SyncObjectType.BookGroupOrder in objectTypes) add(orderSnapshot(
                SyncObjectType.BookGroupOrder,
                "bookGroups",
                groups.sortedBy { it.order }.map { it.syncId },
                version
            ))
            if (SyncObjectType.BookshelfOrder in objectTypes) add(orderSnapshot(
                SyncObjectType.BookshelfOrder,
                "bookshelf",
                books.sortedBy { it.order }.map(SyncIds::bookId),
                version
            ))
            if (SyncObjectType.BookSourceOrder in objectTypes) add(orderSnapshot(
                SyncObjectType.BookSourceOrder,
                "bookSources",
                bookSources.sortedBy { it.customOrder }.map(SyncIds::bookSourceId),
                version
            ))
            if (SyncObjectType.RssSourceOrder in objectTypes) add(orderSnapshot(
                SyncObjectType.RssSourceOrder,
                "rssSources",
                rssSources.sortedBy { it.customOrder }.map { SyncIds.rssSourceId(it.sourceUrl) },
                version
            ))
            if (SyncObjectType.RuleSubOrder in objectTypes) add(orderSnapshot(
                SyncObjectType.RuleSubOrder,
                "ruleSubs",
                ruleSubs.sortedBy { it.customOrder }.map(SyncIds::ruleSubId),
                version
            ))
        }
    }

    private fun orderSnapshot(
        objectType: String,
        objectId: String,
        items: List<String>,
        version: SyncVersion
    ): SyncSnapshot {
        val payload = SyncOrderPayload(version.timestamp, version.deviceId, items)
        return snapshot(objectType, objectId, payload, SyncPayloadHash.order(payload), version)
    }

    private fun snapshot(
        objectType: String,
        objectId: String,
        payload: Any,
        contentHash: String,
        version: SyncVersion
    ) = SyncSnapshot(objectType, objectId, contentHash, GSON.toJson(payload), version)
}
