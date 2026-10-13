package io.wanjuan.app.help.webView

import android.os.Handler
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.wanjuan.app.help.http.BackstageWebView
import io.wanjuan.app.ui.about.AboutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class TestWebViewPowerLifecycle {
    private lateinit var scenario: ActivityScenario<AboutActivity>
    private lateinit var activity: AboutActivity
    private val borrowed = mutableListOf<PooledWebView>()

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(AboutActivity::class.java)
        scenario.onActivity { activity = it }
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) {
            borrowed.forEach(WebViewPool::release)
            idle().forEach { it.lastUseTime = SystemClock.uptimeMillis() - 31 * 60_000L }
            resetting().values.forEach { it.lastUseTime = SystemClock.uptimeMillis() - 31 * 60_000L }
            cleanup()
        }
        scenario.close()
    }

    @Test
    fun idleViewsDisableScriptsAndResumeOnReuseWithoutDuplicateEntries() = runBlocking {
        val pooled = acquire()
        withContext(Dispatchers.Main) {
            assertTrue(pooled.realWebView.settings.javaScriptEnabled)
            WebViewPool.release(pooled)
            WebViewPool.release(pooled)
            assertFalse(pooled.isInUse)
            assertFalse(pooled.realWebView.settings.javaScriptEnabled)
        }
        awaitIdle(pooled)
        withContext(Dispatchers.Main) {
            assertEquals(1, idle().count { it === pooled })
            assertFalse(pooled.realWebView.settings.javaScriptEnabled)
        }
        val reused = acquire()
        assertSame(pooled, reused)
        withContext(Dispatchers.Main) {
            assertTrue(reused.realWebView.settings.javaScriptEnabled)
        }
        assertEquals("2", evaluate(reused.realWebView, "1+1"))
    }

    @Test
    fun expiredIdleViewsAreDestroyedOnMainAndLeaveNoCleanupPolling() = runBlocking {
        val pooled = acquire()
        withContext(Dispatchers.Main) { WebViewPool.release(pooled) }
        awaitIdle(pooled)
        withContext(Dispatchers.Main) {
            idle().forEach { it.lastUseTime = SystemClock.uptimeMillis() - 31 * 60_000L }
            cleanup()
            assertTrue(idle().isEmpty())
            val handler = field("handler") as Handler
            assertFalse(handler.hasCallbacks(field("cleanup") as Runnable))
        }
    }

    @Test
    fun unfinishedBlankResetHasABoundedLifetime() = runBlocking {
        val pooled = acquire()
        withContext(Dispatchers.Main) {
            WebViewPool.release(pooled)
            pooled.realWebView.stopLoading()
            assertTrue(resetting().containsKey(pooled.id))
            pooled.lastUseTime = SystemClock.uptimeMillis() - 6_000L
            cleanup()
            assertFalse(resetting().containsKey(pooled.id))
            assertFalse(idle().contains(pooled))
        }
    }

    @Test
    fun stoppedWindowBecomesHiddenToTheWebPage() = runBlocking {
        val pooled = acquire()
        withContext(Dispatchers.Main) {
            activity.findViewById<ViewGroup>(android.R.id.content).addView(pooled.realWebView)
            pooled.realWebView.loadDataWithBaseURL(null, "<html><body>Power fixture</body></html>", "text/html", "utf-8", null)
        }
        withTimeout(5_000) {
            while (evaluate(pooled.realWebView, "document.visibilityState") != "\"visible\"") delay(50)
        }
        scenario.moveToState(Lifecycle.State.CREATED)
        withTimeout(5_000) {
            while (evaluate(pooled.realWebView, "document.visibilityState") != "\"hidden\"") delay(50)
        }
        scenario.moveToState(Lifecycle.State.RESUMED)
        withTimeout(5_000) {
            while (evaluate(pooled.realWebView, "document.visibilityState") != "\"visible\"") delay(50)
        }
    }

    @Test
    fun cancelledSnifferDoesNotRunDelayedScriptsOnReusedWebView() = runBlocking {
        val sniffer = BackstageWebView(
            html = "<html><body>Sniffer fixture</body></html>",
            javaScript = "window.lateWrites = (window.lateWrites || 0) + 1; null",
            delayTime = 1_500L
        )
        val task = async { sniffer.getStrResponse() }
        val pooled = withTimeout(5_000) {
            var pooled: PooledWebView? = null
            while (pooled == null) {
                delay(50)
                pooled = withContext(Dispatchers.Main) {
                    BackstageWebView::class.java.getDeclaredField("pooledWebView").apply { isAccessible = true }
                        .get(sniffer) as? PooledWebView
                }
            }
            pooled
        }
        delay(400)
        task.cancelAndJoin()
        awaitIdle(pooled)
        val reused = acquire()
        assertSame(pooled, reused)
        delay(1_700)
        assertEquals("0", evaluate(reused.realWebView, "window.lateWrites || 0"))
    }

    private suspend fun acquire() = withContext(Dispatchers.Main) {
        WebViewPool.acquire(activity).also(borrowed::add)
    }

    private suspend fun awaitIdle(pooled: PooledWebView) = withTimeout(5_000) {
        while (!withContext(Dispatchers.Main) { pooled in idle() }) delay(20)
    }

    private suspend fun evaluate(webView: WebView, script: String): String =
        withTimeout(3_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    webView.evaluateJavascript(script) {
                        if (continuation.isActive) continuation.resume(it)
                    }
                }
            }
        }

    private fun field(name: String): Any = WebViewPool::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(WebViewPool)!!

    @Suppress("UNCHECKED_CAST")
    private fun idle() = field("idlePool") as List<PooledWebView>

    @Suppress("UNCHECKED_CAST")
    private fun resetting() = field("resettingPool") as Map<String, PooledWebView>

    private fun cleanup() {
        WebViewPool::class.java.getDeclaredMethod("cleanupExpiredViews").apply { isAccessible = true }
            .invoke(WebViewPool)
    }
}
