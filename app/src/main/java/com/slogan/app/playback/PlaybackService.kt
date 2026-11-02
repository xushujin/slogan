package com.slogan.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.slogan.app.MainActivity
import com.slogan.app.R
import com.slogan.app.SloganApp
import com.slogan.app.AppContainer
import com.slogan.app.core.playback.PlaybackRequest
import com.slogan.app.core.playback.PlaybackState
import com.slogan.app.domain.model.PlaybackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台播放服务。
 *
 * - 合成与循环都发生在进程内单例引擎里，服务只负责“保活 + 通知 + 生命周期”。
 * - 通知常驻，用户退到后台/锁屏后循环播报不会被打断。
 * - 播放结束或失败时自动退出前台，不留下空转的通知。
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private var stateJob: Job? = null
    private var hasStartedPlayback = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationText: String? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as SloganApp).container
        ensureChannel()
        stateJob = scope.launch {
            container.playbackEngine.state.collect(::onStateChanged)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val request = intent.toPlaybackRequest()
                if (request == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                hasStartedPlayback = false
                lastNotificationText = null
                startInForeground(getString(R.string.notification_preparing))
                refreshWakeLock()
                container.playbackEngine.start(request)
            }

            ACTION_STOP -> {
                container.playbackEngine.stop()
                shutdownService()
            }

            else -> shutdownService()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        container.playbackEngine.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun onStateChanged(state: PlaybackState) {
        when (state) {
            is PlaybackState.Preparing -> {
                hasStartedPlayback = true
                updateNotification(getString(R.string.notification_preparing))
            }

            is PlaybackState.Playing -> {
                hasStartedPlayback = true
                refreshWakeLock()
                val text = if (state.totalIterations <= 0) {
                    getString(R.string.notification_playing_infinite, state.iteration + 1)
                } else {
                    getString(
                        R.string.notification_playing_count,
                        state.iteration + 1,
                        state.totalIterations,
                    )
                }
                updateNotification(text)
            }

            is PlaybackState.Waiting -> {
                hasStartedPlayback = true
                updateNotification(
                    getString(R.string.notification_waiting, state.gapMs / 1000.0),
                )
            }

            PlaybackState.Idle -> {
                if (hasStartedPlayback) shutdownService()
            }

            is PlaybackState.Failed -> {
                updateNotification(state.message)
                shutdownService()
            }
        }
    }

    private fun startInForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), type)
    }

    private fun updateNotification(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_slogan)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun refreshWakeLock() {
        val current = wakeLock
        if (current?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
    }

    private fun shutdownService() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun Intent.toPlaybackRequest(): PlaybackRequest? {
        val text = getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return null
        val settings = PlaybackSettings(
            intervalMs = getIntExtra(EXTRA_INTERVAL, PlaybackSettings.DEFAULT_INTERVAL_MS),
            repeatCount = getIntExtra(EXTRA_REPEAT, PlaybackSettings.DEFAULT_REPEAT_COUNT),
            voiceName = getStringExtra(EXTRA_VOICE),
            speechRate = getFloatExtra(EXTRA_RATE, 1.0f),
            pitch = getFloatExtra(EXTRA_PITCH, 1.0f),
            volume = getFloatExtra(EXTRA_VOLUME, 0.8f),
            boost = getBooleanExtra(EXTRA_BOOST, true),
            themeId = PlaybackSettings.DEFAULT_THEME_ID,
        ).normalized()
        return PlaybackRequest(
            title = getStringExtra(EXTRA_TITLE).orEmpty(),
            text = text,
            settings = settings,
        )
    }

    companion object {
        const val ACTION_START = "com.slogan.app.action.START"
        const val ACTION_STOP = "com.slogan.app.action.STOP"

        private const val CHANNEL_ID = "slogan_playback"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "Slogan:playback"
        private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 1000L

        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_VOICE = "voice"
        private const val EXTRA_INTERVAL = "interval"
        private const val EXTRA_REPEAT = "repeat"
        private const val EXTRA_RATE = "rate"
        private const val EXTRA_PITCH = "pitch"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_BOOST = "boost"

        fun startIntent(context: Context, request: PlaybackRequest): Intent =
            Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, request.title)
                putExtra(EXTRA_TEXT, request.text)
                putExtra(EXTRA_VOICE, request.settings.voiceName)
                putExtra(EXTRA_INTERVAL, request.settings.intervalMs)
                putExtra(EXTRA_REPEAT, request.settings.repeatCount)
                putExtra(EXTRA_RATE, request.settings.speechRate)
                putExtra(EXTRA_PITCH, request.settings.pitch)
                putExtra(EXTRA_VOLUME, request.settings.volume)
                putExtra(EXTRA_BOOST, request.settings.boost)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_STOP)
    }
}
