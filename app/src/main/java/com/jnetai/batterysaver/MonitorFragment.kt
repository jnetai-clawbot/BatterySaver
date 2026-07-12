package com.jnetai.batterysaver

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.jnetai.batterysaver.databinding.FragmentMonitorBinding

class MonitorFragment : Fragment() {
    companion object {
        private const val TAG = "BS_MonitorFragment"
    }

    private var _binding: FragmentMonitorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnToggleService.setOnClickListener {
            toggleService()
        }

        updateUI()
    }

    fun updateUI() {
        try {
            val context = context ?: return
            val batteryInfo = BatteryInfoReader.read(context)
            val prefs = context.getSharedPreferences("battery_saver_prefs", android.content.Context.MODE_PRIVATE)

            val isRunning = BatteryMonitorService.isServiceRunning
            binding.tvServiceStatus.text = if (isRunning) "Service Running" else "Service Stopped"
            binding.tvServiceStatus.setTextColor(
                if (isRunning) context.getColor(android.R.color.holo_green_light)
                else context.getColor(android.R.color.holo_red_light)
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
                "Failed to update monitor fragment UI",
                e
            )
        }
    }

    private fun toggleService() {
        try {
            val context = context ?: return
            if (BatteryMonitorService.isServiceRunning) {
                val intent = Intent(context, BatteryMonitorService::class.java).apply {
                    action = BatteryMonitorService.ACTION_STOP
                }
                context.startService(intent)
                DebugLogger.logInfo("User stopped monitoring service")
            } else {
                val intent = Intent(context, BatteryMonitorService::class.java).apply {
                    action = BatteryMonitorService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
