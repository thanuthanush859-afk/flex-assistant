package com.flex.assistant

/** Central defaults. Every model id can also be changed in Settings without rebuilding. */
object FlexConfig {
    const val DEFAULT_CHAT_MODEL = "gemini-3.8-flash"
    const val DEFAULT_LIVE_MODEL = "gemini-3.8-live"
    const val DEFAULT_PRO_MODEL = "gemini-3.1-pro-preview"
    const val DEFAULT_IMAGE_FAST = "gemini-3.1-flash-image"
    const val DEFAULT_IMAGE_PRO = "gemini-3-pro-image"
    const val DEFAULT_VOICE = "Aoede"
    const val API_BASE = "https://generativelanguage.googleapis.com/v1beta"
    const val LIVE_WS = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val ASSISTANT_NAME = "FLEX"
    const val CREDIT = "Made by Thanush gowda. N"
    const val MAX_TOOL_STEPS = 5
}
