package io.wanjuan.app.utils

import android.content.res.ColorStateList
import android.view.MenuItem
import android.widget.ImageButton
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.view.MenuItemCompat
import io.wanjuan.app.R

fun MenuItem.applyIconTint(@ColorInt color: Int) {
    // Store tint on the item so replacing its drawable retains the current theme.
    icon?.mutate()?.clearColorFilter()
    val tint = ColorStateList.valueOf(color)
    MenuItemCompat.setIconTintList(this, tint)
    actionView?.findViewById<ImageButton>(R.id.item)?.apply {
        clearColorFilter()
        imageTintList = tint
        setImageDrawable(icon)
    }
}

fun MenuItem.setIconCompat(@DrawableRes iconRes: Int) {
    setIcon(iconRes)
    actionView?.findViewById<ImageButton>(R.id.item)?.setImageDrawable(icon)
}
