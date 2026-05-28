package io.wanjuan.app.help.source

import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.entities.BaseSource
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.CacheManager
import io.wanjuan.app.help.IntentData
import io.wanjuan.app.ui.association.VerificationCodeActivity
import io.wanjuan.app.ui.browser.WebViewActivity
import io.wanjuan.app.utils.isMainThread
import io.wanjuan.app.utils.startActivity
import splitties.init.appCtx
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration.Companion.minutes

/**
 * 源验证
 */
object SourceVerificationHelp {

    private val waitTime = 1.minutes.inWholeNanoseconds
    private val browserVerificationStates = ConcurrentHashMap<String, BrowserVerificationState>()
    private val singleVerificationLock = Any()

    private class BrowserVerificationState {
        val latch = CountDownLatch(1)
        @Volatile
        var result: Pair<String, String>? = null
        @Volatile
        var error: Throwable? = null
    }

    private fun getVerificationResultKey(source: BaseSource) =
        getVerificationResultKey(source.getKey())

    private fun getVerificationResultKey(sourceKey: String) = "${sourceKey}_verificationResult"

    /**
     * 获取书源验证结果
     * 图片验证码 防爬 滑动验证码 点击字符 等等
     */
    fun getVerificationResult(
        source: BaseSource?,
        url: String,
        title: String,
        useBrowser: Boolean,
        refetchAfterSuccess: Boolean = true,
        html: String? = null,
        refetchAfterSharedVerification: ((String) -> Pair<String, String>)? = null
    ): Pair<String, String> {
        source
            ?: throw NoStackTraceException("getVerificationResult parameter source cannot be null")
        require(url.length < 64 * 1024) { "getVerificationResult parameter url too long" }
        check(!isMainThread) { "getVerificationResult must be called on a background thread" }

        val shareBrowserVerification = useBrowser && refetchAfterSuccess && html == null
        if (!shareBrowserVerification) {
            return synchronized(singleVerificationLock) {
                performVerification(source, url, title, useBrowser, refetchAfterSuccess, html)
            }
        }

        val ownerState = BrowserVerificationState()
        val activeState = browserVerificationStates.putIfAbsent(source.getKey(), ownerState)
        if (activeState != null) {
            return waitForSharedBrowserVerification(
                source, url, activeState, refetchAfterSharedVerification
            )
        }

        try {
            val result = synchronized(singleVerificationLock) {
                performVerification(source, url, title, useBrowser, refetchAfterSuccess, html)
            }
            ownerState.result = result
            return result
        } catch (e: Throwable) {
            ownerState.error = e
            throw e
        } finally {
            ownerState.latch.countDown()
            browserVerificationStates.remove(source.getKey(), ownerState)
        }
    }

    private fun performVerification(
        source: BaseSource,
        url: String,
        title: String,
        useBrowser: Boolean,
        refetchAfterSuccess: Boolean,
        html: String?
    ): Pair<String, String> {
        clearResult(source.getKey())

        if (!useBrowser) {
            appCtx.startActivity<VerificationCodeActivity> {
                putExtra("imageUrl", url)
                putExtra("sourceOrigin", source.getKey())
                putExtra("sourceName", source.getTag())
                putExtra("sourceType", source.getSourceType())
                IntentData.put(getVerificationResultKey(source), Thread.currentThread())
            }
        } else {
            startBrowser(source, url, title, true, refetchAfterSuccess, html)
        }

        var waitUserInput = false
        while (getResult(source.getKey()) == null) {
            if (!waitUserInput && html == null) {
                AppLog.putDebug("等待返回验证结果...")
                waitUserInput = true
            }
            LockSupport.parkNanos(this, waitTime)
        }
        val result = getResult(source.getKey()) ?: throw NoStackTraceException("验证结果为空")
        clearResult(source.getKey())
        if (result.second.isEmpty()) throw NoStackTraceException("验证结果为空")
        return result
    }

    private fun waitForSharedBrowserVerification(
        source: BaseSource,
        url: String,
        activeState: BrowserVerificationState,
        refetchAfterSharedVerification: ((String) -> Pair<String, String>)?
    ): Pair<String, String> {
        var waitUserInput = false
        while (activeState.latch.count > 0) {
            if (!waitUserInput) {
                AppLog.putDebug("${source.getTag()} waiting for shared browser verification result...")
                waitUserInput = true
            }
            activeState.latch.await(waitTime, TimeUnit.NANOSECONDS)
        }
        activeState.error?.let { throw it }
        val result = activeState.result ?: throw NoStackTraceException("verification result is empty")
        if (result.second.isEmpty()) throw NoStackTraceException("verification result is empty")
        if (result.first == url || refetchAfterSharedVerification == null) {
            return result
        }
        return refetchAfterSharedVerification(url).also {
            if (it.second.isEmpty()) throw NoStackTraceException("verification result is empty")
        }
    }

    /**
     * 启动内置浏览器
     * @param saveResult 保存网页源代码到数据库
     */
    fun startBrowser(
        source: BaseSource?,
        url: String,
        title: String,
        saveResult: Boolean? = false,
        refetchAfterSuccess: Boolean? = true,
        html: String? = null
    ) {
        source ?: throw NoStackTraceException("startBrowser parameter source cannot be null")
        require(url.length < 64 * 1024) { "startBrowser parameter url too long" }
        appCtx.startActivity<WebViewActivity> {
            putExtra("title", title)
            putExtra("url", url)
            putExtra("sourceOrigin", source.getKey())
            putExtra("sourceName", source.getTag())
            putExtra("sourceType", source.getSourceType())
            putExtra("sourceVerificationEnable", saveResult)
            putExtra("refetchAfterSuccess", refetchAfterSuccess)
            putExtra("html", html)
            IntentData.put(getVerificationResultKey(source), Thread.currentThread())
        }
    }


    fun checkResult(sourceKey: String) {
        getResult(sourceKey) ?: setResult(sourceKey, "")
        val thread = IntentData.get<Thread>(getVerificationResultKey(sourceKey))
        LockSupport.unpark(thread)
    }

    fun setResult(sourceKey: String, result: String, url: String = "") {
        CacheManager.putMemory(getVerificationResultKey(sourceKey), (url to result))
    }

    fun getResult(sourceKey: String): Pair<String, String>? {
        val pair = CacheManager.getFromMemory(getVerificationResultKey(sourceKey)) as? Pair<*, *>
            ?: return null
        if (pair.first is String && pair.second is  String) {
            return pair.first as String to pair.second as String
        }
        return null
    }

    fun clearResult(sourceKey: String) {
        CacheManager.delete(getVerificationResultKey(sourceKey))
    }
}
