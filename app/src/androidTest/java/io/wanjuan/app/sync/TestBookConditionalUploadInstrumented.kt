package io.wanjuan.app.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.elonen.NanoHTTPD
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.sync.remote.mergeBook
import io.wanjuan.app.utils.GSON
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestBookConditionalUploadInstrumented {
    private val server = VersionedServer()
    private lateinit var client: WebDavSyncClient

    @Before
    fun setUp() {
        server.start()
        client = WebDavSyncClient({ "http://127.0.0.1:${server.listeningPort}/" }, { Authorization("test", "test") })
    }

    @After
    fun tearDown() = server.stop()

    @Test
    fun retriesConflictAndPreservesProgressWrittenAfterFirstGet() = runBlocking {
        val local = payload(100, 79, 400, 160)
        server.json = GSON.toJson(payload(100, 79, 100, 120))
        server.replaceBeforeFirstPut = GSON.toJson(payload(300, 99, 100, 120))

        val merged = client.mergeBook(local)

        assertEquals(2, server.puts)
        assertEquals(99, merged.book.durChapterIndex)
        assertEquals(300L, merged.progressUpdatedAt)
        assertEquals(160, merged.book.totalChapterNum)
        assertEquals(listOf("\"1\"", "\"2\""), server.conditions)
    }

    @Test
    fun invalidRemoteJsonDoesNotGetOverwritten() = runBlocking {
        server.json = "invalid json"

        val result = runCatching { client.mergeBook(payload(100, 79, 100, 120)) }

        assertTrue(result.isFailure)
        assertEquals(0, server.puts)
        assertEquals("invalid json", server.json)
    }

    private fun payload(progress: Long, chapter: Int, catalog: Long, count: Int) = SyncBookPayload(
        "book", SyncBook(bookUrl = "book", durChapterIndex = chapter, syncTime = progress, totalChapterNum = count),
        100, catalog, progress, "device"
    )

    private class VersionedServer : NanoHTTPD("127.0.0.1", 0) {
        var json = ""
        var replaceBeforeFirstPut: String? = null
        var puts = 0
        var revision = 1
        val conditions = arrayListOf<String?>()

        override fun serve(session: IHTTPSession): Response = when (session.method) {
            Method.GET -> newFixedLengthResponse(Response.Status.OK, "application/json", json).apply {
                addHeader("ETag", "\"$revision\"")
            }
            Method.PUT -> {
                puts++
                val bytes = ByteArray(session.headers.getValue("content-length").toInt())
                java.io.DataInputStream(session.inputStream).readFully(bytes)
                replaceBeforeFirstPut?.let {
                    json = it
                    revision++
                    replaceBeforeFirstPut = null
                }
                conditions += session.headers["if-match"]
                if (session.headers["if-match"] != "\"$revision\"") {
                    newFixedLengthResponse(Response.Status.PRECONDITION_FAILED, MIME_PLAINTEXT, "Changed")
                } else {
                    json = bytes.toString(Charsets.UTF_8)
                    revision++
                    newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")
                }
            }
            else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
        }
    }
}
