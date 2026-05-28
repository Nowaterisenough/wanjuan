package io.wanjuan.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.wanjuan.app.R
import io.wanjuan.app.lib.theme.Selector
import io.wanjuan.app.lib.theme.ThemeStore
import io.wanjuan.app.lib.theme.UiCorner
import io.wanjuan.app.lib.theme.bottomBackground
import io.wanjuan.app.utils.ColorUtils
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.getCompatColor

class AccentStrokeTextView(context: Context, attrs: AttributeSet) :
    AppCompatTextView(context, attrs) {

    private var radius = 3.dpToPx()
    private val isBottomBackground: Boolean

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AccentStrokeTextView)
        radius = (typedArray.getDimensionPixelOffset(R.styleable.StrokeTextView_radius, radius) *
            UiCorner.scale()).toInt()
        isBottomBackground =
            typedArray.getBoolean(R.styleable.StrokeTextView_isBottomBackground, false)
        typedArray.recycle()
        upStyle()
    }

    private fun upStyle() {
        val isLight = ColorUtils.isColorLight(context.bottomBackground)
        val disableColor = if (isBottomBackground) {
            if (isLight) {
                context.getCompatColor(R.color.md_light_disabled)
            } else {
                context.getCompatColor(R.color.md_dark_disabled)
            }
        } else {
            context.getCompatColor(R.color.disabled)
        }
        val accentColor = if (isInEditMode) {
            context.getCompatColor(R.color.accent)
        } else {
            ThemeStore.accentColor(context)
        }
        background = Selector.shapeBuild()
            .setCornerRadius(radius)
            .setStrokeWidth(1.dpToPx())
            .setDisabledStrokeColor(disableColor)
            .setDefaultStrokeColor(accentColor)
            .setPressedBgColor(context.getCompatColor(R.color.transparent30))
            .create()
        setTextColor(
            Selector.colorBuild()
                .setDefaultColor(accentColor)
                .setDisabledColor(disableColor)
                .create()
        )
    }

}
