package com.example.sneakylinky.service.urlanalyzer

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure parsing tests: String -> CanonUrl?
 * These tests do not hit DB or heuristics — only canonicalization.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CanonUrlParsingTest {

    /**
     * Comprehensive case — asserts EVERY field on CanonUrl.
     */
    @Test
    fun parse_all_fields_comprehensive() {
        val url =
            "https://admin:1234@shop.пример.co.uk:8443/products/view/item;ref=567?user=om%20er&id=7#details"

        val canon = url.toCanonUrlOrNull()
        assertNotNull("Canonicalization should succeed for: $url", canon)
        val c = canon!!

        // core fields
        assertEquals("Scheme mismatch", "https", c.scheme)
        assertEquals("UserInfo mismatch", "admin:1234", c.userInfo)
        assertEquals("Unicode host mismatch", "shop.пример.co.uk", c.hostUnicode)
        assertEquals("ASCII host (punycode) mismatch", "shop.xn--e1afmkfd.co.uk", c.hostAscii)
        assertEquals("Explicit port should be captured", 8443, c.port)
        assertEquals("Path mismatch", "/products/view/item;ref=567", c.path)
        assertEquals("Query mismatch", "user=om%20er&id=7", c.query)
        assertEquals("Fragment mismatch", "details", c.fragment)

        // derived fields
        assertEquals("eTLD+1 (domain) mismatch", "xn--e1afmkfd.co.uk", c.domain)
        assertEquals("Subdomain mismatch", "shop", c.subdomain)
        assertEquals("TLD mismatch", "co.uk", c.tld)
        assertEquals("Path segments mismatch",
            listOf("products", "view", "item;ref=567"), c.pathSegments)
        assertTrue("Expected encoded parts due to %20 in query", c.hasEncodedParts)
        assertTrue("Expected non-basic-latin in host (Unicode), isMixedScript should be true", c.isMixedScript)

        // matrix params map (every segment present; only "item" has params)
        val expectedParams = mapOf(
            "products" to emptyList<String>(),
            "view" to emptyList(),
            "item" to listOf("ref=567")
        )
        assertEquals("Matrix path params mismatch", expectedParams, c.pathsToParams)
    }

    /** IPv4 literal host should parse; domain/subdomain/tld are null. */
    @Test
    fun parse_ipv4_literal() {
        val url = "http://192.168.0.1/index.html"
        val canon = url.toCanonUrlOrNull()
        assertNotNull("Canonicalization should succeed for IPv4 literal: $url", canon)
        val c = canon!!

        assertEquals("Scheme mismatch", "http", c.scheme)
        assertNull("UserInfo should be null when not present", c.userInfo)
        assertEquals("Unicode host mismatch for IPv4", "192.168.0.1", c.hostUnicode)
        assertEquals("ASCII host mismatch for IPv4", "192.168.0.1", c.hostAscii)
        assertNull("No explicit port expected", c.port)
        assertEquals("Path mismatch", "/index.html", c.path)
        assertNull("Query should be null when absent", c.query)
        assertNull("Fragment should be null when absent", c.fragment)

        assertNull("Domain should be null for IP literals", c.domain)
        assertNull("Subdomain should be null for IP literals", c.subdomain)
        assertNull("TLD should be null for IP literals", c.tld)
        assertEquals("Path segments mismatch", listOf("index.html"), c.pathSegments)
        assertFalse("hasEncodedParts should be false when no % present", c.hasEncodedParts)
        assertFalse("IPv4 should not be marked as mixed script", c.isMixedScript)
    }

    /** IPv6 literal in brackets with explicit port. */
    @Test
    fun parse_ipv6_literal_with_port() {
        val url = "http://[2001:db8::1]:8080/"
        val canon = url.toCanonUrlOrNull()
        assertNotNull("Canonicalization should succeed for IPv6 literal: $url", canon)
        val c = canon!!

        assertEquals("Scheme mismatch", "http", c.scheme)
        assertEquals("IPv6 host should be unbracketed", "2001:db8::1", c.hostUnicode)
        assertEquals("ASCII host should equal IPv6 literal", "2001:db8::1", c.hostAscii)
        assertEquals("Explicit port should be captured", 8080, c.port)
        assertEquals("Path mismatch", "/", c.path)

        assertNull("Domain should be null for IP literals", c.domain)
        assertNull("Subdomain should be null for IP literals", c.subdomain)
        assertNull("TLD should be null for IP literals", c.tld)
        assertFalse("hasEncodedParts should be false when no % present", c.hasEncodedParts)
        assertFalse("IPv6 should not be marked as mixed script", c.isMixedScript)
    }

    /** Unicode domain (no subdomain) — should punycode host and set isMixedScript = true. */
    @Test
    fun parse_unicode_domain_punycode() {
        val url = "https://пример.com/"
        val canon = url.toCanonUrlOrNull()
        assertNotNull("Canonicalization should succeed for Unicode domain: $url", canon)
        val c = canon!!

        assertEquals("Scheme mismatch", "https", c.scheme)
        assertEquals("Unicode host mismatch", "пример.com", c.hostUnicode)
        assertEquals("Punycode host mismatch (пример -> xn--e1afmkfd)",
            "xn--e1afmkfd.com", c.hostAscii)
        assertEquals("Path mismatch", "/", c.path)

        assertEquals("eTLD+1 mismatch", "xn--e1afmkfd.com", c.domain)
        assertNull("Subdomain expected to be null", c.subdomain)
        assertEquals("TLD mismatch", "com", c.tld)
        assertTrue("Unicode domain should set isMixedScript=true", c.isMixedScript)
    }

    /** Unsupported scheme should return null (we only accept http/https by default). */
    @Test
    fun parse_invalid_scheme_returns_null() {
        val url = "gopher://example.com"
        val canon = url.toCanonUrlOrNull()
        assertNull("Unsupported scheme should yield null: $url", canon)
    }

    /** Encoded characters in path/query/fragment should set hasEncodedParts = true. */
    @Test
    fun parse_has_encoded_parts() {
        val url = "https://example.com/p%61th?q=a%20b#frag%2F"
        val canon = url.toCanonUrlOrNull()
        assertNotNull("Canonicalization should succeed", canon)
        val c = canon!!

        assertEquals("Path should preserve raw encoding", "/p%61th", c.path)
        assertEquals("Query should preserve raw encoding", "q=a%20b", c.query)
        assertEquals("Fragment should preserve raw encoding", "frag%2F", c.fragment)
        assertTrue("Any '%' in path/query/fragment should set hasEncodedParts=true", c.hasEncodedParts)
    }
}
