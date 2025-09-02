//// File: app/src/test/java/com/example/sneakylinky/service/urlanalyzer/UrlHeuristicsUnitTest.kt
package com.example.sneakylinky.service.urlanalyzer

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.junit.BeforeClass
import org.junit.AfterClass


/* ──────────────────────────────────────────────────────────────────────────────
   - Unit tests per heuristic (boolean & numeric).
   - Uses real CanonUrl parser via toCanonUrlOrNull(); no network/redirects.
   - MED tests are @Ignore by default due to AppDatabase dependency.
   ──────────────────────────────────────────────────────────────────────────── */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UrlHeuristicsUnitTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun enableLogcatForThisClass() {
            ShadowLog.stream = System.out   // send android.util.Log to test output
        }

        @AfterClass
        @JvmStatic
        fun disableLogcatForThisClass() {
            ShadowLog.stream = null
        }
    }

    /* =======================
       MED (DB-dependent) — ignored by default
       ======================= */

    @Test
    fun medNearWhitelist_close_domain_scores() {
        val c = "https://examp1e.com/".toCanonUrlOrNull()!!
        val saved = WhitelistSource.loader
        try {
            // Inject a fake whitelist for this test (no real DB needed)
            WhitelistSource.loader = { listOf("example.com", "google.com") }

            val info = kotlinx.coroutines.runBlocking { hMedNearWhitelistInfo(c) }

            // Prints for visibility in test output (in addition to Logcat via ShadowLog)
            println("[MED close] nearest=${info.nearestDomain} ratio=${info.ratio} score=${info.score}")

            // Expect >0 when whitelist contains "example.com"
            assertTrue("expected >0 when seeded with example.com", info.score > 0.0)

            // Optional stronger checks (uncomment if you want)
            // assertEquals("example.com", info.nearestDomain)
            // assertTrue("ratio should be <= 0.20 to score > 0", (info.ratio ?: 1.0) <= 0.20)
        } finally {
            WhitelistSource.loader = saved
        }
    }

    @Test
    fun medNearWhitelist_far_domain_zero() {
        val c = "https://totally-unrelated.host/".toCanonUrlOrNull()!!
        val saved = WhitelistSource.loader
        try {
            // Inject a fake whitelist for this test (no real DB needed)
            WhitelistSource.loader = { listOf("example.com", "google.com") }

            val info = kotlinx.coroutines.runBlocking { hMedNearWhitelistInfo(c) }

            println("[MED far] nearest=${info.nearestDomain} ratio=${info.ratio} score=${info.score}")

            // With the fake whitelist, unrelated host should score 0
            assertEquals("expected 0 for unrelated host", 0.0, info.score, 1e-9)
        } finally {
            WhitelistSource.loader = saved
        }
    }
}
    /* =======================
       Critical booleans
       ======================= */

//    @Test
//    fun ipHost_ipv4_true() {
//        val c = "http://192.168.0.1/".toCanonUrlOrNull()!!
//        assertTrue(hIpHost(c))
//    }
//
//    @Test fun ipHost_ipv6_true() {
//        val c = "http://[2001:db8::1]/".toCanonUrlOrNull()!!
//        assertTrue(hIpHost(c))
//    }
//
//    @Test fun ipHost_domain_false() {
//        val c = "https://example.com/".toCanonUrlOrNull()!!
//        assertFalse(hIpHost(c))
//    }
//
//    @Test fun mixedScript_unicode_true() {
//        val c = "https://пример.com/".toCanonUrlOrNull()!!
//        assertTrue(hMixedScript(c))
//    }
//
//    @Test fun mixedScript_ascii_false() {
//        val c = "https://example.com/".toCanonUrlOrNull()!!
//        assertFalse(hMixedScript(c))
//    }
//
//    @Test fun userInfo_present_true() {
//        val c = "https://user:pass@example.com/".toCanonUrlOrNull()!!
//        assertTrue(hUserInfo(c))
//    }
//
//    @Test fun userInfo_absent_false() {
//        val c = "https://example.com/".toCanonUrlOrNull()!!
//        assertFalse(hUserInfo(c))
//    }
//
//    @Test fun unfamiliarTld_unknown_true() {
//        // "xyz" is NOT in the FAMILIAR_TLDS in UrlHeuristicsCore.kt
//        val c = "https://example.xyz/".toCanonUrlOrNull()!!
//        assertTrue(hUnfamiliarTld(c))
//    }
//
//    @Test fun unfamiliarTld_known_false() {
//        val c = "https://example.com/".toCanonUrlOrNull()!!
//        assertFalse(hUnfamiliarTld(c))
//    }
//
//    /* =======================
//       Non-critical booleans
//       ======================= */
//
//    @Test fun portSchemeMismatch_http_8080_true() {
//        val c = "http://example.com:8080/".toCanonUrlOrNull()!!
//        assertTrue(hPortSchemeMismatch(c))
//    }
//
//    @Test fun portSchemeMismatch_https_443_false() {
//        val c = "https://example.com:443/".toCanonUrlOrNull()!!
//        assertFalse(hPortSchemeMismatch(c))
//    }
//
//    @Test fun portSchemeMismatch_no_explicit_port_false() {
//        val c = "https://example.com/".toCanonUrlOrNull()!!
//        assertFalse(hPortSchemeMismatch(c))
//    }
//
//    @Test fun encodedParts_present_true() {
//        val c = "https://example.com/a%2F?x=a%20b#frag%2F".toCanonUrlOrNull()!!
//        assertTrue(hEncodedParts(c))
//    }
//
//    @Test fun encodedParts_absent_false() {
//        val c = "https://example.com/path?x=ab#frag".toCanonUrlOrNull()!!
//        assertFalse(hEncodedParts(c))
//    }
//
//    /* =======================
//       Numeric heuristics
//       ======================= */
//
//    @Test fun urlLength_safe_zero() {
//        // LEN_SAFE_MAX = 80 → score 0 at/below baseline
//        val c = "https://example.com/".toCanonUrlOrNull()!!
//        assertEquals(0.0, hUrlLength(c), 1e-6)
//    }
//
//    @Test fun urlLength_suspicious_partial() {
//        // >80 but <300 → partial score
//        val longPath = "/".padEnd(120, 'a')
//        val c = "https://example.com$longPath".toCanonUrlOrNull()!!
//        val s = hUrlLength(c)
//        assertTrue("expected partial (0<s<1)", s > 0.0 && s < 1.0)
//    }
//
//    @Test fun urlLength_critical_full() {
//        // ≥300 → full critical score (≈1.0)
//        val longPath = "/".padEnd(320, 'a')
//        val c = "https://example.com$longPath".toCanonUrlOrNull()!!
//        assertTrue("expected near/full 1.0", hUrlLength(c) >= 0.99)
//    }
//
//    @Test fun subdomainDepth_safe_zero() {
//        // SUBD_SAFE_MAX = 2 → exactly 2 labels (e.g., a.b.example.com → subd "a.b")
//        val c = "https://a.b.example.com/".toCanonUrlOrNull()!!
//        assertEquals(0.0, hSubdomainDepth(c), 1e-6)
//    }
//
//    @Test fun subdomainDepth_suspicious_partial() {
//        // 4 labels in subdomain (a.b.c.d.example.com) → partial
//        val c = "https://a.b.c.d.example.com/".toCanonUrlOrNull()!!
//        val s = hSubdomainDepth(c)
//        assertTrue("expected partial (0<s<1)", s > 0.0 && s < 1.0)
//    }
//
//    @Test fun subdomainDepth_critical_full() {
//        // ≥6 labels in subdomain (a.b.c.d.e.f.example.com) → ~1.0
//        val c = "https://a.b.c.d.e.f.example.com/".toCanonUrlOrNull()!!
//        assertTrue("expected near/full 1.0", hSubdomainDepth(c) >= 0.99)
//    }
//
//    @Test fun phishKeywords_none_zero() {
//        val c = "https://example.com/hello/world".toCanonUrlOrNull()!!
//        assertEquals(0.0, hPhishKeywords(c), 1e-6)
//    }
//
//    @Test fun phishKeywords_some_partial() {
//        // unique hits: secure, login, account
//        val c = "https://secure-login-account.example.com/".toCanonUrlOrNull()!!
//        val s = hPhishKeywords(c)
//        assertTrue("expected partial (0<s<1)", s > 0.0 && s < 1.0)
//    }
//
//    @Test fun phishKeywords_many_critical() {
//        // unique hits: secure, login, account, reset, password, verify → 6
//        val c = "https://secure-login-account.example.com/reset/password?verify=1"
//            .toCanonUrlOrNull()!!
//        assertTrue("expected near/full 1.0", hPhishKeywords(c) >= 0.99)
//    }
//


//
//    /* =======================
//   analyzeAndDecide — larger end-to-end cases
//   ======================= */
//
//    @Test
//    fun analyze_critical_userinfo_blocks() {
//        val saved = WhitelistSource.loader
//        try {
//            WhitelistSource.loader = { listOf("example.com", "google.com") } // no real DB
//            val url = "https://admin:1234@shop.example.com/login?verify=1"
//            val c = url.toCanonUrlOrNull()!!
//            val r = kotlinx.coroutines.runBlocking { runLocalHeuristicsAndDecide(c) }
//
//            println("analyze_userinfo → blocked=${r.blocked} score=${"%.2f".format(r.totalScore)} reasons=${r.reasons}")
//            assertTrue("should hard-block on userinfo", r.blocked)
//            assertTrue(r.reasons.contains(Reason.USERINFO_PRESENT))
//        } finally { WhitelistSource.loader = saved }
//    }
//
//    @Test
//    fun analyze_ip_host_blocks() {
//        val saved = WhitelistSource.loader
//        try {
//            WhitelistSource.loader = { emptyList() } // MED not relevant
//            val url = "http://[2001:db8::1]:8080/abc"
//            val c = url.toCanonUrlOrNull()!!
//            val r = kotlinx.coroutines.runBlocking { runLocalHeuristicsAndDecide(c) }
//
//            println("analyze_ip → blocked=${r.blocked} score=${"%.2f".format(r.totalScore)} reasons=${r.reasons}")
//            assertTrue("should hard-block on IP host", r.blocked)
//            assertTrue(r.reasons.contains(Reason.IP_HOST))
//        } finally { WhitelistSource.loader = saved }
//    }
//
//    @Test
//    fun analyze_mixed_script_blocks() {
//        val saved = WhitelistSource.loader
//        try {
//            WhitelistSource.loader = { listOf("example.com") }
//            val url = "https://пример.com/account/reset?verify=1"
//            val c = url.toCanonUrlOrNull()!!
//            val r = kotlinx.coroutines.runBlocking { runLocalHeuristicsAndDecide(c) }
//
//            println("analyze_mixed → blocked=${r.blocked} score=${"%.2f".format(r.totalScore)} reasons=${r.reasons}")
//            assertTrue("should hard-block on mixed script", r.blocked)
//            assertTrue(r.reasons.contains(Reason.MIXED_SCRIPT))
//        } finally { WhitelistSource.loader = saved }
//    }
//
//    @Test
//    fun analyze_unfamiliar_tld_blocks() {
//        val saved = WhitelistSource.loader
//        try {
//            WhitelistSource.loader = { listOf("example.com") }
//            val url = "https://example.xyz/login"
//            val c = url.toCanonUrlOrNull()!!
//            val r = kotlinx.coroutines.runBlocking { runLocalHeuristicsAndDecide(c) }
//
//            println("analyze_unfamiliar_tld → blocked=${r.blocked} score=${"%.2f".format(r.totalScore)} reasons=${r.reasons}")
//            assertTrue("should hard-block on unfamiliar TLD", r.blocked)
//            assertTrue(r.reasons.contains(Reason.UNFAMILIAR_TLD))
//        } finally { WhitelistSource.loader = saved }
//    }
//
//    @Test
//    fun analyze_soft_combo_blocks_without_criticals() {
//        val saved = WhitelistSource.loader
//        try {
//            WhitelistSource.loader = { listOf("example.com", "google.com") }
//            val sub = "secure-login-account.a.b.c.d.e" // 6 subdomain labels (near/at critical)
//            val longPath = "/".padEnd(320, 'a')        // ≥ LEN_CRIT_MIN
//            val url = "https://$sub.example.com$longPath?q=a%20b&verify=1"
//            val c = url.toCanonUrlOrNull()!!
//            val r = kotlinx.coroutines.runBlocking { runLocalHeuristicsAndDecide(c) }
//
//            println("analyze_soft_combo → blocked=${r.blocked} score=${"%.2f".format(r.totalScore)} reasons=${r.reasons}")
//
//            assertFalse("no critical reasons expected",
//                r.reasons.any { it == Reason.IP_HOST || it == Reason.USERINFO_PRESENT || it == Reason.MIXED_SCRIPT || it == Reason.UNFAMILIAR_TLD }
//            )
//            assertTrue("should block via soft signals", r.blocked)
//            assertTrue(r.reasons.contains(Reason.LONG_URL))
//            assertTrue(r.reasons.contains(Reason.TOO_MANY_SUBDOMAINS))
//            assertTrue(r.reasons.contains(Reason.PHISH_KEYWORDS))
//            assertTrue("encoded parts should be present (q=a%20b)", r.reasons.contains(Reason.ENCODED_PARTS))
//        } finally { WhitelistSource.loader = saved }
//    }
//
//    @Test
//    fun analyze_port_mismatch_only_is_safe() {
//        val saved = WhitelistSource.loader
//        try {
//            WhitelistSource.loader = { listOf("example.com") }
//            val url = "http://example.com:8080/home"
//            val c = url.toCanonUrlOrNull()!!
//            val r = kotlinx.coroutines.runBlocking { runLocalHeuristicsAndDecide(c) }
//
//            println("analyze_port_only → blocked=${r.blocked} score=${"%.2f".format(r.totalScore)} reasons=${r.reasons}")
//            assertFalse("port mismatch alone should not cross threshold", r.blocked)
//            assertTrue(r.reasons.contains(Reason.PORT_SCHEME_MISMATCH))
//        } finally { WhitelistSource.loader = saved }
//    }
//
//    @Test
//    fun analyze_simple_known_good_is_safe() {
//        val saved = WhitelistSource.loader
//        try {
//            WhitelistSource.loader = { listOf("example.com", "google.com") }
//            val url = "https://www.example.com/"
//            val c = url.toCanonUrlOrNull()!!
//            val r = kotlinx.coroutines.runBlocking { runLocalHeuristicsAndDecide(c) }
//
//            println("analyze_good → blocked=${r.blocked} score=${"%.2f".format(r.totalScore)} reasons=${r.reasons}")
//            assertFalse("simple known-good URL should be safe", r.blocked)
//            assertTrue("no reasons expected on safe", r.reasons.isEmpty())
//        } finally { WhitelistSource.loader = saved }
//    }
//
//}
//
