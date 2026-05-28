package io.wanjuan.app.ui.book.manga

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.wanjuan.app.model.BookCover
import io.wanjuan.app.ui.book.ProgressMinimapDragCalculator
import io.wanjuan.app.utils.dpToPx
import kotlin.math.roundToInt

class MangaProgressMinimapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onProgressChanging: ((progressRatio: Float) -> Unit)? = null
    var onProgressChanged: ((progressRatio: Float) -> Unit)? = null
    var onProgressDragFinished: (() -> Unit)? = null
    var onThumbnailReady: ((pageIndex: Int, imageUrl: String) -> Unit)? = null

    private val trackRect = RectF()
    private val thumbRect = RectF()
    private val pageRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f.dpToPx()
    }
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f.dpToPx()
    }

    private var pageCount: Int = 0
    private var progress: Int = 0
    private var scrollProgressRatio: Float? = null
    private var imageUrls: List<String> = emptyList()
    private var sourceOrigin: String? = null
    private val thumbnailDrawables = mutableMapOf<Int, Drawable>()
    private val thumbnailTargets = mutableMapOf<Int, CustomTarget<Drawable>>()
    private val thumbnailLoadingIndexes = mutableSetOf<Int>()
    private var clearingThumbnailTargets = false
    private var dragRatio: Float? = null
    private var pinnedProgressRatio: Float? = null
    private var dragThumbTouchOffset = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var dragStartY = 0f
    private var hasDragged = false
    private var isDragging = false
    private var maxAvailableHeightPx = Int.MAX_VALUE

    init {
        isClickable = true
        isFocusable = true
    }

    fun setMaxAvailableHeight(maxHeightPx: Int) {
        val safeMaxHeight = maxHeightPx.coerceAtLeast(0)
        if (maxAvailableHeightPx == safeMaxHeight) {
            return
        }
        maxAvailableHeightPx = safeMaxHeight
        requestLayout()
    }

    fun desiredHeightWithin(maxHeightPx: Int): Int {
        return desiredHeightForPageCount().coerceAtMost(maxHeightPx.coerceAtLeast(0))
    }

    fun pinProgressRatio(progressRatio: Float) {
        pinnedProgressRatio = progressRatio.coerceIn(0f, 1f)
        invalidate()
    }

    fun clearPinnedProgressRatio() {
        if (pinnedProgressRatio == null) {
            return
        }
        pinnedProgressRatio = null
        invalidate()
    }

    fun isDraggingProgress(): Boolean {
        return isDragging
    }

    fun shouldPreservePanelPosition(): Boolean {
        return isDragging || pinnedProgressRatio != null
    }

    fun updatePages(imageUrls: List<String>, sourceOrigin: String?, progress: Int, progressRatio: Float?) {
        if (isDragging) {
            return
        }
        val safeImageUrls = imageUrls.filter { it.isNotBlank() }
        val pagesChanged = this.imageUrls != safeImageUrls || this.sourceOrigin != sourceOrigin
        if (pagesChanged) {
            clearThumbnailTargets()
            this.imageUrls = safeImageUrls
            this.sourceOrigin = sourceOrigin
            pinnedProgressRatio = null
        }
        updateProgress(safeImageUrls.size, progress, progressRatio)
        if (pagesChanged) {
            maybeLoadThumbnails()
        }
    }

    fun updateProgress(pageCount: Int, progress: Int, progressRatio: Float?) {
        if (isDragging) {
            return
        }
        val safePageCount = pageCount.coerceAtLeast(0)
        val safeProgress = progress.coerceIn(0, (safePageCount - 1).coerceAtLeast(0))
        val safeProgressRatio = progressRatio?.coerceIn(0f, 1f)
            ?: pageProgressRatio(safePageCount, safeProgress)
        val pageCountChanged = this.pageCount != safePageCount
        val progressChanged = this.progress != safeProgress
        val progressRatioChanged = this.scrollProgressRatio != safeProgressRatio
        if (!isDragging && pinnedProgressRatio == null &&
            (pageCountChanged || progressChanged || progressRatioChanged)
        ) {
            pinnedProgressRatio = null
        }
        if (this.pageCount == safePageCount &&
            this.progress == safeProgress &&
            this.scrollProgressRatio == safeProgressRatio &&
            !isDragging
        ) {
            return
        }
        this.pageCount = safePageCount
        this.progress = safeProgress
        this.scrollProgressRatio = safeProgressRatio
        if (pageCountChanged) {
            requestLayout()
        }
        if (progressChanged) {
            prioritizeCurrentThumbnail()
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        maybeLoadThumbnails()
    }

    override fun onDetachedFromWindow() {
        clearThumbnailTargets()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if ((width != oldWidth || height != oldHeight) && thumbnailTargets.isEmpty()) {
            maybeLoadThumbnails()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 56f.dpToPx().roundToInt()
        val maxHeight = maxHeightForMeasureSpec(heightMeasureSpec)
        val desiredHeight = desiredHeightForPageCount().coerceAtMost(maxHeight)
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageCount <= 1) {
            return
        }
        updateTrackRect()
        drawTrack(canvas)
        drawPageStrip(canvas)
        drawThumb(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || pageCount <= 1) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                isDragging = true
                if (!beginDrag(event.y)) {
                    isPressed = false
                    isDragging = false
                    return false
                }
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                updateDragFromY(event.y)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!hasDragged) {
                    finishDrag()
                    return true
                }
                commitDrag(event.y)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                finishDrag()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun finishDrag(notifyFinished: Boolean = true) {
        parent.requestDisallowInterceptTouchEvent(false)
        isPressed = false
        isDragging = false
        dragRatio = null
        dragThumbTouchOffset = 0f
        dragStartY = 0f
        hasDragged = false
        invalidate()
        if (notifyFinished) {
            onProgressDragFinished?.invoke()
        }
    }

    private fun beginDrag(y: Float): Boolean {
        updateTrackRect()
        val thumbHeight = thumbHeight(trackRect.height())
        val travel = (trackRect.height() - thumbHeight).coerceAtLeast(0f)
        val initialRatio = dragRatio ?: pinnedProgressRatio ?: progressRatio()
        val thumbTop = trackRect.top + travel * initialRatio.coerceIn(0f, 1f)
        val touchInsideThumb = isTouchInsideThumb(y, thumbTop, thumbHeight)
        dragThumbTouchOffset = ProgressMinimapDragCalculator.dragTouchOffset(
            touchInsideThumb,
            y,
            thumbTop,
            thumbHeight
        )
        dragStartY = y
        hasDragged = !touchInsideThumb
        pinnedProgressRatio = null
        dragRatio = if (touchInsideThumb) initialRatio else ratioForY(y)
        invalidate()
        if (!touchInsideThumb) {
            onProgressChanging?.invoke(dragRatio ?: progressRatio())
        }
        return true
    }

    private fun isTouchInsideThumb(y: Float, thumbTop: Float, thumbHeight: Float): Boolean {
        return y >= thumbTop && y <= thumbTop + thumbHeight
    }

    private fun updateDragFromY(y: Float) {
        if (!hasDragged && kotlin.math.abs(y - dragStartY) < touchSlop) {
            return
        }
        hasDragged = true
        updateTrackRect()
        val ratio = ratioForY(y)
        dragRatio = ratio
        invalidate()
        onProgressChanging?.invoke(ratio)
    }

    private fun commitDrag(y: Float) {
        updateDragFromY(y)
        val ratio = dragRatio ?: progressRatio()
        progress = pageForRatio(ratio)
        pinnedProgressRatio = ratio
        finishDrag(notifyFinished = false)
        onProgressChanged?.invoke(ratio)
    }

    private fun ratioForY(y: Float): Float {
        val maxPage = (pageCount - 1).coerceAtLeast(0)
        if (maxPage == 0 || trackRect.height() <= 0f) {
            return 0f
        }
        val thumbHeight = thumbHeight(trackRect.height())
        val centerY = y - dragThumbTouchOffset + thumbHeight / 2f
        return ratioForThumbCenterY(centerY)
    }

    private fun ratioForThumbCenterY(centerY: Float): Float {
        val thumbHeight = thumbHeight(trackRect.height())
        return ProgressMinimapDragCalculator.ratioForTrackY(
            centerY,
            trackRect.top + thumbHeight / 2f,
            trackRect.bottom - thumbHeight / 2f
        )
    }

    private fun thumbCenterYForRatio(ratio: Float): Float {
        val thumbHeight = thumbHeight(trackRect.height())
        val travelTop = trackRect.top + thumbHeight / 2f
        val travelBottom = trackRect.bottom - thumbHeight / 2f
        return travelTop + (travelBottom - travelTop).coerceAtLeast(0f) * ratio.coerceIn(0f, 1f)
    }

    private fun pageForRatio(ratio: Float): Int {
        if (pageCount <= 0) {
            return 0
        }
        val scaledProgress = (ratio.coerceIn(0f, 1f) * pageCount).coerceIn(0f, pageCount.toFloat())
        return if (scaledProgress >= pageCount) {
            pageCount - 1
        } else {
            scaledProgress.toInt().coerceIn(0, pageCount - 1)
        }
    }

    private fun progressRatio(): Float {
        return scrollProgressRatio ?: pageProgressRatio(pageCount, progress)
    }

    private fun pageProgressRatio(pageCount: Int, progress: Int): Float {
        return if (pageCount <= 0) {
            0f
        } else {
            progress.coerceIn(0, pageCount - 1) / pageCount.toFloat()
        }
    }

    private fun updateTrackRect() {
        val horizontalInset = 5f.dpToPx()
        trackRect.set(
            paddingLeft + horizontalInset,
            paddingTop.toFloat(),
            width - paddingRight - horizontalInset,
            height - paddingBottom.toFloat()
        )
    }

    private fun drawTrack(canvas: Canvas) {
        trackPaint.color = ColorUtils.setAlphaComponent(Color.rgb(18, 24, 32), 86)
        canvas.drawRect(trackRect, trackPaint)
        outlinePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 42)
        canvas.drawRect(trackRect, outlinePaint)
    }

    private fun drawPageStrip(canvas: Canvas) {
        val contentInsetX = 7f.dpToPx()
        val contentInsetY = 8f.dpToPx()
        val left = trackRect.left + contentInsetX
        val right = trackRect.right - contentInsetX
        val top = trackRect.top + contentInsetY
        val bottom = trackRect.bottom - contentInsetY
        val availableHeight = (bottom - top).coerceAtLeast(1f)
        val pageHeight = availableHeight / pageCount.coerceAtLeast(1)
        val gap = if (pageHeight >= 4f.dpToPx()) 1f.dpToPx() else 0f

        canvas.save()
        canvas.clipRect(trackRect)
        for (index in 0 until pageCount) {
            val pageTop = top + pageHeight * index + gap / 2f
            val pageBottom = top + pageHeight * (index + 1) - gap / 2f
            pageRect.set(left, pageTop, right, pageBottom.coerceAtLeast(pageTop + 1f))
            if (thumbnailDrawables.containsKey(index)) {
                drawPageThumbnail(canvas, index)
            } else {
                drawPagePlaceholder(canvas, index)
            }
        }
        canvas.restore()
    }

    private fun drawPageThumbnail(canvas: Canvas, index: Int) {
        val drawable = thumbnailDrawables[index] ?: return
        val oldAlpha = drawable.alpha
        drawable.alpha = 232
        drawable.setBounds(
            pageRect.left.roundToInt(),
            pageRect.top.roundToInt(),
            pageRect.right.roundToInt(),
            pageRect.bottom.roundToInt()
        )
        drawable.draw(canvas)
        drawable.alpha = oldAlpha
    }

    private fun drawPagePlaceholder(canvas: Canvas, index: Int) {
        val alpha = when {
            index == progress -> 112
            index % 2 == 0 -> 68
            else -> 50
        }
        pagePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, alpha)
        canvas.drawRect(pageRect, pagePaint)
    }

    private fun drawThumb(canvas: Canvas) {
        val thumbHeight = thumbHeight(trackRect.height())
        val ratio = dragRatio ?: pinnedProgressRatio ?: progressRatio()
        val centerY = thumbCenterYForRatio(ratio)
        val top = centerY - thumbHeight / 2f
        thumbRect.set(
            trackRect.left + 2f.dpToPx(),
            top,
            trackRect.right - 2f.dpToPx(),
            top + thumbHeight
        )
        thumbPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, if (isPressed) 96 else 72)
        canvas.drawRect(thumbRect, thumbPaint)
        thumbStrokePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, if (isPressed) 236 else 200)
        canvas.drawRect(thumbRect, thumbStrokePaint)
    }

    private fun thumbHeight(trackHeight: Float): Float {
        if (pageCount <= 0) {
            return trackHeight
        }
        return (trackHeight / pageCount).coerceIn(44f.dpToPx(), trackHeight)
    }

    private fun maybeLoadThumbnails() {
        if (!isAttachedToWindow || !isShown || imageUrls.isEmpty()) {
            return
        }
        val remainingSlots = MAX_THUMBNAIL_REQUESTS - thumbnailLoadingIndexes.size
        if (remainingSlots <= 0) {
            return
        }
        imageUrls.indices.asSequence()
            .filter {
                !thumbnailTargets.containsKey(it) &&
                    !thumbnailDrawables.containsKey(it) &&
                    !thumbnailLoadingIndexes.contains(it)
            }
            .sortedWith(compareBy<Int> { kotlin.math.abs(it - progress) }.thenBy { it })
            .take(remainingSlots)
            .forEach { index ->
                loadThumbnail(index, imageUrls[index])
            }
    }

    private fun finishThumbnailLoad(index: Int) {
        thumbnailLoadingIndexes.remove(index)
        maybeLoadThumbnails()
    }

    private fun clearThumbnailLoading(index: Int) {
        thumbnailLoadingIndexes.remove(index)
        if (!clearingThumbnailTargets) {
            maybeLoadThumbnails()
        }
    }

    private fun prioritizeCurrentThumbnail() {
        if (
            !isAttachedToWindow ||
            !isShown ||
            progress !in imageUrls.indices ||
            thumbnailTargets.containsKey(progress) ||
            thumbnailDrawables.containsKey(progress)
        ) {
            return
        }
        thumbnailLoadingIndexes.toList()
            .filter { index -> index != progress }
            .forEach { index -> cancelThumbnailTarget(index) }
        maybeLoadThumbnails()
    }

    private fun cancelThumbnailTarget(index: Int) {
        val target = thumbnailTargets.remove(index) ?: return
        thumbnailLoadingIndexes.remove(index)
        val wasClearingThumbnailTargets = clearingThumbnailTargets
        clearingThumbnailTargets = true
        try {
            runCatching {
                Glide.with(context).clear(target)
            }
        } finally {
            clearingThumbnailTargets = wasClearingThumbnailTargets
        }
    }

    private fun loadThumbnail(index: Int, url: String) {
        if (!thumbnailLoadingIndexes.add(index)) {
            return
        }
        val target = object : CustomTarget<Drawable>(
            thumbnailRequestWidth(),
            thumbnailRequestHeight()
        ) {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                thumbnailDrawables[index] = resource.mutate()
                invalidate()
                onThumbnailReady?.invoke(index, url)
                finishThumbnailLoad(index)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                errorDrawable?.let {
                    thumbnailDrawables[index] = it.mutate()
                }
                invalidate()
                finishThumbnailLoad(index)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                thumbnailDrawables.remove(index)
                invalidate()
                clearThumbnailLoading(index)
            }
        }
        thumbnailTargets[index] = target
        BookCover.loadManga(
            context,
            url,
            sourceOrigin = sourceOrigin
        ).override(
            thumbnailRequestWidth(),
            thumbnailRequestHeight()
        ).priority(Priority.LOW).into(target)
    }

    private fun clearThumbnailTargets() {
        clearingThumbnailTargets = true
        try {
            thumbnailTargets.values.forEach { target ->
                runCatching {
                    Glide.with(context).clear(target)
                }
            }
        } finally {
            clearingThumbnailTargets = false
        }
        thumbnailTargets.clear()
        thumbnailDrawables.clear()
        thumbnailLoadingIndexes.clear()
    }

    private fun thumbnailRequestWidth(): Int {
        return (width.takeIf { it > 0 } ?: 56f.dpToPx().roundToInt()).coerceAtLeast(32)
    }

    private fun thumbnailRequestHeight(): Int {
        val baseHeight = height.takeIf { it > 0 } ?: desiredHeightForPageCount()
        return (baseHeight / pageCount.coerceAtLeast(1)).coerceIn(48f.dpToPx().roundToInt(), 240f.dpToPx().roundToInt())
    }

    private fun desiredHeightForPageCount(): Int {
        val baseHeight = MINIMAP_BASE_HEIGHT_DP.dpToPx()
        val pageHeight = MINIMAP_PAGE_HEIGHT_DP.dpToPx()
        val minHeight = MINIMAP_MIN_HEIGHT_DP.dpToPx()
        val maxHeight = MINIMAP_MAX_HEIGHT_DP.dpToPx()
        return (baseHeight + pageHeight * pageCount.coerceAtLeast(1))
            .roundToInt()
            .coerceIn(minHeight.roundToInt(), maxHeight.roundToInt())
    }

    private fun maxHeightForMeasureSpec(heightMeasureSpec: Int): Int {
        val configuredMaxHeight = MINIMAP_MAX_HEIGHT_DP.dpToPx()
            .roundToInt()
            .coerceAtMost(maxAvailableHeightPx)
        return when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.AT_MOST,
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
                .takeIf { it > 0 }
                ?.coerceAtMost(configuredMaxHeight)
                ?: configuredMaxHeight

            else -> configuredMaxHeight
        }
    }

    private companion object {
        const val MINIMAP_MIN_HEIGHT_DP = 300f
        const val MINIMAP_MAX_HEIGHT_DP = 620f
        const val MINIMAP_BASE_HEIGHT_DP = 180f
        const val MINIMAP_PAGE_HEIGHT_DP = 18f
        private const val MAX_THUMBNAIL_REQUESTS = 3
    }
}
