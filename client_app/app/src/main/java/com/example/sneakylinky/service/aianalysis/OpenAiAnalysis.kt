package com.example.sneakylinky.service.aianalysis

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

/**
 * Represents the response structure returned from the phishing analysis API.
 *
 * @property phishingScore A float between 0.0 (not phishing) and 1.0 (very suspicious).
 * @property suspicionReasons List of reasons why the message or URL is considered suspicious.
 * @property recommendedActions List of recommended actions for the user (may be null).
 */
data class OpenAiAnalysis(
    @SerializedName("phishing_score")      val phishingScore: Float,
    @SerializedName("suspicion_reasons")   val suspicionReasons: List<String>,
    @SerializedName("recommended_actions") val recommendedActions: List<String>? = null
)

/**
 * UrlAnalyzer is a helper object for submitting text or URLs to a remote phishing detection service.
 * Uses OkHttp3 to send POST requests with JSON payload and parses the JSON response.
 */
object UrlAnalyzer {

    private val client = OkHttpClient()
    private val gson   = Gson()

    private val JSON: MediaType = MediaType.parse("application/json; charset=utf-8")
        ?: throw IllegalStateException("Cannot create MediaType")

    private const val ENDPOINT =
        "https://openai-proxy-901205359337.europe-west1.run.app/analyze-url"

    /**
     * Submits a message or URL to the phishing detection API and returns the parsed result.
     *
     * @param message The plain-text message or URL to analyze.
     * @return An [OpenAiAnalysis] object containing the results.
     * @throws IllegalStateException If the server returns a non-2xx response or the response is invalid.
     */
    @Throws(Exception::class)
    fun analyze(message: String): OpenAiAnalysis {
        if (!isValidMessage(message)) {
            throw IllegalArgumentException("Message must be non-empty and ≤ 600 characters")
        }
        val jsonBody = gson.toJson(mapOf("message" to message))
        val requestBody: RequestBody = RequestBody.create(JSON, jsonBody)

        val request = Request.Builder()
            .url(ENDPOINT)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body()?.string() ?: ""
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code()} – ${response.message()}")
            }

            return gson.fromJson(rawBody, OpenAiAnalysis::class.java)
        }
    }

    /**
     * Validates the message before attempting to analyze it.
     *
     * @param message The message or URL to check.
     * @return `true` if valid (non-empty and ≤ 600 characters), otherwise `false`.
     */
    fun isValidMessage(message: String): Boolean {
        return message.isNotBlank() && message.length <= 600
    }
}
