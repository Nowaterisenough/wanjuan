package io.wanjuan.app.service

import android.content.Intent
import android.os.PowerManager
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.IntentAction
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.model.AudioPlay
import io.wanjuan.app.ui.about.AboutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestAudioPowerLifecycle {
    @Test
    fun stopErrorAndPauseReleaseLocksAndPausedReadyDoesNotPoll() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = AppConfig.audioPlayUseWakeLock
        AppConfig.audioPlayUseWakeLock = true
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        val intent = Intent(context, AudioPlayService::class.java)
        try {
            context.startService(Intent(intent).setAction(IntentAction.stopPlay))
            val service = withTimeout(5_000) {
                var service: AudioPlayService? = null
                while (service == null) {
                    delay(50)
                    service = withContext(Dispatchers.Main) {
                        AudioPlay::class.java.getDeclaredField("serviceContext").apply { isAccessible = true }
                            .get(AudioPlay) as? AudioPlayService
                    }
                }
                service
            }
            withContext(Dispatchers.Main) {
                val lock = invoke(service, "getWakeLock") as PowerManager.WakeLock
                val player = invoke(service, "getExoPlayer") as ExoPlayer
                lock.acquire(10_000)
                service.onStartCommand(Intent(intent).setAction(IntentAction.stopPlay), 0, 2)
                assertFalse("stopPlay must release the CPU lock", lock.isHeld)

                lock.acquire(10_000)
                service.onPlayerError(PlaybackException("Power fixture", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
                assertFalse("Player errors must release the CPU lock", lock.isHeld)

                player.playWhenReady = true
                lock.acquire(10_000)
                service.onStartCommand(Intent(intent).setAction(IntentAction.pause), 0, 3)
                assertFalse("Pause must clear playWhenReady even before playback starts", player.playWhenReady)
                assertFalse(lock.isHeld)

                service.onPlaybackStateChanged(Player.STATE_READY)
                val progressJob = AudioPlayService::class.java.getDeclaredField("upPlayProgressJob")
                    .apply { isAccessible = true }.get(service) as? kotlinx.coroutines.Job
                assertTrue("Paused READY must not start progress polling", progressJob?.isActive != true)
            }
        } finally {
            context.stopService(intent)
            withTimeout(5_000) { while (AudioPlayService.isRun) delay(50) }
            scenario.close()
            AppConfig.audioPlayUseWakeLock = previous
        }
    }

    private fun invoke(service: AudioPlayService, name: String): Any? =
        AudioPlayService::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(service)
}
