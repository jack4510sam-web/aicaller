package com.aicaller.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin, swappable client for whatever LLM backend the user configures in Settings.
 * Ships pointed at the Anthropic Messages API format by default, but the endpoint,
 * model and key are all user-configurable — point it at any compatible backend.
 *
 * NOTE: shipping a real API key inside the app is unsafe (it can be extracted from
 * the APK). For production use, proxy these calls through your own backend server
 * instead of calling the LLM directly from the device. See README.md.
 */
class LlmClient(
    private val apiKey: String,
    private val endpoint: String,
    private val model: String
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Turn(val role: String, val text: String) // role: "caller" or "assistant"

    /**
     * Sends the conversation so far plus a system prompt describing the AI's job,
     * and returns the assistant's next spoken line.
     */
    suspend fun nextReply(
        systemPrompt: String,
        history: List<Turn>
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No LLM API key configured"))
        }
        runCatching {
            val messages = JSONArray().apply {
                history.forEach { turn ->
                    put(JSONObject().apply {
                        put("role", if (turn.role == "caller") "user" else "assistant")
                        put("content", turn.text)
                    })
                }
            }

            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 200)
                put("system", systemPrompt)
                put("messages", messages)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body)
                .build()

            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("LLM request failed: ${resp.code} ${resp.message}")
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json.optJSONArray("content")
                content?.optJSONObject(0)?.optString("text")
                    ?: throw IllegalStateException("Unexpected LLM response shape")
            }
        }
    }
}
