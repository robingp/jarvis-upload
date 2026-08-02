package com.jarvis.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {

    // You can change this model name later if you like (e.g. gemini-1.5-flash).
    private const val MODEL = "gemini-2.0-flash"
    private const val URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key="

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * history: list of Pair(role, text) where role is "user" or "model".
     * Returns the model's raw text reply, or a string starting with "ERROR:" on failure.
     */
    suspend fun generate(
        apiKey: String,
        systemPrompt: String,
        history: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "ERROR: No API key. Open Settings and paste your free Gemini key."

        try {
            val contents = JSONArray()
            for ((role, text) in history) {
                val parts = JSONArray().put(JSONObject().put("text", text))
                contents.put(JSONObject().put("role", role).put("parts", parts))
            }

            val body = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemPrompt))
                    )
                )
                .put("contents", contents)

            val request = Request.Builder()
                .url(URL + apiKey)
                .post(body.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val msg = try {
                        JSONObject(raw).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP ${resp.code}"
                    }
                    return@withContext "ERROR: $msg"
                }
                return@withContext parseText(raw)
            }
        } catch (e: Exception) {
            return@withContext "ERROR: ${e.message ?: "network problem"}"
        }
    }

    private fun parseText(raw: String): String {
        return try {
            val cands = JSONObject(raw).optJSONArray("candidates") ?: return "ERROR: empty reply"
            if (cands.length() == 0) return "ERROR: empty reply"
            val parts = cands.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            sb.toString().trim()
        } catch (e: Exception) {
            "ERROR: could not read reply"
        }
    }
}
