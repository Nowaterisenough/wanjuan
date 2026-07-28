package io.wanjuan.app.data

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.rule.BookListRule
import io.wanjuan.app.model.analyzeRule.AnalyzeByJSoup
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL

class MangaSourceRuleTest {

    @Test
    fun koreanOfficialSourceUsesReachableGetSearchRoute() {
        val source = source("韩国漫画官方")

        assertEquals("/index.php?action=search&wd={{key}}", source.searchUrl)
    }

    @Test
    fun koreanOfficialSourceParsesCurrentListMarkup() {
        val source = source("韩国漫画官方")
        assertDirectSource(source, "qyyuapi.com/sy/js/韩国漫画")
        val html = """
            <ul class="ptm-list-view">
              <li>
                <div class="pt-cover">
                  <a href="/list/42.html"><img data-original="//img.hanguomanhua.me/cover/42.jpg"></a>
                </div>
                <div class="pt-novel">
                  <div class="pt-name"><a href="/list/42.html">测试韩漫</a></div>
                  <div class="pt-desc ptm-text-cut">测试简介</div>
                  <div class="s711">
                    <div class="s712">作者:作者甲</div>
                    <div class="s713">123热度</div>
                    <div class="s713">连载中</div>
                    <div class="s713">2026-07-28</div>
                  </div>
                </div>
              </li>
            </ul>
        """.trimIndent()

        val books = parseBookList(source, source.getExploreRule(), html, KOREAN_LIST_URL)

        assertEquals(
            listOf(
                ParsedBook(
                    name = "测试韩漫",
                    author = "作者甲",
                    bookUrl = "https://www.hanguomanhua.me/list/42.html",
                    coverUrl = "https://img.hanguomanhua.me/cover/42.jpg",
                    intro = "测试简介",
                    kind = "连载中",
                    word = "2026-07-28"
                )
            ),
            books
        )
    }

    @Test
    fun koreanOfficialSourceExposesChapterPageUrls() {
        val source = source("韩国漫画官方")
        assertDirectSource(source, "qyyuapi.com/sy/js/韩国漫画")
        val html = """
            <ul id="chapterlist">
              <li><a href="/view/101.html">第1话</a></li>
              <li><a href="/view/102.html">第2话</a></li>
            </ul>
            <div class="pagelistbox">
              <select>
                <option value="/list/42_60_1.html">1</option>
                <option value="/list/42_60_2.html">2</option>
              </select>
            </div>
        """.trimIndent()
        val rule = source.getTocRule()
        val analyzeRule = AnalyzeByJSoup(html)
        val chapters = analyzeRule.getElements(rule.chapterList.orEmpty()).map { element ->
            val item = AnalyzeByJSoup(element)
            item.readString(rule.chapterName) to absoluteUrl(
                KOREAN_DETAIL_URL,
                item.readString(rule.chapterUrl)
            )
        }
        val pageUrls = analyzeRule.getStringList(rule.nextTocUrl.orEmpty())
            .map { absoluteUrl(KOREAN_DETAIL_URL, it) }

        assertEquals(
            listOf(
                "第1话" to "https://www.hanguomanhua.me/view/101.html",
                "第2话" to "https://www.hanguomanhua.me/view/102.html"
            ),
            chapters
        )
        assertEquals(
            listOf(
                "https://www.hanguomanhua.me/list/42_60_1.html",
                "https://www.hanguomanhua.me/list/42_60_2.html"
            ),
            pageUrls
        )
    }

    @Test
    fun koreanOfficialSourceFiltersLoadingImageFromContent() {
        val source = source("韩国漫画官方")
        assertDirectSource(source, "qyyuapi.com/sy/js/韩国漫画")
        val html = """
            <div class="chaptercontent">
              <img id="preloading" src="https://img.hanguomanhua.me/style2/Loading200x200.png">
              <div class="responsive-image-container"><img src="//img.hanguomanhua.me/pages/1.jpg"></div>
              <div class="responsive-image-container"><img loading="lazy" src="/pages/2.jpg"></div>
              <img src="https://img.hanguomanhua.me/style2/001.gif">
            </div>
        """.trimIndent()

        val urls = evaluateContentUrls(source, html, KOREAN_CHAPTER_URL)

        assertEquals(
            listOf(
                "https://img.hanguomanhua.me/pages/1.jpg",
                "https://www.hanguomanhua.me/pages/2.jpg"
            ),
            urls
        )
    }

    private fun parseBookList(
        source: BookSource,
        rule: BookListRule,
        html: String,
        baseUrl: String
    ): List<ParsedBook> {
        return AnalyzeByJSoup(html).getElements(rule.bookList.orEmpty()).map { element ->
            val analyzeRule = AnalyzeByJSoup(element)
            ParsedBook(
                name = analyzeRule.readString(rule.name),
                author = analyzeRule.readString(rule.author),
                bookUrl = absoluteUrl(baseUrl, analyzeRule.readString(rule.bookUrl)),
                coverUrl = absoluteUrl(baseUrl, analyzeRule.readString(rule.coverUrl)),
                intro = analyzeRule.readString(rule.intro),
                kind = analyzeRule.readString(rule.kind),
                word = analyzeRule.readString(rule.wordCount)
            )
        }
    }

    private fun evaluateContentUrls(source: BookSource, html: String, baseUrl: String): List<String> {
        val rule = source.getContentRule().content.orEmpty()
        val script = when {
            rule.startsWith("<js>") && rule.endsWith("</js>") ->
                rule.removePrefix("<js>").removeSuffix("</js>")
            rule.startsWith("@js:") -> rule.removePrefix("@js:")
            else -> error("Expected a JavaScript content rule for ${source.bookSourceName}")
        }
        val bindings = ScriptBindings().apply {
            this["result"] = html
            this["baseUrl"] = baseUrl
        }
        val output = RhinoScriptEngine.eval(script, bindings).toString()
        return output.lineSequence().mapNotNull { line ->
            Regex("""<img src="([^,"]+)""").find(line)?.groupValues?.get(1)
        }.toList()
    }

    private fun AnalyzeByJSoup.readString(rule: String?): String {
        val parts = rule.orEmpty().split("##")
        val value = getString(parts.first()).orEmpty()
        if (parts.size < 2) return value
        return Regex(parts[1]).replace(value, parts.getOrElse(2) { "" })
    }

    private fun absoluteUrl(baseUrl: String, value: String): String {
        if (value.isBlank()) return ""
        return URL(URL(baseUrl), value).toString()
    }

    private fun assertDirectSource(source: BookSource, remotePrefix: String) {
        assertFalse("${source.bookSourceName} should not depend on $remotePrefix", allRules(source).contains(remotePrefix))
    }

    private fun allRules(source: BookSource): String = listOf(
        source.bookSourceUrl,
        source.bookUrlPattern,
        source.exploreUrl,
        source.searchUrl,
        source.header,
        source.jsLib,
        source.loginUrl,
        source.loginUi,
        source.variableComment,
        GSON.toJson(source.ruleExplore),
        GSON.toJson(source.ruleSearch),
        GSON.toJson(source.ruleBookInfo),
        GSON.toJson(source.ruleToc),
        GSON.toJson(source.ruleContent)
    ).joinToString("\n")

    private fun source(name: String): BookSource {
        val matches = GSON.fromJsonArray<BookSource>(repoFile("tests/shareBookSource.json").readText())
            .getOrThrow()
            .filter { it.bookSourceName == name }
        assertEquals("shareBookSource.json should contain exactly one $name source", 1, matches.size)
        return matches.single()
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private data class ParsedBook(
        val name: String = "",
        val author: String = "",
        val bookUrl: String = "",
        val coverUrl: String = "",
        val intro: String = "",
        val kind: String = "",
        val word: String = ""
    )

    private companion object {
        const val KOREAN_LIST_URL = "https://www.hanguomanhua.me/book/1435_1.html"
        const val KOREAN_DETAIL_URL = "https://www.hanguomanhua.me/list/42.html"
        const val KOREAN_CHAPTER_URL = "https://www.hanguomanhua.me/view/101.html"
    }
}
