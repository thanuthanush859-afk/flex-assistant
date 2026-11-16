package com.flex.assistant.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flex.assistant.FlexApp
import com.flex.assistant.ai.FlexException
import com.flex.assistant.device.AppInfo
import com.flex.assistant.device.LaunchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

data class ChatMessage(val fromUser: Boolean, val text: String)

data class UiState(
    val note: String? = null,
    val busy: Boolean = false,
    val hasKey: Boolean = false,
    val standby: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val keyStatus: String = "",
    val testLines: List<String> = emptyList(),
    val testing: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = FlexApp.instance

    private fun loadMessages() = a.memory.allMessages().map { ChatMessage(it.role == "user", it.text) }

    var state by mutableStateOf(
        UiState(
            hasKey = a.settings.apiKey.isNotBlank(),
            messages = loadMessages(),
            keyStatus = if (a.settings.apiKey.isBlank()) "API key not configured" else "API key saved"
        )
    )
        private set

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    fun reloadMessages() { state = state.copy(messages = loadMessages()) }

    fun loadApps() {
        viewModelScope.launch { apps = withContext(Dispatchers.Default) { a.launcher.installedApps() } }
    }

    fun openApp(app: AppInfo) {
        val msg = when (val r = a.launcher.openPackage(app.packageName)) {
            is LaunchResult.Launched -> "Opened ${r.label}."
            is LaunchResult.NotInstalled -> "${app.label} is not installed."
            is LaunchResult.Failed -> "Could not open ${r.label}: ${r.reason}"
        }
        state = state.copy(note = msg)
    }

    fun setStandby(on: Boolean) {
        if (on) a.voice.stop()
        state = state.copy(standby = on, note = null)
    }

    fun clearHistory() {
        a.memory.clearMessages()
        a.brain.clearContext()
        state = state.copy(messages = emptyList(), note = "Conversation history cleared.")
    }

    fun saveKey(key: String) {
        val k = key.trim()
        if (k.isEmpty()) { state = state.copy(keyStatus = "Paste your API key first."); return }
        a.settings.apiKey = k
        val saved = a.settings.apiKey == k
        state = state.copy(hasKey = saved, keyStatus = if (saved) "API key saved. Testing connection..." else "Could not save the key on this phone.")
        if (saved) testConnection()
    }

    fun saveModels(chat: String, live: String, pro: String, imgFast: String, imgPro: String, voice: String) {
        val s = a.settings
        if (chat.isNotBlank()) s.chatModel = chat
        if (live.isNotBlank()) s.liveModel = live
        if (pro.isNotBlank()) s.proModel = pro
        if (imgFast.isNotBlank()) s.imageFastModel = imgFast
        if (imgPro.isNotBlank()) s.imageProModel = imgPro
        if (voice.isNotBlank()) s.voiceName = voice
        state = state.copy(keyStatus = "Models saved. Press Test connection to check them.")
    }

    fun testConnection() {
        if (state.testing) return
        state = state.copy(testing = true, testLines = emptyList(), keyStatus = "Testing connection...")
        viewModelScope.launch {
            val s = a.settings
            val models = listOf("Chat" to s.chatModel, "Live voice" to s.liveModel, "Coding (Pro)" to s.proModel,
                "Image (fast)" to s.imageFastModel, "Image (pro)" to s.imageProModel)
            val lines = ArrayList<String>()
            var headline = "Connected: key works and chat model is available"
            for ((label, model) in models) {
                val r = a.gemini.verifyModel(model)
                if (r.code == 0) { headline = r.message; lines.add("$label ($model): ${r.message}"); break }
                if (r.code == 400 || r.code == 401 || r.code == 403) { headline = "Invalid API key"; lines.add("$label: ${r.message}"); break }
                if (r.code == 429) { headline = "Quota exceeded"; lines.add("$label: ${r.message}"); break }
                lines.add("$label ($model): " + if (r.ok) "available" else "NOT available (${r.code})")
                if (label == "Chat" && !r.ok) headline = "Key works, but the chat model is not available"
            }
            state = state.copy(testing = false, testLines = lines, keyStatus = headline)
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || state.busy || state.standby) return
        state = state.copy(busy = true, note = null, messages = state.messages + ChatMessage(true, t))
        viewModelScope.launch {
            var reply: String
            try {
                reply = withTimeout(60_000) { withContext(Dispatchers.Default) { a.brain.process(t) } }
            } catch (e: TimeoutCancellationException) {
                reply = "No answer after 60 seconds. Check your internet and the chat model in Settings."
            } catch (e: FlexException) {
                reply = "Error: ${e.message}"
            } catch (e: IOException) {
                reply = "Network problem: ${e.message}. Check your internet connection."
            } catch (e: Exception) {
                reply = "Unexpected error: ${e.message}"
            }
            state = state.copy(busy = false, messages = state.messages + ChatMessage(false, reply))
        }
    }
}
