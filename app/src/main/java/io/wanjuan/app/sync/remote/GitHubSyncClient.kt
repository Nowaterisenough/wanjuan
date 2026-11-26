package io.wanjuan.app.sync.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.wanjuan.app.help.http.await
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.File
import io.wanjuan.app.constant.AppPattern
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class GitHubSyncClient(
    private val config: GitHubSyncConfig,
    private val http: OkHttpClient = defaultHttp,
    private val apiBase: HttpUrl = "https://api.github.com/".toHttpUrl()
) : SyncRemoteStore {
    private companion object {
        val defaultHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
        val jsonType = "application/json; charset=utf-8".toMediaType()
    }

    private val writes = Mutex()
    private val initialization = Mutex()
    private val rateLimitUntil = AtomicLong()
    @Volatile private var branch: String? = null
    @Volatile private var emptyRepositoryHint = false
    @Volatile private var written: Pair<String, SyncRemoteRead>? = null

    override suspend fun ensureDirs() {
        initialize()
    }

    private suspend fun initialize(): String = initialization.withLock {
        branch?.let { return@withLock it }
        config.validate()
        val repo = request("GET", url()).json!!.asJsonObject
        check(repo.getAsJsonObject("permissions")?.get("push")?.asBoolean != false) {
            "GitHub 仓库没有写入权限，请检查 Token 的 Contents 读写权限"
        }
        val selected = config.branch.ifBlank { repo["default_branch"].asString }
        emptyRepositoryHint = repo["size"]?.asLong == 0L
        // An empty repository has no branch yet; the first Contents PUT initializes it.
        if (repo["size"]?.asLong != 0L) {
            request("GET", url("branches", selected))
        }
        branch = selected
        selected
    }

    override suspend fun snapshot(directories: List<String>, onProgress: (Int, Int) -> Unit): Map<String, List<SyncRemoteFile>> {
        onProgress(0, 1)
        val entries = treeEntries()
        val prefix = (config.directoryParts + listOf("sync", "v1")).joinToString("/") + "/"
        val requested = directories.distinct().associateWith { mutableListOf<SyncRemoteFile>() }
        for (element in entries) {
            val entry = element.asJsonObject
            if (entry["type"]?.asString != "blob") continue
            val path = entry["path"].asString
            if (!path.startsWith(prefix)) continue
            val relative = path.removePrefix(prefix)
            syncPathParts(relative)
            val directory = relative.substringBeforeLast('/', "")
            requested[directory]?.add(SyncRemoteFile(relative, relative.substringAfterLast('/'), 0L,
                "sha:${entry["sha"].asString}"))
        }
        onProgress(1, 1)
        return requested
    }

    private suspend fun treeEntries(): Iterable<JsonElement> {
        val selected = initialize()
        val result = request("GET", url("git", "trees", selected).newBuilder()
            .addQueryParameter("recursive", "1").build(), allowConflict = true)
        if (result.code == 409 && emptyRepositoryHint) {
            return emptyList()
        }
        check(result.code == 200) { "GitHub 无法读取同步索引 (${result.code})" }
        val tree = requireNotNull(result.json).asJsonObject
        check(tree["truncated"]?.asBoolean != true) { "GitHub 仓库索引过大，无法安全读取完整变更清单" }
        return tree.getAsJsonArray("tree")
    }

    private val libraryPrefix get() = (config.directoryParts + "books").joinToString("/") + "/"

    internal suspend fun libraryFiles(): List<GitHubBookFile> = treeEntries().mapNotNull { element ->
        val entry = element.asJsonObject
        val fullPath = entry["path"].asString
        if (entry["type"].asString != "blob" || entry["mode"]?.asString == "120000" ||
            !fullPath.startsWith(libraryPrefix)) return@mapNotNull null
        val path = fullPath.removePrefix(libraryPrefix)
        if (!AppPattern.bookFileRegex.matches(path.substringAfterLast('/'))) return@mapNotNull null
        libraryPathParts(path)
        GitHubBookFile(path, entry["sha"].asString, entry["size"].asLong)
    }

    internal suspend fun downloadBook(file: GitHubBookFile, destination: File) {
        checkBookSize(file.size)
        require(file.sha.matches(Regex("[0-9a-f]{40}"))) { "书籍文件版本无效" }
        exchange("GET", url("git", "blobs", file.sha), accept = "application/vnd.github.raw+json") { response ->
            val source = response.body.source()
            destination.outputStream().use { output ->
                var remaining = file.size
                val buffer = ByteArray(64 * 1024)
                while (remaining > 0) {
                    val n = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    check(n > 0) { "书籍未下载完整" }
                    output.write(buffer, 0, n)
                    remaining -= n
                }
                check(source.exhausted()) { "书籍文件长度不匹配" }
            }
        }
        check(gitBookSha(destination) == file.sha) { "书籍文件校验失败" }
    }

    internal suspend fun uploadBook(path: String, file: File, previous: String?): String = writes.withLock {
        val selected = initialize()
        val parts = config.directoryParts + "books" + libraryPathParts(path)
        val size = file.length()
        checkBookSize(size)
        val metadata = JsonObject().apply {
            addProperty("message", "Sync book $path")
            addProperty("branch", selected)
            previous?.let { addProperty("sha", it) }
        }
        val prefix = (metadata.toString().dropLast(1) + ",\"content\":\"").toByteArray()
        val body = object : RequestBody() {
            override fun contentType() = jsonType
            override fun contentLength() = prefix.size + ((size + 2) / 3) * 4 + 2
            override fun writeTo(sink: BufferedSink) {
                sink.write(prefix)
                file.source().buffer().use { source ->
                    var remaining = size
                    while (remaining > 0) {
                        // Every non-final block must encode a whole number of Base64 triples.
                        val length = minOf(48 * 1024L, remaining)
                        sink.writeUtf8(source.readByteString(length).base64())
                        remaining -= length
                    }
                }
                sink.writeUtf8("\"}")
            }
        }
        exchange("PUT", url("contents", *parts.toTypedArray()), body, allowConflict = true) { response ->
            check(response.isSuccessful) { "仓库中的书籍已变化，请再次下拉刷新" }
            emptyRepositoryHint = false
            JsonParser.parseReader(requireNotNull(response.body).charStream()).asJsonObject
                .getAsJsonObject("content")["sha"].asString
        }
    }

    private fun url(vararg segments: String): HttpUrl = apiBase.newBuilder()
        .addPathSegment("repos")
        .addPathSegment(config.owner)
        .addPathSegment(config.repository)
        .apply { segments.forEach(::addPathSegment) }
        .build()

    private suspend fun contentUrl(path: String, directory: Boolean = false): HttpUrl {
        val parts = config.directoryParts + listOf("sync", "v1") + syncPathParts(path, directory)
        return url("contents", *parts.toTypedArray()).newBuilder()
            .addQueryParameter("ref", initialize()).build()
    }

    override suspend fun list(relativeDir: String): List<SyncRemoteFile> {
        val result = request("GET", contentUrl(relativeDir, directory = true), allowMissing = true)
        val entries = result.json ?: return emptyList()
        check(entries.isJsonArray) { "GitHub 同步路径不是目录" }
        // Contents listings are capped at 1,000 entries; never treat a partial list as complete.
        check(entries.asJsonArray.size() < 1000) { "GitHub 同步目录达到 1000 项上限，请拆分同步仓库" }
        val prefix = syncPathParts(relativeDir, true).joinToString("/")
        return entries.asJsonArray.mapNotNull { item ->
            val file = item.asJsonObject
            if (file["type"].asString != "file") return@mapNotNull null
            val name = file["name"].asString
            require(syncPathParts(name).size == 1) { "Invalid GitHub file name" }
            SyncRemoteFile(listOf(prefix, name).filter(String::isNotEmpty).joinToString("/"), name, 0L,
                file["sha"]?.asString?.let { "sha:$it" })
        }
    }

    private data class Content(val sha: String, val text: String)
    private data class ValidatedHead(val etag: String, val content: Content)
    @Volatile private var validatedHead: ValidatedHead? = null

    private suspend fun read(path: String): Content? {
        val file = request("GET", contentUrl(path), allowMissing = true).json?.asJsonObject ?: return null
        return parseContent(path, file)
    }

    private suspend fun parseContent(path: String, file: JsonObject): Content {
        check(file["type"]?.asString == "file") { "Unsupported GitHub sync file: $path" }
        val text = if (file["encoding"]?.asString == "base64") {
            val encoded = file["content"].asString.filterNot(Char::isWhitespace)
            Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8)
        } else {
            // Legacy files above 1 MB have no inline base64 content. Pin the raw read to its SHA.
            val blob = requireNotNull(request("GET", url("git", "blobs", file["sha"].asString)).json).asJsonObject
            check(blob["encoding"]?.asString == "base64") { "Unsupported GitHub blob encoding" }
            Base64.getDecoder().decode(blob["content"].asString.filterNot(Char::isWhitespace)).toString(Charsets.UTF_8)
        }
        return Content(file["sha"].asString, text)
    }

    override suspend fun downloadJson(relativePath: String): String? = read(relativePath)?.text

    override suspend fun readJson(file: SyncRemoteFile): SyncRemoteRead {
        val content = read(file.path) ?: return SyncRemoteRead(null)
        return SyncRemoteRead(content.text, "sha:${content.sha}")
    }

    override suspend fun readForUpdate(path: String): SyncRemoteRead {
        if (path != "delta-v2/head.json") return readJson(SyncRemoteFile(path, path.substringAfterLast('/'), 0))
        val cached = validatedHead
        val result = request("GET", contentUrl(path), allowMissing = true, ifNoneMatch = cached?.etag)
        if (result.code == 304) {
            val content = requireNotNull(cached).content
            return SyncRemoteRead(content.text, "sha:${content.sha}", fromCache = true)
        }
        val file = result.json?.asJsonObject
        if (file == null) {
            validatedHead = null
            return SyncRemoteRead(null)
        }
        val content = parseContent(path, file)
        validatedHead = result.etag?.let { ValidatedHead(it, content) }
        return SyncRemoteRead(content.text, "sha:${content.sha}")
    }

    override fun lastWrittenContent(relativePath: String): SyncRemoteRead? = written?.takeIf { it.first == relativePath }?.second

    override suspend fun compareAndSetJson(path: String, previous: SyncRemoteRead, json: String): Boolean = writes.withLock {
        val sha = previous.revision?.takeIf { it.startsWith("sha:") }?.removePrefix("sha:")
        require(previous.json == null || sha != null) { "Missing GitHub write revision" }
        put(path, json, sha)
    }

    override suspend fun uploadJson(relativePath: String, json: String) {
        writes.withLock {
            repeat(3) {
                val previous = read(relativePath)
                if (previous?.text == json) {
                    written = relativePath to SyncRemoteRead(json, "sha:${previous.sha}")
                    return@withLock
                }
                if (put(relativePath, json, previous?.sha)) return@withLock
            }
            throw IOException("GitHub 同步写入冲突，请稍后重试")
        }
    }

    override suspend fun uploadImmutableJson(relativePath: String, json: String) = writes.withLock {
        require(relativePath.matches(Regex("delta-v2/(blocks|chunks|indexes)/[a-f0-9]{64}\\.json"))) {
            "Invalid immutable sync path"
        }
        repeat(3) {
            if (put(relativePath, json, null)) return@withLock
            // An existing object or a competing branch writer can reject creation. Never
            // overwrite immutable content; verify duplicates and retry only missing paths.
            val existing = read(relativePath)
            if (existing != null) {
                check(existing.text == json) { "Immutable sync object content mismatch" }
                written = relativePath to SyncRemoteRead(json, "sha:${existing.sha}")
                return@withLock
            }
        }
        throw IOException("GitHub 同步对象创建冲突，请稍后重试")
    }

    override suspend fun updateBook(
        id: String,
        transform: (SyncBookPayload?) -> SyncBookPayload?
    ): SyncBookPayload? = writes.withLock {
        val path = "books/$id.json"
        repeat(3) {
            val previous = read(path)
            val remote = previous?.let { GSON.fromJsonObject<SyncBookPayload>(it.text).getOrThrow() }
            require(remote == null || remote.bookSyncId == id) { "Remote book ID mismatch" }
            val updated = transform(remote) ?: return@withLock null
            val json = GSON.toJson(updated)
            if (previous?.text == json) {
                written = path to SyncRemoteRead(json, "sha:${previous.sha}")
                return@withLock updated
            }
            if (put(path, json, previous?.sha)) return@withLock updated
        }
        throw IOException("GitHub 书籍同步冲突，请稍后重试")
    }

    private suspend fun put(path: String, json: String, sha: String?): Boolean {
        require(json.toByteArray(Charsets.UTF_8).size < 1024 * 1024) { "GitHub 单个同步文件必须小于 1 MB：$path" }
        val body = JsonObject().apply {
            addProperty("message", "Sync $path")
            addProperty("content", Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)))
            addProperty("branch", initialize())
            sha?.let { addProperty("sha", it) }
        }
        val result = request("PUT", contentUrl(path), body, allowConflict = true)
        if (result.code in 200..299) {
            emptyRepositoryHint = false
            written = null
            result.json?.asJsonObject?.getAsJsonObject("content")?.get("sha")?.asString?.let {
                written = path to SyncRemoteRead(json, "sha:$it")
            }
        }
        return result.code != 409 && result.code != 422
    }

    override suspend fun delete(relativePath: String): Boolean = writes.withLock {
        repeat(3) {
            val previous = read(relativePath) ?: return@withLock true
            val body = JsonObject().apply {
                addProperty("message", "Sync remove $relativePath")
                addProperty("sha", previous.sha)
                addProperty("branch", initialize())
            }
            val result = request("DELETE", contentUrl(relativePath), body, allowConflict = true)
            if (result.code != 409 && result.code != 422) {
                if (written?.first == relativePath) written = null
                return@withLock true
            }
        }
        throw IOException("GitHub 同步删除冲突，请稍后重试")
    }

    private data class ApiResult(val code: Int, val json: JsonElement?, val etag: String? = null)

    private suspend fun request(
        method: String,
        url: HttpUrl,
        body: JsonObject? = null,
        allowMissing: Boolean = false,
        allowConflict: Boolean = false,
        ifNoneMatch: String? = null
    ): ApiResult = exchange(method, url, body?.toString()?.toRequestBody(jsonType), allowMissing,
        allowConflict, ifNoneMatch) { response ->
        val json = if (response.isSuccessful) response.body.string().takeIf(String::isNotBlank)?.let(JsonParser::parseString) else null
        ApiResult(response.code, json, response.header("ETag"))
    }

    private suspend fun <T> exchange(
        method: String, url: HttpUrl, body: RequestBody? = null,
        allowMissing: Boolean = false, allowConflict: Boolean = false, ifNoneMatch: String? = null,
        accept: String = "application/vnd.github+json", read: (Response) -> T
    ): T {
        while (true) {
            val remaining = rateLimitUntil.get() - System.nanoTime()
            if (remaining <= 0) break
            delay(maxOf(1L, TimeUnit.NANOSECONDS.toMillis(remaining)))
        }
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "wanjuan-sync")
            .apply { ifNoneMatch?.let { header("If-None-Match", it) } }
            .method(method, body).build()
        val call = http.newCall(request)
        if (accept == "application/vnd.github.raw+json" || (body?.contentLength() ?: 0) > 1024 * 1024) {
            call.timeout().timeout(15, TimeUnit.MINUTES)
        }
        return call.await().use { response ->
            val allowed = (ifNoneMatch != null && response.code == 304) ||
                (allowMissing && response.code == 404) || (allowConflict && response.code in listOf(409, 422))
            if (!response.isSuccessful && !allowed) {
                if (response.code == 429 || response.code == 403) {
                    val seconds = response.header("Retry-After")?.toLongOrNull()
                    val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()
                    val waitMillis = when {
                        seconds != null -> seconds.coerceIn(1, 86400) * 1000
                        response.header("X-RateLimit-Remaining") == "0" && reset != null ->
                            (reset * 1000 - System.currentTimeMillis()).coerceAtLeast(1000)
                        else -> 60_000L
                    }
                    val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis)
                    rateLimitUntil.updateAndGet { maxOf(it, until) }
                }
                val reason = when (response.code) {
                    401 -> "Token 无效或已过期"
                    403, 429 -> if (response.header("X-RateLimit-Remaining") == "0" || response.code == 429)
                        "API 请求已限流，请稍后重试" else "没有访问权限或触发限流，请检查 Contents 读写权限后重试"
                    404 -> "仓库或分支不存在，或 Token 无访问权限"
                    else -> "请求失败，请稍后重试"
                }
                // Do not include remote bodies or credential-bearing request details in logs.
                throw IOException("GitHub $reason (${response.code})")
            }
            read(response)
        }
    }
}

data class GitHubSyncConfig(
    val owner: String,
    val repository: String,
    val token: String,
    val branch: String = "",
    val directory: String = "wanjuan"
) {
    val directoryParts: List<String> get() = syncPathParts(directory, directory = true)

    fun validate() {
        require(owner.matches(Regex("[A-Za-z0-9-]+"))) { "GitHub 仓库所有者格式不正确" }
        require(repository.matches(Regex("[A-Za-z0-9_.-]+")) && repository !in listOf(".", "..")) {
            "GitHub 仓库名格式不正确"
        }
        require(token.isNotBlank() && token.all { it.code in 33..126 }) { "请填写有效的 GitHub Token" }
        directoryParts
    }

    override fun toString(): String = "GitHubSyncConfig($owner/$repository, branch=$branch, directory=$directory)"
}

internal fun syncPathParts(path: String, directory: Boolean = false): List<String> {
    require(!path.startsWith('/') && !path.contains('\\') && !path.contains(':')) { "Sync path must be relative" }
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty()) {
        require(directory) { "Sync file path must not be empty" }
        return emptyList()
    }
    return trimmed.split('/').also { parts ->
        require(parts.none { it.isBlank() || it == "." || it == ".." || it.any(Char::isISOControl) }) {
            "Invalid sync path"
        }
    }
}
