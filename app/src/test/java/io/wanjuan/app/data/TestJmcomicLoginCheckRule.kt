package io.wanjuan.app.data

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestJmcomicLoginCheckRule {

    @Test
    fun normalCloudflareInstrumentedPageDoesNotOpenBrowser() {
        val response = FakeResponse(
            urlValue = "https://comic18j-codi.net/albums?o=mr&page=1",
            codeValue = 200,
            bodyValue = """
                <title>最新 Comics - 禁漫天堂</title>
                <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
            """.trimIndent()
        )

        rules().forEach { (fileName, rule) ->
            assertTrue("$fileName should contain Jmcomic loginCheckJs", rule.isNotBlank())
            assertEquals(fileName, emptyList<BrowserCall>(), evaluate(rule, response))
        }
    }

    @Test
    fun challengeBodyMarkersRequireChallengeStatus() {
        val cases = listOf(
            ResponseCase(
                "403 without an explicit marker",
                FakeResponse("https://comic18j-codi.net/albums", 403, "forbidden")
            ),
            ResponseCase(
                "503 without an explicit marker",
                FakeResponse("https://comic18j-codi.net/albums", 503, "service unavailable")
            ),
            ResponseCase(
                "200 with a challenge title only",
                FakeResponse(
                    "https://comic18j-codi.net/albums",
                    200,
                    "<title>Just a moment</title>"
                )
            ),
            ResponseCase(
                "200 with a challenge form only",
                FakeResponse(
                    "https://comic18j-codi.net/albums",
                    200,
                    "<form id=\"challenge-form\"></form>"
                )
            )
        )

        rules().forEach { (fileName, rule) ->
            cases.forEach { case ->
                assertEquals(
                    "$fileName ${case.name}",
                    emptyList<BrowserCall>(),
                    evaluate(rule, case.response)
                )
            }
        }
    }

    @Test
    fun explicitLoginFailureOpensLoginBrowser() {
        val responses = listOf(
            FakeResponse("https://comic18j-codi.net/account", 401, "unauthorized"),
            FakeResponse("https://comic18j-codi.net/login", 200, "<title>会员登录</title>")
        )

        rules().forEach { (fileName, rule) ->
            responses.forEach { response ->
                assertEquals(
                    fileName,
                    listOf(BrowserCall(response.url(), "登录", false)),
                    evaluate(rule, response)
                )
            }
        }
    }

    @Test
    fun explicitVerificationAndCloudflareChallengeOpenVerificationBrowser() {
        val cases = listOf(
            ResponseCase(
                "explicit verify page",
                FakeResponse("https://comic18j-codi.net/verify.php", 200, "verify")
            ),
            ResponseCase(
                "403 challenge title only",
                FakeResponse(
                    "https://comic18j-codi.net/albums",
                    403,
                    "<title>Just a moment</title>"
                )
            ),
            ResponseCase(
                "403 challenge form only",
                FakeResponse(
                    "https://comic18j-codi.net/albums",
                    403,
                    "<form id=\"challenge-form\"></form>"
                )
            ),
            ResponseCase(
                "503 challenge title only",
                FakeResponse(
                    "https://comic18j-codi.net/albums",
                    503,
                    "<title>Checking your browser</title>"
                )
            ),
            ResponseCase(
                "503 challenge form only",
                FakeResponse(
                    "https://comic18j-codi.net/albums",
                    503,
                    "<input name=\"cf-turnstile-response\">"
                )
            ),
            ResponseCase(
                "Cloudflare challenge response header",
                FakeResponse(
                    "https://comic18j-codi.net/albums",
                    200,
                    "challenge",
                    mapOf("cf-mitigated" to "challenge")
                )
            )
        )

        rules().forEach { (fileName, rule) ->
            cases.forEach { case ->
                assertEquals(
                    "$fileName ${case.name}",
                    listOf(BrowserCall(case.response.url(), "验证", false)),
                    evaluate(rule, case.response)
                )
            }
        }
    }

    @Test
    fun dynamicSourceCopiesStaySynchronized() {
        val sources = jmcomicSources()

        sources.forEach { (fileName, source) ->
            assertEquals(
                "$fileName Jmcomic lastUpdateTime",
                EXPECTED_LAST_UPDATE_TIME,
                source.lastUpdateTime
            )
        }
        assertEquals(
            "Jmcomic loginCheckJs should be identical in both dynamic source files",
            sources[0].second.loginCheckJs,
            sources[1].second.loginCheckJs
        )
    }

    private fun rules(): List<Pair<String, String>> {
        return jmcomicSources().map { (fileName, source) ->
            fileName to source.loginCheckJs.orEmpty()
        }
    }

    private fun jmcomicSources(): List<Pair<String, BookSource>> {
        return SOURCE_FILES.map { fileName ->
            val sources = GSON.fromJsonArray<BookSource>(repoFile("tests/$fileName").readText())
                .getOrThrow()
                .filter { it.bookSourceUrl == JMCOMIC_SOURCE_URL }
            assertEquals("$fileName should contain exactly one Jmcomic source", 1, sources.size)
            fileName to sources.single()
        }
    }

    private fun evaluate(rule: String, response: FakeResponse): List<BrowserCall> {
        val java = FakeJava()
        val bindings = ScriptBindings().apply {
            this["result"] = response
            this["java"] = java
        }
        RhinoScriptEngine.eval(rule, bindings)
        return java.calls.toList()
    }

    data class BrowserCall(
        val url: String,
        val title: String,
        val refetchAfterSuccess: Boolean
    )

    private data class ResponseCase(
        val name: String,
        val response: FakeResponse
    )

    class FakeJava {
        val calls = mutableListOf<BrowserCall>()

        fun startBrowserAwait(
            url: String,
            title: String,
            refetchAfterSuccess: Boolean
        ): FakeResponse {
            calls += BrowserCall(url, title, refetchAfterSuccess)
            return FakeResponse(url, 200, "verified")
        }
    }

    class FakeResponse(
        private val urlValue: String,
        private val codeValue: Int,
        private val bodyValue: String,
        private val headerValues: Map<String, String> = emptyMap()
    ) {
        fun url(): String = urlValue
        fun code(): Int = codeValue
        fun body(): String = bodyValue
        fun headers(): FakeHeaders = FakeHeaders(headerValues)
    }

    class FakeHeaders(private val values: Map<String, String>) {
        fun get(name: String): String? = values[name]
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private companion object {
        const val JMCOMIC_SOURCE_URL = "https://jmcomicgo.me"
        const val EXPECTED_LAST_UPDATE_TIME = 1784017318000L
        val SOURCE_FILES = listOf("shareBookSource.json", "qyyuapiBookSource.json")
    }
}
