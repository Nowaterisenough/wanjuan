package io.wanjuan.app.data

import com.google.gson.JsonParser
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.model.analyzeRule.AnalyzeByXPath
import io.wanjuan.app.model.analyzeRule.AnalyzeUrl
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class XmanContentRuleTest {

    @Test
    fun contentRuleExtractsLazyImagesWithSafeAttributeFallback() {
        val source = xmanSource()
        val html = """
            <html><body>
              <img data-src="https://outside.example/logo.jpg">
              <center><div>
                <img data-original="" data-src="//cdn.example/1.jpg">
                <img data-src="" data-original="/images/2.jpg">
                <img src="3.jpg">
                <img data-src="//cdn.example/1.jpg">
              </div></center>
            </body></html>
        """.trimIndent()

        val output = evaluateContentRule(source.getContentRule().content.orEmpty(), html)
        val urls = Jsoup.parseBodyFragment(output).select("img").map { it.attr("src") }

        assertEquals(
            listOf(
                "https://cdn.example/1.jpg",
                "https://xman7.org/images/2.jpg",
                "https://xman7.org/photos/100/3.jpg"
            ),
            urls
        )
    }

    @Test
    fun contentRuleBuildsOrderedNnpicFallbackChainsWithoutTouchingLastHost() {
        val source = xmanSource()
        val html = """
            <html><body><center><div>
              <img data-src="https://img.nnpic.xyz/upload_s/from-img.jpg?token=1">
              <img data-src="https://p9.nnpic.xyz/upload_s/from-p9.jpg#page">
              <img data-src="https://p8.nnpic.xyz/upload_s/from-p8.jpg">
              <img data-src="https://last.nnpic.xyz/upload_s/last.jpg">
              <img data-src="https://cdn.example/untouched.jpg">
            </div></center></body></html>
        """.trimIndent()

        val output = evaluateContentRule(source.getContentRule().content.orEmpty(), html)
        val models = Jsoup.parseBodyFragment(output).select("img").map { it.attr("src") }
        val analyzed = models.map(::AnalyzeUrl)

        assertEquals(
            "https://p8.nnpic.xyz/upload_s/from-img.jpg?token=1",
            analyzed[0].url
        )
        assertEquals(
            listOf("img", "p4", "p6", "p9", "p10", "p11")
                .map { "https://$it.nnpic.xyz/upload_s/from-img.jpg?token=1" },
            analyzed[0].getFallbackUrls()
        )
        assertEquals("https://p8.nnpic.xyz/upload_s/from-p9.jpg#page", analyzed[1].url)
        assertEquals("https://p9.nnpic.xyz/upload_s/from-p9.jpg#page", analyzed[1].getFallbackUrls().first())
        assertEquals(8000L, analyzed[1].getFallbackTimeout())
        assertEquals("https://p8.nnpic.xyz/upload_s/from-p8.jpg", analyzed[2].url)
        assertEquals(6, analyzed[2].getFallbackUrls().size)
        assertEquals("https://last.nnpic.xyz/upload_s/last.jpg", models[3])
        assertEquals(emptyList<String>(), analyzed[3].getFallbackUrls())
        assertEquals("https://cdn.example/untouched.jpg", models[4])
        assertEquals(emptyList<String>(), analyzed[4].getFallbackUrls())
    }

    @Test
    fun currentAndLegacyContentRulesBothExtractCurrentLazyImages() {
        val source = xmanSource()
        val commentRoot = JsonParser.parseString(source.bookSourceComment).asJsonObject
        val legacySource = commentRoot.entrySet().single().value.asJsonObject
        val legacyRule = legacySource.getAsJsonObject("contentRule").get("content").asString
        val html = """
            <html><body><center><div>
              <img data-src="//cdn.example/1.jpg">
              <img data-src="/images/2.jpg">
            </div></center></body></html>
        """.trimIndent()

        val currentOutput = evaluateContentRule(source.getContentRule().content.orEmpty(), html)
        val currentUrls = Jsoup.parseBodyFragment(currentOutput).select("img").map { it.attr("src") }
        val legacyUrls = AnalyzeByXPath(html).getStringList(legacyRule)

        assertEquals(
            listOf("https://cdn.example/1.jpg", "https://xman7.org/images/2.jpg"),
            currentUrls
        )
        assertEquals(listOf("//cdn.example/1.jpg", "/images/2.jpg"), legacyUrls)
    }

    private fun xmanSource(): BookSource {
        val matches = GSON.fromJsonArray<BookSource>(repoFile("tests/shareBookSource.json").readText())
            .getOrThrow()
            .filter { it.bookSourceName == "禁漫" && it.bookSourceUrl == XMAN_URL }
        assertEquals("shareBookSource.json should contain exactly one Xman source", 1, matches.size)
        return matches.single()
    }

    private fun evaluateContentRule(rule: String, html: String): String {
        if (!rule.startsWith("<js>") || !rule.endsWith("</js>")) {
            return AnalyzeByXPath(html).getStringList(rule).joinToString("\n")
        }
        val bindings = ScriptBindings().apply {
            this["result"] = html
            this["baseUrl"] = BASE_URL
        }
        return RhinoScriptEngine.eval(
            rule.removePrefix("<js>").removeSuffix("</js>"),
            bindings
        ).toString()
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private companion object {
        const val XMAN_URL = "https://xman7.org"
        const val BASE_URL = "https://xman7.org/photos/100/"
    }
}
