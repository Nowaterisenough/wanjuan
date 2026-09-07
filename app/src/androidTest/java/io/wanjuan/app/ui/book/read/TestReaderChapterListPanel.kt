package io.wanjuan.app.ui.book.read

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.ui.book.read.config.ReaderUiStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestReaderChapterListPanel {
    @Test
    fun currentChapterIsLocatedAndFilteredSelectionKeepsTheOriginalIndex() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, androidx.appcompat.R.style.Theme_AppCompat)
            var selectedIndex = -1
            val panel = ReaderChapterListPanel(context, 749, {}) { selectedIndex = it.index }
            panel.submitChapters((0 until 1000).map { BookChapter(index = it, title = "Chapter") })
            fun layout() {
                panel.measure(View.MeasureSpec.makeMeasureSpec(ReaderUiStyle.dp(context, 320), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(ReaderUiStyle.dp(context, 420), View.MeasureSpec.EXACTLY))
                panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
            }
            fun descendants(view: View): List<View> = listOf(view) +
                if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            layout()
            val list = descendants(panel).filterIsInstance<RecyclerView>().single()
            val manager = list.layoutManager as LinearLayoutManager
            assertEquals(749, manager.findFirstVisibleItemPosition())
            descendants(panel).filterIsInstance<EditText>().single().setText("12")
            layout()
            assertEquals(1, list.adapter!!.itemCount)
            list.getChildAt(0).performClick()
            assertEquals(11, selectedIndex)
            descendants(panel).filterIsInstance<TextView>().single { it.text.toString() == "定位当前" }.performClick()
            layout()
            assertEquals(1000, list.adapter!!.itemCount)
            assertEquals(749, manager.findFirstVisibleItemPosition())
        }
    }
}
