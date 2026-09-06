package io.wanjuan.app.help.config

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.model.ImageProvider
import io.wanjuan.app.utils.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestCommentIndicatorTheme {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var day: ThemeConfig.Config
    private lateinit var night: ThemeConfig.Config
    private var themeMode: String? = null
    private val book = Book(bookUrl = "comment-theme-test", name = "comment-theme-test")
    private val cachedSources = mutableListOf<String>()

    @Before
    fun setUp() {
        day = ThemeConfig.getThemeConfig(context, false)
        night = ThemeConfig.getThemeConfig(context, true)
        themeMode = context.getPrefString(PreferKey.themeMode)
        context.putPrefString(PreferKey.themeMode, "1")
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        ThemeConfig.applyConfig(context, day, switchNightMode = false, notify = false)
        ThemeConfig.applyConfig(context, night, switchNightMode = false, notify = false)
        context.putPrefString(PreferKey.themeMode, themeMode)
        instrumentation.waitForIdleSync()
        cachedSources.forEach { BookHelp.getImage(book, it).delete() }
        ImageProvider.clear()
    }

    @Test
    fun cachedInlineBubblesRecolorPixelsAndRestoreWithoutChangingOriginalFiles() = runBlocking {
        val fixtures = instrumentation.context.assets.open("fixtures/comment-indicators.json").bufferedReader().use {
            GSON.fromJsonArray<Map<String, Any>>(it.readText()).getOrThrow()
        }
        for ((index, fixture) in fixtures.withIndex()) {
            val name = fixture["name"].toString()
            val svg = fixture.getValue("svg").toString()
            val key = listOf("js", "click", "pclick")[index % 3]
            val action = listOf("openDiscussion(42)", "a.b(c)", "java.showBrowser(url, title)")[index % 3]
            val options = GSON.toJson(mapOf(key to action, "style" to "text"))
            val src = "data:image/svg+xml;base64," + Base64.encodeToString(svg.toByteArray(), Base64.NO_WRAP) +
                ",$options"
            cachedSources.add(src)
            ImageProvider.cacheImage(book, src, null)
            val originalBytes = BookHelp.getImage(book, src).readBytes()
            context.putPrefString(PreferKey.commentIndicatorColor, null)
            val original = ImageProvider.getImage(book, src, 100, 90)
            assertFalse("original $name", containsColor(original, Color.RED))
            context.putPrefString(PreferKey.commentIndicatorColor, "#FFFF0000")
            val red = ImageProvider.getImage(book, src, 100, 90)
            assertTrue("red foreground $name", containsColor(red, Color.RED))
            if (fixture["hasBackground"] == true) assertTrue("background preserved $name", containsColor(red, Color.WHITE))
            assertArrayEquals(originalBytes, BookHelp.getImage(book, src).readBytes())
            context.putPrefString(PreferKey.commentIndicatorColor, "#FF0000FF")
            val blue = ImageProvider.getImage(book, src, 100, 90)
            assertTrue("blue foreground $name", containsColor(blue, Color.BLUE))
            assertFalse("stale red cache $name", containsColor(blue, Color.RED))
            context.putPrefString(PreferKey.commentIndicatorColor, null)
            assertSame(original, ImageProvider.getImage(book, src, 100, 90))

            val illustration = src.substringBefore(",{")
            cachedSources.add(illustration)
            ImageProvider.cacheImage(book, illustration, null)
            context.putPrefString(PreferKey.commentIndicatorColor, "#FFFF0000")
            assertFalse("ordinary SVG $name", containsColor(ImageProvider.getImage(book, illustration, 100, 90), Color.RED))
        }
    }

    @Test
    fun dayAndNightColorsRemainIndependentAndOldThemesRestoreSourceDefault() {
        ThemeConfig.applyConfig(context, day.copy(commentIndicatorColor = "#FF0000"), notify = false)
        ThemeConfig.applyConfig(context, night.copy(commentIndicatorColor = "#0000FF"), switchNightMode = false, notify = false)
        assertEquals("#FF0000", ThemeConfig.getCommentIndicatorColor(context))
        assertEquals("#FF0000", ThemeConfig.getThemeConfig(context, false).commentIndicatorColor)
        assertEquals("#0000FF", ThemeConfig.getThemeConfig(context, true).commentIndicatorColor)
        context.putPrefString(PreferKey.themeMode, "2")
        instrumentation.waitForIdleSync()
        assertEquals("#0000FF", ThemeConfig.getCommentIndicatorColor(context))
        ThemeConfig.applyConfig(context, night.copy(commentIndicatorColor = null), notify = false)
        assertNull(ThemeConfig.getCommentIndicatorColor(context))
        assertEquals("#FF0000", ThemeConfig.getCommentIndicatorColor(context, false))
    }

    @Test
    fun themePackageExportAndImportKeepCommentColor() = runBlocking {
        val config = day.copy(themeName = "comment-color-test-${System.nanoTime()}", commentIndicatorColor = "#FF345678")
        val entry = ThemePackageManager.addFromConfig(config)
        val zip = ThemePackageManager.exportZip(entry)
        ThemePackageManager.deleteLocal(entry)
        var imported: ThemePackageManager.Entry? = null
        try {
            imported = ThemePackageManager.importZip(zip)
            assertEquals(config.commentIndicatorColor, ThemePackageManager.getConfig(imported).commentIndicatorColor)
        } finally {
            imported?.let { ThemePackageManager.deleteLocal(it) }
            zip.delete()
        }
    }

    private fun containsColor(bitmap: Bitmap, color: Int): Boolean {
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 100 && (pixel and 0xFFFFFF) == (color and 0xFFFFFF)) return true
        }
        return false
    }
}
