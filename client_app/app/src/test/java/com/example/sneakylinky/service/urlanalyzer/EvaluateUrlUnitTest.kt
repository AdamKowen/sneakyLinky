// File: app/src/test/java/com/example/sneakylinky/service/urlanalyzer/EvaluateUrlUnitTest.kt
package com.example.sneakylinky.service.urlanalyzer

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/* ──────────────────────────────────────────────────────────────────────────────
   Unit tests (pure JVM with Robolectric): core logic only, no Room.
   - We test heuristics via analyzeAndDecide(canon)
   - We inject a tiny whitelist for MED using WhitelistSource.loader
   - We avoid any AppDatabase usage here.
   - ShadowLog sends android.util.Log to stdout so you can see scores.
   ──────────────────────────────────────────────────────────────────────────── */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EvaluateUrlUnitTest {

    private var savedLoader: (suspend () -> List<String>)? = null

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        // Save the production loader and default to empty (MED off) per test unless set.
        savedLoader = WhitelistSource.loader
        WhitelistSource.loader = { emptyList() }
    }

    @After
    fun tearDown() {
        savedLoader?.let { WhitelistSource.loader = it }
        ShadowLog.stream = null
    }

    private fun canonOf(raw: String): CanonUrl =
        raw.toCanonUrlOrNull() ?: error("Failed to canonicalize: $raw")

    @Test
    fun med_close_domain_blocks_when_med_on() = runBlocking {
        // MED ON with small whitelist
        WhitelistSource.loader = { listOf("example.com") }

        val c = canonOf("https://examp1e.com/") // 'l' -> '1' lookalike
        val r = analyzeAndDecide(c)

        assertTrue("expected block due to MED", r.blocked)
        assertTrue("near-whitelist reason present", r.reasons.contains(Reason.NEAR_WHITELIST_LOOKALIKE))
    }

    @Test
    fun simple_known_good_is_safe() = runBlocking {
        // MED OFF (no whitelist influence)
        WhitelistSource.loader = { emptyList() }

        val c = canonOf("https://www.example.org/")
        val r = analyzeAndDecide(c)

        assertFalse("expected safe", r.blocked)
        assertTrue("no reasons expected", r.reasons.isEmpty())
    }

    @Test
    fun port_mismatch_only_does_not_block_when_med_off() = runBlocking {
        // MED OFF to avoid near-whitelist accidental scoring
        WhitelistSource.loader = { emptyList() }

        // Use a familiar TLD so we don't trip UNFAMILIAR_TLD
        val c = canonOf("http://legit.example.com:8080/home")
        val r = analyzeAndDecide(c)

        assertFalse("port mismatch alone should not cross threshold", r.blocked)
        assertTrue("expect PORT_SCHEME_MISMATCH reason",
            r.reasons.contains(Reason.PORT_SCHEME_MISMATCH))
        assertFalse("should NOT flag MED on exact whitelist OFF",
            r.reasons.contains(Reason.NEAR_WHITELIST_LOOKALIKE))
    }

    @Test
    fun length_plus_subdomains_and_encoded_parts_together_block() = runBlocking {
        // MED OFF; we want the soft heuristics to trigger by combination
        WhitelistSource.loader = { emptyList() }

        val raw = "https://a.b.c.d.e.f.g.h.i.j.k.verylongsub.hostknown.org" +
                "/path/with/a/lot/of/segments/and/stuff" +
                "/more/and/more/and/more/%20/encoded?q=a%20b%20c#frag%2F"
        val c = canonOf(raw)
        val r = analyzeAndDecide(c)

        assertTrue("expected block by combined soft heuristics", r.blocked)
        // sanity: at least one of the expected soft reasons must appear
        assertTrue(
            r.reasons.any {
                it == Reason.LONG_URL ||
                        it == Reason.TOO_MANY_SUBDOMAINS ||
                        it == Reason.ENCODED_PARTS
            }
        )
    }

    @Test
    fun ip_literal_is_critical_even_when_med_off() = runBlocking {
        WhitelistSource.loader = { emptyList() }

        val c = canonOf("https://192.168.1.10/login")
        val r = analyzeAndDecide(c)

        assertTrue("IP literal should be critical", r.blocked)
        assertTrue(r.reasons.contains(Reason.IP_HOST))
    }

    @Test
    fun userinfo_is_critical_even_when_med_off() = runBlocking {
        WhitelistSource.loader = { emptyList() }

        val c = canonOf("https://admin:1234@example.org/secure")
        val r = analyzeAndDecide(c)

        assertTrue("Userinfo should be critical", r.blocked)
        assertTrue(r.reasons.contains(Reason.USERINFO_PRESENT))
    }
}
