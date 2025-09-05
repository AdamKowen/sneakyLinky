package com.example.sneakylinky.service

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * `LinkChecker` is a utility object used to inspect URLs **without automatically following redirects**.
 *
 * The core functionality is to perform a HEAD-only probe on a given URL and manually follow redirects
 * up to a caller-defined maximum. It returns the final resolved URL or a descriptive failure result.
 *
 * We use the HTTP **HEAD** method because it allows checking the URL's existence and redirection
 * behavior **without downloading the response body**. This makes it:
 *
 * - **Faster**: since there's no payload to download.
 * - **Safer**: especially in security-sensitive contexts (e.g. phishing detection), where we want to
 *   avoid triggering side effects or loading malicious content from the target server.
 * - **Lighter on bandwidth**: which is important for mobile and bulk URL scanning use cases.
 *
 * It is especially useful in security-sensitive applications (e.g. phishing detection), where
 * automatic redirect following is discouraged.
 */
object LinkChecker {

    private const val TAG = "LinkChecker"
    /** Default limit for maximum redirects to follow manually */
    private const val DEFAULT_REDIRECT_LIMIT = 5


    /** Smart timeouts (tweak as you like)*/
    private const val OVERALL_BUDGET_MS   = 10000  // total time budget for the whole chain
    private const val PER_HOP_TIMEOUT_MS  = 7500  // max time allotted for a single hop
    private const val MIN_REMAINING_MS    = 1000   // minimum remaining budget to attempt another hop




    /** Internal HTTP client configured to *not* follow redirects automatically. */
    internal val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Represents the result of attempting to resolve a URL.
     */
    sealed class UrlResolutionResult {



        data class Success(
            val originalUrl: String,
            val finalUrl: String,
            val redirectCount: Int,
            val finalStatusCode: Int,
        ) : UrlResolutionResult()

        data class Failure(
            val originalUrl: String,
            val error: ErrorCause,
            val redirectCount: Int = 0
        ) : UrlResolutionResult()


        @Suppress("unused")
        enum class ErrorCause(val code: Int, val description: String) {
            INVALID_URL(1, "The supplied URL is invalid or not absolute"),
            LOOP_DETECTED(2, "A redirect loop was detected"),
            EXCEEDED_REDIRECT_LIMIT(3, "The redirect chain exceeded the allowed limit"),
            UNRECOVERABLE_LOCATION(
                4,
                "Redirect response missing or containing invalid Location header"
            ),
            NETWORK_EXCEPTION(5, "Network error, timeout or request cancelled"),
            UNKNOWN(6, "An unknown error occurred"),

            SLOW_REDIRECT(7, "Redirect chain took too long")
        }
    }

    /**
     * Attempts to resolve a given URL by issuing only HTTP HEAD requests.
     * Follows redirects manually up to a specified limit.
     *
     * @param url Absolute URL to inspect.
     * @param maxRedirects Maximum number of redirect hops to follow (default is 5).
     * @return A [UrlResolutionResult] representing either a success or a failure.
     */
    fun resolveUrl(url: String, maxRedirects: Int = DEFAULT_REDIRECT_LIMIT): UrlResolutionResult {

        var httpsUpgradeAttempted = false

        // Smart deadline for the whole chain
        val startedNs = System.nanoTime()
        val deadlineNs = startedNs + OVERALL_BUDGET_MS * 1_000_000L

        // --- normalize scheme --------------------------------------------------------
        var sanitized = url.trim()
        Log.d(TAG, "resolveUrl() start: input='$url' sanitized-pre='$sanitized'")

        val hasScheme = sanitized.startsWith("http://", true) || sanitized.startsWith("https://", true)

        if (!hasScheme) {
            sanitized = "https://$sanitized"
            Log.d(TAG, "no scheme detected → prefix 'https://' → sanitized='$sanitized'")
        }        // -----------------------------------------------------------------------------


        val parsed = HttpUrl.parse(sanitized)
        if (parsed == null) {
            Log.w(TAG, "HttpUrl.parse() failed → INVALID_URL for '$sanitized'")
            return UrlResolutionResult.Failure(
                sanitized,
                UrlResolutionResult.ErrorCause.INVALID_URL
            )
        } else {
            Log.d(TAG, "parsed OK: current='$parsed'")
        }
        var current: HttpUrl = parsed

        val visited = mutableSetOf<HttpUrl>()
        var hops = 0



        while (hops <= maxRedirects) {
            // Compute remaining budget and per-hop timeout
            val nowNs = System.nanoTime()
            val remainingMs = ((deadlineNs - nowNs) / 1_000_000L).coerceAtLeast(0L)
            if (remainingMs < MIN_REMAINING_MS) {
                Log.w(TAG, "resolve timeout: remaining=${remainingMs}ms hops=$hops")
                return UrlResolutionResult.Failure(sanitized, UrlResolutionResult.ErrorCause.SLOW_REDIRECT, hops)
            }
            val hopTimeoutMs = kotlin.math.min(PER_HOP_TIMEOUT_MS.toLong(), remainingMs).toInt()

            Log.d(TAG, "HEAD request → hop=$hops url='$current' remaining=${remainingMs}ms hopTimeout=${hopTimeoutMs}ms")

            try {

                val call = client.newCall(buildHeadRequest(current))
                // Per-hop call timeout bounded by remaining budget
                call.timeout().timeout(hopTimeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)


                // --- measure hop elapsed time ---
                val hopStart = System.nanoTime()
                call.execute().use { res ->

                    val code = res.code()
                    val loc  = res.header("Location")
                    Log.d(TAG, "HEAD response ← hop=$hops code=$code" + (if (loc != null) " location='${loc.take(200)}'" else ""))

                    when {
                        res.code() in 300..399 -> {
                            val next = nextHop(res)
                            if (next == null) {
                                Log.w(TAG, "redirect without valid Location → UNRECOVERABLE_LOCATION (hop=$hops)")
                                return UrlResolutionResult.Failure(
                                    sanitized, UrlResolutionResult.ErrorCause.UNRECOVERABLE_LOCATION, hops
                                )
                            }
                            if (!visited.add(next)) {
                                Log.w(TAG, "loop detected at '$next' (hop=$hops) → LOOP_DETECTED")
                                return UrlResolutionResult.Failure(
                                    sanitized, UrlResolutionResult.ErrorCause.LOOP_DETECTED, hops
                                )
                            }
                            Log.d(TAG, "follow redirect: '$current' → '$next'")
                            current = next
                            hops++
                        }
                        else -> {
                            Log.d(TAG, "final hop reached: hops=$hops final='$current' status=$code")
                            return UrlResolutionResult.Success(
                                sanitized, current.toString(), hops, code
                            )
                        }
                    }
                }
            } catch (e: IOException) {

                if (isHostnameMismatch(e)) {
                    android.util.Log.w(TAG, "SSL hostname mismatch for $current — allowing resolve to pass-through", e)
                    return UrlResolutionResult.Success(
                        originalUrl = sanitized,
                        finalUrl = current.toString(),
                        redirectCount = hops,
                        finalStatusCode = 0,
                    )
                }

                // NEW: treat explicit timeouts as SLOW_REDIRECT for clearer UX
                if (isTimeout(e)) {
                    Log.w(TAG, "timeout on hop=$hops url='$current' msg=${e.message}")
                    return UrlResolutionResult.Failure(
                        sanitized, UrlResolutionResult.ErrorCause.SLOW_REDIRECT, hops
                    )
                }

                // HTTPS upgrade on cleartext block (unchanged)
                val isCleartextBlocked =
                    (e is java.net.UnknownServiceException) &&
                            (e.message?.contains("CLEARTEXT", ignoreCase = true) == true)

                if (!httpsUpgradeAttempted &&
                    current.scheme().equals("http", ignoreCase = true) &&
                    isCleartextBlocked
                ) {
                    Log.w(TAG,"cleartext blocked at '$current' (hop=$hops) → attempting HTTPS upgrade once")
                    httpsUpgradeAttempted = true
                    val httpsUrl = try { current.newBuilder().scheme("https").build() } catch (_: Throwable) { null }
                    if (httpsUrl != null) {
                        sanitized = sanitized.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
                        current = httpsUrl
                        Log.d(TAG, "HTTPS upgrade success → retry same hop with '$current'")
                        continue
                    } else {
                        Log.e(TAG, "HTTPS upgrade failed to build new URL for '$current'")
                    }
                }

                Log.w(TAG, "IOException on hop=$hops url='$current': ${e.message}", e)
                return UrlResolutionResult.Failure(
                    sanitized, UrlResolutionResult.ErrorCause.NETWORK_EXCEPTION, hops
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception on hop=$hops url='$current': ${e.message}", e)
                return UrlResolutionResult.Failure(
                    sanitized, UrlResolutionResult.ErrorCause.UNKNOWN, hops
                )
            }
        }

        Log.w(TAG, "exceeded redirect limit ($maxRedirects) for start='$sanitized' at hop=$hops")
        return UrlResolutionResult.Failure(sanitized, UrlResolutionResult.ErrorCause.EXCEEDED_REDIRECT_LIMIT, hops)
    }

    // Helper to detect hostname mismatch across OkHttp/JDK variants
    private fun isHostnameMismatch(e: Throwable): Boolean {
        if (e is SSLPeerUnverifiedException) return true
        if (e is SSLHandshakeException && e.cause is SSLPeerUnverifiedException) return true
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("hostname") && (msg.contains("not verified") || msg.contains("mismatch"))
    }


    private fun isTimeout(e: Throwable): Boolean {
        return e is java.net.SocketTimeoutException ||
                e is java.io.InterruptedIOException ||
                (e.message?.contains("timeout", ignoreCase = true) == true)
    }


    /** Builds a HEAD request for the specified URL. */
    private fun buildHeadRequest(url: HttpUrl): Request =
        Request.Builder()
            .url(url)
            .head()
            .build()

    /** Extracts the next redirect hop from the Location header. */
    private fun nextHop(res: Response): HttpUrl? {
        val location = res.header("Location") ?: return null

        HttpUrl.parse(location)?.let {
            Log.d(TAG, "nextHop: absolute location parsed → '$it'")
            return it
        }

        // Fallback to relative resolution
        val resolved = res.request().url().resolve(location)
        if (resolved == null) {
            Log.w(TAG, "nextHop: failed to resolve relative location='$location'")
        } else {
            Log.d(TAG, "nextHop: relative resolved → '$resolved'")
        }
        return resolved
    }
}

