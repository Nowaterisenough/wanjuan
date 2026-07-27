package io.wanjuan.app.help.glide.progress

import android.os.Handler
import android.os.Looper
import io.wanjuan.app.model.analyzeRule.AnalyzeUrl
import java.util.concurrent.ConcurrentHashMap

private class Registration(val listener: OnProgressListener)

internal class ProgressListenerRegistry(
    private val postConnecting: (() -> Unit) -> Unit,
) : ProgressResponseBody.InternalProgressListener {
    private val listenersMap = ConcurrentHashMap<String, Registration>()

    override fun onProgress(url: String, bytesRead: Long, totalBytes: Long) {
        getRegistration(url)?.let { registration ->
            if (totalBytes <= 0L) {
                registration.listener.invoke(false, 0, bytesRead, totalBytes)
                return@let
            }
            val percentage = (bytesRead * 1f / totalBytes * 100f).toInt()
            val isComplete = percentage >= 100
            registration.listener.invoke(isComplete, percentage, bytesRead, totalBytes)
            if (isComplete) {
                removeListener(url)
            }
        }
    }

    fun addListener(url: String, listener: OnProgressListener) {
        if (url.isNotEmpty()) {
            listenersMap[normalize(url)] = Registration(listener)
            notifyConnecting(url)
        }
    }

    fun notifyConnecting(url: String) {
        if (url.isEmpty()) return
        val key = normalize(url)
        val registration = listenersMap[key]
        postConnecting {
            if (listenersMap[key] === registration) {
                registration?.listener?.invoke(false, 0, 0, 0)
            }
        }
    }

    fun removeListener(url: String) {
        if (url.isNotEmpty()) {
            listenersMap.remove(normalize(url))
        }
    }

    fun getProgressListener(url: String): OnProgressListener? {
        return getRegistration(url)?.listener
    }

    private fun getRegistration(url: String): Registration? {
        return if (url.isEmpty() || listenersMap.isEmpty()) null else listenersMap[normalize(url)]
    }

    private fun normalize(url: String): String {
        val urlMatcher = AnalyzeUrl.paramPattern.matcher(url)
        return if (urlMatcher.find()) {
            url.take(urlMatcher.start())
        } else {
            url
        }
    }
}

/**
 * 进度监听器管理类
 * 加入图片加载进度监听，加入Https支持
 */
object ProgressManager {
    private val mainThreadHandler by lazy { Handler(Looper.getMainLooper()) }
    private val registry = ProgressListenerRegistry { action ->
        mainThreadHandler.post(action)
    }

    val LISTENER: ProgressResponseBody.InternalProgressListener = registry

    fun addListener(url: String, listener: OnProgressListener) {
        registry.addListener(url, listener)
    }

    fun notifyConnecting(url: String) {
        registry.notifyConnecting(url)
    }

    fun removeListener(url: String) {
        registry.removeListener(url)
    }

    fun getProgressListener(url: String): OnProgressListener? {
        return registry.getProgressListener(url)
    }
}
