package io.wanjuan.app.help.source

import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TestBookSourceImportParser {
    private val source = """{"bookSourceUrl":"https://example.org","bookSourceName":"Example"}"""

    @Test
    fun stringEncodedArrayReproducesReportedErrorAndImportsSuccessfully() {
        val encoded = GSON.toJson("[$source]")
        val oldError = GSON.fromJsonArray<BookSource>(encoded.byteInputStream()).exceptionOrNull()
        assertTrue(oldError?.message.orEmpty().contains("Expected BEGIN_ARRAY but was STRING"))

        assertEquals("https://example.org", BookSourceImportParser.parse(encoded).sources.single().bookSourceUrl)
        assertEquals("Example", BookSourceImportParser.parse(encoded.reader()).sources.single().bookSourceName)
    }

    @Test
    fun objectAndArrayWorkForBothTextAndReaderInputs() {
        for (json in listOf(source, "[$source]", GSON.toJson(source))) {
            assertEquals("Example", BookSourceImportParser.parse(json).sources.single().bookSourceName)
            assertEquals("Example", BookSourceImportParser.parse(json.reader()).sources.single().bookSourceName)
        }
    }

    @Test
    fun bomAndWhitespaceAreAcceptedInsideAndOutsideStringEncoding() {
        val encoded = " \uFEFF\n" + GSON.toJson("\uFEFF [$source] \n")
        assertEquals(1, BookSourceImportParser.parse(encoded).sources.size)
    }

    @Test
    fun nestedRuleStringsAndScriptsArePreserved() {
        val script = "@js: var text = \"quoted\";\ntext.replace(/\\s+/g, '\\n');"
        val json = GSON.toJson(mapOf(
            "bookSourceUrl" to "https://example.org",
            "ruleContent" to GSON.toJson(mapOf("content" to script))
        ))
        assertEquals(script, BookSourceImportParser.parse(GSON.toJson(json)).sources.single().ruleContent?.content)
    }

    @Test
    fun trailingCommaAndNullArrayEntriesRemainCompatible() {
        assertEquals(1, BookSourceImportParser.parse("[null,$source,]").sources.size)
    }

    @Test
    fun everyArrayEntryMustBeASource() {
        val error = assertThrows(NoStackTraceException::class.java) {
            BookSourceImportParser.parse("[$source,{}]")
        }
        assertTrue(error.message.orEmpty().contains("2"))
        assertTrue(error.message.orEmpty().contains("bookSourceUrl"))
        for (entry in listOf("true", "42", "\"text\"", "[]")) {
            assertThrows(NoStackTraceException::class.java) {
                BookSourceImportParser.parse("[$source,$entry]")
            }
        }
    }

    @Test
    fun sourceAddressMustBeANonBlankString() {
        for (value in listOf("null", "123", "{}", "[]", "\" \"")) {
            assertThrows(NoStackTraceException::class.java) {
                BookSourceImportParser.parse("{\"bookSourceUrl\":$value}")
            }
        }
    }

    @Test
    fun sourceUrlListsAreSupportedAndValidated() {
        val json = """{"sourceUrls":[" https://example.org/a.json ","https://example.org/b.json#requestWithoutUA"]}"""
        val result = BookSourceImportParser.parse(GSON.toJson(json))
        assertEquals(listOf("https://example.org/a.json", "https://example.org/b.json#requestWithoutUA"), result.sourceUrls)
        assertTrue(result.sources.isEmpty())
        for (urls in listOf("null", "\"https://example.org\"", "[null]", "[42]", "[\"file:///private.json\"]")) {
            assertThrows(NoStackTraceException::class.java) {
                BookSourceImportParser.parse("{\"sourceUrls\":$urls}")
            }
        }
    }

    @Test
    fun htmlAndPlainTextErrorsDoNotExposeGsonStackMessages() {
        for (text in listOf("", " ", "Not Found", "<html>Access denied</html>", GSON.toJson("Forbidden"), "null", "true", "42")) {
            val error = assertThrows(NoStackTraceException::class.java) {
                BookSourceImportParser.parse(text)
            }
            assertTrue(error.message.orEmpty().isNotBlank())
            assertTrue(!error.message.orEmpty().contains("BEGIN_ARRAY"))
        }
    }

    @Test
    fun malformedJsonAndMalformedRuleFieldsFailClearly() {
        for (text in listOf("[$source", "{\"bookSourceUrl\":", source + " garbage",
            """{"bookSourceUrl":"https://example.org","enabled":{}}""")) {
            assertThrows(NoStackTraceException::class.java) {
                BookSourceImportParser.parse(text)
            }
        }
    }

    @Test
    fun excessiveStringEncodingIsBounded() {
        var encoded = source
        repeat(9) { encoded = GSON.toJson(encoded) }
        assertThrows(NoStackTraceException::class.java) { BookSourceImportParser.parse(encoded) }
    }

    @Test
    fun emptyArrayDoesNotProduceAnInvalidSource() {
        assertTrue(BookSourceImportParser.parse("[]").sources.isEmpty())
    }
}
