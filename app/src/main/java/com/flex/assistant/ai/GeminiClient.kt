package com.flex.assistant.ai

import com.flex.assistant.FlexConfig
import com.flex.assistant.settings.FlexSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient(private val settings: FlexSettings) {

    suspend fun generate(
        contents: JSONArray,
        systemPrompt: String,
        tools: JSONArray?
    ): JSONObject = withContext(Dispatchers.IO) {
        val key = settings.apiKey
        if (key.isBlank()) throw FlexException("No Gemini API key set. Open Settings and paste your key.")

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)
        if (tools != null) body.put("tools", tools)

        val url = URL("${FlexConfig.API_BASE}/models/${FlexConfig.CHAT_MODEL}:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw FlexException("Gemini error $code: ${errorMessage(text)}")
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(raw: String): String = try {
        JSONObject(raw).getJSONObject("error").getString("message")
    } catch (e: Exception) { raw.take(200) }
}
