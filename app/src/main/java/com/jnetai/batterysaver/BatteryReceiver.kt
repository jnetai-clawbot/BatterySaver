package com.jnetai.batterysaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager

class BatteryReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BS_BatteryReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        DebugLogger.logDebug("BatteryReceiver received: $action")

        try {
            when (action) {
                Intent.ACTION_BATTERY_LOW -> {
                    DebugLogger.logWarning("System battery low broadcast received")
                    ensureServiceRunning(context)
                }
                Intent.ACTION_BATTERY_OKAY -> {
                    DebugLogger.logInfo("System battery okay broadcast received")
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    DebugLogger.logInfo("Power connected")
                    ensureServiceRunning(context)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    DebugLogger.logInfo("Power disconnected")
                    ensureServiceRunning(context)
                }
            }
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS009,
                "Error handling battery broadcast: $action",
                e
            )
            DebugLogger.logStackTrace(e)
        }
    }

    private fun ensureServiceRunning(context: Context) {
        if (!BatteryMonitorService.isServiceRunning) {
            val prefs = context.getSharedPreferences("battery_saver_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean(BatteryMonitorService.PREF_AUTO_START, true)
            if (autoStart) {
                DebugLogger.logInfo("Ensuring BatteryMonitorService is running")
                try {
                    val serviceIntent = Intent(context, BatteryMonitorService::class.java).apply {
                        action = BatteryMonitorService.ACTION_START
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    DebugLogger.logError(
                        DebugLogger.ErrorCode.BS009,
                        "Failed to ensure service running",
                        e
                    )
                }
            }
        }
    }
}
