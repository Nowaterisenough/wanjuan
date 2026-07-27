package io.wanjuan.app.help.glide

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import io.wanjuan.app.help.glide.progress.mangaProgressUrl

class MangaImageFallbackDownloaderTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("manga-fallback-test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun httpErrorFallsBackAndPreservesHeadersAndProgressKey() {
        val attempts = mutableListOf<Request>()
        val client = testClient { request ->
            attempts += request
            if (request.url.host == "primary.example") {
                response(request, 520, "error".toResponseBody())
            } else {
                response(request, 200, "image-data".toResponseBody())
            }
        }

        val result = newDownloader(client).download(
            MangaImageRequestChain(
                urls = listOf(
                    "https://primary.example/a.jpg",
                    "https://mirror.example/a.jpg",
                ),
                headers = mapOf("Referer" to "https://reader.example/"),
                readTimeoutMillis = 8_000L,
                progressUrl = "https://primary.example/a.jpg,{\"fallbackUrls\":[]}",
            )
        )

        assertEquals("image-data", result.file.readText())
        assertEquals(listOf("primary.example", "mirror.example"), attempts.map { it.url.host })
        assertEquals(
            listOf("https://reader.example/", "https://reader.example/"),
            attempts.map { it.header("Referer") },
        )
        assertEquals(
            listOf(
                "https://primary.example/a.jpg,{\"fallbackUrls\":[]}",
                "https://primary.example/a.jpg,{\"fallbackUrls\":[]}",
            ),
            attempts.map { it.mangaProgressUrl() },
        )
    }

    @Test
    fun readFailureAndEmptyBodyFallBackAndDeleteFailedFiles() {
        val attempts = mutableListOf<String>()
        val client = testClient { request ->
            attempts += request.url.host
            when (request.url.host) {
                "stalled.example" -> response(request, 200, stalledBody())
                "empty.example" -> response(request, 200, ByteArray(0).toResponseBody())
                else -> response(request, 200, "ok".toResponseBody())
            }
        }

        val result = newDownloader(client).download(
            MangaImageRequestChain(
                urls = listOf(
                    "https://stalled.example/a.jpg",
                    "https://empty.example/a.jpg",
                    "https://healthy.example/a.jpg",
                ),
                headers = emptyMap(),
                readTimeoutMillis = 8_000L,
                progressUrl = "https://stalled.example/a.jpg",
            )
        )

        assertEquals(listOf("stalled.example", "empty.example", "healthy.example"), attempts)
        assertEquals("ok", result.file.readText())
        assertEquals(listOf(result.file), tempDir.listFiles()?.toList())
    }

    @Test
    fun allFailuresAreReportedOnceWithSuppressedCauses() {
        val client = testClient { request ->
            val code = when (request.url.host) {
                "one.example" -> 520
                "two.example" -> 521
                else -> 522
            }
            response(request, code, "error".toResponseBody())
        }

        val error = assertThrows(IOException::class.java) {
            newDownloader(client).download(chain("one.example", "two.example", "three.example"))
        }

        assertEquals("漫画图片所有候选节点均加载失败", error.message)
        assertEquals("HTTP 522", error.cause?.message)
        assertEquals(listOf("HTTP 520", "HTTP 521"), error.suppressed.map { it.message })
        assertTrue(tempDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun cancelStopsBeforeStartingTheNextCandidate() {
        var attempts = 0
        val client = testClient { request ->
            attempts++
            response(request, 200, "unused".toResponseBody())
        }
        lateinit var downloader: MangaImageFallbackDownloader
        downloader = newDownloader(client) { downloader.cancel() }

        assertThrows(CancellationException::class.java) {
            downloader.download(chain("one.example", "two.example"))
        }

        assertEquals(0, attempts)
        assertTrue(tempDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun prepareFailureContinuesWithTheNextCandidate() {
        val client = testClient { request ->
            response(request, 200, request.url.host.toResponseBody())
        }

        val result = newDownloader(client).download(chain("one.example", "two.example")) {
            if (it.requestUrl.contains("one.example")) null else it
        }

        assertEquals("two.example", result.file.readText())
        assertEquals(listOf(result.file), tempDir.listFiles()?.toList())
    }

    @Test
    fun cancelBeforeCallPublicationPreventsExecutionAndLaterCandidates() {
        val publicationBlocked = CountDownLatch(1)
        val allowPublication = CountDownLatch(1)
        val connecting = mutableListOf<String>()
        val attempts = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val outcome = AtomicReference<Throwable?>()
        val client = testClient { request ->
            attempts += request.url.host
            response(request, 200, "unused".toResponseBody())
        }
        val downloader = newDownloader(
            client = client,
            onConnecting = { connecting += it },
            onAttemptFailed = { host, _ -> failures += host },
            beforeCallPublication = {
                publicationBlocked.countDown()
                allowPublication.await(5, TimeUnit.SECONDS)
            },
        )

        val worker = thread {
            outcome.set(runCatching {
                downloader.download(chain("one.example", "two.example"))
            }.exceptionOrNull())
        }
        assertTrue(publicationBlocked.await(5, TimeUnit.SECONDS))
        downloader.cancel()
        allowPublication.countDown()
        worker.join(5_000L)

        assertTrue(outcome.get() is CancellationException)
        assertEquals(listOf("https://one.example/a.jpg"), connecting)
        assertTrue(attempts.isEmpty())
        assertTrue(failures.isEmpty())
        assertTrue(tempDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun cancelDuringReadDeletesPartialFileAndSkipsLaterBehavior() {
        val readStarted = CountDownLatch(1)
        val allowReadFailure = CountDownLatch(1)
        val attempts = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val outcome = AtomicReference<Throwable?>()
        var prepareCalls = 0
        val client = testClient { request ->
            attempts += request.url.host
            when (request.url.host) {
                "one.example" -> response(request, 200, blockedAfterFirstByteBody(readStarted, allowReadFailure))
                else -> response(request, 200, "unexpected".toResponseBody())
            }
        }
        val downloader = newDownloader(client, onAttemptFailed = { host, _ -> failures += host })

        val worker = thread {
            outcome.set(runCatching {
                downloader.download(chain("one.example", "two.example")) {
                    prepareCalls++
                    it
                }
            }.exceptionOrNull())
        }
        assertTrue(readStarted.await(5, TimeUnit.SECONDS))
        downloader.cancel()
        allowReadFailure.countDown()
        worker.join(5_000L)

        assertTrue(outcome.get() is CancellationException)
        assertEquals(listOf("one.example"), attempts)
        assertTrue(failures.isEmpty())
        assertEquals(0, prepareCalls)
        assertTrue(tempDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun prepareReplacementDeletesDownloadedFileAndKeepsReplacement() {
        var downloadedFile: File? = null
        val replacement = File(tempDir, "prepared.img")
        val result = newDownloader(testClient { request ->
            response(request, 200, "downloaded".toResponseBody())
        }).download(chain("one.example")) { downloaded ->
            downloadedFile = downloaded.file
            replacement.writeText("prepared")
            DownloadedMangaImage(replacement, downloaded.requestUrl)
        }

        assertEquals(replacement, result.file)
        assertEquals("prepared", result.file.readText())
        assertTrue(downloadedFile?.exists() == false)
        assertEquals(listOf(replacement), tempDir.listFiles()?.toList())
    }

    @Test
    fun cancelAfterPreparationDeletesDownloadedAndPreparedFiles() {
        var downloadedFile: File? = null
        val replacement = File(tempDir, "prepared.img")
        val failures = mutableListOf<String>()
        lateinit var downloader: MangaImageFallbackDownloader
        downloader = newDownloader(
            client = testClient { request -> response(request, 200, "downloaded".toResponseBody()) },
            onAttemptFailed = { host, _ -> failures += host },
        )

        assertThrows(CancellationException::class.java) {
            downloader.download(chain("one.example")) { downloaded ->
                downloadedFile = downloaded.file
                replacement.writeText("prepared")
                downloader.cancel()
                DownloadedMangaImage(replacement, downloaded.requestUrl)
            }
        }

        assertTrue(downloadedFile?.exists() == false)
        assertTrue(replacement.exists().not())
        assertTrue(failures.isEmpty())
        assertTrue(tempDir.listFiles().isNullOrEmpty())
    }

    private fun testClient(responder: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain -> responder(chain.request()) }.build()

    private fun response(request: Request, code: Int, body: ResponseBody): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(code.toString())
            .body(body)
            .build()

    private fun newDownloader(
        client: OkHttpClient,
        onConnecting: (String) -> Unit = {},
        onAttemptFailed: (String, Throwable) -> Unit = { _, _ -> },
        beforeCallPublication: () -> Unit = {},
    ): MangaImageFallbackDownloader = MangaImageFallbackDownloader(
        baseClient = client,
        createTempFile = {
            File.createTempFile("candidate_", ".img", tempDir)
        },
        onConnecting = onConnecting,
        onAttemptFailed = onAttemptFailed,
        beforeCallPublication = beforeCallPublication,
    )

    private fun chain(vararg hosts: String) = MangaImageRequestChain(
        urls = hosts.map { "https://$it/a.jpg" },
        headers = emptyMap(),
        readTimeoutMillis = 8_000L,
        progressUrl = "https://${hosts.first()}/a.jpg",
    )

    private fun stalledBody(): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = 2L

        override fun source(): BufferedSource {
            val data = Buffer().writeUtf8("ab")
            var firstRead = true
            return object : ForwardingSource(data) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (!firstRead) throw SocketTimeoutException("stalled")
                    firstRead = false
                    return super.read(sink, 1L)
                }
            }.buffer()
        }
    }

    private fun blockedAfterFirstByteBody(
        readStarted: CountDownLatch,
        allowReadFailure: CountDownLatch,
    ): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = 2L

        override fun source(): BufferedSource {
            val data = Buffer().writeUtf8("ab")
            var firstRead = true
            return object : ForwardingSource(data) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (firstRead) {
                        firstRead = false
                        val count = super.read(sink, 1L)
                        readStarted.countDown()
                        return count
                    }
                    allowReadFailure.await(5, TimeUnit.SECONDS)
                    throw IOException("cancelled read")
                }
            }.buffer()
        }
    }
}
