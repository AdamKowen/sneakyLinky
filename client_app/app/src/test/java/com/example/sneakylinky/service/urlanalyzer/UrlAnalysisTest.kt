package com.example.sneakylinky.service.urlanalyzer

import org.junit.Assert.*
import org.junit.Test


class CanonicalUrlParserTest {

    @Test
    fun testValidAsciiUrl() {
        val url = "https://example.com/path?query=1#section"
        val result = url.canonicalize()
        println(result)
        assertTrue(result is CanonicalParseResult.Success)
        assertEquals("example.com", (result as CanonicalParseResult.Success).canonUrl.hostUnicode)
    }

    @Test
    fun testValidUnicodeUrl() {
        val url = "https://пример.рф/каталог?поиск=тест"
        val result = url.canonicalize()
        println(result)
        assertTrue(result is CanonicalParseResult.Success)
        val canon = (result as CanonicalParseResult.Success).canonUrl
        assertTrue(canon.hostAscii!!.startsWith("xn--"))
        assertTrue(canon.isMixedScript)
    }

    @Test
    fun testInvalidScheme() {
        val url = "gopher://example.com"
        val result = url.canonicalize()
        println(result)
        assertTrue(result is CanonicalParseResult.Error)
        assertEquals(ParseFailureReason.UNSUPPORTED_SCHEME, (result as CanonicalParseResult.Error).reason)
    }

    @Test
    fun testMissingHost() {
        val url = "https:///path/only"
        val result = url.canonicalize()
        println(result)
        assertTrue(result is CanonicalParseResult.Error)
        assertEquals(ParseFailureReason.MISSING_HOST, (result as CanonicalParseResult.Error).reason)
    }

    @Test
    fun testMalformedHost() {
        val url = "https://google💣.com"
        val result = url.canonicalize()
        println(result)
        assertTrue(result is CanonicalParseResult.Error)
        assertEquals(ParseFailureReason.MALFORMED_AUTHORITY, (result as CanonicalParseResult.Error).reason)
    }

    @Test
    fun testIsLikelyUrlTrue() {
        val url = "http://test.com"
        assertTrue(url.isLikelyUrl())
    }

    @Test
    fun testIsLikelyUrlFalse() {
        val url = "notaurl"
        assertFalse(url.isLikelyUrl())
    }

    @Test
    fun testToCanonUrlOrNullSuccess() {
        val url = "https://sub.example.co.uk/shop/item;ref=123"
        val canon = url.toCanonUrlOrNull()
        assertNotNull(canon)
        println("✅ Canonical: \n$canon")
    }

    @Test
    fun testToCanonUrlOrNullFailure() {
        val url = "http://google💣.com"
        val canon = url.toCanonUrlOrNull()
        assertNull(canon)
        println("❌ Canonicalization failed for: $url")
    }
}
