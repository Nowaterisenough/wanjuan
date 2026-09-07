@file:Suppress("unused")

package io.wanjuan.app.utils

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import io.wanjuan.app.R
import io.wanjuan.app.lib.theme.ThemeUtils
import io.wanjuan.app.lib.theme.primaryTextColor

/**
 * Apply the shared top bar icon family, size, and tint.
 */
fun Toolbar.setMoreIconColor(color: Int) {
    overflowIcon = AppCompatResources.getDrawable(context, R.drawable.ic_lucide_more_vertical)
    applyTopBarIconMetrics(color)
}

@SuppressLint("RestrictedApi")
fun Toolbar.applyTopBarIconMetrics(@ColorInt tintColor: Int? = null) {
    val iconSize = resources.getDimensionPixelSize(R.dimen.read_top_bar_icon_size)
    tintColor?.let { setTag(R.id.top_bar_icon_tint, it) }
    // Toolbar overlays provide the correct contrast color for light and dark title bars.
    val resolvedTint = getTag(R.id.top_bar_icon_tint) as? Int ?: ThemeUtils.resolveColor(
        context,
        androidx.appcompat.R.attr.colorControlNormal,
        context.primaryTextColor
    )
    navigationIcon = navigationIcon?.mutate()?.applyTopBarIconMetrics(iconSize, resolvedTint)
    overflowIcon = overflowIcon?.mutate()?.applyTopBarIconMetrics(iconSize, resolvedTint)
    menu.children.forEach { item ->
        item.icon?.setBounds(0, 0, iconSize, iconSize)
        item.applyIconTint(
            if ((item as? MenuItemImpl)?.requiresOverflow() == true) {
                context.getCompatColor(R.color.primaryText)
            } else {
                resolvedTint
            }
        )
    }
}

private fun Drawable.applyTopBarIconMetrics(iconSize: Int, @ColorInt tintColor: Int): Drawable {
    setBounds(0, 0, iconSize, iconSize)
    clearColorFilter()
    setTintMutate(tintColor)
    return this
}
