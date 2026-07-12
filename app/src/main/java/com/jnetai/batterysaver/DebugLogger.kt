package com.jnetai.batterysaver

import android.util.Log

object DebugLogger {
    private const val TAG = "BS_Debug"
    const val ERROR_PREFIX = "BS-"

    enum class ErrorCode(val code: String, val description: String) {
        BS001("BS001", "Service start failed"),
        BS002("BS002", "Service stop failed"),
        BS003("BS003", "Alert engine error"),
        BS004("BS004", "Battery info read error"),
        BS005("BS005", "Settings load error"),
        BS006("BS006", "Settings save error"),
        BS007("BS007", "Notification error"),
        BS008("BS008", "Boot receiver error"),
        BS009("BS009", "Battery receiver error"),
        BS010("BS010", "UI update error"),
        BS011("BS011", "GitHub API error"),
        BS012("BS012", "Share intent error"),
        BS013("BS013", "Wake lock error"),
        BS014("BS014", "Vibration error"),
        BS015("BS015", "Audio playback error"),
        BS016("BS016", "Foreground service error"),
        BS017("BS017", "Permission error"),
        BS018("BS018", "Thread pool error"),
        BS019("BS019", "Battery intent parse error"),
        BS020("BS020", "Temperature parse error"),
        BS021("BS021", "Voltage parse error"),
        BS022("BS022", "Charging state error"),
        BS023("BS023", "Alert dialog error"),
        BS024("BS024", "Lifecycle error"),
        BS025("BS025", "ViewPager error"),
        BS999("BS999", "Unknown error")
    }

    fun logDebug(message: String) {
        Log.d(TAG, message)
    }

    fun logInfo(message: String) {
        Log.i(TAG, message)
    }

    fun logWarning(message: String) {
        Log.w(TAG, message)
    }

    fun logError(code: ErrorCode, message: String, exception: Exception? = null) {
        val fullMsg = "[${ERROR_PREFIX}${code.code}] ${code.description}: $message"
        if (exception != null) {
            Log.e(TAG, fullMsg, exception)
        } else {
            Log.e(TAG, fullMsg)
        }
    }

    fun logStackTrace(exception: Exception) {
        Log.e(TAG, "Stack trace:", exception)
    }
}
