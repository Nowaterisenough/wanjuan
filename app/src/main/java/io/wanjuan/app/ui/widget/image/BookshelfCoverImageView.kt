package io.wanjuan.app.ui.widget.image

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.wanjuan.app.R
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.sync.BookSyncProgress
import io.wanjuan.app.sync.SyncIds
import io.wanjuan.app.sync.SyncManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Shelf-only feedback: real publication milestones reveal the cover from top to bottom. */
class BookshelfCoverImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    CoverImageView(context, attrs) {
    private val bookId = MutableStateFlow<String?>(null)
    private var observer: Job? = null
    private var animation: ValueAnimator? = null
    private var status: BookSyncProgress? = null
    private var revealed = 1f
    private val grayPaint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 10 * resources.displayMetrics.scaledDensity
    }

    override fun load(book: Book, loadOnlyWifi: Boolean, fragment: Fragment?, lifecycle: Lifecycle?, onLoadFinish: (() -> Unit)?) {
        bindBook(book)
        super.load(book, loadOnlyWifi, fragment, lifecycle, onLoadFinish)
    }

    override fun loadThumb(book: Book, loadOnlyWifi: Boolean, fragment: Fragment?, lifecycle: Lifecycle?) {
        bindBook(book)
        super.loadThumb(book, loadOnlyWifi, fragment, lifecycle)
    }

    private fun bindBook(book: Book) {
        val id = SyncIds.bookId(book)
        if (bookId.value != id) {
            bookId.value = id
            // A recycled holder must never animate the previous book's state onto this one.
            showSyncStatus(SyncManager.bookProgress.value[id], animate = false)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = findViewTreeLifecycleOwner() ?: return
        observer?.cancel()
        observer = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(bookId, SyncManager.bookProgress) { id, books -> books[id] }
                    .distinctUntilChanged().collect { showSyncStatus(it) }
            }
        }
    }

    override fun onDetachedFromWindow() {
        observer?.cancel()
        observer = null
        animation?.cancel()
        animation = null
        super.onDetachedFromWindow()
    }

    internal fun showSyncStatus(next: BookSyncProgress?, animate: Boolean = true) {
        val target = next?.fraction?.coerceIn(0f, 0.95f) ?: 1f
        if (next == status && revealed == target) return
        val wasIdle = status == null
        status = next
        animation?.cancel()
        ViewCompat.setStateDescription(this, next?.let {
            context.getString(if (it.failed) R.string.sync_cover_failed else R.string.sync_cover_running)
        })
        if (!animate || (wasIdle && next != null) || target < revealed) {
            revealed = target
            invalidate()
        } else {
            animation = ValueAnimator.ofFloat(revealed, target).apply {
                duration = 240
                addUpdateListener { revealed = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (revealed >= 1f) {
            super.onDraw(canvas)
            return
        }
        val boundary = height * revealed
        var saved = canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), boundary)
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
        saved = canvas.save()
        canvas.clipRect(0f, boundary, width.toFloat(), height.toFloat())
        val layer = canvas.saveLayer(0f, boundary, width.toFloat(), height.toFloat(), grayPaint)
        super.onDraw(canvas)
        canvas.restoreToCount(layer)
        canvas.restoreToCount(saved)
        status?.let {
            val label = context.getString(if (it.failed) R.string.sync_cover_failed else R.string.sync_cover_running)
            val density = resources.displayMetrics.density
            val badgeHeight = labelPaint.textSize + 8 * density
            badgePaint.color = if (it.failed) Color.rgb(168, 56, 48) else Color.argb(215, 35, 35, 35)
            canvas.drawRect(0f, height - badgeHeight, width.toFloat(), height.toFloat(), badgePaint)
            canvas.drawText(label, width / 2f, height - 4 * density - labelPaint.descent(), labelPaint)
        }
    }
}
