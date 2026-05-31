package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncManagerSourceTest {

    @Test
    fun managerHasStartupNetworkAndManualEntryPoints() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/sync/SyncManager.kt").readText()
        assertTrue(source.contains("fun onAppStart"))
        assertTrue(source.contains("fun onNetworkAvailable"))
        assertTrue(source.contains("fun syncNow"))
        assertTrue(source.contains("ProgressSyncCoordinator"))
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
        val prefs = repoFile("app/src/main/res/xml/pref_config_backup.xml").readText()
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
