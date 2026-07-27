package io.wanjuan.app.help.glide

import io.wanjuan.app.help.glide.progress.MangaProgressKey
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
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

internal class MangaImageFallbackDownloader(
    private val baseClient: OkHttpClient,
    private val createTempFile: () -> File,
    private val onConnecting: (String) -> Unit = {},
    private val onAttemptFailed: (String, Throwable) -> Unit = { _, _ -> },
    private val beforeCallPublication: () -> Unit = {},
) {
    private val callLock = Any()

    @Volatile
    private var cancelled = false

    @Volatile
    private var call: Call? = null

    fun download(
        chain: MangaImageRequestChain,
        prepare: (DownloadedMangaImage) -> DownloadedMangaImage? = { it },
    ): DownloadedMangaImage {
        val failures = mutableListOf<Throwable>()
        val client = baseClient.newBuilder()
            .readTimeout(chain.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()

        for (requestUrl in chain.urls.distinct()) {
            checkNotCancelled()
            onConnecting(chain.progressUrl)
            checkNotCancelled()
            var downloadedFile: File? = null
            var prepared: DownloadedMangaImage? = null
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
                    val target = createTempFile()
                    downloadedFile = target
                    response.body.byteStream().use { input ->
                        target.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    if (target.length() == 0L) throw IOException("图片响应为空")
                }
                checkNotCancelled()
                val candidate = DownloadedMangaImage(requireNotNull(downloadedFile), requestUrl)
                val preparedCandidate = prepare(candidate) ?: throw IOException("图片处理失败")
                prepared = preparedCandidate
                if (preparedCandidate.file != downloadedFile) downloadedFile.delete()
                if (!finishCall(newCall)) throw CancellationException("漫画图片加载已取消")
                return preparedCandidate
            } catch (error: Exception) {
                downloadedFile?.delete()
                prepared?.file?.takeIf { it != downloadedFile }?.delete()
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
        val activeCall = synchronized(callLock) {
            cancelled = true
            call
        }
        activeCall?.cancel()
    }

    private fun checkNotCancelled() {
        if (cancelled) throw CancellationException("漫画图片加载已取消")
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
