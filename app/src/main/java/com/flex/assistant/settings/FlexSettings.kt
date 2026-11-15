package com.flex.assistant.settings

import android.content.Context

class FlexSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("flex_prefs", Context.MODE_PRIVATE)

    /** Stored in app-private storage. Personal build only; never ship a key inside a public APK. */
    var apiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) { prefs.edit().putString("gemini_api_key", value.trim()).apply() }
}
