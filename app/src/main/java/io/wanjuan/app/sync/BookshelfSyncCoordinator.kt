package io.wanjuan.app.sync

import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.data.entities.BookProgress
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.sync.mapper.BookSyncMapper
import io.wanjuan.app.sync.mapper.progressSyncTime
import io.wanjuan.app.sync.merge.BookSyncMerge
import io.wanjuan.app.sync.merge.SyncMerge
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.sync.remote.mergeBook
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
    private val objectApplier: BookshelfObjectApplier = BookshelfObjectApplier(),
    private val db: AppDatabase = appDb
) {
    private val bookState = BookSyncState(db, clock, deviceIdProvider, groupCoordinator)

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
        db.runInTransaction { repository.queueBook(bookState.capture(book)) }
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
            catalogUpdatedAt = catalogUpdatedAt,
            groupSyncIds = groupCoordinator.localMaskToRemoteGroupIds(book.group)
        )
        pushBookPayload(payload)
    }

    suspend fun pushBookPayload(payload: SyncBookPayload) {
        val merged = client.mergeBook(payload)
        db.runInTransaction { bookState.record(merged) }
        runCatching { client.delete("tombstones/books/${payload.bookSyncId}.json") }
    }

    suspend fun pullProgress(book: Book): BookProgress? {
        if (!AppWebDav.isConfigured) return null
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
        if (!AppWebDav.isConfigured) return false
        if (!NetworkUtils.isAvailable()) return false
        return try {
            val id = SyncIds.bookId(book)
            val localUpdatedAt = book.localProgressUpdatedAt()
            val uploaded = client.updateBook(id) { remote ->
                if (!force && remote != null && SyncMerge.remoteProgressWins(
                        localUpdatedAt,
                        remote.effectiveProgressUpdatedAt()
                    )
                ) {
                    return@updateBook null
                }
                val deviceId = deviceIdProvider()
                val payload = if (remote == null) {
                    BookSyncMapper.toBookPayload(
                        book = book,
                        deviceId = deviceId,
                        shelfUpdatedAt = clock.now(),
                        catalogUpdatedAt = clock.now(),
                        progressUpdatedAt = localUpdatedAt,
                        groupSyncIds = groupCoordinator.localMaskToRemoteGroupIds(book.group)
                    )
                } else {
                    remote.copy(
                        book = remote.book.withProgressFrom(book, localUpdatedAt),
                        progressUpdatedAt = localUpdatedAt,
                        progressUpdatedByDeviceId = deviceId
                    )
                }
                payload
            } ?: return false
            runCatching { client.delete("tombstones/books/$id.json") }
            book.syncTime = uploaded.effectiveProgressUpdatedAt()
            true
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e, toast)
            false
        }
    }

    fun applyProgress(book: Book, progress: BookProgress) {
        SyncScope.remoteApply {
            db.runInTransaction {
                val current = db.bookDao.getBook(book.bookUrl) ?: return@runInTransaction
                if (progress.durChapterTime <= current.localProgressUpdatedAt() ||
                    progress.durChapterIndex !in 0..current.lastChapterIndex
                ) return@runInTransaction
                current.durChapterIndex = progress.durChapterIndex
                current.durChapterPos = progress.durChapterPos
                current.durChapterTitle = progress.durChapterTitle
                current.durChapterTime = progress.durChapterTime
                current.syncTime = progress.durChapterTime
                db.bookDao.update(current)
            }
        }
    }

    fun applyRemoteBook(payload: SyncBookPayload): SyncApplyOutcome {
        var outcome = SyncApplyOutcome.Updated
        repository.applyRemote {
            db.runInTransaction {
                val dao = db.bookDao
                val local = dao.getBook(payload.book.bookUrl)
                val localMask = payload.localGroupMask(groupCoordinator)
                val remote = payload.copy(
                    schemaVersion = 2,
                    book = payload.book.copy(
                        groupSyncIds = groupCoordinator.localMaskToRemoteGroupIds(localMask)
                    )
                )
                val merged = if (local == null) remote else {
                    BookSyncMerge.merge(bookState.capture(local, newLocalBook = false), remote)
                }
                val book = merged.book.toBook(merged.localGroupMask(groupCoordinator))
                if (local == null) {
                    dao.insert(book)
                    outcome = SyncApplyOutcome.Inserted
                } else {
                    dao.update(book)
                }
                bookState.record(merged)
                if (SyncPayloadHash.book(merged) != SyncPayloadHash.book(remote)) {
                    repository.queueBook(merged)
                    outcome = SyncApplyOutcome.Merged
                }
            }
        }
        return outcome
    }

    fun applyUploadedBook(payload: SyncBookPayload) {
        db.runInTransaction {
            // An upload acknowledgement must not resurrect a book deleted while the PUT ran.
            if (db.bookDao.getBook(payload.book.bookUrl) == null ||
                db.syncOutboxDao.latestForObject(SyncObjectType.Book, payload.bookSyncId)?.operation == "delete"
            ) return@runInTransaction
            applyRemoteBook(payload)
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

    private fun SyncBookPayload.localGroupMask(
        coordinator: BookGroupSyncCoordinator
    ): Long = if (schemaVersion >= 2) {
        coordinator.remoteGroupIdsToLocalMask(book.groupSyncIds)
    } else {
        coordinator.remoteLegacyMaskToLocalMask(book.group)
    }

    private fun Book.localProgressUpdatedAt(): Long = progressSyncTime()

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
