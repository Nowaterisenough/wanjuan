package io.wanjuan.app.lib.cronet

import androidx.annotation.Keep
import okhttp3.RequestBody
import okio.Pipe
import okio.buffer
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** Streams a request without retaining the whole body or a blocked writer after cancellation. */
@Keep
class LargeBodyUploadProvider(
    private val body: RequestBody,
    private val executorService: ExecutorService
) : UploadDataProvider(), AutoCloseable {
    private class Stream {
        val pipe = Pipe(BUFFER_SIZE.toLong())
        val source = pipe.source.buffer()
        @Volatile var failure: IOException? = null
        var writer: Future<*>? = null

        fun cancel() {
            pipe.cancel()
            writer?.cancel(true)
        }
    }

    private var stream: Stream? = null
    private var closed = false
    private var bytesRead = 0L

    override fun getLength(): Long = body.contentLength()

    @Synchronized
    private fun currentStream(): Stream {
        check(!closed) { "Upload provider is closed" }
        stream?.let { return it }
        val next = Stream()
        stream = next
        next.writer = executorService.submit {
            try {
                next.pipe.sink.buffer().use { sink ->
                    try {
                        body.writeTo(sink)
                    } catch (error: Exception) {
                        next.failure = error as? IOException ?: IOException("Request body write failed", error)
                        throw error
                    }
                }
            } catch (error: Exception) {
                next.failure = error as? IOException ?: IOException("Request body write failed", error)
                next.pipe.cancel() // Wake the reader as well as any blocked writer.
            }
        }
        return next
    }

    override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
        check(byteBuffer.hasRemaining()) { "Cronet passed a buffer with no bytes remaining" }
        val current = currentStream()
        val count = try {
            current.source.read(byteBuffer)
        } catch (error: IOException) {
            throw current.failure ?: error
        }
        current.failure?.let { throw it }
        val length = length
        if (count < 0) {
            if (length >= 0) throw EOFException("Request body ended after $bytesRead of $length bytes")
            uploadDataSink.onReadSucceeded(true)
            return
        }
        bytesRead += count
        if (length >= 0 && bytesRead > length) throw IOException("Request body exceeds content length")
        uploadDataSink.onReadSucceeded(false)
    }

    @Synchronized
    override fun rewind(uploadDataSink: UploadDataSink) {
        check(!closed) { "Upload provider is closed" }
        check(!body.isOneShot()) { "OkHttp RequestBody is oneShot" }
        stream?.cancel()
        stream = null
        bytesRead = 0
        uploadDataSink.onRewindSucceeded()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        stream?.cancel()
        stream = null
        super.close()
    }
}
