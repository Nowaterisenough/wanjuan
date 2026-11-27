package io.wanjuan.app.model.jsSource

import com.google.gson.JsonObject
import io.wanjuan.app.constant.BookSourceType
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.utils.GSON
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory

/** Read metadata in an interpreter without Android or Java host objects. */
internal object JsSourceConfig {
    fun looksLikeScript(text: String) = Regex("""\b(?:var|const|let)\s+(?:config|source)\b""").containsMatchIn(text)

    fun parse(script: String): BookSource {
        val deadline = System.nanoTime() + 3_000_000_000L
        val factory = object : ContextFactory() {
            override fun observeInstructionCount(cx: Context, count: Int) {
                if (System.nanoTime() > deadline) throw NoStackTraceException("书源配置执行超时")
            }
        }
        return factory.call { cx ->
            cx.languageVersion = Context.VERSION_ES6
            cx.optimizationLevel = -1
            cx.instructionObserverThreshold = 4096
            cx.setClassShutter { false }
            val scope = cx.initSafeStandardObjects()
            cx.evaluateString(scope, script, "bookSource", 1, null)
            fun eval(expression: String) = cx.evaluateString(scope, expression, "config", 1, null)
            val json = eval("JSON.stringify(typeof config === 'object' && config.bookSourceUrl ? config : source)").toString()
            val config = GSON.fromJson(json, JsonObject::class.java)
            for (key in listOf("exploreUrl", "loginUi", "jsLib")) {
                config.get(key)?.takeIf { it.isJsonArray || it.isJsonObject }?.let { config.addProperty(key, it.toString()) }
            }
            val source = GSON.fromJson(config, BookSource::class.java)
            require(source.bookSourceUrl.isNotBlank() && source.bookSourceName.isNotBlank()) { "JS 书源缺少地址或名称" }
            val required = if (source.bookSourceType == BookSourceType.file) listOf("search", "getBookInfo") else listOf("search", "getChapters", "getContent")
            for (name in required) require(eval("typeof $name === 'function'") == true) { "JS 书源缺少 $name" }
            if (eval("typeof loginUi === 'function' && typeof loginAction === 'function'") == true) {
                source.loginUi = "{\"version\":2}"
                if (source.loginUrl.isNullOrBlank()) source.loginUrl = "@js:"
            }
            config.get("maxBatchSize")?.takeUnless { it.isJsonNull }?.let {
                require(it.asInt > 0 && eval("typeof getContentBatch === 'function'") == true) { "批量书源需要 getContentBatch 和正数 maxBatchSize" }
                source.getContentRule().maxBatchSize = it.asInt
            }
            source.mainJs = script
            source
        }
    }
}
