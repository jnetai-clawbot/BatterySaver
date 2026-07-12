package com.jnetai.batterysaver

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.jnetai.batterysaver.databinding.FragmentAboutBinding
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AboutFragment : Fragment() {
    companion object {
        private const val TAG = "BS_AboutFragment"
        private const val GITHUB_API_URL = "https://api.github.com/repos/jnetai-clawbot/BatterySaver/releases/latest"
        private const val GITHUB_RELEASES_URL = "https://github.com/jnetai-clawbot/BatterySaver/releases"
    }

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            val context = requireContext()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0.0"
            binding.tvVersion.text = "Version $versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            binding.tvVersion.text = "Version 1.0.0"
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS010,
                "Failed to get version name",
                e
            )
        }

        binding.btnCheckUpdates.setOnClickListener {
            checkForUpdates()
        }

        binding.btnShareApp.setOnClickListener {
            shareApp()
        }
    }

    private fun checkForUpdates() {
        Toast.makeText(requireContext(), "Checking for updates...", Toast.LENGTH_SHORT).show()
        DebugLogger.logDebug("Checking for updates from GitHub API")

        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val latestVersion = json.optString("tag_name", "").removePrefix("v")
                    val htmlUrl = json.optString("html_url", GITHUB_RELEASES_URL)

                    val context = requireContext()
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersion = packageInfo.versionName ?: "1.0.0"

                    withContext(Dispatchers.Main) {
                        if (latestVersion.isNotEmpty() && latestVersion != currentVersion) {
                            androidx.appcompat.app.AlertDialog.Builder(context)
                                .setTitle("Update Available")
                                .setMessage("New version $latestVersion is available!\n\nCurrent version: $currentVersion\n\nVisit GitHub to download the latest release.")
                                .setPositiveButton("Open GitHub") { _, _ ->
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GITHUB_RELEASES_URL))
                                    startActivity(intent)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            Toast.makeText(context, "You are on the latest version ($currentVersion)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (responseCode == 404) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No releases found yet", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Could not check for updates (HTTP $responseCode)", Toast.LENGTH_SHORT).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                DebugLogger.logError(
                    DebugLogger.ErrorCode.BS011,
                    "Failed to check for updates",
                    e
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Update check failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareApp() {
        try {
            val shareText = "Check out Battery Saver - a battery health monitoring app for Android!\n\n" +
                    "Download from GitHub: $GITHUB_RELEASES_URL"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Battery Saver App")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Share Battery Saver"))
        } catch (e: Exception) {
            DebugLogger.logError(
                DebugLogger.ErrorCode.BS012,
                "Failed to share app",
                e
            )
            Toast.makeText(requireContext(), "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }
}
