package io.wanjuan.app.sync

import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookProgress
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.sync.mapper.BookSyncMapper
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.merge.SyncMerge
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.utils.NetworkUtils
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface BookshelfSyncStore {
    fun allBooks(): List<Book>

    fun insertBook(book: Book)

    fun updateBook(book: Book)

    fun deleteBook(book: Book)

    fun runInTransaction(block: () -> Unit) = block()
}

class RoomBookshelfSyncStore(
    private val db: AppDatabase = appDb
) : BookshelfSyncStore {
    override fun allBooks(): List<Book> = db.bookDao.all

    override fun insertBook(book: Book) = db.bookDao.insert(book)

    override fun updateBook(book: Book) = db.bookDao.update(book)

    override fun deleteBook(book: Book) = db.bookDao.delete(book)

    override fun runInTransaction(block: () -> Unit) = db.runInTransaction(block)
}

class BookshelfObjectApplier(
    private val store: BookshelfSyncStore = RoomBookshelfSyncStore()
) {
    fun applyRemoteDelete(bookSyncId: String): Boolean {
        var deleted = false
        store.runInTransaction {
            val book = store.allBooks().firstOrNull { SyncIds.bookId(it) == bookSyncId }
                ?: return@runInTransaction
            store.deleteBook(book)
            deleted = true
        }
        return deleted
    }

    fun applyRemoteOrder(payload: SyncOrderPayload) {
        store.runInTransaction {
            val books = store.allBooks()
            val byId = books.associateBy(SyncIds::bookId)
            val ordered = buildList {
                payload.items.distinct().mapNotNullTo(this) { byId[it] }
                books.sortedBy { it.order }.forEach { book ->
                    if (none { it.bookUrl == book.bookUrl }) add(book)
                }
            }
            ordered.forEachIndexed { index, book ->
                if (book.order != index) {
                    book.order = index
                    store.updateBook(book)
                }
            }
        }
    }
}

class BookshelfSyncCoordinator(
    private val client: WebDavSyncClient,
    private val repository: SyncRepository,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String,
    private val groupCoordinator: BookGroupSyncCoordinator = BookGroupSyncCoordinator(),
    private val objectApplier: BookshelfObjectApplier = BookshelfObjectApplier()
) {
    private companion object {
        const val BookShelfClockType = "bookShelf"
        const val BookCatalogClockType = "bookCatalog"
    }

    fun enqueueBookDelete(book: Book) {
        val id = SyncIds.bookId(book)
        val deletedAt = clock.now()
        val deviceId = deviceIdProvider()
        repository.markDirty(
            SyncObjectType.Book,
            id,
            SyncTombstonePayload(
                objectType = SyncObjectType.Book,
                objectId = id,
                deletedAt = deletedAt,
                deletedByDeviceId = deviceId
            ),
            "delete"
        )
    }

    fun enqueueBook(book: Book) {
        val payload = BookSyncMapper.toBookPayload(book, deviceIdProvider(), clock.now(), clock.now())
        recordLocalBookClocks(payload)
        repository.markDirty(SyncObjectType.Book, payload.bookSyncId, payload, "upsert")
    }

    fun enqueueBookshelfOrder(books: List<Book>) {
        val payload = SyncOrderPayload(
            updatedAt = clock.now(),
            updatedByDeviceId = deviceIdProvider(),
            items = books.sortedBy { it.order }.map { SyncIds.bookId(it) }
        )
        repository.markDirty(SyncObjectType.BookshelfOrder, "bookshelf", payload, "order")
    }

    suspend fun pushBook(
        book: Book,
        shelfUpdatedAt: Long = clock.now(),
        catalogUpdatedAt: Long = clock.now()
    ) {
        val payload = BookSyncMapper.toBookPayload(
            book = book,
            deviceId = deviceIdProvider(),
            shelfUpdatedAt = shelfUpdatedAt,
            catalogUpdatedAt = catalogUpdatedAt
        )
        pushBookPayload(payload)
    }

    suspend fun pushBookPayload(payload: SyncBookPayload) {
        recordLocalBookClocks(payload)
        client.upload("books/${payload.bookSyncId}.json", payload)
        runCatching { client.delete("tombstones/books/${payload.bookSyncId}.json") }
    }

    suspend fun pullProgress(book: Book): BookProgress? {
        val id = SyncIds.bookId(book)
        val payload = client.download<SyncBookPayload>("books/$id.json") ?: return null
        if (payload.bookSyncId != id) return null
        val remoteUpdatedAt = payload.effectiveProgressUpdatedAt()
        if (!SyncMerge.remoteProgressWins(book.localProgressUpdatedAt(), remoteUpdatedAt)) {
            return null
        }
        return BookProgress(
            name = payload.book.name,
            author = payload.book.author,
            durChapterIndex = payload.book.durChapterIndex,
            durChapterPos = payload.book.durChapterPos,
            durChapterTime = remoteUpdatedAt,
            durChapterTitle = payload.book.durChapterTitle
        )
    }

    /**
     * Reading progress is one component of the book sync object. Update that component in the
     * existing remote object so a progress upload cannot overwrite newer shelf/catalog fields.
     */
    suspend fun pushProgress(book: Book, toast: Boolean = false, force: Boolean = false): Boolean {
        if (!AppConfig.webDavObjectSync) return false
        if (!NetworkUtils.isAvailable()) return false
        return try {
            val id = SyncIds.bookId(book)
            val localUpdatedAt = book.localProgressUpdatedAt()
                .takeIf { it > 0L }
                ?: book.durChapterTime
            val remote = client.download<SyncBookPayload>("books/$id.json")
            if (!force && remote != null && SyncMerge.remoteProgressWins(
                    localUpdatedAt,
                    remote.effectiveProgressUpdatedAt()
                )
            ) {
                return false
            }
            val deviceId = deviceIdProvider()
            val payload = if (remote == null) {
                BookSyncMapper.toBookPayload(
                    book = book,
                    deviceId = deviceId,
                    shelfUpdatedAt = clock.now(),
                    catalogUpdatedAt = clock.now(),
                    progressUpdatedAt = localUpdatedAt
                )
            } else {
                remote.copy(
                    book = remote.book.withProgressFrom(book, localUpdatedAt),
                    progressUpdatedAt = localUpdatedAt,
                    progressUpdatedByDeviceId = deviceId
                )
            }
            client.upload("books/$id.json", payload)
            runCatching { client.delete("tombstones/books/$id.json") }
            book.syncTime = localUpdatedAt
            true
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e, toast)
            false
        }
    }

    fun applyProgress(book: Book, progress: BookProgress) {
        SyncScope.remoteApply {
            book.durChapterIndex = progress.durChapterIndex
            book.durChapterPos = progress.durChapterPos
            book.durChapterTitle = progress.durChapterTitle
            book.durChapterTime = progress.durChapterTime
            book.syncTime = progress.durChapterTime
            appDb.bookDao.update(book)
        }
    }

    fun applyRemoteBook(payload: SyncBookPayload) {
        repository.applyRemote {
            appDb.runInTransaction {
                val dao = appDb.bookDao
                val local = dao.getBook(payload.book.bookUrl)
                val remote = payload.book.toBook(payload.localGroupMask(groupCoordinator))
                if (local == null) {
                    dao.insert(remote)
                } else {
                    dao.update(remote)
                }
            }
        }
    }

    suspend fun pushBookshelfOrder(books: List<Book>) {
        val payload = SyncOrderPayload(
            updatedAt = clock.now(),
            updatedByDeviceId = deviceIdProvider(),
            items = books.sortedBy { it.order }.map { SyncIds.bookId(it) }
        )
        client.upload("order/bookshelf.json", payload)
    }

    fun applyRemoteDelete(bookSyncId: String): Boolean =
        repository.applyRemote { objectApplier.applyRemoteDelete(bookSyncId) }

    fun applyRemoteOrder(payload: SyncOrderPayload) {
        repository.applyRemote { objectApplier.applyRemoteOrder(payload) }
    }

    suspend fun pushBookDelete(book: Book) {
        pushBookDeleteById(SyncIds.bookId(book))
    }

    suspend fun pushBookDeleteById(id: String) {
        val payload = SyncTombstonePayload(
            objectType = SyncObjectType.Book,
            objectId = id,
            deletedAt = clock.now(),
            deletedByDeviceId = deviceIdProvider()
        )
        client.upload("tombstones/books/$id.json", payload)
    }

    private fun SyncMetadata?.withRemoteClock(
        objectType: String,
        objectId: String,
        remoteUpdatedAt: Long,
        updatedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            remoteUpdatedAt = maxOf(this.remoteUpdatedAt, remoteUpdatedAt),
            updatedByDeviceId = updatedByDeviceId
        ) ?: SyncMetadata(
            objectType = objectType,
            objectId = objectId,
            remoteUpdatedAt = remoteUpdatedAt,
            updatedByDeviceId = updatedByDeviceId
        )
    }

    private fun recordLocalBookClocks(payload: SyncBookPayload) {
        appDb.runInTransaction {
            val metadataDao = appDb.syncMetadataDao
            val shelfMetadata = metadataDao.get(BookShelfClockType, payload.bookSyncId)
            val catalogMetadata = metadataDao.get(BookCatalogClockType, payload.bookSyncId)
            metadataDao.insert(
                shelfMetadata.withLocalClock(
                    objectType = BookShelfClockType,
                    objectId = payload.bookSyncId,
                    localUpdatedAt = payload.shelfUpdatedAt,
                    updatedByDeviceId = payload.updatedByDeviceId
                )
            )
            metadataDao.insert(
                catalogMetadata.withLocalClock(
                    objectType = BookCatalogClockType,
                    objectId = payload.bookSyncId,
                    localUpdatedAt = payload.catalogUpdatedAt,
                    updatedByDeviceId = payload.updatedByDeviceId
                )
            )
        }
    }

    private fun SyncMetadata?.withLocalClock(
        objectType: String,
        objectId: String,
        localUpdatedAt: Long,
        updatedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            localUpdatedAt = maxOf(this.localUpdatedAt, localUpdatedAt),
            updatedByDeviceId = updatedByDeviceId
        ) ?: SyncMetadata(
            objectType = objectType,
            objectId = objectId,
            localUpdatedAt = localUpdatedAt,
            updatedByDeviceId = updatedByDeviceId
        )
    }

    private fun SyncBookPayload.localGroupMask(
        coordinator: BookGroupSyncCoordinator
    ): Long = if (schemaVersion >= 2) {
        coordinator.remoteGroupIdsToLocalMask(book.groupSyncIds)
    } else {
        coordinator.remoteLegacyMaskToLocalMask(book.group)
    }

    private fun Book.localProgressUpdatedAt(): Long = syncTime

    private fun SyncBook.withProgressFrom(book: Book, progressUpdatedAt: Long): SyncBook = copy(
        durChapterTitle = book.durChapterTitle,
        durChapterIndex = book.durChapterIndex,
        durVolumeIndex = book.durVolumeIndex,
        chapterInVolumeIndex = book.chapterInVolumeIndex,
        durChapterPos = book.durChapterPos,
        durChapterTime = book.durChapterTime,
        syncTime = progressUpdatedAt
    )

    private fun SyncBook.toBook(localGroupMask: Long): Book {
        return Book(
            bookUrl = bookUrl,
            tocUrl = tocUrl,
            origin = origin,
            originName = originName,
            name = name,
            author = author,
            kind = kind,
            customTag = customTag,
            coverUrl = coverUrl,
            customCoverUrl = customCoverUrl,
            intro = intro,
            customIntro = customIntro,
            charset = charset,
            type = type,
            group = localGroupMask,
            latestChapterTitle = latestChapterTitle,
            latestChapterTime = latestChapterTime,
            lastCheckTime = lastCheckTime,
            lastCheckCount = lastCheckCount,
            totalChapterNum = totalChapterNum,
            durChapterTitle = durChapterTitle,
            durChapterIndex = durChapterIndex,
            durVolumeIndex = durVolumeIndex,
            chapterInVolumeIndex = chapterInVolumeIndex,
            durChapterPos = durChapterPos,
            durChapterTime = durChapterTime,
            wordCount = wordCount,
            canUpdate = canUpdate,
            order = order,
            originOrder = originOrder,
            variable = variable,
            readConfig = readConfig,
            syncTime = syncTime
        )
    }
}
