package io.wanjuan.app.ui.book

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import io.wanjuan.app.ui.book.read.config.ReaderSheetStyle
import io.wanjuan.app.utils.dpToPx

/** Shared chrome for text and image chapter previews; each view owns its progress mapping. */
class ProgressMinimapStyle(private val context: Context) {
    companion object {
        private val surfaceRadius = 8f.dpToPx()

        fun surfaceDrawable(fillColor: Int, strokeColor: Int) = GradientDrawable().apply {
            cornerRadius = surfaceRadius
            setColor(fillColor)
            setStroke(1.dpToPx(), strokeColor)
        }
    }

    var palette = ReaderSheetStyle.resolve(context)
        private set
    val horizontalInset = 9f.dpToPx()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun refreshPalette() {
        palette = ReaderSheetStyle.resolve(context)
    }

    fun drawTrack(canvas: Canvas, bounds: RectF) {
        fill.color = palette.surface
        outline.color = palette.stroke
        outline.strokeWidth = 1f.dpToPx()
        canvas.drawRoundRect(bounds, surfaceRadius, surfaceRadius, fill)
        canvas.drawRoundRect(bounds, surfaceRadius, surfaceRadius, outline)
    }

    fun drawThumb(canvas: Canvas, bounds: RectF, pressed: Boolean) {
        fill.color = ColorUtils.setAlphaComponent(palette.accentColor, if (pressed) 48 else 24)
        outline.color = palette.accentColor
        outline.strokeWidth = 1.4f.dpToPx()
        canvas.drawRoundRect(bounds, 4f.dpToPx(), 4f.dpToPx(), fill)
        canvas.drawRoundRect(bounds, 4f.dpToPx(), 4f.dpToPx(), outline)
    }
}
