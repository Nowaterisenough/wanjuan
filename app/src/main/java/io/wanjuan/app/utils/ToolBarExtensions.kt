@file:Suppress("unused")

package io.wanjuan.app.utils

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import io.wanjuan.app.R

/**
 * Apply the shared top bar icon family, size, and tint.
 */
fun Toolbar.setMoreIconColor(color: Int) {
    overflowIcon = AppCompatResources.getDrawable(context, R.drawable.ic_lucide_more_vertical)
    applyTopBarIconMetrics(color)
}

fun Toolbar.applyTopBarIconMetrics(@ColorInt tintColor: Int? = null) {
    val iconSize = resources.getDimensionPixelSize(R.dimen.read_top_bar_icon_size)
    navigationIcon = navigationIcon?.mutate()?.applyTopBarIconMetrics(iconSize, tintColor)
    overflowIcon = overflowIcon?.mutate()?.applyTopBarIconMetrics(iconSize, tintColor)
    menu.children.forEach { item ->
        item.icon = item.icon?.mutate()?.applyTopBarIconMetrics(iconSize, tintColor)
    }
}

private fun Drawable.applyTopBarIconMetrics(iconSize: Int, @ColorInt tintColor: Int?): Drawable {
    setBounds(0, 0, iconSize, iconSize)
    tintColor?.let {
        colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_ATOP)
    }
    return this
}
