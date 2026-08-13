package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.SoundEngine

class SoundPlaybackService : Service() {

    private val binder = LocalBinder()
    val soundEngine = SoundEngine()
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var mediaSession: MediaSessionCompat

    var isPowerSaveEnabled: Boolean = false
        set(value) {
            field = value
            soundEngine.isPowerSaveEnabled = value
        }

    var isNormalizationEnabled: Boolean = true
        set(value) {
            field = value
            soundEngine.isNormalizationEnabled = value
        }

    inner class LocalBinder : Binder() {
        fun getService(): SoundPlaybackService = this@SoundPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SoundSlumber::BackgroundAudioLock"
        )
        
        mediaSession = MediaSessionCompat(this, "SoundSlumberSession").apply {
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "SoundSlumber")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Sleep Ambient Sounds")
                    .build()
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    startPlayback()
                }

                override fun onPause() {
                    stopPlayback()
                }

                override fun onStop() {
                    stopPlayback()
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startPlayback()
            ACTION_PAUSE -> stopPlayback()
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
            }
        }
        return START_STICKY
    }

    fun startPlayback() {
        soundEngine.start()
        acquireWakeLock()
        
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
                .build()
        )
        
        startForegroundNotification("Playing Sleep Sounds", true)
    }

    fun stopPlayback() {
        soundEngine.stop()
        releaseWakeLock()
        
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
                .build()
        )
        
        startForegroundNotification("Paused", false)
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(8 * 60 * 60 * 1000L) // 8 hours max timeout
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun startForegroundNotification(status: String, isPlaying: Boolean) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause",
                PendingIntent.getService(this, 1, Intent(this, SoundPlaybackService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "Play",
                PendingIntent.getService(this, 2, Intent(this, SoundPlaybackService::class.java).setAction(ACTION_PLAY), PendingIntent.FLAG_IMMUTABLE)
            ).build()
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SoundSlumber")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use launcher icon as small icon
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Ambient Sound Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background playback channel for SoundSlumber"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        soundEngine.stop()
        releaseWakeLock()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "soundslumber_playback"
        const val NOTIFICATION_ID = 101
        const val ACTION_PLAY = "com.example.soundslumber.PLAY"
        const val ACTION_PAUSE = "com.example.soundslumber.PAUSE"
        const val ACTION_STOP = "com.example.soundslumber.STOP"
    }
}
