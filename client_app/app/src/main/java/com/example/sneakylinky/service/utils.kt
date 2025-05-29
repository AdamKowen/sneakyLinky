package com.example.sneakylinky.service

import java.net.URL
import kotlin.math.max

fun extractDomain(urlString: String): String {
    return try {
        URL(urlString).host.lowercase()
    } catch (e: Exception) {
        urlString.lowercase()
    }
}

/** Compute the classic Levenshtein edit distance between two strings. */
fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j

    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[a.length][b.length]
}

/** Normalize distance to [0.0,1.0] where 0.0 = identical and 1.0 = maximally different. */
fun normalizedLevenshtein(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 0.0
    val dist = levenshtein(a, b)
    return dist.toDouble() / max(a.length, b.length)
}

/**
 * Compute similarity between two URLs by:
 * 1. Extracting each domain
 * 2. Returning the normalized Levenshtein distance
 */
fun domainSimilarity(url1: String, url2: String): Double {
    val d1 = extractDomain(url1)
    val d2 = extractDomain(url2)
    return normalizedLevenshtein(d1, d2)
}

fun main() {
    val testUrl  = "https://www.ymet.co.il/"
    val knownUrl = "https://www.ynet.co.il/"
    val sim = domainSimilarity(testUrl, knownUrl)

    println("Domain A: $testUrl")
    println("Domain B: $knownUrl")
    println("Similarity score: $sim")  // 0.0 = identical, closer to 0 = more similar
}
