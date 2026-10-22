package io.wanjuan.app.sync.delta

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import io.wanjuan.app.utils.GSON

data class DeltaField(
    @SerializedName("path") val path: List<String>,
    @SerializedName("remove") val remove: Boolean = false,
    @SerializedName("value") val value: JsonElement? = null
)

data class DeltaChange(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("objectId") val objectId: String,
    @SerializedName("delete") val delete: Boolean = false,
    @SerializedName("create") val create: Boolean = false,
    @SerializedName("fields") val fields: List<DeltaField> = emptyList()
)

data class DeltaHead(
    @SerializedName("timestamp") val timestamp: Long = 0,
    @SerializedName("block") val block: String = "",
    @SerializedName("schema") val schema: Int = 2
)

data class DeltaBlock(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("previous") val previous: DeltaHead,
    @SerializedName("changes") val changes: List<DeltaChange>,
    @SerializedName("schema") val schema: Int = 2
)

object DeltaJson {
    fun parse(json: String): JsonObject = GSON.fromJson(json, JsonObject::class.java)

    fun diff(before: JsonObject?, after: JsonObject): List<DeltaField> = buildList {
        fun visit(old: JsonObject, new: JsonObject, path: List<String>) {
            (old.keySet() + new.keySet()).sorted().forEach { key ->
                val a = old[key]
                val b = new[key]
                when {
                    a == b -> Unit
                    b == null -> add(DeltaField(path + key, remove = true))
                    a?.isJsonObject == true && b.isJsonObject -> visit(a.asJsonObject, b.asJsonObject, path + key)
                    else -> add(DeltaField(path + key, value = b.deepCopy()))
                }
            }
        }
        visit(before ?: JsonObject(), after, emptyList())
    }

    fun apply(before: JsonObject?, fields: List<DeltaField>): JsonObject {
        val result = before?.deepCopy() ?: JsonObject()
        for (field in fields) {
            require(field.path.isNotEmpty() && field.path.size <= 128) { "Invalid delta field path" }
            var parent = result
            var missing = false
            for (key in field.path.dropLast(1)) {
                var child = parent[key]?.takeIf { it.isJsonObject }?.asJsonObject
                if (child == null && field.remove) { missing = true; break }
                if (child == null) child = JsonObject().also { parent.add(key, it) }
                parent = child
            }
            if (missing) continue
            val key = field.path.last()
            if (field.remove) parent.remove(key) else parent.add(key, field.value?.deepCopy() ?: JsonNull.INSTANCE)
        }
        return result
    }
}
