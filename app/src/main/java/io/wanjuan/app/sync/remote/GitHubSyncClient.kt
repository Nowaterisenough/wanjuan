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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

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
        val selected = initialize()
        val result = request("GET", url("git", "trees", selected).newBuilder()
            .addQueryParameter("recursive", "1").build(), allowConflict = true)
        if (result.code == 409 && emptyRepositoryHint) {
            onProgress(1, 1)
            return directories.distinct().associateWith { emptyList() }
        }
        check(result.code == 200) { "GitHub 无法读取同步索引 (${result.code})" }
        val tree = requireNotNull(result.json).asJsonObject
        check(tree["truncated"]?.asBoolean != true) { "GitHub 仓库索引过大，无法安全读取完整变更清单" }
        val prefix = (config.directoryParts + listOf("sync", "v1")).joinToString("/") + "/"
        val requested = directories.distinct().associateWith { mutableListOf<SyncRemoteFile>() }
        for (element in tree.getAsJsonArray("tree")) {
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

    private suspend fun read(path: String): Content? {
        val file = request("GET", contentUrl(path), allowMissing = true).json?.asJsonObject ?: return null
        check(file["type"]?.asString == "file" && file["encoding"]?.asString == "base64") {
            "GitHub 同步文件格式不支持或超过 1 MB：$path"
        }
        val encoded = file["content"].asString.filterNot(Char::isWhitespace)
        return Content(file["sha"].asString, Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8))
    }

    override suspend fun downloadJson(relativePath: String): String? = read(relativePath)?.text

    override suspend fun readJson(file: SyncRemoteFile): SyncRemoteRead {
        val content = read(file.path) ?: return SyncRemoteRead(null)
        return SyncRemoteRead(content.text, "sha:${content.sha}")
    }

    override fun lastWrittenContent(relativePath: String): SyncRemoteRead? = written?.takeIf { it.first == relativePath }?.second

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

    private data class ApiResult(val code: Int, val json: JsonElement?)

    private suspend fun request(
        method: String,
        url: HttpUrl,
        body: JsonObject? = null,
        allowMissing: Boolean = false,
        allowConflict: Boolean = false
    ): ApiResult {
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "wanjuan-sync")
            .method(method, body?.toString()?.toRequestBody(jsonType)).build()
        return http.newCall(request).await().use { response ->
            if (allowMissing && response.code == 404) return@use ApiResult(404, null)
            if (allowConflict && response.code in listOf(409, 422)) return@use ApiResult(response.code, null)
            if (!response.isSuccessful) {
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
            ApiResult(response.code, response.body?.string()?.takeIf(String::isNotBlank)?.let(JsonParser::parseString))
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
