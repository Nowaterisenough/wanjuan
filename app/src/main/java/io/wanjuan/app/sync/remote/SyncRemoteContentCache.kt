package io.wanjuan.app.sync.remote

import com.google.gson.annotations.SerializedName
import io.wanjuan.app.sync.SyncIds
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.utils.GSON
import java.io.File

interface SyncRemoteContentCache {
    fun read(path: String, revision: String): String?
    fun write(path: String, revision: String, json: String)
    fun remove(path: String)
}

/** Disposable, endpoint-scoped cache. Corruption or missing entries always causes a fresh GET. */
class FileSyncRemoteContentCache(private val directory: File) : SyncRemoteContentCache {
    private data class Entry(
        @SerializedName("revision") val revision: String,
        @SerializedName("json") val json: String,
        @SerializedName("checksum") val checksum: String
    )

    private fun file(path: String) = File(directory, SyncIds.hashKey("file", path) + ".json")

    @Synchronized
    override fun read(path: String, revision: String): String? = runCatching {
        val entry = GSON.fromJson(file(path).readText(), Entry::class.java)
        entry?.json?.takeIf {
            entry.revision == revision && entry.checksum == SyncIds.hashKey("content", it)
        }
    }.getOrNull()

    @Synchronized
    override fun write(path: String, revision: String, json: String) {
        runCatching {
            directory.mkdirs()
            val target = file(path)
            val temporary = File(directory, target.name + ".tmp")
            temporary.writeText(GSON.toJson(Entry(revision, json, SyncIds.hashKey("content", json))))
            if (!temporary.renameTo(target)) temporary.delete()
        }
    }

    @Synchronized
    override fun remove(path: String) { runCatching { file(path).delete() } }
}

class CachingSyncRemoteStore(
    private val remote: SyncRemoteStore,
    private val cache: SyncRemoteContentCache
) : SyncRemoteStore by remote {
    private var knownDirectories = emptySet<String>()
    private var knownPaths = emptySet<String>()

    override suspend fun snapshot(directories: List<String>, onProgress: (Int, Int) -> Unit): Map<String, List<SyncRemoteFile>> {
        val snapshot = remote.snapshot(directories, onProgress)
        knownDirectories = snapshot.keys
        knownPaths = snapshot.values.flatten().mapTo(hashSetOf()) { it.path }
        return snapshot
    }
    override suspend fun invalidateCached(relativePath: String) { cache.remove(relativePath) }
    override suspend fun readJson(file: SyncRemoteFile): SyncRemoteRead {
        val revision = file.revision?.takeIf(String::isNotBlank)
        if (revision != null) {
            cache.read(file.path, revision)?.let { return SyncRemoteRead(it, revision, fromCache = true) }
        }
        val read = remote.readJson(file)
        if (read.json != null && !read.revision.isNullOrBlank()) {
            cache.write(file.path, read.revision, read.json)
        }
        return read
    }

    override suspend fun uploadJson(relativePath: String, json: String) {
        remote.uploadJson(relativePath, json)
        rememberUpload(relativePath, json)
    }

    override suspend fun updateBook(
        id: String,
        transform: (SyncBookPayload?) -> SyncBookPayload?
    ): SyncBookPayload? {
        val result = remote.updateBook(id, transform)
        if (result != null) rememberUpload("books/$id.json", GSON.toJson(result))
        return result
    }

    private fun rememberUpload(path: String, json: String) {
        // Never keep an old body after a successful write with an unknown server revision.
        cache.remove(path)
        remote.lastWrittenContent(path)?.takeIf { it.json == json }?.revision?.let {
            cache.write(path, it, json)
        }
    }

    override suspend fun delete(relativePath: String): Boolean {
        if (relativePath.substringBeforeLast('/', "") in knownDirectories && relativePath !in knownPaths) {
            cache.remove(relativePath)
            return true
        }
        val deleted = remote.delete(relativePath)
        if (deleted) cache.remove(relativePath)
        return deleted
    }
}
