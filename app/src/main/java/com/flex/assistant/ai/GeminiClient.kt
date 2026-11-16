package com.flex.assistant.ai

import com.flex.assistant.FlexConfig
import com.flex.assistant.settings.FlexSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class VerifyResult(val ok: Boolean, val code: Int, val message: String)

class GeminiClient(private val settings: FlexSettings) {

    private fun requireKey(): String {
        val k = settings.apiKey
        if (k.isBlank()) throw FlexException("No Gemini API key. Open Settings, paste your key and press Save.")
        return k
    }

    suspend fun generate(contents: JSONArray, system: String, tools: JSONArray?, model: String): JSONObject =
        withContext(Dispatchers.IO) {
            val key = requireKey()
            var r = post(key, model, body(contents, system, tools, model, true), 40000)
            if (r.first == 400 && r.second.contains("thinking", ignoreCase = true)) {
                r = post(key, model, body(contents, system, tools, model, false), 40000)
            }
            if (r.first in 200..299) JSONObject(r.second) else throw errorFor(model, r)
        }

    /** Real internet search through Gemini's Google Search grounding. */
    suspend fun search(query: String): JSONObject = withContext(Dispatchers.IO) {
        val key = requireKey()
        val model = settings.chatModel
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", query)))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val r = post(key, model, body, 45000)
        if (r.first !in 200..299) throw errorFor(model, r)
        val cand = JSONObject(r.second).optJSONArray("candidates")?.optJSONObject(0)
        val text = StringBuilder()
        val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
        if (parts != null) for (i in 0 until parts.length()) text.append(parts.getJSONObject(i).optString("text"))
        val sources = JSONArray()
        val chunks = cand?.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        if (chunks != null) for (i in 0 until minOf(chunks.length(), 5)) {
            val w = chunks.getJSONObject(i).optJSONObject("web") ?: continue
            sources.put(JSONObject().put("title", w.optString("title")).put("url", w.optString("uri")))
        }
        JSONObject().put("success", true).put("answer", text.toString().trim()).put("sources", sources)
    }

    /** Returns image bytes and mime type, or null if the model returned no image. */
    suspend fun generateImage(prompt: String, model: String): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        val key = requireKey()
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))
        val r = post(key, model, body, 90000)
        if (r.first !in 200..299) throw errorFor(model, r)
        val parts = JSONObject(r.second).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return@withContext null
        for (i in 0 until parts.length()) {
            val inl = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
            val data = inl.optString("data")
            if (data.isNotEmpty()) {
                return@withContext android.util.Base64.decode(data, android.util.Base64.DEFAULT) to inl.optString("mimeType", "image/png")
            }
        }
        null
    }

    /** Checks that the key works and the model exists for this account. */
    suspend fun verifyModel(model: String): VerifyResult = withContext(Dispatchers.IO) {
        val key = settings.apiKey
        if (key.isBlank()) return@withContext VerifyResult(false, 0, "API key not configured")
        try {
            val conn = URL("${FlexConfig.API_BASE}/models/${model.removePrefix("models/")}").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("x-goog-api-key", key)
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code in 200..299) VerifyResult(true, code, "Available") else VerifyResult(false, code, errorMessage(text))
            } finally { conn.disconnect() }
        } catch (e: IOException) {
            VerifyResult(false, 0, "Network unavailable: ${e.message}")
        }
    }

    private fun body(contents: JSONArray, system: String, tools: JSONArray?, model: String, thinking: Boolean): JSONObject {
        val b = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        if (tools != null) b.put("tools", tools)
        if (thinking) {
            val tc = if (model.startsWith("gemini-3")) JSONObject().put("thinkingLevel", "low")
            else JSONObject().put("thinkingBudget", 0)
            b.put("generationConfig", JSONObject().put("thinkingConfig", tc))
        }
        return b
    }

    private fun post(key: String, model: String, body: JSONObject, readTimeout: Int): Pair<Int, String> {
        val conn = URL("${FlexConfig.API_BASE}/models/${model.removePrefix("models/")}:generateContent").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = readTimeout
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    private fun errorFor(model: String, r: Pair<Int, String>): FlexException {
        val msg = errorMessage(r.second)
        return when (r.first) {
            400 -> if (msg.contains("API key", true)) FlexException("Invalid API key. Check it in Settings.")
                   else FlexException("Request rejected (400) by $model: $msg")
            401, 403 -> FlexException("API key rejected (${r.first}). Check it in Settings.")
            404 -> FlexException("Model '$model' is not available to your API key, so this feature cannot run. Open Settings > Models and choose another model (for example gemini-2.5-flash).")
            429 -> FlexException("Quota exceeded for $model. Wait a moment or switch model in Settings.")
            else -> FlexException("Gemini error ${r.first} ($model): $msg")
        }
    }

    private fun errorMessage(raw: String): String = try {
        JSONObject(raw).getJSONObject("error").getString("message")
    } catch (e: Exception) { raw.take(200) }
}
