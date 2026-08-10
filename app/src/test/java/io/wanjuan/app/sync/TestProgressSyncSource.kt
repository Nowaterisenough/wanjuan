package io.wanjuan.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestProgressSyncSource {

    @Test
    fun readersStoreProgressSyncTimeSeparatelyFromLastReadTime() {
        val readBook = File("app/src/main/java/io/wanjuan/app/model/ReadBook.kt").readText()
        val readManga = File("app/src/main/java/io/wanjuan/app/model/ReadManga.kt").readText()
        assertTrue(readBook.contains("progressUpdatedAt: Long? = null"))
        assertTrue(readBook.contains("progressChanged"))
        assertTrue(readBook.contains("book.syncTime = durTime"))
        assertTrue(readManga.contains("progressUpdatedAt: Long? = null"))
        assertTrue(readManga.contains("progressChanged"))
        assertTrue(readManga.contains("book.syncTime = book.durChapterTime"))
    }

    @Test
    fun readerStillCallsSureNewProgress() {
        val activity = File("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        assertTrue(activity.contains("override fun sureNewProgress"))
        assertTrue(activity.contains("cloud_progress_exceeds_current"))
    }

    @Test
    fun automaticProgressSyncAppliesWithoutDialog() {
        val readBook = File("app/src/main/java/io/wanjuan/app/model/ReadBook.kt").readText()
        val readManga = File("app/src/main/java/io/wanjuan/app/model/ReadManga.kt").readText()
        val readBookViewModel = File("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookViewModel.kt").readText()
        val readMangaViewModel = File("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaViewModel.kt").readText()
        val readBookActivity = File("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        val readMangaActivity = File("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()

        assertTrue(readBook.contains("if (newProgressAction != null)") && readBook.contains("setProgress(progress)"))
        assertTrue(readManga.contains("if (newProgressAction != null)") && readManga.contains("setProgress(progress)"))
        assertTrue(readBookViewModel.contains("alertSync?.invoke(progress) ?: ReadBook.setProgress(progress)"))
        assertTrue(readMangaViewModel.contains("alertSync?.invoke(progress) ?: ReadManga.setProgress(progress)"))
        assertTrue(!readBookViewModel.contains("ReadBook.callBack?.sureNewProgress(progress)"))
        assertTrue(!readMangaViewModel.contains("ReadManga.mCallback?.sureNewProgress(progress)"))
        assertTrue(
            readBookActivity.substringAfter("networkChangedListener.onNetworkChanged = {")
                .substringBefore("override fun onPause()")
                .contains("ReadBook.syncProgress()")
        )
        assertTrue(
            readMangaActivity.substringAfter("networkChangedListener.onNetworkChanged = {")
                .substringBefore("override fun onPause()")
                .contains("ReadManga.syncProgress()")
        )
    }
}
