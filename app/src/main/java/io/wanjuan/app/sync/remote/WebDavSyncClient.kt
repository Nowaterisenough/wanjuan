package io.wanjuan.app.sync.remote

import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.lib.webdav.ObjectNotFoundException
import io.wanjuan.app.lib.webdav.WebDav
import io.wanjuan.app.lib.webdav.WebDavException
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import java.nio.charset.StandardCharsets

internal fun syncRemotePath(directory: String, href: String): String {
    val fileName = href.replace('\\', '/').trimEnd('/').substringAfterLast('/')
    require(fileName.isNotBlank()) { "Sync remote file name must not be blank" }
    require(fileName != "." && fileName != "..") {
        "Sync remote file name must not be a traversal segment"
    }
    return listOf(directory.trim('/'), fileName)
        .filter { it.isNotEmpty() }
        .joinToString("/")
}

class WebDavSyncClient(
    private val rootUrlProvider: () -> String,
    private val authorizationProvider: () -> Authorization?
) : SyncRemoteStore {

    companion object {
        private const val JSON = "application/json"
        const val SYNC_DIR = "sync/v1/"
        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    }

    private fun baseUrl(): String = rootUrlProvider().trimEnd('/') + "/"

    private fun rootUrl(): String = baseUrl() + SYNC_DIR

    private fun requireAuthorization(): Authorization {
        return authorizationProvider() ?: throw NoStackTraceException("WebDAV 未配置")
    }

    private fun resolve(relativePath: String, asDirectory: Boolean = false): String {
        val trimmed = relativePath.trim()
        require(!SCHEME.containsMatchIn(trimmed)) { "Sync path must be relative" }
        require(!trimmed.startsWith("/")) { "Sync path must be relative" }
        require(!trimmed.startsWith("\\")) { "Sync path must be relative" }

        val path = trimmed.replace('\\', '/').trimEnd('/')
        if (path.isEmpty()) {
            require(asDirectory) { "Sync path must not be blank" }
            return rootUrl()
        }

        val parts = path.split('/')
        require(parts.none { it.isEmpty() }) { "Sync path must not contain blank segments" }
        require(parts.none { it == ".." }) { "Sync path must not contain traversal segments" }

        val normalized = parts.joinToString("/")
        return rootUrl() + when {
            normalized.isEmpty() -> ""
            asDirectory -> "$normalized/"
            else -> normalized
        }
    }

    override suspend fun ensureDirs() {
        val authorization = requireAuthorization()
        val syncRoot = baseUrl() + "sync/"
        val root = rootUrl()
        listOf(
            syncRoot,
            root,
            "${root}devices/",
            "${root}books/",
            "${root}bookGroups/",
            "${root}bookProgress/",
            "${root}bookSources/",
            "${root}rssSources/",
            "${root}ruleSubs/",
            "${root}order/",
            "${root}tombstones/",
            "${root}tombstones/books/",
            "${root}tombstones/bookGroups/",
            "${root}tombstones/bookSources/",
            "${root}tombstones/rssSources/",
            "${root}tombstones/ruleSubs/"
        ).forEach { WebDav(it, authorization).makeAsDir() }
    }

    override suspend fun list(relativeDir: String): List<SyncRemoteFile> {
        val authorization = requireAuthorization()
        val directory = relativeDir.trim('/').takeIf { it.isNotEmpty() }
        return WebDav(resolve(relativeDir, asDirectory = true), authorization).listFiles().map { file ->
            SyncRemoteFile(
                path = syncRemotePath(directory.orEmpty(), file.urlName),
                displayName = file.displayName,
                lastModifiedAt = file.lastModify
            )
        }
    }

    suspend inline fun <reified T> download(relativePath: String): T? {
        return GSON.fromJsonObject<T>(downloadJson(relativePath) ?: return null).getOrNull()
    }

    override suspend fun downloadJson(relativePath: String): String? {
        val authorization = requireAuthorization()
        val bytes = try {
            WebDav(resolve(relativePath), authorization).download()
        } catch (e: ObjectNotFoundException) {
            return null
        } catch (e: WebDavException) {
            if (e.isNotFound()) return null
            throw e
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun WebDavException.isNotFound(): Boolean {
        return message?.contains("\n404:", ignoreCase = true) == true ||
            message?.contains("code:404", ignoreCase = true) == true
    }

    suspend fun upload(relativePath: String, payload: Any) {
        uploadJson(relativePath, GSON.toJson(payload))
    }

    override suspend fun uploadJson(relativePath: String, json: String) {
        val authorization = requireAuthorization()
        WebDav(resolve(relativePath), authorization)
            .upload(json.toByteArray(StandardCharsets.UTF_8), JSON)
    }

    suspend fun delete(relativePath: String): Boolean {
        val authorization = requireAuthorization()
        return WebDav(resolve(relativePath), authorization).delete()
    }
}

fun AppWebDav.newSyncClient(): WebDavSyncClient {
    return WebDavSyncClient(
        rootUrlProvider = { syncRootWebDavUrl() },
        authorizationProvider = { authorization }
    )
}
