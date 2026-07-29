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

class TestMangaSourceRule {

    @Test
    fun firstHanmanUpgradeReplacesLegacySourceWhileRequestingCurrentDomain() {
        val source = source("第一韩漫")
        val sourcesByPrimaryKey = linkedMapOf(
            "https://hm8.me" to BookSource(
                bookSourceUrl = "https://hm8.me",
                bookSourceName = "第一韩漫"
            )
        )

        sourcesByPrimaryKey[source.bookSourceUrl] = source

        assertEquals(
            "Importing the repaired source should replace the legacy record instead of creating a duplicate",
            1,
            sourcesByPrimaryKey.size
        )
        assertEquals("https://www.dymh.top/search/{{key}}", source.searchUrl)
        assertTrue(
            source.exploreUrl.orEmpty().lineSequence()
                .filter { it.isNotBlank() }
                .all { it.substringAfter("::").startsWith("https://www.dymh.top/") }
        )
        assertDirectSource(source, "qyyuapi.com/sy/js/第一韩漫")
    }

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

    @Test
    fun hComicKeepsStableIdentityWhileUsingCurrentDomainRoutes() {
        val source = source("H-Comic")
        assertEquals("https://www.h-comic.site", source.bookSourceUrl)
        assertTrue(source.searchUrl.orEmpty().startsWith("https://www.ikanhm.top/"))
        assertTrue(
            source.exploreUrl.orEmpty().lineSequence()
                .filter { it.isNotBlank() }
                .all { it.substringAfter("::").startsWith("https://www.ikanhm.top/") }
        )
        assertDirectSource(source, "qyyuapi.com/sy/js/爱看漫画")
        val html = """
            <ul class="manga-list-2">
              <li>
                <div class="manga-list-2-cover">
                  <a href="/book/77"><img class="manga-list-2-cover-img" data-original="//cdn.example/77.jpg"></a>
                </div>
                <p class="manga-list-2-title"><a href="/book/77">测试 H 漫</a></p>
                <p class="manga-list-2-tip">发现简介</p>
              </li>
            </ul>
        """.trimIndent()

        val books = parseBookList(source, source.getExploreRule(), html, H_COMIC_LIST_URL)

        assertEquals(
            listOf(
                ParsedBook(
                    name = "测试 H 漫",
                    bookUrl = "https://www.ikanhm.top/book/77",
                    coverUrl = "https://cdn.example/77.jpg",
                    intro = "发现简介"
                )
            ),
            books
        )
    }

    @Test
    fun hComicParsesCurrentSearchMarkup() {
        val source = source("H-Comic")
        assertDirectSource(source, "qyyuapi.com/sy/js/爱看漫画")
        val html = """
            <ul class="book-list">
              <li>
                <div class="book-list-cover">
                  <a href="/book/88"><img class="book-list-cover-img" data-original="/covers/88.jpg"></a>
                </div>
                <div class="book-list-info">
                  <p class="book-list-info-title">搜索结果</p>
                  <p class="book-list-info-desc">搜索简介</p>
                  <p class="book-list-info-bottom">
                    <span class="book-list-info-bottom-item">作者：作者乙</span>
                    <span class="book-list-info-bottom-right-font">已完结</span>
                  </p>
                </div>
              </li>
            </ul>
        """.trimIndent()

        val books = parseBookList(source, source.getSearchRule(), html, H_COMIC_SEARCH_URL)

        assertEquals(
            listOf(
                ParsedBook(
                    name = "搜索结果",
                    author = "作者乙",
                    bookUrl = "https://www.ikanhm.top/book/88",
                    coverUrl = "https://www.ikanhm.top/covers/88.jpg",
                    intro = "搜索简介",
                    kind = "已完结"
                )
            ),
            books
        )
    }

    @Test
    fun hComicParsesCurrentDetailAndTocMarkup() {
        val source = source("H-Comic")
        assertDirectSource(source, "qyyuapi.com/sy/js/爱看漫画")
        val html = """
            <div class="detail-main-cover"><img data-original="/covers/99.jpg"></div>
            <p class="detail-main-info-title">详情漫画</p>
            <p class="detail-main-info-author">图文：作者丙</p>
            <div class="detail-main-info-class"><a>都市</a><a>韩国</a></div>
            <p class="detail-desc">详情简介</p>
            <ul class="detail-list-1">
              <li><a href="/chapter/501">第1话</a></li>
              <li><a href="/chapter/502">第2话</a></li>
            </ul>
        """.trimIndent()
        val infoRule = source.getBookInfoRule()
        val info = AnalyzeByJSoup(html)
        val tocRule = source.getTocRule()
        val chapters = AnalyzeByJSoup(html)
            .getElements(tocRule.chapterList.orEmpty())
            .map { element ->
                val item = AnalyzeByJSoup(element)
                item.readString(tocRule.chapterName) to absoluteUrl(
                    H_COMIC_DETAIL_URL,
                    item.readString(tocRule.chapterUrl)
                )
            }

        assertEquals("详情漫画", info.readString(infoRule.name))
        assertEquals("作者丙", info.readString(infoRule.author))
        assertEquals(
            "https://www.ikanhm.top/covers/99.jpg",
            absoluteUrl(H_COMIC_DETAIL_URL, info.readString(infoRule.coverUrl))
        )
        assertEquals("详情简介", info.readString(infoRule.intro))
        assertEquals("都市\n韩国", info.readString(infoRule.kind))
        assertEquals(
            listOf(
                "第1话" to "https://www.ikanhm.top/chapter/501",
                "第2话" to "https://www.ikanhm.top/chapter/502"
            ),
            chapters
        )
    }

    @Test
    fun hComicExtractsOnlyMangaImages() {
        val source = source("H-Comic")
        assertDirectSource(source, "qyyuapi.com/sy/js/爱看漫画")
        val html = """
            <img class="lazy" data-original="/static/images/logo.png">
            <img class="lazy" data-original="//cdn.example/pages/1.jpg">
            <img class="lazy" data-original="" data-fallback="/pages/2.jpg">
            <img class="lazy" src="loading.gif">
            <img class="lazy" data-original="//cdn.example/pages/1.jpg">
        """.trimIndent()

        val urls = evaluateContentUrls(source, html, H_COMIC_CHAPTER_URL)

        assertEquals(
            listOf(
                "https://cdn.example/pages/1.jpg",
                "https://www.ikanhm.top/pages/2.jpg"
            ),
            urls
        )
    }

    @Test
    fun hComicKeepsMangaImagesHostedUnderStaticUploadPath() {
        val source = source("H-Comic")
        val html = """
            <div class="view-main-1 readForm" id="cp_img">
              <img class="lazy" data-original="https://www.jjmhw6.top/static/upload/book/44/747/15989.jpg">
              <img class="lazy" data-original="https://www.jjmhw6.top/static/upload/book/44/747/15996.jpg">
            </div>
        """.trimIndent()

        val urls = evaluateContentUrls(source, html, H_COMIC_CHAPTER_URL)

        assertEquals(
            listOf(
                "https://www.jjmhw6.top/static/upload/book/44/747/15989.jpg",
                "https://www.jjmhw6.top/static/upload/book/44/747/15996.jpg"
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
        const val H_COMIC_LIST_URL = "https://www.ikanhm.top/booklist?page=1"
        const val H_COMIC_SEARCH_URL = "https://www.ikanhm.top/search?keyword=test"
        const val H_COMIC_DETAIL_URL = "https://www.ikanhm.top/book/99"
        const val H_COMIC_CHAPTER_URL = "https://www.ikanhm.top/chapter/501"
    }
}
