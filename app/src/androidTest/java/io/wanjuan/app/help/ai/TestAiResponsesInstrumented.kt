package io.wanjuan.app.help.ai

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.MenuInflater
import android.widget.FrameLayout
import androidx.appcompat.view.menu.MenuBuilder
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.R
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.ui.book.read.ReadAiFloatingPanel
import io.wanjuan.app.ui.config.AiConfigFragment
import io.wanjuan.app.ui.config.ConfigActivity
import io.wanjuan.app.ui.config.ConfigTag
import io.wanjuan.app.ui.main.ai.AiChatMessage
import io.wanjuan.app.ui.main.ai.AiModelConfig
import io.wanjuan.app.ui.main.ai.AiProviderConfig
import io.wanjuan.app.utils.defaultSharedPreferences
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TestAiResponsesInstrumented {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.defaultSharedPreferences
    private lateinit var saved: Map<String, *>

    @Before fun saveSettings() {
        saved = preferences.all.filterKeys { it.startsWith("ai") }
    }

    @After fun restoreSettings() {
        preferences.edit().apply {
            preferences.all.keys.filter { it.startsWith("ai") }.forEach { remove(it) }
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }.commit()
    }

    @Test fun readerSendsResponsesWithImagesAndWebSearchWithoutLocalTools() = runBlocking {
        FixtureServer(listOf(completed("verified answer"))).use { server ->
            configure(server)
            val result = AiChatService.chatStream(listOf(AiChatMessage(
                role = AiChatMessage.Role.USER, content = "fixture chapter",
                imageDataUrls = listOf("data:image/png;base64,Zml4dHVyZQ==")
            )), {}, reading = true, webSearch = true)
            assertTrue(result.endsWith("verified answer"))
            assertTrue(result.contains("未经联网核验"))
            server.await()
            val (path, body) = server.requests.single()
            assertEquals("POST /v1/responses HTTP/1.1", path)
            assertFalse(body.has("messages"))
            assertFalse(body.getBoolean("store"))
            assertEquals(1, body.getJSONArray("tools").length())
            assertEquals("web_search", body.getJSONArray("tools").getJSONObject(0).getString("type"))
            assertTrue(body.toString().contains("input_image"))
            assertTrue(body.toString().contains("fixture chapter"))
        }
    }

    @Test fun nativeToolContinuationUsesCallIdAndPreservesOutputItems() = runBlocking {
        val tool = JSONObject("""{"type":"function_call","id":"fc_test","call_id":"call_test","name":"get_app_settings","arguments":"{}"}""")
        val reasoning = JSONObject("""{"type":"reasoning","id":"rs_test","summary":[],"encrypted_content":"opaque"}""")
        FixtureServer(listOf(response(listOf(reasoning, tool)), completed("tools complete"))).use { server ->
            configure(server)
            AppConfig.aiEnabledToolNames = setOf("get_app_settings")
            val answer = AiChatService.chatStream(listOf(AiChatMessage(role = AiChatMessage.Role.USER, content = "settings")), {})
            assertTrue(answer.startsWith("tools complete"))
            server.await()
            val followUp = server.requests[1].second.getJSONArray("input")
            val result = followUp.getJSONObject(followUp.length() - 1)
            assertEquals("function_call_output", result.getString("type"))
            assertEquals("call_test", result.getString("call_id"))
            assertTrue(followUp.toString().contains("encrypted_content"))
        }
    }

    @Test fun streamCancellationClosesTheRequestPromptly() = runBlocking {
        FixtureServer(listOf("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n"), holdOpen = true).use { server ->
            configure(server)
            val partial = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.IO) {
                AiChatService.chatStream(listOf(AiChatMessage(role = AiChatMessage.Role.USER, content = "cancel")),
                    { partial.complete(Unit) }, reading = true)
            }
            withTimeout(5000) { partial.await() }
            withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }

    @Test fun chapterPanelIncludesActualContentAndDoesNotPersistImages() {
        FixtureServer(listOf(completed("chapter evidence"))).use { server ->
            configure(server)
            ActivityScenario.launch<ConfigActivity>(configIntent()).use { scenario ->
                lateinit var panel: ReadAiFloatingPanel
                scenario.onActivity { activity ->
                    panel = ReadAiFloatingPanel(activity)
                    activity.addContentView(panel, panelLayoutParams())
                    panel.attach(activity)
                    panel.open(ReadAiFloatingPanel.ReadContext(
                        "ai-fixture-book", "fixture book", "fixture author", "", "chapter 1", 0,
                        "analyze", chapterContent = "ACTUAL_CHAPTER_EVIDENCE", contentScope = "current chapter"
                    ))
                }
                server.await()
                assertTrue(server.requests.single().second.toString().contains("ACTUAL_CHAPTER_EVIDENCE"))
                for (attempt in 0 until 100) {
                    if (AppConfig.aiReadHistoryList.any { history -> history.sessions.any { session -> session.messages.any { it.content == "chapter evidence" } } }) break
                    Thread.sleep(50)
                }
                assertTrue(AppConfig.aiReadHistoryList.any { history -> history.sessions.any { session -> session.messages.any { it.content == "chapter evidence" } } })
                screenshot("ai-reading-panel.png")
                scenario.onActivity { panel.close() }
                assertFalse(preferences.all.filterKeys { it.startsWith("ai") }.values.toString().contains("data:image/"))
            }
        }
    }

    @Test fun mangaUsesActualLocalImageBytes() = runBlocking {
        val file = File(context.cacheDir, "ai-image-fixture.png")
        val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val images = AiReadingImages.load(listOf(file.absolutePath), null)
            assertEquals(1, images.size)
            assertTrue(images.single().startsWith("data:image/jpeg;base64,/9j/"))
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    @Test fun changingReadingContextCancelsOldAnswerWithoutChangingNewSession() {
        val partial = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"old partial\"}\n\n"
        FixtureServer(listOf(partial, completed("new chapter answer")), holdOpen = true).use { server ->
            configure(server)
            ActivityScenario.launch<ConfigActivity>(configIntent()).use { scenario ->
                lateinit var panel: ReadAiFloatingPanel
                val first = ReadAiFloatingPanel.ReadContext("ai-race-fixture", "book", "author", "", "first", 0,
                    "first question", chapterContent = "first chapter")
                scenario.onActivity { activity ->
                    panel = ReadAiFloatingPanel(activity)
                    activity.addContentView(panel, panelLayoutParams())
                    panel.attach(activity)
                    panel.open(first)
                }
                for (attempt in 0 until 100) {
                    if (server.requests.size == 1) break
                    Thread.sleep(20)
                }
                assertEquals(1, server.requests.size)
                scenario.onActivity { panel.open(first.copy(chapterIndex = 1, chapterTitle = "second",
                    selectedText = "second question", chapterContent = "second chapter")) }
                server.await()
                for (attempt in 0 until 100) {
                    if (AppConfig.aiReadHistoryList.first { it.bookUrl == first.bookUrl }.sessions.any {
                            it.messages.any { message -> message.content == "new chapter answer" }
                        }) break
                    Thread.sleep(20)
                }
                val history = AppConfig.aiReadHistoryList.first { it.bookUrl == first.bookUrl }
                val current = history.sessions.first { it.id == history.currentSessionId }
                assertEquals(1, current.chapterIndex)
                assertEquals("new chapter answer", current.messages.last().content)
                scenario.onActivity { panel.close() }
            }
        }
    }

    @Test fun onlyResponsesConfigurationAndBothReaderEntriesAreAvailable() {
        ActivityScenario.launch<ConfigActivity>(configIntent()).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(ConfigTag.AI_CONFIG) as AiConfigFragment
                assertNotNull(fragment.findPreference<Preference>("aiAddProvider"))
                for (key in listOf("aiAddMcpServer", "aiManageMcpServers", "aiTavilyEnabled", "aiTavilyApiKey", "aiImportDefaultSkill", "aiSkillPrompt")) {
                    assertNull(fragment.findPreference<Preference>(key))
                }
                for (menuId in listOf(R.menu.book_read, R.menu.book_manga)) {
                    val menu = MenuBuilder(activity)
                    MenuInflater(activity).inflate(menuId, menu)
                    assertTrue(menu.findItem(R.id.menu_read_ai).isVisible)
                }
            }
            screenshot("ai-responses-settings.png")
        }
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val image = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(context.externalCacheDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
    }

    private fun panelLayoutParams() = FrameLayout.LayoutParams(
        (320 * context.resources.displayMetrics.density).toInt()
            .coerceAtMost(context.resources.displayMetrics.widthPixels),
        FrameLayout.LayoutParams.WRAP_CONTENT
    )

    private fun configIntent() = Intent(context, ConfigActivity::class.java).putExtra("configTag", ConfigTag.AI_CONFIG)

    private fun configure(server: FixtureServer) {
        val provider = AiProviderConfig(id = "fixture-provider", name = "Responses fixture", baseUrl = "http://127.0.0.1:${server.port}/v1/chat/completions")
        AppConfig.aiProviderList = listOf(provider)
        AppConfig.aiModelConfigList = listOf(AiModelConfig(id = "fixture-model", providerId = provider.id, modelId = "fixture"))
        AppConfig.aiCurrentProviderId = provider.id
        AppConfig.aiCurrentModelId = "fixture-model"
        AppConfig.aiAssistantEnabled = true
    }

    private fun completed(text: String) = response(listOf(JSONObject().put("type", "message").put("role", "assistant")
        .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", text)))))

    private fun response(output: List<JSONObject>) = "data: " + JSONObject().put("type", "response.completed")
        .put("response", JSONObject().put("status", "completed").put("output", JSONArray(output))) + "\n\n"

    private class FixtureServer(responses: List<String>, holdOpen: Boolean = false) : AutoCloseable {
        private val server = ServerSocket(0)
        val port = server.localPort
        val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        private val executor = Executors.newSingleThreadExecutor()
        @Volatile private var active: Socket? = null
        private val worker = executor.submit {
            responses.forEach { payload ->
                server.accept().use { socket ->
                    active = socket
                    socket.soTimeout = 10000
                    val input = socket.getInputStream().buffered()
                    fun line(): String = buildString {
                        while (true) {
                            val byte = input.read()
                            if (byte < 0 || byte == 10) break
                            if (byte != 13) append(byte.toChar())
                        }
                    }
                    val path = line()
                    var length = 0
                    while (true) {
                        val header = line()
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                    }
                    val bytes = ByteArray(length)
                    var offset = 0
                    while (offset < length) {
                        val count = input.read(bytes, offset, length - offset)
                        check(count > 0)
                        offset += count
                    }
                    requests += path to JSONObject(String(bytes, Charsets.UTF_8))
                    val body = payload.toByteArray(Charsets.UTF_8)
                    val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n" +
                        if (holdOpen) "\r\n" else "Content-Length: ${body.size}\r\n\r\n"
                    socket.getOutputStream().apply { write(headers.toByteArray()); write(body); flush() }
                    if (holdOpen) input.read()
                }
            }
        }
        fun await() { worker.get(10, TimeUnit.SECONDS) }
        override fun close() {
            active?.close()
            server.close()
            executor.shutdownNow()
        }
    }
}
