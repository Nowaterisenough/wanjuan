package io.wanjuan.app.lib.cronet

import androidx.annotation.Keep
import okhttp3.RequestBody
import okio.Buffer
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer

@Keep
class BodyUploadProvider(private val body: RequestBody) : UploadDataProvider(), AutoCloseable {

    private val buffer = Buffer()

    @Volatile
    private var filled: Boolean = false

    init {
        fillBuffer()
    }

    private fun fillBuffer() {
        buffer.clear()
        body.writeTo(buffer)
        filled = true
    }

    @Throws(IOException::class)
    override fun getLength(): Long {
        return body.contentLength()
    }

    @Throws(IOException::class)
    override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
        if (!filled) {
            fillBuffer()
        }
        check(byteBuffer.hasRemaining()) { "Cronet passed a buffer with no bytes remaining" }
        val count = buffer.read(byteBuffer)
        if (count < 0 && length >= 0) throw EOFException("Request body ended before Cronet finished reading")
        uploadDataSink.onReadSucceeded(count < 0 && length < 0)
    }

    @Throws(IOException::class)
    override fun rewind(uploadDataSink: UploadDataSink) {
        check(!body.isOneShot()) { "Okhttp RequestBody is oneShot" }
        filled = false
        fillBuffer()
        uploadDataSink.onRewindSucceeded()
    }

    @Throws(IOException::class)
    override fun close() {
        buffer.clear()
        buffer.close()
        super.close()
    }
}
