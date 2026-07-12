package com.jnetai.batterysaver

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object AlertEngine {
    private const val TAG = "BS_AlertEngine"
    private const val VIBRATE_DURATION_MS = 500L
    private const val ALARM_INTERVAL_MS = 2000L

    private var scheduler: ScheduledExecutorService? = null
    private var alertFuture: ScheduledFuture<*>? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isAlerting = false
    private var wakeLock: PowerManager.WakeLock? = null

    fun startAlert(context: Context, alertOnMute: Boolean, vibrateAndSound: Boolean) {
        if (isAlerting) {
            DebugLogger.logDebug("Alert already active, ignoring start request")
            return
        }

        DebugLogger.logInfo("Starting alert: alertOnMute=$alertOnMute, vibrateAndSound=$vibrateAndSound")

        try {
            isAlerting = true

            acquireWakeLock(context)

            scheduler = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "BS-AlertThread").apply {
                    isDaemon = true
                }
            }

            alertFuture = scheduler?.scheduleAtFixedRate({
                try {
                    if (vibrateAndSound) {
                        vibrate(context)
                    }
                    playAlarm(context, alertOnMute)
                } catch (e: Exception) {
                    DebugLogger.logError(
                        DebugLogger.ErrorCode.BS003,
                        "Alert cycle error",
                        e
                    )
                }
            }, 0, ALARM_INTERVAL_MS, TimeUnit.MILLISECONDS)

        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS003,
                "Failed to start alert",
                e
            )
            DebugLogger.logStackTrace(e)
            isAlerting = false
        }
    }

    fun stopAlert() {
        if (!isAlerting) {
            DebugLogger.logDebug("Alert not active, ignoring stop request")
            return
        }

        DebugLogger.logInfo("Stopping alert")

        try {
            alertFuture?.cancel(true)
            alertFuture = null

            scheduler?.shutdownNow()
            scheduler = null

            stopMediaPlayer()
            releaseWakeLock()

            isAlerting = false
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS003,
                "Failed to stop alert",
                e
            )
            DebugLogger.logStackTrace(e)
            isAlerting = false
        }
    }

    fun isActive(): Boolean = isAlerting

    private fun vibrate(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        VIBRATE_DURATION_MS,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(
                            VIBRATE_DURATION_MS,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(VIBRATE_DURATION_MS)
                }
            }
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS014,
                "Vibration failed",
                e
            )
        }
    }

    private fun playAlarm(context: Context, alertOnMute: Boolean) {
        try {
            stopMediaPlayer()

            mediaPlayer = MediaPlayer().apply {
                if (alertOnMute) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                } else {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }

                val alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (alarmUri != null) {
                    setDataSource(context, alarmUri)
                } else {
                    val afd: AssetFileDescriptor = context.assets.openFd("alarm_default.ogg")
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }

                isLooping = false
                setOnPreparedListener { mp ->
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    mp.release()
                }
                setOnErrorListener { mp, what, extra ->
                    DebugLogger.logError(
                        DebugLogger.ErrorCode.BS015,
                        "MediaPlayer error: what=$what, extra=$extra"
                    )
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS015,
                "Failed to play alarm sound",
                e
            )
            DebugLogger.logStackTrace(e)
            try {
                fallbackAlarm(context, alertOnMute)
            } catch (e2: Exception) {
                DebugLogger.logError(
                    DebugLogger.ErrorCode.BS015,
                    "Fallback alarm also failed",
                    e2
                )
            }
        }
    }

    private fun fallbackAlarm(context: Context, alertOnMute: Boolean) {
        try {
            stopMediaPlayer()

            mediaPlayer = MediaPlayer().apply {
                if (alertOnMute) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                } else {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }

                val notificationUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (notificationUri != null) {
                    setDataSource(context, notificationUri)
                } else {
                    val afd: AssetFileDescriptor = context.assets.openFd("alarm_default.ogg")
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }

                isLooping = false
                setOnPreparedListener { mp -> mp.start() }
                setOnCompletionListener { mp -> mp.release() }
                prepareAsync()
            }
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS015,
                "Fallback alarm failed completely",
                e
            )
        }
    }

    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS015,
                "Error stopping media player",
                e
            )
            mediaPlayer = null
        }
    }

    private fun acquireWakeLock(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "BS:AlertWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L)
            DebugLogger.logDebug("WakeLock acquired")
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS013,
                "Failed to acquire wake lock",
                e
            )
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    DebugLogger.logDebug("WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS013,
                "Failed to release wake lock",
                e
            )
            wakeLock = null
        }
    }
}
