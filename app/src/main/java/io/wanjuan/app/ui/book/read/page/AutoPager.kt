package io.wanjuan.app.ui.book.read.page

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import androidx.core.graphics.withClip
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.ReadBookConfig
import io.wanjuan.app.lib.theme.ThemeStore
import io.wanjuan.app.ui.book.read.ReadMenuWorkbench
import io.wanjuan.app.utils.getPrefInt
import io.wanjuan.app.ui.book.read.page.entities.PageDirection
import io.wanjuan.app.utils.canvasrecorder.CanvasRecorderFactory
import io.wanjuan.app.utils.canvasrecorder.recordIfNeeded

/**
 * 自动翻页
 */
class AutoPager(private val readView: ReadView) : Runnable {
    private var progress = 0
    var isRunning = false
        private set
    private var isPausing = false
    private var isEInkMode = false
    private var scrollOffsetRemain = 0.0
    private var scrollOffset = 0
    private var lastTimeMillis = 0L
    private var canvasRecorder = CanvasRecorderFactory.create()
    private val paint by lazy { Paint() }
    var onStopped: (() -> Unit)? = null
    private var stopAt = 0L
    private val stopTimer = object : Runnable {
        override fun run() {
            if (!isRunning || stopAt == 0L) return
            val remaining = stopAt - SystemClock.elapsedRealtime()
            if (remaining <= 0) stop() else readView.postDelayed(this, remaining.coerceAtMost(1000L))
        }
    }
    private val isTimedMode: Boolean
        get() = ReadBookConfig.autoReadMode == ReadBookConfig.AUTO_READ_MODE_TIMED


    fun start() {
        if (isRunning) return
        isRunning = true
        updateStopTimer()
        isEInkMode = AppConfig.isEInkMode
        readView.curPage.upSelectAble(false)
        if (isTimedMode || isEInkMode) {
            readView.postDelayed(this, ReadBookConfig.autoReadSpeed * 1000L)
        } else {
            paint.color = ThemeStore.accentColor
            lastTimeMillis = SystemClock.uptimeMillis()
            readView.invalidate()
        }
    }

    fun stop() {
        if (!isRunning) {
            return
        }
        isRunning = false
        isPausing = false
        isEInkMode = false
        readView.removeCallbacks(this)
        readView.removeCallbacks(stopTimer)
        stopAt = 0L
        readView.curPage.upSelectAble(AppConfig.textSelectAble)
        readView.invalidate()
        reset()
        canvasRecorder.recycle()
        onStopped?.invoke()
    }

    fun pause() {
        if (!isRunning) {
            return
        }
        isPausing = true
        readView.removeCallbacks(this)
    }

    fun resume() {
        if (!isRunning) {
            return
        }
        isPausing = false
        readView.removeCallbacks(this)
        readView.removeCallbacks(stopTimer)
        stopTimer.run()
        if (!isRunning) return
        if (isTimedMode || isEInkMode) {
            readView.postDelayed(this, ReadBookConfig.autoReadSpeed * 1000L)
        } else {
            lastTimeMillis = SystemClock.uptimeMillis()
            readView.invalidate()
        }
    }

    fun reset() {
        if (isTimedMode || isEInkMode) {
            readView.removeCallbacks(this)
            if (isRunning && !isPausing) readView.postDelayed(this, ReadBookConfig.autoReadSpeed * 1000L)
        } else {
            progress = 0
            scrollOffsetRemain = 0.0
            scrollOffset = 0
            canvasRecorder.invalidate()
        }
    }

    fun updateStopTimer() {
        readView.removeCallbacks(stopTimer)
        val minutes = readView.context.getPrefInt(ReadMenuWorkbench.AUTO_STOP_MINUTES).coerceAtLeast(0)
        stopAt = if (isRunning && minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L
        if (stopAt > 0L) readView.postDelayed(stopTimer, 1000L)
    }

    fun upRecorder() {
        canvasRecorder.recycle()
        canvasRecorder = CanvasRecorderFactory.create()
    }

    fun onDraw(canvas: Canvas) {
        if (!isRunning || isEInkMode || isTimedMode) {
            return
        }

        if (readView.isScroll) {
            if (!isPausing) {
                readView.curPage.scroll(-scrollOffset)
                scrollOffset = 0
            }
        } else {
            val bottom = progress
            val width = readView.width

            canvasRecorder.recordIfNeeded(readView.nextPage)
            canvas.withClip(0, 0, width, bottom) {
                canvasRecorder.draw(this)
            }

            canvas.drawRect(
                0f,
                bottom.toFloat() - 1,
                width.toFloat(),
                bottom.toFloat(),
                paint
            )
            if (!isPausing) readView.postInvalidate()
        }

    }

    fun computeOffset() {
        if (!isRunning || isPausing || isEInkMode || isTimedMode) {
            return
        }

        val currentTime = SystemClock.uptimeMillis()
        val elapsedTime = currentTime - lastTimeMillis
        lastTimeMillis = currentTime

        val readTime = ReadBookConfig.autoReadSpeed * 1000.0
        val height = readView.height
        scrollOffsetRemain += height / readTime * elapsedTime
        if (scrollOffsetRemain < 1) {
            return
        }
        scrollOffset = scrollOffsetRemain.toInt()
        this.scrollOffsetRemain -= scrollOffset
        if (!readView.isScroll) {
            progress += scrollOffset
            if (progress >= height) {
                if (!readView.fillPage(PageDirection.NEXT)) {
                    stop()
                } else {
                    reset()
                }
            }
        }
    }

    override fun run() {
        if (!isRunning || isPausing) {
            return
        }

        if (isTimedMode && !isEInkMode) {
            val delegate = readView.pageDelegate
            if (delegate?.isRunning == true) {
                readView.postDelayed(this, 250L)
                return
            }
            if (delegate != null) {
                delegate.isCancel = false
                delegate.nextPageByAnim(readView.defaultAnimationSpeed)
                return
            }
            if (!readView.fillPage(PageDirection.NEXT)) {
                stop()
            } else {
                readView.postDelayed(this, ReadBookConfig.autoReadSpeed * 1000L)
            }
        } else {
            if (!readView.fillPage(PageDirection.NEXT)) {
                stop()
            } else {
                readView.postDelayed(this, ReadBookConfig.autoReadSpeed * 1000L)
            }
        }
    }

}
