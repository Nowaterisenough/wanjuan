package io.wanjuan.app.sync

import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.sync.mapper.BookGroupSyncMapper
import io.wanjuan.app.sync.model.SyncBookGroupPayload
import io.wanjuan.app.sync.model.SyncOrderPayload

interface BookGroupSyncStore {
    fun allGroups(): List<BookGroup>

    fun allBooks(): List<Book>

    fun nextGroupId(): Long

    fun insertGroup(group: BookGroup)

    fun updateGroup(group: BookGroup)

    fun deleteGroup(group: BookGroup)

    fun updateBook(book: Book)

    fun runInTransaction(block: () -> Unit) = block()
}

class RoomBookGroupSyncStore(
    private val db: AppDatabase = appDb
) : BookGroupSyncStore {
    override fun allGroups(): List<BookGroup> = db.bookGroupDao.all

    override fun allBooks(): List<Book> = db.bookDao.all

    override fun nextGroupId(): Long = db.bookGroupDao.getUnusedId()

    override fun insertGroup(group: BookGroup) = db.bookGroupDao.insert(group)

    override fun updateGroup(group: BookGroup) = db.bookGroupDao.update(group)

    override fun deleteGroup(group: BookGroup) = db.bookGroupDao.delete(group)

    override fun updateBook(book: Book) = db.bookDao.update(book)

    override fun runInTransaction(block: () -> Unit) = db.runInTransaction(block)
}

class BookGroupSyncCoordinator(
    private val store: BookGroupSyncStore = RoomBookGroupSyncStore()
) {
    private val legacyIdToSyncId = linkedMapOf<Long, String>()

    fun ensureStableIds() {
        store.runInTransaction {
            store.allGroups()
                .filter { it.groupId > 0L && it.syncId.isBlank() }
                .forEach { group ->
                    group.syncId = SyncIds.existingGroupId(group.groupName)
                    store.updateGroup(group)
                }
        }
    }

    fun localMaskToRemoteGroupIds(mask: Long): List<String> {
        ensureStableIds()
        return store.allGroups()
            .asSequence()
            .filter { it.groupId > 0L && mask and it.groupId != 0L }
            .sortedBy { it.order }
            .map { it.syncId }
            .filter { it.isNotBlank() }
            .toList()
    }

    fun remoteGroupIdsToLocalMask(groupSyncIds: List<String>): Long {
        ensureStableIds()
        var mask = 0L
        store.runInTransaction {
            groupSyncIds.distinct().forEach { syncId ->
                val group = findBySyncId(syncId) ?: BookGroup(
                    groupId = store.nextGroupId(),
                    groupName = syncId,
                    order = nextOrder(),
                    syncId = syncId
                ).also(store::insertGroup)
                mask = mask or group.groupId
            }
        }
        return mask
    }

    fun remoteLegacyMaskToLocalMask(legacyMask: Long): Long {
        val syncIds = legacyIdToSyncId
            .filterKeys { legacyId -> legacyId > 0L && legacyMask and legacyId != 0L }
            .values
            .toList()
        return remoteGroupIdsToLocalMask(syncIds)
    }

    fun applyRemote(payload: SyncBookGroupPayload): BookGroup {
        lateinit var applied: BookGroup
        store.runInTransaction {
            ensureStableIds()
            if (payload.legacyGroupId > 0L) {
                legacyIdToSyncId[payload.legacyGroupId] = payload.groupSyncId
            }
            val current = findBySyncId(payload.groupSyncId)
            applied = BookGroupSyncMapper.toEntity(
                payload = payload,
                localGroupId = current?.groupId ?: store.nextGroupId()
            )
            if (current == null) {
                store.insertGroup(applied)
            } else {
                store.updateGroup(applied)
            }
        }
        return applied
    }

    fun applyRemoteDelete(groupSyncId: String): Boolean {
        var deleted = false
        store.runInTransaction {
            val group = findBySyncId(groupSyncId) ?: return@runInTransaction
            store.allBooks()
                .filter { it.group and group.groupId != 0L }
                .forEach { book ->
                    book.group = book.group and group.groupId.inv()
                    store.updateBook(book)
                }
            store.deleteGroup(group)
            deleted = true
        }
        return deleted
    }

    fun applyRemoteOrder(payload: SyncOrderPayload) {
        store.runInTransaction {
            ensureStableIds()
            val customGroups = store.allGroups().filter { it.groupId > 0L }
            val byId = customGroups.associateBy { it.syncId }
            val ordered = buildList {
                payload.items.distinct().mapNotNullTo(this) { byId[it] }
                customGroups.sortedBy { it.order }.forEach { group ->
                    if (none { it.syncId == group.syncId }) add(group)
                }
            }
            ordered.forEachIndexed { index, group ->
                if (group.order != index) {
                    group.order = index
                    store.updateGroup(group)
                }
            }
        }
    }

    private fun findBySyncId(syncId: String): BookGroup? =
        store.allGroups().firstOrNull { it.groupId > 0L && it.syncId == syncId }

    private fun nextOrder(): Int =
        (store.allGroups().filter { it.groupId > 0L }.maxOfOrNull { it.order } ?: -1) + 1
}
