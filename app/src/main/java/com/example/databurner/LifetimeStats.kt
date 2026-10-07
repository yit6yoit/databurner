package com.example.databurner

import android.content.Context

/**
 * Persistent, all-time total of bytes burned, stored in SharedPreferences so it
 * survives app restarts. The service flushes the running session total into here
 * periodically and on stop.
 */
object LifetimeStats {
    private const val PREFS = "burn_prefs"
    private const val KEY_BYTES = "lifetime_bytes"

    fun get(context: Context): Long =
        prefs(context).getLong(KEY_BYTES, 0L)

    fun add(context: Context, bytes: Long) {
        if (bytes <= 0L) return
        val p = prefs(context)
        p.edit().putLong(KEY_BYTES, p.getLong(KEY_BYTES, 0L) + bytes).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().putLong(KEY_BYTES, 0L).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
