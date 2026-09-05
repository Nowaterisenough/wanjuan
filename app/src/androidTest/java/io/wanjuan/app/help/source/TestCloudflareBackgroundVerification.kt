package io.wanjuan.app.help.source

import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.elonen.NanoHTTPD
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.help.LifecycleHelp
import io.wanjuan.app.ui.browser.WebViewActivity
import io.wanjuan.app.ui.about.AboutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class TestCloudflareBackgroundVerification {
    private val server = ChallengeFixture()
    private lateinit var scenario: ActivityScenario<AboutActivity>
    private lateinit var activity: AboutActivity
    private var childCount = 0

    @Before
    fun setUp() {
        server.start()
        scenario = ActivityScenario.launch(AboutActivity::class.java)
        scenario.onActivity {
            activity = it
            childCount = it.findViewById<ViewGroup>(android.R.id.content).childCount
        }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            (LifecycleHelp.getResumedActivity() as? WebViewActivity)?.finish()
        }
        scenario.close()
        server.stop()
    }

    @Test
    fun hiddenCrossOriginCheckboxReceivesOneTapAndReturnsContent() = runBlocking {
        val verifier = verifier("/challenge?pass=1")
        val attempt = async { verifier.verify() }
        delay(600)
        withContext(Dispatchers.Main) {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertSame(activity, LifecycleHelp.getResumedActivity())
            assertEquals(childCount + 1, content.childCount)
            assertEquals(0f, content.getChildAt(0).alpha)
        }
        val result = attempt.await()
        assertTrue(verifier.attemptedClick)
        assertEquals(1, server.clicks.get())
        assertTrue(result.first.endsWith("/content"))
        assertTrue(result.second.contains("Verified chapter"))
        assertTrue(verifier.cookie.orEmpty().contains("background_fixture=passed"))
        assertCleanedUp()
    }

    @Test
    fun hiddenClosedShadowCheckboxReceivesTap() = runBlocking {
        val verifier = verifier("/challenge?pass=1&closed=1")
        val result = verifier.verify()
        assertTrue(verifier.attemptedClick)
        assertEquals(1, server.clicks.get())
        assertTrue(result.second.contains("Verified chapter"))
        assertCleanedUp()
    }

    @Test
    fun waitsForInteractiveCheckboxBeforeUsingItsOnlyTap() = runBlocking {
        val verifier = verifier("/challenge?pass=1&closed=1&delay=4000")
        val attempt = async { verifier.verify() }
        delay(2500)
        assertFalse(verifier.attemptedClick)
        assertEquals(0, server.clicks.get())
        val result = attempt.await()
        assertTrue(verifier.attemptedClick)
        assertEquals(1, server.clicks.get())
        assertTrue(result.second.contains("Verified chapter"))
        assertCleanedUp()
    }

    @Test
    fun ignoresReadinessFromADifferentWindow() = runBlocking {
        val verifier = verifier("/challenge?pass=1&delay=10000&spoof=1", timeout = 3500)
        val failure = runCatching { verifier.verify() }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
        assertFalse(verifier.attemptedClick)
        assertEquals(0, server.clicks.get())
        assertCleanedUp()
    }

    @Test
    fun unrecognizedWidgetTimesOutWithoutClickingAndRemovesHiddenView() = runBlocking {
        val verifier = verifier("/challenge?unknown=1", timeout = 3500)
        val failure = runCatching { verifier.verify() }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
        assertFalse(verifier.attemptedClick)
        assertEquals(0, server.clicks.get())
        assertCleanedUp()
    }

    @Test
    fun failedBackgroundClickOpensForegroundVerificationAfterTimeout() = runBlocking {
        val source = BookSource(bookSourceUrl = "https://www.uaa.com", bookSourceName = "UAA fixture")
        val attempt = async(Dispatchers.IO) {
            SourceVerificationHelp.getVerificationResult(
                source, server.url("/challenge"), "Cloudflare", useBrowser = true
            )
        }
        try {
            withTimeout(10000) {
                while (server.clicks.get() == 0) delay(200)
            }
            withContext(Dispatchers.Main) {
                assertSame(activity, LifecycleHelp.getResumedActivity())
            }
            val browser = withTimeout(30000) {
                var browser: WebViewActivity? = null
                while (browser == null) {
                    delay(200)
                    browser = withContext(Dispatchers.Main) {
                        LifecycleHelp.getResumedActivity() as? WebViewActivity
                    }
                }
                browser
            }
            assertEquals(1, server.clicks.get())
            withContext(Dispatchers.Main) {
                assertEquals(childCount, activity.findViewById<ViewGroup>(android.R.id.content).childCount)
            }
            SourceVerificationHelp.setResult(source.bookSourceUrl, "<p>Manual verification</p>", server.url("/content"))
            SourceVerificationHelp.checkResult(source.bookSourceUrl)
            assertTrue(withTimeout(5000) { attempt.await() }.second.contains("Manual verification"))
            withContext(Dispatchers.Main) { browser.finish() }
        } finally {
            // Release the blocking source-rule thread even when an assertion fails.
            SourceVerificationHelp.setResult(source.bookSourceUrl, "<p>Stopped</p>")
            SourceVerificationHelp.checkResult(source.bookSourceUrl)
            attempt.cancel()
        }
    }

    private fun verifier(path: String, timeout: Long = 12000) =
        CloudflareBackgroundVerification(server.url(path), emptyMap(), "", timeout)

    private suspend fun assertCleanedUp() = withContext(Dispatchers.Main) {
        assertSame(activity, LifecycleHelp.getResumedActivity())
        assertEquals(childCount, activity.findViewById<ViewGroup>(android.R.id.content).childCount)
    }

    private class ChallengeFixture : NanoHTTPD("127.0.0.1", 0) {
        val clicks = AtomicInteger()
        fun url(path: String) = "http://127.0.0.1:$listeningPort$path"

        override fun serve(session: IHTTPSession): Response {
            if (session.uri == "/clicked") {
                clicks.incrementAndGet()
                return newFixedLengthResponse("ok")
            }
            if (session.uri == "/content") {
                return newFixedLengthResponse("""
                    <title>Chapter</title><div id="readerContent">Verified chapter</div>
                    <script type="text/plain" src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
                """.trimIndent()).apply {
                    addHeader("Set-Cookie", "background_fixture=passed; HttpOnly; Path=/; Max-Age=60")
                }
            }
            if (session.uri == "/widget") {
                val readyDelay = session.parameters["delay"]?.firstOrNull()?.toIntOrNull() ?: 0
                return newFixedLengthResponse(Response.Status.OK, "text/html", """
                    <style>body{margin:0}input{position:absolute;left:18px;top:20px;width:24px;height:24px;margin:0}</style>
                    <input disabled type="checkbox" onclick="fetch('/clicked').then(function(){parent.postMessage('verified','*')})">
                    <script>setTimeout(function(){
                        document.querySelector('input').disabled=false;
                        parent.postMessage({source:'cloudflare-challenge',event:'interactiveBegin'},'*');
                    },$readyDelay);</script>
                """.trimIndent())
            }
            val pass = session.parameters["pass"]?.firstOrNull() == "1"
            val unknown = session.parameters["unknown"]?.firstOrNull() == "1"
            val closed = session.parameters["closed"]?.firstOrNull() == "1"
            val readyDelay = session.parameters["delay"]?.firstOrNull()?.toIntOrNull() ?: 0
            val spoof = session.parameters["spoof"]?.firstOrNull() == "1"
            val frame = """
                <iframe id="${if (unknown) "unknown-widget" else "cf-chl-widget-fixture"}"
                    src="http://localhost:$listeningPort/widget?delay=$readyDelay" style="border:0;width:300px;height:65px"></iframe>
            """.trimIndent()
            val widget = if (closed) """
                <div id="widget-host"></div><script>
                    document.getElementById('widget-host').attachShadow({mode:'closed'}).innerHTML =
                        ${org.json.JSONObject.quote(frame)};
                </script>
            """.trimIndent() else frame
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html", """
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Just a moment...</title><p>Verify you are human</p>
                <script>window._cf_chl_opt={};
                    if ($spoof) window.postMessage({source:'cloudflare-challenge',event:'interactiveBegin'},'*');
                    window.addEventListener('message',function(e){
                        if(e.data==='verified' && $pass) location.href='/content';
                    });
                </script>
                $widget
            """.trimIndent()).apply { addHeader("cf-mitigated", "challenge") }
        }
    }
}
