package com.flex.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.flex.assistant.FlexApp
import com.flex.assistant.FlexRuntime
import com.flex.assistant.VoiceState
import com.flex.assistant.ai.GeminiLiveClient
import com.flex.assistant.ai.Persona
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Owns the Gemini Live session: microphone in, native audio out, tools, timeouts and reconnects. */
class VoiceController(private val app: FlexApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var live: GeminiLiveClient? = null
    private var audio: LiveAudio? = null
    private var timeoutJob: Job? = null
    @Volatile private var userStopped = false
    private var retries = 0
    private var everReady = false
    private val userBuf = StringBuilder()
    private val modelBuf = StringBuilder()
    private val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isActive(): Boolean {
        val s = FlexRuntime.voiceState.value
        return s != VoiceState.IDLE && s != VoiceState.ERROR
    }

    @Synchronized
    fun start() {
        if (isActive()) return
        if (app.settings.apiKey.isBlank()) { fail("No API key. Open Settings, paste your Gemini key and press Save."); return }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Microphone permission is off. Allow it in Settings > Permissions."); return
        }
        userStopped = false
        retries = 0
        everReady = false
        connect()
    }

    private fun connect() {
        FlexRuntime.voiceError.value = null
        FlexRuntime.voiceState.value = VoiceState.CONNECTING
        val session = Session()
        live = session.client
        val facts = app.memory.facts(30).joinToString("\n") { "- " + it.text }
        session.client.connect(app.settings.apiKey, app.models.live(), Persona.text(facts), app.tools.declarations(), app.settings.voiceName)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(12000)
            if (FlexRuntime.voiceState.value == VoiceState.CONNECTING) {
                fail("Could not connect to '${app.models.live()}' within 12 seconds. Check internet, API key and the live model name in Settings.")
            }
        }
    }

    @Synchronized
    fun stop() {
        userStopped = true
        teardown()
        FlexRuntime.voiceError.value = null
        FlexRuntime.voiceState.value = VoiceState.IDLE
    }

    private fun fail(msg: String) {
        teardown()
        FlexRuntime.voiceError.value = msg
        FlexRuntime.voiceState.value = VoiceState.ERROR
    }

    private fun teardown() {
        timeoutJob?.cancel()
        val a = audio
        val l = live
        audio = null
        live = null
        a?.stop()
        l?.close()
        try { am.mode = AudioManager.MODE_NORMAL; am.isSpeakerphoneOn = false } catch (e: Exception) { }
        FlexRuntime.level.value = 0f
    }

    fun sendFrame(jpeg: ByteArray) { live?.let { if (it.ready) it.sendImage(jpeg) } }
    fun sendText(text: String) { live?.let { if (it.ready) it.sendText(text) } }

    private fun flushTranscripts() {
        val u = userBuf.toString().trim()
        val m = modelBuf.toString().trim()
        userBuf.setLength(0)
        modelBuf.setLength(0)
        if (u.isNotEmpty()) app.memory.addMessage("user", u)
        if (m.isNotEmpty()) app.memory.addMessage("model", m)
    }

    private inner class Session : GeminiLiveClient.Listener {
        val client: GeminiLiveClient = GeminiLiveClient(this)
        private fun stale() = live !== client

        override fun onSetupComplete() {
            if (stale()) return
            timeoutJob?.cancel()
            val a = LiveAudio { bytes, rms ->
                if (!stale()) {
                    val st = FlexRuntime.voiceState.value
                    // while FLEX is speaking only loud speech passes (barge-in) so it does not hear itself
                    if (!(st == VoiceState.SPEAKING && rms < 0.10f)) {
                        if (st != VoiceState.SPEAKING) FlexRuntime.level.value = rms
                        client.sendAudio(bytes)
                    }
                }
            }
            audio = a
            try { am.mode = AudioManager.MODE_IN_COMMUNICATION; am.isSpeakerphoneOn = true } catch (e: Exception) { }
            val err = a.startMic()
            if (err != null) { fail(err); return }
            everReady = true
            retries = 0
            FlexRuntime.voiceState.value = VoiceState.LISTENING
        }

        override fun onAudio(data: ByteArray, rate: Int) {
            if (stale()) return
            FlexRuntime.voiceState.value = VoiceState.SPEAKING
            audio?.play(data, rate)
        }

        override fun onInterrupted() {
            if (stale()) return
            audio?.flush()
            FlexRuntime.voiceState.value = VoiceState.LISTENING
        }

        override fun onTurnComplete() {
            if (stale()) return
            flushTranscripts()
            scope.launch {
                while (audio?.pending() == true) delay(80)
                delay(250)
                if (!stale() && FlexRuntime.voiceState.value == VoiceState.SPEAKING) FlexRuntime.voiceState.value = VoiceState.LISTENING
            }
        }

        override fun onTranscript(user: Boolean, text: String) {
            if (stale()) return
            if (user) {
                userBuf.append(text)
                if (FlexRuntime.voiceState.value == VoiceState.LISTENING) FlexRuntime.voiceState.value = VoiceState.THINKING
            } else modelBuf.append(text)
        }

        override fun onToolCall(id: String, name: String, args: JSONObject) {
            if (stale()) return
            FlexRuntime.voiceState.value = VoiceState.EXECUTING
            scope.launch {
                val res = withTimeoutOrNull(25000) { app.tools.execute(name, args) }
                    ?: JSONObject().put("success", false).put("message", "The action timed out after 25 seconds.")
                if (!stale()) {
                    client.sendToolResponse(id, name, res)
                    FlexRuntime.voiceState.value = VoiceState.THINKING
                }
            }
        }

        override fun onClosed(reason: String, error: Boolean) {
            if (stale()) return
            teardown()
            if (userStopped) { FlexRuntime.voiceState.value = VoiceState.IDLE; return }
            if (error && everReady && retries < 2) {
                retries++
                FlexRuntime.voiceState.value = VoiceState.CONNECTING
                scope.launch { delay(1200); if (!userStopped) connect() }
                return
            }
            if (error) fail(reason) else FlexRuntime.voiceState.value = VoiceState.IDLE
        }
    }
}
