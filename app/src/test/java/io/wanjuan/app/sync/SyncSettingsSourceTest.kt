package io.wanjuan.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncSettingsSourceTest {

    @Test
    fun backupPreferencesExposeManualSync() {
        val xml = repoFile("app/src/main/res/xml/pref_config_backup.xml").readText()
        assertTrue(xml.contains("web_dav_sync_now"))
    }

    @Test
    fun backupFragmentHandlesManualSyncClick() {
        val fragment = repoFile("app/src/main/java/io/wanjuan/app/ui/config/BackupConfigFragment.kt").readText()
        assertTrue(fragment.contains("web_dav_sync_now"))
        assertTrue(fragment.contains("SyncManager.syncNow()"))
    }

    private fun repoFile(path: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.exists() }
            ?: error("$path not found")
    }
}
