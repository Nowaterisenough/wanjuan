package io.wanjuan.app.sync.remote

import io.wanjuan.app.sync.merge.BookSyncMerge
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject

data class SyncRemoteFile(
    val path: String,
    val displayName: String,
    val lastModifiedAt: Long,
    val revision: String? = null
)

data class SyncRemoteRead(val json: String?, val revision: String? = null, val fromCache: Boolean = false)

interface SyncRemoteStore {
    suspend fun ensureDirs()
    suspend fun list(relativeDir: String): List<SyncRemoteFile>
    suspend fun downloadJson(relativePath: String): String?
    suspend fun uploadJson(relativePath: String, json: String)
    suspend fun delete(relativePath: String): Boolean = false
    suspend fun snapshot(
        directories: List<String>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Map<String, List<SyncRemoteFile>> {
        val unique = directories.distinct()
        onProgress(0, unique.size)
        return unique.mapIndexed { index, directory ->
            val files = list(directory)
            onProgress(index + 1, unique.size)
            directory to files
        }.toMap()
    }
    suspend fun readJson(file: SyncRemoteFile): SyncRemoteRead = SyncRemoteRead(downloadJson(file.path))
    suspend fun invalidateCached(relativePath: String) = Unit
    fun lastWrittenContent(relativePath: String): SyncRemoteRead? = null

    suspend fun readForUpdate(path: String): SyncRemoteRead = readJson(SyncRemoteFile(path, path.substringAfterLast('/'), 0))

    /** Must be atomic across devices. Never fall back to an unconditional journal-head write. */
    suspend fun compareAndSetJson(path: String, previous: SyncRemoteRead, json: String): Boolean =
        error("Conditional sync writes are not supported by this store")

    suspend fun updateBook(
        id: String,
        transform: (SyncBookPayload?) -> SyncBookPayload?
    ): SyncBookPayload? {
        val path = "books/$id.json"
        val remote = downloadJson(path)?.let { GSON.fromJsonObject<SyncBookPayload>(it).getOrThrow() }
        require(remote == null || remote.bookSyncId == id) { "Remote book ID mismatch" }
        val updated = transform(remote) ?: return null
        uploadJson(path, GSON.toJson(updated))
        return updated
    }
}

suspend inline fun <reified T> SyncRemoteStore.download(relativePath: String): T? =
    downloadJson(relativePath)?.let { GSON.fromJsonObject<T>(it).getOrThrow() }

suspend fun SyncRemoteStore.upload(relativePath: String, payload: Any) =
    uploadJson(relativePath, GSON.toJson(payload))

suspend fun SyncRemoteStore.mergeBook(payload: SyncBookPayload): SyncBookPayload =
    requireNotNull(updateBook(payload.bookSyncId) { remote ->
        if (remote == null) payload else BookSyncMerge.merge(payload, remote)
    })
