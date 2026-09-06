package io.wanjuan.app.ui.book.read.config

import android.content.Context
import android.graphics.drawable.GradientDrawable
import io.wanjuan.app.lib.theme.bottomBackground
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.lib.theme.UiCorner
import io.wanjuan.app.utils.dpToPx

object ReaderSheetStyle {

    data class Palette(
        val surface: Int,
        val panel: Int,
        val panelStrong: Int,
        val stroke: Int,
        val textColor: Int,
        val secondaryTextColor: Int,
        val primaryColor: Int,
        val accentColor: Int
    )

    fun resolve(context: Context, baseColor: Int = context.bottomBackground): Palette {
        val dark = AppConfig.isNightTheme
        if (AppConfig.isEInkMode) {
            return Palette(-1, -1, 0xffeeeeee.toInt(), 0xff666666.toInt(),
                0xff000000.toInt(), 0xff333333.toInt(), 0xff000000.toInt(), 0xff000000.toInt())
        }
        return Palette(
            surface = if (dark) 0xff282f35.toInt() else 0xfff8faf8.toInt(),
            panel = if (dark) 0xff333c43.toInt() else 0xffedf1ee.toInt(),
            panelStrong = if (dark) 0xff263e60.toInt() else 0xffe5efff.toInt(),
            stroke = if (dark) 0xff414a50.toInt() else 0xffdce1dd.toInt(),
            textColor = if (dark) 0xffe0e5e5.toInt() else 0xff303934.toInt(),
            secondaryTextColor = if (dark) 0xffa0abb0.toInt() else 0xff77817e.toInt(),
            primaryColor = 0xff006eff.toInt(),
            accentColor = 0xff006eff.toInt()
        )
    }

    fun topSheetDrawable(palette: Palette, radiusDp: Float = 10f): GradientDrawable {
        val radius = UiCorner.scaledDp(radiusDp)
        return GradientDrawable().apply {
            cornerRadii = floatArrayOf(
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
            )
            setColor(palette.surface)
            setStroke(1.dpToPx(), palette.stroke)
        }
    }

    fun blockDrawable(fillColor: Int, strokeColor: Int, radiusDp: Float = 10f): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = UiCorner.scaledDp(radiusDp)
            setColor(fillColor)
            setStroke(1.dpToPx(), strokeColor)
        }
    }
}
