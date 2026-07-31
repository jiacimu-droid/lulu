package me.rerere.rikkahub.plugin.webview

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.rerere.rikkahub.POMODORO_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyStore
import org.koin.android.ext.android.inject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val TAG = "PomodoroTimerService"

class PomodoroTimerService : android.app.Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.POMODORO_START"
        const val ACTION_STOP = "me.rerere.rikkahub.action.POMODORO_STOP"
        const val ACTION_BOOT_RESTORE = "me.rerere.rikkahub.action.POMODORO_BOOT_RESTORE"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_TASK = "task"
        const val EXTRA_RECORD_STUDY = "record_study"
        const val NOTIFICATION_ID = 3001
        const val ACTION_TIMER_END = "me.rerere.rikkahub.TIMER_END"

        private const val PREFS_NAME = "pomodoro_timer_prefs"
        private const val KEY_END_TIMESTAMP = "end_timestamp"
        private const val KEY_TOTAL_SECONDS = "total_seconds"
        private const val KEY_TASK = "task"
        private const val KEY_RECORD_STUDY = "record_study"
        private const val KEY_REWARD_RECORDED = "reward_recorded"

        @Volatile private var remainingSeconds: Int = 0
        @Volatile private var totalSeconds: Int = 0
        @Volatile private var task: String = ""
        @Volatile private var running: Boolean = false

        fun getRemainingSeconds(): Int = remainingSeconds
        fun getTotalSeconds(): Int = totalSeconds
        fun getTask(): String = task
        fun isRunning(): Boolean = running

        fun start(
            context: Context,
            seconds: Int,
            task: String = "",
            recordStudy: Boolean = false,
        ) {
            val intent = Intent(context, PomodoroTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_TASK, task)
                putExtra(EXTRA_RECORD_STUDY, recordStudy)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PomodoroTimerService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    private val studyStore by inject<StudyStore>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scheduler: ScheduledExecutorService? = null
    private var endTimestamp: Long = 0L
    private var recordStudy: Boolean = false

    private val timerEndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            timerEndReceiver,
            IntentFilter(ACTION_TIMER_END),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduler?.shutdownNow()
        scheduler = null
        running = false
        remainingSeconds = 0
        runCatching { LocalBroadcastManager.getInstance(this).unregisterReceiver(timerEndReceiver) }
            .onFailure { Log.w(TAG, "Error unregistering receiver", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCountdown(
                seconds = intent.getIntExtra(EXTRA_SECONDS, 25 * 60),
                taskText = intent.getStringExtra(EXTRA_TASK).orEmpty(),
                shouldRecordStudy = intent.getBooleanExtra(EXTRA_RECORD_STUDY, false),
            )
            ACTION_STOP -> stopCountdown(recordElapsed = true)
            ACTION_BOOT_RESTORE, null -> restoreFromPrefs()
        }
        return START_STICKY
    }

    private fun startCountdown(seconds: Int, taskText: String, shouldRecordStudy: Boolean) {
        scheduler?.shutdownNow()
        totalSeconds = seconds.coerceAtLeast(1)
        remainingSeconds = totalSeconds
        task = taskText.trim()
        recordStudy = shouldRecordStudy
        running = true
        endTimestamp = System.currentTimeMillis() + totalSeconds * 1000L
        saveToPrefs(rewardRecorded = false)
        startForegroundCompat(buildNotification(remainingSeconds))
        startScheduler()
    }

    private fun startScheduler() {
        scheduler?.shutdownNow()
        scheduler = Executors.newSingleThreadScheduledExecutor().also { executor ->
            executor.scheduleAtFixedRate({
                runCatching {
                    val next = ((endTimestamp - System.currentTimeMillis() + 999L) / 1000L).toInt()
                    if (next <= 0) {
                        remainingSeconds = 0
                        running = false
                        recordStudyCompletion(totalSeconds)
                        onTimerEnd()
                        scheduler?.shutdownNow()
                    } else {
                        remainingSeconds = next
                        updateNotification(buildNotification(next))
                    }
                }.onFailure { Log.e(TAG, "Pomodoro tick failed", it) }
            }, 0, 1, TimeUnit.SECONDS)
        }
    }

    private fun stopCountdown(recordElapsed: Boolean) {
        scheduler?.shutdownNow()
        scheduler = null
        val elapsed = (totalSeconds - remainingSeconds).coerceIn(0, totalSeconds)
        running = false
        if (recordElapsed) recordStudyCompletion(elapsed)
        remainingSeconds = 0
        clearPrefs()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun restoreFromPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedEnd = prefs.getLong(KEY_END_TIMESTAMP, 0L)
        if (savedEnd <= 0L) {
            stopSelf()
            return
        }
        totalSeconds = prefs.getInt(KEY_TOTAL_SECONDS, 0)
        task = prefs.getString(KEY_TASK, "").orEmpty()
        recordStudy = prefs.getBoolean(KEY_RECORD_STUDY, false)
        endTimestamp = savedEnd
        val restored = ((savedEnd - System.currentTimeMillis() + 999L) / 1000L).toInt()
        if (restored <= 0) {
            remainingSeconds = 0
            running = false
            recordStudyCompletion(totalSeconds)
            onTimerEnd()
            return
        }
        remainingSeconds = restored
        running = true
        startForegroundCompat(buildNotification(restored))
        startScheduler()
    }

    private fun recordStudyCompletion(elapsedSeconds: Int) {
        if (!recordStudy) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REWARD_RECORDED, false)) return
        prefs.edit().putBoolean(KEY_REWARD_RECORDED, true).apply()
        val minutes = (elapsedSeconds.coerceAtLeast(0) / 60).coerceAtLeast(0)
        if (minutes <= 0) return
        serviceScope.launch {
            studyStore.update { state -> StudyRules.completePomodoro(state, minutes).state }
        }
    }

    private fun onTimerEnd() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_TIMER_END))
        clearPrefs()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveToPrefs(rewardRecorded: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_END_TIMESTAMP, endTimestamp)
            .putInt(KEY_TOTAL_SECONDS, totalSeconds)
            .putString(KEY_TASK, task)
            .putBoolean(KEY_RECORD_STUDY, recordStudy)
            .putBoolean(KEY_REWARD_RECORDED, rewardRecorded)
            .apply()
    }

    private fun clearPrefs() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(notification: Notification) {
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(seconds: Int): Notification {
        val timeText = "%02d:%02d".format(seconds / 60, seconds % 60)
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, PomodoroTimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, POMODORO_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(task.ifBlank { "番茄钟" })
            .setContentText("剩余时间：$timeText")
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "结束", stopPendingIntent)
            .build()
    }
}
