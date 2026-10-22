package io.wanjuan.app.sync

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.ui.widget.image.BookshelfCoverImageView
import io.wanjuan.app.data.entities.Book
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TestBookSyncCoverInstrumented {
    @Test fun shelfRefreshSuppressesCoverFeedbackWithoutDroppingPendingState() = runBlocking<Unit> {
        val field = SyncManager::class.java.getDeclaredField("progressTracker").apply { isAccessible = true }
        val tracker = field.get(SyncManager) as SyncProgressTracker
        try {
            tracker.begin()
            tracker.pendingBooks(setOf("cover-test"))
            withTimeout(3000) { SyncManager.bookProgress.first { "cover-test" in it } }
            SyncManager.setBookshelfRefreshActive(true)
            withTimeout(3000) { SyncManager.bookProgress.first { it.isEmpty() } }
            assertTrue("cover-test" in tracker.snapshot().books)
            SyncManager.setBookshelfRefreshActive(false)
            withTimeout(3000) { SyncManager.bookProgress.first { "cover-test" in it } }
        } finally {
            SyncManager.setBookshelfRefreshActive(false)
            tracker.pendingBooks(emptySet())
            tracker.phase(SyncPhase.Idle)
        }
    }

    @Test fun coverRevealsFromTopAndFailureNeverLooksComplete() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val cover = BookshelfCoverImageView(context)
            cover.setImageDrawable(ColorDrawable(Color.rgb(220, 50, 30)))
            cover.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
            cover.layout(0, 0, 240, 320)
            fun render(status: BookSyncProgress?): Bitmap {
                cover.showSyncStatus(status, animate = false)
                return Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).also { cover.draw(Canvas(it)) }
            }
            val waiting = render(BookSyncProgress())
            val half = render(BookSyncProgress(0.5f))
            val failed = render(BookSyncProgress(0.5f, true))
            val complete = render(null)
            fun gray(bitmap: Bitmap, y: Int): Boolean {
                val color = bitmap.getPixel(120, y)
                return kotlin.math.abs(Color.red(color) - Color.green(color)) <= 1 &&
                    kotlin.math.abs(Color.green(color) - Color.blue(color)) <= 1
            }
            assertTrue(gray(waiting, 60))
            assertFalse(gray(half, 60))
            assertTrue(gray(half, 210))
            assertTrue(gray(failed, 210))
            assertFalse(gray(complete, 210))
            cover.showSyncStatus(BookSyncProgress(0.2f, true), animate = false)
            cover.loadThumb(Book(bookUrl = "different-cover-test", origin = "test"))
            cover.setImageDrawable(ColorDrawable(Color.RED))
            val rebound = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
            cover.draw(Canvas(rebound))
            assertFalse(gray(rebound, 210))
            rebound.recycle()
            // Render actual view output side by side for visual QA, not a mockup.
            val preview = Bitmap.createBitmap(960, 320, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(preview)
            listOf(waiting, half, failed, complete).forEachIndexed { index, bitmap ->
                canvas.drawBitmap(bitmap, index * 240f, 0f, null)
                bitmap.recycle()
            }
            File(context.externalCacheDir, "book-sync-cover-preview.png").outputStream().use {
                preview.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            preview.recycle()
        }
    }
}
