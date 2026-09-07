package io.wanjuan.app.help.source

import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.utils.GSON
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.Reader

object BookSourceImportParser {
    data class Content(
        val sources: List<BookSource> = emptyList(),
        val sourceUrls: List<String> = emptyList()
    )

    fun parse(reader: Reader): Content = parse(reader.readText())

    fun parse(text: String): Content {
        var content = text
        // Some exporters serialize the JSON document itself as a JSON string.
        repeat(8) {
            content = content.trim { it.isWhitespace() || it == '\uFEFF' }
            if (content.isEmpty()) {
                throw NoStackTraceException("书源内容为空")
            }
            if (content.startsWith('<')) {
                throw NoStackTraceException("导入内容是网页，不是书源 JSON，请使用书源文件或直链")
            }
            if (content.first() !in charArrayOf('{', '[', '"')) {
                throw NoStackTraceException("导入内容不是书源 JSON，请检查文件或链接返回的内容")
            }
            val root = try {
                GSON.fromJson(content, JsonElement::class.java)
            } catch (e: JsonParseException) {
                throw NoStackTraceException("书源 JSON 格式错误，请检查文件是否完整")
            }
            when {
                root.isJsonPrimitive && root.asJsonPrimitive.isString -> {
                    content = root.asString
                }
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    if (obj.has("sourceUrls") && !obj.has("bookSourceUrl")) {
                        val urls = obj.get("sourceUrls")
                        if (!urls.isJsonArray) {
                            throw NoStackTraceException("书源链接列表 sourceUrls 必须是数组")
                        }
                        return Content(sourceUrls = urls.asJsonArray.mapIndexed { index, item ->
                            val url = item.takeIf {
                                it.isJsonPrimitive && it.asJsonPrimitive.isString
                            }?.asString?.trim()
                            if (url.isNullOrBlank() || url.toHttpUrlOrNull() == null) {
                                throw NoStackTraceException("第 ${index + 1} 个书源链接无效")
                            }
                            url
                        })
                    }
                    return Content(sources = listOf(parseSource(root, 1)))
                }
                root.isJsonArray -> {
                    return Content(sources = root.asJsonArray.mapIndexedNotNull { index, item ->
                        // Preserve compatibility with Gson's lenient trailing commas.
                        if (item.isJsonNull) null else parseSource(item, index + 1)
                    })
                }
                else -> throw NoStackTraceException("导入内容不是书源对象或数组")
            }
        }
        throw NoStackTraceException("书源 JSON 字符串嵌套层数过多")
    }

    private fun parseSource(element: JsonElement, index: Int): BookSource {
        if (!element.isJsonObject) {
            throw NoStackTraceException("第 $index 项不是书源对象")
        }
        val url = element.asJsonObject.get("bookSourceUrl")
        if (url == null || !url.isJsonPrimitive || !url.asJsonPrimitive.isString ||
            url.asString.isBlank()
        ) {
            throw NoStackTraceException("第 $index 项不是有效书源：缺少书源地址 bookSourceUrl")
        }
        return try {
            GSON.fromJson(element, BookSource::class.java)
        } catch (e: JsonParseException) {
            throw NoStackTraceException("第 $index 个书源字段格式错误，请检查书源规则")
        }
    }
}
