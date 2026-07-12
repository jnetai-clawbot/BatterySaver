package com.jnetai.batterysaver

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jnetai.batterysaver.databinding.FragmentSettingsBinding

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        try {
            val context = context ?: return
            val prefs = context.getSharedPreferences("battery_saver_prefs", Context.MODE_PRIVATE)

            val batteryThreshold = prefs.getInt(BatteryMonitorService.PREF_BATTERY_THRESHOLD, 10)
            binding.seekBarBatteryThreshold.progress = batteryThreshold
            binding.tvBatteryThresholdValue.text = "${batteryThreshold}%"

            val overheatThreshold = prefs.getInt(BatteryMonitorService.PREF_OVERHEAT_THRESHOLD, 45)
            val overheatProgress = overheatThreshold - 30
            binding.seekBarOverheatThreshold.progress = overheatProgress.coerceIn(0, 30)
            binding.tvOverheatThresholdValue.text = "${overheatThreshold}°C"

            binding.switchAlertOnMute.isChecked = prefs.getBoolean(BatteryMonitorService.PREF_ALERT_ON_MUTE, true)
            binding.switchUnstablePower.isChecked = prefs.getBoolean(BatteryMonitorService.PREF_UNSTABLE_POWER, true)
            binding.switchOverheatAlert.isChecked = prefs.getBoolean(BatteryMonitorService.PREF_OVERHEAT_ALERT, true)
            binding.switchBatteryLowAlert.isChecked = prefs.getBoolean(BatteryMonitorService.PREF_BATTERY_LOW_ALERT, true)
            binding.switchVibrateSound.isChecked = prefs.getBoolean(BatteryMonitorService.PREF_VIBRATE_SOUND, true)
            binding.switchAutoStart.isChecked = prefs.getBoolean(BatteryMonitorService.PREF_AUTO_START, true)
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS005,
                "Failed to load settings",
                e
            )
        }
    }

    private fun setupListeners() {
        binding.seekBarBatteryThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = if (progress < 1) 1 else progress
                binding.tvBatteryThresholdValue.text = "${value}%"
                if (fromUser) saveIntSetting(BatteryMonitorService.PREF_BATTERY_THRESHOLD, value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarOverheatThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 30 + progress
                binding.tvOverheatThresholdValue.text = "${value}°C"
                if (fromUser) saveIntSetting(BatteryMonitorService.PREF_OVERHEAT_THRESHOLD, value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.switchAlertOnMute.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanSetting(BatteryMonitorService.PREF_ALERT_ON_MUTE, isChecked)
        }

        binding.switchUnstablePower.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanSetting(BatteryMonitorService.PREF_UNSTABLE_POWER, isChecked)
        }

        binding.switchOverheatAlert.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanSetting(BatteryMonitorService.PREF_OVERHEAT_ALERT, isChecked)
        }

        binding.switchBatteryLowAlert.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanSetting(BatteryMonitorService.PREF_BATTERY_LOW_ALERT, isChecked)
        }

        binding.switchVibrateSound.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanSetting(BatteryMonitorService.PREF_VIBRATE_SOUND, isChecked)
        }

        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanSetting(BatteryMonitorService.PREF_AUTO_START, isChecked)
        }
    }

    private fun saveIntSetting(key: String, value: Int) {
        try {
            val context = context ?: return
            context.getSharedPreferences("battery_saver_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt(key, value)
                .apply()
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS006,
                "Failed to save int setting: $key",
                e
            )
        }
    }

    private fun saveBooleanSetting(key: String, value: Boolean) {
        try {
            val context = context ?: return
            context.getSharedPreferences("battery_saver_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, value)
                .apply()
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS006,
                "Failed to save boolean setting: $key",
                e
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
