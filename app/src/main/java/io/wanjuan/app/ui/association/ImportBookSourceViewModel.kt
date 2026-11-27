package io.wanjuan.app.ui.association

import android.app.Application
import android.net.Uri
import io.wanjuan.app.R
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.constant.AppPattern
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.BookSourcePart
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.book.ContentProcessor
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.http.decompressed
import io.wanjuan.app.help.http.newCallResponse
import io.wanjuan.app.help.http.okHttpClient
import io.wanjuan.app.help.source.BookSourceImportParser
import io.wanjuan.app.help.source.SourceHelp
import io.wanjuan.app.model.RuleUpdate
import io.wanjuan.app.utils.inputStream
import io.wanjuan.app.utils.isAbsUrl
import io.wanjuan.app.utils.isUri
import io.wanjuan.app.utils.splitNotBlank

class ImportBookSourceViewModel(app: Application) : ImportPreviewViewModel<BookSource>(app) {
    var isAddGroup = false
    var groupName: String? = null

    val checkSources = arrayListOf<BookSourcePart?>()
    val newSourceStatus = arrayListOf<Boolean>()
    val updateSourceStatus = arrayListOf<Boolean>()

    val isSelectAllNew: Boolean
        get() {
            newSourceStatus.forEachIndexed { index, b ->
                if (b && !selectStatus[index]) {
                    return false
                }
            }
            return true
        }

    val isSelectAllUpdate: Boolean
        get() {
            updateSourceStatus.forEachIndexed { index, b ->
                if (b && !selectStatus[index]) {
                    return false
                }
            }
            return true
        }

    override suspend fun saveSelection() {
        val group = groupName?.trim()
        val keepName = AppConfig.importKeepName
        val keepGroup = AppConfig.importKeepGroup
        val keepEnable = AppConfig.importKeepEnable
        val selectSource = arrayListOf<BookSource>()
        selectStatus.forEachIndexed { index, b ->
            if (b) {
                val source = allSources[index]
                checkSources[index]?.let {
                    if (keepName) {
                        source.bookSourceName = it.bookSourceName
                    }
                    if (keepGroup) {
                        source.bookSourceGroup = it.bookSourceGroup
                    }
                    if (keepEnable) {
                        source.enabled = it.enabled
                        source.enabledExplore = it.enabledExplore
                    }
                    source.customOrder = it.customOrder
                }
                if (!group.isNullOrEmpty()) {
                    if (isAddGroup) {
                        val groups = linkedSetOf<String>()
                        source.bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.let {
                            groups.addAll(it)
                        }
                        groups.add(group)
                        source.bookSourceGroup = groups.joinToString(",")
                    } else {
                        source.bookSourceGroup = group
                    }
                }
                selectSource.add(source)
            }
        }
        SourceHelp.insertBookSource(*selectSource.toTypedArray())
        ContentProcessor.upReplaceRules()
    }

    override suspend fun preparePreview(text: String) {
        val mText = text.trim { it.isWhitespace() || it == '\uFEFF' }
        val sources = when {
            mText.isAbsUrl() -> importSourceUrl(mText, emptySet())
            mText.isUri() -> {
                val uri = Uri.parse(mText)
                uri.inputStream(context).getOrThrow().use { inputS ->
                    importContent(BookSourceImportParser.parse(inputS.reader(Charsets.UTF_8)))
                }
            }
            else -> importContent(BookSourceImportParser.parse(mText))
        }
        if (sources.isEmpty()) {
            throw NoStackTraceException(context.getString(R.string.wrong_format))
        }
        // Publish only after all files and linked sources have been validated.
        allSources.clear()
        allSources.addAll(sources)
        checkSources.clear()
        selectStatus.clear()
        newSourceStatus.clear()
        updateSourceStatus.clear()
        val existing = appDb.bookSourceDao.allPart.associateBy { it.bookSourceUrl }
        allSources.forEach {
            val source = existing[it.bookSourceUrl]
            checkSources.add(source)
            selectStatus.add(source == null || source.lastUpdateTime < it.lastUpdateTime)
            newSourceStatus.add(source == null)
            updateSourceStatus.add(source != null && source.lastUpdateTime < it.lastUpdateTime)
        }
    }

    private suspend fun importContent(
        content: BookSourceImportParser.Content,
        visitedUrls: Set<String> = emptySet()
    ): List<BookSource> {
        val sources = content.sources.toMutableList()
        content.sourceUrls.forEach { sources.addAll(importSourceUrl(it, visitedUrls)) }
        return sources
    }

    private suspend fun importSourceUrl(url: String, visitedUrls: Set<String>): List<BookSource> {
        if (url in visitedUrls || visitedUrls.size >= 8) {
            throw NoStackTraceException("书源链接存在循环引用或嵌套层数过多")
        }
        RuleUpdate.cacheBookSourceMap[url]?.also {
            RuleUpdate.cacheBookSourceMap.remove(url)
            return it
        }
        return okHttpClient.newCallResponse {
            if (url.endsWith("#requestWithoutUA")) {
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.use { response ->
            if (!response.isSuccessful) {
                throw NoStackTraceException("书源下载失败：HTTP ${response.code}，请检查链接或稍后重试")
            }
            response.body.decompressed().use { body ->
                importContent(BookSourceImportParser.parse(body.charStream()), visitedUrls + url)
            }
        }
    }
}
