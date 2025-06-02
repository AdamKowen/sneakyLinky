// File: app/src/main/java/com/example/sneakylinky/service/urltesting.kt
package com.example.sneakylinky.service.urlanalyzer

import android.content.Context
import com.example.sneakylinky.data.AppDatabase

import com.example.sneakylinky.data.WhitelistEntry
import com.example.sneakylinky.data.BlacklistEntry
import com.example.sneakylinky.data.TrustedHost
import kotlinx.coroutines.runBlocking

/**
 * Enum to describe where a host was found (or not found) in local tables.
 */
enum class HostCheckResult {
    WHITELISTED,
    BLACKLISTED,
    CACHED_TRUSTED,
    CACHED_SUSPICIOUS,
    CACHED_BLACKLISTED,
    NOT_PRESENT
}

/**
 * Given a parsed CanonUrl, check in this order:
 *  1. Whitelist
 *  2. Blacklist
 *  3. Trusted‐hosts cache
 *
 * Returns one of HostCheckResult.
 */
suspend fun checkHostInLocalTables(
    context: Context,
    canon: CanonUrl
): HostCheckResult {
    // 1) We only care about the ASCII host
    val host = canon.hostAscii?.lowercase() ?: return HostCheckResult.NOT_PRESENT

    // 2) Grab all three DAOs
    val db = AppDatabase.getInstance(context)
    val whitelistDao = db.whitelistDao()
    val blacklistDao = db.blacklistDao()
    val trustedDao = db.trustedHostDao()

    // 3) Check whitelist first
    if (whitelistDao.isWhitelisted(host)) {
        return HostCheckResult.WHITELISTED
    }

    // 4) Then check blacklist
    if (blacklistDao.isBlacklisted(host)) {
        return HostCheckResult.BLACKLISTED
    }

    // 5) Finally check trusted‐hosts cache
    //    getStatus(...) returns Int? (1=trusted, 2=suspicious, 3=blacklisted), or null if not in table
    val status = trustedDao.getStatus(host)
    if (status == 1) {
        return HostCheckResult.CACHED_TRUSTED
    } else if (status == 2) {
        return HostCheckResult.CACHED_SUSPICIOUS
    } else if (status == 3) {
        return HostCheckResult.CACHED_BLACKLISTED
    }

    // 6) If we reach here, the host is in none of the tables
    return HostCheckResult.NOT_PRESENT
}


fun populateTestData(context: Context) {
    runBlocking {
        val db = AppDatabase.getInstance(context)
        val wlDao = db.whitelistDao()
        val blDao = db.blacklistDao()
        val tDao  = db.trustedHostDao()

        // Clear any existing data (so you can re-run without duplicates)
        wlDao.clearAll()
        blDao.clearAll()
        tDao.deleteOlderThan(System.currentTimeMillis() + 1)
        // (deleteOlderThan now-1 always deletes nothing, but there is no "clearAll" for trusted table.
        // You can also add a `@Query("DELETE FROM trusted_hosts") fun clearTrusted()` if you want.)

        // 1) Insert a whitelist host
        wlDao.insert(WhitelistEntry("example.com"))
        wlDao.insert(WhitelistEntry("google.com"))
        wlDao.insert(WhitelistEntry("facebook.com"))
        wlDao.insert(WhitelistEntry("openai.com"))

        // 2) Insert a blacklist host
        blDao.insert(BlacklistEntry("bad.com"))
        blDao.insert(BlacklistEntry("phishing.org"))

        // 3) Insert one “trusted” and one “suspicious” host
        val now = System.currentTimeMillis()
        tDao.upsert(TrustedHost("trusted.example", now, status = 1))      // status=1 => trusted
        tDao.upsert(TrustedHost("suspicious.example", now, status = 2))   // status=2 => suspicious
    }
}