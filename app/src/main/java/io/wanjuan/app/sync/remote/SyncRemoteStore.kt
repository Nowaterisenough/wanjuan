package io.wanjuan.app.sync.remote

import io.wanjuan.app.sync.merge.BookSyncMerge
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject

data class SyncRemoteFile(
    val path: String,
    val displayName: String,
    val lastModifiedAt: Long
)

interface SyncRemoteStore {
    suspend fun ensureDirs()
    suspend fun list(relativeDir: String): List<SyncRemoteFile>
    suspend fun downloadJson(relativePath: String): String?
    suspend fun uploadJson(relativePath: String, json: String)

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

suspend fun SyncRemoteStore.mergeBook(payload: SyncBookPayload): SyncBookPayload =
    requireNotNull(updateBook(payload.bookSyncId) { remote ->
        if (remote == null) payload else BookSyncMerge.merge(payload, remote)
    })
