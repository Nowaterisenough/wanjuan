package io.wanjuan.app.utils

import com.script.ScriptBindings
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.entities.BaseSource
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.RssSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import java.io.InputStream

internal fun interface ImageRuleEvaluator {
    fun evaluate(ruleJs: String, bindingsConfig: ScriptBindings.() -> Unit): Any?
}

/**
 * 加密图片解密工具
 */
object ImageUtils {

    /**
     * @param isCover 根据这个执行书源中不同的解密规则
     * @return 解密失败返回Null 解密规则为空不处理
     */
    fun decode(
        src: String, bytes: ByteArray, isCover: Boolean,
        source: BaseSource?, book: Book? = null
    ): ByteArray? = decode(
        src,
        bytes,
        isCover,
        source,
        book,
        ::logDecodeFailure,
        sourceEvaluator(source),
    )

    internal fun decode(
        src: String,
        bytes: ByteArray,
        isCover: Boolean,
        source: BaseSource?,
        book: Book? = null,
        logFailure: (String) -> Unit,
        evaluator: ImageRuleEvaluator,
    ): ByteArray? {
        val ruleJs = getRuleJs(source, isCover)
        if (ruleJs.isNullOrBlank()) return bytes
        //解密库hutool.crypto ByteArray|InputStream -> ByteArray
        return kotlin.runCatching {
            evaluator.evaluate(ruleJs) {
                put("book", book)
                put("result", bytes)
                put("src", src)
            } as ByteArray
        }.onFailure {
            logFailure(decodeFailureMessage(src, it))
        }.getOrNull()
    }

    fun decode(
        src: String, inputStream: InputStream, isCover: Boolean,
        source: BaseSource?, book: Book? = null
    ): InputStream? = decode(
        src,
        inputStream,
        isCover,
        source,
        book,
        ::logDecodeFailure,
        sourceEvaluator(source),
    )

    internal fun decode(
        src: String,
        inputStream: InputStream,
        isCover: Boolean,
        source: BaseSource?,
        book: Book? = null,
        logFailure: (String) -> Unit,
        evaluator: ImageRuleEvaluator,
    ): InputStream? {
        val ruleJs = getRuleJs(source, isCover)
        if (ruleJs.isNullOrBlank()) return inputStream
        //解密库hutool.crypto ByteArray|InputStream -> ByteArray
        return kotlin.runCatching {
            val bytes = evaluator.evaluate(ruleJs) {
                put("book", book)
                put("result", inputStream)
                put("src", src)
            } as ByteArray
            ByteArrayInputStream(bytes)
        }.onFailure {
            logFailure(decodeFailureMessage(src, it))
        }.getOrNull()
    }

    fun skipDecode(source: BaseSource?, isCover: Boolean): Boolean {
        return getRuleJs(source, isCover).isNullOrBlank()
    }

    private fun getRuleJs(
        source: BaseSource?, isCover: Boolean
    ): String? {
        return when (source) {
            is BookSource ->
                if (isCover) source.coverDecodeJs
                else source.getContentRule().imageDecode

            is RssSource -> source.coverDecodeJs
            else -> null
        }
    }

    private fun logDecodeFailure(message: String) {
        AppLog.putDebug(message)
    }

    private fun sourceEvaluator(source: BaseSource?) = ImageRuleEvaluator { ruleJs, bindingsConfig ->
        source?.evalJS(ruleJs, bindingsConfig)
    }

    private fun decodeFailureMessage(src: String, error: Throwable): String {
        val host = src.substringBefore(",{").toHttpUrlOrNull()?.host
        val hostPart = host?.let { " host=$it" }.orEmpty()
        val exceptionClass = error.javaClass.name.substringAfterLast('.')
        return "图片解密失败$hostPart reason=$exceptionClass"
    }

}
