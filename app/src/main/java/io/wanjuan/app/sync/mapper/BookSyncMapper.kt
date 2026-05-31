package io.wanjuan.app.sync.mapper

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.sync.SyncIds
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncBookProgressPayload

object BookSyncMapper {

    fun toBookPayload(
        book: Book,
        deviceId: String,
        shelfUpdatedAt: Long,
        catalogUpdatedAt: Long
    ): SyncBookPayload {
        return SyncBookPayload(
            bookSyncId = SyncIds.bookId(book),
            book = SyncBook.from(book),
            shelfUpdatedAt = shelfUpdatedAt,
            catalogUpdatedAt = catalogUpdatedAt,
            updatedByDeviceId = deviceId
        )
    }

    fun toProgressPayload(
        book: Book,
        deviceId: String,
        progressUpdatedAt: Long = book.syncTime.takeIf { it > 0L } ?: book.durChapterTime
    ): SyncBookProgressPayload {
        return SyncBookProgressPayload(
            bookSyncId = SyncIds.bookId(book),
            name = book.name,
            author = book.author,
            durChapterIndex = book.durChapterIndex,
            durChapterPos = book.durChapterPos,
            durChapterTitle = book.durChapterTitle,
            progressUpdatedAt = progressUpdatedAt,
            updatedByDeviceId = deviceId
        )
    }
}
