package com.example.sneakylinky.service

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

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

    /** Default limit for maximum redirects to follow manually */
    private const val DEFAULT_REDIRECT_LIMIT = 5

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
            val finalStatusCode: Int
        ) : UrlResolutionResult()

        data class Failure(
            val originalUrl: String,
            val error: ErrorCause,
            val redirectCount: Int = 0
        ) : UrlResolutionResult()

        enum class ErrorCause(val code: Int, val description: String) {
            INVALID_URL(1, "The supplied URL is invalid or not absolute"),
            LOOP_DETECTED(2, "A redirect loop was detected"),
            EXCEEDED_REDIRECT_LIMIT(3, "The redirect chain exceeded the allowed limit"),
            UNRECOVERABLE_LOCATION(
                4,
                "Redirect response missing or containing invalid Location header"
            ),
            NETWORK_EXCEPTION(5, "Network error, timeout or request cancelled"),
            UNKNOWN(6, "An unknown error occurred")
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
        fun resolveUrl(
            url: String,
            maxRedirects: Int = DEFAULT_REDIRECT_LIMIT
        ): UrlResolutionResult {

            println("🔍 Resolving URL: $url")
            var current = HttpUrl.parse(url)
                ?: return UrlResolutionResult.Failure(
                    url,
                    UrlResolutionResult.ErrorCause.INVALID_URL
                )

            val visited = mutableSetOf<HttpUrl>()
            var hops = 0

            while (hops <= maxRedirects) {

                try {
                    client.newCall(buildHeadRequest(current)).execute().use { res ->

                        when {
                            res.code() < 300 || res.code() >= 400 -> {
                                return UrlResolutionResult.Success(
                                    url,
                                    current.toString(),
                                    hops,
                                    res.code()
                                )
                            }

                            res.code() in 300..399 -> {
                                val next = nextHop(res)

                                if (next == null) {
                                    return UrlResolutionResult.Failure(
                                        url,
                                        UrlResolutionResult.ErrorCause.UNRECOVERABLE_LOCATION,
                                        hops
                                    )
                                }

                                if (!visited.add(next)) {
                                    return UrlResolutionResult.Failure(
                                        url,
                                        UrlResolutionResult.ErrorCause.LOOP_DETECTED,
                                        hops
                                    )
                                }

                                current = next
                                hops++
                            }

                            else -> {}
                        }
                    }
                } catch (e: IOException) {
                    return UrlResolutionResult.Failure(
                        url,
                        UrlResolutionResult.ErrorCause.NETWORK_EXCEPTION,
                        hops
                    )
                } catch (e: Exception) {
                    return UrlResolutionResult.Failure(
                        url,
                        UrlResolutionResult.ErrorCause.UNKNOWN,
                        hops
                    )
                }
            }

            return UrlResolutionResult.Failure(
                url,
                UrlResolutionResult.ErrorCause.EXCEEDED_REDIRECT_LIMIT,
                hops
            )
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


        HttpUrl.parse(location)?.let { return it }

        return res.request().url().resolve(location)
    }

}

