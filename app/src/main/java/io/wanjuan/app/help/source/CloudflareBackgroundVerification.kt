package io.wanjuan.app.help.source

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.LifecycleHelp
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.webView.WebViewPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume

/** A single hidden verification attempt; the caller owns the foreground fallback. */
class CloudflareBackgroundVerification(
    private val url: String,
    private val headers: Map<String, String>,
    private val storedCookie: String,
    private val timeoutMillis: Long = 25000L
) {
    var cookie: String? = null
        private set
    var attemptedClick = false
        private set

    suspend fun verify(): Pair<String, String> = withContext(Dispatchers.Main.immediate) {
        val activity = LifecycleHelp.getResumedActivity()
            ?: throw NoStackTraceException("后台验证没有可用的阅读窗口")
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: throw NoStackTraceException("后台验证无法创建视口")
        val host = FrameLayout(activity).apply {
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        val pooledWebView = WebViewPool.acquire(activity)
        val webView = pooledWebView.realWebView
        var shadowTracker: ScriptHandler? = null
        try {
            // Keep a real layout and window without showing a page or taking reader input.
            host.addView(webView, FrameLayout.LayoutParams(-1, -1))
            content.addView(host, 0, ViewGroup.LayoutParams(-1, -1))
            val width = content.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
            val height = content.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
            host.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            host.layout(0, 0, width, height)
            withTimeout(timeoutMillis) {
                configure(webView)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    val origins = CloudflareVerification.allowedOrigins(url)
                    shadowTracker = WebViewCompat.addDocumentStartJavaScript(webView, SHADOW_TRACKER, origins)
                }
                restoreMissingCookies()
                val client = VerificationClient()
                webView.webViewClient = client
                webView.loadUrl(url, headers.filterKeys { !it.equals("Cookie", true) })
                var previousTarget: JSONObject? = null
                while (true) {
                    delay(500)
                    if (!host.isAttachedToWindow || activity.isDestroyed || activity.isFinishing) {
                        throw NoStackTraceException("后台验证窗口已关闭")
                    }
                    client.error?.let { throw NoStackTraceException(it) }
                    if (!client.pageFinished) continue
                    val navigation = client.navigation
                    val page = snapshot(webView)
                    if (navigation != client.navigation || !client.pageFinished) continue
                    val body = page.optString("html")
                    val challenge = CloudflareVerification.isChallengeBody(body)
                    if (!challenge && !client.challenged && client.statusCode >= 400) {
                        throw NoStackTraceException("后台验证请求失败: HTTP ${client.statusCode}")
                    }
                    if (!challenge && !client.challenged && page.optInt("textLength") > 0 &&
                        page.optString("ready") == "complete"
                    ) {
                        return@withTimeout (webView.url ?: url) to body
                    }
                    if (!attemptedClick && (challenge || client.challenged)) {
                        val target = page.optJSONObject("target")
                        if (target != null && sameTarget(previousTarget, target)) {
                            attemptedClick = true
                            AppLog.putDebug("Cloudflare: 后台尝试点击验证控件")
                            tap(webView, target, page.optDouble("viewportWidth"))
                        }
                        previousTarget = target
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                error("Unreachable")
            }
        } finally {
            val cookieManager = CookieManager.getInstance()
            cookie = cookieManager.getCookie(url)
            cookieManager.flush()
            shadowTracker?.remove()
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            content.removeView(host)
            host.removeView(webView)
            webView.settings.offscreenPreRaster = false
            WebViewPool.release(pooledWebView)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = false
            offscreenPreRaster = true
            userAgentString = headers.entries.firstOrNull {
                it.key.equals(AppConst.UA_NAME, true)
            }?.value ?: AppConfig.userAgent
        }
        webView.onResume()
    }

    private suspend fun restoreMissingCookies() {
        val manager = CookieManager.getInstance()
        val existing = manager.getCookie(url).orEmpty().split(';')
            .map { it.substringBefore('=').trim() }.toSet()
        // The browser's current cookies take precedence over older HTTP cookie snapshots.
        for (entry in storedCookie.split(';')) {
            val name = entry.substringBefore('=').trim()
            if ('=' in entry && name.isNotEmpty() && name !in existing) {
                suspendCancellableCoroutine { continuation ->
                    manager.setCookie(url, entry.trim()) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        }
    }

    private suspend fun snapshot(webView: WebView): JSONObject =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(PROBE_SCRIPT) { result ->
                if (continuation.isActive) {
                    continuation.resume(runCatching { JSONObject(result) }.getOrDefault(JSONObject()))
                }
            }
        }

    private fun sameTarget(previous: JSONObject?, current: JSONObject): Boolean = previous != null &&
        previous.optString("id") == current.optString("id") &&
        kotlin.math.abs(previous.optDouble("x") - current.optDouble("x")) < 2 &&
        kotlin.math.abs(previous.optDouble("y") - current.optDouble("y")) < 2

    private suspend fun tap(webView: WebView, target: JSONObject, viewportWidth: Double) {
        if (!viewportWidth.isFinite() || viewportWidth <= 0) return
        val scale = webView.width / viewportWidth
        val x = (target.optDouble("x") * scale).toFloat()
        val y = (target.optDouble("y") * scale).toFloat()
        if (!x.isFinite() || !y.isFinite() || x < 0 || y < 0 ||
            x >= webView.width || y >= webView.height
        ) return
        val downTime = SystemClock.uptimeMillis()
        fun dispatch(action: Int) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                webView.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        dispatch(MotionEvent.ACTION_DOWN)
        try {
            delay(80)
            dispatch(MotionEvent.ACTION_UP)
        } catch (error: Throwable) {
            dispatch(MotionEvent.ACTION_CANCEL)
            throw error
        }
    }

    private inner class VerificationClient : WebViewClient() {
        var pageFinished = false
        var challenged = false
        var navigation = 0
        var statusCode = 200
        var error: String? = null

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pageFinished = false
            challenged = false
            statusCode = 200
            navigation++
        }

        override fun onPageFinished(view: WebView, url: String) {
            pageFinished = true
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val allowed = CloudflareVerification.allowsRedirect(url, request.url.toString())
            if (!allowed) error = "后台验证跳转到了其他站点"
            return !allowed
        }

        override fun onReceivedHttpError(
            view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse
        ) {
            if (!request.isForMainFrame) return
            statusCode = errorResponse.statusCode
            challenged = errorResponse.responseHeaders.orEmpty().any {
                it.key.equals("cf-mitigated", true) && it.value.equals("challenge", true)
            }
            if (!challenged && errorResponse.statusCode !in listOf(403, 503)) {
                error = "后台验证请求失败: HTTP ${errorResponse.statusCode}"
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) this.error = "后台验证页面加载失败: ${error.errorCode}"
        }
    }

    companion object {
        // Retain roots as they are created without changing the widget's closed-shadow mode.
        private val SHADOW_TRACKER = """
            (function() {
                var roots = [];
                var widgets = new WeakMap();
                var attach = Element.prototype.attachShadow;
                Object.defineProperty(window, '__wanjuanCfRoots', {value:roots});
                Object.defineProperty(window, '__wanjuanCfWidgets', {value:widgets});
                window.addEventListener('message', function(e) {
                    if (!e.source || !e.data || e.data.source !== 'cloudflare-challenge') return;
                    if (e.data.event === 'interactiveBegin') {
                        widgets.set(e.source, {origin:e.origin, ready:true});
                    } else if (['init', 'interactiveEnd', 'complete', 'fail', 'reject', 'refreshRequest']
                        .indexOf(e.data.event) >= 0) {
                        widgets.delete(e.source);
                    }
                });
                Element.prototype.attachShadow = function(options) {
                    var root = attach.call(this, options);
                    roots.push(root);
                    return root;
                };
            })();
        """.trimIndent()

        private val PROBE_SCRIPT = """
            (function() {
                var target = null;
                var viewport = window.visualViewport;
                var width = viewport ? viewport.width : window.innerWidth;
                var height = viewport ? viewport.height : window.innerHeight;
                var offsetX = viewport ? viewport.offsetLeft : 0;
                var offsetY = viewport ? viewport.offsetTop : 0;
                var roots = [document].concat(window.__wanjuanCfRoots || []);
                for (var i = 0; i < roots.length && i < 100 && !target; i++) {
                    if (roots[i].host && !roots[i].host.isConnected) continue;
                    var nodes = roots[i].querySelectorAll('*');
                    for (var j = 0; j < nodes.length; j++) {
                        var node = nodes[j];
                        if (node.shadowRoot && roots.indexOf(node.shadowRoot) < 0) roots.push(node.shadowRoot);
                        if (node.tagName !== 'IFRAME') continue;
                        var src;
                        try { src = new URL(node.src, location.href); } catch (e) { continue; }
                        if (src.hostname !== 'challenges.cloudflare.com' &&
                            !/^cf-chl-widget-/.test(node.id)) continue;
                        // The iframe is laid out while the checkbox is still a loading spinner.
                        var state = window.__wanjuanCfWidgets && window.__wanjuanCfWidgets.get(node.contentWindow);
                        if (!state || !state.ready || state.origin !== src.origin) continue;
                        var rect = node.getBoundingClientRect();
                        var style = getComputedStyle(node);
                        // Only the standard checkbox layout has a known hit region.
                        if (node.clientWidth < 250 || node.clientHeight < 50 || node.clientHeight > 100 ||
                            rect.width <= 0 || rect.height <= 0 || style.visibility !== 'visible' ||
                            style.display === 'none' || Number(style.opacity) === 0) continue;
                        var x = rect.left + 30 * rect.width / node.offsetWidth;
                        var y = rect.top + rect.height / 2;
                        if (x < offsetX || x >= offsetX + width || y < offsetY || y >= offsetY + height) {
                            node.scrollIntoView({block:'center', inline:'nearest'});
                            continue;
                        }
                        var hit = roots[i].elementFromPoint(x, y);
                        if (hit !== node) continue;
                        target = {id:node.id || node.src, x:x-offsetX, y:y-offsetY};
                        break;
                    }
                }
                return {
                    html:document.documentElement ? document.documentElement.outerHTML : '',
                    textLength:document.body ? document.body.innerText.trim().length : 0,
                    ready:document.readyState, viewportWidth:width, target:target
                };
            })();
        """.trimIndent()
    }
}
