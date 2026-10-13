package io.wanjuan.app.help.webView

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.ui.rss.read.VisibleWebView
import io.wanjuan.app.utils.setDarkeningAllowed
import splitties.init.appCtx
import java.util.Stack
import kotlin.math.max
import kotlin.random.Random

object WebViewPool {
    const val BLANK_HTML = "about:blank"
    const val DATA_HTML = "data:text/html;charset=utf-8;base64,"
    // 未使用的、已预初始化的WebView池 (使用栈结构，后进先出，复用缓存)
    private val idlePool = Stack<PooledWebView>()
    // 正在使用的WebView集合
    private val inUsePool = mutableMapOf<String, PooledWebView>()

    private val resettingPool = mutableMapOf<String, PooledWebView>()
    private val CACHED_WEB_VIEW_MAX_NUM = max(AppConfig.threadCount / 10, 5) // 池子总容量（闲置+使用）
    private const val IDLE_TIME_OUT: Long = 5 * 60 * 1000 // 闲置5分钟后销毁
    private const val IDLE_TIME_OUT_LAST: Long = 30 * 60 * 1000 // 最后一个闲置30分钟后销毁
    private const val RESET_TIME_OUT = 5_000L
    private val handler = Handler(Looper.getMainLooper())
    private val cleanup = Runnable { cleanupExpiredViews() }

    // 获取一个WebView
    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    fun acquire(context: Context): PooledWebView {
        checkMainThread()
        val pooledWebView = if (idlePool.isNotEmpty()) {
            idlePool.pop() // 复用闲置实例
        } else {
            createNewWebView() // 创建新实例
        }
        pooledWebView.upContext(context).apply {
            realWebView.settings.setDarkeningAllowed(AppConfig.isNightTheme) //设置是否夜间
            realWebView.settings.javaScriptEnabled = true
            realWebView.onResume()
            isInUse = true
        }
        inUsePool[pooledWebView.id] = pooledWebView
        pooledWebView.realWebView.setBackgroundColor(Color.TRANSPARENT)
        scheduleCleanup()
        return pooledWebView
    }

    // 释放WebView回池
    @Synchronized
    fun release(pooledWebView: PooledWebView) {
        checkMainThread()
        // A late or duplicate release must not destroy an idle/resetting instance.
        if (inUsePool.remove(pooledWebView.id) == null) return
        pooledWebView.isInUse = false
        pooledWebView.lastUseTime = SystemClock.uptimeMillis()
        // 重置WebView状态
        pooledWebView.realWebView.run {
            (parent as? ViewGroup)?.removeView(this)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            stopLoading()
            settings.javaScriptEnabled = false
            settings.offscreenPreRaster = false
            onPause()
            clearFocus() //清除焦点
            setOnLongClickListener(null)
            setOnTouchListener(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnScrollChangeListener(null)
            }
            setDownloadListener(null)
            outlineProvider = null
            clipToOutline = false
            webChromeClient = null
            removeJavascriptInterface(WebJsExtensions.nameBasic)
            removeJavascriptInterface(WebJsExtensions.nameJava)
            removeJavascriptInterface(WebJsExtensions.nameSource)
            removeJavascriptInterface(WebJsExtensions.nameCache)
            clearFormData() //清除表单数据
            clearMatches() //清除查找匹配项
            clearDisappearingChildren() //清除消失中的子视图
            clearAnimation() //清除动画
            pooledWebView.upContext(appCtx)
            if (idlePool.size + resettingPool.size >= CACHED_WEB_VIEW_MAX_NUM - inUsePool.size) {
                // 池子已满，直接销毁
                pooledWebView.realWebView.destroy()
                scheduleCleanup()
                return
            }
            resettingPool[pooledWebView.id] = pooledWebView
            webViewClient = object: WebViewClient() {
                @SuppressLint("SetJavaScriptEnabled")
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != BLANK_HTML || resettingPool.remove(pooledWebView.id) == null) return
                    view?.let{ webview ->
                        webview.settings.apply {
                            javaScriptEnabled = false
                            blockNetworkImage = false // 确保允许加载网络图片
                            cacheMode = WebSettings.LOAD_DEFAULT // 重置缓存模式
                            useWideViewPort = false // 恢复默认关闭宽视模式
                            loadWithOverviewMode = false // 恢复默认
                            textZoom = 100
                        }
                        // pauseTimers() affects every WebView in the process, including
                        // active comment windows. Idle instances disable their own JS instead.
                        webview.onPause()
                    }
                    pooledWebView.isInUse = false
                    pooledWebView.lastUseTime = SystemClock.uptimeMillis()
                    idlePool.push(pooledWebView)
                    scheduleCleanup()
                }
            }
            loadUrl(BLANK_HTML)
            scheduleCleanup()
        }
    }

    private fun createNewWebView(): PooledWebView {
        val webView = VisibleWebView(MutableContextWrapper(appCtx))
        preInitWebView(webView)
        return PooledWebView(webView, generateId())
    }

    private fun generateId(): String {
        return "web_${System.currentTimeMillis()}_${Random.nextLong()}"
    }

    // 初始化
    @SuppressLint("SetJavaScriptEnabled")
    private fun preInitWebView(webView: WebView) {
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.settings.apply {
            javaScriptEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
        }
        webView.setBackgroundColor(Color.TRANSPARENT)
    }

    // Schedule only the next expiry; WebView destruction must run on the main looper.
    private fun scheduleCleanup() {
        handler.removeCallbacks(cleanup)
        val nextIdle = idlePool.mapIndexed { index, pooled ->
            pooled.lastUseTime + if (index == 0) IDLE_TIME_OUT_LAST else IDLE_TIME_OUT
        }.minOrNull()
        val nextReset = resettingPool.values.minOfOrNull { it.lastUseTime + RESET_TIME_OUT }
        val next = listOfNotNull(nextIdle, nextReset).minOrNull() ?: return
        handler.postAtTime(cleanup, next)
    }

    private fun cleanupExpiredViews() {
        checkMainThread()
        val now = SystemClock.uptimeMillis()
        val expired = idlePool.filterIndexed { index, pooled ->
            now - pooled.lastUseTime >= if (index == 0) IDLE_TIME_OUT_LAST else IDLE_TIME_OUT
        } + resettingPool.values.filter { now - it.lastUseTime >= RESET_TIME_OUT }
        expired.forEach { pooled ->
            idlePool.remove(pooled)
            resettingPool.remove(pooled.id)
            pooled.realWebView.destroy()
        }
        scheduleCleanup()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "WebViewPool must be accessed on the main thread"
        }
    }
}
