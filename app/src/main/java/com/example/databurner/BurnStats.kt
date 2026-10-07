package com.example.databurner

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared, process-global state that the download service writes to and the
 * UI reads from. Kept deliberately simple (atomics + volatiles) so the
 * Activity can poll it without binding to the service.
 */
object BurnStats {
    /** Total bytes downloaded (and discarded) since the last reset. */
    val totalBytes = AtomicLong(0)

    /** How many worker connections are currently transferring. */
    val activeConnections = AtomicInteger(0)

    /** True while the service is actively burning. */
    @Volatile
    var running: Boolean = false

    /** Timestamp (ms) the current run started, for elapsed-time display. */
    @Volatile
    var startedAt: Long = 0L

    fun reset() {
        totalBytes.set(0)
        activeConnections.set(0)
        startedAt = 0L
    }
}
