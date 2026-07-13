package io.wanjuan.app.sync

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.wanjuan.app.utils.GSON

object SyncCanonicalJson {

    fun encode(value: Any): String = GSON.toJson(canonicalize(GSON.toJsonTree(value)))

    fun hash(value: Any): String = SyncIds.hashKey("content", encode(value))

    private fun canonicalize(element: JsonElement): JsonElement {
        return when {
            element.isJsonObject -> JsonObject().apply {
                element.asJsonObject.entrySet()
                    .sortedBy { it.key }
                    .forEach { (key, value) -> add(key, canonicalize(value)) }
            }
            element.isJsonArray -> JsonArray().apply {
                element.asJsonArray.forEach { add(canonicalize(it)) }
            }
            else -> element.deepCopy()
        }
    }
}
