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
import com.flex.assistant.device.AppLauncher
import com.flex.assistant.settings.FlexSettings
import kotlinx.coroutines.launch
import java.io.IOException

data class ChatMessage(val fromUser: Boolean, val text: String)

data class UiState(
    val status: String = "FLEX is ready.",
    val busy: Boolean = false,
    val hasKey: Boolean = false,
    val messages: List<ChatMessage> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = FlexSettings(app)
    private val brain = FlexBrain(
        GeminiClient(settings), ConversationManager(), ToolRouter(AppLauncher(app))
    )

    var state by mutableStateOf(UiState(hasKey = settings.apiKey.isNotBlank()))
        private set

    fun saveKey(key: String) {
        settings.apiKey = key
        state = state.copy(hasKey = settings.apiKey.isNotBlank())
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || state.busy) return
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
