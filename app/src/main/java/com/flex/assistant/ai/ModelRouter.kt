package com.flex.assistant.ai

import com.flex.assistant.settings.FlexSettings

/** One place that decides which Gemini model handles which job. */
class ModelRouter(private val s: FlexSettings) {
    fun chat() = s.chatModel
    fun live() = s.liveModel
    fun pro() = s.proModel
    fun imageFast() = s.imageFastModel
    fun imagePro() = s.imageProModel

    fun forText(text: String): String {
        val t = text.lowercase()
        val coding = listOf("code", "kotlin", "python", "gradle", "debug", "compile", "script", "algorithm", "build error")
            .any { t.contains(it) }
        return if (coding) pro() else chat()
    }
}
