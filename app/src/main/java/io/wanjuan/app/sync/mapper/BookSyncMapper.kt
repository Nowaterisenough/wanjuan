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
        progressUpdatedAt: Long = book.progressSyncTime(),
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

fun Book.progressSyncTime(): Long = syncTime.takeIf { it > 0L }
    ?: durChapterTime.takeIf { durChapterIndex > 0 || durChapterPos > 0 }
    ?: 0L
