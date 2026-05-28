package io.wanjuan.app.ui.book.read

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.utils.dpToPx

const val MINIMAP_CHAPTER_BUTTON_PRESSED_SCALE = 1.08f

private const val MINIMAP_CHAPTER_BUTTON_FEEDBACK_OVERLAY_TAG = "minimap_chapter_button_feedback_overlay"
private const val MINIMAP_CHAPTER_BUTTON_RELEASE_DURATION = 120L
private const val MINIMAP_CHAPTER_BUTTON_OVERLAY_MAX_ALPHA = 0.58f

@SuppressLint("ClickableViewAccessibility")
fun ViewGroup.setMinimapChapterNavigationClickListener(label: TextView, action: () -> Unit) {
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> applyMinimapChapterButtonPressedFeedback(label)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> clearMinimapChapterButtonPressedFeedback(label)
        }
        false
    }
    setOnClickListener {
        action()
    }
}

private fun ViewGroup.applyMinimapChapterButtonPressedFeedback(label: TextView) {
    animate().cancel()

    val overlay = ensureMinimapChapterButtonFeedbackOverlay(label)
    overlay.animate().cancel()

    pivotX = width / 2f
    pivotY = height / 2f
    scaleX = MINIMAP_CHAPTER_BUTTON_PRESSED_SCALE
    scaleY = MINIMAP_CHAPTER_BUTTON_PRESSED_SCALE
    overlay.alpha = MINIMAP_CHAPTER_BUTTON_OVERLAY_MAX_ALPHA
}

private fun ViewGroup.clearMinimapChapterButtonPressedFeedback(@Suppress("UNUSED_PARAMETER") label: TextView) {
    val overlay = findMinimapChapterButtonFeedbackOverlay()

    animate().cancel()
    overlay?.animate()?.cancel()

    animate()
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(MINIMAP_CHAPTER_BUTTON_RELEASE_DURATION)
        .setInterpolator(DecelerateInterpolator())
        .start()

    overlay
        ?.animate()
        ?.alpha(0f)
        ?.setDuration(MINIMAP_CHAPTER_BUTTON_RELEASE_DURATION)
        ?.setInterpolator(DecelerateInterpolator())
        ?.start()
}

private fun ViewGroup.ensureMinimapChapterButtonFeedbackOverlay(label: TextView): View {
    val existing = findMinimapChapterButtonFeedbackOverlay()
    if (existing != null) {
        existing.background = minimapChapterButtonFeedbackBackground()
        return existing
    }
    return View(context).apply {
        tag = MINIMAP_CHAPTER_BUTTON_FEEDBACK_OVERLAY_TAG
        alpha = 0f
        isClickable = false
        isFocusable = false
        background = minimapChapterButtonFeedbackBackground()
        val insertIndex = indexOfChild(label).takeIf { it >= 0 } ?: childCount
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        addView(this, insertIndex, params)
    }
}

private fun ViewGroup.findMinimapChapterButtonFeedbackOverlay(): View? {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child.tag == MINIMAP_CHAPTER_BUTTON_FEEDBACK_OVERLAY_TAG) {
            return child
        }
    }
    return null
}

private fun View.minimapChapterButtonFeedbackBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 28f.dpToPx()
        setColor(context.accentColor)
    }
}
