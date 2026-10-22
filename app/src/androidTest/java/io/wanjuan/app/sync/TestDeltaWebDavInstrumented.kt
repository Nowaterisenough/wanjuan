package io.wanjuan.app.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.elonen.NanoHTTPD
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.sync.delta.DeltaRemote
import io.wanjuan.app.sync.remote.WebDavSyncClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class TestDeltaWebDavInstrumented {
    @Test fun headCreationAndUpdatesRejectStaleEtags() = runBlocking {
        Server().use { server ->
            val a = server.client(); val b = server.client()
            val first = a.readForUpdate(DeltaRemote.HEAD)
            assertTrue(a.compareAndSetJson(DeltaRemote.HEAD, first, "one"))
            assertFalse(b.compareAndSetJson(DeltaRemote.HEAD, first, "two"))
            val old = a.readForUpdate(DeltaRemote.HEAD)
            assertTrue(b.compareAndSetJson(DeltaRemote.HEAD, old, "two"))
            assertFalse(a.compareAndSetJson(DeltaRemote.HEAD, old, "three"))
            assertEquals("two", a.downloadJson(DeltaRemote.HEAD))
        }
    }

    @Test fun missingOrWeakEtagCannotOverwritePublishedHead() = runBlocking {
        for (etag in listOf<String?>(null, "W/\"weak\"")) Server().use { server ->
            val client = server.client()
            assertTrue(client.compareAndSetJson(DeltaRemote.HEAD, client.readForUpdate(DeltaRemote.HEAD), "one"))
            server.etagOverride = etag
            server.overrideEtag = true
            val old = client.readForUpdate(DeltaRemote.HEAD)
            assertTrue(runCatching { client.compareAndSetJson(DeltaRemote.HEAD, old, "two") }.isFailure)
            assertEquals("one", client.downloadJson(DeltaRemote.HEAD))
        }
    }

    private class Server : NanoHTTPD("127.0.0.1", 0), AutoCloseable {
        private val objects = ConcurrentHashMap<String, String>()
        private val revisions = ConcurrentHashMap<String, Int>()
        var overrideEtag = false
        var etagOverride: String? = null
        init { start() }
        fun client() = WebDavSyncClient({ "http://127.0.0.1:$listeningPort/" }, { Authorization("test", "test") })
        override fun close() = stop()

        @Synchronized override fun serve(session: IHTTPSession): Response {
            val path = session.uri
            val old = objects[path]
            val etag = "\"${revisions[path] ?: 0}\""
            val body = ByteArray(session.headers["content-length"]?.toIntOrNull() ?: 0)
            java.io.DataInputStream(session.inputStream).readFully(body)
            return when (session.method) {
                Method.PROPFIND -> newFixedLengthResponse(Response.Status.MULTI_STATUS, "application/xml", "<d:multistatus xmlns:d=\"DAV:\"/>")
                Method.MKCOL -> newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "")
                Method.GET -> if (old == null) newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
                    else newFixedLengthResponse(Response.Status.OK, "application/json", old).apply {
                        (if (overrideEtag) etagOverride else etag)?.let { addHeader("ETag", it) }
                    }
                Method.PUT -> {
                    if ((session.headers["if-none-match"] == "*" && old != null) ||
                        (session.headers["if-match"] != null && session.headers["if-match"] != etag)) {
                        newFixedLengthResponse(Response.Status.PRECONDITION_FAILED, MIME_PLAINTEXT, "")
                    } else {
                        objects[path] = body.toString(Charsets.UTF_8)
                        revisions[path] = (revisions[path] ?: 0) + 1
                        newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "").apply {
                            addHeader("ETag", "\"${revisions[path]}\"")
                        }
                    }
                }
                else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
            }
        }
    }
}
