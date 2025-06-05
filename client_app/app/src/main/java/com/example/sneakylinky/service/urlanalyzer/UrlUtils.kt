package com.example.sneakylinky.service.urlanalyzer

import android.util.Log
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.data.AppDatabase
import com.example.sneakylinky.data.BlacklistEntry
import com.example.sneakylinky.data.CachedHostEntry
import com.example.sneakylinky.data.WhitelistEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


enum class HostCheckResult {
    WHITELISTED,
    BLACKLISTED,
    CACHED_TRUSTED,
    CACHED_SUSPICIOUS,
    CACHED_BLACKLISTED,
    NOT_PRESENT
}


suspend fun checkHostInLocalTables(canon: CanonUrl): HostCheckResult {
    val host = canon.hostAscii?.lowercase() ?: return HostCheckResult.NOT_PRESENT

    // 2) Grab all three DAOs
    val db = AppDatabase.getInstance(SneakyLinkyApp.appContext())
    val whitelistDao = db.whitelistDao()
    val blacklistDao = db.blacklistDao()
    val trustedDao = db.hostCacheDao()

    if (whitelistDao.isWhitelisted(host))
        return HostCheckResult.WHITELISTED

    if (blacklistDao.isBlacklisted(host))
        return HostCheckResult.BLACKLISTED

    //    getStatus(...) returns Int? (1=trusted, 2=suspicious, 3=blacklisted), or null if not in table
    val status = trustedDao.getStatus(host)
    return when (status) {
        1 -> HostCheckResult.CACHED_TRUSTED
        2 -> HostCheckResult.CACHED_SUSPICIOUS
        3 -> HostCheckResult.CACHED_BLACKLISTED
        else -> HostCheckResult.NOT_PRESENT
    }
}

suspend fun CanonUrl.checkLocalTables(): HostCheckResult {
    return checkHostInLocalTables(this)
}

suspend fun isUrlPassStaticChecks(canon: CanonUrl): Boolean {
    if (isSuspiciousByIp(canon)) {
        Log.d("UrlUtils", "isStaticUrlSafe → failed IP‐literal check")
        return false
    }
    val isTooClose = withContext(Dispatchers.IO) {
        isSuspiciousByNormalizedDistance(canon)
    }
    if (isTooClose) {
        Log.d("UrlUtils", "isStaticUrlSafe → failed near‐whitelist check")
        return false
    }

    // TODO : insert more static checks here as needed
    Log.d("UrlUtils", "isStaticUrlSafe → all checks passed")
    return true
}

suspend fun CanonUrl.passesStaticChecks(): Boolean {
    return isUrlPassStaticChecks(this)
}

suspend fun isUrlLocalSafe(canon: CanonUrl): Boolean {
    // 1) Look up in local tables
    when (checkHostInLocalTables(canon)) {
        HostCheckResult.WHITELISTED,
        HostCheckResult.CACHED_TRUSTED -> {
            return true
        }

        HostCheckResult.BLACKLISTED,
        HostCheckResult.CACHED_SUSPICIOUS,
        HostCheckResult.CACHED_BLACKLISTED -> {
            return false
        }

        HostCheckResult.NOT_PRESENT -> {
            // deliberately empty - allows to fall through to static checks
        }
    }

    // 2) Not in any table → run static checks
    return isUrlPassStaticChecks(canon)
}

suspend fun CanonUrl.isLocalSafe(): Boolean {
    return isUrlLocalSafe(this)
}

// TODO : remove this function in production
fun populateTestData() {
    runBlocking {
        val db = AppDatabase.getInstance(SneakyLinkyApp.appContext())
        val wlDao = db.whitelistDao()
        val blDao = db.blacklistDao()
        val tDao = db.hostCacheDao()

        // Clear any existing data (so you can re-run without duplicates)
        wlDao.clearAll()
        blDao.clearAll()
        tDao.deleteOlderThan(System.currentTimeMillis() + 1)

        // 1) Insert a whitelist host
        val entries = listOf(
            "adobe.com",
            "aliexpress.com",
            "amazon.co.jp",
            "amazon.co.uk",
            "amazon.com",
            "amazon.de",
            "amazon.in",
            "apple.com",
            "baidu.com",
            "bbc.co.uk",
            "bbc.com",
            "bet.br",
            "bilibili.com",
            "bing.com",
            "booking.com",
            "canva.com",
            "chatgpt.com",
            "clevelandclinic.org",
            "cnn.com",
            "cricbuzz.com",
            "dailymotion.com",
            "detik.com",
            "discord.com",
            "duckduckgo.com",
            "dzen.ru",
            "ebay.com",
            "espn.com",
            "espncricinfo.com",
            "facebook.com",
            "fandom.com",
            "github.com",
            "globo.com",
            "google.co.uk",
            "google.com",
            "google.com.br",
            "hindustantimes.com",
            "ilovepdf.com",
            "imdb.com",
            "indeed.com",
            "instagram.com",
            "iplt20.com",
            "linkedin.com",
            "live.com",
            "mail.ru",
            "mayoclinic.org",
            "microsoft.com",
            "microsoftonline.com",
            "msn.com",
            "naver.com",
            "netflix.com",
            "news.yahoo.co.jp",
            "nytimes.com",
            "office.com",
            "openai.com",
            "paypal.com",
            "pinterest.com",
            "pornhub.com",
            "quora.com",
            "rakuten.co.jp",
            "reddit.com",
            "roblox.com",
            "samsung.com",
            "sharepoint.com",
            "spotify.com",
            "stripchat.com",
            "t.me",
            "telegram.org",
            "temu.com",
            "tiktok.com",
            "twitch.tv",
            "twitter.com",
            "uol.com.br",
            "usps.com",
            "vk.com",
            "walmart.com",
            "weather.com",
            "whatsapp.com",
            "wikipedia.org",
            "x.com",
            "xhamster.com",
            "xnxx.com",
            "xvideos.com",
            "yahoo.co.jp",
            "yahoo.com",
            "yandex.ru",
            "youtube.com",
            "zoom.us",
            "m.facebook.com"
        ).map { WhitelistEntry(it) }
        wlDao.insertAll(entries)

        // 2) Insert a blacklist host
        blDao.insert(BlacklistEntry("bad.com"))
        blDao.insert(BlacklistEntry("phishing.org"))

        // 3) Insert one “trusted” and one “suspicious” host
        val now = System.currentTimeMillis()
        tDao.upsert(CachedHostEntry("trusted.example", now, status = 1))      // status=1 => trusted
        tDao.upsert(
            CachedHostEntry(
                "suspicious.example",
                now,
                status = 2
            )
        )   // status=2 => suspicious
        tDao.upsert(
            CachedHostEntry(
                "phishing.com",
                now,
                status = 3
            )
        )         // status=3 => phishing/blacklisted
    }
}
