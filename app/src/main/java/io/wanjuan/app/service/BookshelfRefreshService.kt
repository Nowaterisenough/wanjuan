package io.wanjuan.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.content.pm.ServiceInfo
import io.wanjuan.app.R
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.NotificationId
import io.wanjuan.app.ui.main.MainActivity
import kotlinx.coroutines.Job

/** Keeps a user-initiated refresh runnable after its Activity goes into the background. */
class BookshelfRefreshService : Service() {
    companion object {
        private const val CHANNEL_ID = "bookshelf_refresh"
        private const val WAKE_LOCK_TIMEOUT = 10 * 60 * 1000L
        private val mainHandler = Handler(Looper.getMainLooper())
        // All access is on the main thread. Completed jobs may precede service creation.
        private val tasks = linkedSetOf<Job>()
        private var instance: BookshelfRefreshService? = null
        private var progress = ""

        fun track(context: Context, task: Job) {
            check(Looper.myLooper() == Looper.getMainLooper())
            if (task.isCompleted || !tasks.add(task)) return
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, BookshelfRefreshService::class.java)
                )
            } catch (error: Exception) {
                tasks.remove(task)
                throw error
            }
            task.invokeOnCompletion {
                mainHandler.post {
                    tasks.remove(task)
                    instance?.stopIfFinished()
                }
            }
        }

        fun updateProgress(message: String) {
            check(Looper.myLooper() == Looper.getMainLooper())
            if (message == progress) return
            progress = message
            instance?.scheduleNotification()
        }
    }

    private val ownedTasks = linkedSetOf<Job>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false
    private var lastStartId = 0
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }
    private var notificationPending = false
    private val updateNotification = Runnable {
        notificationPending = false
        if (foreground && tasks.isNotEmpty()) {
            notificationManager.notify(NotificationId.BookshelfRefreshService, notification())
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, getString(R.string.bookshelf_refresh_notification), NotificationManager.IMPORTANCE_LOW
            ))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        ownedTasks.addAll(tasks)
        try {
            // Even an already completed task must fulfil startForegroundService's contract.
            ServiceCompat.startForeground(
                this, NotificationId.BookshelfRefreshService, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
            foreground = true
            if (tasks.isNotEmpty() && wakeLock?.isHeld != true) {
                wakeLock = getSystemService(PowerManager::class.java).newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "wanjuan:BookshelfRefresh"
                ).apply {
                    setReferenceCounted(false)
                    // Bound CPU protection if a source never returns; always release on completion.
                    acquire(WAKE_LOCK_TIMEOUT)
                }
            }
            stopIfFinished()
        } catch (error: Exception) {
            AppLog.put("书架后台刷新服务启动失败", error)
            cancelTasksAndStop()
        }
        // Never replay an in-memory UI request after process death.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelTasksAndStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(updateNotification)
        notificationPending = false
        ownedTasks.forEach { it.cancel() }
        ownedTasks.clear()
        releaseWakeLock()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun stopIfFinished() {
        if (tasks.any { !it.isCompleted }) return
        mainHandler.removeCallbacks(updateNotification)
        notificationPending = false
        releaseWakeLock()
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        stopSelfResult(lastStartId)
    }

    private fun cancelTasksAndStop() {
        ownedTasks.forEach { it.cancel() }
        mainHandler.removeCallbacks(updateNotification)
        notificationPending = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun scheduleNotification() {
        // Chapter completions can arrive in bursts; keep notification updates bounded.
        if (foreground && !notificationPending) {
            notificationPending = true
            mainHandler.postDelayed(updateNotification, 500)
        }
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_refresh_white_24dp)
        .setContentTitle(getString(R.string.bookshelf_refresh_notification))
        .setContentText(progress.ifBlank { getString(R.string.bookshelf_sync_pulling) })
        .setContentIntent(PendingIntent.getActivity(this, NotificationId.BookshelfRefreshService,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()
}
