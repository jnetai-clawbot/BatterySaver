package com.jnetai.batterysaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BS_BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        DebugLogger.logDebug("BootReceiver received: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = context.getSharedPreferences("battery_saver_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean(BatteryMonitorService.PREF_AUTO_START, true)

            if (!autoStart) {
                DebugLogger.logDebug("Auto-start disabled in settings, skipping")
                return
            }

            try {
                DebugLogger.logInfo("Auto-starting BatteryMonitorService after boot")
                val serviceIntent = Intent(context, BatteryMonitorService::class.java).apply {
                    action = BatteryMonitorService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                DebugLogger.logError(
                    DebugLogger.ErrorCode.BS008,
                    "Failed to auto-start service on boot",
                    e
                )
                DebugLogger.logStackTrace(e)
            }
        }
    }
}
