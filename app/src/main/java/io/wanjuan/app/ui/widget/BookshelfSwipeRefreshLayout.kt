package io.wanjuan.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.view.children
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.min

/**
 * 让书架内容跟随下拉手势移动，并在松手后回弹。
 *
 * SwipeRefreshLayout 自带的进度视图保持隐藏，刷新状态由书籍封面反馈。
 */
class BookshelfSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val maxPullOffset = 112f * resources.displayMetrics.density
    private val indicatorClip = Rect()
    private var contentView: View? = null
    private var indicatorView: View? = null
    private var statusView: View? = null
    private var downY = 0f
    private var pullStarted = false
    private var releaseAnimator: ValueAnimator? = null
    private var onPullStart: (() -> Unit)? = null

    fun setPullContent(
        content: View,
        indicator: View,
        onPullStart: () -> Unit,
        status: View? = null
    ) {
        contentView = content
        indicatorView = indicator
        statusView = status
        this.onPullStart = onPullStart
        indicator.visibility = View.INVISIBLE
        children.filter { it !== content }.forEach { it.alpha = 0f }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                releaseAnimator?.cancel()
                setPullOffset(0f)
                // The status row can move this layout while the finger stays on screen.
                downY = event.rawY
                pullStarted = false
            }

            MotionEvent.ACTION_MOVE -> updatePullOffset(event.rawY)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val handled = super.dispatchTouchEvent(event)
                animateRelease()
                return handled
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        releaseAnimator?.cancel()
        releaseAnimator = null
        super.onDetachedFromWindow()
    }

    private fun updatePullOffset(currentY: Float) {
        val content = contentView ?: return
        if (!isEnabled) return
        if (canChildScrollUp() && content.translationY == 0f) {
            downY = currentY
            return
        }
        val dragDistance = currentY - downY - touchSlop
        if (dragDistance <= 0f) {
            setPullOffset(0f)
            return
        }
        if (!pullStarted) {
            pullStarted = true
            onPullStart?.invoke()
        }
        setPullOffset(min(dragDistance * PULL_RESISTANCE, maxPullOffset))
    }

    private fun animateRelease() {
        val startOffset = contentView?.translationY ?: return
        if (startOffset <= 0f) return
        releaseAnimator?.cancel()
        releaseAnimator = ValueAnimator.ofFloat(startOffset, 0f).apply {
            duration = RELEASE_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { setPullOffset(it.animatedValue as Float) }
            start()
        }
    }

    private fun setPullOffset(offset: Float) {
        contentView?.translationY = offset
        statusView?.translationY = offset
        indicatorView?.let { indicator ->
            if (offset <= 0f) {
                indicator.visibility = View.INVISIBLE
                indicator.clipBounds = null
                return@let
            }
            indicator.visibility = View.VISIBLE
            indicatorClip.set(
                0,
                0,
                indicator.width.coerceAtLeast(width),
                offset.toInt().coerceAtMost(indicator.height)
            )
            indicator.clipBounds = indicatorClip
        }
    }

    private companion object {
        private const val PULL_RESISTANCE = 0.55f
        private const val RELEASE_DURATION_MS = 220L
    }
}
