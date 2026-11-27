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

    fun parse(text: String): Content = parse(text.reader())

    fun parse(reader: Reader): Content = parse(reader, 0)

    private fun parse(reader: Reader, depth: Int): Content {
        if (depth >= 8) throw NoStackTraceException("书源 JSON 字符串嵌套层数过多")
        val input = reader.buffered()
        var first: Int
        do {
            input.mark(1)
            first = input.read()
        } while (first >= 0 && (first.toChar().isWhitespace() || first.toChar() == '\uFEFF'))
        if (first < 0) throw NoStackTraceException("书源内容为空")
        input.reset()
        when (first.toChar()) {
            '<' -> throw NoStackTraceException("导入内容是网页，不是书源 JSON，请使用书源文件或直链")
            '{', '[', '"' -> Unit
            else -> {
                val script = input.readText()
                if (!io.wanjuan.app.model.jsSource.JsSourceConfig.looksLikeScript(script)) {
                    throw NoStackTraceException("导入内容不是书源 JSON，请检查文件或链接返回的内容")
                }
                return Content(sources = listOf(io.wanjuan.app.model.jsSource.JsSourceConfig.parse(script)))
            }
        }
        val json = GSON.newJsonReader(input).apply { strictness = com.google.gson.Strictness.LENIENT }
        try {
            val result = when (first.toChar()) {
                '"' -> parse(json.nextString().reader(), depth + 1)
                '[' -> {
                    json.beginArray()
                    val items = mutableListOf<BookSource>()
                    var index = 0
                    while (json.hasNext()) {
                        index++
                        val item = GSON.fromJson<JsonElement>(json, JsonElement::class.java)
                        if (!item.isJsonNull) items.add(parseSource(item, index))
                    }
                    json.endArray()
                    Content(sources = items)
                }
                else -> parseObject(GSON.fromJson(json, JsonElement::class.java))
            }
            if (json.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) {
                throw JsonParseException("trailing data")
            }
            return result
        } catch (e: JsonParseException) {
            throw NoStackTraceException("书源 JSON 格式错误，请检查文件是否完整")
        } catch (e: java.io.EOFException) {
            throw NoStackTraceException("书源 JSON 格式错误，请检查文件是否完整")
        } catch (e: com.google.gson.stream.MalformedJsonException) {
            throw NoStackTraceException("书源 JSON 格式错误，请检查文件是否完整")
        }
    }

    private fun parseObject(root: JsonElement): Content {
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
