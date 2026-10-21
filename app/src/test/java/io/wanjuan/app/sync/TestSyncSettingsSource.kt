package io.wanjuan.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestSyncSettingsSource {

    @Test
    fun backupPreferencesExposeManualSync() {
        val xml = repoFile("app/src/main/res/xml/pref_config_sync.xml").readText()
        assertTrue(xml.contains("sync_now"))
    }

    @Test
    fun backupFragmentHandlesManualSyncClick() {
        val fragment = repoFile("app/src/main/java/io/wanjuan/app/ui/config/SyncConfigFragment.kt").readText()
        assertTrue(fragment.contains("sync_now"))
        assertTrue(fragment.contains("SyncManager.syncNow(force = true) { result ->"))
        assertTrue(fragment.contains("sync_complete_counts"))
    }

    private fun repoFile(path: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.exists() }
            ?: error("$path not found")
    }
}
