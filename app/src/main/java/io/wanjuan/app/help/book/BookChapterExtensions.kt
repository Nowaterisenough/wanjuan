@file:Suppress("unused")

package io.wanjuan.app.help.book

import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.help.RuleBigDataHelp.getDanmakuFile

fun BookChapter.getDanmaku(): Any? { //读取弹幕数据
    return variableMap["danmaku"] ?: getDanmakuFile(bookUrl, url)
}