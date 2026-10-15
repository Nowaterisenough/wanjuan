package io.wanjuan.app.ui.book.read

import android.content.Intent
import android.net.Uri
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.R
import io.wanjuan.app.data.appDb
import io.wanjuan.app.help.config.ReadBookConfig
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.model.localBook.LocalBook
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TestReaderParagraphIndent {
    @Test
    fun changingIndentationReflowsTheOpenChapterWithoutReopeningTheBook() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalBook = ReadBook.book
        val file = File(context.cacheDir, "paragraph-indent-${System.nanoTime()}.txt")
        file.writeText("第一章 测试\n" + ("第一段正文，检查段首空格。\n第二段正文，检查即时重排。\n").repeat(20))
        val book = LocalBook.importFile(Uri.fromFile(file))
        val chapters = LocalBook.getChapterList(book)
        appDb.bookChapterDao.insert(*chapters.toTypedArray())
        appDb.bookDao.update(book)
        var snapshot: ReadBookConfig.AppearanceSnapshot? = null
        try {
            val intent = Intent(context, ReadBookActivity::class.java).putExtra("bookUrl", book.bookUrl)
            ActivityScenario.launch<ReadBookActivity>(intent).use { scenario ->
                awaitContent { ReadBook.book?.bookUrl == book.bookUrl }
                scenario.onActivity {
                    snapshot = ReadBookConfig.captureAppearance()
                    ReadBookConfig.useBookAppearance()
                    it.findViewById<ReadMenu>(R.id.read_menu).showWorkbenchPage(ReadMenuWorkbench.Page.LAYOUT)
                }
                for (spaces in listOf(4, 0, 2)) {
                    onView(withText("段首空格数")).perform(click())
                    onView(isAssignableFrom(EditText::class.java)).perform(replaceText("$spaces"))
                    onView(withText("确定")).perform(click())
                    awaitContent {
                        val paragraphs = ReadBook.curTextChapter!!.getContent().lines().filter { line ->
                            line.contains("第一段正文") || line.contains("第二段正文")
                        }
                        paragraphs.size >= 2 && paragraphs.all { line ->
                            line.takeWhile { it == '　' }.length == spaces
                        }
                    }
                }
                scenario.onActivity { snapshot?.let(ReadBookConfig::restoreAppearance) }
            }
        } finally {
            ReadBook.executor.submit {}.get()
            appDb.bookDao.delete(book)
            appDb.readRecentBookDao.delete(book.bookUrl)
            LocalBook.deleteBook(book, false)
            file.delete()
            ReadBook.book = originalBook
        }
    }

    private fun awaitContent(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (ReadBook.curTextChapter?.isCompleted == true && condition()) return
            Thread.sleep(50)
        }
        assertTrue("The open chapter did not update to the selected paragraph indentation", false)
    }
}
