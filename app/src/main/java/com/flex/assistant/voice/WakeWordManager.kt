package com.flex.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Optional "A Flex" listener. It only runs when the user turns Wake word on, and its notification is always visible. */
class WakeWordManager(private val ctx: Context, private val onWake: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var running = false

    fun start() {
        main.post {
            if (running) return@post
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return@post
            running = true
            listen()
        }
    }

    fun stop() {
        main.post {
            running = false
            rec?.destroy()
            rec = null
        }
    }

    private fun listen() {
        if (!running) return
        rec?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) { check(partialResults) }
            override fun onResults(results: Bundle?) { if (!check(results)) restart() }
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { running = false; return }
                restart()
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        try { r.startListening(i) } catch (e: Exception) { restart() }
    }

    private fun restart() { if (running) main.postDelayed({ listen() }, 500) }

    private fun check(b: Bundle?): Boolean {
        val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        if (list.any { val t = it.lowercase(); t.contains("flex") || t.contains("flecks") }) {
            running = false
            rec?.destroy()
            rec = null
            onWake()
            return true
        }
        return false
    }
}
