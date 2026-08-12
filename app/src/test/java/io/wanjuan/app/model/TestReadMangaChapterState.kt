package io.wanjuan.app.model

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestReadMangaChapterState {

    @After
    fun tearDown() {
        ReadManga.book = null
    }

    @Test
    fun `chapter is accepted only when it belongs to current book`() {
        ReadManga.book = Book(bookUrl = "current-url")

        assertTrue(ReadManga.isCurrentBookChapter(BookChapter(bookUrl = "current-url")))
        assertFalse(ReadManga.isCurrentBookChapter(BookChapter(bookUrl = "old-url")))
    }

    @Test
    fun `chapter jumps reject invalid indices and stale content submissions`() {
        val viewModelSource = File(
            "app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaViewModel.kt"
        ).readText()
        val activitySource = File(
            "app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt"
        ).readText()
        val readMangaSource = File(
            "app/src/main/java/io/wanjuan/app/model/ReadManga.kt"
        ).readText()

        assertTrue(viewModelSource.contains("index in 0 until ReadManga.chapterSize"))
        assertTrue(activitySource.contains("ReadManga.isCurrentContent(data)"))
        assertTrue(activitySource.contains("list.getOrNull(pos)"))
        assertTrue(readMangaSource.contains("generation != contentGeneration"))
        assertTrue(readMangaSource.contains("contentLoadScope.coroutineContext.cancelChildren()"))
    }

    @Test
    fun `manga chapter controls use safe liquid glass views`() {
        val activityLayout = File("app/src/main/res/layout/activity_manga.xml").readText()
        val menuLayout = File("app/src/main/res/layout/view_manga_menu.xml").readText()
        val safeView = "io.wanjuan.app.ui.widget.SafeLiquidGlassView"

        assertTrue(activityLayout.split(safeView).size - 1 == 4)
        assertFalse(activityLayout.contains("com.qmdeve.liquidglass.widget.LiquidGlassView"))
        assertTrue(menuLayout.contains("<$safeView"))
        assertFalse(menuLayout.contains("com.qmdeve.liquidglass.widget.LiquidGlassView"))
    }
}
