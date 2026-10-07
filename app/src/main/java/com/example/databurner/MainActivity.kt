package com.example.databurner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.databurner.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())

    private var lastSampleBytes = 0L
    private var lastSampleTime = 0L

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore result */ }

    private val ticker = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.connectionsSlider.addOnChangeListener { _, value, _ ->
            binding.connectionsLabel.text =
                getString(R.string.connections_label, value.toInt())
        }
        binding.connectionsSlider.value = DownloadService.DEFAULT_CONNECTIONS.toFloat()
        binding.connectionsLabel.text =
            getString(R.string.connections_label, DownloadService.DEFAULT_CONNECTIONS)

        binding.startButton.setOnClickListener { start() }
        binding.stopButton.setOnClickListener { stop() }

        // Long-press the all-time card to reset the persistent total.
        val resetLifetime = View.OnLongClickListener {
            LifetimeStats.reset(this)
            binding.lifetimeValue.text = formatBytes(0)
            Toast.makeText(this, R.string.lifetime_reset_done, Toast.LENGTH_SHORT).show()
            true
        }
        binding.lifetimeValue.setOnLongClickListener(resetLifetime)
        binding.lifetimeReset.setOnLongClickListener(resetLifetime)

        maybeRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun start() {
        val connections = binding.connectionsSlider.value.toInt()
        lastSampleBytes = 0L
        lastSampleTime = System.currentTimeMillis()

        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_CONNECTIONS, connections)
        }
        ContextCompat.startForegroundService(this, intent)
        setRunningState(true)
    }

    private fun stop() {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_STOP
        }
        startService(intent)
        setRunningState(false)
    }

    private fun setRunningState(running: Boolean) {
        binding.startButton.isEnabled = !running
        binding.stopButton.isEnabled = running
        binding.connectionsSlider.isEnabled = !running
        binding.statusText.text =
            getString(if (running) R.string.status_running else R.string.status_idle)
    }

    private fun updateUi() {
        val total = BurnStats.totalBytes.get()
        val now = System.currentTimeMillis()

        // Instantaneous speed from the delta since the last sample.
        val speedMbps: Double = if (lastSampleTime > 0 && now > lastSampleTime) {
            val deltaBytes = total - lastSampleBytes
            val deltaSec = (now - lastSampleTime) / 1000.0
            (deltaBytes * 8.0) / 1_000_000.0 / deltaSec
        } else 0.0

        lastSampleBytes = total
        lastSampleTime = now

        binding.speedValue.text = String.format(Locale.US, "%.1f", speedMbps)
        binding.totalValue.text = formatBytes(total)
        binding.connectionsValue.text = BurnStats.activeConnections.get().toString()
        binding.lifetimeValue.text = formatBytes(LifetimeStats.get(this))

        val elapsed = if (BurnStats.running && BurnStats.startedAt > 0) {
            (now - BurnStats.startedAt) / 1000
        } else 0
        binding.elapsedValue.text = formatElapsed(elapsed)

        // Keep buttons in sync with the service's actual state (either direction),
        // e.g. when the app is reopened while a run is already in progress.
        if (BurnStats.running && binding.startButton.isEnabled) {
            setRunningState(true)
        } else if (!BurnStats.running && binding.stopButton.isEnabled) {
            setRunningState(false)
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var i = 0
        while (value >= 1024.0 && i < units.size - 1) {
            value /= 1024.0
            i++
        }
        return String.format(Locale.US, "%.2f %s", value, units[i])
    }

    private fun formatElapsed(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 1000L
    }
}
