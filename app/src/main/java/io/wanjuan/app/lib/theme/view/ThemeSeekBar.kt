package io.wanjuan.app.lib.theme.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.utils.applyTint
import io.wanjuan.app.utils.dpToPx

/**
 * @author Aidan Follestad (afollestad)
 */
class ThemeSeekBar(context: Context, attrs: AttributeSet) : AppCompatSeekBar(context, attrs) {

    init {
        if (!isInEditMode) {
            val accentColor = context.accentColor
            applyTint(accentColor)
            thumbTintList = null
            thumb = buildRingThumb(accentColor)
            thumbOffset = 9.dpToPx()
        }
    }

    private fun buildRingThumb(accentColor: Int): LayerDrawable {
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentColor)
        }
        return LayerDrawable(arrayOf(outer, inner)).apply {
            setLayerSize(0, 18.dpToPx(), 18.dpToPx())
            setLayerGravity(0, android.view.Gravity.CENTER)
            setLayerSize(1, 12.dpToPx(), 12.dpToPx())
            setLayerGravity(1, android.view.Gravity.CENTER)
        }
    }
}
