package com.flex.assistant

import kotlinx.coroutines.flow.MutableStateFlow

enum class VoiceState { IDLE, CONNECTING, LISTENING, THINKING, EXECUTING, SPEAKING, ERROR }

/** Shared live state that the UI, the voice service and the tools all read. */
object FlexRuntime {
    val voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceError = MutableStateFlow<String?>(null)
    val level = MutableStateFlow(0f)
    val requestedPage = MutableStateFlow<String?>(null)
    val resumeTick = MutableStateFlow(0)
}
