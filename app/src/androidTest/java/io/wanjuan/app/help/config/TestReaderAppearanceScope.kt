package io.wanjuan.app.help.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.help.book.ContentProcessor
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.ui.book.read.ReadMenuThemeSuite
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TestReaderAppearanceScope {
    private val first = Book(bookUrl = "reader-appearance-test-a", name = "Appearance A")
    private val second = Book(bookUrl = "reader-appearance-test-b", name = "Appearance B")
    private var originalBook: Book? = null
    private lateinit var snapshot: ReadBookConfig.AppearanceSnapshot
    private var shared = false
    private var comic = false

    @Before
    fun setup() {
        originalBook = ReadBook.book
        comic = ReadBookConfig.isComic
        ReadBookConfig.isComic = false
        ReadBook.book = first
        shared = ReadBookConfig.shareLayout
        snapshot = ReadBookConfig.captureAppearance()
        appDb.bookDao.insert(first, second)
    }

    @After
    fun cleanup() {
        ReadBook.book = first
        ReadBookConfig.restoreAppearance(snapshot)
        ReadBookConfig.shareLayout = shared
        ReadBook.executor.submit {}.get(10, TimeUnit.SECONDS)
        appDb.bookDao.delete(first, second)
        appDb.readRecentBookDao.delete(first.bookUrl)
        appDb.readRecentBookDao.delete(second.bookUrl)
        ReadBook.book = originalBook
        ReadBookConfig.isComic = comic
    }

    @Test
    fun independentAppearancePersistsAndDoesNotChangeOtherBooksOrGlobalFont() {
        val defaultSize = ReadBookConfig.textSize
        val defaultIndent = ReadBookConfig.paragraphIndent
        val globalTypeface = AppConfig.systemTypefaces
        ReadBookConfig.useBookAppearance()
        ReadBookConfig.textSize = 31
        ReadBookConfig.paragraphIndentSize = 4
        ReadBookConfig.systemTypeface = 2
        ReadBookConfig.animationSpeed = 760
        ReadBookConfig.save()
        ReadBook.executor.submit {}.get(10, TimeUnit.SECONDS)
        val restored = appDb.bookDao.getBook(first.bookUrl)!!
        ReadBook.book = second
        assertEquals(defaultSize, ReadBookConfig.textSize)
        assertEquals(defaultIndent, ReadBookConfig.paragraphIndent)
        assertFalse(ReadBookConfig.hasBookAppearance)
        assertEquals(globalTypeface, AppConfig.systemTypefaces)
        ReadBook.book = restored
        assertEquals(31, ReadBookConfig.textSize)
        assertEquals("　　　　", ReadBookConfig.paragraphIndent)
        assertEquals(2, ReadBookConfig.systemTypeface)
        assertEquals(760, ReadBookConfig.animationSpeed)
    }

    @Test
    fun indentationReplacesExistingWhitespaceAndLeavesTheTitleUnindented() {
        ReadBookConfig.useBookAppearance()
        val chapter = BookChapter(bookUrl = first.bookUrl, url = "paragraph-indent-test", title = "测试章节")
        val processor = ContentProcessor.get(first)
        for (spaces in listOf(0, 1, 2, 4, 8)) {
            ReadBookConfig.paragraphIndentSize = spaces
            val result = processor.getContent(first, chapter, "  第一段正文\n　　第二段正文", useReplace = false,
                chineseConvert = false, reSegment = false)
            assertEquals(listOf("测试章节", "${"　".repeat(spaces)}第一段正文", "${"　".repeat(spaces)}第二段正文"), result.textList)
        }
        val saved = ReadMenuThemeSuite.captureCurrent("段首空格")
        ReadBookConfig.paragraphIndentSize = 0
        saved.applyToReader()
        assertEquals(8, ReadBookConfig.paragraphIndentSize)
        saved.copy(paragraphIndent = null).applyToReader()
        assertEquals(2, ReadBookConfig.paragraphIndentSize)
    }

    @Test
    fun sharedTypographyExportsTheActiveIndentation() {
        ReadBookConfig.shareLayout = true
        ReadBookConfig.paragraphIndentSize = 0
        assertEquals("", ReadBookConfig.getExportConfig().paragraphIndent)
        ReadBookConfig.paragraphIndentSize = 4
        assertEquals("　　　　", ReadBookConfig.getExportConfig().paragraphIndent)
        ReadBookConfig.useBookAppearance()
        ReadBookConfig.paragraphIndentSize = 0
        assertEquals("", ReadBookConfig.getExportConfig().paragraphIndent)
    }

    @Test
    fun promotionAndUndoPreserveSharedLayoutAndExistingIndependentBook() {
        ReadBookConfig.shareLayout = true
        ReadBookConfig.shareConfig.textSize = 19
        ReadBookConfig.durConfig.bgStrNight = "#102030"
        ReadBookConfig.useBookAppearance()
        assertEquals("#102030", ReadBookConfig.durConfig.bgStrNight)
        ReadBookConfig.textSize = 27
        ReadBookConfig.save()
        ReadBook.book = second
        ReadBookConfig.useBookAppearance()
        ReadBookConfig.textSize = 23
        ReadBookConfig.save()
        ReadBook.book = first
        val before = ReadBookConfig.captureAppearance()
        ReadBookConfig.useDefaultAppearance(promoteCurrent = true)
        assertFalse(ReadBookConfig.hasBookAppearance)
        assertEquals(27, ReadBookConfig.textSize)
        ReadBook.book = second
        assertEquals(23, ReadBookConfig.textSize)
        ReadBook.book = first
        ReadBookConfig.restoreAppearance(before)
        assertTrue(ReadBookConfig.hasBookAppearance)
        assertEquals(19, ReadBookConfig.shareConfig.textSize)
        ReadBookConfig.useDefaultAppearance()
        assertEquals(19, ReadBookConfig.textSize)
    }

    @Test
    fun malformedOverrideFallsBackAndPartialThemeLeavesUnselectedSectionsAlone() {
        first.config.readerAppearance = "{broken"
        assertFalse(ReadBookConfig.hasBookAppearance)
        ReadBookConfig.useBookAppearance()
        val current = ReadMenuThemeSuite.captureCurrent("partial")
        val partial = current.copy(textSize = 35, bgValue = "#ABCDEF", includeTypography = true,
            includeBackground = false, includeTurning = false)
        partial.applyToReader()
        assertEquals(35, ReadBookConfig.textSize)
        assertEquals(current.bgValue, ReadBookConfig.durConfig.curBgStr())
        assertTrue(partial.matchesCurrentTheme())
        val legacyJson = GSON.toJson(current)
        val legacy = GSON.fromJsonObject<ReadMenuThemeSuite>(legacyJson).getOrThrow()
        legacy.applyToReader()
        assertEquals(current.textSize, ReadBookConfig.textSize)
    }
}
