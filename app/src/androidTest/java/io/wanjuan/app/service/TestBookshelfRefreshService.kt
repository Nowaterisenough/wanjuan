package io.wanjuan.app.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.os.PowerManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.NotificationId
import io.wanjuan.app.help.LifecycleHelp
import io.wanjuan.app.sync.SingleFlightSync
import io.wanjuan.app.ui.about.AboutActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestBookshelfRefreshService {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun queuedRepeatAndFinalUploadStayProtectedInBackground() = runBlocking {
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        val firstCatalog = CompletableDeferred<Unit>()
        val secondCatalog = CompletableDeferred<Unit>()
        val upload = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var runs = 0
        val flight = SingleFlightSync(this) {
            runs++
            if (runs == 1) firstCatalog.await() else {
                started.complete(Unit)
                secondCatalog.await()
                upload.await()
            }
        }
        val task = flight.start()
        try {
            withContext(Dispatchers.Main) {
                BookshelfRefreshService.track(context, task)
                assertSame(task, flight.start(rerunIfActive = true))
                BookshelfRefreshService.track(context, task)
            }
            awaitForeground()
            goHome()
            firstCatalog.complete(Unit)
            withTimeout(5_000) { started.await() }
            assertTrue(isForeground())
            secondCatalog.complete(Unit)
            delay(100)
            assertTrue("Final upload must retain foreground protection", isForeground())
            upload.complete(Unit)
            withTimeout(5_000) { task.await() }
            awaitStopped()
            assertEquals(2, runs)
        } finally {
            task.cancel()
            scenario.close()
            awaitStopped()
        }
    }

    @Test
    fun completionBeforeServiceCreationDoesNotLeaveAnIdleService() = runBlocking {
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        try {
            repeat(3) {
                withContext(Dispatchers.Main) {
                    val task = CompletableDeferred<Unit>()
                    BookshelfRefreshService.track(context, task)
                    task.complete(Unit)
                }
                // Allow both service creation and its completion callback to run.
                delay(500)
                awaitStopped()
            }
            val next = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) { BookshelfRefreshService.track(context, next) }
            awaitForeground()
            next.complete(Unit)
            awaitStopped()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun cancellationRemovesNotificationAndReleasesWakeLock() = runBlocking {
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        val task = CompletableDeferred<Unit>()
        try {
            withContext(Dispatchers.Main) { BookshelfRefreshService.track(context, task) }
            awaitForeground()
            val lock = currentWakeLock()
            assertTrue(lock.isHeld)
            task.cancel()
            awaitStopped()
            assertFalse(lock.isHeld)
        } finally {
            task.cancel()
            scenario.close()
        }
    }

    @Test
    fun systemTimeoutCancelsWorkAndReleasesWakeLock() = runBlocking {
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        val task = CompletableDeferred<Unit>()
        try {
            withContext(Dispatchers.Main) { BookshelfRefreshService.track(context, task) }
            awaitForeground()
            val lock = currentWakeLock()
            withContext(Dispatchers.Main) { service()!!.onTimeout(1, 1) }
            awaitStopped()
            assertTrue(task.isCancelled)
            assertFalse(lock.isHeld)
        } finally {
            task.cancel()
            scenario.close()
        }
    }

    private suspend fun goHome() {
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use {
            java.io.FileInputStream(it.fileDescriptor).readBytes()
        }
        awaitCondition { withContext(Dispatchers.Main) { LifecycleHelp.getResumedActivity() == null } }
    }

    @Suppress("DEPRECATION")
    private fun isForeground(): Boolean = context.getSystemService(ActivityManager::class.java)
        .getRunningServices(100).any { it.service.className == BookshelfRefreshService::class.java.name && it.foreground }

    private suspend fun awaitForeground() = awaitCondition { isForeground() }

    private suspend fun awaitStopped() = awaitCondition {
        withContext(Dispatchers.Main) { service() == null } &&
            context.getSystemService(NotificationManager::class.java).activeNotifications.none {
                it.id == NotificationId.BookshelfRefreshService
            }
    }

    private suspend fun currentWakeLock() = withContext(Dispatchers.Main) {
        BookshelfRefreshService::class.java.getDeclaredField("wakeLock").apply { isAccessible = true }
            .get(service()) as PowerManager.WakeLock
    }

    private fun service() = BookshelfRefreshService::class.java.getDeclaredField("instance")
        .apply { isAccessible = true }.get(null) as? BookshelfRefreshService

    private suspend fun awaitCondition(condition: suspend () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(50)
    }
}
