package io.wanjuan.app.sync

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.data.entities.RssSource
import io.wanjuan.app.sync.mapper.BookGroupSyncMapper
import io.wanjuan.app.sync.mapper.BookSyncMapper
import io.wanjuan.app.sync.mapper.RssSourceSyncMapper
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyncV2MapperTest {

    @Test
    fun existingGroupIdsNormalizeNamesAndNewIdsAreUnique() {
        assertEquals(
            SyncIds.existingGroupId(" 漫画 "),
            SyncIds.existingGroupId("漫画")
        )
        assertNotEquals(SyncIds.newGroupId(), SyncIds.newGroupId())
    }

    @Test
    fun rssSourceIdUsesUrl() {
        assertEquals(
            SyncIds.rssSourceId("https://rss.example/source"),
            SyncIds.rssSourceId("https://rss.example/source")
        )
    }

    @Test
    fun bookGroupRoundTripsAllUserFields() {
        val group = BookGroup(
            groupId = 4L,
            groupName = "漫画",
            cover = "cover.png",
            order = 3,
            enableRefresh = false,
            show = false,
            bookSort = 2,
            onlyUpdateRead = true,
            syncId = "group-stable"
        )
        val payload = BookGroupSyncMapper.toPayload(
            group,
            SyncVersion(123L, "device-a")
        )
        val mapped = BookGroupSyncMapper.toEntity(payload, localGroupId = 8L)

        assertEquals(8L, mapped.groupId)
        assertEquals(group.copy(groupId = 8L), mapped)
        assertEquals(2, payload.schemaVersion)
    }

    @Test
    fun rssSourceRoundTripsEveryPersistedField() {
        val source = fullRssSource()
        val payload = RssSourceSyncMapper.toPayload(
            source,
            SyncVersion(456L, "device-b")
        )
        val mapped = RssSourceSyncMapper.toEntity(payload)

        assertEquals(GSON.toJson(source), GSON.toJson(mapped))
        assertEquals(2, payload.schemaVersion)
    }

    @Test
    fun bookPayloadCarriesStableGroupIds() {
        val book = Book(
            bookUrl = "https://book/1",
            origin = "https://source",
            name = "Book",
            author = "Author"
        )
        val payload = BookSyncMapper.toBookPayload(
            book = book,
            deviceId = "device",
            shelfUpdatedAt = 10L,
            catalogUpdatedAt = 20L,
            groupSyncIds = listOf("group-a", "group-b")
        )

        assertEquals(listOf("group-a", "group-b"), payload.book.groupSyncIds)
        assertEquals(2, payload.schemaVersion)
    }

    private fun fullRssSource(): RssSource = RssSource(
        sourceUrl = "https://rss.example/source",
        sourceName = "RSS",
        sourceIcon = "https://rss.example/icon.png",
        sourceGroup = "news,video",
        sourceComment = "comment",
        enabled = false,
        variableComment = "variable",
        jsLib = "https://rss.example/lib.js",
        enabledCookieJar = false,
        concurrentRate = "2/1000",
        header = "{\"User-Agent\":\"wanjuan\"}",
        loginUrl = "https://rss.example/login",
        loginUi = "login-ui",
        loginCheckJs = "return true",
        coverDecodeJs = "return src",
        sortUrl = "https://rss.example/{{page}}",
        singleUrl = true,
        articleStyle = 3,
        ruleArticles = ".article",
        ruleNextPage = ".next@href",
        ruleTitle = "h1@text",
        rulePubDate = ".date@text",
        ruleDescription = ".description@text",
        ruleImage = "img@src",
        ruleLink = "a@href",
        ruleContent = ".content@html",
        contentWhitelist = "example.com",
        contentBlacklist = "ads.example.com",
        shouldOverrideUrlLoading = "return false",
        style = "body{}",
        enableJs = false,
        loadWithBaseUrl = false,
        injectJs = "inject()",
        preloadJs = "preload()",
        startHtml = "<html></html>",
        startStyle = "html{}",
        startJs = "start()",
        showWebLog = true,
        lastUpdateTime = 99L,
        customOrder = 7,
        type = 2,
        preload = true,
        cacheFirst = true,
        searchUrl = "https://rss.example/search?q={{key}}"
    )
}
