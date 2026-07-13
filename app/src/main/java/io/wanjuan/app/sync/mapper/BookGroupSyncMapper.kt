package io.wanjuan.app.sync.mapper

import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.sync.model.SyncBookGroupPayload
import io.wanjuan.app.sync.model.SyncVersion

object BookGroupSyncMapper {

    fun toPayload(group: BookGroup, version: SyncVersion): SyncBookGroupPayload {
        return SyncBookGroupPayload(
            groupSyncId = group.syncId,
            legacyGroupId = group.groupId,
            groupName = group.groupName,
            cover = group.cover,
            order = group.order,
            enableRefresh = group.enableRefresh,
            show = group.show,
            bookSort = group.bookSort,
            onlyUpdateRead = group.onlyUpdateRead,
            updatedAt = version.timestamp,
            updatedByDeviceId = version.deviceId
        )
    }

    fun toEntity(payload: SyncBookGroupPayload, localGroupId: Long): BookGroup {
        return BookGroup(
            groupId = localGroupId,
            groupName = payload.groupName,
            cover = payload.cover,
            order = payload.order,
            enableRefresh = payload.enableRefresh,
            show = payload.show,
            bookSort = payload.bookSort,
            onlyUpdateRead = payload.onlyUpdateRead,
            syncId = payload.groupSyncId
        )
    }
}
