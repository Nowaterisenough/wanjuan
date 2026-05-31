package io.wanjuan.app.sync.remote

import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.lib.webdav.ObjectNotFoundException
import io.wanjuan.app.lib.webdav.WebDav
import io.wanjuan.app.lib.webdav.WebDavException
import io.wanjuan.app.lib.webdav.WebDavFile
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import java.nio.charset.StandardCharsets

class WebDavSyncClient(
    private val rootUrlProvider: () -> String,
    private val authorizationProvider: () -> Authorization?
) {

    companion object {
        private const val JSON = "application/json"
        const val SYNC_DIR = "sync/v1/"
        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    }

    private fun baseUrl(): String = rootUrlProvider().trimEnd('/') + "/"

    private fun rootUrl(): String = baseUrl() + SYNC_DIR

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

    suspend fun ensureDirs() {
        val authorization = authorizationProvider() ?: return
        val syncRoot = baseUrl() + "sync/"
        val root = rootUrl()
        listOf(
            syncRoot,
            root,
            "${root}devices/",
            "${root}books/",
            "${root}bookProgress/",
            "${root}bookSources/",
            "${root}ruleSubs/",
            "${root}order/",
            "${root}tombstones/",
            "${root}tombstones/books/",
            "${root}tombstones/bookSources/",
            "${root}tombstones/ruleSubs/"
        ).forEach { WebDav(it, authorization).makeAsDir() }
    }

    suspend fun list(relativeDir: String): List<WebDavFile> {
        val authorization = authorizationProvider() ?: return emptyList()
        return WebDav(resolve(relativeDir, asDirectory = true), authorization).listFiles()
    }

    suspend inline fun <reified T> download(relativePath: String): T? {
        return GSON.fromJsonObject<T>(downloadJson(relativePath) ?: return null).getOrNull()
    }

    @PublishedApi
    internal suspend fun downloadJson(relativePath: String): String? {
        val authorization = authorizationProvider() ?: return null
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
        val authorization = authorizationProvider() ?: return
        val json = GSON.toJson(payload)
        WebDav(resolve(relativePath), authorization)
            .upload(json.toByteArray(StandardCharsets.UTF_8), JSON)
    }

    suspend fun delete(relativePath: String): Boolean {
        val authorization = authorizationProvider() ?: return false
        return WebDav(resolve(relativePath), authorization).delete()
    }
}

fun AppWebDav.newSyncClient(): WebDavSyncClient {
    return WebDavSyncClient(
        rootUrlProvider = { syncRootWebDavUrl() },
        authorizationProvider = { authorization }
    )
}
