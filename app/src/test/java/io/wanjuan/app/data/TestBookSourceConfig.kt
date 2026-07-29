package io.wanjuan.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestBookSourceConfig {

    @Test
    fun shareBookSourceJsonIsUtf8WithoutBom() {
        val bytes = repoFile("tests/shareBookSource.json").readBytes()

        assertFalse(
            "shareBookSource.json should not start with UTF-8 BOM because the app import path treats it as invalid",
            bytes.take(3).toByteArray().contentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        )
    }

    @Test
    fun firstHanmanSourceUsesDirectDiscoverRules() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val source = sourceObject(sourceText, """"bookSourceName": "第一韩漫"""")
        val exploreUrl = fieldValue(source, "exploreUrl")
        val bookListRule = fieldValue(source, "bookList")
        val searchUrl = fieldValue(source, "searchUrl")

        assertTrue("第一韩漫 source should exist", source.isNotBlank())
        assertTrue("第一韩漫 should keep the legacy primary key so imports replace the old source", source.contains(""""bookSourceUrl": "https://hm8.me""""))
        assertTrue("第一韩漫 discover should stay enabled", source.contains(""""enabledExplore": true"""))
        assertTrue("第一韩漫 discover should expose the reachable latest route", exploreUrl.contains("最近更新::https://www.dymh.top/latest"))
        assertFalse("第一韩漫 should not request the site's fake 404 pagination", exploreUrl.contains("/latest/{{page}}"))
        assertTrue("第一韩漫 discover should expose rankings", exploreUrl.contains("排行榜::https://www.dymh.top/rank"))
        assertTrue("第一韩漫 discover should expose Korean comics", exploreUrl.contains("韩漫::https://www.dymh.top/zone/2.html"))
        assertTrue("第一韩漫 explore parser should read the current list-card markup", bookListRule.contains(".UpdateList .itemBox"))
        assertTrue("第一韩漫 explore parser should read homepage recommendation cards", bookListRule.contains(".imgBox li"))
        assertTrue("第一韩漫 search should use the absolute working route", searchUrl.contains("https://www.dymh.top/search/{{key}}"))
        assertFalse("第一韩漫 should not depend on remote qyyuapi rule scripts", source.contains("qyyuapi.com/sy/js/第一韩漫"))
        assertFalse("第一韩漫 should not expose stale source-switch actions", source.contains("update()"))
    }

    @Test
    fun roumanSourceUsesPublishedDomainAndCurrentMangaRules() {
        val sources = listOf(
            "shareBookSource.json" to sourceObject(
                repoFile("tests/shareBookSource.json").readText(),
                """"bookSourceName": "肉漫屋 Rouman5""""
            ),
            "qyyuapiBookSource.json" to sourceObject(
                repoFile("tests/qyyuapiBookSource.json").readText(),
                """"bookSourceName": "肉漫屋""""
            )
        )

        sources.forEach { (fileName, source) ->
            val exploreUrl = fieldValue(source, "exploreUrl")
            val searchUrl = fieldValue(source, "searchUrl")
            val contentRule = fieldValue(source, "content")
            val chapterListRule = fieldValue(source, "chapterList")

            assertTrue("$fileName 肉漫屋 source should exist", source.isNotBlank())
            assertTrue("$fileName 肉漫屋 should use the published current domain", source.contains(""""bookSourceUrl": "https://rouman5.com""""))
            assertFalse("$fileName 肉漫屋 should not keep the stale roum26 domain", source.contains("https://roum26.xyz"))
            assertTrue("$fileName 肉漫屋 discover home should use the current domain", exploreUrl.contains("https://rouman5.com/home"))
            assertTrue("$fileName 肉漫屋 discover list should use the current domain", exploreUrl.contains("https://rouman5.com/books?continued=true&page={{page}}"))
            assertTrue("$fileName 肉漫屋 search should use the current route", searchUrl.contains("https://rouman5.com/search?term={{key}}"))
            assertTrue("$fileName 肉漫屋 list parser should match the current responsive grid", source.contains("grid grid-cols-1 sm:grid-cols-4 md:grid-cols-6"))
            assertTrue("$fileName 肉漫屋 search parser should match the current search grid", source.contains("grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6"))
            assertTrue("$fileName 肉漫屋 cover parser should read background-image covers", source.contains("bg-cover"))
            assertTrue("$fileName 肉漫屋 chapter list should read current /books chapter anchors", chapterListRule.contains("starts-with(@href,'/books')"))
            assertTrue("$fileName 肉漫屋 content should read current imageUrl fields", contentRule.contains("imageUrl"))
            assertTrue("$fileName 肉漫屋 content should emit manga image tags", contentRule.contains("<img src=\\\""))
            assertTrue("$fileName 肉漫屋 should keep the sr:1 slice reorder decoder", source.contains("src.indexOf(\\\"sr:1\\\")"))
        }
    }

    @Test
    fun javdbVideoSourceIncludesLoginAndDiscoverCategories() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val javdbSource = sourceObject(sourceText, """"bookSourceUrl": "https://javdb.com/"""")
        val exploreUrl = fieldValue(javdbSource, "exploreUrl")
        val loginUrl = fieldValue(javdbSource, "loginUrl")

        assertTrue("JavDB source should exist", javdbSource.isNotBlank())
        assertTrue(loginUrl.contains("login"))
        listOf("有码", "无码", "FC2", "动漫", "排行榜").forEach {
            assertTrue("Missing JavDB primary category: $it", exploreUrl.contains(it))
        }
        listOf("censored", "uncensored", "fc2", "anime", "rankings/top").forEach {
            assertTrue("Missing JavDB primary category route: $it", exploreUrl.contains(it))
        }
        listOf("vft=4&vst=3", "vft=5&vst=3", "vft=1", "vft=2", "vft=3").forEach {
            assertTrue("Missing JavDB secondary filter: $it", exploreUrl.contains(it))
        }
        assertFalse("JavDB default categories should not include 欧美", exploreUrl.contains("western"))
    }

    @Test
    fun javdbVideoSourceUsesCurrentFilterRoutesAndExpandedTags() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val javdbSource = sourceObject(sourceText, """"bookSourceUrl": "https://javdb.com/"""")
        val exploreUrl = fieldValue(javdbSource, "exploreUrl")

        assertTrue("JavDB source should exist", javdbSource.isNotBlank())
        listOf("censored", "uncensored", "fc2", "anime").forEach {
            assertTrue("Missing JavDB primary category route: $it", exploreUrl.contains(it))
        }
        listOf("vft=0", "vft=4&vst=3", "vft=5&vst=3", "vft=1", "vft=2", "vft=3").forEach {
            assertTrue("Missing JavDB current filter route: $it", exploreUrl.contains(it))
        }
        assertTrue(exploreUrl.contains("primary.forEach"))
        assertTrue(exploreUrl.contains("filters.forEach"))
        assertTrue(exploreUrl.contains("rankTypes"))
        listOf("rankings/playback", "rankings/top").forEach {
            assertTrue("Missing JavDB ranking category: $it", exploreUrl.contains(it))
        }
        listOf("daily", "weekly", "monthly", "censored", "uncensored", "fc2", "anime").forEach {
            assertTrue("Missing JavDB ranking option: $it", exploreUrl.contains(it))
        }
        assertTrue("Missing JavDB ranking movie route", exploreUrl.contains("rankings/movies?p=${'$'}{period}&t=${'$'}{type}&page=${'$'}{page}"))
        assertFalse("JavDB discover should not include old hot tag block", exploreUrl.contains("热门 Tag"))
        assertFalse("JavDB discover should not use stale f=playable filter", exploreUrl.contains("f=playable"))
        assertFalse("JavDB discover should not use stale f=download filter", exploreUrl.contains("f=download"))
    }

    @Test
    fun javdbVideoSourceUsesSmallCoverWithoutCropDecode() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val javdbSource = sourceObject(sourceText, """"bookSourceUrl": "https://javdb.com/"""")
        val infoCoverRule = fieldValue(javdbSource, "coverUrl")
        val coverDecodeJs = fieldValue(javdbSource, "coverDecodeJs")

        assertTrue("JavDB source should exist", javdbSource.isNotBlank())
        assertTrue("JavDB should not crop covers through coverDecodeJs", coverDecodeJs.isBlank())
        assertTrue("JavDB detail should keep the small cover already captured from list/search", infoCoverRule.contains("book.coverUrl"))
        assertTrue("JavDB detail should still fall back to page thumbnails", infoCoverRule.contains(".movie-list .item img"))
        assertTrue("JavDB list/search should read thumbnail image URLs", javdbSource.contains(""""coverUrl": "img@src||img@data-src""""))
        assertFalse("JavDB small cover mode should not use BitmapFactory crop", javdbSource.contains("BitmapFactory"))
    }

    @Test
    fun javdbVideoSourceAddsClickableMagnetsToIntro() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val javdbSource = sourceObject(sourceText, """"bookSourceUrl": "https://javdb.com/"""")
        val introRule = fieldValue(javdbSource, "intro")

        assertTrue("JavDB source should exist", javdbSource.isNotBlank())
        assertTrue("JavDB intro should be generated as rich text", introRule.contains("<usehtml>"))
        assertTrue("JavDB intro should parse magnet attributes from detail HTML", introRule.contains("data-clipboard-text|href"))
        assertTrue("JavDB intro should keep magnets as vertical buttons", introRule.contains("<p><button>磁力 "))
        assertTrue("JavDB intro should not show raw magnet links", introRule.contains("<a href=\\\"").not())
        assertTrue("JavDB intro should preserve magnet URLs in button actions", introRule.contains("magnet:\\\\?xt"))
        assertTrue("JavDB intro should copy magnets when tapping a button", introRule.contains("java.copyText"))
        assertTrue("JavDB intro should open magnet apps when tapping a button", introRule.contains("java.openUrl"))
        assertTrue("JavDB intro should keep short magnet button blocks clickable without Kotlin UI changes", introRule.contains("Array(230).join('&#8203;')"))
    }

    @Test
    fun hsexVideoSourceSearchUsesPagedSearchRoute() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val hsexSource = sourceObject(sourceText, """"bookSourceUrl": "https://hsex.icu/"""")
        val searchUrl = fieldValue(hsexSource, "searchUrl")

        assertTrue("好色TV source should exist", hsexSource.isNotBlank())
        assertTrue(searchUrl.contains("search-{{page}}.htm?search={{key}}&sort=new"))
        assertFalse("好色TV search should not stay on first page", searchUrl.contains("search.htm?search={{key}}&sort=new"))
    }

    @Test
    fun jmcomicLoginCheckDoesNotInvalidateCookiesForNormalSiteScripts() {
        val sources = listOf(
            "shareBookSource.json" to sourceObject(
                repoFile("tests/shareBookSource.json").readText(),
                """"bookSourceUrl": "https://jmcomicgo.me""""
            ),
            "qyyuapiBookSource.json" to sourceObject(
                repoFile("tests/qyyuapiBookSource.json").readText(),
                """"bookSourceUrl": "https://jmcomicgo.me""""
            )
        )

        sources.forEach { (fileName, source) ->
            val loginCheckJs = fieldValue(source, "loginCheckJs").replace("\\\"", "\"")

            assertTrue("$fileName Jmcomic source should exist", source.isNotBlank())
            assertTrue("$fileName Jmcomic login check should still open browser for real verification pages", loginCheckJs.contains("startBrowserAwait"))
            assertTrue("$fileName Jmcomic login check should inspect HTTP status", loginCheckJs.contains("result.code()"))
            assertTrue("$fileName Jmcomic login check should inspect Cloudflare's challenge header", loginCheckJs.contains("cf-mitigated"))
            assertTrue("$fileName Jmcomic login check should still detect explicit verify pages", loginCheckJs.contains("isVerifyPage"))
            assertTrue("$fileName Jmcomic login check should classify login expiry", loginCheckJs.contains("isLoginExpired"))
            assertTrue("$fileName Jmcomic login check should gate body markers by challenge status", loginCheckJs.contains("hasChallengeStatus &&"))
            assertTrue("$fileName Jmcomic login check should distinguish login browser title", loginCheckJs.contains("\"登录\""))
            assertFalse("$fileName Jmcomic normal pages should not be classified by generic challenge-platform scripts", loginCheckJs.contains("|challenge-platform|"))
            assertFalse("$fileName Jmcomic login check should not clear cookies", loginCheckJs.contains("removeCookie"))
            assertFalse("$fileName Jmcomic login check should not treat normal ge_ua scripts as verification", loginCheckJs.contains("ge_ua"))
            assertFalse("$fileName Jmcomic login check should not use broad _cf_ matching", loginCheckJs.contains("/_cf_"))
        }
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private fun sourceObject(sourceText: String, marker: String): String {
        val markerIndex = sourceText.indexOf(marker)
        if (markerIndex < 0) return ""

        val start = sourceText.lastIndexOf("\n  {", markerIndex)
        val end = sourceText.indexOf("\n  }", markerIndex)
        if (start < 0 || end < 0) return ""

        return sourceText.substring(start, end + "\n  }".length)
    }

    private fun fieldValue(sourceText: String, fieldName: String): String {
        return Regex(
            """"$fieldName"\s*:\s*"((?:\\.|[^"\\])*)",""",
            RegexOption.DOT_MATCHES_ALL
        ).find(sourceText)?.groupValues?.get(1).orEmpty()
    }
}
