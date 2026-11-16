package com.flex.assistant.ai

import android.util.Base64
import com.flex.assistant.FlexConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Real-time two-way audio with Gemini Live over a WebSocket. */
class GeminiLiveClient(private val l: Listener) {

    interface Listener {
        fun onSetupComplete()
        fun onAudio(data: ByteArray, rate: Int)
        fun onInterrupted()
        fun onTurnComplete()
        fun onTranscript(user: Boolean, text: String)
        fun onToolCall(id: String, name: String, args: JSONObject)
        fun onClosed(reason: String, error: Boolean)
    }

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var ws: WebSocket? = null
    private var reported = false

    @Volatile var ready = false
        private set

    private fun report(msg: String, error: Boolean) {
        synchronized(this) { if (reported) return; reported = true }
        ready = false
        l.onClosed(msg, error)
    }

    fun connect(apiKey: String, model: String, system: String, tools: JSONArray, voice: String) {
        val req = Request.Builder().url("${FlexConfig.LIVE_WS}?key=$apiKey").build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setup(model, system, tools, voice).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val http = response?.let { " (HTTP ${it.code})" } ?: ""
                report("Connection failed: ${t.message ?: t.javaClass.simpleName}$http", true)
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                report("Closed by server ($code) $reason", code != 1000)
                webSocket.close(1000, null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                report("Closed ($code) $reason", code != 1000)
            }
        })
    }

    private fun setup(model: String, system: String, tools: JSONArray, voice: String): JSONObject {
        val speech = JSONObject()
            .put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))
            .put("languageCode", "en-IN")
        val gen = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", speech)
        return JSONObject().put("setup", JSONObject()
            .put("model", "models/" + model.removePrefix("models/"))
            .put("generationConfig", gen)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("tools", tools)
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject()))
    }

    private fun handle(raw: String) {
        val m = try { JSONObject(raw) } catch (e: Exception) { return }
        if (m.has("setupComplete")) { ready = true; l.onSetupComplete(); return }
        val sc = m.optJSONObject("serverContent")
        if (sc != null) {
            if (sc.optBoolean("interrupted")) l.onInterrupted()
            val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) for (i in 0 until parts.length()) {
                val inl = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                val mime = inl.optString("mimeType", "audio/pcm;rate=24000")
                val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                val data = Base64.decode(inl.optString("data"), Base64.DEFAULT)
                if (data.isNotEmpty()) l.onAudio(data, rate)
            }
            val inT = sc.optJSONObject("inputTranscription")?.optString("text").orEmpty()
            if (inT.isNotEmpty()) l.onTranscript(true, inT)
            val outT = sc.optJSONObject("outputTranscription")?.optString("text").orEmpty()
            if (outT.isNotEmpty()) l.onTranscript(false, outT)
            if (sc.optBoolean("turnComplete")) l.onTurnComplete()
        }
        val calls = m.optJSONObject("toolCall")?.optJSONArray("functionCalls")
        if (calls != null) for (i in 0 until calls.length()) {
            val c = calls.getJSONObject(i)
            l.onToolCall(c.optString("id"), c.getString("name"), c.optJSONObject("args") ?: JSONObject())
        }
        if (m.has("goAway")) report("The server is ending this session.", true)
    }

    private fun send(o: JSONObject) { if (ready) ws?.send(o.toString()) }

    fun sendAudio(pcm: ByteArray) = send(JSONObject().put("realtimeInput", JSONObject().put("audio",
        JSONObject().put("data", Base64.encodeToString(pcm, Base64.NO_WRAP)).put("mimeType", "audio/pcm;rate=16000"))))

    fun sendImage(jpeg: ByteArray) = send(JSONObject().put("realtimeInput", JSONObject().put("video",
        JSONObject().put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)).put("mimeType", "image/jpeg"))))

    fun sendText(text: String) = send(JSONObject().put("realtimeInput", JSONObject().put("text", text)))

    fun sendToolResponse(id: String, name: String, response: JSONObject) = send(JSONObject().put("toolResponse",
        JSONObject().put("functionResponses", JSONArray().put(
            JSONObject().put("id", id).put("name", name).put("response", response)))))

    fun close() {
        ready = false
        synchronized(this) { reported = true }
        try { ws?.close(1000, "bye") } catch (e: Exception) { }
        ws = null
    }
}
