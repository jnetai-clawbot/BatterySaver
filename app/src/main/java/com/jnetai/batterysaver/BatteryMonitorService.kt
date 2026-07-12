package com.jnetai.batterysaver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class BatteryMonitorService : Service() {
    companion object {
        private const val TAG = "BS_MonitorService"
        const val CHANNEL_ID = "battery_monitor_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.jnetai.batterysaver.START"
        const val ACTION_STOP = "com.jnetai.batterysaver.STOP"
        const val ACTION_DISMISS_ALERT = "com.jnetai.batterysaver.DISMISS_ALERT"
        const val PREF_BATTERY_THRESHOLD = "battery_threshold"
        const val PREF_OVERHEAT_THRESHOLD = "overheat_threshold"
        const val PREF_ALERT_ON_MUTE = "alert_on_mute"
        const val PREF_UNSTABLE_POWER = "unstable_power_alert"
        const val PREF_OVERHEAT_ALERT = "overheat_alert"
        const val PREF_BATTERY_LOW_ALERT = "battery_low_alert"
        const val PREF_VIBRATE_SOUND = "vibrate_sound"
        const val PREF_AUTO_START = "auto_start"

        @Volatile
        var isServiceRunning = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
    private var lastBatteryInfo: BatteryInfo? = null
    private var alertActive = AtomicBoolean(false)
    private var lastAlertType: String? = null

    override fun onCreate() {
        super.onCreate()
        DebugLogger.logInfo("BatteryMonitorService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.logDebug("onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
            ACTION_DISMISS_ALERT -> dismissAlert()
            else -> {
                if (!isServiceRunning) {
                    startMonitoring()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        if (isServiceRunning) {
            DebugLogger.logDebug("Service already running")
            return
        }

        DebugLogger.logInfo("Starting battery monitoring")

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            isServiceRunning = true
            startMonitorLoop()
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS001,
                "Failed to start monitoring service",
                e
            )
            DebugLogger.logStackTrace(e)
            isServiceRunning = false
            stopSelf()
        }
    }

    private fun stopMonitoring() {
        DebugLogger.logInfo("Stopping battery monitoring")

        try {
            monitorJob?.cancel()
            monitorJob = null
            dismissAlert()
            isServiceRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS002,
                "Failed to stop monitoring service",
                e
            )
            DebugLogger.logStackTrace(e)
            isServiceRunning = false
            stopSelf()
        }
    }

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            DebugLogger.logDebug("Monitor loop started")
            while (isActive && isServiceRunning) {
                try {
                    val batteryInfo = BatteryInfoReader.read(this@BatteryMonitorService)
                    lastBatteryInfo = batteryInfo
                    checkAlerts(batteryInfo)
                    delay(3000)
                } catch (e: CancellationException) {
                    DebugLogger.logDebug("Monitor loop cancelled")
                    break
                } catch (e: Exception) {
                    DebugLogger.logError(
                        DebugLogger.ErrorCode.BS004,
                        "Monitor loop error",
                        e
                    )
                    delay(5000)
                }
            }
            DebugLogger.logDebug("Monitor loop ended")
        }
    }

    private fun checkAlerts(batteryInfo: BatteryInfo) {
        val prefs = getSharedPreferences("battery_saver_prefs", MODE_PRIVATE)

        val batteryThreshold = prefs.getInt(PREF_BATTERY_THRESHOLD, 10)
        val overheatThreshold = prefs.getInt(PREF_OVERHEAT_THRESHOLD, 45)
        val alertOnMute = prefs.getBoolean(PREF_ALERT_ON_MUTE, true)
        val unstablePowerEnabled = prefs.getBoolean(PREF_UNSTABLE_POWER, true)
        val overheatEnabled = prefs.getBoolean(PREF_OVERHEAT_ALERT, true)
        val batteryLowEnabled = prefs.getBoolean(PREF_BATTERY_LOW_ALERT, true)
        val vibrateSound = prefs.getBoolean(PREF_VIBRATE_SOUND, true)

        var shouldAlert = false
        var alertType = ""

        if (batteryLowEnabled && batteryInfo.levelPercent in 1..batteryThreshold) {
            shouldAlert = true
            alertType = "BATTERY_LOW"
            DebugLogger.logWarning(
                "Battery low alert: ${batteryInfo.levelPercent}% <= threshold $batteryThreshold%"
            )
        }

        if (overheatEnabled && batteryInfo.temperatureCelsius >= overheatThreshold) {
            shouldAlert = true
            alertType = "OVERHEAT"
            DebugLogger.logWarning(
                "Overheat alert: ${batteryInfo.temperatureCelsius}°C >= threshold ${overheatThreshold}°C"
            )
        }

        if (unstablePowerEnabled && batteryInfo.isUnstablePower) {
            shouldAlert = true
            alertType = "UNSTABLE_POWER"
            DebugLogger.logWarning(
                "Unstable power alert: voltage=${batteryInfo.voltageVolts}V, health=${batteryInfo.healthStatus}"
            )
        }

        if (shouldAlert && !alertActive.get()) {
            alertActive.set(true)
            lastAlertType = alertType
            AlertEngine.startAlert(this, alertOnMute, vibrateSound)
            showAlertNotification(alertType, batteryInfo)
        } else if (!shouldAlert && alertActive.get()) {
            dismissAlert()
        }
    }

    private fun dismissAlert() {
        if (alertActive.getAndSet(false)) {
            DebugLogger.logInfo("Dismissing alert: type=$lastAlertType")
            AlertEngine.stopAlert()
            lastAlertType = null
            cancelAlertNotification()
        }
    }

    private fun showAlertNotification(alertType: String, batteryInfo: BatteryInfo) {
        try {
            val title = when (alertType) {
                "BATTERY_LOW" -> "Battery Low!"
                "OVERHEAT" -> "Battery Overheating!"
                "UNSTABLE_POWER" -> "Unstable Charging Detected!"
                else -> "Battery Alert!"
            }

            val message = when (alertType) {
                "BATTERY_LOW" -> "Battery at ${batteryInfo.levelPercent}%. Charge your device."
                "OVERHEAT" -> "Temperature at ${"%.1f".format(batteryInfo.temperatureCelsius)}°C. Let device cool down."
                "UNSTABLE_POWER" -> "Voltage: ${"%.2f".format(batteryInfo.voltageVolts)}V. Unplug charger."
                else -> "Check your battery status."
            }

            val dismissIntent = Intent(this, BatteryMonitorService::class.java).apply {
                action = ACTION_DISMISS_ALERT
            }
            val dismissPendingIntent = PendingIntent.getService(
                this, 0, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(openPendingIntent)
                .addAction(android.R.drawable.ic_media_pause, "Dismiss", dismissPendingIntent)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID + 1, alertNotification)
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS007,
                "Failed to show alert notification",
                e
            )
        }
    }

    private fun cancelAlertNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID + 1)
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS007,
                "Failed to cancel alert notification",
                e
            )
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BatteryMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Battery Saver Active")
            .setContentText("Monitoring battery health")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Battery health monitoring alerts"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        DebugLogger.logInfo("BatteryMonitorService onDestroy")
        monitorJob?.cancel()
        scope.cancel()
        dismissAlert()
        isServiceRunning = false
        super.onDestroy()
    }
}
