package io.wanjuan.app.data

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.model.analyzeRule.AnalyzeByJSoup
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestUaaSourceRule {

    @Test
    fun tocRuleParsesCurrentDetailMarkup() {
        val html = """
            <div id="ndcBody" data-render-source="server">
              <a class="ndc-row" href="/novel/chapter?id=101" title="序章">
                <span class="ndc-name">序章</span><span class="ndc-acc">游客</span>
              </a>
              <a class="ndc-row" href="/novel/chapter?id=102" title="第1章 测试章节">
                <span class="ndc-name">第1章 测试章节</span><span class="ndc-acc">注册会员</span>
              </a>
            </div>
        """.trimIndent()

        assertEquals(
            listOf(
                "序章" to "/novel/chapter?id=101",
                "第1章 测试章节" to "/novel/chapter?id=102"
            ),
            parseChapters(html)
        )
    }

    @Test
    fun tocRuleKeepsLegacyCatalogMarkupCompatible() {
        val html = """
            <div class="catalog_ul">
              <li><a href="/novel/chapter?id=201">旧版第一章</a></li>
              <li><a href="/novel/chapter?id=202">旧版第二章</a></li>
            </div>
        """.trimIndent()

        assertEquals(
            listOf(
                "旧版第一章" to "/novel/chapter?id=201",
                "旧版第二章" to "/novel/chapter?id=202"
            ),
            parseChapters(html)
        )
    }

    @Test
    fun chapterRequestUsesWebView() {
        assertTrue(
            "UAA chapter requests should reuse the verified WebView session",
            uaaSource().getTocRule().chapterUrl.orEmpty().contains("\"webView\":true")
        )
    }

    @Test
    fun contentRuleParsesCurrentReaderMarkupAndRemovesCommentControls() {
        val html = """
            <div id="readerContent">
              <section class="reader-chapseg">
                <h1 class="reader-chap">第1章 测试章节</h1>
                <div class="reader-body">
                  <p data-pi="0">第一段<button class="pc-bub"><svg><path /></svg></button></p>
                  <p data-pi="1">第二段</p>
                </div>
              </section>
            </div>
        """.trimIndent()

        val output = evaluateContentRule(uaaSource().getContentRule().content.orEmpty(), html)
        val body = Jsoup.parseBodyFragment(output).body()

        assertEquals(listOf("第一段", "第二段"), body.select("p").map { it.text() })
        assertFalse(output.contains("pc-bub"))
        assertTrue(body.select("button, svg").isEmpty())
    }

    @Test
    fun contentRuleKeepsLegacyMarkupCompatible() {
        val html = """
            <div class="chapter_box">
              <div class="article"><p>旧版正文</p></div>
            </div>
        """.trimIndent()

        val output = evaluateContentRule(uaaSource().getContentRule().content.orEmpty(), html)

        assertEquals("旧版正文", Jsoup.parseBodyFragment(output).text())
    }

    private fun parseChapters(html: String): List<Pair<String, String>> {
        val rule = uaaSource().getTocRule()
        val chapterUrlElementRule = rule.chapterUrl.orEmpty().substringBefore("##")
        return AnalyzeByJSoup(html).getElements(rule.chapterList.orEmpty()).map { element ->
            val item = AnalyzeByJSoup(element)
            item.getString(rule.chapterName.orEmpty()).orEmpty() to
                item.getString(chapterUrlElementRule).orEmpty()
        }
    }

    private fun evaluateContentRule(rule: String, html: String): String {
        val script = rule.removePrefix("<js>").removeSuffix("</js>")
        val bindings = ScriptBindings().apply {
            this["result"] = html
        }
        return RhinoScriptEngine.eval(script, bindings).toString()
    }

    private fun uaaSource(): BookSource {
        val matches = GSON.fromJsonArray<BookSource>(repoFile("tests/shareBookSource.json").readText())
            .getOrThrow()
            .filter { it.bookSourceName == "UAA官网" && it.bookSourceUrl == "https://www.uaa.com" }
        assertEquals("shareBookSource.json should contain exactly one UAA source", 1, matches.size)
        return matches.single()
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
