package io.wanjuan.app.ui.font

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.help.config.ReadBookConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TestReaderFontLibrary {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File
    private lateinit var context: Context

    @Before
    fun setup() {
        directory = File(app.cacheDir, "reader-font-test-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(app) {
            override fun getExternalFilesDir(type: String?) = directory
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences("${directory.name}-$name", mode)
        }
    }

    @After
    fun cleanup() {
        directory.deleteRecursively()
    }

    private fun fontUri(name: String = "Imported.ttf"): Uri {
        val systemFont = File("/system/fonts").listFiles()!!.first { it.extension == "ttf" }
        return Uri.fromFile(File(directory, name).also { systemFont.copyTo(it, overwrite = true) })
    }

    @Test
    fun importPersistsAndDeduplicatesWithoutSelectingOrChangingWeight() {
        val originalFont = ReadBookConfig.textFont
        val originalWeight = ReadBookConfig.textWeight
        val first = ReaderFontLibrary.import(context, fontUri())
        val duplicate = ReaderFontLibrary.import(context, fontUri("Renamed.ttf"))
        assertEquals(first.path, duplicate.path)
        assertEquals(listOf(first), ReaderFontLibrary.list(context, ""))
        assertEquals(originalFont, ReadBookConfig.textFont)
        assertEquals(originalWeight, ReadBookConfig.textWeight)
        assertNotNull(ReaderFontLibrary.typeface(context, first.path))
    }

    @Test
    fun hidingRetainsReferencedFilesAndReimportRestoresTheEntry() {
        val uri = fontUri()
        val font = ReaderFontLibrary.import(context, uri)
        ReaderFontLibrary.hide(context, font.path)
        assertTrue(ReaderFontLibrary.list(context, "").isEmpty())
        assertTrue(File(font.path).exists())
        assertEquals(font.path, ReaderFontLibrary.list(context, font.path).single().path)
        assertEquals(font.path, ReaderFontLibrary.import(context, uri).path)
        assertEquals(font, ReaderFontLibrary.list(context, "").single())
    }

    @Test
    fun rejectsInvalidAndEmptyFontFilesWithoutPublishingPartialCopies() {
        listOf("", "not a font").forEachIndexed { index, content ->
            val file = File(directory, "Invalid$index.otf").apply { writeText(content) }
            assertTrue(runCatching { ReaderFontLibrary.import(context, Uri.fromFile(file)) }.isFailure)
        }
        assertTrue(File(directory, "font").listFiles().orEmpty().isEmpty())
    }
}
