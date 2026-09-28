package io.wanjuan.app.ui.book.manga

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.R
import io.wanjuan.app.databinding.ActivityMangaBinding
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.model.ReadManga
import io.wanjuan.app.ui.book.manga.entities.MangaChapter
import io.wanjuan.app.ui.book.manga.entities.MangaPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class TestMangaDoubleColumnProgress {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val imageHeights = List(48) { 240 + it % 4 * 120 }

    @Test
    fun bothColumnsFollowEveryPreviewBeforeTheSliderIsReleased() {
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "double-column-progress-${System.nanoTime()}").apply { mkdirs() }
        val pages = imageHeights.mapIndexed { index, height ->
            val file = File(directory, "$index.png")
            val bitmap = Bitmap.createBitmap(200, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.HSVToColor(floatArrayOf(index * 7f, .6f, .8f)))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            MangaPage(chapterSize = 1, mImageUrl = file.absolutePath, index = index, imageCount = imageHeights.size)
        }
        val hideTitle = AppConfig.hideMangaTitle
        val readRecord = AppConfig.enableReadRecord
        val intent = Intent(context, ReadMangaActivity::class.java)
            .putExtra("bookUrl", "double-column-progress-test-missing-book")
            .putExtra("inBookshelf", false)
        try {
            ActivityScenario.launch<ReadMangaActivity>(intent).use { scenario ->
                await(scenario) { it.views.llRetry.visibility == View.VISIBLE }
                scenario.onActivity { activity ->
                    AppConfig.hideMangaTitle = true
                    AppConfig.enableReadRecord = false
                    ReadManga.book = null
                    ReadManga.bookSource = null
                    ReadManga.durChapterIndex = 0
                    ReadManga.durChapterPos = 0
                    ReadManga.chapterSize = 1
                    ReadManga.simulatedChapterSize = 1
                    ReadManga.prevMangaChapter = null
                    ReadManga.nextMangaChapter = null
                    ReadManga.curMangaChapter = MangaChapter(BookChapter(title = "Progress test"), pages, pages.size)
                    ReadMangaActivity::class.java.getDeclaredMethod(
                        "setDoubleColumnLayout", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                    ).apply { isAccessible = true }.invoke(activity, true, false)
                    activity.upContent()
                }
                await(scenario) { it.views.flLoading.visibility != View.VISIBLE && visibleImagesLoaded(it.views.recyclerView) }
                scenario.onActivity {
                    assertEquals(View.VISIBLE, it.views.webtoonFrameDoubleRight.visibility)
                    it.views.mangaMenu.runMenuIn(anim = false)
                }
                await(scenario) { it.views.mangaProgressMinimapPanel.visibility == View.VISIBLE && it.views.mangaProgressMinimap.height > 0 }

                val downTime = SystemClock.uptimeMillis()
                // Keep one drag active across distant jumps and direction changes.
                listOf(.2f, .7f, .35f, .36f, .37f, .1f, .6f).forEachIndexed { index, ratio ->
                    var previousLeft = 0
                    var expectedPage = 0
                    scenario.onActivity { activity ->
                        previousLeft = contentOffset(activity.views.recyclerView)
                        val minimap = activity.views.mangaProgressMinimap
                        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                            if (index == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE,
                            minimap.width / 2f, minimap.height * ratio, 0)
                        try { minimap.dispatchTouchEvent(event) } finally { event.recycle() }
                        expectedPage = ReadManga.durChapterPos
                        assertTrue("Preview must run before release", minimap.isDraggingProgress())
                    }
                    await(scenario) { activity ->
                        val left = activity.views.recyclerView
                        val right = activity.views.recyclerViewDoubleRight
                        val first = (left.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                        val gap = contentOffset(right) - contentOffset(left)
                        first >= expectedPage && contentOffset(left) != previousLeft &&
                            visibleImagesLoaded(left) && visibleImagesLoaded(right) &&
                            abs(gap - (left.height - left.paddingTop - left.paddingBottom)) <= 2
                    }
                    scenario.onActivity { assertTrue(it.views.mangaProgressMinimap.isDraggingProgress()) }
                }
                scenario.onActivity { activity ->
                    val minimap = activity.views.mangaProgressMinimap
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                        minimap.width / 2f, minimap.height * .6f, 0)
                    try { minimap.dispatchTouchEvent(event) } finally { event.recycle() }
                }
                await(scenario) {
                    val left = it.views.recyclerView
                    abs(contentOffset(it.views.recyclerViewDoubleRight) - contentOffset(left) - left.height) <= 2
                }
            }
        } finally {
            AppConfig.hideMangaTitle = hideTitle
            AppConfig.enableReadRecord = readRecord
            directory.deleteRecursively()
            ReadManga.curMangaChapter = null
        }
    }

    private val ReadMangaActivity.views: ActivityMangaBinding
        get() = ActivityMangaBinding.bind(findViewById<View>(R.id.manga_columns_container).parent as View)

    private fun contentOffset(view: RecyclerView): Int {
        val manager = view.layoutManager as LinearLayoutManager
        val position = manager.findFirstVisibleItemPosition()
        val child = manager.findViewByPosition(position) ?: return Int.MIN_VALUE / 2
        val precedingHeight = imageHeights.take(position).sumOf { (it * view.width / 200f).roundToInt() }
        return precedingHeight - manager.getDecoratedTop(child) + view.paddingTop
    }

    private fun visibleImagesLoaded(view: RecyclerView): Boolean {
        val manager = view.layoutManager as LinearLayoutManager
        val first = manager.findFirstVisibleItemPosition()
        val last = manager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return false
        return (first..last).all { position ->
            val child = manager.findViewByPosition(position) ?: return@all false
            val height = imageHeights.getOrNull(position) ?: return@all false
            abs(child.height - (height * view.width / 200f).roundToInt()) <= 1
        }
    }

    private fun await(scenario: ActivityScenario<ReadMangaActivity>, condition: (ReadMangaActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(32)
        }
        scenario.onActivity {
            android.util.Log.e("MangaProgressTest", listOf(it.views.recyclerView, it.views.recyclerViewDoubleRight).joinToString { view ->
                val manager = view.layoutManager as LinearLayoutManager
                "first=${manager.findFirstVisibleItemPosition()} height=${view.height} children=" + (0 until view.childCount).joinToString { i -> val child = view.getChildAt(i); "${view.getChildAdapterPosition(child)}:${child.top}:${child.height}" }
            })
            throw AssertionError("Timed out: left=${contentOffset(it.views.recyclerView)}, right=${contentOffset(it.views.recyclerViewDoubleRight)}")
        }
    }
}
