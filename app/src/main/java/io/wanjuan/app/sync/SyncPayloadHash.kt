package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncBookGroupPayload
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncBookSourcePayload
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncRssSourcePayload
import io.wanjuan.app.sync.model.SyncRuleSubPayload

object SyncPayloadHash {
    fun book(payload: SyncBookPayload): String {
        val businessBook = if (payload.schemaVersion >= 2) {
            payload.book.copy(
                group = 0L,
                order = 0,
                groupSyncIds = payload.book.groupSyncIds.sorted()
            )
        } else {
            payload.book.copy(order = 0)
        }
        return SyncCanonicalJson.hash(businessBook)
    }

    fun bookGroup(payload: SyncBookGroupPayload): String = SyncCanonicalJson.hash(
        payload.copy(
            legacyGroupId = 0L,
            order = 0,
            updatedAt = 0L,
            updatedByDeviceId = "",
            schemaVersion = 2
        )
    )

    fun bookSource(payload: SyncBookSourcePayload): String =
        SyncCanonicalJson.hash(payload.bookSource.copy(customOrder = 0))

    fun rssSource(payload: SyncRssSourcePayload): String =
        SyncCanonicalJson.hash(payload.rssSource.copy(customOrder = 0))

    fun ruleSub(payload: SyncRuleSubPayload): String =
        SyncCanonicalJson.hash(payload.ruleSub.copy(customOrder = 0))

    fun order(payload: SyncOrderPayload): String = SyncCanonicalJson.hash(payload.items)
}
