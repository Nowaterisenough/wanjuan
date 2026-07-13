package io.wanjuan.app.sync

import io.wanjuan.app.sync.remote.SyncRemoteFile
import io.wanjuan.app.sync.remote.SyncRemoteStore

class FakeSyncRemoteStore : SyncRemoteStore {
    data class Entry(var json: String, var lastModifiedAt: Long)

    val entries = linkedMapOf<String, Entry>()
    var downloads = 0
    var failUploads = false

    fun put(path: String, json: String, lastModifiedAt: Long) {
        entries[path] = Entry(json, lastModifiedAt)
    }

    override suspend fun ensureDirs() = Unit

    override suspend fun list(relativeDir: String): List<SyncRemoteFile> {
        val prefix = relativeDir.trim('/') + "/"
        return entries.mapNotNull { (path, entry) ->
            if (!path.startsWith(prefix) || path.removePrefix(prefix).contains('/')) {
                null
            } else {
                SyncRemoteFile(
                    path = path,
                    displayName = path.substringAfterLast('/'),
                    lastModifiedAt = entry.lastModifiedAt
                )
            }
        }
    }

    override suspend fun downloadJson(relativePath: String): String? {
        downloads += 1
        return entries[relativePath]?.json
    }

    override suspend fun uploadJson(relativePath: String, json: String) {
        if (failUploads) error("upload failed")
        val nextModified = (entries[relativePath]?.lastModifiedAt ?: 0L) + 1L
        entries[relativePath] = Entry(json, nextModified)
    }
}
