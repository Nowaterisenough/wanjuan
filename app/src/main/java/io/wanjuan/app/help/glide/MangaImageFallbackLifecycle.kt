package io.wanjuan.app.help.glide

import java.io.File
import java.io.InputStream

internal class MangaImageFallbackLifecycle(
    private val onConnecting: () -> Unit,
    private val onDataReady: (DownloadedMangaImage) -> InputStream?,
    private val onLoadFailed: (Throwable) -> Unit,
) {
    private enum class State { ACTIVE, CANCELLING, CANCELLED }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()

    private var state = State.ACTIVE
    private var cleanupOwner: Thread? = null
    private var terminalDelivered = false
    private var cancelDownload: (() -> Unit)? = null
    private var ownedFile: File? = null
    private var ownedStream: InputStream? = null

    fun start(cancelDownload: () -> Unit): Boolean = synchronized(lock) {
        if (state != State.ACTIVE || terminalDelivered) {
            false
        } else {
            this.cancelDownload = cancelDownload
            true
        }
    }

    fun notifyConnecting() = synchronized(lock) {
        if (state == State.ACTIVE && !terminalDelivered) {
            onConnecting()
        }
    }

    fun deliverSuccess(downloaded: DownloadedMangaImage) = synchronized(lock) {
        if (state != State.ACTIVE || terminalDelivered) {
            downloaded.file.delete()
            return@synchronized
        }
        terminalDelivered = true
        ownedFile = downloaded.file
        val publishedStream = onDataReady(downloaded)
        if (state != State.ACTIVE) {
            publishedStream?.close()
            ownedFile = null
            downloaded.file.delete()
            return@synchronized
        }
        ownedStream = publishedStream
    }

    fun deliverFailure(error: Throwable) = synchronized(lock) {
        if (state == State.ACTIVE && !terminalDelivered) {
            terminalDelivered = true
            onLoadFailed(error)
        }
    }

    fun cancel() {
        val file: File?
        val stream: InputStream?
        val cancel: (() -> Unit)?
        synchronized(lock) {
            when (state) {
                State.CANCELLED -> return
                State.CANCELLING -> {
                    if (cleanupOwner === Thread.currentThread()) return
                    var interrupted = false
                    while (state == State.CANCELLING) {
                        try {
                            lock.wait()
                        } catch (_: InterruptedException) {
                            interrupted = true
                        }
                    }
                    if (interrupted) Thread.currentThread().interrupt()
                    return
                }
                State.ACTIVE -> {
                    state = State.CANCELLING
                    cleanupOwner = Thread.currentThread()
                }
            }
            file = ownedFile
            ownedFile = null
            stream = ownedStream
            ownedStream = null
            cancel = cancelDownload
            cancelDownload = null
        }
        try {
            runCatching { cancel?.invoke() }
            runCatching { stream?.close() }
            file?.delete()
        } finally {
            synchronized(lock) {
                cleanupOwner = null
                state = State.CANCELLED
                lock.notifyAll()
            }
        }
    }
}
