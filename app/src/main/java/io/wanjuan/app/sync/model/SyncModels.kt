package io.wanjuan.app.sync.model

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.RuleSub
import io.wanjuan.app.data.entities.rule.BookInfoRule
import io.wanjuan.app.data.entities.rule.ContentRule
import io.wanjuan.app.data.entities.rule.ExploreRule
import io.wanjuan.app.data.entities.rule.ReviewRule
import io.wanjuan.app.data.entities.rule.SearchRule
import io.wanjuan.app.data.entities.rule.TocRule

data class SyncManifest(
    val version: Int = 1,
    val updatedAt: Long = 0L,
    val updatedByDeviceId: String = "",
    val booksChangedAt: Long = 0L,
    val bookProgressChangedAt: Long = 0L,
    val bookSourcesChangedAt: Long = 0L,
    val ruleSubsChangedAt: Long = 0L,
    val bookGroupsChangedAt: Long = 0L,
    val tombstonesChangedAt: Long = 0L
)

data class SyncDevice(
    val deviceId: String,
    val deviceName: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val lastSeenAt: Long
)

data class SyncBookPayload(
    val bookSyncId: String,
    val book: SyncBook,
    val shelfUpdatedAt: Long,
    val catalogUpdatedAt: Long,
    val updatedByDeviceId: String,
    val schemaVersion: Int = 1
) {
    constructor(
        bookSyncId: String,
        book: Book,
        shelfUpdatedAt: Long,
        catalogUpdatedAt: Long,
        updatedByDeviceId: String,
        schemaVersion: Int = 1
    ) : this(
        bookSyncId = bookSyncId,
        book = SyncBook.from(book),
        shelfUpdatedAt = shelfUpdatedAt,
        catalogUpdatedAt = catalogUpdatedAt,
        updatedByDeviceId = updatedByDeviceId,
        schemaVersion = schemaVersion
    )
}

data class SyncBookProgressPayload(
    val bookSyncId: String,
    val name: String,
    val author: String,
    val durChapterIndex: Int,
    val durChapterPos: Int,
    val durChapterTitle: String?,
    val progressUpdatedAt: Long,
    val updatedByDeviceId: String,
    val schemaVersion: Int = 1
)

data class SyncBookSourcePayload(
    val sourceHash: String,
    val bookSourceUrl: String,
    val bookSource: SyncBookSource,
    val sourceUpdatedAt: Long,
    val updatedByDeviceId: String,
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
    val ruleSubHash: String,
    val type: Int,
    val url: String,
    val ruleSub: SyncRuleSub,
    val subscriptionUpdatedAt: Long,
    val updatedByDeviceId: String,
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
    val updatedAt: Long,
    val updatedByDeviceId: String,
    val items: List<String>
)

data class SyncTombstonePayload(
    val objectType: String,
    val objectId: String,
    val deletedAt: Long,
    val deletedByDeviceId: String,
    val objectKey: String? = null,
    val reason: String = "user"
)

data class SyncDeleteKeyPayload(
    val key: String
)

data class SyncBook(
    val bookUrl: String = "",
    val tocUrl: String = "",
    val origin: String = "",
    val originName: String = "",
    val name: String = "",
    val author: String = "",
    val kind: String? = null,
    val customTag: String? = null,
    val coverUrl: String? = null,
    val customCoverUrl: String? = null,
    val intro: String? = null,
    val customIntro: String? = null,
    val charset: String? = null,
    val type: Int = 0,
    val group: Long = 0,
    val latestChapterTitle: String? = null,
    val latestChapterTime: Long = 0L,
    val lastCheckTime: Long = 0L,
    val lastCheckCount: Int = 0,
    val totalChapterNum: Int = 0,
    val durChapterTitle: String? = null,
    val durChapterIndex: Int = 0,
    val durVolumeIndex: Int = 0,
    val chapterInVolumeIndex: Int = 0,
    val durChapterPos: Int = 0,
    val durChapterTime: Long = 0L,
    val wordCount: String? = null,
    val canUpdate: Boolean = true,
    val order: Int = 0,
    val originOrder: Int = 0,
    val variable: String? = null,
    val readConfig: Book.ReadConfig? = null,
    val syncTime: Long = 0L
) {
    companion object {
        fun from(book: Book): SyncBook {
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
                syncTime = book.syncTime
            )
        }
    }
}

data class SyncBookSource(
    val bookSourceUrl: String = "",
    val bookSourceName: String = "",
    val bookSourceGroup: String? = null,
    val bookSourceType: Int = 0,
    val bookUrlPattern: String? = null,
    val customOrder: Int = 0,
    val enabled: Boolean = true,
    val enabledExplore: Boolean = true,
    val jsLib: String? = null,
    val enabledCookieJar: Boolean? = true,
    val concurrentRate: String? = null,
    val header: String? = null,
    val loginUrl: String? = null,
    val loginUi: String? = null,
    val loginCheckJs: String? = null,
    val coverDecodeJs: String? = null,
    val bookSourceComment: String? = null,
    val variableComment: String? = null,
    val lastUpdateTime: Long = 0L,
    val respondTime: Long = 180000L,
    val weight: Int = 0,
    val preDownloadNum: Int? = null,
    val exploreUrl: String? = null,
    val exploreScreen: String? = null,
    val ruleExplore: ExploreRule? = null,
    val searchUrl: String? = null,
    val ruleSearch: SearchRule? = null,
    val ruleBookInfo: BookInfoRule? = null,
    val ruleToc: TocRule? = null,
    val ruleContent: ContentRule? = null,
    val ruleReview: ReviewRule? = null,
    val eventListener: Boolean = false,
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
    val name: String = "",
    val url: String = "",
    val type: Int = 0,
    val customOrder: Int = 0,
    val autoUpdate: Boolean = false,
    val update: Long = 0L,
    val updateInterval: Int = 0,
    val silentUpdate: Boolean = false,
    val js: String? = null,
    val showRule: String? = null,
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
