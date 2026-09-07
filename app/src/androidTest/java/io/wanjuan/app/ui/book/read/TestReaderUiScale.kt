package io.wanjuan.app.ui.book.read

import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.R
import io.wanjuan.app.ui.book.read.config.ReaderUiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class TestReaderUiScale {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun panelsKeepNavigationInsideNarrowScreensWithLargerSystemText() {
        instrumentation.runOnMainSync {
            for (scale in listOf(1f, 1.3f)) {
                val configuration = Configuration(instrumentation.targetContext.resources.configuration).apply { fontScale = scale }
                val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(configuration), androidx.appcompat.R.style.Theme_AppCompat)
                val callback = Proxy.newProxyInstance(ReadMenu.CallBack::class.java.classLoader, arrayOf(ReadMenu.CallBack::class.java)) { _, _, _ -> null } as ReadMenu.CallBack
                val menu = ReadMenuWorkbench(context, callback, {}, {}, {})
                val width = ReaderUiStyle.dp(context, 320)
                for (page in ReadMenuWorkbench.Page.entries) {
                    menu.show(page, false)
                    menu.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(ReaderUiStyle.dp(context, 600), View.MeasureSpec.AT_MOST))
                    menu.layout(0, 0, menu.measuredWidth, menu.measuredHeight)
                    val navigation = menu.getChildAt(menu.childCount - 1) as ViewGroup
                    assertTrue("$page navigation must remain visible", navigation.bottom <= menu.height)
                    for (i in 0 until navigation.childCount) {
                        val slot = navigation.getChildAt(i) as ViewGroup
                        val target = slot.getChildAt(0)
                        assertTrue("$page navigation target overflows its slot", target.left >= 0 && target.right <= slot.width)
                        assertTrue(target.isClickable)
                    }
                    val allowed = listOf(12f, 14f, 16f).map {
                        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, it, context.resources.displayMetrics)
                    }
                    fun verifyText(view: View) {
                        if (view is TextView) assertTrue("$page has an inconsistent text size: ${view.text}", allowed.any { kotlin.math.abs(it - view.textSize) < 1f })
                        if (view is ViewGroup) for (i in 0 until view.childCount) verifyText(view.getChildAt(i))
                    }
                    verifyText(menu)
                }
            }
        }
    }

    @Test
    fun widerButtonsKeepTheirIconAtTheSameSize() {
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val icon = ReaderUiStyle.icon(context, R.drawable.ic_lucide_search, "搜索", 0xff000000.toInt())
            for (width in listOf(44, 96, 180)) {
                val w = ReaderUiStyle.dp(context, width)
                val h = ReaderUiStyle.dp(context, 44)
                icon.layout(0, 0, w, h)
                assertEquals(ReaderUiStyle.dp(context, 20), icon.width - icon.paddingLeft - icon.paddingRight)
                assertEquals(ReaderUiStyle.dp(context, 20), icon.height - icon.paddingTop - icon.paddingBottom)
            }
        }
    }
}
