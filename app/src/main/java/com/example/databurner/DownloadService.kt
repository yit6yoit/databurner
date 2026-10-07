package com.example.databurner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service that keeps N parallel HTTP connections saturated,
 * reading each response body as fast as possible and discarding the bytes.
 * Runs with a partial wake lock so it continues with the screen off.
 */
class DownloadService : Service() {

    @Volatile private var running = false
    private var executor: ExecutorService? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Lifetime-total bookkeeping: how much of the session total we've already
    // folded into the persistent all-time counter.
    private var lastFlushed = 0L
    private var flusher: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBurning()
                return START_NOT_STICKY
            }
            else -> {
                val connections = intent?.getIntExtra(EXTRA_CONNECTIONS, DEFAULT_CONNECTIONS)
                    ?: DEFAULT_CONNECTIONS
                startBurning(connections)
            }
        }
        return START_STICKY
    }

    private fun startBurning(connections: Int) {
        if (running) return
        running = true

        BurnStats.reset()
        BurnStats.running = true
        BurnStats.startedAt = System.currentTimeMillis()

        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()

        lastFlushed = 0L
        startLifetimeFlusher()

        val pool = Executors.newFixedThreadPool(connections)
        executor = pool
        repeat(connections) { workerIndex ->
            pool.execute { workerLoop(workerIndex) }
        }
    }

    private fun stopBurning() {
        running = false
        BurnStats.running = false
        executor?.shutdownNow()
        executor = null
        flusher?.interrupt()
        flusher = null
        flushLifetime() // fold in whatever was downloaded since the last flush
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Periodically persist the session delta into the all-time total. */
    private fun startLifetimeFlusher() {
        flusher = Thread {
            while (running) {
                try {
                    Thread.sleep(FLUSH_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    break
                }
                flushLifetime()
            }
        }.also { it.start() }
    }

    @Synchronized
    private fun flushLifetime() {
        val current = BurnStats.totalBytes.get()
        val delta = current - lastFlushed
        if (delta > 0) {
            LifetimeStats.add(applicationContext, delta)
            lastFlushed = current
        }
    }

    /**
     * One worker: repeatedly open a connection to a test server and stream the
     * body into a throwaway buffer, counting the bytes. When a body ends (or
     * errors) it simply starts another transfer, so the pipe stays full.
     */
    private fun workerLoop(workerIndex: Int) {
        val buffer = ByteArray(BUFFER_SIZE)
        var attempt = workerIndex
        while (running && !Thread.currentThread().isInterrupted) {
            val urlString = TestServers.forWorker(attempt++)
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    useCaches = false
                    setRequestProperty("Connection", "keep-alive")
                    setRequestProperty("User-Agent", "DataBurner/1.0")
                }
                connection.connect()

                BurnStats.activeConnections.incrementAndGet()
                try {
                    connection.inputStream.use { input ->
                        while (running) {
                            val read = input.read(buffer)
                            if (read < 0) break // body finished — loop re-requests
                            BurnStats.totalBytes.addAndGet(read.toLong())
                        }
                    }
                } finally {
                    BurnStats.activeConnections.decrementAndGet()
                }
            } catch (ie: InterruptedException) {
                break
            } catch (t: Throwable) {
                // Network hiccup / server refused — back off briefly then retry.
                if (!running) break
                try {
                    Thread.sleep(RETRY_DELAY_MS)
                } catch (e: InterruptedException) {
                    break
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    // ---- notification --------------------------------------------------

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Data Burner",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Active download session" }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Data Burner running")
            .setContentText("Downloading from public test servers…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ---- wake lock -----------------------------------------------------

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DataBurner::wakelock").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKELOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        stopBurning()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.databurner.START"
        const val ACTION_STOP = "com.example.databurner.STOP"
        const val EXTRA_CONNECTIONS = "connections"

        const val DEFAULT_CONNECTIONS = 16
        private const val BUFFER_SIZE = 64 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val RETRY_DELAY_MS = 500L
        private const val FLUSH_INTERVAL_MS = 2_000L
        private const val MAX_WAKELOCK_MS = 12L * 60L * 60L * 1000L // 12h safety cap

        private const val CHANNEL_ID = "data_burner"
        private const val NOTIFICATION_ID = 1
    }
}
