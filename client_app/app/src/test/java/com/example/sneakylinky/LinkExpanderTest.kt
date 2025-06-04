@file:Suppress("SameParameterValue")

package com.example.sneakylinky

import com.example.sneakylinky.service.LinkChecker
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*      // assertEquals / assertTrue / assertThat וכו'

class LinkCheckerTest {

    private lateinit var server: MockWebServer

    /* ───────────── Lifecycle ───────────── */

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /* ───────────── Helpers ───────────── */

    /** Enqueue a simple (empty-body) HEAD response with optional Location header. */
    private fun enqueueHead(status: Int, location: String? = null) {
        val response = MockResponse().setResponseCode(status).setBody("")
        location?.let { response.setHeader("Location", it) }
        server.enqueue(response)
    }

    /** Convenience to build an absolute URL served by the mock server. */
    private fun mockUrl(path: String) = server.url(path).toString()

    /* ───────────── Tests ───────────── */

    @Test
    fun noRedirect_returnsSuccessWith200() {
        // Arrange
        enqueueHead(200)
        val url = mockUrl("/ok")

        // Act
        val result = LinkChecker.resolveUrl(url)

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Success)
        result as LinkChecker.UrlResolutionResult.Success
        assertEquals(url, result.finalUrl)
        assertEquals(0, result.redirectCount)
        assertEquals(200, result.finalStatusCode)
    }

    @Test
    fun singleRedirect_resolvesWithOneHop() {
        // Arrange
        val targetUrl = mockUrl("/final")
        enqueueHead(302, targetUrl)  // hop #0
        enqueueHead(200)             // hop #1

        // Act
        val result = LinkChecker.resolveUrl(mockUrl("/start"))

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Success)
        result as LinkChecker.UrlResolutionResult.Success
        assertEquals(targetUrl, result.finalUrl)
        assertEquals(1, result.redirectCount)
        assertEquals(200, result.finalStatusCode)
    }

    @Test
    fun multipleRedirectsWithinLimit_returnsSuccess() {
        // Arrange
        val hop1 = mockUrl("/1")
        val hop2 = mockUrl("/2")
        val hop3 = mockUrl("/3")
        enqueueHead(301, hop1)
        enqueueHead(302, hop2)
        enqueueHead(307, hop3)
        enqueueHead(200)

        // Act
        val result = LinkChecker.resolveUrl(mockUrl("/start"))

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Success)
        result as LinkChecker.UrlResolutionResult.Success
        assertEquals(hop3, result.finalUrl)
        assertEquals(3, result.redirectCount)
    }

    @Test
    fun redirectLoop_returnsLoopDetected() {
        // Arrange
        val loopUrl = mockUrl("/loop")
        enqueueHead(301, loopUrl)
        enqueueHead(301, loopUrl)     // same Location repeated

        // Act
        val result = LinkChecker.resolveUrl(loopUrl)

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Failure)
        result as LinkChecker.UrlResolutionResult.Failure
        assertEquals(LinkChecker.UrlResolutionResult.ErrorCause.LOOP_DETECTED, result.error)
    }

    @Test
    fun exceedsRedirectLimit_returnsExceeded() {
        // Arrange – 6 redirects > default 5
        repeat(6) { enqueueHead(302, mockUrl("/$it")) }

        // Act
        val result = LinkChecker.resolveUrl(mockUrl("/start"))

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Failure)
        result as LinkChecker.UrlResolutionResult.Failure
        assertEquals(LinkChecker.UrlResolutionResult.ErrorCause.EXCEEDED_REDIRECT_LIMIT, result.error)
        assertTrue(result.redirectCount > 5)
    }

    @Test
    fun missingLocationHeader_returnsUnrecoverableLocation() {
        // Arrange
        enqueueHead(302)              // 302 without Location

        // Act
        val result = LinkChecker.resolveUrl(mockUrl("/broken"))

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Failure)
        result as LinkChecker.UrlResolutionResult.Failure
        assertEquals(LinkChecker.UrlResolutionResult.ErrorCause.UNRECOVERABLE_LOCATION, result.error)
    }

    @Test
    fun invalidUrl_returnsInvalidUrl() {
        // Act
        val result = LinkChecker.resolveUrl("ht!tp:// bad url")

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Failure)
        result as LinkChecker.UrlResolutionResult.Failure
        assertEquals(LinkChecker.UrlResolutionResult.ErrorCause.INVALID_URL, result.error)
    }

    @Test
    fun finalStatus403_stillSuccess() {
        // Arrange
        enqueueHead(403)

        // Act
        val result = LinkChecker.resolveUrl(mockUrl("/forbidden"))

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Success)
        result as LinkChecker.UrlResolutionResult.Success
        assertEquals(403, result.finalStatusCode)
    }

    @Test
    fun headNotAllowed405_stillSuccess() {
        // Arrange
        enqueueHead(405)

        // Act
        val result = LinkChecker.resolveUrl(mockUrl("/head-not-allowed"))

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Success)
        result as LinkChecker.UrlResolutionResult.Success
        assertEquals(405, result.finalStatusCode)
    }

    @Test
    fun networkException_returnsNetworkError() {
        // Arrange – shut server before making request
        server.shutdown()
        val unreachable = mockUrl("/down")

        // Act
        val result = LinkChecker.resolveUrl(unreachable)

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Failure)
        result as LinkChecker.UrlResolutionResult.Failure
        assertEquals(LinkChecker.UrlResolutionResult.ErrorCause.NETWORK_EXCEPTION, result.error)
    }


    @Test
    fun garbageLocation_returnsUnrecoverableLocation() {
        enqueueHead(302, null)             // no Location header
        val result = LinkChecker.resolveUrl(mockUrl("/start"))
        assertTrue(result is LinkChecker.UrlResolutionResult.Failure)
        result as LinkChecker.UrlResolutionResult.Failure
        assertEquals(
            LinkChecker.UrlResolutionResult.ErrorCause.UNRECOVERABLE_LOCATION,
            result.error
        )
    }

    @Test
    fun customRedirectLimitIsHonoured() {
        // Arrange – 2 redirects but limit = 1
        val hop1 = mockUrl("/hop1")
        enqueueHead(302, hop1)
        enqueueHead(302, mockUrl("/hop2"))

        // Act
        val result = LinkChecker.resolveUrl(mockUrl("/start"), maxRedirects = 1)

        // Assert
        assertTrue(result is LinkChecker.UrlResolutionResult.Failure)
        result as LinkChecker.UrlResolutionResult.Failure
        assertEquals(LinkChecker.UrlResolutionResult.ErrorCause.EXCEEDED_REDIRECT_LIMIT, result.error)
        assertEquals(2, result.redirectCount)
    }
}
