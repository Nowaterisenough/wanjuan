package io.wanjuan.app.ui.book.manga

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.R
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.rule.ContentRule
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.model.ReadManga
import io.wanjuan.app.ui.book.manga.entities.MangaPage
import io.wanjuan.app.ui.book.manga.recyclerview.MangaAdapter
import io.wanjuan.app.utils.putPrefBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TestMangaChapterNavigation {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "manga-nav-${System.nanoTime()}"
    private val book = Book(bookUrl = id, tocUrl = id, name = id, origin = id,
        type = BookType.image, totalChapterNum = 6, canUpdate = false)
    private val chapters = List(book.totalChapterNum) {
        BookChapter(bookUrl = id, url = "$id/$it", index = it, title = "Chapter $it")
    }
    private val directory = File(context.cacheDir, id)
    private val oldAutoChange = AppConfig.autoChangeSource
    private val oldReadRecord = AppConfig.enableReadRecord

    @Before fun setUp() {
        context.putPrefBoolean(PreferKey.autoChangeSource, false)
        AppConfig.enableReadRecord = false
        book.config.mangaHorizontalScroll = false
        appDb.bookDao.insert(book)
        appDb.bookChapterDao.insert(*chapters.toTypedArray())
        directory.mkdirs()
        val image = Bitmap.createBitmap(100, 240, Bitmap.Config.ARGB_8888)
        repeat(3) { i -> File(directory, "$i.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        image.recycle()
    }

    @After fun cleanUp() {
        context.putPrefBoolean(PreferKey.autoChangeSource, oldAutoChange)
        AppConfig.enableReadRecord = oldReadRecord
        appDb.bookDao.delete(book)
        BookHelp.clearCache(book)
        directory.deleteRecursively()
    }

    private fun cache(index: Int, missingImages: Boolean = false) {
        // Deliberately share image URLs across chapters, as happens with credits and cover pages.
        BookHelp.saveText(book, chapters[index], (0..2).joinToString("\n") {
            val name = if (missingImages) "missing-$it.png" else "$it.png"
            "<img src=\"${File(directory, name).absolutePath}\">"
        })
    }

    private fun intent() = Intent(context, ReadMangaActivity::class.java).putExtra("bookUrl", id)
    private val ReadMangaActivity.reader get() = findViewById<RecyclerView>(R.id.recycler_view)
    private fun ReadMangaActivity.at(chapter: Int, page: Int): Boolean {
        if (findViewById<View>(R.id.fl_loading).visibility == View.VISIBLE || ReadManga.durChapterIndex != chapter) return false
        val manager = reader.layoutManager as LinearLayoutManager
        val position = manager.findFirstVisibleItemPosition()
        val item = (reader.adapter as MangaAdapter).getItem(position) as? MangaPage
        return item?.chapterIndex == chapter && item.index == page
    }

    @Test fun neighbourAloneCannotOpenOrSaveChapterTwo() {
        cache(1)
        ActivityScenario.launch<ReadMangaActivity>(intent()).use { scenario ->
            scenario.await { findViewById<View>(R.id.ll_retry).visibility == View.VISIBLE && ReadManga.nextMangaChapter != null }
            scenario.onActivity {
                assertEquals(0, ReadManga.durChapterIndex)
                assertTrue((it.reader.adapter as MangaAdapter).getItems().isEmpty())
            }
            cache(0)
            scenario.onActivity { it.findViewById<View>(R.id.tv_retry).performClick() }
            scenario.await { at(0, 0) }
        }
        assertEquals(0, appDb.bookDao.getBook(id)!!.durChapterIndex)
    }

    @Test fun explicitPageAndRapidCatalogueJumpsSurviveLateResultsAndScrollBackAcrossChapters() {
        chapters.indices.forEach(::cache)
        ActivityScenario.launch<ReadMangaActivity>(intent().putExtra("index", 2).putExtra("chapterPos", 1)).use { scenario ->
            scenario.await { at(2, 1) }
            scenario.onActivity {
                it.skipToChapter(4)
                it.findViewById<View>(R.id.btn_manga_minimap_next).performClick()
            }
            // Chapter 4 was still loading when Next cancelled its request.
            scenario.await { at(5, 0) && ReadManga.prevMangaChapter?.chapter?.index == 4 }
            scenario.onActivity { it.findViewById<View>(R.id.btn_manga_minimap_previous).performClick() }
            scenario.await { at(4, 0) }
            scenario.onActivity {
                it.skipToChapter(1)
                it.skipToChapter(5)
                it.skipToChapter(4)
            }
            scenario.await { at(4, 0) && ReadManga.prevMangaChapter?.chapter?.index == 3 }
            for (target in listOf(3, 2)) {
                scenario.await { ReadManga.prevMangaChapter?.chapter?.index == target }
                scenario.scrollBackTo(target)
            }
        }
    }

    @Test fun dragAtTheTopRetriesAnUnavailablePreviousChapter() {
        cache(3)
        cache(4)
        ActivityScenario.launch<ReadMangaActivity>(intent().putExtra("index", 3)).use { scenario ->
            scenario.await { at(3, 0) && ReadManga.nextMangaChapter != null }
            scenario.onActivity { it.reader.scrollBy(0, -it.reader.height) }
            scenario.await { !reader.canScrollVertically(-1) }
            cache(2)
            scenario.onActivity {
                val view = it.reader
                val t = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN to .3f, MotionEvent.ACTION_MOVE to .5f,
                    MotionEvent.ACTION_MOVE to .8f, MotionEvent.ACTION_UP to .8f).forEachIndexed { i, (action, y) ->
                    val event = MotionEvent.obtain(t, t + i * 48, action, view.width / 2f, view.height * y, 0)
                    try { view.dispatchTouchEvent(event) } finally { event.recycle() }
                }
            }
            scenario.await { ReadManga.prevMangaChapter?.chapter?.index == 2 || ReadManga.durChapterIndex == 2 }
            scenario.scrollBackTo(2)
        }
    }

    private fun ActivityScenario<ReadMangaActivity>.awaitToolbar() = await {
        val shell = findViewById<View>(R.id.title_bar_shell)
        findViewById<View>(R.id.menu_refresh)?.isShown == true && shell.animation?.hasEnded() != false
    }

    private fun ActivityScenario<ReadMangaActivity>.tapRefresh() {
        onView(withId(R.id.webtoon_frame)).perform(click())
        awaitToolbar()
        onView(withId(R.id.menu_refresh)).perform(click())
    }

    @Test fun refreshFailureStillLetsTheReaderOpenTheCatalogueAndChangeChapter() {
        cache(0, missingImages = true)
        cache(1)
        ActivityScenario.launch<ReadMangaActivity>(intent()).use { scenario ->
            scenario.await { at(0, 0) && reader.findViewById<View>(R.id.retry)?.isShown == true }
            scenario.tapRefresh()
            scenario.await { findViewById<View>(R.id.tv_retry).isShown }
            onView(withId(R.id.fl_loading)).perform(click())
            scenario.awaitToolbar()
            onView(withId(R.id.menu_catalog)).perform(click())
            onView(withText("2  Chapter 1")).perform(click())
            scenario.await { at(1, 0) }
        }
    }

    @Test fun refreshingAChapterRemovedFromTheCatalogueShowsRetryInsteadOfLoadingForever() {
        cache(0)
        ActivityScenario.launch<ReadMangaActivity>(intent()).use { scenario ->
            scenario.await { at(0, 0) }
            appDb.bookChapterDao.delByBook(id)
            scenario.tapRefresh()
            scenario.await { findViewById<View>(R.id.tv_retry).isShown }
            onView(withId(R.id.fl_loading)).perform(click())
            scenario.awaitToolbar()
        }
    }

    @Test fun menuCanOpenDuringRefreshAndReopenAfterNewPagesArrive() {
        // The source supplies one image in a volume entry. Its old cached body has three pages.
        appDb.bookSourceDao.insert(BookSource(bookSourceUrl = id, ruleContent = ContentRule(content = "body@html")))
        try {
            chapters[0].apply {
                isVolume = true
                url = title
                tag = "<img src=\"${File(directory, "2.png").absolutePath}\">"
            }
            appDb.bookChapterDao.delByBook(id)
            appDb.bookChapterDao.insert(*chapters.toTypedArray())
            cache(0)
            cache(1)
            ActivityScenario.launch<ReadMangaActivity>(intent()).use { scenario ->
                scenario.await { at(0, 0) && ReadManga.curMangaChapter?.imageCount == 3 }
                // Hold the UI delivery of the refreshed content so the loading screen can be tapped deterministically.
                scenario.onActivity { activity ->
                    ReadManga.register(object : ReadManga.Callback by activity { override fun upContent() = Unit })
                }
                try {
                    scenario.tapRefresh()
                    scenario.await { findViewById<View>(R.id.ll_loading).isShown && ReadManga.curMangaChapter?.imageCount == 1 }
                    onView(withId(R.id.fl_loading)).perform(click())
                    scenario.awaitToolbar()
                    onView(withId(R.id.menu_catalog)).perform(click())
                    scenario.await { findViewById<View>(R.id.bottom_menu).isShown }
                } finally {
                    scenario.onActivity { activity -> ReadManga.register(activity); activity.upContent() }
                }
                scenario.await { at(0, 0) && ReadManga.curMangaChapter?.imageCount == 1 &&
                    reader.findViewById<View>(R.id.fl_progress)?.visibility == View.GONE }
                onView(withContentDescription("关闭面板")).perform(click())
                onView(withId(R.id.vw_menu_bg)).perform(click())
                scenario.await { !findViewById<View>(R.id.manga_menu).isShown }
                onView(withId(R.id.webtoon_frame)).perform(click())
                scenario.awaitToolbar()
            }
        } finally {
            appDb.bookSourceDao.delete(id)
        }
    }

    private fun ActivityScenario<ReadMangaActivity>.scrollBackTo(target: Int) = await {
        if (ReadManga.durChapterIndex == target) true
        else {
            reader.scrollBy(0, -reader.height / 2)
            false
        }
    }

    private fun ActivityScenario<ReadMangaActivity>.await(check: ReadMangaActivity.() -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            onActivity { ready = check(it) }
            if (ready) return
            SystemClock.sleep(40)
        }
        throw AssertionError("Timed out at chapter ${ReadManga.durChapterIndex}, page ${ReadManga.durChapterPos}, previous=${ReadManga.prevMangaChapter?.chapter?.index}")
    }
}
