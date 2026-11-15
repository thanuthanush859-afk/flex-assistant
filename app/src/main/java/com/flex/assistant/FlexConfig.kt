package com.flex.assistant

/** Central config. Change models here without touching any other file. */
object FlexConfig {
    const val CHAT_MODEL = "gemini-2.5-flash"
    const val LIVE_MODEL = "gemini-2.5-flash-native-audio-preview" // used in the voice part
    const val API_BASE = "https://generativelanguage.googleapis.com/v1beta"
    const val ASSISTANT_NAME = "FLEX AI"
    const val CREDIT = "Made by Thanush gowda. N"
    const val MAX_TOOL_STEPS = 4
}
