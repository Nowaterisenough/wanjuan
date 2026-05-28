package io.wanjuan.app.ui.book.read

import android.content.Context
import android.graphics.Color
import io.wanjuan.app.R
import io.wanjuan.app.constant.PageAnim
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.ReadBookConfig
import io.wanjuan.app.help.config.ReadTipConfig
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.putPrefString
import kotlin.math.abs

data class ReadMenuThemeSuite(
    val name: String,
    val backgroundColor: Int,
    val textColor: Int,
    val bgType: Int,
    val bgValue: String,
    val textSize: Int,
    val textWeight: Int,
    val textBold: Int = 0,
    val textFont: String? = "",
    val systemTypeface: Int = 0,
    val letterSpacing: Float,
    val lineSpacingExtra: Int,
    val paragraphSpacing: Int,
    val paddingTop: Int = 0,
    val paddingBottom: Int = 0,
    val paddingLeft: Int = 0,
    val paddingRight: Int = 0,
    val titleTopSpacing: Int = 0,
    val titleBottomSpacing: Int = 0,
    val titleSize: Int = 0,
    val titleMode: Int = 0,
    val headerMode: Int = 0,
    val headerPaddingTop: Int = 0,
    val headerPaddingBottom: Int = 0,
    val headerPaddingLeft: Int = 0,
    val headerPaddingRight: Int = 0,
    val showHeaderLine: Boolean = false,
    val footerMode: Int = 0,
    val footerPaddingTop: Int = 0,
    val footerPaddingBottom: Int = 0,
    val footerPaddingLeft: Int = 0,
    val footerPaddingRight: Int = 0,
    val showFooterLine: Boolean = false,
    val pageAnim: Int,
    val pageAnimationSpeed: Int,
    val bgBrightness: Int,
    val bgSaturation: Int,
    val bgAlpha: Int,
    val paperInkStrength: Int,
    val createdAt: Long = System.currentTimeMillis()
) {

    fun matchesCurrentTheme(): Boolean {
        return matches(captureCurrent(name))
    }

    fun matches(other: ReadMenuThemeSuite): Boolean {
        return textColor == other.textColor &&
                bgType == other.bgType &&
                bgValue.equals(other.bgValue, ignoreCase = true) &&
                textSize == other.textSize &&
                textWeight == other.textWeight &&
                textBold == other.textBold &&
                textFont.orEmpty() == other.textFont.orEmpty() &&
                systemTypeface == other.systemTypeface &&
                abs(letterSpacing - other.letterSpacing) < 0.01f &&
                lineSpacingExtra == other.lineSpacingExtra &&
                paragraphSpacing == other.paragraphSpacing &&
                paddingTop == other.paddingTop &&
                paddingBottom == other.paddingBottom &&
                paddingLeft == other.paddingLeft &&
                paddingRight == other.paddingRight &&
                titleTopSpacing == other.titleTopSpacing &&
                titleBottomSpacing == other.titleBottomSpacing &&
                titleSize == other.titleSize &&
                titleMode == other.titleMode &&
                headerMode == other.headerMode &&
                headerPaddingTop == other.headerPaddingTop &&
                headerPaddingBottom == other.headerPaddingBottom &&
                headerPaddingLeft == other.headerPaddingLeft &&
                headerPaddingRight == other.headerPaddingRight &&
                showHeaderLine == other.showHeaderLine &&
                footerMode == other.footerMode &&
                footerPaddingTop == other.footerPaddingTop &&
                footerPaddingBottom == other.footerPaddingBottom &&
                footerPaddingLeft == other.footerPaddingLeft &&
                footerPaddingRight == other.footerPaddingRight &&
                showFooterLine == other.showFooterLine &&
                pageAnim == other.pageAnim &&
                pageAnimationSpeed == other.pageAnimationSpeed &&
                bgBrightness == other.bgBrightness &&
                bgSaturation == other.bgSaturation &&
                bgAlpha == other.bgAlpha &&
                paperInkStrength == other.paperInkStrength
    }

    fun summaryText(context: Context): String {
        return context.getString(
            R.string.read_menu_theme_suite_summary,
            context.getString(R.string.read_menu_theme_layout_custom, textSize),
            context.getString(pageTurnLabelRes()),
            backgroundLabel(context)
        )
    }

    fun applyToReader() {
        ReadBookConfig.durConfig.setCurBg(bgType, bgValue)
        ReadBookConfig.durConfig.setCurTextColor(textColor)
        ReadBookConfig.textSize = textSize
        ReadBookConfig.textWeight = textWeight
        ReadBookConfig.textBold = textBold
        ReadBookConfig.textFont = textFont.orEmpty()
        AppConfig.systemTypefaces = systemTypeface
        ReadBookConfig.letterSpacing = letterSpacing
        ReadBookConfig.lineSpacingExtra = lineSpacingExtra
        ReadBookConfig.paragraphSpacing = paragraphSpacing
        ReadBookConfig.paddingTop = paddingTop
        ReadBookConfig.paddingBottom = paddingBottom
        ReadBookConfig.paddingLeft = paddingLeft
        ReadBookConfig.paddingRight = paddingRight
        ReadBookConfig.titleTopSpacing = titleTopSpacing
        ReadBookConfig.titleBottomSpacing = titleBottomSpacing
        ReadBookConfig.titleSize = titleSize
        ReadBookConfig.titleMode = titleMode
        ReadTipConfig.headerMode = headerMode
        ReadBookConfig.headerPaddingTop = headerPaddingTop
        ReadBookConfig.headerPaddingBottom = headerPaddingBottom
        ReadBookConfig.headerPaddingLeft = headerPaddingLeft
        ReadBookConfig.headerPaddingRight = headerPaddingRight
        ReadBookConfig.showHeaderLine = showHeaderLine
        ReadTipConfig.footerMode = footerMode
        ReadBookConfig.footerPaddingTop = footerPaddingTop
        ReadBookConfig.footerPaddingBottom = footerPaddingBottom
        ReadBookConfig.footerPaddingLeft = footerPaddingLeft
        ReadBookConfig.footerPaddingRight = footerPaddingRight
        ReadBookConfig.showFooterLine = showFooterLine
        ReadBookConfig.pageAnim = pageAnim
        ReadBook.book?.setPageAnim(null)
        ReadBook.saveRead()
        AppConfig.pageAnimationSpeed = pageAnimationSpeed
        ReadBookConfig.bgBrightness = bgBrightness
        ReadBookConfig.bgSaturation = bgSaturation
        ReadBookConfig.bgAlpha = bgAlpha
        ReadBookConfig.paperInkStrength = paperInkStrength
    }

    private fun pageTurnLabelRes(): Int {
        return when (pageAnim) {
            PageAnim.linkedCoverPageAnim -> R.string.page_anim_linked_cover
            PageAnim.slidePageAnim -> R.string.page_anim_slide
            PageAnim.simulationPageAnim -> R.string.page_anim_simulation
            PageAnim.scrollPageAnim -> R.string.page_anim_scroll
            PageAnim.noAnim -> R.string.page_anim_none
            else -> R.string.page_anim_cover
        }
    }

    private fun backgroundLabel(context: Context): String {
        return when (bgType) {
            0 -> context.getString(R.string.read_menu_theme_bg_color)
            1 -> bgValue.substringBeforeLast(".")
            else -> context.getString(R.string.read_menu_theme_bg_custom)
        }
    }

    companion object {
        fun captureCurrent(name: String): ReadMenuThemeSuite {
            val bgType = ReadBookConfig.durConfig.curBgType()
            val bgValue = ReadBookConfig.durConfig.curBgStr()
            val backgroundColor = if (bgType == 0) {
                runCatching { Color.parseColor(bgValue) }
                    .getOrDefault(ReadBookConfig.bgMeanColor)
            } else {
                ReadBookConfig.bgMeanColor.takeIf { it != 0 } ?: Color.TRANSPARENT
            }
            return ReadMenuThemeSuite(
                name = name,
                backgroundColor = backgroundColor,
                textColor = ReadBookConfig.durConfig.curTextColor(),
                bgType = bgType,
                bgValue = bgValue,
                textSize = ReadBookConfig.textSize,
                textWeight = ReadBookConfig.textWeight,
                textBold = ReadBookConfig.textBold,
                textFont = ReadBookConfig.textFont,
                systemTypeface = AppConfig.systemTypefaces,
                letterSpacing = ReadBookConfig.letterSpacing,
                lineSpacingExtra = ReadBookConfig.lineSpacingExtra,
                paragraphSpacing = ReadBookConfig.paragraphSpacing,
                paddingTop = ReadBookConfig.paddingTop,
                paddingBottom = ReadBookConfig.paddingBottom,
                paddingLeft = ReadBookConfig.paddingLeft,
                paddingRight = ReadBookConfig.paddingRight,
                titleTopSpacing = ReadBookConfig.titleTopSpacing,
                titleBottomSpacing = ReadBookConfig.titleBottomSpacing,
                titleSize = ReadBookConfig.titleSize,
                titleMode = ReadBookConfig.titleMode,
                headerMode = ReadTipConfig.headerMode,
                headerPaddingTop = ReadBookConfig.headerPaddingTop,
                headerPaddingBottom = ReadBookConfig.headerPaddingBottom,
                headerPaddingLeft = ReadBookConfig.headerPaddingLeft,
                headerPaddingRight = ReadBookConfig.headerPaddingRight,
                showHeaderLine = ReadBookConfig.showHeaderLine,
                footerMode = ReadTipConfig.footerMode,
                footerPaddingTop = ReadBookConfig.footerPaddingTop,
                footerPaddingBottom = ReadBookConfig.footerPaddingBottom,
                footerPaddingLeft = ReadBookConfig.footerPaddingLeft,
                footerPaddingRight = ReadBookConfig.footerPaddingRight,
                showFooterLine = ReadBookConfig.showFooterLine,
                pageAnim = ReadBookConfig.pageAnim,
                pageAnimationSpeed = AppConfig.pageAnimationSpeed,
                bgBrightness = ReadBookConfig.bgBrightness,
                bgSaturation = ReadBookConfig.bgSaturation,
                bgAlpha = ReadBookConfig.bgAlpha,
                paperInkStrength = ReadBookConfig.paperInkStrength
            )
        }

        fun fromPreset(name: String, preset: ReadMenuThemePreset): ReadMenuThemeSuite {
            return ReadMenuThemeSuite(
                name = name,
                backgroundColor = preset.backgroundColor,
                textColor = preset.textColor,
                bgType = preset.bgType,
                bgValue = preset.bgValue,
                textSize = preset.textSize,
                textWeight = preset.textWeight,
                textBold = preset.textBold,
                textFont = preset.textFont,
                systemTypeface = preset.systemTypeface,
                letterSpacing = preset.letterSpacing,
                lineSpacingExtra = preset.lineSpacingExtra,
                paragraphSpacing = preset.paragraphSpacing,
                paddingTop = preset.paddingTop,
                paddingBottom = preset.paddingBottom,
                paddingLeft = preset.paddingLeft,
                paddingRight = preset.paddingRight,
                titleTopSpacing = preset.titleTopSpacing,
                titleBottomSpacing = preset.titleBottomSpacing,
                titleSize = preset.titleSize,
                titleMode = preset.titleMode,
                headerMode = preset.headerMode,
                headerPaddingTop = preset.headerPaddingTop,
                headerPaddingBottom = preset.headerPaddingBottom,
                headerPaddingLeft = preset.headerPaddingLeft,
                headerPaddingRight = preset.headerPaddingRight,
                showHeaderLine = preset.showHeaderLine,
                footerMode = preset.footerMode,
                footerPaddingTop = preset.footerPaddingTop,
                footerPaddingBottom = preset.footerPaddingBottom,
                footerPaddingLeft = preset.footerPaddingLeft,
                footerPaddingRight = preset.footerPaddingRight,
                showFooterLine = preset.showFooterLine,
                pageAnim = preset.pageAnim,
                pageAnimationSpeed = preset.pageAnimationSpeed,
                bgBrightness = preset.bgBrightness,
                bgSaturation = preset.bgSaturation,
                bgAlpha = preset.bgAlpha,
                paperInkStrength = preset.paperInkStrength
            )
        }
    }
}

object ReadMenuThemeSuiteStore {
    private const val PREF_KEY = "readMenuThemeSuites"
    private const val ACTIVE_PREF_KEY = "readMenuThemeActiveSuiteCreatedAt"
    private const val MAX_SUITES = 12

    fun load(context: Context): List<ReadMenuThemeSuite> {
        val raw = context.getPrefString(PREF_KEY).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return GSON.fromJsonArray<ReadMenuThemeSuite>(raw)
            .getOrElse { emptyList() }
    }

    fun save(context: Context, suite: ReadMenuThemeSuite) {
        val suites = load(context)
        val updatedSuites = if (suites.any { it.createdAt == suite.createdAt }) {
            suites.map {
                if (it.createdAt == suite.createdAt) suite else it
            }
        } else {
            suites + suite
        }.takeLast(MAX_SUITES)
        context.putPrefString(PREF_KEY, GSON.toJson(updatedSuites))
    }

    fun rename(context: Context, suite: ReadMenuThemeSuite, name: String): ReadMenuThemeSuite? {
        var renamed: ReadMenuThemeSuite? = null
        val suites = load(context).map {
            if (it.createdAt == suite.createdAt) {
                it.copy(name = name).also { newSuite -> renamed = newSuite }
            } else {
                it
            }
        }
        context.putPrefString(PREF_KEY, GSON.toJson(suites))
        return renamed
    }

    fun matchingSuite(context: Context, suite: ReadMenuThemeSuite): ReadMenuThemeSuite? {
        return load(context).firstOrNull { it.matches(suite) }
    }

    fun saveOrSelectExisting(context: Context, suite: ReadMenuThemeSuite): ReadMenuThemeSuite {
        val existing = matchingSuite(context, suite)
        return if (existing != null) {
            select(context, existing)
            existing
        } else {
            save(context, suite)
            select(context, suite)
            suite
        }
    }

    fun select(context: Context, suite: ReadMenuThemeSuite) {
        context.putPrefString(ACTIVE_PREF_KEY, suite.createdAt.toString())
    }

    fun clearSelection(context: Context) {
        context.putPrefString(ACTIVE_PREF_KEY, "")
    }

    fun updateActiveFromCurrent(context: Context): Boolean {
        val activeCreatedAt = context.getPrefString(ACTIVE_PREF_KEY)
            .orEmpty()
            .toLongOrNull()
            ?: return false
        val suites = load(context)
        val active = suites.firstOrNull { it.createdAt == activeCreatedAt } ?: return false
        val updated = captureCurrent(active.name).copy(createdAt = active.createdAt)
        val updatedSuites = suites.map { suite ->
            if (suite.createdAt == active.createdAt) updated else suite
        }
        context.putPrefString(PREF_KEY, GSON.toJson(updatedSuites))
        select(context, updated)
        return true
    }

    fun explicitSavedIndex(context: Context, suites: List<ReadMenuThemeSuite>): Int {
        val activeCreatedAt = context.getPrefString(ACTIVE_PREF_KEY).orEmpty().toLongOrNull()
        return suites.indexOfFirst {
            it.createdAt == activeCreatedAt && it.matchesCurrentTheme()
        }
    }

    fun selectedSavedIndex(context: Context, suites: List<ReadMenuThemeSuite>): Int {
        val activeIndex = explicitSavedIndex(context, suites)
        return activeIndex.takeIf { it != -1 }
            ?: suites.indexOfFirst { it.matchesCurrentTheme() }
    }

    fun delete(context: Context, suite: ReadMenuThemeSuite) {
        val suites = load(context).filterNot { it.createdAt == suite.createdAt }
        context.putPrefString(PREF_KEY, GSON.toJson(suites))
        clearSelection(context)
    }

    fun captureCurrent(name: String): ReadMenuThemeSuite {
        return ReadMenuThemeSuite.captureCurrent(name)
    }

    fun captureDefaultPreset(name: String, preset: ReadMenuThemePreset): ReadMenuThemeSuite {
        return ReadMenuThemeSuite.fromPreset(name, preset)
    }
}
