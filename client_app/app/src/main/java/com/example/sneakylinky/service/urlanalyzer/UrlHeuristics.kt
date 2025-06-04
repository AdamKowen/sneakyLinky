// File: app/src/main/java/com/example/sneakylinky/service/urltesting.kt
package com.example.sneakylinky.service.urlanalyzer

import android.util.Log
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.data.AppDatabase


fun isSuspiciousByIp(canon: CanonUrl): Boolean {
    val host = canon.hostAscii?.lowercase() ?: return false
    if (host.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}"""))) {    // IPv4 (e.g., “192.168.0.1”)
        Log.d("UrlUtils", "suspicious—IPv4 literal host: $host")
        return true
    }
    if (host.contains(':')) {     // IPv6 (contains “:”)
        Log.d("UrlUtils", "suspicious—IPv6 literal host: $host")
        return true
    }
    return false
}

private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j

    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,       // deletion
                dp[i][j - 1] + 1,       // insertion
                dp[i - 1][j - 1] + cost // substitution
            )
        }
    }
    return dp[a.length][b.length]
}

// Compute normalized edit distance (0.0–1.0)
private fun normalizedLevenshtein(a: String, b: String): Double {
    val dist = levenshtein(a, b)
    val maxLen = maxOf(a.length, b.length).coerceAtLeast(1)
    return dist.toDouble() / maxLen
}

// Step 2 (English): Check if host is “too close” to any whitelist entry by normalized distance
private const val NORMALIZED_DISTANCE_THRESHOLD = 0.2

// Step 2: Check if host is “too close” to any whitelist entry by normalized distance
suspend fun isSuspiciousByNormalizedDistance(canon: CanonUrl): Boolean {
    val host = canon.hostAscii?.lowercase() ?: return false

    // 1) Grab WhitelistDao using the Application singleton
    val db = AppDatabase.getInstance(SneakyLinkyApp.appContext())
    val whitelistDao = db.whitelistDao()

    // 2) Fetch all whitelist entries
    val whitelistHosts = whitelistDao.getAll().map { it.hostAscii.lowercase() }

    // 3) Compare normalized Levenshtein ratio against each
    for (w in whitelistHosts) {
        val ratio = normalizedLevenshtein(host, w)
        if (ratio > 0.0 && ratio <= NORMALIZED_DISTANCE_THRESHOLD) {
            Log.d(
                "UrlUtils",
                "isSuspiciousByNormalizedDistance → suspicious—near-whitelist host: $host (matches '$w' with ratio $ratio)"
            )
            return true
        }
    }
    return false
}
