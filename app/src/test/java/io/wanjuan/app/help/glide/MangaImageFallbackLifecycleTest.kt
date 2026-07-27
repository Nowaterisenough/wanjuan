package io.wanjuan.app.help.glide

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MangaImageFallbackLifecycleTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("manga-fallback-lifecycle").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun immediateFailureAfterStartDeliversItsTerminalCallback() {
        val failures = mutableListOf<Throwable>()
        val lifecycle = MangaImageFallbackLifecycle(
            onConnecting = {},
            onDataReady = { null },
            onLoadFailed = { failures += it },
        )

        assertTrue(lifecycle.start { })
        lifecycle.deliverFailure(IOException("fast failure"))

        assertEquals(listOf("fast failure"), failures.map { it.message })
    }

    @Test
    fun cancelBeforeTerminalSuppressesLateConnectingDataAndFailure() {
        var connecting = 0
        var data = 0
        var failures = 0
        val returnedFile = newFile("late.img")
        val lifecycle = MangaImageFallbackLifecycle(
            onConnecting = { connecting++ },
            onDataReady = { data++; null },
            onLoadFailed = { failures++ },
        )

        assertTrue(lifecycle.start { })
        lifecycle.cancel()
        lifecycle.notifyConnecting()
        lifecycle.deliverSuccess(DownloadedMangaImage(returnedFile, "https://primary.example/a.jpg"))
        lifecycle.deliverFailure(IOException("late failure"))

        assertEquals(0, connecting)
        assertEquals(0, data)
        assertEquals(0, failures)
        assertFalse(returnedFile.exists())
    }

    @Test
    fun cancelRacingDataDeliveryDeletesOwnedFileBeforeReturning() {
        val dataEntered = CountDownLatch(1)
        val releaseData = CountDownLatch(1)
        val cancelStarted = CountDownLatch(1)
        val cancelReturned = CountDownLatch(1)
        var data = 0
        val returnedFile = newFile("race.img")
        val publishedStream = CloseTrackingInputStream()
        val lifecycle = MangaImageFallbackLifecycle(
            onConnecting = {},
            onDataReady = {
                dataEntered.countDown()
                releaseData.await(5, TimeUnit.SECONDS)
                data++
                publishedStream
            },
            onLoadFailed = {},
        )

        assertTrue(lifecycle.start { })
        val delivery = thread {
            lifecycle.deliverSuccess(
                DownloadedMangaImage(returnedFile, "https://primary.example/a.jpg")
            )
        }
        assertTrue(dataEntered.await(5, TimeUnit.SECONDS))
        val cancellation = thread {
            cancelStarted.countDown()
            lifecycle.cancel()
            cancelReturned.countDown()
        }

        assertTrue(cancelStarted.await(5, TimeUnit.SECONDS))
        assertFalse(cancelReturned.await(150, TimeUnit.MILLISECONDS))
        releaseData.countDown()
        delivery.join(5_000L)
        cancellation.join(5_000L)

        assertEquals(1, data)
        assertEquals(0L, cancelReturned.count)
        assertTrue(publishedStream.closed)
        assertFalse(returnedFile.exists())
    }

    @Test
    fun cleanupTriggeredInsideSuccessClosesTheJustPublishedStream() {
        val returnedFile = newFile("reentrant-cleanup.img")
        val publishedStream = CloseTrackingInputStream()
        lateinit var lifecycle: MangaImageFallbackLifecycle
        lifecycle = MangaImageFallbackLifecycle(
            onConnecting = {},
            onDataReady = {
                lifecycle.cancel()
                publishedStream
            },
            onLoadFailed = {},
        )

        assertTrue(lifecycle.start { })
        lifecycle.deliverSuccess(
            DownloadedMangaImage(returnedFile, "https://primary.example/a.jpg")
        )

        assertTrue(publishedStream.closed)
        assertFalse(returnedFile.exists())
    }

    @Test
    fun cancelDuringDownloaderToFetcherHandoffDeletesReturnedFile() {
        val returnedFile = newFile("handoff.img")
        val lifecycle = MangaImageFallbackLifecycle(
            onConnecting = {},
            onDataReady = { null },
            onLoadFailed = {},
        )

        assertTrue(lifecycle.start { })
        lifecycle.cancel()
        lifecycle.deliverSuccess(DownloadedMangaImage(returnedFile, "https://primary.example/a.jpg"))

        assertFalse(returnedFile.exists())
    }

    @Test
    fun cancelAfterSuccessClosesPublishedStreamAndDeletesFileBeforeReturning() {
        val returnedFile = newFile("published.img")
        val publishedStream = CloseTrackingInputStream()
        val lifecycle = MangaImageFallbackLifecycle(
            onConnecting = {},
            onDataReady = { publishedStream },
            onLoadFailed = {},
        )

        assertTrue(lifecycle.start { })
        lifecycle.deliverSuccess(
            DownloadedMangaImage(returnedFile, "https://primary.example/a.jpg")
        )
        lifecycle.cancel()

        assertTrue(publishedStream.closed)
        assertFalse(returnedFile.exists())
    }

    private fun newFile(name: String): File = File(tempDir, name).apply {
        writeText("image")
    }

    private class CloseTrackingInputStream : ByteArrayInputStream(byteArrayOf(1)) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }
}
