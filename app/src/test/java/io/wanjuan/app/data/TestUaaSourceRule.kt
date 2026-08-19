package io.wanjuan.app.data

import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.model.analyzeRule.AnalyzeByJSoup
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
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

    private fun parseChapters(html: String): List<Pair<String, String>> {
        val rule = uaaSource().getTocRule()
        return AnalyzeByJSoup(html).getElements(rule.chapterList.orEmpty()).map { element ->
            val item = AnalyzeByJSoup(element)
            item.getString(rule.chapterName.orEmpty()).orEmpty() to
                item.getString(rule.chapterUrl.orEmpty()).orEmpty()
        }
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
