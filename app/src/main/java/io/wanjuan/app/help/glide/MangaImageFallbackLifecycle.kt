package io.wanjuan.app.help.glide

import java.io.File
import java.io.InputStream

internal class MangaImageFallbackLifecycle(
    private val onConnecting: () -> Unit,
    private val onDataReady: (DownloadedMangaImage) -> InputStream?,
    private val onLoadFailed: (Throwable) -> Unit,
) {
    private val lock = Any()

    private var active = true
    private var terminalDelivered = false
    private var cancelDownload: (() -> Unit)? = null
    private var ownedFile: File? = null
    private var ownedStream: InputStream? = null

    fun start(cancelDownload: () -> Unit): Boolean = synchronized(lock) {
        if (!active || terminalDelivered) {
            false
        } else {
            this.cancelDownload = cancelDownload
            true
        }
    }

    fun notifyConnecting() = synchronized(lock) {
        if (active && !terminalDelivered) {
            onConnecting()
        }
    }

    fun deliverSuccess(downloaded: DownloadedMangaImage) = synchronized(lock) {
        if (!active || terminalDelivered) {
            downloaded.file.delete()
            return@synchronized
        }
        terminalDelivered = true
        ownedFile = downloaded.file
        val publishedStream = onDataReady(downloaded)
        if (!active) {
            publishedStream?.close()
            ownedFile = null
            downloaded.file.delete()
            return@synchronized
        }
        ownedStream = publishedStream
    }

    fun deliverFailure(error: Throwable) = synchronized(lock) {
        if (active && !terminalDelivered) {
            terminalDelivered = true
            onLoadFailed(error)
        }
    }

    fun cancel() {
        val file: File?
        val stream: InputStream?
        val cancel: (() -> Unit)?
        synchronized(lock) {
            if (!active) return
            active = false
            file = ownedFile
            ownedFile = null
            stream = ownedStream
            ownedStream = null
            cancel = cancelDownload
            cancelDownload = null
        }
        runCatching { cancel?.invoke() }
        runCatching { stream?.close() }
        file?.delete()
    }
}
