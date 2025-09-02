// File: app/src/main/java/com/example/sneakylinky/service/urlanalyzer/UrlUtils.kt
package com.example.sneakylinky.service.urlanalyzer

import android.util.Log
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.data.AppDatabase
import com.example.sneakylinky.data.BlacklistEntry
import com.example.sneakylinky.data.WhitelistEntry
import kotlinx.coroutines.runBlocking

private const val TAG = "UrlUtils"

/* ──────────────────────────────────────────────────────────────────────────────
   Local list status (no cache table).
   Used to short-circuit before heuristics.
   ──────────────────────────────────────────────────────────────────────────── */
enum class ListCheckResult {
    WHITELISTED,
    BLACKLISTED,
    NOT_PRESENT
}

/* ──────────────────────────────────────────────────────────────────────────────
   Decision envelope for the full local flow.
   - verdict: SAFE or BLOCK
   - source : where the decision came from
   - reasons/score: only populated for HEURISTICS decisions (when blocked)
   - canon  : returned for convenience to downstream consumers
   ──────────────────────────────────────────────────────────────────────────── */
enum class Verdict { SAFE, BLOCK }
enum class DecisionSource { CANON_PARSE_ERROR, WHITELIST, BLACKLIST, HEURISTICS }

data class UrlEvaluation(
    val verdict: Verdict,
    val source: DecisionSource,
    val reasonDetails: List<ReasonDetail> = emptyList(),
    val score: Double = 0.0,
    val canon: CanonUrl? = null
)

/* ──────────────────────────────────────────────────────────────────────────────
   Look up host in local Room lists (whitelist/blacklist).
   Purely local; returns a coarse status (no network).
   ──────────────────────────────────────────────────────────────────────────── */
suspend fun checkLocalLists(canon: CanonUrl): ListCheckResult {
    val host = canon.hostAscii?.lowercase() ?: return ListCheckResult.NOT_PRESENT
    val db = AppDatabase.getInstance(SneakyLinkyApp.appContext())
    val whitelistDao = db.whitelistDao()
    val blacklistDao = db.blacklistDao()

    if (whitelistDao.isWhitelisted(host)) return ListCheckResult.WHITELISTED
    if (blacklistDao.isBlacklisted(host)) return ListCheckResult.BLACKLISTED
    return ListCheckResult.NOT_PRESENT
}

/* ──────────────────────────────────────────────────────────────────────────────
   Single-entry local decision:
   1) Parse (fail-closed → BLOCK if parse fails).
   2) Check whitelist/blacklist.
   3) Else run all heuristics (analyzeAndDecide).

   Notes:
   - We return SAFE for whitelist hits, BLOCK for blacklist.
   - Heuristics always run all checks internally and combine.
   - Logs are compact (≤~80 chars).
   ──────────────────────────────────────────────────────────────────────────── */
suspend fun evaluateUrl(raw: String): UrlEvaluation {
    val tag = "UrlDecision"
    val canon = raw.toCanonUrlOrNull()
    if (canon == null) {
        Log.d(tag, "parse: fail → block (PARSE_ERROR)")
        return UrlEvaluation(
            verdict = Verdict.BLOCK,
            source = DecisionSource.CANON_PARSE_ERROR
        )
    }

    when (checkLocalLists(canon)) {
        ListCheckResult.WHITELISTED -> {
            Log.d(tag, "lists: whitelist → safe")
            return UrlEvaluation(
                verdict = Verdict.SAFE,
                source = DecisionSource.WHITELIST,
                canon = canon
            )
        }
        ListCheckResult.BLACKLISTED -> {
            Log.d(tag, "lists: blacklist → block")
            return UrlEvaluation(
                verdict = Verdict.BLOCK,
                source = DecisionSource.BLACKLIST,
                canon = canon
            )
        }
        ListCheckResult.NOT_PRESENT -> {
            // fall through to heuristics
        }
    }

    val h = runLocalHeuristicsAndDecide(canon)
    if (h.blocked) {
        Log.d(tag, "heur: block soft=${"%.2f".format(h.totalScore)} reasons=${h.reasonDetails}")
    } else {
        Log.d(tag, "heur: safe soft=${"%.2f".format(h.totalScore)}")
    }
    return UrlEvaluation(
        verdict = if (h.blocked) Verdict.BLOCK else Verdict.SAFE,
        source = DecisionSource.HEURISTICS,
        reasonDetails = if (h.blocked) h.reasonDetails else emptyList(), // ← pass messages
        score = h.totalScore,
        canon = canon
    )
}

/* ──────────────────────────────────────────────────────────────────────────────
   Dev helper to seed local lists for manual testing.
   DO NOT ship in production builds.
   ──────────────────────────────────────────────────────────────────────────── */
fun populateTestData() {
    runBlocking {
        val db = AppDatabase.getInstance(SneakyLinkyApp.appContext())
        val wlDao = db.whitelistDao()
        val blDao = db.blacklistDao()

        // Clear any existing data (so you can re-run without duplicates)
        wlDao.clearAll()
        blDao.clearAll()

        // Seed a (truncated) whitelist. Expand as you like.
        val entries = listOf(
            "bankhapoalim.co.il/","ynetnews.com",
            "adobe.com","aliexpress.com","amazon.co.jp","amazon.co.uk","amazon.com",
            "amazon.de","amazon.in","apple.com","baidu.com","bbc.co.uk","bbc.com",
            "bilibili.com","bing.com","booking.com","canva.com","chatgpt.com",
            "clevelandclinic.org","cnn.com","discord.com","duckduckgo.com",
            "ebay.com","espn.com","facebook.com","github.com","google.com",
            "google.co.uk","imdb.com","instagram.com","linkedin.com","live.com",
            "microsoft.com","microsoftonline.com","netflix.com","nytimes.com",
            "office.com","openai.com","paypal.com","pinterest.com","reddit.com",
            "spotify.com","telegram.org","tiktok.com","twitch.tv","twitter.com",
            "walmart.com","whatsapp.com","wikipedia.org","x.com","yandex.ru",
            "youtube.com","zoom.us","m.facebook.com"
        ).map { WhitelistEntry(it) }
        wlDao.insertAll(entries)

        // Blacklist samples
        blDao.insert(BlacklistEntry("bad.com"))
        blDao.insert(BlacklistEntry("phishing.org"))

        Log.d(TAG, "populateTestData: seeded whitelist/blacklist")
    }
}
