package io.wanjuan.app.sync.remote

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
}
