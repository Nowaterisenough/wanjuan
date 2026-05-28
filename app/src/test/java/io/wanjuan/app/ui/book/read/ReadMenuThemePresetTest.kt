package io.wanjuan.app.ui.book.read

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadMenuThemePresetTest {

    @Test
    fun defaultPresetsMatchReferenceSheet() {
        val presets = ReadMenuThemePreset.defaultPresets()

        assertEquals(1, presets.size)
        assertEquals("default", presets[0].key)
        assertTrue(presets.all { it.previewTitle.isNotBlank() })
        assertTrue(presets.all { it.previewBody.isNotBlank() })
    }

    @Test
    fun onlyBuiltInThemeUsesRequestedReaderDefaults() {
        val preset = ReadMenuThemePreset.defaultPresets().single()

        assertEquals("", preset.textFont)
        assertEquals(0, preset.systemTypeface)
        assertEquals(17, preset.textSize)
        assertEquals(50, preset.textWeight)
        assertEquals(0f, preset.letterSpacing, 0f)
        assertEquals(13, preset.lineSpacingExtra)
        assertEquals(8, preset.paragraphSpacing)
        assertEquals(10, preset.paddingTop)
        assertEquals(5, preset.paddingBottom)
        assertEquals(20, preset.paddingLeft)
        assertEquals(20, preset.paddingRight)
        assertEquals(5, preset.titleTopSpacing)
        assertEquals(0, preset.titleBottomSpacing)
        assertEquals(15, preset.titleSize)
        assertEquals(0, preset.titleMode)
        assertEquals(0, preset.headerMode)
        assertEquals(10, preset.headerPaddingTop)
        assertEquals(0, preset.headerPaddingBottom)
        assertEquals(false, preset.showHeaderLine)
        assertEquals(0, preset.footerMode)
        assertEquals(0, preset.footerPaddingTop)
        assertEquals(0, preset.footerPaddingBottom)
        assertEquals(false, preset.showFooterLine)
        assertEquals(1, preset.bgType)
        assertEquals("羊皮纸4.jpg", preset.bgValue)
        assertEquals(45, preset.bgBrightness)
        assertEquals(45, preset.bgSaturation)
        assertEquals(90, preset.bgAlpha)
    }

    @Test
    fun bundledReadConfigContainsOnlyRequestedDefaultTheme() {
        val configFile = repoFile("app/src/main/assets/defaultData/readConfig.json")
        val root = JsonParser.parseString(configFile.readText()).asJsonArray
        val config = root.single().asJsonObject

        assertEquals(1, root.size())
        assertEquals("", config["textFont"].asString)
        assertEquals(17, config["textSize"].asInt)
        assertEquals(50, config["textWeight"].asInt)
        assertEquals(0f, config["letterSpacing"].asFloat, 0f)
        assertEquals(13, config["lineSpacingExtra"].asInt)
        assertEquals(8, config["paragraphSpacing"].asInt)
        assertEquals(10, config["paddingTop"].asInt)
        assertEquals(5, config["paddingBottom"].asInt)
        assertEquals(20, config["paddingLeft"].asInt)
        assertEquals(20, config["paddingRight"].asInt)
        assertEquals(5, config["titleTopSpacing"].asInt)
        assertEquals(0, config["titleBottomSpacing"].asInt)
        assertEquals(15, config["titleSize"].asInt)
        assertEquals(0, config["titleMode"].asInt)
        assertEquals(0, config["headerMode"].asInt)
        assertEquals(10, config["headerPaddingTop"].asInt)
        assertEquals(0, config["headerPaddingBottom"].asInt)
        assertEquals(false, config["showHeaderLine"].asBoolean)
        assertEquals(0, config["footerMode"].asInt)
        assertEquals(0, config["footerPaddingTop"].asInt)
        assertEquals(0, config["footerPaddingBottom"].asInt)
        assertEquals(false, config["showFooterLine"].asBoolean)
        assertEquals(1, config["bgType"].asInt)
        assertEquals("羊皮纸4.jpg", config["bgStr"].asString)
        assertEquals(45, config["bgBrightness"].asInt)
        assertEquals(45, config["bgSaturation"].asInt)
        assertEquals(90, config["bgAlpha"].asInt)
    }

    private fun repoFile(path: String): File {
        return sequenceOf(
            File(path),
            File("..", path),
            File("../..", path)
        ).firstOrNull { it.exists() } ?: error("$path not found")
    }

    @Test
    fun contrastProgressDarkensTextOnLightBackground() {
        val result = ReadMenuThemePreset.contrastTextColor(
            baseTextColor = 0xFF444444.toInt(),
            backgroundColor = 0xFFF6F6F6.toInt(),
            progress = 75
        )

        assertEquals(0xFF333333.toInt(), result)
    }

    @Test
    fun contrastProgressLightensTextOnDarkBackground() {
        val result = ReadMenuThemePreset.contrastTextColor(
            baseTextColor = 0xFFBBBBBB.toInt(),
            backgroundColor = 0xFF101010.toInt(),
            progress = 75
        )

        assertEquals(0xFFD2D2D2.toInt(), result)
    }
}
