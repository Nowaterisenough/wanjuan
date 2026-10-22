package io.wanjuan.app.sync.remote

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.wanjuan.app.sync.merge.BookSyncMerge
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Base64

class TestGitHubSyncClient {
    @Test
    fun oneTreeSnapshotIndexesAllObjectDirectoriesWithRevisions() = runBlocking {
        Server().use { server ->
            server.contents["wanjuan/sync/v1/books/a.json"] = "{}"
            server.contents["wanjuan/sync/v1/bookSources/b.json"] = "{}"
            server.contents["unrelated/file.json"] = "{}"
            val client = server.client()
            val snapshot = client.snapshot(listOf("books", "bookSources", "books"))
            assertEquals(1, server.treeRequests)
            assertEquals(setOf("books", "bookSources"), snapshot.keys)
            assertEquals("sha:0", snapshot.getValue("books").single().revision)
            assertEquals(0, server.contentReads)
            assertEquals(2, snapshot.values.sumOf { it.size })
        }
    }

    @Test
    fun emptyTreeIsSupportedAndTruncatedTreeFailsSafely() = runBlocking {
        Server().use { server ->
            server.emptyRepository = true
            assertTrue(server.client().snapshot(listOf("books")).getValue("books").isEmpty())
        }
        Server().use { server ->
            server.truncatedTree = true
            assertTrue(runCatching { server.client().snapshot(listOf("books")) }.isFailure)
        }
    }

    @Test
    fun successfulPutRecordsBodyAndRevisionForIncrementalReadback() = runBlocking {
        Server().use { server ->
            val client = server.client()
            client.uploadJson("books/a.json", "{}")
            assertEquals(SyncRemoteRead("{}", "sha:1"), client.lastWrittenContent("books/a.json"))
        }
    }
    @Test
    fun roundTripListDeleteAndNoOpWrite() = runBlocking {
        Server().use { server ->
            val client = server.client(branch = "feature/sync", directory = "阅读 data")
            client.ensureDirs()
            assertNull(client.downloadJson("books/a.json"))
            client.uploadJson("books/a.json", "{\"title\":\"测试\"}")
            assertEquals("{\"title\":\"测试\"}", client.downloadJson("books/a.json"))
            client.uploadJson("books/a.json", "{\"title\":\"测试\"}")
            assertEquals(1, server.puts)
            assertTrue(server.refs.all { it == "ref=feature%2Fsync" })
            val file = client.list("books").single()
            assertEquals("books/a.json", file.path)
            assertEquals(0L, file.lastModifiedAt)
            assertTrue(client.delete("books/a.json"))
            assertTrue(client.delete("books/a.json"))
            assertTrue(client.list("books").isEmpty())
            assertTrue(server.headersValid)
        }
    }

    @Test
    fun concurrentBookWriteReReadsShaAndMergesNewCatalog() = runBlocking {
        Server().use { server ->
            val local = book(progress = 100, catalog = 10)
            val remote = book(progress = 1, catalog = 30)
            server.contents["wanjuan/sync/v1/books/id.json"] = GSON.toJson(book(progress = 1, catalog = 10))
            server.conflictContent = GSON.toJson(remote)
            val merged = server.client().mergeBook(local)
            assertEquals(100L, merged.progressUpdatedAt)
            assertEquals(30L, merged.catalogUpdatedAt)
            assertEquals(30, merged.book.totalChapterNum)
            assertEquals(2, server.puts)
            val saved = GSON.fromJsonObject<SyncBookPayload>(server.contents.values.single()).getOrThrow()
            assertEquals(BookSyncMerge.version(merged), BookSyncMerge.version(saved))
        }
    }

    @Test
    fun emptyRepositoryUsesDefaultBranchAndFirstPutInitializes() = runBlocking {
        Server().use { server ->
            server.emptyRepository = true
            val client = server.client()
            client.uploadJson("order/bookshelf.json", "{}")
            assertEquals(0, server.branchChecks)
            assertEquals("main", server.lastWriteBranch)
            assertEquals("{}", client.downloadJson("order/bookshelf.json"))
        }
    }

    @Test
    fun accessErrorsAreNotTreatedAsMissingAndNeverExposeToken() = runBlocking {
        for (code in listOf(401, 403, 404, 429, 500)) {
            Server().use { server ->
                server.failure = code
                val error = runCatching { server.client().downloadJson("books/a.json") }.exceptionOrNull()
                assertNotNull("HTTP $code must fail repository validation", error)
                assertFalse(error!!.message.orEmpty().contains("secret-token"))
                assertTrue(error.message.orEmpty().contains(code.toString()))
            }
        }
    }

    @Test
    fun readOnlyTokenAndMissingBranchFailBeforeAnyWrite() = runBlocking {
        Server().use { server ->
            server.pushAllowed = false
            assertTrue(runCatching { server.client().ensureDirs() }.isFailure)
            assertEquals(0, server.puts)
        }
        Server().use { server ->
            server.missingBranch = true
            assertTrue(runCatching { server.client(branch = "missing").ensureDirs() }.isFailure)
            assertEquals(0, server.puts)
        }
    }

    @Test
    fun permanentConflictsAndOversizedJsonRemainFailures() = runBlocking {
        Server().use { server ->
            server.alwaysConflict = true
            assertTrue(runCatching { server.client().uploadJson("books/a.json", "{}") }.isFailure)
            assertEquals(3, server.puts)
        }
        Server().use { server ->
            assertTrue(runCatching { server.client().uploadJson("books/a.json", "a".repeat(1024 * 1024)) }.isFailure)
            assertEquals(0, server.puts)
        }
    }

    @Test
    fun truncatedDirectoryAndTraversalPathsAreRejected() = runBlocking {
        Server().use { server ->
            server.largeDirectory = true
            assertTrue(runCatching { server.client().list("books") }.isFailure)
            for (path in listOf("../books/a.json", "/books/a.json", "books//a.json", "https://other/a", "books/./a", "books\\a")) {
                assertTrue(path, runCatching { server.client().downloadJson(path) }.isFailure)
            }
        }
        assertTrue(runCatching { GitHubSyncConfig("owner", "repo", "token", directory = "../escape").validate() }.isFailure)
        for (token in listOf("secret\ttoken", "secret\ntoken", "secret\u0100token")) {
            val error = runCatching { GitHubSyncConfig("owner", "repo", token).validate() }.exceptionOrNull()
            assertNotNull(error)
            assertFalse(error!!.message.orEmpty().contains(token))
        }
        assertFalse(GitHubSyncConfig("owner", "repo", "secret-token").toString().contains("secret-token"))
    }

    private fun book(progress: Long, catalog: Long) = SyncBookPayload(
        bookSyncId = "id", book = SyncBook(name = "Novel", totalChapterNum = catalog.toInt()),
        shelfUpdatedAt = 1, catalogUpdatedAt = catalog, progressUpdatedAt = progress, updatedByDeviceId = "device"
    )

    private class Server : AutoCloseable {
        val contents = linkedMapOf<String, String>()
        private val versions = hashMapOf<String, Int>()
        var conflictContent: String? = null
        var puts = 0
        var branchChecks = 0
        var lastWriteBranch = ""
        var emptyRepository = false
        var pushAllowed = true
        var missingBranch = false
        var alwaysConflict = false
        var failure: Int? = null
        var largeDirectory = false
        var headersValid = true
        var treeRequests = 0
        var contentReads = 0
        var truncatedTree = false
        val refs = mutableListOf<String>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                try { handle(exchange) } catch (e: Throwable) {
                    respond(exchange, 500, "{}")
                } finally { exchange.close() }
            }
            start()
        }

        fun client(branch: String = "", directory: String = "wanjuan") = GitHubSyncClient(
            GitHubSyncConfig("owner", "repo", "secret-token", branch, directory),
            apiBase = "http://127.0.0.1:${server.address.port}/".toHttpUrl()
        )

        private fun handle(exchange: HttpExchange) {
            headersValid = headersValid && exchange.requestHeaders.getFirst("Authorization") == "Bearer secret-token" &&
                exchange.requestHeaders.getFirst("X-GitHub-Api-Version") == "2022-11-28"
            failure?.let { respond(exchange, it, "{}"); return }
            val path = exchange.requestURI.path
            if (path == "/repos/owner/repo") {
                respond(exchange, 200, """{"default_branch":"main","size":${if (emptyRepository) 0 else 1},"permissions":{"push":$pushAllowed}}""")
                return
            }
            if (path.contains("/branches/")) {
                branchChecks++
                respond(exchange, if (missingBranch) 404 else 200, "{}")
                return
            }
            if (path.contains("/git/trees/")) {
                treeRequests++
                if (emptyRepository && contents.isEmpty()) {
                    respond(exchange, 409, "{}")
                } else {
                    respond(exchange, 200, GSON.toJson(mapOf("truncated" to truncatedTree,
                        "tree" to contents.keys.map { mapOf("path" to it, "type" to "blob", "sha" to (versions[it] ?: 0).toString()) })))
                }
                return
            }
            val key = path.substringAfter("/contents/")
            refs += exchange.requestURI.rawQuery.orEmpty()
            val sha = (versions[key] ?: 0).toString()
            when (exchange.requestMethod) {
                "GET" -> {
                    contentReads++
                    val value = contents[key]
                    if (value != null) {
                        respond(exchange, 200, GSON.toJson(mapOf("type" to "file", "encoding" to "base64", "sha" to sha,
                            "content" to Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8)))))
                    } else {
                        val names = contents.keys.filter { it.startsWith("$key/") }.map { it.removePrefix("$key/") }
                        val entries = if (largeDirectory) List(1000) { mapOf("type" to "file", "name" to "$it.json") }
                            else names.map { mapOf("type" to "file", "name" to it) }
                        respond(exchange, if (entries.isEmpty()) 404 else 200, GSON.toJson(entries))
                    }
                }
                "PUT", "DELETE" -> {
                    val body = JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject
                    if (exchange.requestMethod == "PUT") puts++
                    lastWriteBranch = body["branch"].asString
                    conflictContent?.let {
                        contents[key] = it
                        versions[key] = (versions[key] ?: 0) + 1
                        conflictContent = null
                        respond(exchange, 409, "{}")
                        return
                    }
                    if (alwaysConflict || (contents.containsKey(key) && body["sha"]?.asString != sha)) {
                        respond(exchange, 409, "{}")
                        return
                    }
                    if (exchange.requestMethod == "DELETE") contents.remove(key)
                    else contents[key] = Base64.getDecoder().decode(body["content"].asString).toString(Charsets.UTF_8)
                    versions[key] = (versions[key] ?: 0) + 1
                    respond(exchange, 200, GSON.toJson(mapOf("content" to mapOf("sha" to versions[key].toString()))))
                }
                else -> respond(exchange, 405, "{}")
            }
        }

        private fun respond(exchange: HttpExchange, code: Int, json: String) {
            val bytes = json.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        }

        override fun close() = server.stop(0)
    }
}
