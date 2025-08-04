package com.example.sneakylinky.service.serveranalysis

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

//──────────────────────────────────────────────────────────────────────────────
//  CONSTANTS
//──────────────────────────────────────────────────────────────────────────────
private const val ENDPOINT =
    "https://sneaky-server-901205359337.europe-west1.run.app/v1/analyze-message"

private const val JSON_MEDIA_TYPE   = "application/json; charset=utf-8"
private const val JSON_KEY_MESSAGE  = "message"
private const val MAX_MESSAGE_LENGTH = 4000     // ← numeric value kept as a constant

//──────────────────────────────────────────────────────────────────────────────
//  DATA-TRANSFER OBJECT
//──────────────────────────────────────────────────────────────────────────────
/**
 * Represents the JSON structure returned from the phishing-analysis API.
 *
 * @property phishingScore      Score in the range **0.0 – 1.0**
 *                              (0 = benign, 1 = highly suspicious).
 * @property suspicionReasons   Human-readable reasons for classifying the message.
 * @property recommendedActions Optional remediation suggestions (may be *null*).
 */
data class MessageResult(
    @SerializedName("phishing_score")      val phishingScore: Float,
    @SerializedName("suspicion_reasons")   val suspicionReasons: List<String>,
    @SerializedName("recommended_actions") val recommendedActions: List<String>? = null
)

//──────────────────────────────────────────────────────────────────────────────
//  SERVICE OBJECT
//──────────────────────────────────────────────────────────────────────────────
/**
 * Utility object that submits a **message** to the remote phishing-detection API
 * and parses the JSON response into a [MessageResult] instance.
 */
object MessageAnalyzer {

    // Re-usable singletons
    private val gson   = Gson()
    private val client = OkHttpClient()
    private val JSON   = MediaType.parse(JSON_MEDIA_TYPE)
        ?: throw IllegalStateException("Unable to create MediaType")

    /**
     * Sends a single message to the API and returns the parsed result.
     *
     * @param message Plain-text message to analyze.
     * @return [MessageResult] parsed from the server’s JSON response.
     * @throws IllegalArgumentException if *message* is empty or exceeds 4 000 chars.
     * @throws IllegalStateException    if the server responds with non-2xx code
     *                                  or returns malformed JSON.
     */
    fun analyze(message: String): MessageResult {
        require(isValidMessage(message)) {
            "Message must be non-empty and ≤ $MAX_MESSAGE_LENGTH characters"
        }

        val requestBody = RequestBody.create(
            JSON,
            gson.toJson(mapOf(JSON_KEY_MESSAGE to message))
        )

        val request = Request.Builder()
            .url(ENDPOINT)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body()?.string() ?: ""
            check(response.isSuccessful) {
                "HTTP ${response.code()} — ${response.message()}"
            }
            return gson.fromJson(rawBody, MessageResult::class.java)
        }
    }

    /** Simple length-based validation; replace with real validation if needed. */
    private fun isValidMessage(message: String): Boolean =
        message.isNotBlank() && message.length <= MAX_MESSAGE_LENGTH
}
