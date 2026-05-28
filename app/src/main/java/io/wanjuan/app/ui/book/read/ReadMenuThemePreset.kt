package io.wanjuan.app.ui.book.read

import android.content.Context
import androidx.annotation.StringRes
import io.wanjuan.app.constant.PageAnim
import io.wanjuan.app.R
import kotlin.math.roundToInt

data class ReadMenuThemePreset(
    val key: String,
    @param:StringRes val labelRes: Int,
    val backgroundColor: Int,
    val textColor: Int,
    val bgType: Int = 0,
    val bgValue: String,
    @param:StringRes val layoutLabelRes: Int = R.string.read_menu_theme_layout_balanced,
    @param:StringRes val pageTurnLabelRes: Int = R.string.page_anim_cover,
    @param:StringRes val backgroundLabelRes: Int = labelRes,
    val textSize: Int = 20,
    val textWeight: Int = 50,
    val textBold: Int = 0,
    val textFont: String = "",
    val systemTypeface: Int = 0,
    val letterSpacing: Float = 0.1f,
    val lineSpacingExtra: Int = 12,
    val paragraphSpacing: Int = 2,
    val paddingTop: Int = 6,
    val paddingBottom: Int = 6,
    val paddingLeft: Int = 16,
    val paddingRight: Int = 16,
    val titleTopSpacing: Int = 0,
    val titleBottomSpacing: Int = 0,
    val titleSize: Int = 0,
    val titleMode: Int = 0,
    val headerMode: Int = 0,
    val headerPaddingTop: Int = 0,
    val headerPaddingBottom: Int = 0,
    val headerPaddingLeft: Int = 16,
    val headerPaddingRight: Int = 16,
    val showHeaderLine: Boolean = false,
    val footerMode: Int = 0,
    val footerPaddingTop: Int = 6,
    val footerPaddingBottom: Int = 6,
    val footerPaddingLeft: Int = 16,
    val footerPaddingRight: Int = 16,
    val showFooterLine: Boolean = true,
    val pageAnim: Int = PageAnim.coverPageAnim,
    val pageAnimationSpeed: Int = 300,
    val bgBrightness: Int = 50,
    val bgSaturation: Int = 50,
    val bgAlpha: Int = 100,
    val paperInkStrength: Int = 0,
    val previewTitle: String = "\u7b2c\u4e00\u7ae0 \u6625\u591c",
    val previewBody: String = "\u591c\u8272\u5fae\u51c9\uff0c\u661f\u5149\u6d12\u843d\n\u5728\u5bc2\u9759\u7684\u5ead\u9662\u91cc..."
) {
    fun summaryText(context: Context): String {
        return context.getString(
            R.string.read_menu_theme_suite_summary,
            context.getString(layoutLabelRes),
            context.getString(pageTurnLabelRes),
            context.getString(backgroundLabelRes)
        )
    }

    companion object {
        const val DEFAULT_PREVIEW_TITLE = "\u7b2c\u4e00\u7ae0 \u6625\u591c"
        const val DEFAULT_PREVIEW_BODY = "\u591c\u8272\u5fae\u51c9\uff0c\u661f\u5149\u6d12\u843d\n\u5728\u5bc2\u9759\u7684\u5ead\u9662\u91cc..."

        fun defaultPresets(): List<ReadMenuThemePreset> = listOf(
            ReadMenuThemePreset(
                key = "default",
                labelRes = R.string.btn_default_s,
                backgroundColor = 0xFFF1E2C5.toInt(),
                textColor = 0xFF3E3D3B.toInt(),
                bgType = 1,
                bgValue = "\u7f8a\u76ae\u7eb84.jpg",
                layoutLabelRes = R.string.read_menu_theme_layout_balanced,
                pageTurnLabelRes = R.string.page_anim_cover,
                backgroundLabelRes = R.string.read_menu_theme_paper,
                textSize = 17,
                textWeight = 50,
                textBold = 0,
                textFont = "",
                systemTypeface = 0,
                letterSpacing = 0f,
                lineSpacingExtra = 13,
                paragraphSpacing = 8,
                paddingTop = 10,
                paddingBottom = 5,
                paddingLeft = 20,
                paddingRight = 20,
                titleTopSpacing = 5,
                titleBottomSpacing = 0,
                titleSize = 15,
                titleMode = 0,
                headerMode = 0,
                headerPaddingTop = 10,
                headerPaddingBottom = 0,
                showHeaderLine = false,
                footerMode = 0,
                footerPaddingTop = 0,
                footerPaddingBottom = 0,
                showFooterLine = false,
                bgBrightness = 45,
                bgSaturation = 45,
                bgAlpha = 90
            )
        )

        fun contrastTextColor(baseTextColor: Int, backgroundColor: Int, progress: Int): Int {
            val normalized = ((progress.coerceIn(0, 100) - 50) / 100f).coerceIn(-0.5f, 0.5f)
            val lightBackground = luminance(backgroundColor) >= 0.5f
            val ratio = normalized.coerceAtLeast(0f) * if (lightBackground) 1f else 1.35f
            return if (lightBackground) {
                blend(baseTextColor, 0xFF000000.toInt(), ratio)
            } else {
                blend(baseTextColor, 0xFFFFFFFF.toInt(), ratio)
            }
        }

        private fun blend(from: Int, to: Int, ratio: Float): Int {
            val boundedRatio = ratio.coerceIn(0f, 1f)
            val r = channel(from, 16) +
                    ((channel(to, 16) - channel(from, 16)) * boundedRatio).roundToInt()
            val g = channel(from, 8) +
                    ((channel(to, 8) - channel(from, 8)) * boundedRatio).roundToInt()
            val b = channel(from, 0) +
                    ((channel(to, 0) - channel(from, 0)) * boundedRatio).roundToInt()
            return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or
                    (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }

        private fun channel(color: Int, shift: Int): Int = color shr shift and 0xFF

        private fun luminance(color: Int): Float {
            val r = channel(color, 16) / 255f
            val g = channel(color, 8) / 255f
            val b = channel(color, 0) / 255f
            return 0.299f * r + 0.587f * g + 0.114f * b
        }
    }
}
