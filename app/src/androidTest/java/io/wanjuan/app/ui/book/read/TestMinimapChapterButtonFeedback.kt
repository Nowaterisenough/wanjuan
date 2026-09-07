package io.wanjuan.app.ui.book.read

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.ui.book.read.config.ReaderUiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestMinimapChapterButtonFeedback {
    @Test
    fun pressCancelAndDragOutKeepButtonBoundsAndRestoreAppearance() = withButton { button, label ->
        val normal = button.fillColor()
        val textColor = label.currentTextColor
        button.touch(MotionEvent.ACTION_DOWN)
        assertTrue(button.isPressed)
        assertNotEquals(normal, button.fillColor())
        assertNotEquals(textColor, label.currentTextColor)
        assertEquals(1f, button.scaleX, 0f)
        assertEquals(1f, button.scaleY, 0f)
        assertEquals(1, button.childCount)

        button.touch(MotionEvent.ACTION_CANCEL)
        assertFalse(button.isPressed)
        assertEquals(normal, button.fillColor())
        assertEquals(textColor, label.currentTextColor)

        button.touch(MotionEvent.ACTION_DOWN)
        button.touch(MotionEvent.ACTION_MOVE, -button.width.toFloat())
        assertFalse(button.isPressed)
        assertEquals(normal, button.fillColor())
        button.touch(MotionEvent.ACTION_CANCEL)
    }

    @Test
    fun disabledButtonsIgnoreTouchFeedback() = withButton { button, _ ->
        val normal = button.fillColor()
        button.isEnabled = false
        button.touch(MotionEvent.ACTION_DOWN)
        assertFalse(button.isPressed)
        assertEquals(normal, button.fillColor())
        assertEquals(1f, button.scaleX, 0f)
        assertEquals(1f, button.scaleY, 0f)
        assertEquals(1, button.childCount)
        button.touch(MotionEvent.ACTION_CANCEL)
    }

    private fun withButton(test: (FrameLayout, TextView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val width = ReaderUiStyle.dp(context, 68)
            val height = ReaderUiStyle.dp(context, 36)
            val button = FrameLayout(context).apply { setOnClickListener {} }
            val label = TextView(context).apply { text = "Next" }
            button.addView(label, FrameLayout.LayoutParams(-1, -1))
            button.applyMinimapChapterNavigationStyle(label)
            button.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            button.layout(0, 0, width, height)
            test(button, label)
        }
    }

    private fun View.touch(action: Int, x: Float = width / 2f) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, height / 2f, 0)
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun View.fillColor(): Int {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        background.setBounds(0, 0, width, height)
        background.draw(Canvas(bitmap))
        return bitmap.getPixel(width / 2, height / 2).also { bitmap.recycle() }
    }
}
