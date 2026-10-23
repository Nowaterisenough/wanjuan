package io.wanjuan.app.sync.local

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

/** Five full JSON copies in one SQLite row can exceed Android's CursorWindow before upload. */
object SyncDeltaPayloadCodec {
    const val PREFIX = "@delta-json/1/"
    const val INLINE_BYTES = 32 * 1024
    const val PART_BYTES = 64 * 1024

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun encode(json: String?, write: (List<SyncDeltaBlob>) -> Unit): String? {
        json ?: return null
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.size <= INLINE_BYTES) return json
        val id = hash(bytes)
        val parts = ArrayList<SyncDeltaBlob>()
        var offset = 0
        while (offset < bytes.size) {
            val chunk = bytes.copyOfRange(offset, minOf(offset + PART_BYTES, bytes.size))
            parts += SyncDeltaBlob(id, parts.size, Base64.getEncoder().encodeToString(chunk))
            offset += chunk.size
        }
        write(parts)
        return "$PREFIX$id:${parts.size}:${bytes.size}"
    }

    fun decode(value: String?, read: (String) -> List<SyncDeltaBlob>): String? {
        value ?: return null
        if (!value.startsWith(PREFIX)) return value
        val fields = value.removePrefix(PREFIX).split(':')
        require(fields.size == 3 && fields[0].matches(Regex("[a-f0-9]{64}"))) { "Invalid local delta reference" }
        val count = fields[1].toInt()
        val size = fields[2].toInt()
        require(count > 0 && size > INLINE_BYTES && (size.toLong() + PART_BYTES - 1) / PART_BYTES == count.toLong()) { "Invalid local delta size" }
        val parts = read(fields[0])
        check(parts.size == count) { "Missing local delta data" }
        val output = ByteArrayOutputStream()
        parts.forEachIndexed { index, part ->
            check(part.partIndex == index && part.blobId == fields[0]) { "Invalid local delta part" }
            val bytes = Base64.getDecoder().decode(part.content)
            check(bytes.size <= PART_BYTES && output.size().toLong() + bytes.size <= size) { "Invalid local delta length" }
            output.write(bytes)
        }
        val bytes = output.toByteArray()
        check(bytes.size == size && hash(bytes) == fields[0]) { "Corrupt local delta data" }
        return bytes.toString(Charsets.UTF_8)
    }

    fun id(value: String?): String? = value?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.substringBefore(':')
}

class SyncDeltaPayloadStore(private val dao: SyncDeltaDao) {
    private fun SyncDeltaState.values() = listOf(remoteJson, observedJson, pendingJson, inFlightChange, inFlightValue)

    fun save(state: SyncDeltaState) {
        val previous = dao.get(state.target, state.objectType, state.objectId)
        val encoded = hashMapOf<String, String>()
        fun store(value: String?) = value?.let { encoded.getOrPut(it) {
            requireNotNull(SyncDeltaPayloadCodec.encode(it, dao::insertParts))
        } }
        dao.save(state.copy(remoteJson = store(state.remoteJson), observedJson = store(state.observedJson),
            pendingJson = store(state.pendingJson), inFlightChange = store(state.inFlightChange), inFlightValue = store(state.inFlightValue)))
        previous?.values()?.mapNotNull(SyncDeltaPayloadCodec::id)?.distinct()?.forEach { id ->
            if (dao.references("${SyncDeltaPayloadCodec.PREFIX}$id:%") == 0) dao.deleteParts(id)
        }
    }

    fun decode(states: List<SyncDeltaState>): List<SyncDeltaState> {
        val loaded = hashMapOf<String, String>()
        fun load(value: String?): String? {
            if (value == null || !value.startsWith(SyncDeltaPayloadCodec.PREFIX)) return value
            return loaded.getOrPut(value) { requireNotNull(SyncDeltaPayloadCodec.decode(value, dao::parts)) }
        }
        return states.map { it.copy(remoteJson = load(it.remoteJson), observedJson = load(it.observedJson),
            pendingJson = load(it.pendingJson), inFlightChange = load(it.inFlightChange), inFlightValue = load(it.inFlightValue)) }
    }
}
