package com.jnetai.batterysaver

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

data class BatteryInfo(
    val level: Int = -1,
    val scale: Int = 100,
    val temperature: Float = 0f,
    val voltage: Int = 0,
    val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN,
    val plugged: Int = 0,
    val health: Int = BatteryManager.BATTERY_HEALTH_UNKNOWN,
    val technology: String = "",
    val chargeCounter: Int = 0,
    val currentNow: Int = 0,
    val currentAverage: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    val levelPercent: Int
        get() = if (scale > 0) (level * 100) / scale else 0

    val temperatureCelsius: Float
        get() = temperature / 10f

    val voltageVolts: Float
        get() = voltage / 1000f

    val isCharging: Boolean
        get() = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

    val isPlugged: Boolean
        get() = plugged != 0

    val chargingType: String
        get() = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }

    val healthStatus: String
        get() = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

    val isUnstablePower: Boolean
        get() {
            if (!isPlugged) return false
            if (health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE ||
                health == BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE) return true
            if (voltage > 0 && (voltageVolts < 3.5f || voltageVolts > 4.4f)) return true
            return false
        }
}

object BatteryInfoReader {
    private const val TAG = "BS_BatteryReader"

    fun read(context: Context): BatteryInfo {
        return try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            if (intent == null) {
                DebugLogger.logError(
                    DebugLogger.ErrorCode.BS004,
                    "Battery intent was null"
                )
                return BatteryInfo()
            }

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
            val chargeCounter = intent.getIntExtra("charge_counter", 0)
            val currentNow = intent.getIntExtra("current_now", 0)
            val currentAverage = intent.getIntExtra("current_average", 0)

            val info = BatteryInfo(
                level = level,
                scale = scale,
                temperature = temperature.toFloat(),
                voltage = voltage,
                status = status,
                plugged = plugged,
                health = health,
                technology = technology,
                chargeCounter = chargeCounter,
                currentNow = currentNow,
                currentAverage = currentAverage
            )

            DebugLogger.logDebug(
                "Battery read: level=${info.levelPercent}%, temp=${info.temperatureCelsius}°C, " +
                "voltage=${info.voltageVolts}V, charging=${info.isCharging}, " +
                "plugged=${info.chargingType}, health=${info.healthStatus}, " +
                "unstable=${info.isUnstablePower}"
            )

            info
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS004,
                "Failed to read battery info",
                e
            )
            DebugLogger.logStackTrace(e)
            BatteryInfo()
        }
    }
}
