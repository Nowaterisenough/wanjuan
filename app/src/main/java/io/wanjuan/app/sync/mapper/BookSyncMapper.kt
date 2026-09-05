package io.wanjuan.app.sync.mapper

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.sync.SyncIds
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload

object BookSyncMapper {

    fun toBookPayload(
        book: Book,
        deviceId: String,
        shelfUpdatedAt: Long,
        catalogUpdatedAt: Long,
        progressUpdatedAt: Long = book.syncTime.takeIf { it > 0L } ?: book.durChapterTime,
        groupSyncIds: List<String>
    ): SyncBookPayload {
        return SyncBookPayload(
            bookSyncId = SyncIds.bookId(book),
            book = SyncBook.from(book, groupSyncIds),
            shelfUpdatedAt = shelfUpdatedAt,
            catalogUpdatedAt = catalogUpdatedAt,
            progressUpdatedAt = progressUpdatedAt,
            updatedByDeviceId = deviceId
        )
    }
}
