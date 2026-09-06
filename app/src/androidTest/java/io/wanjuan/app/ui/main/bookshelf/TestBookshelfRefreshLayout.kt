package io.wanjuan.app.ui.main.bookshelf

import android.graphics.Rect
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.R
import io.wanjuan.app.ui.widget.BookshelfSwipeRefreshLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class TestBookshelfRefreshLayout {
    @Test
    fun tabListMovesBelowStatusAndReturnsToItsOriginalPosition() = verifyLayout(R.layout.fragment_books, 1)

    @Test
    fun tabGridMovesBelowStatusAndReturnsToItsOriginalPosition() = verifyLayout(R.layout.fragment_books, 3)

    @Test
    fun folderListMovesBelowStatusAndReturnsToItsOriginalPosition() = verifyLayout(R.layout.fragment_bookshelf2, 1)

    @Test
    fun folderGridMovesBelowStatusAndReturnsToItsOriginalPosition() = verifyLayout(R.layout.fragment_bookshelf2, 3)

    private fun verifyLayout(layout: Int, columns: Int) = onMain {
        val shelf = Shelf(layout, columns)
        for (position in listOf(0, 12)) {
            shelf.manager.scrollToPositionWithOffset(position, if (position == 0) 0 else -24)
            shelf.layout()
            val first = shelf.manager.findFirstVisibleItemPosition()
            val before = shelf.bounds(requireNotNull(shelf.manager.findViewByPosition(first))).top
            val viewportTop = shelf.bounds(shelf.recycler).top
            val viewportHeight = shelf.recycler.height

            repeat(2) {
                shelf.status.visibility = View.VISIBLE
                shelf.layout()

                val after = shelf.bounds(requireNotNull(shelf.manager.findViewByPosition(first))).top
                assertEquals(first, shelf.manager.findFirstVisibleItemPosition())
                assertEquals(before + shelf.status.height, after)
                assertEquals(viewportTop + shelf.status.height, shelf.bounds(shelf.recycler).top)
                assertEquals(viewportHeight - shelf.status.height, shelf.recycler.height)
                assertEquals(shelf.bounds(shelf.status).bottom, shelf.bounds(shelf.recycler).top)
                assertEquals(0, shelf.recycler.paddingTop)

                shelf.status.visibility = View.GONE
                shelf.layout()

                assertEquals(before, shelf.bounds(requireNotNull(shelf.manager.findViewByPosition(first))).top)
                assertEquals(viewportHeight, shelf.recycler.height)
                assertEquals(viewportTop, shelf.bounds(shelf.recycler).top)
            }
        }
    }

    @Test
    fun statusVisibilityChangesDoNotChangeTheFingerDragOffset() = onMain {
        for (layout in listOf(R.layout.fragment_books, R.layout.fragment_bookshelf2)) {
            val shelf = Shelf(layout, 1)
            val startY = shelf.refresh.top + 200f
            shelf.touch(MotionEvent.ACTION_DOWN, startY)
            shelf.touch(MotionEvent.ACTION_MOVE, startY + 160f)
            val offset = shelf.recycler.translationY
            assertTrue(offset > 0f)

            shelf.status.visibility = View.VISIBLE
            shelf.layout()
            shelf.touch(MotionEvent.ACTION_MOVE, startY + 160f)

            assertEquals(offset, shelf.recycler.translationY, 0.01f)
            assertEquals(offset, shelf.status.translationY, 0.01f)
            assertEquals(shelf.bounds(shelf.status).bottom, shelf.bounds(shelf.recycler).top)
            assertTrue(shelf.bounds(shelf.indicator).top + requireNotNull(shelf.indicator.clipBounds).bottom <=
                    shelf.bounds(shelf.status).top)

            shelf.status.visibility = View.GONE
            shelf.layout()
            shelf.touch(MotionEvent.ACTION_MOVE, startY + 160f)
            assertEquals(offset, shelf.recycler.translationY, 0.01f)
            shelf.touch(MotionEvent.ACTION_CANCEL, startY + 160f)
        }
    }

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private class Shelf(layout: Int, columns: Int) {
        private val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.AppTheme_Light
        )
        private val root = LayoutInflater.from(context).inflate(layout, null) as ViewGroup
        val refresh: BookshelfSwipeRefreshLayout = root.findViewById(R.id.refresh_layout)
        val recycler: RecyclerView = root.findViewById(R.id.rv_bookshelf)
        val status: TextView = root.findViewById(R.id.tv_sync_status)
        val indicator: TextView = root.findViewById(R.id.tv_last_refresh)
        val manager: LinearLayoutManager = if (columns == 1) LinearLayoutManager(context)
            else GridLayoutManager(context, columns)
        private val downTime = SystemClock.uptimeMillis()

        init {
            recycler.layoutManager = manager
            recycler.itemAnimator = null
            recycler.clipToPadding = true
            recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val row = TextView(context).apply {
                        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 240)
                    }
                    return object : RecyclerView.ViewHolder(row) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    (holder.itemView as TextView).text = "Book $position"
                }

                override fun getItemCount() = 60
            }
            status.setText(R.string.bookshelf_sync_uploading)
            refresh.setPullContent(recycler, indicator, {}, status)
            layout()
        }

        fun layout() {
            root.requestLayout()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, 1080, 1920)
        }

        fun bounds(view: View): Rect = Rect(0, 0, view.width, view.height).also { rect ->
            var child = view
            while (child !== root) {
                val parent = child.parent as View
                rect.offset((child.x - parent.scrollX).roundToInt(), (child.y - parent.scrollY).roundToInt())
                child = parent
            }
        }

        fun touch(action: Int, screenY: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 540f, screenY, 0)
            try {
                root.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }
}
