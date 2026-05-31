package io.wanjuan.app.sync

import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.sync.mapper.BookSyncMapper
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.merge.SyncMerge
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.remote.WebDavSyncClient

class BookshelfSyncCoordinator(
    private val client: WebDavSyncClient,
    private val repository: SyncRepository,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String
) {
    private companion object {
        const val BookShelfClockType = "bookShelf"
        const val BookCatalogClockType = "bookCatalog"
    }

    fun enqueueBookDelete(book: Book) {
        val id = SyncIds.bookId(book)
        repository.markDirty(SyncObjectType.Book, id, null, "delete")
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
    }

    fun applyRemoteBook(payload: SyncBookPayload) {
        repository.applyRemote {
            appDb.runInTransaction {
                val dao = appDb.bookDao
                val metadataDao = appDb.syncMetadataDao
                val shelfMetadata = metadataDao.get(BookShelfClockType, payload.bookSyncId)
                val catalogMetadata = metadataDao.get(BookCatalogClockType, payload.bookSyncId)
                val localShelfUpdatedAt = maxOf(
                    shelfMetadata?.localUpdatedAt ?: 0L,
                    shelfMetadata?.remoteUpdatedAt ?: 0L
                )
                val localCatalogUpdatedAt = maxOf(
                    catalogMetadata?.localUpdatedAt ?: 0L,
                    catalogMetadata?.remoteUpdatedAt ?: 0L
                )
                val local = dao.getBook(payload.book.bookUrl)
                if (local == null) {
                    dao.insert(payload.book.toBook())
                    metadataDao.insert(
                        shelfMetadata.withRemoteClock(
                            objectType = BookShelfClockType,
                            objectId = payload.bookSyncId,
                            remoteUpdatedAt = payload.shelfUpdatedAt,
                            updatedByDeviceId = payload.updatedByDeviceId
                        )
                    )
                    metadataDao.insert(
                        catalogMetadata.withRemoteClock(
                            objectType = BookCatalogClockType,
                            objectId = payload.bookSyncId,
                            remoteUpdatedAt = payload.catalogUpdatedAt,
                            updatedByDeviceId = payload.updatedByDeviceId
                        )
                    )
                    return@runInTransaction
                }

                val shelfWins = SyncMerge.remoteWins(localShelfUpdatedAt, payload.shelfUpdatedAt)
                val catalogWins = SyncMerge.remoteWins(localCatalogUpdatedAt, payload.catalogUpdatedAt)

                if (shelfWins) {
                    local.group = payload.book.group
                    local.order = payload.book.order
                    local.customCoverUrl = payload.book.customCoverUrl
                    local.customIntro = payload.book.customIntro
                    local.customTag = payload.book.customTag
                    local.canUpdate = payload.book.canUpdate
                    local.readConfig = payload.book.readConfig
                }

                if (catalogWins) {
                    local.totalChapterNum = payload.book.totalChapterNum
                    local.lastCheckCount = payload.book.lastCheckCount
                    local.latestChapterTitle = payload.book.latestChapterTitle
                    local.latestChapterTime = payload.book.latestChapterTime
                    local.lastCheckTime = payload.book.lastCheckTime
                }

                if (shelfWins || catalogWins) {
                    dao.update(local)
                }
                if (shelfWins) {
                    metadataDao.insert(
                        shelfMetadata.withRemoteClock(
                            objectType = BookShelfClockType,
                            objectId = payload.bookSyncId,
                            remoteUpdatedAt = payload.shelfUpdatedAt,
                            updatedByDeviceId = payload.updatedByDeviceId
                        )
                    )
                }
                if (catalogWins) {
                    metadataDao.insert(
                        catalogMetadata.withRemoteClock(
                            objectType = BookCatalogClockType,
                            objectId = payload.bookSyncId,
                            remoteUpdatedAt = payload.catalogUpdatedAt,
                            updatedByDeviceId = payload.updatedByDeviceId
                        )
                    )
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

    private fun SyncBook.toBook(): Book {
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
            group = group,
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
