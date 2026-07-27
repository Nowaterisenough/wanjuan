package io.wanjuan.app.utils

import com.script.ScriptBindings
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.rule.ContentRule
import org.mozilla.javascript.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ImageUtilsPrivacyTest {

    private val model = "https://images.example/a.jpg?token=query-secret," +
        "{\"fallbackUrls\":[\"https://mirror.example/a.jpg\"]," +
        "\"headers\":{\"Cookie\":\"cookie-secret\"}}"

    @Test
    fun byteArrayDecodeKeepsOriginalSrcInScriptAndLogsOnlyHostAndExceptionClass() {
        val source = throwingSource()
        val logs = mutableListOf<String>()
        var observedSrc: String? = null

        val result = ImageUtils.decode(
            model,
            byteArrayOf(1),
            false,
            source,
            null,
            logs::add,
            recordingFailureEvaluator { observedSrc = it },
        )

        assertNull(result)
        assertEquals(model, observedSrc)
        assertSanitized(logs.single())
    }

    @Test
    fun inputStreamDecodeKeepsOriginalSrcInScriptAndLogsOnlyHostAndExceptionClass() {
        val source = throwingSource()
        val logs = mutableListOf<String>()
        var observedSrc: String? = null

        val result = ImageUtils.decode(
            model,
            ByteArrayInputStream(byteArrayOf(1)),
            false,
            source,
            null,
            logs::add,
            recordingFailureEvaluator { observedSrc = it },
        )

        assertNull(result)
        assertEquals(model, observedSrc)
        assertSanitized(logs.single())
    }

    private fun throwingSource() = BookSource(
        bookSourceUrl = "https://source.example",
        ruleContent = ContentRule(
            imageDecode = "decodeRule()"
        ),
    )

    private fun recordingFailureEvaluator(recordSrc: (String) -> Unit) =
        ImageRuleEvaluator { _, configure ->
            val bindings = ScriptBindings().apply(configure)
            val src = Context.jsToJava(
                bindings.get("src", bindings),
                String::class.java,
            ) as String
            recordSrc(src)
            throw IllegalArgumentException("throwable-message-secret")
        }

    private fun assertSanitized(log: String) {
        assertEquals("图片解密失败 host=images.example reason=IllegalArgumentException", log)
        assertFalse(log.contains(model))
        assertFalse(log.contains("https://"))
        assertFalse(log.contains("query-secret"))
        assertFalse(log.contains("fallbackUrls"))
        assertFalse(log.contains("headers"))
        assertFalse(log.contains("Cookie"))
        assertFalse(log.contains("cookie-secret"))
        assertFalse(log.contains("throwable-message-secret"))
        assertFalse(log.contains("at io.wanjuan"))
        assertTrue(log.contains("images.example"))
    }
}
