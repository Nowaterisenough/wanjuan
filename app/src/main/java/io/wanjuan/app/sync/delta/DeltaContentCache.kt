package io.wanjuan.app.sync.delta

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Keys are immutable content hashes, never the mutable journal head. */
interface DeltaContentCache {
    fun read(path: String): ByteArray?
    fun write(path: String, bytes: ByteArray)
    fun remove(path: String)
    fun trim() = Unit
}

class MemoryDeltaContentCache : DeltaContentCache {
    private val values = ConcurrentHashMap<String, ByteArray>()
    override fun read(path: String) = values[path]?.clone()
    override fun write(path: String, bytes: ByteArray) { values[path] = bytes.clone() }
    override fun remove(path: String) { values.remove(path) }
}

class FileDeltaContentCache(private val root: File, private val maxBytes: Long = 128L * 1024 * 1024) : DeltaContentCache {
    private fun file(path: String): File {
        require(path.matches(Regex("delta-v2/(blocks|chunks|indexes)/[a-f0-9]{64}\\.json")))
        return File(root, path.removePrefix("delta-v2/"))
    }

    override fun read(path: String): ByteArray? = runCatching {
        val file = file(path)
        if (!file.isFile) return null
        file.readBytes().also { file.setLastModified(System.currentTimeMillis()) }
    }.getOrNull()

    override fun write(path: String, bytes: ByteArray) {
        val target = file(path)
        if (target.isFile && target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) return
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Cannot create sync cache" }
        val temporary = File.createTempFile("delta-", ".tmp", target.parentFile)
        try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                check(!target.exists() || target.delete()) { "Cannot replace sync cache" }
                check(temporary.renameTo(target)) { "Cannot save sync cache" }
            }
        } finally { temporary.delete() }
    }

    override fun remove(path: String) { file(path).delete() }

    // Trim only after a sync finishes: downloaded history stays pinned until it is applied.
    override fun trim() {
        val files = listOf("blocks", "chunks", "indexes").flatMap { File(root, it).listFiles().orEmpty().toList() }
            .filter { it.isFile }.sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }
        files.forEach { file ->
            if (bytes > maxBytes) {
                val size = file.length()
                if (file.delete()) bytes -= size
            }
        }
    }
}
