package io.wanjuan.app.ui.widget.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class CoverCropTransform(
    val scale: Float,
    val translateX: Float,
    val translateY: Float
)

internal data class CoverSize(
    val width: Int,
    val height: Int
)

internal object CoverCropCalculator {
    fun fitWithin(
        maxWidth: Int,
        maxHeight: Int,
        drawableWidth: Int,
        drawableHeight: Int
    ): CoverSize? {
        if (maxWidth <= 0 || maxHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) {
            return null
        }

        val scale = min(
            maxWidth.toFloat() / drawableWidth,
            maxHeight.toFloat() / drawableHeight
        )
        return CoverSize(
            width = (drawableWidth * scale).roundToInt().coerceAtLeast(1),
            height = (drawableHeight * scale).roundToInt().coerceAtLeast(1)
        )
    }

    fun calculate(
        viewWidth: Int,
        viewHeight: Int,
        drawableWidth: Int,
        drawableHeight: Int
    ): CoverCropTransform? {
        if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) {
            return null
        }

        val scale = max(
            viewWidth.toFloat() / drawableWidth,
            viewHeight.toFloat() / drawableHeight
        )
        val scaledWidth = drawableWidth * scale
        val scaledHeight = drawableHeight * scale
        val translateX = if (scaledWidth > viewWidth) {
            viewWidth - scaledWidth
        } else {
            (viewWidth - scaledWidth) / 2f
        }

        return CoverCropTransform(
            scale = scale,
            translateX = translateX,
            translateY = (viewHeight - scaledHeight) / 2f
        )
    }
}
