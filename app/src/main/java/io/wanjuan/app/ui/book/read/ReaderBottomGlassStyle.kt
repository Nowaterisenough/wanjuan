package io.wanjuan.app.ui.book.read

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.qmdeve.liquidglass.widget.LiquidGlassView
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.lib.theme.bottomBackground
import io.wanjuan.app.utils.ColorUtils
import io.wanjuan.app.utils.dpToPx

internal object ReaderBottomGlassStyle {

    fun glassLevel(): Float {
        return (AppConfig.frostedGlassLevel / 100f).coerceIn(0.45f, 1f)
    }

    fun shell(context: Context, glassLevel: Float, cornerRadius: Float): GradientDrawable {
        val surfaceColor = glassSurfaceColor(context)
        val topAlpha = (0.32f + glassLevel * 0.44f).coerceIn(0f, 0.86f)
        val centerAlpha = (0.24f + glassLevel * 0.38f).coerceIn(0f, 0.74f)
        val bottomAlpha = (0.18f + glassLevel * 0.32f).coerceIn(0f, 0.66f)
        val strokeAlpha = (0.22f + glassLevel * 0.22f).coerceIn(0f, 0.58f)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.withAlpha(surfaceColor, topAlpha),
                ColorUtils.withAlpha(surfaceColor, centerAlpha),
                ColorUtils.withAlpha(surfaceColor, bottomAlpha)
            )
        ).apply {
            this.cornerRadius = cornerRadius
            setStroke(1.dpToPx(), ColorUtils.withAlpha(surfaceColor, strokeAlpha))
        }
    }

    fun fallbackShell(context: Context, glassLevel: Float, cornerRadius: Float): GradientDrawable {
        val surfaceColor = glassSurfaceColor(context)
        val alpha = (0.24f + glassLevel * 0.38f).coerceIn(0f, 0.74f)
        val strokeAlpha = (0.22f + glassLevel * 0.22f).coerceIn(0f, 0.58f)
        return GradientDrawable().apply {
            this.cornerRadius = cornerRadius
            setColor(ColorUtils.withAlpha(surfaceColor, alpha))
            setStroke(1.dpToPx(), ColorUtils.withAlpha(surfaceColor, strokeAlpha))
        }
    }

    fun configureLiquidGlass(
        liquidGlassView: LiquidGlassView,
        target: ViewGroup,
        cornerRadius: Float,
        bindTarget: Boolean,
        glassLevel: Float = glassLevel()
    ): Boolean {
        if (!ViewCompat.isLaidOut(target) || !ViewCompat.isLaidOut(liquidGlassView)) {
            return false
        }
        if (bindTarget) {
            liquidGlassView.bind(target)
        }
        liquidGlassView.setCornerRadius(cornerRadius)
        liquidGlassView.setRefractionHeight((12f + glassLevel * 10f).dpToPx())
        liquidGlassView.setRefractionOffset((36f + glassLevel * 18f).dpToPx())
        liquidGlassView.setBlurRadius((6f + glassLevel * 12f).dpToPx())
        liquidGlassView.setDispersion((0.18f + glassLevel * 0.16f).coerceAtMost(0.42f))
        liquidGlassView.setTintAlpha(tintAlpha(glassLevel))
        tintColor().let { tintColor ->
            liquidGlassView.setTintColorRed(tintColor[0])
            liquidGlassView.setTintColorGreen(tintColor[1])
            liquidGlassView.setTintColorBlue(tintColor[2])
        }
        liquidGlassView.setDraggableEnabled(false)
        liquidGlassView.setElasticEnabled(false)
        liquidGlassView.setTouchEffectEnabled(false)
        liquidGlassView.isClickable = false
        liquidGlassView.isFocusable = false
        liquidGlassView.invalidate()
        return true
    }

    fun glassSurfaceColor(context: Context): Int {
        val baseColor = context.bottomBackground
        return if (ColorUtils.isColorLight(baseColor)) {
            ColorUtils.blendColors(baseColor, Color.WHITE, 0.72f)
        } else {
            ColorUtils.blendColors(baseColor, Color.BLACK, 0.24f)
        }
    }

    fun tintAlpha(glassLevel: Float): Float {
        return 0.12f + glassLevel * 0.18f
    }

    fun tintColor(): FloatArray {
        return if (useDarkGlass()) {
            floatArrayOf(0.08f, 0.10f, 0.14f)
        } else {
            floatArrayOf(0.70f, 0.79f, 0.86f)
        }
    }

    fun useDarkGlass(): Boolean {
        return AppConfig.isNightTheme && !AppConfig.isEInkMode
    }
}
