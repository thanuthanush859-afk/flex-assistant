package com.flex.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.flex.assistant.ai.FlexBrain
import com.flex.assistant.ai.ConversationManager
import com.flex.assistant.ai.GeminiClient
import com.flex.assistant.ai.ModelRouter
import com.flex.assistant.ai.ToolRouter
import com.flex.assistant.device.AppLauncher
import com.flex.assistant.device.DeviceActions
import com.flex.assistant.memory.MemoryStore
import com.flex.assistant.school.SchoolModeManager
import com.flex.assistant.settings.FlexSettings
import com.flex.assistant.voice.VoiceController

class FlexApp : Application() {
    companion object {
        lateinit var instance: FlexApp
            private set
    }

    val settings by lazy { FlexSettings(this) }
    val memory by lazy { MemoryStore(this) }
    val launcher by lazy { AppLauncher(this) }
    val models by lazy { ModelRouter(settings) }
    val gemini by lazy { GeminiClient(settings) }
    val actions by lazy { DeviceActions(this) }
    val tools by lazy { ToolRouter(this) }
    val brain by lazy { FlexBrain(this, ConversationManager(memory)) }
    val voice by lazy { VoiceController(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("flex_live", "FLEX voice", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("flex_reminders", "FLEX reminders", NotificationManager.IMPORTANCE_DEFAULT))
        if (settings.schoolMode) SchoolModeManager.register(this)
    }
}
