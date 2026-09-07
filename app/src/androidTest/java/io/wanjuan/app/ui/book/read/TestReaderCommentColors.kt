package io.wanjuan.app.ui.book.read

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.R
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.ReadBookConfig
import io.wanjuan.app.help.config.ThemeConfig
import io.wanjuan.app.ui.book.read.config.ReaderCommentColorPanel
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.putPrefString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class TestReaderCommentColors {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ContextThemeWrapper(instrumentation.targetContext, androidx.appcompat.R.style.Theme_AppCompat)
    private var day: String? = null
    private var night: String? = null

    @Before
    fun setUp() {
        day = context.getPrefString(PreferKey.commentIndicatorColor)
        night = context.getPrefString(PreferKey.commentIndicatorColorNight)
        context.putPrefString(PreferKey.commentIndicatorColor, null)
        context.putPrefString(PreferKey.commentIndicatorColorNight, "#80B6FF")
    }

    @After
    fun tearDown() {
        context.putPrefString(PreferKey.commentIndicatorColor, day)
        context.putPrefString(PreferKey.commentIndicatorColorNight, night)
    }

    @Test
    fun colorsExpandFromBothBackgroundEntriesAndKeepTheDock() {
        instrumentation.runOnMainSync {
            val expandedStates = mutableListOf<Boolean>()
            val callback = Proxy.newProxyInstance(ReadMenu.CallBack::class.java.classLoader, arrayOf(ReadMenu.CallBack::class.java)) { _, method, args ->
                if (method.name == "onReadMenuExpandedPanelVisibilityChanged") expandedStates.add(args!![0] as Boolean)
                null
            } as ReadMenu.CallBack
            val menu = ReadMenuWorkbench(context, callback, { fail("Keep the reader open") }, { fail("Keep colors in the dock") }, {})
            val navigation = menu.getChildAt(menu.childCount - 1) as ViewGroup
            val font = ReadBookConfig.textFont
            val size = ReadBookConfig.textSize
            val weight = ReadBookConfig.textWeight
            for (parent in listOf(ReadMenuWorkbench.Page.BACKGROUND, ReadMenuWorkbench.Page.BACKGROUND_DETAILS)) {
                menu.show(parent, false)
                (label(menu, R.string.theme_comment_indicator).parent as View).performClick()
                assertEquals(ReadMenuWorkbench.Page.COMMENT_COLORS, menu.page)
                assertTrue(expandedStates.last())
                assertSame(navigation, menu.getChildAt(menu.childCount - 1))
                assertTrue((navigation.getChildAt(3) as ViewGroup).getChildAt(0).isSelected)
                label(menu, R.string.night).performClick()
                menu.refresh()
                assertTrue(label(menu, R.string.night).isSelected)
                menu.goBack()
                assertEquals(parent, menu.page)
            }
            assertEquals(font, ReadBookConfig.textFont)
            assertEquals(size, ReadBookConfig.textSize)
            assertEquals(weight, ReadBookConfig.textWeight)
        }
    }

    @Test
    fun presetsAndCustomColorsPersistIndependentlyAndSourceDefaultResetsOneMode() {
        instrumentation.runOnMainSync {
            val originalMode = AppConfig.isNightTheme
            val panel = ReaderCommentColorPanel(context, false) {}
            descendants(panel).first { it.contentDescription == "#006EFF" }.performClick()
            assertEquals("#006EFF", ThemeConfig.getCommentIndicatorColor(context, false))
            assertEquals("#80B6FF", ThemeConfig.getCommentIndicatorColor(context, true))
            assertTrue(descendants(panel).first { it.contentDescription == "#006EFF" }.isSelected)
            label(panel, R.string.night).performClick()
            val input = descendants(panel).filterIsInstance<EditText>().single()
            input.setText("a1b2c3")
            label(panel, R.string.theme_comment_indicator_apply).performClick()
            assertEquals("#A1B2C3", ThemeConfig.getCommentIndicatorColor(context, true))
            val reopened = ReaderCommentColorPanel(context, true) {}
            assertEquals("#A1B2C3", descendants(reopened).filterIsInstance<EditText>().single().text.toString())
            label(reopened, R.string.theme_color_follow_source).performClick()
            assertNull(ThemeConfig.getCommentIndicatorColor(context, true))
            assertTrue(label(reopened, R.string.theme_color_follow_source).isSelected)
            assertEquals("#006EFF", ThemeConfig.getCommentIndicatorColor(context, false))
            assertEquals(originalMode, AppConfig.isNightTheme)
        }
    }

    @Test
    fun invalidOrUnappliedInputDoesNotChangeSavedColor() {
        instrumentation.runOnMainSync {
            val panel = ReaderCommentColorPanel(context, true) {}
            val input = descendants(panel).filterIsInstance<EditText>().single()
            input.setText("#12ZZ34")
            label(panel, R.string.theme_comment_indicator_apply).performClick()
            assertNotNull(input.error)
            assertEquals("#80B6FF", ThemeConfig.getCommentIndicatorColor(context, true))
            input.setText("#112233")
            label(panel, R.string.day).performClick()
            assertEquals("#80B6FF", ThemeConfig.getCommentIndicatorColor(context, true))
            assertNull(ThemeConfig.getCommentIndicatorColor(context, false))
        }
    }

    private fun label(root: View, resource: Int) = descendants(root).filterIsInstance<TextView>()
        .first { it.text.toString() == context.getString(resource) }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
