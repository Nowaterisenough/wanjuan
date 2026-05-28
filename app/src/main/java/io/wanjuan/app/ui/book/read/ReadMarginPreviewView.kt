package io.wanjuan.app.ui.book.read

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.utils.ColorUtils
import io.wanjuan.app.utils.dpToPx

class ReadMarginPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val frameRect = RectF()
    private val pageRect = RectF()
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.dpToPx().toFloat()
        pathEffect = DashPathEffect(floatArrayOf(4.dpToPx().toFloat(), 3.dpToPx().toFloat()), 0f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.dpToPx().toFloat()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(10.dpToPx().toFloat(), 0f, 4.dpToPx().toFloat(), ColorUtils.adjustAlpha(Color.BLACK, 0.10f))
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3.dpToPx().toFloat()
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = 18.dpToPx().toFloat()
        isFakeBoldText = true
    }

    private var accentColor = context.accentColor
    private var mode = Mode.Body
    private var topMargin = 0
    private var bottomMargin = 0
    private var leftMargin = 0
    private var rightMargin = 0
    private var titleSizeProgress = 0
    private var titleMode = 0

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setAccentColor(color: Int) {
        if (accentColor == color) return
        accentColor = color
        invalidate()
    }

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        invalidate()
    }

    fun setTitleSize(progress: Int) {
        val newProgress = progress.coerceIn(0, MAX_TITLE_SIZE_PROGRESS)
        if (titleSizeProgress == newProgress) return
        titleSizeProgress = newProgress
        invalidate()
    }

    fun setTitleMode(mode: Int) {
        if (titleMode == mode) return
        titleMode = mode
        invalidate()
    }

    fun setMargins(top: Int, bottom: Int, left: Int, right: Int) {
        val newTop = top.coerceIn(0, MAX_MARGIN)
        val newBottom = bottom.coerceIn(0, MAX_MARGIN)
        val newLeft = left.coerceIn(0, MAX_MARGIN)
        val newRight = right.coerceIn(0, MAX_MARGIN)
        if (
            topMargin == newTop &&
            bottomMargin == newBottom &&
            leftMargin == newLeft &&
            rightMargin == newRight
        ) {
            return
        }
        topMargin = newTop
        bottomMargin = newBottom
        leftMargin = newLeft
        rightMargin = newRight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val color = accentColor
        val strokeAlpha = ColorUtils.adjustAlpha(color, 0.48f)
        val guideAlpha = ColorUtils.adjustAlpha(color, 0.18f)
        accentPaint.color = strokeAlpha
        guidePaint.color = guideAlpha
        dotPaint.color = color
        pagePaint.color = ColorUtils.blendColors(Color.WHITE, color, 0.08f)
        linePaint.color = ColorUtils.adjustAlpha(Color.BLACK, 0.08f)
        bandPaint.color = ColorUtils.adjustAlpha(color, 0.20f)
        titleTextPaint.color = ColorUtils.adjustAlpha(Color.BLACK, 0.74f)
        titleTextPaint.textSize = 15.dpToPx() + titleSizeProgress * 0.45f.dpToPx()

        val centerX = width / 2f
        val centerY = height / 2f
        val frameInset = 13.dpToPx().toFloat()
        frameRect.set(frameInset, frameInset, width - frameInset, height - frameInset)

        canvas.drawRoundRect(frameRect, 12.dpToPx().toFloat(), 12.dpToPx().toFloat(), accentPaint)
        drawGuides(canvas, centerX, centerY)

        val basePageInset = 34.dpToPx().toFloat()
        pageRect.set(
            basePageInset + leftMargin * MARGIN_SCALE,
            basePageInset + topMargin * MARGIN_SCALE,
            width - basePageInset - rightMargin * MARGIN_SCALE,
            height - basePageInset - bottomMargin * MARGIN_SCALE
        )
        if (pageRect.width() < 56.dpToPx()) {
            val overflow = 56.dpToPx() - pageRect.width()
            pageRect.inset(-overflow / 2f, 0f)
        }
        if (pageRect.height() < 56.dpToPx()) {
            val overflow = 56.dpToPx() - pageRect.height()
            pageRect.inset(0f, -overflow / 2f)
        }
        canvas.drawRoundRect(pageRect, 3.dpToPx().toFloat(), 3.dpToPx().toFloat(), pagePaint)
        when (mode) {
            Mode.Text -> drawTextLines(canvas)
            Mode.Title -> drawTitlePreview(canvas)
            Mode.Header -> drawHeaderPreview(canvas)
            Mode.Footer -> drawFooterPreview(canvas)
            Mode.Body -> drawTextLines(canvas)
        }
        drawDots(canvas, centerX, centerY)
    }

    private fun drawGuides(canvas: Canvas, centerX: Float, centerY: Float) {
        canvas.drawLine(centerX, 0f, centerX, height.toFloat(), guidePaint)
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, guidePaint)
    }

    private fun drawTextLines(canvas: Canvas) {
        val startX = pageRect.left + 18.dpToPx()
        val maxEndX = pageRect.right - 18.dpToPx()
        var y = pageRect.top + 28.dpToPx()
        val gap = 16.dpToPx()
        val widths = floatArrayOf(0.92f, 0.88f, 0.78f, 0.42f, 0.86f, 0.72f)
        widths.forEach { factor ->
            if (y < pageRect.bottom - 14.dpToPx()) {
                val endX = startX + (maxEndX - startX) * factor
                canvas.drawLine(startX, y, endX, y, linePaint)
            }
            y += gap
        }
    }

    private fun drawHeaderPreview(canvas: Canvas) {
        val headerHeight = (20.dpToPx() + (topMargin + bottomMargin) * MARGIN_SCALE)
            .coerceAtMost(pageRect.height() * 0.34f)
        val headerRect = RectF(pageRect.left, pageRect.top, pageRect.right, pageRect.top + headerHeight)
        canvas.drawRect(headerRect, bandPaint)
        canvas.drawLine(headerRect.left, headerRect.bottom, headerRect.right, headerRect.bottom, accentPaint)
        drawTextLines(canvas)
    }

    private fun drawFooterPreview(canvas: Canvas) {
        val footerHeight = (20.dpToPx() + (topMargin + bottomMargin) * MARGIN_SCALE)
            .coerceAtMost(pageRect.height() * 0.34f)
        val footerRect = RectF(pageRect.left, pageRect.bottom - footerHeight, pageRect.right, pageRect.bottom)
        drawTextLines(canvas)
        canvas.drawLine(footerRect.left, footerRect.top, footerRect.right, footerRect.top, accentPaint)
        canvas.drawRect(footerRect, bandPaint)
    }

    private fun drawTitlePreview(canvas: Canvas) {
        val titleTop = pageRect.top + 18.dpToPx() + topMargin * MARGIN_SCALE
        val titleHeight = (30.dpToPx() + titleSizeProgress * 0.55f.dpToPx())
            .coerceAtMost(50.dpToPx().toFloat())
        val titleRect = RectF(
            pageRect.left + 14.dpToPx(),
            titleTop,
            pageRect.right - 14.dpToPx(),
            titleTop + titleHeight
        )
        canvas.drawRoundRect(titleRect, 5.dpToPx().toFloat(), 5.dpToPx().toFloat(), bandPaint)
        if (titleMode != 2) {
            val oldAlign = titleTextPaint.textAlign
            titleTextPaint.textAlign = if (titleMode == 0) Paint.Align.LEFT else Paint.Align.CENTER
            val textX = if (titleMode == 0) titleRect.left + 10.dpToPx() else titleRect.centerX()
            canvas.drawText(
                "\u7ae0\u8282\u6807\u9898",
                textX,
                titleRect.centerY() + titleTextPaint.textSize * 0.34f,
                titleTextPaint
            )
            titleTextPaint.textAlign = oldAlign
        }
        val oldTop = pageRect.top
        pageRect.top = titleRect.bottom + 16.dpToPx() + bottomMargin * MARGIN_SCALE
        drawTextLines(canvas)
        pageRect.top = oldTop
    }

    private fun drawDots(canvas: Canvas, centerX: Float, centerY: Float) {
        val radius = 7.dpToPx().toFloat()
        canvas.drawCircle(centerX, frameRect.top, radius, dotPaint)
        canvas.drawCircle(centerX, frameRect.bottom, radius, dotPaint)
        canvas.drawCircle(frameRect.left, centerY, radius, dotPaint)
        canvas.drawCircle(frameRect.right, centerY, radius, dotPaint)
    }

    private companion object {
        const val MAX_MARGIN = 80
        const val MAX_TITLE_SIZE_PROGRESS = 20
        const val MARGIN_SCALE = 0.18f
    }

    enum class Mode {
        Body,
        Text,
        Title,
        Header,
        Footer
    }
}
