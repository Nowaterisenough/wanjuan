package io.wanjuan.app.sync.mapper

import io.wanjuan.app.data.entities.RssSource
import io.wanjuan.app.sync.SyncIds
import io.wanjuan.app.sync.model.SyncRssSource
import io.wanjuan.app.sync.model.SyncRssSourcePayload
import io.wanjuan.app.sync.model.SyncVersion

object RssSourceSyncMapper {

    fun toPayload(source: RssSource, version: SyncVersion): SyncRssSourcePayload {
        return SyncRssSourcePayload(
            sourceHash = SyncIds.rssSourceId(source.sourceUrl),
            sourceUrl = source.sourceUrl,
            rssSource = source.toSyncSource(),
            sourceUpdatedAt = version.timestamp,
            updatedByDeviceId = version.deviceId
        )
    }

    fun toEntity(payload: SyncRssSourcePayload): RssSource {
        return payload.rssSource.toEntity().apply { sourceUrl = payload.sourceUrl }
    }

    private fun RssSource.toSyncSource(): SyncRssSource = SyncRssSource(
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        sourceIcon = sourceIcon,
        sourceGroup = sourceGroup,
        sourceComment = sourceComment,
        enabled = enabled,
        variableComment = variableComment,
        jsLib = jsLib,
        enabledCookieJar = enabledCookieJar,
        concurrentRate = concurrentRate,
        header = header,
        loginUrl = loginUrl,
        loginUi = loginUi,
        loginCheckJs = loginCheckJs,
        coverDecodeJs = coverDecodeJs,
        sortUrl = sortUrl,
        singleUrl = singleUrl,
        articleStyle = articleStyle,
        ruleArticles = ruleArticles,
        ruleNextPage = ruleNextPage,
        ruleTitle = ruleTitle,
        rulePubDate = rulePubDate,
        ruleDescription = ruleDescription,
        ruleImage = ruleImage,
        ruleLink = ruleLink,
        ruleContent = ruleContent,
        contentWhitelist = contentWhitelist,
        contentBlacklist = contentBlacklist,
        shouldOverrideUrlLoading = shouldOverrideUrlLoading,
        style = style,
        enableJs = enableJs,
        loadWithBaseUrl = loadWithBaseUrl,
        injectJs = injectJs,
        preloadJs = preloadJs,
        startHtml = startHtml,
        startStyle = startStyle,
        startJs = startJs,
        showWebLog = showWebLog,
        lastUpdateTime = lastUpdateTime,
        customOrder = customOrder,
        type = type,
        preload = preload,
        cacheFirst = cacheFirst,
        searchUrl = searchUrl
    )

    private fun SyncRssSource.toEntity(): RssSource = RssSource(
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        sourceIcon = sourceIcon,
        sourceGroup = sourceGroup,
        sourceComment = sourceComment,
        enabled = enabled,
        variableComment = variableComment,
        jsLib = jsLib,
        enabledCookieJar = enabledCookieJar,
        concurrentRate = concurrentRate,
        header = header,
        loginUrl = loginUrl,
        loginUi = loginUi,
        loginCheckJs = loginCheckJs,
        coverDecodeJs = coverDecodeJs,
        sortUrl = sortUrl,
        singleUrl = singleUrl,
        articleStyle = articleStyle,
        ruleArticles = ruleArticles,
        ruleNextPage = ruleNextPage,
        ruleTitle = ruleTitle,
        rulePubDate = rulePubDate,
        ruleDescription = ruleDescription,
        ruleImage = ruleImage,
        ruleLink = ruleLink,
        ruleContent = ruleContent,
        contentWhitelist = contentWhitelist,
        contentBlacklist = contentBlacklist,
        shouldOverrideUrlLoading = shouldOverrideUrlLoading,
        style = style,
        enableJs = enableJs,
        loadWithBaseUrl = loadWithBaseUrl,
        injectJs = injectJs,
        preloadJs = preloadJs,
        startHtml = startHtml,
        startStyle = startStyle,
        startJs = startJs,
        showWebLog = showWebLog,
        lastUpdateTime = lastUpdateTime,
        customOrder = customOrder,
        type = type,
        preload = preload,
        cacheFirst = cacheFirst,
        searchUrl = searchUrl
    )
}
