package io.wanjuan.app.sync.delta

import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.sync.SyncCanonicalJson
import io.wanjuan.app.sync.model.*
import io.wanjuan.app.utils.GSON

object DeltaCodec {
    private inline fun <reified T> decode(json: String): T = GSON.fromJson(json, T::class.java)

    fun normalize(type: String, json: String): String = SyncCanonicalJson.encode(when (type) {
        SyncObjectType.Book -> decode<SyncBookPayload>(json).let { payload ->
            payload.copy(
                book = payload.book.copy(group = 0, order = 0, groupSyncIds = payload.book.groupSyncIds.sorted(),
                    lastCheckTime = 0, lastCheckCount = 0, durChapterTime = 0, syncTime = 0,
                    readConfig = payload.book.readConfig ?: Book.ReadConfig(),
                    type = payload.book.type and BookType.updateError.inv()),
                shelfUpdatedAt = 0, catalogUpdatedAt = 0, progressUpdatedAt = 0,
                updatedByDeviceId = "", shelfUpdatedByDeviceId = "", catalogUpdatedByDeviceId = "",
                progressUpdatedByDeviceId = "", schemaVersion = 2
            )
        }
        SyncObjectType.BookGroup -> decode<SyncBookGroupPayload>(json).copy(
            legacyGroupId = 0, order = 0, updatedAt = 0, updatedByDeviceId = "", schemaVersion = 2)
        SyncObjectType.BookSource -> decode<SyncBookSourcePayload>(json).let {
            it.copy(bookSource = it.bookSource.copy(customOrder = 0, respondTime = 180000, lastUpdateTime = 0),
                sourceUpdatedAt = 0, updatedByDeviceId = "")
        }
        SyncObjectType.RssSource -> decode<SyncRssSourcePayload>(json).let {
            it.copy(rssSource = it.rssSource.copy(customOrder = 0, lastUpdateTime = 0),
                sourceUpdatedAt = 0, updatedByDeviceId = "")
        }
        SyncObjectType.RuleSub -> decode<SyncRuleSubPayload>(json).let {
            it.copy(ruleSub = it.ruleSub.copy(customOrder = 0, update = 0), subscriptionUpdatedAt = 0, updatedByDeviceId = "")
        }
        in orderTypes -> decode<SyncOrderPayload>(json).copy(updatedAt = 0, updatedByDeviceId = "")
        else -> error("Unsupported delta object type: $type")
    })

    fun payload(type: String, json: String, timestamp: Long): String = GSON.toJson(when (type) {
        SyncObjectType.Book -> decode<SyncBookPayload>(json).copy(
            shelfUpdatedAt = timestamp, catalogUpdatedAt = timestamp, progressUpdatedAt = timestamp,
            updatedByDeviceId = "delta", shelfUpdatedByDeviceId = "delta", catalogUpdatedByDeviceId = "delta",
            progressUpdatedByDeviceId = "delta")
        SyncObjectType.BookGroup -> decode<SyncBookGroupPayload>(json).copy(updatedAt = timestamp, updatedByDeviceId = "delta")
        SyncObjectType.BookSource -> decode<SyncBookSourcePayload>(json).copy(sourceUpdatedAt = timestamp, updatedByDeviceId = "delta")
        SyncObjectType.RssSource -> decode<SyncRssSourcePayload>(json).copy(sourceUpdatedAt = timestamp, updatedByDeviceId = "delta")
        SyncObjectType.RuleSub -> decode<SyncRuleSubPayload>(json).copy(subscriptionUpdatedAt = timestamp, updatedByDeviceId = "delta")
        in orderTypes -> decode<SyncOrderPayload>(json).copy(updatedAt = timestamp, updatedByDeviceId = "delta")
        else -> error("Unsupported delta object type: $type")
    })

    val orderTypes = setOf(SyncObjectType.BookshelfOrder, SyncObjectType.BookGroupOrder,
        SyncObjectType.BookSourceOrder, SyncObjectType.RssSourceOrder, SyncObjectType.RuleSubOrder)

    fun preserveLocalFields(type: String, json: String, local: String?): String {
        if (local == null) return json
        return GSON.toJson(when (type) {
            SyncObjectType.BookGroup -> decode<SyncBookGroupPayload>(json).copy(order = decode<SyncBookGroupPayload>(local).order)
            SyncObjectType.BookSource -> decode<SyncBookSourcePayload>(json).let { remote ->
                val old = decode<SyncBookSourcePayload>(local).bookSource
                remote.copy(bookSource = remote.bookSource.copy(customOrder = old.customOrder,
                    respondTime = old.respondTime, lastUpdateTime = old.lastUpdateTime))
            }
            SyncObjectType.RssSource -> decode<SyncRssSourcePayload>(json).let { remote ->
                val old = decode<SyncRssSourcePayload>(local).rssSource
                remote.copy(rssSource = remote.rssSource.copy(customOrder = old.customOrder, lastUpdateTime = old.lastUpdateTime))
            }
            SyncObjectType.RuleSub -> decode<SyncRuleSubPayload>(json).let { remote ->
                val old = decode<SyncRuleSubPayload>(local).ruleSub
                remote.copy(ruleSub = remote.ruleSub.copy(customOrder = old.customOrder, update = old.update))
            }
            else -> return json
        })
    }

    fun isEmptyOrder(type: String, json: String): Boolean =
        type in orderTypes && decode<SyncOrderPayload>(json).items.isEmpty()

    fun sameBookTitle(first: String, second: String): Boolean {
        val a = decode<SyncBookPayload>(first).book
        val b = decode<SyncBookPayload>(second).book
        return a.name.isNotBlank() && a.name == b.name && a.author == b.author
    }

    fun fields(type: String, before: String?, after: String): List<DeltaField> {
        val old = before?.let(DeltaJson::parse)
        val current = DeltaJson.parse(after)
        val fields = DeltaJson.diff(old, current).toMutableList()
        // A reading position is one value: never merge a chapter from one edit with another edit's offset.
        val progressKeys = setOf("u", "v", "w", "x", "y")
        if (type == SyncObjectType.Book && fields.any { it.path.size == 2 && it.path[0] == "b" && it.path[1] in progressKeys }) {
            fields.removeAll { it.path.size == 2 && it.path[0] == "b" && it.path[1] in progressKeys }
            progressKeys.forEach { key ->
                val value = current.getAsJsonObject("b")[key]
                fields += DeltaField(listOf("b", key), remove = value == null, value = value)
            }
        }
        return fields
    }

    fun rebase(type: String, before: String?, desired: String, cloud: String?): String {
        if (type in orderTypes && cloud != null) {
            val old = before?.let { decode<SyncOrderPayload>(it).items.toSet() }.orEmpty()
            val pending = decode<SyncOrderPayload>(desired)
            val remote = decode<SyncOrderPayload>(cloud).items
            val items = (pending.items.filter { it in remote || it !in old } +
                remote.filter { it !in old && it !in pending.items }).distinct()
            return normalize(type, GSON.toJson(pending.copy(items = items)))
        }
        return normalize(type, DeltaJson.apply(cloud?.let(DeltaJson::parse), fields(type, before, desired)).toString())
    }
}
