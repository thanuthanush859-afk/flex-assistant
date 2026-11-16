package com.flex.assistant.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.flex.assistant.FlexConfig

class FlexSettings(context: Context) {
    private val ctx = context.applicationContext
    private val prefs: SharedPreferences = ctx.getSharedPreferences("flex_prefs", Context.MODE_PRIVATE)

    /** API key lives in Android Keystore-backed encrypted storage. */
    private val secure: SharedPreferences = try {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "flex_secure", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("flex_secure_fallback", Context.MODE_PRIVATE)
    }

    init {
        // migrate a key saved by an older version
        val old = prefs.getString("gemini_api_key", "") ?: ""
        if (old.isNotBlank() && apiKey.isBlank()) { apiKey = old }
        if (old.isNotBlank()) prefs.edit().remove("gemini_api_key").commit()
    }

    var apiKey: String
        get() = secure.getString("gemini_api_key", "") ?: ""
        set(v) { secure.edit().putString("gemini_api_key", v.trim()).commit() }

    private fun str(k: String, d: String) = prefs.getString(k, d) ?: d
    private fun put(k: String, v: String) { prefs.edit().putString(k, v.trim()).commit() }

    var chatModel: String
        get() = str("chat_model", FlexConfig.DEFAULT_CHAT_MODEL)
        set(v) = put("chat_model", v.removePrefix("models/"))
    var liveModel: String
        get() = str("live_model", FlexConfig.DEFAULT_LIVE_MODEL)
        set(v) = put("live_model", v.removePrefix("models/"))
    var proModel: String
        get() = str("pro_model", FlexConfig.DEFAULT_PRO_MODEL)
        set(v) = put("pro_model", v.removePrefix("models/"))
    var imageFastModel: String
        get() = str("image_fast", FlexConfig.DEFAULT_IMAGE_FAST)
        set(v) = put("image_fast", v.removePrefix("models/"))
    var imageProModel: String
        get() = str("image_pro", FlexConfig.DEFAULT_IMAGE_PRO)
        set(v) = put("image_pro", v.removePrefix("models/"))
    var voiceName: String
        get() = str("voice_name", FlexConfig.DEFAULT_VOICE)
        set(v) = put("voice_name", v)

    var wakeWord: Boolean
        get() = prefs.getBoolean("wake_word", false)
        set(v) { prefs.edit().putBoolean("wake_word", v).commit() }
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) { prefs.edit().putBoolean("onboarded", v).commit() }
    var schoolMode: Boolean
        get() = prefs.getBoolean("school_mode", false)
        set(v) { prefs.edit().putBoolean("school_mode", v).commit() }
    var declineCalls: Boolean
        get() = prefs.getBoolean("decline_calls", false)
        set(v) { prefs.edit().putBoolean("decline_calls", v).commit() }
    var schoolReply: String
        get() = str("school_reply", "Hi, this is FLEX, Thanush's assistant. Thanush is at school right now and will get back to you when he is free. If it is urgent, please call him.")
        set(v) = put("school_reply", v)
    var trusted: String
        get() = str("trusted", "")
        set(v) = put("trusted", v)
}
