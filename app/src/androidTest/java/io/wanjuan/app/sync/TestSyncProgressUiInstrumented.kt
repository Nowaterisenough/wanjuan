package io.wanjuan.app.sync

import android.content.Intent
import android.graphics.Bitmap
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.ui.config.ConfigActivity
import io.wanjuan.app.ui.config.ConfigTag
import io.wanjuan.app.ui.config.SyncConfigFragment
import io.wanjuan.app.utils.defaultSharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TestSyncProgressUiInstrumented {
    @Test
    fun settingsCollectsLiveProgressAndDisablesDuplicateManualSync() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.defaultSharedPreferences
        val saved = preferences.all[PreferKey.syncBookProgress]
        preferences.edit().putBoolean(PreferKey.syncBookProgress, false).commit()
        // Presentation-only fixture: no cloud access and no changes to the user's library.
        val field = SyncManager::class.java.getDeclaredField("progressTracker").apply { isAccessible = true }
        val tracker = field.get(SyncManager) as SyncProgressTracker
        val intent = Intent(context, ConfigActivity::class.java).putExtra("configTag", ConfigTag.SYNC_CONFIG)
        try {
            ActivityScenario.launch<ConfigActivity>(intent).use { scenario ->
                tracker.begin()
                tracker.reading(500)
                tracker.read(fromCache = false)
                repeat(19) { tracker.read(fromCache = true) }
                var received = false
                for (attempt in 0 until 50) {
                    scenario.onActivity { activity ->
                        val fragment = activity.supportFragmentManager.findFragmentByTag(ConfigTag.SYNC_CONFIG) as SyncConfigFragment
                        received = fragment.findPreference<Preference>("sync_status")!!.summary.toString().contains("20/500")
                        if (received) {
                            assertFalse(fragment.findPreference<Preference>("sync_now")!!.isEnabled)
                            fragment.scrollToPreference("sync_status")
                        }
                    }
                    if (received) break
                    Thread.sleep(20)
                }
                assertTrue(received)
                instrumentation.waitForIdleSync()
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                File(context.externalCacheDir, "sync-progress-preview.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
        } finally {
            tracker.begin()
            tracker.phase(SyncPhase.Idle)
            preferences.edit().apply {
                if (saved is Boolean) putBoolean(PreferKey.syncBookProgress, saved) else remove(PreferKey.syncBookProgress)
            }.commit()
        }
    }
}
