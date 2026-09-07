package io.wanjuan.app.ui.book.read

import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.wanjuan.app.ui.book.ProgressMinimapStyle
import io.wanjuan.app.ui.book.read.config.ReaderSheetStyle

fun ViewGroup.applyMinimapChapterNavigationStyle(label: TextView) {
    val colors = ReaderSheetStyle.resolve(context)
    val disabled = intArrayOf(-android.R.attr.state_enabled)
    val pressed = intArrayOf(android.R.attr.state_pressed)
    val focused = intArrayOf(android.R.attr.state_focused)
    val normal = intArrayOf()
    fun surface(active: Boolean) = ProgressMinimapStyle.surfaceDrawable(
        if (active) ColorUtils.blendARGB(colors.surface, colors.accentColor, .16f) else colors.surface,
        if (active) colors.accentColor else colors.stroke
    )

    // Native pressed state handles dragging out, cancellation and disabled buttons without scaling.
    background = StateListDrawable().apply {
        addState(disabled, surface(false))
        addState(pressed, surface(true))
        addState(focused, surface(true))
        addState(normal, surface(false))
    }
    elevation = 0f
    label.isDuplicateParentStateEnabled = true
    label.setTextColor(ColorStateList(
        arrayOf(disabled, pressed, focused, normal),
        intArrayOf(colors.secondaryTextColor, colors.accentTextColor, colors.accentTextColor, colors.textColor)
    ))
}
