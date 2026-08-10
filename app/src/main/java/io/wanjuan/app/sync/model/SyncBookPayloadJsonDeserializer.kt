package io.wanjuan.app.sync.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * Reads both compact release layouts of a book object.
 *
 * Before reading progress became part of [SyncBookPayload], compact key `e` contained the device
 * ID. The new layout uses `e` for the progress timestamp and moves device IDs to `f` and `g`.
 * Distinguishing the layouts by the JSON value type keeps existing WebDAV data readable.
 */
class SyncBookPayloadJsonDeserializer : JsonDeserializer<SyncBookPayload> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): SyncBookPayload {
        val obj = json.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw JsonParseException("Book sync payload must be a JSON object")
        val compactProgressOrLegacyDevice = obj["e"]
        val progressUpdatedAt = obj.element("progressUpdatedAt").longOrNull()
            ?: compactProgressOrLegacyDevice.longOrNull()
            ?: 0L
        val updatedByDeviceId = obj.element("updatedByDeviceId").stringOrNull()
            ?: obj["f"].stringOrNull()
            ?: compactProgressOrLegacyDevice.stringOrNull()
            ?: ""
        val progressUpdatedByDeviceId = obj.element("progressUpdatedByDeviceId").stringOrNull()
            ?: obj["g"].stringOrNull()
            ?: updatedByDeviceId
        val schemaVersion = obj.element("schemaVersion").intOrNull()
            ?: obj["h"].intOrNull()
            ?: obj["f"].intOrNull()
            ?: if (compactProgressOrLegacyDevice.longOrNull() != null) 2 else 1
        val bookElement = obj.element("book", "b")
            ?: throw JsonParseException("Book sync payload is missing book")
        val book = context.deserialize<SyncBook>(bookElement, SyncBook::class.java)
            ?: throw JsonParseException("Book sync payload contains an invalid book")

        return SyncBookPayload(
            bookSyncId = obj.element("bookSyncId", "a").stringOrNull()
                ?: throw JsonParseException("Book sync payload is missing bookSyncId"),
            book = book,
            shelfUpdatedAt = obj.element("shelfUpdatedAt", "c").longOrNull() ?: 0L,
            catalogUpdatedAt = obj.element("catalogUpdatedAt", "d").longOrNull() ?: 0L,
            progressUpdatedAt = progressUpdatedAt,
            updatedByDeviceId = updatedByDeviceId,
            progressUpdatedByDeviceId = progressUpdatedByDeviceId,
            schemaVersion = schemaVersion
        )
    }
}

private fun JsonObject.element(vararg names: String): JsonElement? {
    names.forEach { name ->
        get(name)?.takeUnless { it.isJsonNull }?.let { return it }
    }
    return null
}

private fun JsonElement?.stringOrNull(): String? =
    this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private fun JsonElement?.longOrNull(): Long? {
    val primitive = this?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
    return when {
        primitive.isNumber -> runCatching { primitive.asLong }.getOrNull()
        primitive.isString -> primitive.asString.toLongOrNull()
        else -> null
    }
}

private fun JsonElement?.intOrNull(): Int? = longOrNull()?.toInt()
