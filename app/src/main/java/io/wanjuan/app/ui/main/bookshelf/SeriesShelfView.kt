package io.wanjuan.app.ui.main.bookshelf

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import io.wanjuan.app.R
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.lib.theme.primaryTextColor
import io.wanjuan.app.ui.widget.image.CoverImageView
import io.wanjuan.app.utils.dpToPx
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class SeriesShelfView(context: Context, grid: Boolean) : LinearLayout(context) {
    private val covers = SeriesCoverStack(context)
    private val count = TextView(context)
    private val title = TextView(context)

    init {
        orientation = if (grid) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padding = (if (grid) 8 else 12).dpToPx()
        setPadding(padding, 6.dpToPx(), padding, 6.dpToPx())
        isFocusable = true
        setBackgroundResource(R.drawable.bg_item_focused_on_tv)
        val selectable = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
        if (selectable.resourceId != 0) foreground = context.getDrawable(selectable.resourceId)
        addView(covers, LayoutParams(if (grid) MATCH_PARENT else 76.dpToPx(), WRAP_CONTENT))
        val caption = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        title.apply {
            setTextColor(context.primaryTextColor)
            textSize = if (grid) 12f else 16f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        caption.addView(title, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1f))
        count.apply {
            setTextColor(context.getColor(R.color.tv_text_summary))
            textSize = 11f
            isSingleLine = true
        }
        caption.addView(count, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = 6.dpToPx() })
        addView(caption, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            if (grid) {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 4.dpToPx()
            } else marginStart = 12.dpToPx()
        })
    }

    fun bind(series: BookSeries, click: () -> Unit, longClick: () -> Unit) {
        covers.bind(series.books)
        title.text = series.key.title
        count.text = context.getString(R.string.series_count, series.books.size)
        contentDescription = context.getString(R.string.series_description, title.text, count.text)
        setOnClickListener { click() }
        setOnLongClickListener { longClick(); true }
    }
}

/** Keep the first volume legible and reveal the next two covers on either side. */
private class SeriesCoverStack(context: Context) : ViewGroup(context) {
    // Positions and sizes are relative to the width of the upright front cover.
    private class Layer(val centerX: Float, val centerY: Float, val width: Float, val tilt: Float) {
        private val height = width * 4f / 3f
        val bounds = RectF(centerX - width / 2, centerY - height / 2,
            centerX + width / 2, centerY + height / 2).also { rect ->
            Matrix().apply { setRotate(tilt, centerX, centerY) }.mapRect(rect)
        }
    }

    private companion object {
        val layers = listOf(
            Layer(0f, 0f, 1f, 0f),
            Layer(-0.29f, -0.13f, 0.90f, -9f),
            Layer(0.34f, 0.10f, 0.84f, 5f)
        )
    }

    private val covers = List(layers.size) { CoverImageView(context) }
    private val inset = 4.dpToPx()
    private val stackBounds = RectF()
    private var visibleCount = 0
    private var frontWidth = 0f

    init {
        for (index in covers.indices.reversed()) {
            val cover = covers[index]
            cover.rotation = layers[index].tilt
            cover.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            cover.elevation = (if (index == 0) 2f else 1f) * resources.displayMetrics.density
            cover.foreground = GradientDrawable().apply {
                setColor(if (index == 0) Color.TRANSPARENT else 0x18000000)
                setStroke(1.dpToPx(), 0x20000000)
                cornerRadius = 12f // Matches the outline of CoverImageView.
            }
            addView(cover)
        }
    }

    fun bind(books: List<Book>) {
        visibleCount = minOf(books.size, covers.size)
        covers.forEachIndexed { index, cover ->
            cover.isVisible = index < visibleCount
            if (cover.isVisible) cover.loadThumb(books[index], false)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        stackBounds.setEmpty()
        for (index in 0 until visibleCount) stackBounds.union(layers[index].bounds)
        if (stackBounds.isEmpty) {
            setMeasuredDimension(width, 0)
            return
        }
        frontWidth = (width - 2 * inset).coerceAtLeast(0) / stackBounds.width()
        for (index in 0 until visibleCount) {
            val coverWidth = (frontWidth * layers[index].width).roundToInt()
            covers[index].measure(MeasureSpec.makeMeasureSpec(coverWidth, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
        val height = ceil(stackBounds.height() * frontWidth).toInt() + 2 * inset
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val originX = width / 2f - stackBounds.centerX() * frontWidth
        val originY = height / 2f - stackBounds.centerY() * frontWidth
        for (index in 0 until visibleCount) {
            val cover = covers[index]
            val layer = layers[index]
            val left = (originX + layer.centerX * frontWidth - cover.measuredWidth / 2f).roundToInt()
            val top = (originY + layer.centerY * frontWidth - cover.measuredHeight / 2f).roundToInt()
            cover.layout(left, top, left + cover.measuredWidth, top + cover.measuredHeight)
        }
    }
}

internal fun Context.showSeries(series: BookSeries, select: (Book) -> Unit) {
    val names = series.books.map { book ->
        listOf(book.name, book.author).filter(String::isNotBlank).joinToString("  ·  ")
    }.toTypedArray()
    AlertDialog.Builder(this).setTitle(series.key.title).setItems(names) { _, index -> select(series.books[index]) }
        .setNegativeButton(android.R.string.cancel, null).show()
}
