package io.wanjuan.app.ui.association

import android.app.Application
import androidx.core.net.toUri
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.constant.AppPattern
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.ReplaceRule
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.ReplaceAnalyzer
import io.wanjuan.app.help.http.decompressed
import io.wanjuan.app.help.http.newCallResponseBody
import io.wanjuan.app.help.http.okHttpClient
import io.wanjuan.app.help.http.text
import io.wanjuan.app.model.RuleUpdate
import io.wanjuan.app.utils.isAbsUrl
import io.wanjuan.app.utils.isJsonArray
import io.wanjuan.app.utils.isJsonObject
import io.wanjuan.app.utils.isUri
import io.wanjuan.app.utils.readText
import io.wanjuan.app.utils.splitNotBlank
import splitties.init.appCtx

class ImportReplaceRuleViewModel(app: Application) : ImportPreviewViewModel<ReplaceRule>(app) {
    var isAddGroup = false
    var groupName: String? = null

    val checkRules = arrayListOf<ReplaceRule?>()

    override suspend fun saveSelection() {
        val group = groupName?.trim()
        val selectRules = arrayListOf<ReplaceRule>()
        selectStatus.forEachIndexed { index, b ->
            if (b) {
                val rule = allSources[index]
                if (!group.isNullOrEmpty()) {
                    if (isAddGroup) {
                        val groups = linkedSetOf<String>()
                        rule.group?.splitNotBlank(AppPattern.splitGroupRegex)?.let {
                            groups.addAll(it)
                        }
                        groups.add(group)
                        rule.group = groups.joinToString(",")
                    } else {
                        rule.group = group
                    }
                }
                selectRules.add(rule)
            }
        }
        appDb.replaceRuleDao.insert(*selectRules.toTypedArray())
    }

    override suspend fun preparePreview(text: String) {
        importAwait(text.trim())
        allSources.forEach {
            val rule = appDb.replaceRuleDao.findById(it.id)
            checkRules.add(rule)
            selectStatus.add(rule == null)
        }
    }

    private suspend fun importAwait(text: String) {
        when {
            text.isAbsUrl() -> importUrl(text)
            text.isJsonArray() -> {
                val rules = ReplaceAnalyzer.jsonToReplaceRules(text).getOrThrow()
                allSources.addAll(rules)
            }

            text.isJsonObject() -> {
                val rule = ReplaceAnalyzer.jsonToReplaceRule(text).getOrThrow()
                allSources.add(rule)
            }

            text.isUri() -> {
                importAwait(text.toUri().readText(appCtx))
            }

            else -> throw NoStackTraceException("格式不对")
        }
    }

    private suspend fun importUrl(url: String) {
        RuleUpdate.cacheReplaceRuleMap[url]?.also {
            allSources.addAll(it)
            RuleUpdate.cacheReplaceRuleMap.remove(url)
            return
        }
        okHttpClient.newCallResponseBody {
            if (url.endsWith("#requestWithoutUA")) {
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.decompressed().text("utf-8").let {
            importAwait(it)
        }
    }
}
