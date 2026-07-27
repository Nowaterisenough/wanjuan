package io.wanjuan.app.help.glide

import io.wanjuan.app.help.glide.progress.MangaProgressKey
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

internal data class MangaImageRequestChain(
    val urls: List<String>,
    val headers: Map<String, String>,
    val readTimeoutMillis: Long,
    val progressUrl: String,
)

internal data class DownloadedMangaImage(
    val file: File,
    val requestUrl: String,
)

internal interface MangaImagePreparationFiles {
    fun read(file: File): ByteArray
    fun write(bytes: ByteArray): File
}

internal class MangaImageFallbackDownloader(
    private val baseClient: OkHttpClient,
    private val createTempFile: () -> File,
    private val onConnecting: (String) -> Unit = {},
    private val onAttemptFailed: (String, Throwable) -> Unit = { _, _ -> },
    private val beforeCallPublication: () -> Unit = {},
    private val openInput: (File) -> InputStream = File::inputStream,
) {
    private val callLock = Any()

    @Volatile
    private var cancelled = false

    @Volatile
    private var call: Call? = null

    private val ownedFiles = linkedSetOf<File>()
    private val ownedCloseables = linkedSetOf<Closeable>()

    fun download(
        chain: MangaImageRequestChain,
        prepare: (DownloadedMangaImage, MangaImagePreparationFiles) -> DownloadedMangaImage? =
            { downloaded, _ -> downloaded },
    ): DownloadedMangaImage {
        val failures = mutableListOf<Throwable>()
        val client = baseClient.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(chain.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()

        for (requestUrl in chain.urls.distinct()) {
            checkNotCancelled()
            onConnecting(chain.progressUrl)
            checkNotCancelled()
            var downloadedFile: File? = null
            var prepared: DownloadedMangaImage? = null
            val preparedFiles = mutableListOf<File>()
            var activeCall: Call? = null
            try {
                val request = Request.Builder()
                    .url(requestUrl)
                    .apply {
                        chain.headers.forEach { (name, value) -> header(name, value) }
                    }
                    .tag(MangaProgressKey::class.java, MangaProgressKey(chain.progressUrl))
                    .build()
                val newCall = client.newCall(request)
                activeCall = newCall
                beforeCallPublication()
                publishCall(newCall)
                checkNotCancelled()
                newCall.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val target = createOwnedTempFile()
                    downloadedFile = target
                    withOwnedCloseable({ response.body.byteStream() }) { input ->
                        withOwnedCloseable({ target.outputStream().buffered() }) { output ->
                            input.copyTo(output)
                        }
                    }
                    if (target.length() == 0L) throw IOException("图片响应为空")
                }
                checkNotCancelled()
                val candidate = DownloadedMangaImage(requireNotNull(downloadedFile), requestUrl)
                val preparationFiles = object : MangaImagePreparationFiles {
                    override fun read(file: File): ByteArray = readOwnedFile(file)

                    override fun write(bytes: ByteArray): File =
                        writeOwnedTempFile(bytes).also(preparedFiles::add)
                }
                val preparedCandidate = prepare(candidate, preparationFiles)
                    ?: throw IOException("图片处理失败")
                prepared = preparedCandidate
                ownFile(preparedCandidate.file)
                preparedFiles.filter { it != preparedCandidate.file }.forEach(::discardFile)
                if (preparedCandidate.file != downloadedFile) discardFile(downloadedFile)
                if (!finishCall(newCall)) throw CancellationException("漫画图片加载已取消")
                return preparedCandidate
            } catch (error: Exception) {
                discardFile(downloadedFile)
                prepared?.file?.takeIf { it != downloadedFile }?.let(::discardFile)
                preparedFiles.forEach(::discardFile)
                if (error is CancellationException) throw error
                if (cancelled) throw CancellationException("漫画图片加载已取消")
                failures += error
                val host = requestUrl.toHttpUrlOrNull()?.host.orEmpty()
                onAttemptFailed(host, error)
            } finally {
                clearCall(activeCall)
            }
        }

        val finalError = IOException("漫画图片所有候选节点均加载失败", failures.lastOrNull())
        failures.dropLast(1).forEach(finalError::addSuppressed)
        throw finalError
    }

    fun cancel() {
        val resources = synchronized(callLock) {
            cancelled = true
            val files = ownedFiles.toList()
            ownedFiles.clear()
            val closeables = ownedCloseables.toList()
            ownedCloseables.clear()
            Triple(call, files, closeables)
        }
        resources.first?.cancel()
        resources.third.forEach { runCatching { it.close() } }
        resources.second.forEach(File::delete)
    }

    private fun checkNotCancelled() {
        if (cancelled) throw CancellationException("漫画图片加载已取消")
    }

    private fun createOwnedTempFile(): File = synchronized(callLock) {
        checkNotCancelled()
        createTempFile().also(ownedFiles::add)
    }

    private fun writeOwnedTempFile(bytes: ByteArray): File {
        val file = createOwnedTempFile()
        return try {
            withOwnedCloseable({ file.outputStream().buffered() }) { output ->
                output.write(bytes)
            }
            file
        } catch (error: Throwable) {
            discardFile(file)
            throw error
        }
    }

    private fun readOwnedFile(file: File): ByteArray =
        withOwnedCloseable({ openInput(file) }, InputStream::readBytes)

    private fun <T : Closeable, R> withOwnedCloseable(
        create: () -> T,
        block: (T) -> R,
    ): R {
        val closeable = synchronized(callLock) {
            checkNotCancelled()
            create().also(ownedCloseables::add)
        }
        return try {
            closeable.use(block)
        } finally {
            synchronized(callLock) {
                ownedCloseables.remove(closeable)
            }
        }
    }

    private fun ownFile(file: File) {
        val shouldDelete = synchronized(callLock) {
            if (cancelled) true else {
                ownedFiles += file
                false
            }
        }
        if (shouldDelete) {
            file.delete()
            throw CancellationException("漫画图片加载已取消")
        }
    }

    private fun discardFile(file: File?) {
        file ?: return
        synchronized(callLock) {
            ownedFiles.remove(file)
        }
        file.delete()
    }

    private fun publishCall(activeCall: Call) {
        val shouldCancel = synchronized(callLock) {
            if (cancelled) {
                true
            } else {
                call = activeCall
                false
            }
        }
        if (shouldCancel) {
            activeCall.cancel()
            throw CancellationException("漫画图片加载已取消")
        }
    }

    private fun finishCall(activeCall: Call): Boolean = synchronized(callLock) {
        if (cancelled) {
            false
        } else {
            if (call === activeCall) call = null
            true
        }
    }

    private fun clearCall(activeCall: Call?) {
        synchronized(callLock) {
            if (call === activeCall) call = null
        }
    }
}
