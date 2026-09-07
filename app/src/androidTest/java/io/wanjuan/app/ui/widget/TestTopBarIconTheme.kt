package io.wanjuan.app.ui.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.MenuItem
import android.widget.ImageButton
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuItemCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.R
import io.wanjuan.app.utils.applyOpenTint
import io.wanjuan.app.utils.applyTopBarIconMetrics
import io.wanjuan.app.utils.getCompatColor
import io.wanjuan.app.utils.iconItemOnLongClick
import io.wanjuan.app.utils.setIconCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestTopBarIconTheme {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun navigationOverflowAndActionsFollowBothToolbarThemes() {
        instrumentation.runOnMainSync {
            for (dark in listOf(false, true)) {
                val toolbar = toolbar(dark)
                val action = toolbar.menu.add("Action").apply {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    setIcon(R.drawable.ic_lucide_search)
                }
                toolbar.applyTopBarIconMetrics()
                for (icon in listOf(toolbar.navigationIcon, toolbar.overflowIcon, action.icon)) {
                    val color = renderedColor(icon!!)
                    assertTrue("Expected ${if (dark) "light" else "dark"} icon: $color",
                        if (dark) Color.red(color) >= 160 else Color.red(color) < 80)
                }
            }
        }
    }

    @Test
    fun explicitReaderTintSurvivesMenuCreationAndDrawableReplacement() {
        instrumentation.runOnMainSync {
            val toolbar = toolbar(false)
            toolbar.applyTopBarIconMetrics(Color.WHITE)
            val action = toolbar.menu.add(0, R.id.menu_refresh, 0, "Refresh").apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                setIcon(R.drawable.ic_lucide_refresh_cw)
            }
            toolbar.menu.iconItemOnLongClick(action.itemId) {}
            toolbar.applyTopBarIconMetrics()
            action.setIconCompat(R.drawable.ic_lucide_search)
            val button = action.actionView!!.findViewById<ImageButton>(R.id.item)
            assertEquals(Color.WHITE, MenuItemCompat.getIconTintList(action)!!.defaultColor)
            assertEquals(Color.WHITE, button.imageTintList!!.defaultColor)
            assertEquals(Color.WHITE, renderedColor(button.drawable))
            assertEquals(Color.WHITE, renderedColor(toolbar.navigationIcon!!))
        }
    }

    @Test
    fun overflowPopupKeepsItsOwnContrastWithAnExplicitReaderTint() {
        instrumentation.runOnMainSync {
            val toolbar = toolbar(false)
            val overflow = toolbar.menu.add("Overflow").apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                setIcon(R.drawable.ic_lucide_settings)
            }
            toolbar.applyTopBarIconMetrics(Color.WHITE)
            toolbar.menu.applyOpenTint(toolbar.context)
            val expected = toolbar.context.getCompatColor(R.color.primaryText)
            assertEquals(expected, MenuItemCompat.getIconTintList(overflow)!!.defaultColor)
            assertTrue(Color.red(renderedColor(overflow.icon!!)) < 80)
            assertEquals(Color.WHITE, renderedColor(toolbar.overflowIcon!!))
        }
    }

    private fun toolbar(dark: Boolean): Toolbar {
        val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val context = ContextThemeWrapper(
            instrumentation.targetContext.createConfigurationContext(config),
            if (dark) R.style.AppTheme_Dark else R.style.AppTheme_Light
        )
        return Toolbar(ContextThemeWrapper(context,
            if (dark) R.style.AppTheme_AppBarOverlay_Dark else R.style.AppTheme_AppBarOverlay_Light
        )).apply {
            setNavigationIcon(R.drawable.ic_lucide_arrow_left)
            setOverflowIcon(context.getDrawable(R.drawable.ic_lucide_more_vertical))
        }
    }

    private fun renderedColor(drawable: Drawable): Int {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 48, 48)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(48 * 48)
        bitmap.getPixels(pixels, 0, 48, 0, 0, 48, 48)
        bitmap.recycle()
        return pixels.maxBy { Color.alpha(it) }
    }
}
