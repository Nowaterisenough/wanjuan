package io.wanjuan.app.help.storage

import com.jayway.jsonpath.DocumentContext
import io.wanjuan.app.R
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.constant.BookSourceType
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.rule.*
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.utils.*
import splitties.init.appCtx
import java.util.regex.Pattern

object ImportOldData {

    @Suppress("RegExpRedundantEscape")
    private val headerPattern = Pattern.compile("@Header:\\{.+?\\}", Pattern.CASE_INSENSITIVE)
    @Suppress("RegExpRedundantEscape")
    private val jsPattern = Pattern.compile("\\{\\{.+?\\}\\}", Pattern.CASE_INSENSITIVE)

    fun fromOldBookSource(jsonItem: DocumentContext): BookSource {
        val source = BookSource()
        return source.apply {
            bookSourceUrl = jsonItem.readString("bookSourceUrl")
                ?: throw NoStackTraceException(appCtx.getString(R.string.wrong_format))
            bookSourceName = jsonItem.readString("bookSourceName") ?: ""
            bookSourceGroup = jsonItem.readString("bookSourceGroup")
            loginUrl = jsonItem.readString("loginUrl")
            loginUi = jsonItem.readString("loginUi")
            loginCheckJs = jsonItem.readString("loginCheckJs")
            coverDecodeJs = jsonItem.readString("coverDecodeJs")
            bookSourceComment = jsonItem.readString("bookSourceComment") ?: ""
            bookUrlPattern = jsonItem.readString("ruleBookUrlPattern")
            customOrder = jsonItem.readInt("serialNumber") ?: 0
            header = uaToHeader(jsonItem.readString("httpUserAgent"))
            searchUrl = toNewUrl(jsonItem.readString("ruleSearchUrl"))
            exploreUrl = toNewUrls(jsonItem.readString("ruleFindUrl"))
            bookSourceType =
                if (jsonItem.readString("bookSourceType") == "AUDIO") BookSourceType.audio else BookSourceType.default
            enabled = jsonItem.readBool("enable") ?: true
            if (exploreUrl.isNullOrBlank()) {
                enabledExplore = false
            }
            ruleSearch = SearchRule(
                bookList = toNewRule(jsonItem.readString("ruleSearchList")),
                name = toNewRule(jsonItem.readString("ruleSearchName")),
                author = toNewRule(jsonItem.readString("ruleSearchAuthor")),
                intro = toNewRule(jsonItem.readString("ruleSearchIntroduce")),
                kind = toNewRule(jsonItem.readString("ruleSearchKind")),
                bookUrl = toNewRule(jsonItem.readString("ruleSearchNoteUrl")),
                coverUrl = toNewRule(jsonItem.readString("ruleSearchCoverUrl")),
                lastChapter = toNewRule(jsonItem.readString("ruleSearchLastChapter"))
            )
            ruleExplore = ExploreRule(
                bookList = toNewRule(jsonItem.readString("ruleFindList")),
                name = toNewRule(jsonItem.readString("ruleFindName")),
                author = toNewRule(jsonItem.readString("ruleFindAuthor")),
                intro = toNewRule(jsonItem.readString("ruleFindIntroduce")),
                kind = toNewRule(jsonItem.readString("ruleFindKind")),
                bookUrl = toNewRule(jsonItem.readString("ruleFindNoteUrl")),
                coverUrl = toNewRule(jsonItem.readString("ruleFindCoverUrl")),
                lastChapter = toNewRule(jsonItem.readString("ruleFindLastChapter"))
            )
            ruleBookInfo = BookInfoRule(
                init = toNewRule(jsonItem.readString("ruleBookInfoInit")),
                name = toNewRule(jsonItem.readString("ruleBookName")),
                author = toNewRule(jsonItem.readString("ruleBookAuthor")),
                intro = toNewRule(jsonItem.readString("ruleIntroduce")),
                kind = toNewRule(jsonItem.readString("ruleBookKind")),
                coverUrl = toNewRule(jsonItem.readString("ruleCoverUrl")),
                lastChapter = toNewRule(jsonItem.readString("ruleBookLastChapter")),
                tocUrl = toNewRule(jsonItem.readString("ruleChapterUrl"))
            )
            ruleToc = TocRule(
                chapterList = toNewRule(jsonItem.readString("ruleChapterList")),
                chapterName = toNewRule(jsonItem.readString("ruleChapterName")),
                chapterUrl = toNewRule(jsonItem.readString("ruleContentUrl")),
                nextTocUrl = toNewRule(jsonItem.readString("ruleChapterUrlNext"))
            )
            var content = toNewRule(jsonItem.readString("ruleBookContent")) ?: ""
            if (content.startsWith("$") && !content.startsWith("$.")) {
                content = content.substring(1)
            }
            ruleContent = ContentRule(
                content = content,
                replaceRegex = toNewRule(jsonItem.readString("ruleBookContentReplace")),
                nextContentUrl = toNewRule(jsonItem.readString("ruleContentUrlNext"))
            )
        }
    }


    // default规则适配
    // #正则#替换内容 替换成 ##正则##替换内容
    // | 替换成 ||
    // & 替换成 &&
    private fun toNewRule(oldRule: String?): String? {
        if (oldRule.isNullOrBlank()) return null
        var newRule = oldRule
        var reverse = false
        var allinone = false
        if (oldRule.startsWith("-")) {
            reverse = true
            newRule = oldRule.substring(1)
        }
        if (newRule.startsWith("+")) {
            allinone = true
            newRule = newRule.substring(1)
        }
        if (!newRule.startsWith("@CSS:", true) &&
            !newRule.startsWith("@XPath:", true) &&
            !newRule.startsWith("//") &&
            !newRule.startsWith("##") &&
            !newRule.startsWith(":") &&
            !newRule.contains("@js:", true) &&
            !newRule.contains("<js>", true)
        ) {
            if (newRule.contains("#") && !newRule.contains("##")) {
                newRule = oldRule.replace("#", "##")
            }
            if (newRule.contains("|") && !newRule.contains("||")) {
                if (newRule.contains("##")) {
                    val list = newRule.split("##")
                    if (list[0].contains("|")) {
                        newRule = list[0].replace("|", "||")
                        for (i in 1 until list.size) {
                            newRule += "##" + list[i]
                        }
                    }
                } else {
                    newRule = newRule.replace("|", "||")
                }
            }
            if (newRule.contains("&")
                && !newRule.contains("&&")
                && !newRule.contains("http")
                && !newRule.startsWith("/")
            ) {
                newRule = newRule.replace("&", "&&")
            }
        }
        if (allinone) {
            newRule = "+$newRule"
        }
        if (reverse) {
            newRule = "-$newRule"
        }
        return newRule
    }

    private fun toNewUrls(oldUrls: String?): String? {
        if (oldUrls.isNullOrBlank()) return null
        if (oldUrls.startsWith("@js:") || oldUrls.startsWith("<js>")) {
            return oldUrls
        }
        if (!oldUrls.contains("\n") && !oldUrls.contains("&&")) {
            return toNewUrl(oldUrls)
        }
        val urls = oldUrls.split("(&&|\r?\n)+".toRegex())
        return urls.map {
            toNewUrl(it)?.replace("\n\\s*".toRegex(), "")
        }.joinToString("\n")
    }

    private fun toNewUrl(oldUrl: String?): String? {
        if (oldUrl.isNullOrBlank()) return null
        var url: String = oldUrl
        if (oldUrl.startsWith("<js>", true)) {
            url = url.replace("=searchKey", "={{key}}")
                .replace("=searchPage", "={{page}}")
            return url
        }
        val map = HashMap<String, String>()
        var mather = headerPattern.matcher(url)
        if (mather.find()) {
            val header = mather.group()
            url = url.replace(header, "")
            map["headers"] = header.substring(8)
        }
        var urlList = url.split("|")
        url = urlList[0]
        if (urlList.size > 1) {
            map["charset"] = urlList[1].split("=")[1]
        }
        mather = jsPattern.matcher(url)
        val jsList = arrayListOf<String>()
        while (mather.find()) {
            jsList.add(mather.group())
            url = url.replace(jsList.last(), "$${jsList.size - 1}")
        }
        url = url.replace("{", "<").replace("}", ">")
        url = url.replace("searchKey", "{{key}}")
        url = url.replace("<searchPage([-+]1)>".toRegex(), "{{page$1}}")
            .replace("searchPage([-+]1)".toRegex(), "{{page$1}}")
            .replace("searchPage", "{{page}}")
        for ((index, item) in jsList.withIndex()) {
            url = url.replace(
                "$$index",
                item.replace("searchKey", "key").replace("searchPage", "page")
            )
        }
        urlList = url.split("@")
        url = urlList[0]
        if (urlList.size > 1) {
            map["method"] = "POST"
            map["body"] = urlList[1]
        }
        if (map.size > 0) {
            url += "," + GSON.toJson(map)
        }
        return url
    }

    private fun uaToHeader(ua: String?): String? {
        if (ua.isNullOrEmpty()) return null
        val map = mapOf(Pair(AppConst.UA_NAME, ua))
        return GSON.toJson(map)
    }

}