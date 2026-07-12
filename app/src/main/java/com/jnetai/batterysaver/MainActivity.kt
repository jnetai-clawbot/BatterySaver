package com.jnetai.batterysaver

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jnetai.batterysaver.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BS_MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var uiUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.logDebug("MainActivity onCreate")

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS010,
                "Failed to inflate layout",
                e
            )
            return
        }

        binding.btnToggleService.setOnClickListener {
            toggleService()
        }

        binding.btnSettings.setOnClickListener {
            SettingsBottomSheet().show(supportFragmentManager, "SettingsBottomSheet")
        }

        binding.btnAbout.setOnClickListener {
            AboutBottomSheet().show(supportFragmentManager, "AboutBottomSheet")
        }

        handleIntent(intent)
        startUiUpdates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (AlertEngine.isActive()) {
            showAlertDialog()
        }
    }

    private fun toggleService() {
        try {
            if (BatteryMonitorService.isServiceRunning) {
                val intent = Intent(this, BatteryMonitorService::class.java).apply {
                    action = BatteryMonitorService.ACTION_STOP
                }
                startService(intent)
                DebugLogger.logInfo("User stopped monitoring service")
            } else {
                val intent = Intent(this, BatteryMonitorService::class.java).apply {
                    action = BatteryMonitorService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                DebugLogger.logInfo("User started monitoring service")
            }
            updateUI()
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS010,
                "Failed to toggle service",
                e
            )
        }
    }

    fun updateUI() {
        try {
            val batteryInfo = BatteryInfoReader.read(this)
            val prefs = getSharedPreferences("battery_saver_prefs", MODE_PRIVATE)

            val isRunning = BatteryMonitorService.isServiceRunning
            binding.tvServiceStatus.text = if (isRunning) "Service Running" else "Service Stopped"
            binding.tvServiceStatus.setTextColor(
                if (isRunning) getColor(android.R.color.holo_green_light)
                else getColor(android.R.color.holo_red_light)
            )

            binding.btnToggleService.text = if (isRunning) "Stop Monitoring" else "Start Monitoring"

            binding.tvBatteryLevel.text = "${batteryInfo.levelPercent}%"
            binding.progressBattery.progress = batteryInfo.levelPercent

            binding.tvTemperature.text = "${"%.1f".format(batteryInfo.temperatureCelsius)}°C"

            binding.tvVoltage.text = "${"%.2f".format(batteryInfo.voltageVolts)}V"

            binding.tvChargingStatus.text = when {
                batteryInfo.isCharging -> "Charging (${batteryInfo.chargingType})"
                else -> "Not Charging"
            }

            val batteryThreshold = prefs.getInt(BatteryMonitorService.PREF_BATTERY_THRESHOLD, 10)
            val overheatThreshold = prefs.getInt(BatteryMonitorService.PREF_OVERHEAT_THRESHOLD, 45)

            binding.tvLowBatteryThreshold.text = "${batteryThreshold}%"
            binding.tvOverheatThreshold.text = "${overheatThreshold}°C"

        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS010,
                "Failed to update UI",
                e
            )
        }
    }

    fun startUiUpdates() {
        stopUiUpdates()
        uiUpdateJob = scope.launch {
            while (isActive) {
                try {
                    updateUI()
                    delay(2000)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    DebugLogger.logError(
                        DebugLogger.ErrorCode.BS010,
                        "UI update error",
                        e
                    )
                    delay(5000)
                }
            }
        }
    }

    fun stopUiUpdates() {
        uiUpdateJob?.cancel()
        uiUpdateJob = null
    }

    fun showAlertDialog() {
        try {
            val alertType = when {
                BatteryMonitorService.isServiceRunning -> "Battery Alert!"
                else -> "Alert"
            }

            val message = "An alert has been triggered. Press OK to dismiss."

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(alertType)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK") { dialog, _ ->
                    dismissAlert()
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS023,
                "Failed to show alert dialog",
                e
            )
        }
    }

    private fun dismissAlert() {
        try {
            val intent = Intent(this, BatteryMonitorService::class.java).apply {
                action = BatteryMonitorService.ACTION_DISMISS_ALERT
            }
            startService(intent)
            DebugLogger.logInfo("Alert dismissed by user")
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS023,
                "Failed to dismiss alert",
                e
            )
        }
    }

    override fun onResume() {
        super.onResume()
        startUiUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopUiUpdates()
    }

    override fun onDestroy() {
        stopUiUpdates()
        scope.cancel()
        super.onDestroy()
    }
}
