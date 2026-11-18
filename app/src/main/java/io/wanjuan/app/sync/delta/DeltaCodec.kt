package io.wanjuan.app.sync.delta

import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.sync.SyncCanonicalJson
import io.wanjuan.app.sync.model.*
import io.wanjuan.app.utils.GSON

object DeltaCodec {
    private inline fun <reified T> decode(json: String): T = GSON.fromJson(json, T::class.java)

    fun normalize(type: String, json: String): String = normalizePayload(when (type) {
        SyncObjectType.Book -> decode<SyncBookPayload>(json)
        SyncObjectType.BookGroup -> decode<SyncBookGroupPayload>(json)
        SyncObjectType.BookSource -> decode<SyncBookSourcePayload>(json)
        SyncObjectType.RssSource -> decode<SyncRssSourcePayload>(json)
        SyncObjectType.RuleSub -> decode<SyncRuleSubPayload>(json)
        in orderTypes -> decode<SyncOrderPayload>(json)
        else -> error("Unsupported delta object type: $type")
    })

    internal fun normalizePayload(payload: Any): String = SyncCanonicalJson.encode(when (payload) {
        is SyncBookPayload -> payload.copy(
            book = payload.book.copy(group = 0, order = 0, groupSyncIds = payload.book.groupSyncIds.sorted(),
                lastCheckTime = 0, lastCheckCount = 0, durChapterTime = 0,
                readConfig = payload.book.readConfig ?: Book.ReadConfig(),
                type = payload.book.type and BookType.updateError.inv()),
            shelfUpdatedAt = 0, catalogUpdatedAt = 0, progressUpdatedAt = 0,
            updatedByDeviceId = "", shelfUpdatedByDeviceId = "", catalogUpdatedByDeviceId = "",
            progressUpdatedByDeviceId = "", schemaVersion = 2)
        is SyncBookGroupPayload -> payload.copy(legacyGroupId = 0, order = 0, updatedAt = 0,
            updatedByDeviceId = "", schemaVersion = 2)
        is SyncBookSourcePayload -> payload.copy(bookSource = payload.bookSource.copy(customOrder = 0,
            respondTime = 180000, lastUpdateTime = 0), sourceUpdatedAt = 0, updatedByDeviceId = "")
        is SyncRssSourcePayload -> payload.copy(rssSource = payload.rssSource.copy(customOrder = 0,
            lastUpdateTime = 0), sourceUpdatedAt = 0, updatedByDeviceId = "")
        is SyncRuleSubPayload -> payload.copy(ruleSub = payload.ruleSub.copy(customOrder = 0, update = 0),
            subscriptionUpdatedAt = 0, updatedByDeviceId = "")
        is SyncOrderPayload -> payload.copy(updatedAt = 0, updatedByDeviceId = "")
        else -> error("Unsupported delta payload: ${payload.javaClass}")
    })

    fun payload(type: String, json: String, timestamp: Long): String = GSON.toJson(when (type) {
        SyncObjectType.Book -> decode<SyncBookPayload>(json).let { it.copy(
            shelfUpdatedAt = timestamp, catalogUpdatedAt = timestamp,
            progressUpdatedAt = it.book.syncTime.takeIf { time -> time > 0 } ?: timestamp,
            updatedByDeviceId = "delta", shelfUpdatedByDeviceId = "delta", catalogUpdatedByDeviceId = "delta",
            progressUpdatedByDeviceId = "delta") }
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
        if (type == SyncObjectType.Book && fields.any { it.isBookField(positionKeys) }) {
            fields.removeAll { it.isBookField(progressKeys) }
            progressKeys.forEach { key ->
                val value = current.getAsJsonObject("b")[key]
                fields += DeltaField(listOf("b", key), remove = value == null, value = value)
            }
        } else if (type == SyncObjectType.Book) {
            // Upgrading an old baseline or opening a book is not a new reading position.
            fields.removeAll { it.path == listOf("b", "G") }
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
        val merged = DeltaJson.apply(cloud?.let(DeltaJson::parse), fields(type, before, desired))
        if (type == SyncObjectType.Book && cloud != null) {
            val old = before?.let { decode<SyncBookPayload>(it).book }
            val local = decode<SyncBookPayload>(desired).book
            val remote = decode<SyncBookPayload>(cloud).book
            val remoteMoved = old == null || !samePosition(old, remote)
            val localMoved = old == null || !samePosition(old, local)
            // Only compare actual reading events, never upload time or catalog refresh time.
            // Older clients have no reading timestamp; preserve their existing pending-edit policy.
            if ((remoteMoved && !localMoved) || (localMoved &&
                    remote.syncTime > 0 && local.syncTime > 0 && remote.syncTime >= local.syncTime)) {
                // Also handle a full book creation from a device without a previous baseline.
                copyProgress(DeltaJson.parse(cloud), merged)
            }
        }
        return normalize(type, merged.toString())
    }

    /** Ignore timestamp-only differences, including old normalized snapshots with G=0. */
    fun sameContent(type: String, first: String?, second: String?): Boolean = first == second ||
        (type == SyncObjectType.Book && first != null && second != null && fields(type, first, second).isEmpty())

    private val positionKeys = setOf("v", "w", "x", "y")
    private val progressKeys = positionKeys + setOf("u", "G")
    private fun DeltaField.isBookField(keys: Set<String>) = path.size == 2 && path[0] == "b" && path[1] in keys
    private fun samePosition(a: SyncBook, b: SyncBook) = a.durChapterIndex == b.durChapterIndex &&
        a.durChapterPos == b.durChapterPos && a.durVolumeIndex == b.durVolumeIndex &&
        a.chapterInVolumeIndex == b.chapterInVolumeIndex

    fun applyChange(type: String, before: String?, fields: List<DeltaField>, legacyTimestamp: Long = 0): String {
        val applied = DeltaJson.apply(before?.let(DeltaJson::parse), fields)
        // A legacy position patch must not inherit the timestamp of a previous, different read.
        if (type == SyncObjectType.Book) {
            val positionPatch = fields.any { it.isBookField(positionKeys) }
            val wholeBook = fields.any { it.path == listOf("b") }
            val timestamp = applied.getAsJsonObject("b")["G"]?.asLong ?: 0L
            if ((positionPatch && fields.none { it.path == listOf("b", "G") || it.path == listOf("b") }) ||
                ((positionPatch || wholeBook) && timestamp == 0L)) {
                // Old journal entries have only publication time. Do not use a later catalog's time.
                applied.getAsJsonObject("b").addProperty("G", legacyTimestamp)
            }
        }
        if (type == SyncObjectType.Book && before != null) {
            val old = decode<SyncBookPayload>(before).book
            val incoming = decode<SyncBookPayload>(applied.toString()).book
            if (old.syncTime > 0 && incoming.syncTime > 0 &&
                (incoming.syncTime < old.syncTime ||
                    (incoming.syncTime == old.syncTime && !samePosition(old, incoming)))) {
                copyProgress(DeltaJson.parse(before), applied)
            }
        }
        return normalize(type, applied.toString())
    }

    private fun copyProgress(from: com.google.gson.JsonObject, to: com.google.gson.JsonObject) {
        val oldBook = from.getAsJsonObject("b")
        val newBook = to.getAsJsonObject("b")
        progressKeys.forEach { key ->
            if (oldBook.has(key)) newBook.add(key, oldBook[key].deepCopy()) else newBook.remove(key)
        }
    }
}
