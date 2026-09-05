package io.wanjuan.app.sync.model

import com.google.gson.annotations.SerializedName
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.sync.mapper.progressSyncTime
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.RuleSub
import io.wanjuan.app.data.entities.rule.BookInfoRule
import io.wanjuan.app.data.entities.rule.ContentRule
import io.wanjuan.app.data.entities.rule.ExploreRule
import io.wanjuan.app.data.entities.rule.ReviewRule
import io.wanjuan.app.data.entities.rule.SearchRule
import io.wanjuan.app.data.entities.rule.TocRule

data class SyncDevice(
    @SerializedName(value = "a", alternate = ["deviceId"])
    val deviceId: String,
    @SerializedName(value = "b", alternate = ["deviceName"])
    val deviceName: String,
    @SerializedName(value = "c", alternate = ["appVersionName"])
    val appVersionName: String,
    @SerializedName(value = "d", alternate = ["appVersionCode"])
    val appVersionCode: Long,
    @SerializedName(value = "e", alternate = ["lastSeenAt"])
    val lastSeenAt: Long
)

data class SyncBookPayload(
    @SerializedName(value = "a", alternate = ["bookSyncId"])
    val bookSyncId: String,
    @SerializedName(value = "b", alternate = ["book"])
    val book: SyncBook,
    @SerializedName(value = "c", alternate = ["shelfUpdatedAt"])
    val shelfUpdatedAt: Long,
    @SerializedName(value = "d", alternate = ["catalogUpdatedAt"])
    val catalogUpdatedAt: Long,
    @SerializedName(value = "e", alternate = ["progressUpdatedAt"])
    val progressUpdatedAt: Long = 0L,
    @SerializedName(value = "f", alternate = ["updatedByDeviceId"])
    val updatedByDeviceId: String,
    @SerializedName(value = "g", alternate = ["progressUpdatedByDeviceId"])
    val progressUpdatedByDeviceId: String? = updatedByDeviceId,
    @SerializedName(value = "h", alternate = ["schemaVersion"])
    val schemaVersion: Int = 2,
    @SerializedName(value = "i", alternate = ["shelfUpdatedByDeviceId"])
    val shelfUpdatedByDeviceId: String? = null,
    @SerializedName(value = "j", alternate = ["catalogUpdatedByDeviceId"])
    val catalogUpdatedByDeviceId: String? = null
) {
    constructor(
        bookSyncId: String,
        book: Book,
        shelfUpdatedAt: Long,
        catalogUpdatedAt: Long,
        progressUpdatedAt: Long = book.progressSyncTime(),
        updatedByDeviceId: String,
        progressUpdatedByDeviceId: String? = updatedByDeviceId,
        schemaVersion: Int = 2,
        groupSyncIds: List<String> = emptyList()
    ) : this(
        bookSyncId = bookSyncId,
        book = SyncBook.from(book, groupSyncIds),
        shelfUpdatedAt = shelfUpdatedAt,
        catalogUpdatedAt = catalogUpdatedAt,
        progressUpdatedAt = progressUpdatedAt,
        updatedByDeviceId = updatedByDeviceId,
        progressUpdatedByDeviceId = progressUpdatedByDeviceId,
        schemaVersion = schemaVersion
    )
}

data class SyncBookSourcePayload(
    @SerializedName(value = "a", alternate = ["sourceHash"])
    val sourceHash: String,
    @SerializedName(value = "b", alternate = ["bookSourceUrl"])
    val bookSourceUrl: String,
    @SerializedName(value = "c", alternate = ["bookSource"])
    val bookSource: SyncBookSource,
    @SerializedName(value = "d", alternate = ["sourceUpdatedAt"])
    val sourceUpdatedAt: Long,
    @SerializedName(value = "e", alternate = ["updatedByDeviceId"])
    val updatedByDeviceId: String,
    @SerializedName(value = "f", alternate = ["schemaVersion"])
    val schemaVersion: Int = 1
) {
    constructor(
        sourceHash: String,
        bookSourceUrl: String,
        bookSource: BookSource,
        sourceUpdatedAt: Long,
        updatedByDeviceId: String,
        schemaVersion: Int = 1
    ) : this(
        sourceHash = sourceHash,
        bookSourceUrl = bookSourceUrl,
        bookSource = SyncBookSource.from(bookSource),
        sourceUpdatedAt = sourceUpdatedAt,
        updatedByDeviceId = updatedByDeviceId,
        schemaVersion = schemaVersion
    )
}

data class SyncRuleSubPayload(
    @SerializedName(value = "a", alternate = ["ruleSubHash"])
    val ruleSubHash: String,
    @SerializedName(value = "b", alternate = ["type"])
    val type: Int,
    @SerializedName(value = "c", alternate = ["url"])
    val url: String,
    @SerializedName(value = "d", alternate = ["ruleSub"])
    val ruleSub: SyncRuleSub,
    @SerializedName(value = "e", alternate = ["subscriptionUpdatedAt"])
    val subscriptionUpdatedAt: Long,
    @SerializedName(value = "f", alternate = ["updatedByDeviceId"])
    val updatedByDeviceId: String,
    @SerializedName(value = "g", alternate = ["schemaVersion"])
    val schemaVersion: Int = 1
) {
    constructor(
        ruleSubHash: String,
        type: Int,
        url: String,
        ruleSub: RuleSub,
        subscriptionUpdatedAt: Long,
        updatedByDeviceId: String,
        schemaVersion: Int = 1
    ) : this(
        ruleSubHash = ruleSubHash,
        type = type,
        url = url,
        ruleSub = SyncRuleSub.from(ruleSub),
        subscriptionUpdatedAt = subscriptionUpdatedAt,
        updatedByDeviceId = updatedByDeviceId,
        schemaVersion = schemaVersion
    )
}

data class SyncOrderPayload(
    @SerializedName(value = "a", alternate = ["updatedAt"])
    val updatedAt: Long,
    @SerializedName(value = "b", alternate = ["updatedByDeviceId"])
    val updatedByDeviceId: String,
    @SerializedName(value = "c", alternate = ["items"])
    val items: List<String>
)

data class SyncTombstonePayload(
    @SerializedName(value = "a", alternate = ["objectType"])
    val objectType: String,
    @SerializedName(value = "b", alternate = ["objectId"])
    val objectId: String,
    @SerializedName(value = "c", alternate = ["deletedAt"])
    val deletedAt: Long,
    @SerializedName(value = "d", alternate = ["deletedByDeviceId"])
    val deletedByDeviceId: String,
    @SerializedName(value = "e", alternate = ["objectKey"])
    val objectKey: String? = null,
    @SerializedName(value = "f", alternate = ["reason"])
    val reason: String = "user"
)

data class SyncDeleteKeyPayload(
    @SerializedName(value = "a", alternate = ["key"])
    val key: String
)

data class SyncBook(
    @SerializedName(value = "a", alternate = ["bookUrl"])
    val bookUrl: String = "",
    @SerializedName(value = "b", alternate = ["tocUrl"])
    val tocUrl: String = "",
    @SerializedName(value = "c", alternate = ["origin"])
    val origin: String = "",
    @SerializedName(value = "d", alternate = ["originName"])
    val originName: String = "",
    @SerializedName(value = "e", alternate = ["name"])
    val name: String = "",
    @SerializedName(value = "f", alternate = ["author"])
    val author: String = "",
    @SerializedName(value = "g", alternate = ["kind"])
    val kind: String? = null,
    @SerializedName(value = "h", alternate = ["customTag"])
    val customTag: String? = null,
    @SerializedName(value = "i", alternate = ["coverUrl"])
    val coverUrl: String? = null,
    @SerializedName(value = "j", alternate = ["customCoverUrl"])
    val customCoverUrl: String? = null,
    @SerializedName(value = "k", alternate = ["intro"])
    val intro: String? = null,
    @SerializedName(value = "l", alternate = ["customIntro"])
    val customIntro: String? = null,
    @SerializedName(value = "m", alternate = ["charset"])
    val charset: String? = null,
    @SerializedName(value = "n", alternate = ["type"])
    val type: Int = 0,
    @SerializedName(value = "o", alternate = ["group"])
    val group: Long = 0,
    @SerializedName(value = "p", alternate = ["latestChapterTitle"])
    val latestChapterTitle: String? = null,
    @SerializedName(value = "q", alternate = ["latestChapterTime"])
    val latestChapterTime: Long = 0L,
    @SerializedName(value = "r", alternate = ["lastCheckTime"])
    val lastCheckTime: Long = 0L,
    @SerializedName(value = "s", alternate = ["lastCheckCount"])
    val lastCheckCount: Int = 0,
    @SerializedName(value = "t", alternate = ["totalChapterNum"])
    val totalChapterNum: Int = 0,
    @SerializedName(value = "u", alternate = ["durChapterTitle"])
    val durChapterTitle: String? = null,
    @SerializedName(value = "v", alternate = ["durChapterIndex"])
    val durChapterIndex: Int = 0,
    @SerializedName(value = "w", alternate = ["durVolumeIndex"])
    val durVolumeIndex: Int = 0,
    @SerializedName(value = "x", alternate = ["chapterInVolumeIndex"])
    val chapterInVolumeIndex: Int = 0,
    @SerializedName(value = "y", alternate = ["durChapterPos"])
    val durChapterPos: Int = 0,
    @SerializedName(value = "z", alternate = ["durChapterTime"])
    val durChapterTime: Long = 0L,
    @SerializedName(value = "A", alternate = ["wordCount"])
    val wordCount: String? = null,
    @SerializedName(value = "B", alternate = ["canUpdate"])
    val canUpdate: Boolean = true,
    @SerializedName(value = "C", alternate = ["order"])
    val order: Int = 0,
    @SerializedName(value = "D", alternate = ["originOrder"])
    val originOrder: Int = 0,
    @SerializedName(value = "E", alternate = ["variable"])
    val variable: String? = null,
    @SerializedName(value = "F", alternate = ["readConfig"])
    val readConfig: Book.ReadConfig? = null,
    @SerializedName(value = "G", alternate = ["syncTime"])
    val syncTime: Long = 0L,
    @SerializedName(value = "H", alternate = ["groupSyncIds"])
    val groupSyncIds: List<String> = emptyList()
) {
    companion object {
        fun from(book: Book, groupSyncIds: List<String> = emptyList()): SyncBook {
            return SyncBook(
                bookUrl = book.bookUrl,
                tocUrl = book.tocUrl,
                origin = book.origin,
                originName = book.originName,
                name = book.name,
                author = book.author,
                kind = book.kind,
                customTag = book.customTag,
                coverUrl = book.coverUrl,
                customCoverUrl = book.customCoverUrl,
                intro = book.intro,
                customIntro = book.customIntro,
                charset = book.charset,
                type = book.type,
                group = book.group,
                latestChapterTitle = book.latestChapterTitle,
                latestChapterTime = book.latestChapterTime,
                lastCheckTime = book.lastCheckTime,
                lastCheckCount = book.lastCheckCount,
                totalChapterNum = book.totalChapterNum,
                durChapterTitle = book.durChapterTitle,
                durChapterIndex = book.durChapterIndex,
                durVolumeIndex = book.durVolumeIndex,
                chapterInVolumeIndex = book.chapterInVolumeIndex,
                durChapterPos = book.durChapterPos,
                durChapterTime = book.durChapterTime,
                wordCount = book.wordCount,
                canUpdate = book.canUpdate,
                order = book.order,
                originOrder = book.originOrder,
                variable = book.variable,
                readConfig = book.readConfig,
                syncTime = book.syncTime,
                groupSyncIds = groupSyncIds
            )
        }
    }
}

data class SyncBookGroupPayload(
    @SerializedName(value = "a", alternate = ["groupSyncId"])
    val groupSyncId: String,
    @SerializedName(value = "b", alternate = ["legacyGroupId"])
    val legacyGroupId: Long,
    @SerializedName(value = "c", alternate = ["groupName"])
    val groupName: String,
    @SerializedName(value = "d", alternate = ["cover"])
    val cover: String?,
    @SerializedName(value = "e", alternate = ["order"])
    val order: Int,
    @SerializedName(value = "f", alternate = ["enableRefresh"])
    val enableRefresh: Boolean,
    @SerializedName(value = "g", alternate = ["show"])
    val show: Boolean,
    @SerializedName(value = "h", alternate = ["bookSort"])
    val bookSort: Int,
    @SerializedName(value = "i", alternate = ["onlyUpdateRead"])
    val onlyUpdateRead: Boolean,
    @SerializedName(value = "j", alternate = ["updatedAt"])
    val updatedAt: Long,
    @SerializedName(value = "k", alternate = ["updatedByDeviceId"])
    val updatedByDeviceId: String,
    @SerializedName(value = "l", alternate = ["schemaVersion"])
    val schemaVersion: Int = 2
)

data class SyncRssSourcePayload(
    @SerializedName(value = "a", alternate = ["sourceHash"])
    val sourceHash: String,
    @SerializedName(value = "b", alternate = ["sourceUrl"])
    val sourceUrl: String,
    @SerializedName(value = "c", alternate = ["rssSource"])
    val rssSource: SyncRssSource,
    @SerializedName(value = "d", alternate = ["sourceUpdatedAt"])
    val sourceUpdatedAt: Long,
    @SerializedName(value = "e", alternate = ["updatedByDeviceId"])
    val updatedByDeviceId: String,
    @SerializedName(value = "f", alternate = ["schemaVersion"])
    val schemaVersion: Int = 2
)

data class SyncRssSource(
    @SerializedName(value = "a", alternate = ["sourceUrl"])
    val sourceUrl: String = "",
    @SerializedName(value = "b", alternate = ["sourceName"])
    val sourceName: String = "",
    @SerializedName(value = "c", alternate = ["sourceIcon"])
    val sourceIcon: String = "",
    @SerializedName(value = "d", alternate = ["sourceGroup"])
    val sourceGroup: String? = null,
    @SerializedName(value = "e", alternate = ["sourceComment"])
    val sourceComment: String? = null,
    @SerializedName(value = "f", alternate = ["enabled"])
    val enabled: Boolean = true,
    @SerializedName(value = "g", alternate = ["variableComment"])
    val variableComment: String? = null,
    @SerializedName(value = "h", alternate = ["jsLib"])
    val jsLib: String? = null,
    @SerializedName(value = "i", alternate = ["enabledCookieJar"])
    val enabledCookieJar: Boolean? = true,
    @SerializedName(value = "j", alternate = ["concurrentRate"])
    val concurrentRate: String? = null,
    @SerializedName(value = "k", alternate = ["header"])
    val header: String? = null,
    @SerializedName(value = "l", alternate = ["loginUrl"])
    val loginUrl: String? = null,
    @SerializedName(value = "m", alternate = ["loginUi"])
    val loginUi: String? = null,
    @SerializedName(value = "n", alternate = ["loginCheckJs"])
    val loginCheckJs: String? = null,
    @SerializedName(value = "o", alternate = ["coverDecodeJs"])
    val coverDecodeJs: String? = null,
    @SerializedName(value = "p", alternate = ["sortUrl"])
    val sortUrl: String? = null,
    @SerializedName(value = "q", alternate = ["singleUrl"])
    val singleUrl: Boolean = false,
    @SerializedName(value = "r", alternate = ["articleStyle"])
    val articleStyle: Int = 0,
    @SerializedName(value = "s", alternate = ["ruleArticles"])
    val ruleArticles: String? = null,
    @SerializedName(value = "t", alternate = ["ruleNextPage"])
    val ruleNextPage: String? = null,
    @SerializedName(value = "u", alternate = ["ruleTitle"])
    val ruleTitle: String? = null,
    @SerializedName(value = "v", alternate = ["rulePubDate"])
    val rulePubDate: String? = null,
    @SerializedName(value = "w", alternate = ["ruleDescription"])
    val ruleDescription: String? = null,
    @SerializedName(value = "x", alternate = ["ruleImage"])
    val ruleImage: String? = null,
    @SerializedName(value = "y", alternate = ["ruleLink"])
    val ruleLink: String? = null,
    @SerializedName(value = "z", alternate = ["ruleContent"])
    val ruleContent: String? = null,
    @SerializedName(value = "A", alternate = ["contentWhitelist"])
    val contentWhitelist: String? = null,
    @SerializedName(value = "B", alternate = ["contentBlacklist"])
    val contentBlacklist: String? = null,
    @SerializedName(value = "C", alternate = ["shouldOverrideUrlLoading"])
    val shouldOverrideUrlLoading: String? = null,
    @SerializedName(value = "D", alternate = ["style"])
    val style: String? = null,
    @SerializedName(value = "E", alternate = ["enableJs"])
    val enableJs: Boolean = true,
    @SerializedName(value = "F", alternate = ["loadWithBaseUrl"])
    val loadWithBaseUrl: Boolean = true,
    @SerializedName(value = "G", alternate = ["injectJs"])
    val injectJs: String? = null,
    @SerializedName(value = "H", alternate = ["preloadJs"])
    val preloadJs: String? = null,
    @SerializedName(value = "I", alternate = ["startHtml"])
    val startHtml: String? = null,
    @SerializedName(value = "J", alternate = ["startStyle"])
    val startStyle: String? = null,
    @SerializedName(value = "K", alternate = ["startJs"])
    val startJs: String? = null,
    @SerializedName(value = "L", alternate = ["showWebLog"])
    val showWebLog: Boolean = false,
    @SerializedName(value = "M", alternate = ["lastUpdateTime"])
    val lastUpdateTime: Long = 0L,
    @SerializedName(value = "N", alternate = ["customOrder"])
    val customOrder: Int = 0,
    @SerializedName(value = "O", alternate = ["type"])
    val type: Int = 0,
    @SerializedName(value = "P", alternate = ["preload"])
    val preload: Boolean = false,
    @SerializedName(value = "Q", alternate = ["cacheFirst"])
    val cacheFirst: Boolean = false,
    @SerializedName(value = "R", alternate = ["searchUrl"])
    val searchUrl: String? = null
)

data class SyncBookSource(
    @SerializedName(value = "a", alternate = ["bookSourceUrl"])
    val bookSourceUrl: String = "",
    @SerializedName(value = "b", alternate = ["bookSourceName"])
    val bookSourceName: String = "",
    @SerializedName(value = "c", alternate = ["bookSourceGroup"])
    val bookSourceGroup: String? = null,
    @SerializedName(value = "d", alternate = ["bookSourceType"])
    val bookSourceType: Int = 0,
    @SerializedName(value = "e", alternate = ["bookUrlPattern"])
    val bookUrlPattern: String? = null,
    @SerializedName(value = "f", alternate = ["customOrder"])
    val customOrder: Int = 0,
    @SerializedName(value = "g", alternate = ["enabled"])
    val enabled: Boolean = true,
    @SerializedName(value = "h", alternate = ["enabledExplore"])
    val enabledExplore: Boolean = true,
    @SerializedName(value = "i", alternate = ["jsLib"])
    val jsLib: String? = null,
    @SerializedName(value = "j", alternate = ["enabledCookieJar"])
    val enabledCookieJar: Boolean? = true,
    @SerializedName(value = "k", alternate = ["concurrentRate"])
    val concurrentRate: String? = null,
    @SerializedName(value = "l", alternate = ["header"])
    val header: String? = null,
    @SerializedName(value = "m", alternate = ["loginUrl"])
    val loginUrl: String? = null,
    @SerializedName(value = "n", alternate = ["loginUi"])
    val loginUi: String? = null,
    @SerializedName(value = "o", alternate = ["loginCheckJs"])
    val loginCheckJs: String? = null,
    @SerializedName(value = "p", alternate = ["coverDecodeJs"])
    val coverDecodeJs: String? = null,
    @SerializedName(value = "q", alternate = ["bookSourceComment"])
    val bookSourceComment: String? = null,
    @SerializedName(value = "r", alternate = ["variableComment"])
    val variableComment: String? = null,
    @SerializedName(value = "s", alternate = ["lastUpdateTime"])
    val lastUpdateTime: Long = 0L,
    @SerializedName(value = "t", alternate = ["respondTime"])
    val respondTime: Long = 180000L,
    @SerializedName(value = "u", alternate = ["weight"])
    val weight: Int = 0,
    @SerializedName(value = "v", alternate = ["preDownloadNum"])
    val preDownloadNum: Int? = null,
    @SerializedName(value = "w", alternate = ["exploreUrl"])
    val exploreUrl: String? = null,
    @SerializedName(value = "x", alternate = ["exploreScreen"])
    val exploreScreen: String? = null,
    @SerializedName(value = "y", alternate = ["ruleExplore"])
    val ruleExplore: ExploreRule? = null,
    @SerializedName(value = "z", alternate = ["searchUrl"])
    val searchUrl: String? = null,
    @SerializedName(value = "A", alternate = ["ruleSearch"])
    val ruleSearch: SearchRule? = null,
    @SerializedName(value = "B", alternate = ["ruleBookInfo"])
    val ruleBookInfo: BookInfoRule? = null,
    @SerializedName(value = "C", alternate = ["ruleToc"])
    val ruleToc: TocRule? = null,
    @SerializedName(value = "D", alternate = ["ruleContent"])
    val ruleContent: ContentRule? = null,
    @SerializedName(value = "E", alternate = ["ruleReview"])
    val ruleReview: ReviewRule? = null,
    @SerializedName(value = "F", alternate = ["eventListener"])
    val eventListener: Boolean = false,
    @SerializedName(value = "G", alternate = ["customButton"])
    val customButton: Boolean = false
) {
    companion object {
        fun from(source: BookSource): SyncBookSource {
            return SyncBookSource(
                bookSourceUrl = source.bookSourceUrl,
                bookSourceName = source.bookSourceName,
                bookSourceGroup = source.bookSourceGroup,
                bookSourceType = source.bookSourceType,
                bookUrlPattern = source.bookUrlPattern,
                customOrder = source.customOrder,
                enabled = source.enabled,
                enabledExplore = source.enabledExplore,
                jsLib = source.jsLib,
                enabledCookieJar = source.enabledCookieJar,
                concurrentRate = source.concurrentRate,
                header = source.header,
                loginUrl = source.loginUrl,
                loginUi = source.loginUi,
                loginCheckJs = source.loginCheckJs,
                coverDecodeJs = source.coverDecodeJs,
                bookSourceComment = source.bookSourceComment,
                variableComment = source.variableComment,
                lastUpdateTime = source.lastUpdateTime,
                respondTime = source.respondTime,
                weight = source.weight,
                preDownloadNum = source.preDownloadNum,
                exploreUrl = source.exploreUrl,
                exploreScreen = source.exploreScreen,
                ruleExplore = source.ruleExplore,
                searchUrl = source.searchUrl,
                ruleSearch = source.ruleSearch,
                ruleBookInfo = source.ruleBookInfo,
                ruleToc = source.ruleToc,
                ruleContent = source.ruleContent,
                ruleReview = source.ruleReview,
                eventListener = source.eventListener,
                customButton = source.customButton
            )
        }
    }
}

data class SyncRuleSub(
    @SerializedName(value = "a", alternate = ["name"])
    val name: String = "",
    @SerializedName(value = "b", alternate = ["url"])
    val url: String = "",
    @SerializedName(value = "c", alternate = ["type"])
    val type: Int = 0,
    @SerializedName(value = "d", alternate = ["customOrder"])
    val customOrder: Int = 0,
    @SerializedName(value = "e", alternate = ["autoUpdate"])
    val autoUpdate: Boolean = false,
    @SerializedName(value = "f", alternate = ["update"])
    val update: Long = 0L,
    @SerializedName(value = "g", alternate = ["updateInterval"])
    val updateInterval: Int = 0,
    @SerializedName(value = "h", alternate = ["silentUpdate"])
    val silentUpdate: Boolean = false,
    @SerializedName(value = "i", alternate = ["js"])
    val js: String? = null,
    @SerializedName(value = "j", alternate = ["showRule"])
    val showRule: String? = null,
    @SerializedName(value = "k", alternate = ["sourceUrl"])
    val sourceUrl: String? = null
) {
    companion object {
        fun from(ruleSub: RuleSub): SyncRuleSub {
            return SyncRuleSub(
                name = ruleSub.name,
                url = ruleSub.url,
                type = ruleSub.type,
                customOrder = ruleSub.customOrder,
                autoUpdate = ruleSub.autoUpdate,
                update = ruleSub.update,
                updateInterval = ruleSub.updateInterval,
                silentUpdate = ruleSub.silentUpdate,
                js = ruleSub.js,
                showRule = ruleSub.showRule,
                sourceUrl = ruleSub.sourceUrl
            )
        }
    }
}
