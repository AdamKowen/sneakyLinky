package com.example.sneakylinky.service.urlanalyzer

import java.net.URI
import java.net.IDN
import java.util.Locale
import java.lang.Character
import com.google.common.net.InternetDomainName

// ── Public API ───────────────────────────────────────────────────
fun String.toCanonUrlOrNull(): CanonUrl? {
    return (canonicalizeSafely(this) as? CanonicalParseResult.Success)?.canonUrl
}

fun String.isLikelyUrl(): Boolean {
    return canonicalizeSafely(this) is CanonicalParseResult.Success
}

fun String.canonicalize(): CanonicalParseResult {
    return canonicalizeSafely(this)
}

// ── Data Class ───────────────────────────────────────────────────

enum class ParseFailureReason {
    INVALID_URI,
    UNSUPPORTED_SCHEME,
    MISSING_HOST,
    MALFORMED_AUTHORITY,
    EMPTY_INPUT,
    UNKNOWN
}

private val schemeList = listOf("http", "https", "ftp", "ftps", "ws", "wss")

sealed class CanonicalParseResult {
    data class Success(val canonUrl: CanonUrl) : CanonicalParseResult() {
        override fun toString(): String = buildString {
            appendLine("✔️ Success:")
            appendLine(canonUrl.toString())
        }
    }
    data class Error(val reason: ParseFailureReason) : CanonicalParseResult() {
        override fun toString(): String = "❌ Parsing the uri failed - $reason \n"
    }
}

data class CanonUrl(               // scheme://[user[:password]@]host[:port][/path][;params][?query][#fragment]
    val originalUrl:      String,  // "https://user:pass@sub.domain.co.uk:8080/path/to/page.html;params?query=123&x=1#fragment"

    // Core URL components
    val scheme:           String,  // "https"
    val userInfo:         String?, // "user:pass"
    val hostUnicode:      String,  // "sub.пример.com"
    val hostAscii:        String?, // "sub.xn--e1afmkfd.com"
    val port:             Int?,    // 8080
    val path:             String,  // "/path/to/page.html"
    val query:            String?, // "query=123&x=1"
    val fragment:         String?, // "fragment"

    // Derived metadata
    val domain:           String?,  // "domain.co.uk"
    val subdomain:        String?,  // "sub"
    val tld:              String?,  // "co.uk"
    val pathSegments: List<String>, // ["shop", "category", "item"]
    val hasEncodedParts:  Boolean,  // true
    val isMixedScript:    Boolean,  // false
    val pathsToParams: Map<String, List<String>>, // { "shop" -> ["type=books"], "item" -> ["ref=123"] }
    ){ override fun toString(): String = buildString {
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


private fun extractUserInfo(uri: URI): String? {
    val authority = uri.authority ?: return null
    return if ("@" in authority) {
        authority.substringBefore("@")
    } else {
        null
    }
}

private fun extractHostUnicode(uri: URI): String? {
    return uri.host?.lowercase(Locale.ROOT)
        ?: uri.authority
            ?.substringAfter("@")
            ?.substringBefore(":")
            ?.lowercase(Locale.ROOT)
}

private fun safeToAsciiOrNull(unicodeHost: String?): String? {
    if (unicodeHost.isNullOrBlank()) return null
    return try {
        IDN.toASCII(unicodeHost).lowercase(Locale.ROOT)
    } catch (_: Exception) {
        null
    }
}

private fun decomposeWithGuava(host: String): Triple<String?, String?, String?> {
    return try {
        val domainName = InternetDomainName.from(host)

        if (!domainName.isUnderPublicSuffix) {
            Triple(null, null, null)
        } else {
            val topPrivate = domainName.topPrivateDomain().toString() // "example.co.uk"
            val publicSuffix = domainName.publicSuffix().toString()   // "co.uk"
            val subdomain = domainName.parts()
                .dropLast(publicSuffix.split('.').size + 1)
                .joinToString(".")
                .ifBlank { null }

            Triple(topPrivate, subdomain, publicSuffix)
        }
    } catch (_: Exception) {
        Triple(null, null, null)
    }
}

private fun extractPathParams(path: String): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()

    val segments = path.trimStart('/').split('/')
    for (segment in segments) {
        val (base, paramsPart) = segment.split(';', limit = 2).let {
            it[0] to it.getOrNull(1)
        }

        val params = paramsPart
            ?.split(';')
            ?.filter { it.contains('=') }
            ?: emptyList()

        result[base] = params.toMutableList()
    }

    return result
}

private fun tryParseUri(input: String): URI? {
    return try {
        URI(input)
    } catch (_: Exception) {
        null
    }
}

private fun parseCanonical(uri: URI, originalUrl: String): CanonicalParseResult {
    return try {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme == null || scheme !in schemeList) {
            return CanonicalParseResult.Error(ParseFailureReason.UNSUPPORTED_SCHEME)
        }

        val userInfo = extractUserInfo(uri)

        val hostUnicode = extractHostUnicode(uri)
        if (hostUnicode.isNullOrEmpty())
            return CanonicalParseResult.Error(ParseFailureReason.MISSING_HOST)

        val hostAscii = safeToAsciiOrNull(hostUnicode)
        if (hostAscii.isNullOrEmpty())
            return CanonicalParseResult.Error(ParseFailureReason.MALFORMED_AUTHORITY)

        val port = uri.port.takeIf { it != -1 }

        val path = uri.rawPath ?: ""
        val query = uri.rawQuery
        val fragment = uri.rawFragment

        val (domain, subdomain, tld) = decomposeWithGuava(hostAscii)
        val pathSegments = path.split('/').filter { it.isNotEmpty() }

        val hasEncodedParts = listOfNotNull(path, query, fragment).any { it.contains('%') }

        val isMixedScript = hostUnicode.any { ch ->
            val block = Character.UnicodeBlock.of(ch)
            block != null && block != Character.UnicodeBlock.BASIC_LATIN
        }

        val pathsToParams = extractPathParams(path)

        val canonUrl = CanonUrl(
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

        CanonicalParseResult.Success(canonUrl)
    } catch (_: Exception) {
        CanonicalParseResult.Error(ParseFailureReason.UNKNOWN)
    }
}

private fun canonicalizeSafely(url: String): CanonicalParseResult {
    if (url.isBlank()) {
        return CanonicalParseResult.Error(ParseFailureReason.EMPTY_INPUT)
    }
    val originalUrl = url
    val uri = tryParseUri(url) ?: return CanonicalParseResult.Error(ParseFailureReason.INVALID_URI)
    return parseCanonical(uri, originalUrl)
}



