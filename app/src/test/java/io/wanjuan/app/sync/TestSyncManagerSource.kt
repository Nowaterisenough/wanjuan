package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestSyncManagerSource {

    @Test
    fun lifecycleOnlyPullsAndEveryObjectTableUsesTheSameDeltaEngine() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/sync/SyncManager.kt").readText()
        assertTrue(source.contains("runSync(pushChanges = false)"))
        assertTrue(source.contains("if (pushChanges) delta.flush() else delta.pull()"))
        assertTrue(source.contains("\"books\", \"book_groups\", \"book_sources\", \"rssSources\", \"ruleSubs\""))
        assertFalse(source.contains("repository.flushOutbox"))
        assertFalse(source.contains("private val orchestrator"))
        assertFalse(source.contains("resetRemoteState"))
    }

    @Test
    fun managerHasStartupNetworkAndManualEntryPoints() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/sync/SyncManager.kt").readText()
        assertTrue(source.contains("fun onAppStart"))
        assertTrue(source.contains("fun onNetworkAvailable"))
        assertTrue(source.contains("fun syncNow"))
        assertTrue(source.contains("!isEnabled"))
    }

    @Test
    fun appStartsSyncWithoutBlockingUiThread() {
        val app = repoFile("app/src/main/java/io/wanjuan/app/App.kt").readText()
        assertTrue(app.contains("SyncManager.onAppStart"))
        assertFalse(app.contains("downloadAllBookProgress"))
    }

    @Test
    fun newestBackupRestoreIsNotCheckedAutomatically() {
        val main = repoFile("app/src/main/java/io/wanjuan/app/ui/main/MainActivity.kt").readText()
        val prefs = repoFile("app/src/main/res/xml/pref_config_sync.xml").readText()
        val keys = repoFile("app/src/main/java/io/wanjuan/app/constant/PreferKey.kt").readText()
        val config = repoFile("app/src/main/java/io/wanjuan/app/help/config/AppConfig.kt").readText()
        assertFalse(main.contains("backupSync()"))
        assertFalse(main.contains("private fun backupSync"))
        assertFalse(prefs.contains("autoCheckNewBackup"))
        assertFalse(keys.contains("autoCheckNewBackup"))
        assertFalse(config.contains("autoCheckNewBackup"))
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: error("$relativePath not found")
    }
}
