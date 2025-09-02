package com.example.sneakylinky.service.urlanalyzer

import android.util.Log
import java.net.URI
import java.net.IDN
import java.util.Locale
import java.lang.Character
import com.google.common.net.InternetDomainName


/* ──────────────────────────────────────────────────────────────────────────────
   - Parse a user-facing URL string into a normalized [CanonUrl].
   - Returns null on any failure (no crashes), while logging a sanitized hint.
   - Inputs are expected to be browser-intent URLs (Android already recognized them).
   ──────────────────────────────────────────────────────────────────────────── */
fun String.toCanonUrlOrNull(): CanonUrl? {
    if (this.isBlank()) {
        Log.d("URL_PARSE", "empty input")
        return null
    }
    val uri = tryParseUri(this) ?: run {
        Log.d("URL_PARSE", "invalid URI")
        return null
    }
    return parseCanonical(uri, this).also { canon ->
        if (canon == null) Log.d("URL_PARSE", "parse error for ${summarizeForLog(uri)}")
    }
}

/* ──────────────────────────────────────────────────────────────────────────────
   Canonical URL data
   - On success, fields are normalized and safe for heuristics.
   - We intentionally keep raw path/query/fragment (as percent-encoded strings).
   - NOTE: isMixedScript is a simple "has non-basic-latin" flag, not true script-mixing.
   ──────────────────────────────────────────────────────────────────────────── */

private val schemeList = listOf("http", "https") // Keep focused. Add ftp/ws/wss if you truly need them.
private val IPV4_BASIC_REGEX = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")

data class CanonUrl(
    // scheme://[user[:password]@]host[:port][/path][;params][?query][#fragment]
    val originalUrl: String,  // e.g., "https://user:pass@sub.domain.co.uk:8080/path;param?x=1#frag"

    // Core URL components
    val scheme: String,        // "https"
    val userInfo: String?,     // "user:pass"
    val hostUnicode: String,   // "sub.пример.com" or "2001:db8::1"
    val hostAscii: String?,    // "sub.xn--e1afmkfd.com" or "2001:db8::1"
    val port: Int?,            // 8080 (only if explicitly present in the URL)
    val path: String,          // "/path/to/page.html"
    val query: String?,        // "x=1&y=2"
    val fragment: String?,     // "frag"

    // Derived metadata
    val domain: String?,       // "domain.co.uk" (eTLD+1), null for IP/localhost/etc.
    val subdomain: String?,    // "sub"
    val tld: String?,          // "co.uk"
    val pathSegments: List<String>, // ["shop", "category", "item"]
    val hasEncodedParts: Boolean,   // any '%' in path/query/fragment
    val isMixedScript: Boolean,     // simple: true if any char in hostUnicode is non Basic Latin
    val pathsToParams: Map<String, List<String>>, // { "item" -> ["ref=123"] }
) {
    override fun toString(): String = buildString {
        appendLine("  originalUrl      = $originalUrl")
        appendLine("  scheme           = $scheme")
        appendLine("  userInfo         = ${userInfo ?: "<null>"}")
        appendLine("  hostUnicode      = $hostUnicode")
        appendLine("  hostAscii        = ${hostAscii ?: "<null>"}")
        appendLine("  port             = ${port ?: "<default>"}")
        appendLine("  path             = $path")
        appendLine("  query            = ${query ?: "<null>"}")
        appendLine("  fragment         = ${fragment ?: "<null>"}")
        appendLine("  domain           = ${domain ?: "<null>"}")
        appendLine("  subdomain        = ${subdomain ?: "<null>"}")
        appendLine("  tld              = ${tld ?: "<null>"}")
        appendLine("  pathSegments     = $pathSegments")
        appendLine("  hasEncodedParts  = $hasEncodedParts")
        appendLine("  isMixedScript    = $isMixedScript")
        appendLine("  pathsToParams    = $pathsToParams")
    }
}

/* ──────────────────────────────────────────────────────────────────────────────
   Helpers (kept small and single-purpose)
   ──────────────────────────────────────────────────────────────────────────── */

/** Best-effort: create a URI without throwing. */
private fun tryParseUri(input: String): URI? =
    try { URI(input) } catch (_: Exception) {
        Log.d("URL_PARSE", "tryParseUri failed")
        null
    }

/** Minimal, privacy-friendly summary for logs (scheme://host[:port]). */
private fun summarizeForLog(uri: URI?): String {
    if (uri == null) return "<invalid>"
    val scheme = uri.scheme ?: "?"
    val host = extractHostUnicode(uri) ?: "?"
    val port = uri.port.takeIf { it != -1 }?.let { ":$it" } ?: ""
    return "$scheme://$host$port"
}

/** Extracts userInfo if present in the authority. */
private fun extractUserInfo(uri: URI): String? {
    val authority = uri.authority ?: return null
    return if ("@" in authority) authority.substringBefore("@") else null
}

/**
 * Robust host extraction in lowercase Unicode form.
 * - Uses URI.host when available.
 * - Fallback parses authority, including bracketed IPv6 literals like [2001:db8::1]:443.
 * - Returns host without brackets.
 */
private fun extractHostUnicode(uri: URI): String? {
    uri.host?.let { return it.lowercase(Locale.ROOT) }

    val authority = uri.authority ?: return null
    val afterUser = authority.substringAfter('@', authority)

    return if (afterUser.startsWith("[")) {
        val end = afterUser.indexOf(']')
        if (end != -1) {
            afterUser.substring(1, end).lowercase(Locale.ROOT) // strip '[...]'
        } else null
    } else {
        afterUser.substringBefore(":").lowercase(Locale.ROOT)
    }
}

/**
 * Convert Unicode host to ASCII punycode when appropriate.
 * - If host looks like an IP literal (IPv4 or contains ':' for IPv6), return as-is.
 */
private fun safeToAsciiOrNull(unicodeHost: String?): String? {
    if (unicodeHost.isNullOrBlank()) return null
    if (isLikelyIpLiteral(unicodeHost)) return unicodeHost // keep IPs as-is
    return try {
        IDN.toASCII(unicodeHost).lowercase(Locale.ROOT)
    } catch (_: Exception) {
        Log.d("URL_PARSE", "safeToAsciiOrNull: could not convert host to ASCII")
        null
    }
}

private fun isLikelyIpLiteral(host: String): Boolean =
    host.contains(':') || IPV4_BASIC_REGEX.matches(host)

/** eTLD+1 decomposition via Guava. Returns (domain, subdomain, tld) or all nulls if not applicable. */
private fun decomposeWithGuava(hostAscii: String): Triple<String?, String?, String?> =
    try {
        val domainName = InternetDomainName.from(hostAscii)

        if (!domainName.isUnderPublicSuffix) {
            Log.d("URL_PARSE", "decomposeWithGuava: not under public suffix")
            Triple(null, null, null)
        } else {
            val topPrivate = domainName.topPrivateDomain().toString()   // e.g. "example.co.uk"
            val publicSuffix = domainName.publicSuffix().toString()     // e.g. "co.uk"
            val subdomain = domainName.parts()
                .dropLast(publicSuffix.split('.').size + 1)
                .joinToString(".")
                .ifBlank { null }

            Triple(topPrivate, subdomain, publicSuffix)
        }
    } catch (_: Exception) {
        Log.d("URL_PARSE", "decomposeWithGuava: exception")
        Triple(null, null, null)
    }

/**
 * Parses matrix params embedded in path segments ("segment;key=val;key2=val2").
 * Returns a map from base segment -> list of "key=value" strings.
 */
private fun extractPathParams(path: String): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()
    val segments = path.trimStart('/').split('/')

    for (segment in segments) {
        val (base, paramsPart) = segment.split(';', limit = 2).let { it[0] to it.getOrNull(1) }
        val params = paramsPart?.split(';')?.filter { '=' in it } ?: emptyList()
        if (base.isNotEmpty()) {
            if (params.isNotEmpty()) {
                Log.d("URL_PARSE", "extractPathParams: base=$base params=$params")
            }
            result.getOrPut(base) { mutableListOf() }.addAll(params)
        }
    }
    return result
}

/** Best-effort port extraction. Uses URI.port if available, else parses authority. */
private fun extractPortFromAuthority(uri: URI): Int? {
    if (uri.port != -1) return uri.port
    val auth = uri.authority ?: return null
    val afterUser = auth.substringAfter('@', auth)

    return if (afterUser.startsWith("[")) {
        // [2001:db8::1]:8443
        val end = afterUser.indexOf(']')
        if (end != -1 && end + 1 < afterUser.length && afterUser[end + 1] == ':') {
            afterUser.substring(end + 2).toIntOrNull()
        } else null
    } else {
        // host:8443
        afterUser.substringAfter(':', "").toIntOrNull()
    }
}

/* ──────────────────────────────────────────────────────────────────────────────
   Core canonicalizer: strict URI → CanonUrl?
   - Enforces allowed schemes
   - Requires a host (IP or domain)
   - Converts Unicode host to Punycode when needed
   - Derives eTLD+1/subdomain/TLD via Guava
   ──────────────────────────────────────────────────────────────────────────── */
private fun parseCanonical(uri: URI, originalUrl: String): CanonUrl? {
    return try {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme == null || scheme !in schemeList) {
            Log.d("URL_PARSE", "unsupported scheme for ${summarizeForLog(uri)}")
            return null
        }

        val userInfo = extractUserInfo(uri)

        val hostUnicode = extractHostUnicode(uri)?.removeSurrounding("[", "]")
        if (hostUnicode.isNullOrEmpty()) {
            Log.d("URL_PARSE", "missing host for ${summarizeForLog(uri)}")
            return null
        }

        val hostAscii = safeToAsciiOrNull(hostUnicode)
        if (hostAscii.isNullOrEmpty()) {
            Log.d("URL_PARSE", "malformed authority for ${summarizeForLog(uri)}")
            return null
        }

        val port = extractPortFromAuthority(uri)
        val path = uri.rawPath ?: ""
        val query = uri.rawQuery
        val fragment = uri.rawFragment

        val (domain, subdomain, tld) = decomposeWithGuava(hostAscii)
        val pathSegments = path.split('/').filter { it.isNotEmpty() }
        val hasEncodedParts = listOfNotNull(path, query, fragment).any { '%' in it }

        val isMixedScript = hostUnicode.any { ch ->
            val block = Character.UnicodeBlock.of(ch)
            block != null && block != Character.UnicodeBlock.BASIC_LATIN
        }

        val pathsToParams = extractPathParams(path)

        CanonUrl(
            originalUrl = originalUrl,
            scheme = scheme,
            userInfo = userInfo,
            hostUnicode = hostUnicode,
            hostAscii = hostAscii,
            port = port,
            path = path,
            query = query,
            fragment = fragment,
            domain = domain,
            subdomain = subdomain,
            tld = tld,
            pathSegments = pathSegments,
            hasEncodedParts = hasEncodedParts,
            isMixedScript = isMixedScript,
            pathsToParams = pathsToParams,
        )
    } catch (_: Exception) {
        Log.d("URL_PARSE", "unknown parse error for ${summarizeForLog(uri)}")
        null
    }
}
