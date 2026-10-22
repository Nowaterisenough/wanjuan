package io.wanjuan.app.sync.remote

import android.net.Uri
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.lib.webdav.ObjectNotFoundException
import io.wanjuan.app.lib.webdav.WebDav
import io.wanjuan.app.lib.webdav.WebDavException
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.utils.fromJsonObject
import java.nio.charset.StandardCharsets
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

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
    private val authorizationProvider: () -> Authorization?,
    private val authorizationLoader: suspend () -> Authorization? = { authorizationProvider() }
) : SyncRemoteStore {

    companion object {
        const val SYNC_DIR = "sync/v1/"
        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
        private val ensureDirsMutex = Mutex()
        private var readyEndpoint: String? = null
        private val readyDirectories = hashSetOf<String>()
    }

    private fun baseUrl(): String = rootUrlProvider().trimEnd('/') + "/"

    private fun rootUrl(): String = baseUrl() + SYNC_DIR

    private suspend fun requireAuthorization(): Authorization {
        return authorizationProvider()
            ?: authorizationLoader()
            ?: throw NoStackTraceException("WebDAV 未配置")
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
        val base = baseUrl()
        val endpoint = "$base\u0000${authorization.data}"
        ensureDirsMutex.withLock {
            if (readyEndpoint == endpoint) return

            WebDav(base, authorization).makeAsDir()
            val syncRoot = base + "sync/"
            val root = syncRoot + "v1/"
            listOf(syncRoot, root).forEach { WebDav(it, authorization).makeAsDir() }
            readyDirectories.clear()
            readyEndpoint = endpoint
        }
    }

    private suspend fun ensureParent(relativePath: String) {
        ensureDirs()
        val authorization = requireAuthorization()
        val parents = relativePath.substringBeforeLast('/', "").split('/').filter(String::isNotEmpty)
        ensureDirsMutex.withLock {
            var current = ""
            for (part in parents) {
                current = if (current.isEmpty()) part else "$current/$part"
                val url = resolve(current, asDirectory = true)
                if (url !in readyDirectories) {
                    WebDav(url, authorization).makeAsDir()
                    readyDirectories += url
                }
            }
        }
    }

    suspend fun ensureDirectory(relativeDir: String) {
        resolve(relativeDir, asDirectory = true)
        ensureParent(relativeDir.trimEnd('/') + "/")
    }

    override suspend fun snapshot(directories: List<String>, onProgress: (Int, Int) -> Unit): Map<String, List<SyncRemoteFile>> = coroutineScope {
        val permits = Semaphore(3)
        val unique = directories.distinct()
        val completed = AtomicInteger()
        onProgress(0, unique.size)
        unique.map { directory ->
            async { permits.withPermit {
                val files = list(directory)
                onProgress(completed.incrementAndGet(), unique.size)
                directory to files
            } }
        }.awaitAll().toMap()
    }

    override suspend fun list(relativeDir: String): List<SyncRemoteFile> {
        val authorization = requireAuthorization()
        val directory = relativeDir.trim('/').takeIf { it.isNotEmpty() }
        val resource = WebDav(resolve(relativeDir, asDirectory = true), authorization)
        // Absence is never a deletion; tombstones carry deletions explicitly.
        val files = try {
            resource.listFiles()
        } catch (e: ObjectNotFoundException) {
            emptyList()
        } catch (e: WebDavException) {
            if (e.isNotFound()) emptyList() else throw e
        }
        return files.filterNot { it.isDir }.map { file ->
            SyncRemoteFile(
                path = syncRemotePath(directory.orEmpty(), file.urlName),
                displayName = file.displayName,
                lastModifiedAt = file.lastModify,
                revision = file.etag?.let { "etag:$it" }
            )
        }
    }

    override suspend fun readJson(file: SyncRemoteFile): SyncRemoteRead {
        val content = WebDav(resolve(file.path), requireAuthorization()).downloadVersioned()
            ?: return SyncRemoteRead(null)
        return SyncRemoteRead(content.bytes.toString(StandardCharsets.UTF_8), content.etag?.let { "etag:$it" })
    }

    override suspend fun compareAndSetJson(path: String, previous: SyncRemoteRead, json: String): Boolean {
        ensureParent(path)
        val etag = previous.revision?.takeIf { it.startsWith("etag:") }?.removePrefix("etag:")
        check(previous.json == null || (etag != null && !etag.startsWith("W/"))) {
            "WebDAV must provide a strong ETag for safe incremental synchronization"
        }
        val old = previous.json?.let { WebDav.VersionedContent(it.toByteArray(StandardCharsets.UTF_8), etag, null) }
        return WebDav(resolve(path), requireAuthorization())
            .uploadIfUnchangedVersioned(json.toByteArray(StandardCharsets.UTF_8), old).successful
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

    suspend fun upload(relativePath: String, bytes: ByteArray, contentType: String) {
        ensureParent(relativePath)
        WebDav(resolve(relativePath), requireAuthorization()).upload(bytes, contentType)
    }

    suspend fun upload(relativePath: String, uri: Uri, contentType: String) {
        ensureParent(relativePath)
        WebDav(resolve(relativePath), requireAuthorization()).upload(uri, contentType)
    }

    suspend fun upload(relativePath: String, file: File, contentType: String) {
        ensureParent(relativePath)
        WebDav(resolve(relativePath), requireAuthorization()).upload(file, contentType)
    }

    fun directoryUrl(relativePath: String): String = resolve(relativePath, asDirectory = true)

    fun authorizationOrNull(): Authorization? = authorizationProvider()

    override suspend fun uploadJson(relativePath: String, json: String) {
        ensureParent(relativePath)
        val authorization = requireAuthorization()
        val etag = WebDav(resolve(relativePath), authorization)
            .uploadJsonVersioned(json.toByteArray(StandardCharsets.UTF_8))
        rememberWrite(relativePath, json, etag)
    }

    @Volatile private var written: Pair<String, SyncRemoteRead>? = null

    private fun rememberWrite(path: String, json: String, etag: String?) {
        written = null
        if (!etag.isNullOrBlank()) written = path to SyncRemoteRead(json, "etag:$etag")
    }

    override fun lastWrittenContent(relativePath: String): SyncRemoteRead? = written?.takeIf { it.first == relativePath }?.second

    private val bookUpdateMutex = Mutex()

    override suspend fun updateBook(
        id: String,
        transform: (SyncBookPayload?) -> SyncBookPayload?
    ): SyncBookPayload? = bookUpdateMutex.withLock {
        ensureParent("books/$id.json")
        val resource = WebDav(resolve("books/$id.json"), requireAuthorization())
        repeat(3) {
            val previous = resource.downloadVersioned()
            val remote = previous?.let {
                GSON.fromJsonObject<SyncBookPayload>(it.bytes.toString(StandardCharsets.UTF_8)).getOrThrow()
            }
            require(remote == null || remote.bookSyncId == id) { "Remote book ID mismatch" }
            val updated = transform(remote) ?: return@withLock null
            val json = GSON.toJson(updated)
            if (previous?.bytes?.toString(StandardCharsets.UTF_8) == json) {
                rememberWrite("books/$id.json", json, previous.etag)
                return@withLock updated
            }
            val write = resource.uploadIfUnchangedVersioned(json.toByteArray(StandardCharsets.UTF_8), previous)
            if (write.successful) {
                rememberWrite("books/$id.json", json, write.etag)
                return@withLock updated
            }
        }
        throw NoStackTraceException("书籍同步冲突，请稍后重试")
    }

    override suspend fun delete(relativePath: String): Boolean {
        val authorization = requireAuthorization()
        return WebDav(resolve(relativePath), authorization).delete()
    }
}

fun AppWebDav.newSyncClient(): WebDavSyncClient {
    return WebDavSyncClient(
        rootUrlProvider = { syncRootWebDavUrl() },
        authorizationProvider = { configuredAuthorization() },
        authorizationLoader = { requireAuthorization() }
    )
}
