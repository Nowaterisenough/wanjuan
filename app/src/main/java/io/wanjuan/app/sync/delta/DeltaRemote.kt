package io.wanjuan.app.sync.delta

import com.google.gson.JsonObject
import io.wanjuan.app.sync.remote.SyncRemoteRead
import io.wanjuan.app.sync.remote.SyncRemoteStore
import io.wanjuan.app.utils.GSON
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

/** Immutable, content-addressed chunks; the tiny CAS head is published only after all data. */
class DeltaRemote(private val remote: SyncRemoteStore) {
    companion object {
        const val HEAD = "delta-v2/head.json"
        const val MAX_FILE_BYTES = 512 * 1024
        private const val CHUNK_BYTES = 256 * 1024
        private const val FORMAT = "wanjuan-delta-chunks-1"
        fun checksum(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    suspend fun head(): Pair<SyncRemoteRead, DeltaHead> {
        val read = remote.readForUpdate(HEAD)
        val head = read.json?.let { GSON.fromJson(it, DeltaHead::class.java) } ?: DeltaHead()
        require(head.schema == 2 && head.timestamp >= 0 && (head.timestamp == 0L) == head.block.isEmpty()) {
            "Invalid incremental sync head"
        }
        if (head.block.isNotEmpty()) validateBlockPath(head.block)
        return read to head
    }

    suspend fun publish(previous: SyncRemoteRead, head: DeltaHead): Boolean =
        remote.compareAndSetJson(HEAD, previous, GSON.toJson(head))

    suspend fun writeBlock(block: DeltaBlock): String {
        val json = GSON.toJson(block)
        val path = "delta-v2/blocks/${checksum(json.toByteArray(Charsets.UTF_8))}.json"
        write(path, json)
        return path
    }

    suspend fun block(head: DeltaHead): DeltaBlock {
        validateBlockPath(head.block)
        val json = read(head.block)
        require(head.block == "delta-v2/blocks/${checksum(json.toByteArray(Charsets.UTF_8))}.json") { "Delta block checksum mismatch" }
        val block = GSON.fromJson(json, DeltaBlock::class.java)
        require(block.schema == 2 && block.timestamp == head.timestamp && block.previous.timestamp < block.timestamp) {
            "Invalid incremental sync chain"
        }
        require((block.previous.timestamp == 0L) == block.previous.block.isEmpty()) { "Invalid previous sync head" }
        return block
    }

    private fun validateBlockPath(path: String) {
        require(path.matches(Regex("delta-v2/blocks/[a-f0-9]{64}\\.json"))) { "Invalid delta block path" }
    }

    internal suspend fun write(path: String, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_FILE_BYTES) {
            remote.uploadImmutableJson(path, json)
            return
        }
        val parts = com.google.gson.JsonArray()
        val uploaded = hashSetOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val chunk = bytes.copyOfRange(offset, minOf(offset + CHUNK_BYTES, bytes.size))
            val hash = checksum(chunk)
            val encoded = JsonObject().apply { addProperty("data", Base64.getEncoder().encodeToString(chunk)) }
            if (uploaded.add(hash)) remote.uploadImmutableJson("delta-v2/chunks/$hash.json", encoded.toString())
            parts.add(hash)
            offset += chunk.size
        }
        val manifest = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("bytes", bytes.size)
            addProperty("sha256", checksum(bytes))
            add("parts", parts)
        }.toString()
        require(manifest.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) { "Sync chunk manifest is too large" }
        remote.uploadImmutableJson(path, manifest)
    }

    internal suspend fun read(path: String): String {
        val json = requireNotNull(remote.downloadJson(path)) { "Missing sync block: $path" }
        val manifest = DeltaJson.parse(json)
        if (manifest["format"]?.asString != FORMAT) return json
        val size = manifest["bytes"].asLong
        require(size in 0..Int.MAX_VALUE.toLong()) { "Invalid sync chunk length" }
        val output = ByteArrayOutputStream()
        for (part in manifest.getAsJsonArray("parts")) {
            val hash = part.asString
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "Invalid sync chunk path" }
            val chunkJson = requireNotNull(remote.downloadJson("delta-v2/chunks/$hash.json")) { "Missing sync chunk" }
            val chunk = Base64.getDecoder().decode(DeltaJson.parse(chunkJson)["data"].asString)
            require(chunk.size <= CHUNK_BYTES && checksum(chunk) == hash) { "Corrupt sync chunk" }
            require(output.size().toLong() + chunk.size <= size) { "Unexpected sync chunk length" }
            output.write(chunk)
        }
        val bytes = output.toByteArray()
        require(bytes.size.toLong() == size && checksum(bytes) == manifest["sha256"].asString) { "Incomplete sync chunks" }
        return bytes.toString(Charsets.UTF_8)
    }
}
