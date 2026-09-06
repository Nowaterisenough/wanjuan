package io.wanjuan.app.ui.book.read

import io.wanjuan.app.utils.GSON
import org.junit.Assert.*
import org.junit.Test

class TestReadMenuThemePersistence {
    private fun theme() = ReadMenuThemeSuite.fromPreset("测试主题", ReadMenuThemePreset.defaultPresets().single())

    @Test
    fun serializedThemeRetainsTheDefaultFontAndIndependentWeight() {
        val original = theme().copy(textFont = "/fonts/imported.ttf", textWeight = 75)
        val json = GSON.toJson(listOf(original))
        assertTrue(json.contains("\"textFont\""))
        assertTrue(json.contains("\"name\""))
        assertEquals(original, ReadMenuThemeSuiteStore.decode(json).single())
    }

    @Test
    fun incompatibleRecordsDoNotPreventValidThemesFromLoading() {
        val good = GSON.toJson(theme())
        val json = "[null,{}, {\"a\":\"old obfuscated theme\"}, $good]"
        assertEquals(listOf(theme().copy(createdAt = ReadMenuThemeSuiteStore.decode(json).single().createdAt)), ReadMenuThemeSuiteStore.decode(json))
    }

    @Test
    fun incompleteOrBrokenThemesCannotOverwriteTheReaderConfiguration() {
        assertTrue(ReadMenuThemeSuiteStore.decode("{broken").isEmpty())
        assertTrue(ReadMenuThemeSuiteStore.decode("[{\"name\":\"bad\",\"bgValue\":\"#FFFFFF\"}]").isEmpty())
    }
}
