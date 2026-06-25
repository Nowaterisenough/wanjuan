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
    fun firstHanmanSourceUsesDirectDiscoverRules() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val source = sourceObject(sourceText, """"bookSourceName": "第一韩漫"""")
        val exploreUrl = fieldValue(source, "exploreUrl")
        val bookListRule = fieldValue(source, "bookList")
        val searchUrl = fieldValue(source, "searchUrl")

        assertTrue("第一韩漫 source should exist", source.isNotBlank())
        assertTrue("第一韩漫 should use the currently reachable direct domain", source.contains(""""bookSourceUrl": "https://www.dymh.top""""))
        assertTrue("第一韩漫 discover should stay enabled", source.contains(""""enabledExplore": true"""))
        assertTrue("第一韩漫 discover should expose recently updated comics", exploreUrl.contains("最近更新::/latest/{{page}}"))
        assertTrue("第一韩漫 discover should expose rankings", exploreUrl.contains("排行榜::/rank"))
        assertTrue("第一韩漫 discover should expose Korean comics", exploreUrl.contains("韩漫::/zone/2.html"))
        assertTrue("第一韩漫 explore parser should read the current list-card markup", bookListRule.contains(".UpdateList .itemBox"))
        assertTrue("第一韩漫 explore parser should read homepage recommendation cards", bookListRule.contains(".imgBox li"))
        assertTrue("第一韩漫 search should use the working path route", searchUrl.contains("/search/{{key}}"))
        assertFalse("第一韩漫 should not depend on remote qyyuapi rule scripts", source.contains("qyyuapi.com/sy/js/第一韩漫"))
        assertFalse("第一韩漫 should not keep the stale hm8 source domain", source.contains("https://hm8.me"))
    }

    @Test
    fun nnhanmanSourceUsesLiveDomainAndCurrentMangaRules() {
        val sourceText = repoFile("tests/shareBookSource.json").readText()
        val source = sourceObject(sourceText, """"bookSourceName": "鸟鸟韩漫 NNHanman"""")
        val contentRule = fieldValue(source, "content")
        val chapterListRule = fieldValue(source, "chapterList")
        val chapterUrlRule = fieldValue(source, "chapterUrl")

        assertTrue("鸟鸟韩漫 source should exist", source.isNotBlank())
        assertTrue("鸟鸟韩漫 should use the reachable current domain", source.contains(""""bookSourceUrl": "https://nnhm7.com""""))
        assertFalse("鸟鸟韩漫 should not keep the reset nnhanman9 domain", source.contains("https://nnhanman9.com"))
        assertFalse("鸟鸟韩漫 discover parser should not be empty", source.contains(""""ruleExplore": {}"""))
        assertTrue("鸟鸟韩漫 discover should read update and ranking list cards", source.contains(""""bookList": ".UpdateList .itemBox||.col_3_1@li""""))
        assertTrue("鸟鸟韩漫 list parser should read image srcset fallbacks", source.contains("source@srcset||img@data-original||img@src"))
        assertTrue("鸟鸟韩漫 chapter urls should follow the current source domain", chapterUrlRule.contains("source.getKey()"))
        assertTrue("鸟鸟韩漫 chapter list should read the current anchor nodes", chapterListRule.contains("#mh-chapter-list-ol-0 li a"))
        assertTrue("鸟鸟韩漫 chapter url should coerce source key to a JS string before regex replace", chapterUrlRule.contains("String(source.getKey()).replace"))
        assertFalse("鸟鸟韩漫 chapter url should avoid Rhino Java String.replace overload ambiguity", chapterUrlRule.contains("source.getKey().replace"))
        assertTrue("鸟鸟韩漫 content should read lazy reader images", contentRule.contains(".view-imgBox img"))
        assertTrue("鸟鸟韩漫 content should read data-original image urls", contentRule.contains("data-original"))
        assertTrue("鸟鸟韩漫 content should return reader image tags instead of plain URL lines", contentRule.contains("<img src=\\\""))
        assertTrue("鸟鸟韩漫 content should include image request headers", contentRule.contains("JSON.stringify(headers)"))
        assertFalse("鸟鸟韩漫 content should not keep the old tbody rule", contentRule.contains("tbody@all"))
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
        assertTrue("MissAV actress pages should wait in WebView until actress links are rendered", exploreUrl.contains("webViewGetSource(null, target, waitJs, '', false, 6000)"))
        assertTrue("MissAV actress WebView fallback should use the existing public script API", exploreUrl.contains("java.webView(null, target, waitJs, false)"))
        assertTrue("MissAV actress WebView fetch should keep the populated-list threshold for unfiltered pages", exploreUrl.contains("const minMatches = hasActiveActressListFilter() ? 1 : 6"))
        assertTrue("MissAV actress WebView fetch should wait for the route count required by the current filter state", exploreUrl.contains("matches.length >= \" + minMatches"))
        assertTrue("MissAV actress render fallback should fall back to manual verification when rendering still has no populated list", exploreUrl.contains("verifiedActressHtml(target, true)"))
        assertTrue("MissAV browser verification should read confirmed WebView HTML as a plain string", exploreUrl.contains("java.startBrowserAwaitBody"))
        assertTrue("MissAV browser verification should read StrResponse through the Java getter when Rhino does not expose body()", exploreUrl.contains("response.getBody()"))
        assertTrue("MissAV load-more should retry page 1 while no real actress cache exists", exploreUrl.contains("hasRealActressCacheMap(actressMap) ? Math.max"))
        assertFalse("MissAV discovery tags should not block on actress page loading when the actress cache is empty", exploreUrl.contains("Object.keys(actressMap).length < 6"))
        assertTrue("MissAV stale end markers should not disable load-more before a real actress cache exists", exploreUrl.contains("hasRealActressCache"))
        assertTrue("MissAV should replace stale fallback actress slugs once a real actress route is parsed", exploreUrl.contains("hasUsableActressRoute(map[name])"))
        assertTrue("MissAV actress parsing should keep the full dynamic actress route prefix", exploreUrl.contains("normalizeMissAvPath(href)"))
        assertTrue("MissAV actress parser should scan all links so prefixed actress routes are accepted", exploreUrl.contains("doc.select('a[href]')"))
        assertTrue("MissAV actress parser should fall back to the same rendered route collector used by populated-list checks", exploreUrl.contains("collectActressRoutesHtml(html).forEach"))
        assertTrue("MissAV rendered HTML route fallback should not greedily swallow absolute URL paths", exploreUrl.contains("https?:\\/\\/[^\\/\\s\"'<>]+"))
        assertFalse("MissAV rendered HTML route fallback should not keep the old greedy absolute URL prefix", exploreUrl.contains("https?:\\/\\/[^\\s\"'<>]+)?((?:\\/[a-z0-9]+)?\\/actresses\\/"))
        assertTrue("MissAV HTML route fallback should derive actress names from the route slug", exploreUrl.contains("putActress(map, '', route)"))
        val countActressRoutesBody = exploreUrl.substringAfter("function collectActressRoutesHtml(html)")
            .substringBefore("function countActressRoutesHtml(html)")
        assertTrue("MissAV populated-list check should count rendered actress anchors with Jsoup", countActressRoutesBody.contains("doc.select('a[href]')"))
        assertTrue("MissAV populated-list check should normalize anchor hrefs before counting", countActressRoutesBody.contains("links.get(i).attr('href')"))
        assertTrue("MissAV populated-list check should collect routes into an array before counting", exploreUrl.contains("function collectActressRoutesHtml(html)"))
        assertFalse("MissAV populated-list check should not rely on Object.keys(seen).length in Rhino", countActressRoutesBody.contains("Object.keys(seen).length"))
        assertFalse(
            "MissAV populated-list check should avoid redeclaring const route because Rhino treats it as one function-scope binding",
            countActressRoutesBody.indexOf("const route =") != countActressRoutesBody.lastIndexOf("const route =")
        )
        assertTrue("MissAV actress names should drop site metadata from actress cards", exploreUrl.contains("條影片"))
        assertTrue("MissAV actress page loading should retry rendering when Ajax returns a shell without a populated actress list", exploreUrl.contains("!hasPopulatedActressListHtml(html)"))
        assertTrue("MissAV should not mark actress pagination ended when WebView still returns a Cloudflare shell", exploreUrl.contains("!isCloudflareOrShell(html)"))
        assertTrue("MissAV should ignore stale route cache entries that point /actresses at one actress page", exploreUrl.contains("normalizeRouteMap"))
        assertTrue("MissAV route cache should only keep entries ending exactly at the route path", exploreUrl.contains("value.endsWith(route)"))
        assertTrue("MissAV should not parse a single accidental actress link as a populated actress list", exploreUrl.contains("hasPopulatedActressListHtml(html)"))
        assertFalse("MissAV actress dropdown should not include hard-coded fallback actress names", exploreUrl.contains("fallbackActressNames"))
        assertFalse("MissAV actress dropdown should not merge hard-coded fallback choices", exploreUrl.contains("mergeFallbackActresses"))
        assertFalse("MissAV actress dropdown should not contain hard-coded actress names", listOf("三上悠亚", "河北彩花", "桥本有菜").any { exploreUrl.contains("'$it'") })
        assertTrue("MissAV actress dropdown choices should come from the parsed actress map", exploreUrl.contains("const actressChoices = ['全部'].concat(Object.keys(actressMap))"))
        assertTrue("MissAV typed actress search should still use the site search route", exploreUrl.contains("searchActressRoute"))
        assertTrue("MissAV should expose actress search as a discovery settings text field", exploreUrl.contains("const actressSearchKey = '搜索女优'"))
        assertTrue("MissAV should render a text input for actress search", exploreUrl.contains("\\\"type\\\": \\\"text\\\""))
        assertTrue("MissAV actress search should generate a works category from the typed keyword", exploreUrl.contains("searchActressRoute(searchedActress)"))
        assertFalse("MissAV settings should not expose a visible actress refresh button", exploreUrl.contains("刷新女优列表"))
        assertTrue("MissAV should expose the site actress sort setting", exploreUrl.contains("const actressSortKey = '女优排序'"))
        assertTrue("MissAV should expose the site actress height filter", exploreUrl.contains("const actressHeightKey = '身高筛选'"))
        assertTrue("MissAV should expose the site actress cup filter", exploreUrl.contains("const actressCupKey = '罩杯筛选'"))
        assertTrue("MissAV should expose the site actress age filter", exploreUrl.contains("const actressAgeKey = '年龄筛选'"))
        assertTrue("MissAV should expose the site actress debut filter", exploreUrl.contains("const actressDebutKey = '出道筛选'"))
        assertTrue("MissAV actress sort should support the web videos sort", exploreUrl.contains("'影片': 'videos'"))
        assertTrue("MissAV actress sort should support the web debut sort", exploreUrl.contains("'出道': 'debut'"))
        assertTrue("MissAV actress height filter should include the web height range parameter", exploreUrl.contains("'151-155cm': '151-155'"))
        assertTrue("MissAV actress cup filter should include the web cup parameter", exploreUrl.contains("'F': 'F'"))
        assertTrue("MissAV actress age filter should include the web age range parameter", exploreUrl.contains("'20-30岁': '20-30'"))
        assertTrue("MissAV actress debut filter should include the web debut year parameter", exploreUrl.contains("actressDebutValues[String(year)] = String(year)"))
        assertTrue("MissAV should compose actress list query parameters from settings", exploreUrl.contains("function buildActressListRoute()"))
        assertTrue("MissAV actress list route should use the web sort parameter", exploreUrl.contains("addQueryParam(params, 'sort',"))
        assertTrue("MissAV actress list route should use the web height parameter", exploreUrl.contains("addQueryParam(params, 'height',"))
        assertTrue("MissAV actress list route should use the web cup parameter", exploreUrl.contains("addQueryParam(params, 'cup',"))
        assertTrue("MissAV actress list route should use the web age parameter", exploreUrl.contains("addQueryParam(params, 'age',"))
        assertTrue("MissAV actress list route should use the web debut parameter", exploreUrl.contains("addQueryParam(params, 'debut',"))
        assertTrue("MissAV should detect active actress list filters before applying the populated-list threshold", exploreUrl.contains("function hasActiveActressListFilter()"))
        assertTrue("MissAV filtered actress pages should accept short valid result lists", exploreUrl.contains("function hasFilteredActressListHtml(html)"))
        assertTrue("MissAV rendered actress HTML should reuse the filtered short-list check", exploreUrl.contains("function hasUsableActressListHtml(html)"))
        assertTrue("MissAV rendered actress HTML should return filtered short lists without forcing manual verification", exploreUrl.contains("!isCloudflareOrShell(html) && hasUsableActressListHtml(html)"))
        assertTrue("MissAV filtered actress pages should parse even when fewer than six actress links are returned", exploreUrl.contains("const canParseActressList = hasPopulatedList || hasFilteredList"))
        assertTrue("MissAV filtered actress parser should still ignore empty filtered pages", exploreUrl.contains("hasActiveActressListFilter() && countActressRoutesHtml(html) > 0"))
        assertTrue("MissAV general actress list entry should honor actress list settings", exploreUrl.contains("push('女优列表', path(buildActressListRoute()), 0.25)"))
        assertFalse("MissAV active actress filters should not create transient discovery groups that hide stable entries", exploreUrl.contains("function pushActressFilterResultGroups()"))
        assertFalse("MissAV active actress filters should not switch the page to generated setting-value groups", exploreUrl.contains("heading(key + '：' + value)"))
        assertFalse("MissAV should keep filtered actress results under the stable entrance group", exploreUrl.contains("pushActressFilterResultGroups();"))
        assertTrue("MissAV dynamic actress dropdown loading should honor actress list settings", exploreUrl.contains("const actressRoute = buildActressListRoute()"))
        assertTrue("MissAV filter changes should invalidate the stale actress dropdown cache", exploreUrl.contains("const invalidateActressFilterAction"))
        assertTrue("MissAV should automatically invalidate stale actress cache when saved filters changed before this source update", exploreUrl.contains("resetActressCacheForFilter();"))
        assertTrue("MissAV should persist the current filter signature with the actress cache", exploreUrl.contains("const actressFilterKey = 'MissAV女优筛选'"))
        assertTrue("MissAV sort filter should refresh cached actress choices when changed", exploreUrl.contains("select(actressSortKey, actressSortOptions, '影片', null, invalidateActressFilterAction)"))
        assertTrue("MissAV height filter should refresh cached actress choices when changed", exploreUrl.contains("select(actressHeightKey, actressHeightOptions, '全部', null, invalidateActressFilterAction)"))
        assertTrue("MissAV cup filter should refresh cached actress choices when changed", exploreUrl.contains("select(actressCupKey, actressCupOptions, '全部', null, invalidateActressFilterAction)"))
        assertTrue("MissAV age filter should refresh cached actress choices when changed", exploreUrl.contains("select(actressAgeKey, actressAgeOptions, '全部', null, invalidateActressFilterAction)"))
        assertTrue("MissAV debut filter should refresh cached actress choices when changed", exploreUrl.contains("select(actressDebutKey, actressDebutOptions, '全部', null, invalidateActressFilterAction)"))
        assertTrue("MissAV should read discovery state through an explicit InfoMap getter", exploreUrl.contains("function getInfo(key)"))
        assertTrue("MissAV should write discovery state through InfoMap.put instead of JS property assignment", exploreUrl.contains("function setInfo(key, value)"))
        val getInfoBody = exploreUrl.substringAfter("function getInfo(key)")
            .substringBefore("function setInfo(key, value)")
        assertFalse(
            "MissAV getInfo should avoid redeclaring const value because Rhino treats it as one function-scope binding",
            getInfoBody.contains("const value = infoMap.get") && getInfoBody.contains("const value = infoMap[String(key)]")
        )
        val responseBodyFunction = exploreUrl.substringAfter("function responseBody(response)")
            .substringBefore("function verifiedActressHtml(target, manual)")
        assertFalse(
            "MissAV responseBody should avoid redeclaring const body because Rhino treats it as one function-scope binding",
            responseBodyFunction.indexOf("const body =") != responseBodyFunction.lastIndexOf("const body =")
        )
        assertTrue("MissAV actress state changes should be persisted from discovery settings", exploreUrl.contains("saveInfoMapAction"))
        assertFalse("MissAV actress dropdown should not show a fake load-more option", exploreUrl.contains("加载更多..."))
        assertFalse("MissAV actress dropdown should not keep a hidden fake load-more placeholder", exploreUrl.contains("actressLoadMoreLabel"))
        assertTrue("MissAV actress load-more should use an explicit manual marker before discovery refreshes settings rows", exploreUrl.contains("setInfo('\" + actressNeedMoreKey + \"', 'manual');"))
        assertFalse("MissAV filter changes should not force synchronous actress loading while rebuilding discovery tags", exploreUrl.contains("setInfo('\" + actressNeedMoreKey + \"', '1');"))
        assertFalse("MissAV source should not keep the abandoned forced-verification switch", exploreUrl.contains("actressForceVerifyKey"))
        assertFalse("MissAV source should not keep temporary actress diagnostics", exploreUrl.contains("debugActress"))
        assertFalse("MissAV source should not log temporary actress diagnostics", exploreUrl.contains("MissAV女优诊断"))
        assertTrue("MissAV selected actress should create one dynamic category group", exploreUrl.contains("heading('女优：' + selectedActress)"))
        assertTrue("MissAV selected actress category should use the stored full route", exploreUrl.contains("path(actressMap[selectedActress])"))
        assertFalse("MissAV selected actress category should not rebuild stale /actresses/name paths", exploreUrl.contains("path('/actresses/' + actressMap[selectedActress])"))
        assertTrue("MissAV should keep the general actress list entry", exploreUrl.contains("routePath('/actresses')"))
        assertTrue("MissAV should keep the actress ranking entry", exploreUrl.contains("routePath('/actresses/ranking')"))
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
        val bookSourceExtensions = repoFile("app/src/main/java/io/wanjuan/app/help/source/BookSourceExtensions.kt").readText()
        val jsExtensions = repoFile("app/src/main/java/io/wanjuan/app/help/JsExtensions.kt").readText()

        assertTrue("RowUi should carry select load-more JavaScript", rowUi.contains("loadMoreAction"))
        assertTrue("ExploreKind should carry select load-more JavaScript", exploreKind.contains("loadMoreAction"))
        assertTrue("RowUiForm should expose load-more callbacks", rowUiForm.contains("fun onLoadMore(rowUi: RowUi)"))
        assertTrue("RowUiForm should let load-more callbacks update the open selector options", rowUiForm.contains("updateOptions: (List<String>, String?) -> Unit"))
        assertTrue("RowUiForm should forward select load-more callbacks", rowUiForm.contains("callback.onLoadMore(rowUi, updateOptions)"))
        assertTrue("RowUiViewFactory should accept a select load-more callback", rowUiViewFactory.contains("onLoadMore"))
        assertTrue("RowUiViewFactory should use a custom popup for load-more selects", rowUiViewFactory.contains("PopupWindow("))
        assertTrue("RowUiViewFactory should render load-more selects as a scrollable list", rowUiViewFactory.contains("ListView(parent.context)"))
        assertTrue("RowUiViewFactory should trigger load-more from real dropdown scroll state", rowUiViewFactory.contains("setOnScrollListener"))
        assertTrue("RowUiViewFactory should request more when the visible tail reaches the option count", rowUiViewFactory.contains("firstVisibleItem + visibleItemCount >= totalItemCount"))
        assertTrue("RowUiViewFactory should retry load-more after short dropdowns finish layout", rowUiViewFactory.contains("postDelayed({ requestMoreIfAtBottom() }, 250L)"))
        assertTrue("RowUiViewFactory should update select options in place after load-more", rowUiViewFactory.contains("fun updateOptions"))
        assertTrue("RowUiViewFactory should select from the updated adapter data", rowUiViewFactory.contains("adapter.getItem(position)"))
        assertTrue("RowUiViewFactory should calculate the initial selection from normalized dropdown options", rowUiViewFactory.contains("options.indexOf(selectedValue ?: rowUi.default ?: \"\")"))
        assertTrue("RowUiViewFactory custom popup selection should call the same value handler as the spinner", rowUiViewFactory.contains("fun selectOption(value: String)"))
        assertTrue("RowUiViewFactory custom popup should suppress duplicate spinner callbacks after manual selection", rowUiViewFactory.contains("suppressNextSelectionCallback"))
        assertTrue("RowUiViewFactory load-more popup should use an opaque themed surface", rowUiViewFactory.contains("dialogSurfaceBackground"))
        assertTrue("RowUiViewFactory dropdown list should use an explicit opaque color layer", rowUiViewFactory.contains("ColorDrawable(ContextCompat.getColor(context, R.color.dialog_surface))"))
        assertFalse("RowUiViewFactory load-more popup should not render with a transparent background", rowUiViewFactory.contains("ColorDrawable(android.graphics.Color.TRANSPARENT)"))
        assertTrue("RowUiDialog should surface load-more events", rowUiDialog.contains("fun onLoadMore(rowUi: RowUi)"))
        assertTrue("RowUiDialog should let load-more callbacks update an open selector", rowUiDialog.contains("updateOptions: (List<String>, String?) -> Unit"))
        assertTrue("Discovery settings should include text inputs from exploreUrl", exploreFragment.contains("kind.type == ExploreKind.Type.text"))
        assertTrue("Discovery settings should update text inputs into infoMap", exploreFragment.contains("handleDiscoverTextValue"))
        assertTrue("Discovery settings should execute load-more actions", exploreFragment.contains("handleDiscoverSelectLoadMore"))
        assertTrue("Discovery settings should keep loaded select options inside the current settings UI", exploreFragment.contains("updatedItem.toDiscoverRowUi().chars"))
        assertTrue("Discovery settings should route non-default filter selections to the stable entrance group", exploreFragment.contains("preferredDiscoverEntranceGroup(item)"))
        assertTrue("Discovery settings should recognize filter and sort controls as entrance-group settings", exploreFragment.contains("title.endsWith(\"筛选\")") && exploreFragment.contains("title.endsWith(\"排序\")"))
        assertTrue("Discovery settings should prefer the source entrance group after filter changes", exploreFragment.contains("discoverMajorGroups.firstOrNull { it == \"入口\" }"))
        assertTrue("Discovery settings buttons should keep the dialog open while refreshing dynamic rows", exploreFragment.contains("dismissOnAction = false"))
        assertTrue("Discovery settings button refresh should reopen the dialog with regenerated rows", exploreFragment.contains("refreshDiscoverSettingsDialog"))
        assertTrue("Discovery settings should persist infoMap changes made by dynamic actions", exploreFragment.contains("persistDiscoverInfoMap(infoMap)"))
        assertTrue("Source login settings should execute load-more actions", sourceLoginDialog.contains("rowUi.loadMoreAction"))
        assertTrue("Dynamic discovery settings need a java bridge while building explore kinds", bookSourceExtensions.contains("ExploreKindsJsExtensions"))
        assertTrue("Dynamic discovery settings should let exploreUrl scripts call java.ajax/java.webView", bookSourceExtensions.contains("put(\"java\", exploreKindsJava)"))
        assertTrue("Dynamic discovery settings with load-more rows should not cache an empty selector forever", bookSourceExtensions.contains("shouldCacheExploreKinds(rule)"))
        assertTrue("Explore kind cache should skip dynamic load-more rules", bookSourceExtensions.contains("!contains(\"\\\"loadMoreAction\\\"\")"))
        assertTrue("Explore kind memory cache should also skip dynamic load-more rules", bookSourceExtensions.contains("if (shouldCacheExploreKinds(ruleStr)) {\n                    exploreKindsMap[exploreKindsKey] = kinds"))
        assertTrue("JsExtensions should expose browser verification body as a plain string for Rhino callers", jsExtensions.contains("fun startBrowserAwaitBody"))
        assertTrue("Load-more dropdown rows should paint an opaque themed surface", rowUiViewFactory.contains("applyDropdownRowSurface"))
        assertTrue("Load-more dropdown item views should not stay transparent", rowUiViewFactory.contains("view.background = dropdownSurfaceBackground(context)"))
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
