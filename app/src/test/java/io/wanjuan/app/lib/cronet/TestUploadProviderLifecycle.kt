package io.wanjuan.app.lib.cronet

import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import org.junit.Assert.*
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TestUploadProviderLifecycle {
    @Test(timeout = 5_000)
    fun largeUploadStreamsAndRewindsWithoutKeepingOldWriter() {
        val executor = Executors.newCachedThreadPool()
        val bytes = ByteArray(BUFFER_SIZE * 3 + 11) { (it % 251).toByte() }
        try {
            LargeBodyUploadProvider(bytes.toRequestBody(), executor).use { provider ->
                repeat(2) {
                    assertArrayEquals(bytes, readExactly(provider, bytes.size))
                    val sink = ResultSink()
                    provider.rewind(sink)
                    assertTrue(sink.rewound)
                }
            }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 5_000)
    fun truncatedAndFailedLargeBodiesTerminateInsteadOfWaitingOrSpinning() {
        val executor = Executors.newCachedThreadPool()
        try {
            val truncated = body(length = 100) { it.writeUtf8("short") }
            LargeBodyUploadProvider(truncated, executor).use { provider ->
                provider.read(ResultSink(), ByteBuffer.allocate(100))
                assertThrows(EOFException::class.java) { provider.read(ResultSink(), ByteBuffer.allocate(100)) }
            }
            val failed = body(length = 100) { throw IOException("fixture writer failure") }
            LargeBodyUploadProvider(failed, executor).use { provider ->
                val error = assertThrows(IOException::class.java) {
                    provider.read(ResultSink(), ByteBuffer.allocate(100))
                }
                assertEquals("fixture writer failure", error.message)
            }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 5_000)
    fun closeUnblocksBackpressuredLargeWriter() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val request = body(length = 100_000_000) { sink ->
                val bytes = ByteArray(BUFFER_SIZE)
                repeat(2_000) { sink.write(bytes) }
            }
            val provider = LargeBodyUploadProvider(request, executor)
            provider.read(ResultSink(), ByteBuffer.allocate(1))
            provider.close()
            provider.close()
            executor.shutdown()
            assertTrue("Cancelled writer must terminate", executor.awaitTermination(2, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test(timeout = 5_000)
    fun unknownLengthSignalsFinalChunkAndOneShotCannotRewind() {
        val executor = Executors.newCachedThreadPool()
        try {
            val request = body(length = -1, oneShot = true) { it.writeUtf8("chunk") }
            listOf(BodyUploadProvider(request), LargeBodyUploadProvider(request, executor)).forEach { provider ->
                provider.use {
                    val sink = ResultSink()
                    provider.read(sink, ByteBuffer.allocate(100))
                    assertFalse(sink.finalChunk)
                    provider.read(sink, ByteBuffer.allocate(100))
                    assertTrue(sink.finalChunk)
                    assertThrows(IllegalStateException::class.java) { provider.rewind(sink) }
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun smallBodyRewindsAndPropagatesWriteFailure() {
        BodyUploadProvider("small".toRequestBody()).use { provider ->
            assertEquals("small", String(readExactly(provider, 5)))
            val sink = ResultSink()
            provider.rewind(sink)
            assertTrue(sink.rewound)
            assertEquals("small", String(readExactly(provider, 5)))
        }
        assertThrows(IOException::class.java) {
            BodyUploadProvider(body(100) { throw IOException("failed") })
        }
    }

    private fun readExactly(provider: UploadDataProvider, size: Int): ByteArray {
        val result = ByteBuffer.allocate(size)
        while (result.hasRemaining()) provider.read(ResultSink(), result)
        return result.array()
    }

    private fun body(length: Long, oneShot: Boolean = false, writer: (BufferedSink) -> Unit) =
        object : RequestBody() {
            override fun contentType() = null
            override fun contentLength() = length
            override fun isOneShot() = oneShot
            override fun writeTo(sink: BufferedSink) = writer(sink)
        }

    private class ResultSink : UploadDataSink() {
        var finalChunk = false
        var rewound = false
        override fun onReadSucceeded(finalChunk: Boolean) { this.finalChunk = finalChunk }
        override fun onReadError(error: Exception) { throw AssertionError(error) }
        override fun onRewindSucceeded() { rewound = true }
        override fun onRewindError(error: Exception) { throw AssertionError(error) }
    }
}
