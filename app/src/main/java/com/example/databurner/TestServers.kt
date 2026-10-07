package com.example.databurner

/**
 * Public download endpoints that are explicitly intended for bandwidth / speed
 * testing. Each returns a large payload we read and throw away.
 *
 * Only point this app at servers that are meant to be load-tested. These three
 * are run by their owners for exactly this purpose.
 */
object TestServers {

    /**
     * URLs that reliably return a large body. Workers round-robin across them
     * and re-request when a transfer finishes, so the download never stops.
     *
     * Cloudflare lets us ask for an arbitrary byte count via __down?bytes=N.
     */
    val urls: List<String> = listOf(
        // Cloudflare speed endpoint — 1 GiB per request.
        "https://speed.cloudflare.com/__down?bytes=1073741824",
        // Hetzner public test files.
        "https://speed.hetzner.de/1GB.bin",
        "https://ash-speed.hetzner.com/1GB.bin",
        // ThinkBroadband (HTTP only — cleartext is allowed in the manifest).
        "http://ipv4.download.thinkbroadband.com/1GB.zip",
        "http://ipv4.download.thinkbroadband.com/512MB.zip"
    )

    /** Pick a URL for a given worker index, spreading load across servers. */
    fun forWorker(index: Int): String = urls[index % urls.size]
}
