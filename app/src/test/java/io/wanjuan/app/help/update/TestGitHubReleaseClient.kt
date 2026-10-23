package io.wanjuan.app.help.update

import io.wanjuan.app.exception.NoStackTraceException
import kotlinx.coroutines.*
import okhttp3.Dns
import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TestGitHubReleaseClient {
    @Test fun updateClientKeepsPlatformNetworkingAndHasNoSourceInterceptors() {
        val client = GitHubReleaseClient.newClient()
        assertSame(Dns.SYSTEM, client.dns)
        assertNull(client.proxy)
        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.callTimeoutMillis)
    }

    @Test fun platformProxySelectionIsNotBypassed() = runBlocking {
        Server(listOf(Reply(200, releaseJson))).use { server ->
            val original = ProxySelector.getDefault()
            var selected = false
            try {
                ProxySelector.setDefault(object : ProxySelector() {
                    override fun select(uri: URI): List<Proxy> {
                        selected = true
                        return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", server.port)))
                    }
                    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
                })
                val release = GitHubReleaseClient("http://update-fixture.invalid/releases/latest").fetchLatest()
                assertEquals("notes", release.body)
                server.await()
                assertTrue(selected)
                assertTrue(server.requests.single().first().startsWith("GET http://update-fixture.invalid/"))
                assertTrue(server.requests.single().any { it.startsWith("User-Agent: Wanjuan/") })
                assertFalse(server.requests.single().any { it.startsWith("Authorization:", true) })
            } finally { ProxySelector.setDefault(original) }
        }
    }

    @Test fun transientGatewayFailureRetriesOnce() = runBlocking {
        Server(listOf(Reply(503, "unavailable"), Reply(200, releaseJson))).use { server ->
            assertEquals("notes", GitHubReleaseClient(server.url).fetchLatest().body)
            server.await()
            assertEquals(2, server.requests.size)
        }
    }

    @Test fun rateLimitIsExplainedAndNotRetried() = runBlocking {
        Server(listOf(Reply(403, """{"message":"API rate limit exceeded"}""",
            "X-RateLimit-Remaining: 0\r\n"))).use { server ->
            val error = assertThrows(NoStackTraceException::class.java) {
                runBlocking { GitHubReleaseClient(server.url).fetchLatest() }
            }
            assertTrue(error.message!!.contains("请求受限"))
            server.await()
            assertEquals(1, server.requests.size)
        }
    }

    @Test fun permissionErrorsAreNotMisreportedAsRateLimits() {
        assertTrue(GitHubReleaseClient.httpError(403, "{}", Headers.Builder().build()).contains("拒绝访问"))
        assertTrue(GitHubReleaseClient.httpError(404, "{}", Headers.Builder().build()).contains("未找到"))
        assertTrue(GitHubReleaseClient.httpError(429, "{}", Headers.Builder().build()).contains("请求受限"))
    }

    @Test fun malformedResponseDoesNotMeanUpToDate() = runBlocking {
        Server(listOf(Reply(200, "<html>proxy login</html>"))).use { server ->
            val error = assertThrows(NoStackTraceException::class.java) {
                runBlocking { GitHubReleaseClient(server.url).fetchLatest() }
            }
            assertTrue(error.message!!.contains("格式异常"))
        }
    }

    @Test fun cancellationAfterHeadersClosesBodyWithoutRetry() = runBlocking {
        Server(listOf(Reply(200, "{", holdOpen = true))).use { server ->
            val job = launch { GitHubReleaseClient(server.url).fetchLatest() }
            withTimeout(5_000) { while (server.requests.isEmpty()) delay(10) }
            withTimeout(2_000) { job.cancelAndJoin() }
            server.await()
            assertTrue(job.isCancelled)
            assertEquals(1, server.requests.size)
        }
    }

    @Test fun totalDeadlineCancelsStalledBody() = runBlocking {
        Server(listOf(Reply(200, "{", holdOpen = true))).use { server ->
            val error = assertThrows(NoStackTraceException::class.java) {
                runBlocking { GitHubReleaseClient(server.url, totalTimeoutMillis = 500).fetchLatest() }
            }
            assertTrue(error.message!!.contains("超时"))
            server.await()
        }
    }

    private data class Reply(val code: Int, val body: String, val headers: String = "", val holdOpen: Boolean = false)

    private class Server(private val replies: List<Reply>) : AutoCloseable {
        private val server = ServerSocket(0)
        val port = server.localPort
        val url = "http://127.0.0.1:$port/releases/latest"
        val requests = CopyOnWriteArrayList<List<String>>()
        private val executor = Executors.newSingleThreadExecutor()
        @Volatile private var active: Socket? = null
        private val future = executor.submit {
            replies.forEach { reply ->
                server.accept().use { socket ->
                    active = socket
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = mutableListOf<String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        headers += line
                    }
                    requests += headers.toList()
                    val body = reply.body.toByteArray()
                    val framing = if (reply.holdOpen) "" else "Content-Length: ${body.size}\r\n"
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 ${reply.code} Fixture\r\nContent-Type: application/json\r\n" +
                            "Connection: close\r\n" + reply.headers + framing + "\r\n").toByteArray())
                        write(body)
                        flush()
                    }
                    if (reply.holdOpen) reader.read()
                }
            }
        }
        fun await() { future.get(6, TimeUnit.SECONDS) }
        override fun close() {
            active?.close()
            server.close()
            executor.shutdownNow()
        }
    }

    companion object {
        private const val releaseJson = """{"body":"notes","prerelease":false,"assets":[]}"""
    }
}
