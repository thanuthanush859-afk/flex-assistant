package com.flex.assistant.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flex.assistant.ai.ConversationManager
import com.flex.assistant.ai.FlexBrain
import com.flex.assistant.ai.FlexException
import com.flex.assistant.ai.GeminiClient
import com.flex.assistant.ai.ToolRouter
import com.flex.assistant.device.AppInfo
import com.flex.assistant.device.AppLauncher
import com.flex.assistant.device.LaunchResult
import com.flex.assistant.memory.MemoryStore
import com.flex.assistant.settings.FlexSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class ChatMessage(val fromUser: Boolean, val text: String)

data class UiState(
    val status: String = "FLEX is ready.",
    val busy: Boolean = false,
    val hasKey: Boolean = false,
    val standby: Boolean = false,
    val messages: List<ChatMessage> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = FlexSettings(app)
    private val memory = MemoryStore(app)
    private val launcher = AppLauncher(app)
    private val conversation = ConversationManager(memory)
    private val brain = FlexBrain(GeminiClient(settings), conversation, ToolRouter(launcher, memory))

    var state by mutableStateOf(
        UiState(
            hasKey = settings.apiKey.isNotBlank(),
            messages = memory.allMessages().map { ChatMessage(it.role == "user", it.text) }
        )
    )
        private set

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    fun loadApps() {
        viewModelScope.launch { apps = withContext(Dispatchers.Default) { launcher.installedApps() } }
    }

    fun openApp(app: AppInfo) {
        val msg = when (val r = launcher.openPackage(app.packageName)) {
            is LaunchResult.Launched -> "Opened ${r.label}."
            is LaunchResult.NotInstalled -> "${app.label} is not installed."
            is LaunchResult.Failed -> "Could not open ${r.label}: ${r.reason}"
        }
        state = state.copy(status = msg)
    }

    fun saveKey(key: String) {
        settings.apiKey = key
        state = state.copy(hasKey = settings.apiKey.isNotBlank())
    }

    fun setStandby(on: Boolean) {
        state = state.copy(standby = on, status = if (on) "Standby." else "FLEX is ready.")
    }

    fun clearHistory() {
        memory.clearMessages()
        conversation.clearContext()
        state = state.copy(messages = emptyList(), status = "Conversation history cleared.")
    }

    fun onOrbTap() { state = state.copy(status = "Voice is added in the next update.") }
    fun onCamTap() { state = state.copy(status = "Vision mode is added in the next update.") }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || state.busy || state.standby) return
        state = state.copy(busy = true, status = "Thinking...",
            messages = state.messages + ChatMessage(true, t))
        viewModelScope.launch {
            var reply: String
            var status = "FLEX is ready."
            try {
                reply = brain.process(t)
            } catch (e: FlexException) {
                reply = "Error: ${e.message}"
            } catch (e: IOException) {
                reply = "Offline or network error. Check your internet connection."
                status = "Offline."
            } catch (e: Exception) {
                reply = "Unexpected error: ${e.message}"
            }
            state = state.copy(busy = false, status = status,
                messages = state.messages + ChatMessage(false, reply))
        }
    }
}
