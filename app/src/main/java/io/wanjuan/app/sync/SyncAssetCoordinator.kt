package io.wanjuan.app.sync

import android.net.Uri
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.model.remote.RemoteBookWebDav
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.utils.NetworkUtils
import io.wanjuan.app.utils.UrlUtil
import io.wanjuan.app.utils.normalizeFileName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

class SyncAssetCoordinator(
    private val client: WebDavSyncClient
) {
    companion object {
        private const val LibraryDir = "assets/library"
        private const val ExportDir = "assets/exports"
        private const val CachePackageDir = "assets/cachePackages"
    }

    fun remoteBookWebDav(): RemoteBookWebDav? {
        val authorization = client.authorizationOrNull() ?: return null
        return RemoteBookWebDav(
            rootBookUrl = client.directoryUrl(LibraryDir),
            authorization = authorization,
            ensureRoot = false
        )
    }

    suspend fun uploadCachePackage(fileName: String, zipFile: File) {
        requireNetworkAndConfiguration()
        client.ensureDirs()
        val safeFileName = safeFileName(
            fileName.trimEnd('/').removeSuffix(".zip"),
            "cache_${System.currentTimeMillis()}"
        )
        client.upload("$CachePackageDir/$safeFileName.zip", zipFile, "application/zip")
    }

    suspend fun uploadExport(uri: Uri, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            if (client.authorizationOrNull() == null) return
            client.ensureDirs()
            client.upload("$ExportDir/${safeFileName(fileName, "export")}", uri, "text/plain")
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    suspend fun uploadExport(bytes: ByteArray, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            if (client.authorizationOrNull() == null) return
            client.ensureDirs()
            client.upload("$ExportDir/${safeFileName(fileName, "export")}", bytes, "text/plain")
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    private fun requireNetworkAndConfiguration() {
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络未连接")
        if (client.authorizationOrNull() == null) throw NoStackTraceException("webDav未配置")
    }

    private fun safeFileName(fileName: String, fallback: String): String =
        UrlUtil.replaceReservedChar(fileName.normalizeFileName()).ifBlank { fallback }
}
