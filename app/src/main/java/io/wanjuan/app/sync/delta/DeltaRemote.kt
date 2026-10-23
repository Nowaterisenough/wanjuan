package io.wanjuan.app.sync.delta

import com.google.gson.JsonObject
import io.wanjuan.app.sync.remote.SyncRemoteRead
import io.wanjuan.app.sync.remote.SyncRemoteStore
import io.wanjuan.app.utils.GSON
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

/** Immutable, content-addressed chunks; the tiny CAS head is published only after all data. */
class DeltaRemote(private val remote: SyncRemoteStore, private val cache: DeltaContentCache = MemoryDeltaContentCache()) {
    companion object {
        const val HEAD = "delta-v2/head.json"
        const val MAX_FILE_BYTES = 512 * 1024
        private const val CHUNK_BYTES = 256 * 1024
        private const val FORMAT = "wanjuan-delta-chunks-1"
        const val INDEX_ENTRIES = 64
        const val MAX_HEAD_BYTES = 128 * 1024
        const val MAX_LOGICAL_BYTES = 64 * 1024 * 1024
        private const val INLINE_BLOCK_BYTES = 8 * 1024
        private const val INLINE_TOTAL_BYTES = 64 * 1024
        fun checksum(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private val reads = Semaphore(3)
    private val chunkLocks = Array(32) { Mutex() }
    private var readIndex: Pair<DeltaHead, DeltaReadIndex>? = null

    fun trimCache() = runCatching { cache.trim() }.let { Unit }

    private fun hash(path: String) = path.substringAfterLast('/').removeSuffix(".json")

    private fun cached(path: String): ByteArray? {
        val bytes = cache.read(path) ?: return null
        if (checksum(bytes) == hash(path)) return bytes
        cache.remove(path)
        return null
    }

    private suspend fun download(path: String): String = reads.withPermit {
        requireNotNull(remote.downloadJson(path)) { "Missing sync data: $path" }.also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) { "Sync file exceeds size limit" }
        }
    }

    suspend fun head(): Pair<SyncRemoteRead, DeltaHead> {
        val read = remote.readForUpdate(HEAD)
        require((read.json?.toByteArray(Charsets.UTF_8)?.size ?: 0) <= MAX_FILE_BYTES) { "Sync head exceeds size limit" }
        val head = read.json?.let { GSON.fromJson(it, DeltaHead::class.java) } ?: DeltaHead()
        require(head.schema == 2 && head.timestamp >= 0 && (head.timestamp == 0L) == head.block.isEmpty()) {
            "Invalid incremental sync head"
        }
        if (head.block.isNotEmpty()) validateBlockPath(head.block)
        readIndex = parseIndex(read.json)?.also { index ->
            require(index.recent.firstOrNull()?.head() == head) { "Sync index does not match head" }
            index.recent.forEach { entry ->
                entry.inline?.let { json ->
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    require(bytes.size <= INLINE_BLOCK_BYTES && bytes.size == entry.bytes && checksum(bytes) == hash(entry.block)) {
                        "Invalid inline sync block"
                    }
                }
            }
        }?.let { head to it }
        return read to head
    }

    private fun parseIndex(json: String?): DeltaReadIndex? {
        val root = json?.let(DeltaJson::parse) ?: return null
        val value = root["readAhead"]?.takeUnless { it.isJsonNull } ?: return null
        if (value.asJsonObject["version"]?.asInt != 1) return null
        return GSON.fromJson(value, DeltaReadIndex::class.java).also(::validateIndex)
    }

    private fun validateIndex(index: DeltaReadIndex) {
        require(index.version == 1 && index.recent.size in 1..INDEX_ENTRIES) { "Invalid sync index size" }
        require(index.indexedThrough in 0..index.recent.first().timestamp) { "Invalid indexed sync boundary" }
        var timestamp = Long.MAX_VALUE
        index.recent.forEach { entry ->
            validateBlockPath(entry.block)
            require(entry.timestamp > 0 && entry.timestamp <= timestamp && entry.bytes in 0..MAX_LOGICAL_BYTES) { "Invalid sync index entry" }
            timestamp = entry.timestamp - 1
        }
        index.previousIndex?.let(::validateIndexPath)
    }

    suspend fun publish(previous: SyncRemoteRead, head: DeltaHead,
        prepared: List<Pair<DeltaHead, DeltaBlock>> = emptyList()): Boolean {
        if (prepared.isEmpty()) return remote.compareAndSetJson(HEAD, previous, GSON.toJson(head))
        require(prepared.last().first == head)
        val oldHead = previous.json?.let { GSON.fromJson(it, DeltaHead::class.java) } ?: DeltaHead()
        val oldIndex = parseIndex(previous.json)?.also {
            require(it.recent.first().head() == oldHead) { "Invalid previous read index" }
        }
        val entries = prepared.asReversed().map { (position, block) ->
            val json = GSON.toJson(block)
            val bytes = json.toByteArray(Charsets.UTF_8).size
            DeltaIndexEntry(position.timestamp, position.block, bytes, json.takeIf { bytes <= INLINE_BLOCK_BYTES })
        }.toMutableList()
        entries += oldIndex?.recent ?: if (oldHead.timestamp > 0) listOf(DeltaIndexEntry(oldHead.timestamp, oldHead.block)) else emptyList()
        var previousIndex = oldIndex?.previousIndex
        var indexedThrough = oldIndex?.indexedThrough ?: 0
        while (entries.count { it.timestamp > indexedThrough } > INDEX_ENTRIES) {
            val pageEntries = entries.filter { it.timestamp > indexedThrough }.takeLast(INDEX_ENTRIES).map { it.copy(inline = null) }
            val page = DeltaReadIndex(recent = pageEntries, previousIndex = previousIndex, indexedThrough = indexedThrough)
            validateIndex(page)
            val json = GSON.toJson(page)
            val bytes = json.toByteArray(Charsets.UTF_8)
            val path = "delta-v2/indexes/${checksum(bytes)}.json"
            require(bytes.size <= MAX_FILE_BYTES)
            remote.uploadImmutableJson(path, json)
            cache.write(path, bytes)
            previousIndex = path
            indexedThrough = pageEntries.first().timestamp
        }
        var inlineBytes = 0
        val bounded = entries.take(INDEX_ENTRIES).map { entry ->
            val size = entry.inline?.toByteArray(Charsets.UTF_8)?.size ?: 0
            if (inlineBytes + size > INLINE_TOTAL_BYTES) entry.copy(inline = null)
            else entry.also { inlineBytes += size }
        }.toMutableList()
        fun body(): String = GSON.toJsonTree(head).asJsonObject.apply {
            add("readAhead", GSON.toJsonTree(DeltaReadIndex(recent = bounded, previousIndex = previousIndex, indexedThrough = indexedThrough)))
        }.toString()
        var json = body()
        // Escaping JSON inside JSON also consumes bytes; enforce the final wire size.
        while (json.toByteArray(Charsets.UTF_8).size > MAX_HEAD_BYTES) {
            val last = bounded.indexOfLast { it.inline != null }
            check(last >= 0) { "Sync head exceeds size limit" }
            bounded[last] = bounded[last].copy(inline = null)
            json = body()
        }
        return remote.compareAndSetJson(HEAD, previous, json)
    }

    suspend fun writeBlock(block: DeltaBlock): String {
        val json = GSON.toJson(block)
        val path = "delta-v2/blocks/${checksum(json.toByteArray(Charsets.UTF_8))}.json"
        write(path, json)
        cache.write(path, json.toByteArray(Charsets.UTF_8))
        return path
    }

    suspend fun block(head: DeltaHead): DeltaBlock = readBlock(head).block

    suspend fun readBlock(head: DeltaHead, expectedBytes: Int? = null): DeltaBlockRead {
        validateBlockPath(head.block)
        val cachedBytes = cached(head.block)
        val hit = cachedBytes ?: readIndex?.second?.recent?.firstOrNull { it.block == head.block }?.inline?.toByteArray(Charsets.UTF_8)
        val json = hit?.toString(Charsets.UTF_8) ?: read(head.block, expectedBytes)
        require(head.block == "delta-v2/blocks/${checksum(json.toByteArray(Charsets.UTF_8))}.json") { "Delta block checksum mismatch" }
        val block = GSON.fromJson(json, DeltaBlock::class.java)
        require(block.schema == 2 && block.timestamp == head.timestamp && block.previous.timestamp < block.timestamp) {
            "Invalid incremental sync chain"
        }
        require((block.previous.timestamp == 0L) == block.previous.block.isEmpty()) { "Invalid previous sync head" }
        if (block.previous.block.isNotEmpty()) validateBlockPath(block.previous.block)
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(expectedBytes == null || expectedBytes == bytes.size) { "Sync block size does not match index" }
        if (cachedBytes == null) cache.write(head.block, bytes)
        return DeltaBlockRead(block, hit != null, bytes.size)
    }

    private fun validateIndexPath(path: String) {
        require(path.matches(Regex("delta-v2/indexes/[a-f0-9]{64}\\.json"))) { "Invalid sync index path" }
    }

    private suspend fun indexPage(path: String): DeltaReadIndex {
        validateIndexPath(path)
        val bytes = cached(path) ?: download(path).toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_FILE_BYTES && checksum(it) == hash(path)) { "Corrupt sync index page" }
            cache.write(path, it)
        }
        return GSON.fromJson(bytes.toString(Charsets.UTF_8), DeltaReadIndex::class.java).also(::validateIndex)
    }

    /** Validate the complete chain before applying; retain only pointers, with bodies in the cache. */
    suspend fun chain(head: DeltaHead, cursor: DeltaHead, onRead: (DeltaBlockRead) -> Unit): List<DeltaHead> {
        val result = arrayListOf<DeltaHead>()
        var next = head
        var index = readIndex?.takeIf { it.first == head }?.second
        val pages = hashSetOf<String>()
        while (index != null && next.timestamp > cursor.timestamp) {
            val entries = index.recent.takeWhile { it.timestamp > cursor.timestamp }
            var offset = 0
            while (offset < entries.size) {
                currentCoroutineContext().ensureActive()
                // Large blocks get their own window; chunk requests share the same three permits.
                val window = arrayListOf<DeltaIndexEntry>()
                var bytes = 0L
                while (offset < entries.size && window.size < 3) {
                    val entry = entries[offset]
                    val size = entry.bytes.takeIf { it > 0 } ?: MAX_LOGICAL_BYTES
                    if (window.isNotEmpty() && bytes + size > 2 * 1024 * 1024) break
                    window += entry
                    bytes += size
                    offset++
                }
                val values = coroutineScope { window.map { async { readBlock(it.head(), it.bytes.takeIf { size -> size > 0 }) } }.awaitAll() }
                window.zip(values).forEach { (entry, value) ->
                    require(entry.head() == next && (entry.bytes == 0 || entry.bytes == value.bytes)) { "Discontinuous sync read index" }
                    result += next
                    next = value.block.previous
                    onRead(value)
                }
            }
            val path = index.previousIndex
            index = if (next.timestamp > cursor.timestamp && path != null) {
                require(pages.add(path)) { "Cyclic sync index" }
                val page = indexPage(path)
                val remaining = page.recent.dropWhile { it.timestamp > next.timestamp }
                require(remaining.firstOrNull()?.head() == next) { "Disconnected sync index page" }
                page.copy(recent = remaining)
            } else null
        }
        // Old writers may omit optional indexes, or only the tail of an old chain is indexed.
        while (next.timestamp > cursor.timestamp) {
            currentCoroutineContext().ensureActive()
            val value = readBlock(next)
            result += next
            next = value.block.previous
            onRead(value)
        }
        check(next == cursor) { "Remote sync journal does not match the saved cursor" }
        return result
    }

    private fun validateBlockPath(path: String) {
        require(path.matches(Regex("delta-v2/blocks/[a-f0-9]{64}\\.json"))) { "Invalid delta block path" }
    }

    internal suspend fun write(path: String, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_LOGICAL_BYTES) { "Sync block exceeds size limit" }
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

    internal suspend fun read(path: String, expectedBytes: Int? = null): String {
        val json = download(path)
        val manifest = DeltaJson.parse(json)
        if (manifest["format"]?.asString != FORMAT) return json
        val size = manifest["bytes"].asLong
        require(size in 0..MAX_LOGICAL_BYTES.toLong() && (expectedBytes == null || size == expectedBytes.toLong())) { "Invalid sync chunk length" }
        val output = ByteArrayOutputStream()
        val parts = manifest.getAsJsonArray("parts").map { it.asString }
        require(parts.size.toLong() == (size + CHUNK_BYTES - 1) / CHUNK_BYTES) { "Invalid sync chunk count" }
        parts.forEach { require(it.matches(Regex("[a-f0-9]{64}"))) { "Invalid sync chunk path" } }
        for (batch in parts.distinct().chunked(3)) coroutineScope {
            batch.map { hash -> async { chunk(hash) } }.awaitAll()
        }
        parts.forEachIndexed { index, hash ->
            val chunk = chunk(hash)
            val expected = minOf(CHUNK_BYTES.toLong(), size - index.toLong() * CHUNK_BYTES).toInt()
            require(chunk.size == expected) { "Unexpected sync chunk length" }
            output.write(chunk)
        }
        val bytes = output.toByteArray()
        require(bytes.size.toLong() == size && checksum(bytes) == manifest["sha256"].asString) { "Incomplete sync chunks" }
        return bytes.toString(Charsets.UTF_8)
    }

    private suspend fun chunk(hash: String): ByteArray = chunkLocks[(hash.hashCode() and Int.MAX_VALUE) % chunkLocks.size].withLock {
        val path = "delta-v2/chunks/$hash.json"
        cached(path) ?: download(path).let { json ->
            val bytes = Base64.getDecoder().decode(DeltaJson.parse(json)["data"].asString)
            require(bytes.size <= CHUNK_BYTES && checksum(bytes) == hash) { "Corrupt sync chunk" }
            cache.write(path, bytes)
            bytes
        }
    }
}
