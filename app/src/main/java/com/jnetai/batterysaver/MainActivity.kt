package com.jnetai.batterysaver

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.jnetai.batterysaver.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BS_MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var uiUpdateJob: Job? = null
    private var monitorFragment: MonitorFragment? = null

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

        setupViewPager()
        handleIntent(intent)
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

    private fun setupViewPager() {
        try {
            monitorFragment = MonitorFragment()
            val fragments = listOf(
                monitorFragment!!,
                SettingsFragment(),
                AboutFragment()
            )

            val tabTitles = listOf("Monitor", "Settings", "About")

            binding.viewPager.adapter = ViewPagerAdapter(this, fragments)
            binding.viewPager.offscreenPageLimit = 2

            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text = tabTitles[position]
            }.attach()

            binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (position == 0) {
                        startUiUpdates()
                    } else {
                        stopUiUpdates()
                    }
                }
            })

            startUiUpdates()
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS025,
                "Failed to setup ViewPager",
                e
            )
        }
    }

    fun startUiUpdates() {
        stopUiUpdates()
        uiUpdateJob = scope.launch {
            while (isActive) {
                try {
                    updateMonitorUI()
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

    private fun updateMonitorUI() {
        try {
            monitorFragment?.updateUI()
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS010,
                "Failed to update monitor UI",
                e
            )
        }
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
        if (binding.viewPager.currentItem == 0) {
            startUiUpdates()
        }
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

    inner class ViewPagerAdapter(
        activity: AppCompatActivity,
        private val fragments: List<Fragment>
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = fragments.size

        override fun createFragment(position: Int): Fragment = fragments[position]
    }
}
