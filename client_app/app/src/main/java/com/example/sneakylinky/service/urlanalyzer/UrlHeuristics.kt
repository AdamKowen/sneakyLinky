// File: app/src/main/java/com/example/sneakylinky/service/UrlHeuristicsCore.kt
package com.example.sneakylinky.service.urlanalyzer

import android.util.Log
import kotlin.math.max
import kotlin.math.min

/* ──────────────────────────────────────────────────────────────────────────────
   Public surface
   - Reason: enum of reasons that can be reported.
   - BlockResult: SAFE/BLOCK decision, overall soft score, present reasons list.
   - Heuristics take only [canon]; test-specific tuning sits atop each section.
   ──────────────────────────────────────────────────────────────────────────── */
enum class Reason {
    // Booleans — CRITICAL (any one triggers a hard block)
    IP_HOST,
    MIXED_SCRIPT,
    USERINFO_PRESENT,
    UNFAMILIAR_TLD,

    // Booleans — NON-CRITICAL (soft contributors)
    PORT_SCHEME_MISMATCH,
    ENCODED_PARTS,

    // Numeric (0..1 soft contributors)
    NEAR_WHITELIST_LOOKALIKE,
    LONG_URL,
    TOO_MANY_SUBDOMAINS,
    PHISH_KEYWORDS,
}

data class ReasonDetail(
    val reason: Reason,
    val message: String
)

data class LocalHeuristicsDecision(
    val blocked: Boolean,
    val totalScore: Double,          // soft score in [0..1]; set to 1.0 when hard-blocked
    val reasonDetails: List<ReasonDetail> // NEW: end-user facing messages, ordered
)

/* ──────────────────────────────────────────────────────────────────────────────
   General tuning (aggregation & shared data)
   - Critical reasons never use weights: they hard-block by policy.
   - Soft reasons are combined via soft-OR: total = 1 - Π(1 - weight*score).
   ──────────────────────────────────────────────────────────────────────────── */
private const val TAG = "UrlHeuristics"
private const val BLOCK_THRESHOLD = 0.60

private val CRITICAL_REASONS = setOf(
    Reason.IP_HOST,
    Reason.MIXED_SCRIPT,
    Reason.USERINFO_PRESENT,
    Reason.UNFAMILIAR_TLD
)

// Weights for SOFT reasons only (0..1)
private val SOFT_WEIGHTS = mapOf(
    Reason.PORT_SCHEME_MISMATCH to 0.50,
    Reason.ENCODED_PARTS to 0.40,
    Reason.NEAR_WHITELIST_LOOKALIKE to 0.85,
    Reason.LONG_URL to 0.45,
    Reason.TOO_MANY_SUBDOMAINS to 0.55,
    Reason.PHISH_KEYWORDS to 0.65,
)

// Familiar public suffixes (compact list; extend in data layer if desired)
private val FAMILIAR_TLDS = setOf(
    "com","org","net","edu","gov","mil","io","co","me","ai","dev","app",
    "il","co.il","org.il","gov.il","ac.il","muni.il",
    "uk","co.uk","de","fr","es","it","nl","pl","se"
)

// Short reason tags for compact logs (≤80 chars)
private fun tag(r: Reason) = when (r) {
    Reason.IP_HOST -> "ip"
    Reason.MIXED_SCRIPT -> "mix"
    Reason.USERINFO_PRESENT -> "ui"
    Reason.UNFAMILIAR_TLD -> "tld"
    Reason.PORT_SCHEME_MISMATCH -> "port"
    Reason.ENCODED_PARTS -> "enc"
    Reason.NEAR_WHITELIST_LOOKALIKE -> "med"
    Reason.LONG_URL -> "len"
    Reason.TOO_MANY_SUBDOMAINS -> "subd"
    Reason.PHISH_KEYWORDS -> "kw"
}

private fun clamp01(x: Double) = max(0.0, min(1.0, x))

/* =============================================================================
   BOOLEAN HEURISTICS (critical first, then non-critical)
   ============================================================================= */

/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: IP host  (Boolean) — CRITICAL
   Tuning: none
   Log: "ip: true"
   ──────────────────────────────────────────────────────────────────────────── */
fun hIpHost(canon: CanonUrl): Boolean {
    val host = canon.hostAscii?.lowercase() ?: return false
    val isV4 = host.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}"""))
    val isV6 = !isV4 && host.contains(':') // IPv6 unbracketed in CanonUrl
    val hit = isV4 || isV6
    if (hit) Log.d(TAG, "ip: true")
    return hit
}

/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: Mixed-script hostname  (Boolean) — CRITICAL
   Tuning: none (use CanonUrl.isMixedScript)
   Log: "mix: true"
   ──────────────────────────────────────────────────────────────────────────── */
fun hMixedScript(canon: CanonUrl): Boolean {
    val hit = canon.isMixedScript
    if (hit) Log.d(TAG, "mix: true")
    return hit
}

/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: Userinfo present  (Boolean) — CRITICAL
   Tuning: none
   Log: "ui: present"
   ──────────────────────────────────────────────────────────────────────────── */
fun hUserInfo(canon: CanonUrl): Boolean {
    val hit = canon.userInfo != null
    if (hit) Log.d(TAG, "ui: present")
    return hit
}

/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: Unfamiliar TLD  (Boolean) — CRITICAL
   Tuning: none (familiar list below)
   Log: "tld: unfamiliar"
   ──────────────────────────────────────────────────────────────────────────── */
fun hUnfamiliarTld(canon: CanonUrl): Boolean {
    val tld = canon.tld?.lowercase() ?: return false // IP/unknown suffix → ignore
    val hit = tld !in FAMILIAR_TLDS
    if (hit) Log.d(TAG, "tld: unfamiliar")
    return hit
}

/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: Port/scheme mismatch  (Boolean) — NON-CRITICAL
   Tuning (this section):
     - HTTP_ALLOWED_PORTS / HTTPS_ALLOWED_PORTS
     - PORT_BOOL_SCORE: contribution when true (0..1)
   Log: "port: mismatch http:8080" or "port: mismatch https:8080"
   ──────────────────────────────────────────────────────────────────────────── */
private val HTTP_ALLOWED_PORTS = setOf(80)
private val HTTPS_ALLOWED_PORTS = setOf(443)
//private const val PORT_BOOL_SCORE = 0.60

fun hPortSchemeMismatch(canon: CanonUrl): Boolean {
    val p = canon.port ?: return false
    val ok = when (canon.scheme) {
        "http" -> p in HTTP_ALLOWED_PORTS
        "https" -> p in HTTPS_ALLOWED_PORTS
        else -> false
    }
    val hit = !ok
    if (hit) Log.d(TAG, "port: mismatch ${canon.scheme}:$p")
    return hit
}

/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: Encoded parts present  (Boolean) — NON-CRITICAL
   Tuning (this section):
     - ENC_BOOL_SCORE: contribution when true (0..1)
   Log: "enc: present"
   ──────────────────────────────────────────────────────────────────────────── */

fun hEncodedParts(canon: CanonUrl): Boolean {
    val hit = canon.hasEncodedParts
    if (hit) Log.d(TAG, "enc: present")
    return hit
}

/* =============================================================================
   NUMERIC HEURISTICS (0..1). Baseline → 0, Critical → high score.
   Each has a SUSPICIOUS threshold and a CRITICAL threshold.
   ============================================================================= */

/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: MED vs whitelist  (Numeric 0..1)
   Tuning (this section):
     - MED_SUS_MAX_RATIO  = 0.20   // r ≤ this → suspicious
     - MED_CRIT_MAX_RATIO = 0.10   // r ≤ this → critical
     - MED_SCORE_AT_SUS   = 0.60   // score at r = SUS
     - MED_SCORE_AT_CRIT  = 1.00   // score at r = CRIT
   Scoring (lower ratio = worse), BUT exact match (r==0) → safe (score 0).
   Log (if >0): "med: near=example.com r=0.09 s=1.00"
   Log (if exact): "med: exact=example.com s=0.00"

   Added:
     - data class MedResult(score, nearestDomain, ratio)
     - hMedNearWhitelistInfo(...) → MedResult
     - hMedNearWhitelist(...)     → Double (back-compat wrapper)
   ──────────────────────────────────────────────────────────────────────────── */
private const val MED_SUS_MAX_RATIO = 0.20
private const val MED_CRIT_MAX_RATIO = 0.10
private const val MED_SCORE_AT_SUS = 0.60
private const val MED_SCORE_AT_CRIT = 1.00

data class MedResult(
    val score: Double,             // 0..1 (same as before)
    val nearestDomain: String?,    // best whitelist match, if any
    val ratio: Double?             // normalized Levenshtein ratio (0..1, lower=worse)
)

suspend fun hMedNearWhitelistInfo(canon: CanonUrl): MedResult {
    val host = canon.hostAscii?.lowercase() ?: return MedResult(0.0, null, null)
    val whitelist = WhitelistSource.loader.invoke()
    if (whitelist.isEmpty()) return MedResult(0.0, null, null)

    var best = Double.POSITIVE_INFINITY
    var nearest: String? = null
    for (w in whitelist) {
        val r = medNormalizedLevenshtein(host, w)
        if (r < best) { best = r; nearest = w }
    }

    // Exact whitelist match → not suspicious (score 0), log and return
    if (best <= 0.0 + 1e-12) {
        nearest?.let { Log.d(TAG, "med: exact=$it s=0.00") }
        return MedResult(score = 0.0, nearestDomain = nearest, ratio = 0.0)
    }

    val score = when {
        best > MED_SUS_MAX_RATIO -> 0.0
        best <= MED_CRIT_MAX_RATIO -> MED_SCORE_AT_CRIT
        else -> {
            val span = (MED_SUS_MAX_RATIO - MED_CRIT_MAX_RATIO).coerceAtLeast(1e-9)
            val t = (MED_SUS_MAX_RATIO - best) / span // 0..1 (closer→bigger)
            MED_SCORE_AT_SUS + t * (MED_SCORE_AT_CRIT - MED_SCORE_AT_SUS)
        }
    }.coerceIn(0.0, 1.0)

    if (score > 0.0) {
        Log.d(TAG, "med: near=${nearest ?: "-"} r=${"%.2f".format(best)} s=${"%.2f".format(score)}")
    }
    return MedResult(score = score, nearestDomain = nearest, ratio = best)
}

private fun medNormalizedLevenshtein(a: String, b: String): Double {
    fun lev(x: String, y: String): Int {
        val dp = Array(x.length + 1) { IntArray(y.length + 1) }
        for (i in 0..x.length) dp[i][0] = i
        for (j in 0..y.length) dp[0][j] = j
        for (i in 1..x.length) for (j in 1..y.length) {
            val cost = if (x[i - 1] == y[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
        return dp[x.length][y.length]
    }
    val dist = lev(a, b)
    val maxLen = max(a.length, b.length).coerceAtLeast(1)
    return dist.toDouble() / maxLen
}



/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: URL length  (Numeric 0..1)
   Tuning (this section):
     - LEN_SAFE_MAX       = 80     // ≤80 → not suspicious (score 0)
     - LEN_CRIT_MIN       = 300    // ≥300 → critical
     - LEN_SCORE_AT_SUS   = 0.25   // score just above safe
     - LEN_SCORE_AT_CRIT  = 1.00   // score at ≥ CRIT
   Scoring:
     - len ≤ SAFE_MAX → 0.0
     - len ≥ CRIT_MIN → CRIT score
     - else linear SAFE_MAX..CRIT_MIN → SUS..CRIT score
   Log: "len: 123 (>80 by +43) score=0.33"
   ──────────────────────────────────────────────────────────────────────────── */
private const val LEN_SAFE_MAX = 80
private const val LEN_CRIT_MIN = 300
private const val LEN_SCORE_AT_SUS = 0.25
private const val LEN_SCORE_AT_CRIT = 1.00

fun hUrlLength(canon: CanonUrl): Double {
    val len = canon.originalUrl.length
    val score = when {
        len <= LEN_SAFE_MAX -> 0.0
        len >= LEN_CRIT_MIN -> LEN_SCORE_AT_CRIT
        else -> {
            val span = (LEN_CRIT_MIN - LEN_SAFE_MAX).coerceAtLeast(1)
            val t = (len - LEN_SAFE_MAX).toDouble() / span // 0..1
            LEN_SCORE_AT_SUS + t * (LEN_SCORE_AT_CRIT - LEN_SCORE_AT_SUS)
        }
    }.let(::clamp01)

    if (score > 0.0) {
        val over = len - LEN_SAFE_MAX
        Log.d(TAG, "len: $len (>${LEN_SAFE_MAX} by +$over) score=${"%.2f".format(score)}")
    }
    return score
}

/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: Subdomain depth  (Numeric 0..1)
   Tuning (this section):
     - SUBD_SAFE_MAX      = 2      // ≤2 labels → not suspicious (score 0)
     - SUBD_CRIT_MIN      = 6      // ≥6 labels → critical
     - SUBD_SCORE_AT_SUS  = 0.30
     - SUBD_SCORE_AT_CRIT = 1.00
   Scoring: linear SAFE_MAX..CRIT_MIN → SUS..CRIT score
   Log: "subd: 4 (>2 by +2) score=0.50"
   ──────────────────────────────────────────────────────────────────────────── */
private const val SUBD_SAFE_MAX = 2
private const val SUBD_CRIT_MIN = 6
private const val SUBD_SCORE_AT_SUS = 0.30
private const val SUBD_SCORE_AT_CRIT = 1.00

fun hSubdomainDepth(canon: CanonUrl): Double {
    val count = canon.subdomain?.split('.')?.count { it.isNotEmpty() } ?: 0
    val score = when {
        count <= SUBD_SAFE_MAX -> 0.0
        count >= SUBD_CRIT_MIN -> SUBD_SCORE_AT_CRIT
        else -> {
            val span = (SUBD_CRIT_MIN - SUBD_SAFE_MAX).coerceAtLeast(1)
            val t = (count - SUBD_SAFE_MAX).toDouble() / span // 0..1
            SUBD_SCORE_AT_SUS + t * (SUBD_SCORE_AT_CRIT - SUBD_SCORE_AT_SUS)
        }
    }.let(::clamp01)

    if (score > 0.0) {
        val over = count - SUBD_SAFE_MAX
        Log.d(TAG, "subd: $count (>${SUBD_SAFE_MAX} by +$over) score=${"%.2f".format(score)}")
    }
    return score
}

/* ──────────────────────────────────────────────────────────────────────────────
   Heuristic: Phish keywords (unique hits)  (Numeric 0..1)
   Tuning (this section):
     - KEY_SUS_MIN        = 1      // ≥1 → suspicious
     - KEY_CRIT_MIN       = 6      // ≥6 → critical
     - KEY_SCORE_AT_SUS   = 0.30
     - KEY_SCORE_AT_CRIT  = 1.00
     - PHISH_KEYWORDS     = {...}
   Scoring: linear SUS_MIN..CRIT_MIN → SUS..CRIT score
   Log: "kw: 3 (≥1) score=0.58"

   Added:
     - data class PhishKeywordsResult(score, hitCount)
     - hPhishKeywordsInfo(...)   → PhishKeywordsResult
     - hPhishKeywords(...)       → Double (back-compat wrapper)

   Note on naming: picked PhishKeywordsResult (concise, plural matches set).
   If you prefer the explicit variant, rename to PhishKeywordCountResult.
   ──────────────────────────────────────────────────────────────────────────── */
private const val KEY_SUS_MIN = 1
private const val KEY_CRIT_MIN = 6
private const val KEY_SCORE_AT_SUS = 0.30
private const val KEY_SCORE_AT_CRIT = 1.00

private val PHISH_KEYWORDS = setOf(
    "login","verify","secure","update","account","wallet","support","billing",
    "reset","password","bank","pay","invoice","doc","drive","dropbox","free",
    "gift","promo","bonus","prize"
)

data class PhishKeywordsResult(
    val score: Double,   // 0..1
    val hitCount: Int    // number of matched keywords
)

fun hPhishKeywordsInfo(canon: CanonUrl): PhishKeywordsResult {
    val text = buildString {
        append(canon.hostUnicode.replace('.', ' '))
        append(' ')
        append(canon.path.replace('/', ' '))
        canon.query?.let { append(' '); append(it.replace('&', ' ')) }
    }.lowercase()

    val tokens = Regex("[a-z0-9]{3,}").findAll(text).map { it.value }.toSet()
    val hits = tokens.count { it in PHISH_KEYWORDS }

    val score = when {
        hits < KEY_SUS_MIN -> 0.0
        hits >= KEY_CRIT_MIN -> KEY_SCORE_AT_CRIT
        else -> {
            val span = (KEY_CRIT_MIN - KEY_SUS_MIN).coerceAtLeast(1)
            val t = (hits - KEY_SUS_MIN).toDouble() / span // 0..1
            KEY_SCORE_AT_SUS + t * (KEY_SCORE_AT_CRIT - KEY_SCORE_AT_SUS)
        }
    }.let(::clamp01)

    if (score > 0.0) {
        Log.d(TAG, "kw: $hits (≥${KEY_SUS_MIN}) score=${"%.2f".format(score)}")
    }
    return PhishKeywordsResult(score = score, hitCount = hits)
}

/* =============================================================================
   AGGREGATION (single entry point)
   - Run all tests (no short-circuit).
   - Critical booleans → hard block (do not enter soft score).
   - Soft booleans → fixed contributions (section constants).
   - Numeric tests → 0..1; below “suspicious” threshold they are 0 (ignored).
   - Return only Reason list (no per-test scores).
   ============================================================================= */
suspend fun runLocalHeuristicsAndDecide(canon: CanonUrl): LocalHeuristicsDecision {
    val present = linkedSetOf<Reason>()                       // ordered, unique
    val softContribs = mutableListOf<Pair<Reason, Double>>()  // non-critical only
    val details = mutableListOf<ReasonDetail>()               // user-facing messages

    // ---------- Booleans — CRITICAL ----------
    if (hIpHost(canon)) {
        present += Reason.IP_HOST
        details += ReasonDetail(Reason.IP_HOST, "Link uses an IP address instead of a domain.")
    }
    if (hMixedScript(canon)) {
        present += Reason.MIXED_SCRIPT
        details += ReasonDetail(Reason.MIXED_SCRIPT, "Domain mixes writing systems (potential lookalike).")
    }
    if (hUserInfo(canon)) {
        present += Reason.USERINFO_PRESENT
        details += ReasonDetail(Reason.USERINFO_PRESENT, "Link contains embedded username information.")
    }
    if (hUnfamiliarTld(canon)) {
        present += Reason.UNFAMILIAR_TLD
        val tld = canon.tld ?: "unknown"
        details += ReasonDetail(Reason.UNFAMILIAR_TLD, "Unfamiliar domain ending: **$tld**.")
    }

    // ---------- Booleans — NON-CRITICAL ----------
    if (hPortSchemeMismatch(canon)) {
        present += Reason.PORT_SCHEME_MISMATCH
        softContribs += Reason.PORT_SCHEME_MISMATCH to 1.0 // weighted later

        val scheme = canon.scheme.lowercase()
        val port = canon.port ?: -1
        val expected = when (scheme) {
            "https" -> 443
            "http" -> 80
            else -> null
        }
        val humanScheme = scheme.uppercase()
        val msg = if (expected != null)
            "$humanScheme uses a non-default port: **$port** (expected **$expected**)."
        else
            "Uses a non-default network port: **$port**."
        details += ReasonDetail(Reason.PORT_SCHEME_MISMATCH, msg)
    }

    if (hEncodedParts(canon)) {
        present += Reason.ENCODED_PARTS
        softContribs += Reason.ENCODED_PARTS to 1.0
        details += ReasonDetail(Reason.ENCODED_PARTS, "Link contains encoded characters.")
    }

    // ---------- Numeric — with messages ----------
    // MED (near-whitelist lookalike): use info variant to get nearestDomain
    val medInfo = hMedNearWhitelistInfo(canon)
    if (medInfo.score > 0.0) {
        present += Reason.NEAR_WHITELIST_LOOKALIKE
        softContribs += Reason.NEAR_WHITELIST_LOOKALIKE to medInfo.score
        val msg = medInfo.nearestDomain?.let { "Not **$it** — just looks similar." }
            ?: "Domain looks similar to a well-known site."
        details += ReasonDetail(Reason.NEAR_WHITELIST_LOOKALIKE, msg)
    }

    // URL length
    val lenScore = hUrlLength(canon)
    if (lenScore > 0.0) {
        present += Reason.LONG_URL
        softContribs += Reason.LONG_URL to lenScore
        val length = canon.originalUrl.length
        details += ReasonDetail(Reason.LONG_URL, "Very long link (**$length** characters).")
    }

    // Subdomain depth
    val subdScore = hSubdomainDepth(canon)
    if (subdScore > 0.0) {
        present += Reason.TOO_MANY_SUBDOMAINS
        softContribs += Reason.TOO_MANY_SUBDOMAINS to subdScore
        val count = canon.subdomain?.split('.')?.count { it.isNotEmpty() } ?: 0
        details += ReasonDetail(Reason.TOO_MANY_SUBDOMAINS, "Unusually deep subdomain chain (**$count** levels).")
    }

    // Phishing keywords: use info variant to get hitCount
    val kwInfo = hPhishKeywordsInfo(canon)
    if (kwInfo.score > 0.0) {
        present += Reason.PHISH_KEYWORDS
        softContribs += Reason.PHISH_KEYWORDS to kwInfo.score
        details += ReasonDetail(
            Reason.PHISH_KEYWORDS,
            "Contains **${kwInfo.hitCount}** words commonly used in phishing attempts."
        )
    }

    // ---------- Hard rule on critical (still compute soft score for logging/telemetry) ----------
    val hasCritical = present.any { it in CRITICAL_REASONS }

    // Weighted soft-OR merge for non-critical reasons only
    var keep = 1.0
    for ((reason, raw) in softContribs) {
        val w = SOFT_WEIGHTS[reason] ?: 0.0
        val contrib = clamp01(w * raw)
        keep *= (1.0 - contrib)
    }
    val softTotal = clamp01(1.0 - keep)

    val blocked = hasCritical || (softTotal >= BLOCK_THRESHOLD)

    // ---------- Compact final log (unchanged) ----------
    val critTags = present.filter { it in CRITICAL_REASONS }.joinToString(",") { tag(it) }
    val softTags = present.filter { it !in CRITICAL_REASONS }.joinToString(",") { tag(it) }
    val msg = buildList {
        add("soft=${"%.2f".format(softTotal)}")
        add("crit=${if (hasCritical) "1" else "0"}")
        if (critTags.isNotEmpty()) add("hard[$critTags]")
        if (softTags.isNotEmpty()) add("soft[$softTags]")
        add("block=$blocked")
    }.joinToString(" ")
    Log.d(TAG, msg)

    return LocalHeuristicsDecision(
        blocked = blocked,
        totalScore = if (hasCritical) 1.0 else softTotal,
        reasonDetails = details.toList()
    )
}
