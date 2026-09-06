package io.wanjuan.app.ui.book.read

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.help.config.ReadBookConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class TestReaderWorkbench {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun dockKeepsItsViewsAndTogglesPanelsWithoutChangingTypography() {
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, androidx.appcompat.R.style.Theme_AppCompat)
            val callback = Proxy.newProxyInstance(ReadMenu.CallBack::class.java.classLoader, arrayOf(ReadMenu.CallBack::class.java)) { _, _, _ -> null } as ReadMenu.CallBack
            val menu = ReadMenuWorkbench(context, callback, {}, {}, {})
            val originalFont = ReadBookConfig.textFont
            val originalSize = ReadBookConfig.textSize
            val originalWeight = ReadBookConfig.textWeight
            val dock = menu.getChildAt(menu.childCount - 1) as ViewGroup
            val appearance = (dock.getChildAt(3) as ViewGroup).getChildAt(0) as LinearLayout
            appearance.performClick()
            assertEquals(ReadMenuWorkbench.Page.LAYOUT, menu.page)
            assertTrue(appearance.isSelected)
            assertSame(dock, menu.getChildAt(menu.childCount - 1))
            assertNotNull(appearance.background)
            assertEquals(2, appearance.childCount)
            assertEquals("外观", (appearance.getChildAt(1) as TextView).text.toString())
            val density = context.resources.displayMetrics.density
            menu.measure(View.MeasureSpec.makeMeasureSpec((390 * density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((800 * density).toInt(), View.MeasureSpec.AT_MOST))
            assertTrue("Expanded dock must remain compact", menu.measuredHeight <= 380 * density)
            menu.show(ReadMenuWorkbench.Page.TURN, false)
            assertSame(dock, menu.getChildAt(menu.childCount - 1))
            appearance.performClick()
            assertEquals(ReadMenuWorkbench.Page.MAIN, menu.page)
            appearance.performClick()
            assertEquals(ReadMenuWorkbench.Page.TURN, menu.page)
            assertEquals(originalFont, ReadBookConfig.textFont)
            assertEquals(originalSize, ReadBookConfig.textSize)
            assertEquals(originalWeight, ReadBookConfig.textWeight)
        }
    }
}
