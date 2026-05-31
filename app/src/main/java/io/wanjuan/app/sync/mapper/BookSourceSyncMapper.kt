package io.wanjuan.app.sync.mapper

import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.sync.SyncIds
import io.wanjuan.app.sync.model.SyncBookSource
import io.wanjuan.app.sync.model.SyncBookSourcePayload

object BookSourceSyncMapper {

    fun toPayload(source: BookSource, deviceId: String, updatedAt: Long): SyncBookSourcePayload {
        return SyncBookSourcePayload(
            sourceHash = SyncIds.bookSourceId(source),
            bookSourceUrl = source.bookSourceUrl,
            bookSource = SyncBookSource.from(source),
            sourceUpdatedAt = updatedAt,
            updatedByDeviceId = deviceId
        )
    }
}
