package com.example.sneakylinky

import com.example.sneakylinky.service.LinkChecker
import com.example.sneakylinky.service.LinkChecker.UrlResolutionResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*      // assertEquals / assertTrue / assertThat


class LinkCheckerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // 200 OK, no redirects → expect Success
    @Test
    fun resolves_withoutRedirects_returnsSuccess() {
        server.enqueue(MockResponse().setResponseCode(200))

        val targetUrl = server.url("/no-redirect").toString()
        val result = LinkChecker.resolveUrl(targetUrl)

        assertTrue(result is UrlResolutionResult.Success)
        result as UrlResolutionResult.Success
        assertEquals(targetUrl, result.finalUrl)
        assertEquals(0, result.redirectCount)
        assertEquals(200, result.finalStatusCode)
    }

    // 302 → 301 → 200 chain → expect final success after 2 hops
    @Test
    fun follows_redirectChain_untilFinalLocation() {
        // /step1 302 → /step2
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/step2")
        )
        // /step2 301 → /final
        server.enqueue(
            MockResponse()
                .setResponseCode(301)
                .setHeader("Location", "/final")
        )
        // /final 200 OK
        server.enqueue(MockResponse().setResponseCode(200))

        val start = server.url("/step1").toString()
        val expectedFinal = server.url("/final").toString()

        val result = LinkChecker.resolveUrl(start)

        assertTrue(result is UrlResolutionResult.Success)
        result as UrlResolutionResult.Success
        assertEquals(expectedFinal, result.finalUrl)
        assertEquals(2, result.redirectCount)
        assertEquals(200, result.finalStatusCode)
    }

    // Redirect loop (/loop → /loop) should return LOOP_DETECTED failure
    @Test
    fun detect_redirectLoop_returnsLoopDetected() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/loop")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/loop")
        )

        val result = LinkChecker.resolveUrl(server.url("/loop").toString(), maxRedirects = 4)

        assertTrue(result is UrlResolutionResult.Failure)
        result as UrlResolutionResult.Failure
        assertEquals(UrlResolutionResult.ErrorCause.LOOP_DETECTED, result.error)
        assertTrue(result.redirectCount >= 1)
    }

    // More hops than maxRedirects → expect EXCEEDED_REDIRECT_LIMIT failure
    @Test
    fun exceeds_redirectLimit_returnsExceededLimit() {
        repeat(6) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", "/hop$it")
            )
        }

        val result = LinkChecker.resolveUrl(server.url("/hop0").toString(), maxRedirects = 3)

        assertTrue(result is UrlResolutionResult.Failure)
        result as UrlResolutionResult.Failure
        assertEquals(UrlResolutionResult.ErrorCause.EXCEEDED_REDIRECT_LIMIT, result.error)
        assertEquals(4, result.redirectCount)  // 0-based hops counter
    }

    // Relative Location header (/relative) must resolve correctly
    @Test
    fun follows_relativeLocationHeader_correctly() {
        //  start 302 → /relative
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/relative")
        )
        // 200 OK
        server.enqueue(MockResponse().setResponseCode(200))

        val start = server.url("/start").toString()
        val expectedFinal = server.url("/relative").toString()

        val result = LinkChecker.resolveUrl(start)

        assertTrue(result is UrlResolutionResult.Success)
        result as UrlResolutionResult.Success
        assertEquals(1, result.redirectCount)
        assertEquals(expectedFinal, result.finalUrl)
        assertEquals(200, result.finalStatusCode)
    }

    // Host without scheme becomes https://… but fails TLS → NETWORK_EXCEPTION
    @Test
    fun schemeLessHost_returnsNetworkExceptionButAddsScheme() {
        val naked = server.hostName              // "localhost"
        val result = LinkChecker.resolveUrl(naked)

        assertTrue(result is UrlResolutionResult.Failure)
        result as UrlResolutionResult.Failure
        assertEquals(UrlResolutionResult.ErrorCause.NETWORK_EXCEPTION, result.error)
        assertTrue(result.originalUrl.startsWith("https://"))
    }


    // Invalid URL (no scheme, no host) → expect INVALID_URL error
    @Test
    fun absurdString_returnsInvalidUrlError() {
        val absurd = "🚀:// white space\n"      // תווים אסורים + רווח + שורה חדשה
        val result = LinkChecker.resolveUrl(absurd)

        assertTrue(result is UrlResolutionResult.Failure)
        result as UrlResolutionResult.Failure
        assertEquals(UrlResolutionResult.ErrorCause.INVALID_URL, result.error)
        assertEquals(0, result.redirectCount)
    }
}
