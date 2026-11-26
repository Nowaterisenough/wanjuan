package io.wanjuan.app.sync

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import io.wanjuan.app.data.appDb
import io.wanjuan.app.model.localBook.LocalBook
import io.wanjuan.app.sync.remote.GitHubSyncClient
import io.wanjuan.app.sync.remote.GitHubSyncConfig
import io.wanjuan.app.sync.remote.gitBookSha
import io.wanjuan.app.utils.GSON
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import splitties.init.appCtx
import java.io.File
import java.util.Base64
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TestGitHubBookLibraryInstrumented {
    @Test fun uploadsAndRestoresOriginalsInOnePassAndKeepsConflictingEdits() = runBlocking {
        assumeTrue("Use an empty test bookshelf", appDb.bookDao.all.isEmpty())
        val target = UUID.randomUUID().toString()
        val file = File(appCtx.cacheDir, "local-$target.txt")
        val localBytes = ByteArray(150001) { (it % 251).toByte() }
        file.writeBytes(localBytes)
        val book = LocalBook.importFile(Uri.fromFile(file))
        val repo = linkedMapOf("nested/云端.txt" to "来自仓库的正文".toByteArray())
        fun sha(data: ByteArray) = gitBookSha(data.size.toLong(), data.inputStream())
        var puts = 0
        var downloads = 0
        var corruptDownload = false
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.pathSegments.drop(3).joinToString("/")
            var code = 200
            val bytes = when {
                path.isEmpty() -> """{"default_branch":"main","size":1}""".toByteArray()
                path == "branches/main" -> "{}".toByteArray()
                path == "git/trees/main" -> GSON.toJson(mapOf("tree" to repo.map { (name, data) ->
                    mapOf("type" to "blob", "path" to "wanjuan/books/$name", "sha" to sha(data), "size" to data.size)
                })).toByteArray()
                path.startsWith("git/blobs/") -> {
                    downloads++
                    assertEquals("application/vnd.github.raw+json", request.header("Accept"))
                    repo.values.first { sha(it) == path.substringAfterLast('/') }.let { if (corruptDownload) ByteArray(it.size) else it }
                }
                path.startsWith("contents/wanjuan/books/") && request.method == "PUT" -> {
                    val name = path.removePrefix("contents/wanjuan/books/")
                    val json = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
                    assertEquals("main", json["branch"].asString)
                    assertEquals(repo[name]?.let(::sha), json["sha"]?.asString)
                    val data = Base64.getDecoder().decode(json["content"].asString)
                    repo[name] = data
                    puts++
                    code = 201
                    GSON.toJson(mapOf("content" to mapOf("sha" to sha(data)))).toByteArray()
                }
                else -> error("Unexpected request $path")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(bytes.toResponseBody()).build()
        }.build()
        val client = GitHubSyncClient(GitHubSyncConfig("owner", "repo", "test"), http)
        try {
            val first = GitHubBookLibrary.sync(target, client)
            assertTrue(first.toString(), first.isSuccess)
            assertEquals(1, first.uploaded)
            assertEquals(1, first.downloaded)
            val path = "${SyncIds.bookId(book)}/${file.name}"
            assertArrayEquals(localBytes, repo[path])
            val imported = appDb.bookDao.all.single { it.originName == "云端.txt" }
            assertEquals("来自仓库的正文", LocalBook.getBookInputStream(imported).bufferedReader().use { it.readText() })
            assertTrue(GitHubBookLibrary.sync(target, client).isSuccess)
            assertEquals(1, puts)
            assertEquals(1, downloads)
            repo[path] = "new remote version".toByteArray()
            corruptDownload = true
            assertEquals(1, GitHubBookLibrary.sync(target, client).failed)
            assertArrayEquals(localBytes, LocalBook.getBookInputStream(book).use { it.readBytes() })
            corruptDownload = false
            assertTrue(GitHubBookLibrary.sync(target, client).isSuccess)
            assertEquals("new remote version", LocalBook.getBookInputStream(book).bufferedReader().use { it.readText() })
            file.writeText("next local version")
            assertTrue(GitHubBookLibrary.sync(target, client).isSuccess)
            assertArrayEquals(file.readBytes(), repo[path])
            file.appendText("local edit")
            repo[path] = "remote edit".toByteArray()
            assertEquals(1, GitHubBookLibrary.sync(target, client).failed)
            assertEquals(2, puts)
            assertTrue(file.delete())
            assertTrue(GitHubBookLibrary.sync(target, client).isSuccess)
            assertEquals("remote edit", LocalBook.getBookInputStream(book).bufferedReader().use { it.readText() })
            assertEquals(2, appDb.bookDao.all.size)
        } finally {
            for (row in appDb.bookDao.all.filter { it.bookUrl == book.bookUrl || it.bookUrl.startsWith("wanjuan-library://$target/") }) {
                GitHubBookLibrary.copyFile(row).delete()
                appDb.bookDao.delete(row)
            }
            file.delete()
        }
    }
}
