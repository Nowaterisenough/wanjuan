package io.wanjuan.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookSourceConfigTest {

    @Test
    fun shareBookSourceJsonIsUtf8WithoutBom() {
        val bytes = repoFile("tests/shareBookSource.json").readBytes()

        assertFalse(
            "shareBookSource.json should not start with UTF-8 BOM because the app import path treats it as invalid",
            bytes.take(3).toByteArray().contentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        )
    }

    @Test
    fun uaaSourceDetectsCloudflareAndOpensBrowserVerification() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val uaaSource = sourceObject(sourceText, """"bookSourceUrl": "https://www.uaa.com"""")
        val loginCheckJs = fieldValue(uaaSource, "loginCheckJs")

        assertTrue("UAA source should exist", uaaSource.isNotBlank())
        assertTrue(uaaSource.contains(""""concurrentRate": "1/3000""""))
        assertFalse("UAA loginCheckJs should not be blank", loginCheckJs.isBlank())
        assertTrue(loginCheckJs.contains("startBrowserAwait"))
        assertFalse(loginCheckJs.contains("removeCookie"))
        assertTrue(loginCheckJs.contains("cf-mitigated"))
        assertTrue(loginCheckJs.contains("_cf_chl_opt"))
        assertTrue(loginCheckJs.contains("cf_chl"))
        assertTrue(loginCheckJs.contains("Just a moment"))
        assertTrue(loginCheckJs.contains("_cloudflare_browser_attempted"))
        assertTrue(loginCheckJs.contains("cache.getFromMemory(verifyKey)"))
        assertTrue(loginCheckJs.contains("cache.putMemory(verifyKey, \"attempted\")"))
        assertTrue(loginCheckJs.contains("cache.putMemory(verifyKey, stillCloudflare ? \"failed\" : \"success\")"))
        assertTrue(
            "UAA source should return the displayed WebView page instead of refetching after verification",
            loginCheckJs.contains("startBrowserAwait(url, \\\"Cloudflare\\\", false)")
        )
    }

    @Test
    fun uaaBookInfoCoverUsesLazyImageAndFallsBackToListCover() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val uaaSource = sourceObject(sourceText, """"bookSourceUrl": "https://www.uaa.com"""")
        val coverUrlRule = fieldValue(uaaSource, "coverUrl")

        assertTrue("UAA source should exist", uaaSource.isNotBlank())
        assertTrue(coverUrlRule.contains("data-src"))
        assertTrue(coverUrlRule.contains("data-original"))
        assertTrue(coverUrlRule.contains("book.coverUrl"))
        assertTrue(coverUrlRule.contains("data:image"))
        assertTrue(coverUrlRule.contains("placeholder"))
        assertTrue(coverUrlRule.contains("src ||"))
        assertTrue(coverUrlRule.contains("og:image"))
        assertTrue(coverUrlRule.contains("coverUrl"))
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
    fun xmanSourceAddsNewMangaReleaseExploreCategory() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val xmanSource = sourceObject(sourceText, """"bookSourceUrl": "https://xman7.org"""")
        val exploreUrl = fieldValue(xmanSource, "exploreUrl")
        val bookListRule = fieldValue(xmanSource, "bookList")

        assertTrue("禁漫 source should exist", xmanSource.isNotBlank())
        assertTrue("禁漫 discover should include 新漫发布 category", exploreUrl.contains("新漫发布"))
        assertTrue("禁漫 新漫发布 should point to /nm", exploreUrl.contains("https://xman7.org/nm"))
        assertTrue("禁漫 explore parser should detect the /nm page", bookListRule.contains("/nm"))
        assertTrue("禁漫 新漫发布 should parse only the second /nm section", bookListRule.contains("sections.get(1)"))
        assertFalse("禁漫 explore parser should not depend on unavailable java.net.URL", bookListRule.contains("java.net.URL"))
    }

    @Test
    fun xmanSourceCollectsEveryLazyChapterImage() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val xmanSource = sourceObject(sourceText, """"bookSourceUrl": "https://xman7.org"""")
        val contentRule = fieldValue(xmanSource, "content")

        assertTrue("Xman source should exist", xmanSource.isNotBlank())
        assertTrue("Xman content should parse the chapter document", contentRule.contains("org.jsoup.Jsoup.parse(result)"))
        assertTrue("Xman content should read lazy image URLs", contentRule.contains("data-original"))
        assertTrue("Xman content should preserve all matching images", contentRule.contains("urls.push"))
        assertTrue("Xman content should return image tags for every collected URL", contentRule.contains("urls.map(function(u)"))
        assertTrue("Xman content should include image request headers", contentRule.contains("JSON.stringify(headers)"))
        assertFalse("Xman content should not keep the old single XPath image rule", contentRule.contains("//body/div[2]/center[3]/div/img"))
    }

    @Test
    fun jmcomicSourceBuildsEveryReaderImageFromPageArr() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val jmcomicSource = sourceObject(sourceText, """"bookSourceUrl": "https://jmcomicgo.me"""")
        val contentRule = fieldValue(jmcomicSource, "content")

        assertTrue("Jmcomic source should exist", jmcomicSource.isNotBlank())
        assertTrue("Jmcomic content should read the reader page array", contentRule.contains("page_arr"))
        assertTrue("Jmcomic content should derive the CDN photo root from reader images", contentRule.contains("/media/photos/"))
        assertTrue("Jmcomic content should build image URLs from every page_arr file", contentRule.contains("root + file"))
        assertTrue("Jmcomic content should still fall back to DOM image parsing", contentRule.contains(".scramble-page img[data-original]"))
        assertTrue("Jmcomic content should include image request headers", contentRule.contains("JSON.stringify(headers)"))
        assertFalse("Jmcomic content should not keep the old album thumbnail selector", contentRule.contains("thumb-overlay-albums"))
    }

    @Test
    fun missavActressListStaysInSettingsAndLoadsMoreFromSelectScroll() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val missavSource = sourceObject(sourceText, """"bookSourceUrl": "https://missav.ws/"""")
        val exploreUrl = fieldValue(missavSource, "exploreUrl")

        assertTrue("MissAV source should exist", missavSource.isNotBlank())
        assertTrue("MissAV actress selector should be a settings select", exploreUrl.contains("\\\"type\\\": \\\"select\\\""))
        assertTrue("MissAV actress selector should declare dropdown load-more behavior", exploreUrl.contains("loadMoreAction"))
        assertTrue("MissAV actress list should be parsed from the site instead of staying fixed", exploreUrl.contains("loadMissAvActressPage"))
        assertTrue("MissAV should keep the general actress list entry", exploreUrl.contains("path('/actresses')"))
        assertTrue("MissAV should keep the actress ranking entry", exploreUrl.contains("path('/actresses/ranking')"))
        assertFalse(
            "MissAV should not emit every actress as a discovery category",
            exploreUrl.contains("Object.keys(actressMap).forEach")
        )
        assertFalse(
            "MissAV should not group the actress selector as a discovery category section",
            exploreUrl.contains("heading('女优')")
        )
    }

    @Test
    fun rowUiSelectSupportsDropdownLoadMoreActions() {
        val rowUi = repoFile("app/src/main/java/io/wanjuan/app/data/entities/rule/RowUi.kt").readText()
        val exploreKind = repoFile("app/src/main/java/io/wanjuan/app/data/entities/rule/ExploreKind.kt").readText()
        val rowUiForm = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/RowUiForm.kt").readText()
        val rowUiViewFactory = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/RowUiViewFactory.kt").readText()
        val rowUiDialog = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/RowUiDialog.kt").readText()
        val exploreFragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val sourceLoginDialog = repoFile("app/src/main/java/io/wanjuan/app/ui/login/SourceLoginDialog.kt").readText()

        assertTrue("RowUi should carry select load-more JavaScript", rowUi.contains("loadMoreAction"))
        assertTrue("ExploreKind should carry select load-more JavaScript", exploreKind.contains("loadMoreAction"))
        assertTrue("RowUiForm should expose load-more callbacks", rowUiForm.contains("fun onLoadMore(rowUi: RowUi)"))
        assertTrue("RowUiForm should forward select load-more callbacks", rowUiForm.contains("callback.onLoadMore(rowUi)"))
        assertTrue("RowUiViewFactory should accept a select load-more callback", rowUiViewFactory.contains("onLoadMore"))
        assertTrue("RowUiViewFactory should trigger load-more while rendering the dropdown tail", rowUiViewFactory.contains("position == count - 1"))
        assertTrue("RowUiDialog should surface load-more events", rowUiDialog.contains("fun onLoadMore(rowUi: RowUi)"))
        assertTrue("Discovery settings should execute load-more actions", exploreFragment.contains("handleDiscoverSelectLoadMore"))
        assertTrue("Source login settings should execute load-more actions", sourceLoginDialog.contains("rowUi.loadMoreAction"))
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
