package io.wanjuan.app.ui.widget.text

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.wanjuan.app.R
import io.wanjuan.app.lib.theme.Selector
import io.wanjuan.app.lib.theme.ThemeStore
import io.wanjuan.app.lib.theme.UiCorner
import io.wanjuan.app.utils.ColorUtils
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.getCompatColor

class AccentBgTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private var radius = 0

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AccentBgTextView)
        radius = (typedArray.getDimensionPixelOffset(R.styleable.AccentBgTextView_radius, radius) *
            UiCorner.scale()).toInt()
        typedArray.recycle()
        upBackground()
    }

    fun setRadius(radius: Int) {
        this.radius = radius.dpToPx()
        upBackground()
    }

    private fun upBackground() {
        val accentColor = if (isInEditMode) {
            context.getCompatColor(R.color.accent)
        } else {
            ThemeStore.accentColor(context)
        }
        background = Selector.shapeBuild()
            .setCornerRadius(radius)
            .setDefaultBgColor(accentColor)
            .setPressedBgColor(ColorUtils.darkenColor(accentColor))
            .create()
        setTextColor(
            if (ColorUtils.isColorLight(accentColor)) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        )
    }
}
