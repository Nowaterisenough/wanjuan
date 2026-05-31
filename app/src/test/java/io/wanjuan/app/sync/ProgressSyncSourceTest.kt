package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgressSyncSourceTest {

    @Test
    fun progressCoordinatorUsesTimestampOnly() {
        val source = File("app/src/main/java/io/wanjuan/app/sync/ProgressSyncCoordinator.kt").readText()
        assertTrue(source.contains("progressUpdatedAt"))
        assertTrue(source.contains("remoteProgressWins"))
        assertFalse(source.contains("getBookProgress"))
        assertFalse(source.contains("pullOldProgress"))
    }

    @Test
    fun readerStillCallsSureNewProgress() {
        val activity = File("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        assertTrue(activity.contains("override fun sureNewProgress"))
        assertTrue(activity.contains("cloud_progress_exceeds_current"))
    }
}
