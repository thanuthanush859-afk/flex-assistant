package com.flex.assistant.school

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.flex.assistant.FlexApp
import com.flex.assistant.device.ContactResolver

/** School Mode: auto-replies to WhatsApp messages and to callers by SMS. It never talks on a call and never records. */
object SchoolModeManager {
    private var receiver: CallReceiver? = null
    private val lastReply = HashMap<String, Long>()
    private const val COOLDOWN_MS = 30 * 60 * 1000L

    fun setEnabled(app: FlexApp, on: Boolean) {
        app.settings.schoolMode = on
        if (on) register(app) else unregister(app)
        app.memory.addLog(if (on) "School Mode turned ON" else "School Mode turned OFF")
    }

    fun register(ctx: Context) {
        if (receiver != null) return
        val r = CallReceiver()
        receiver = r
        ContextCompat.registerReceiver(ctx.applicationContext, r,
            IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    fun unregister(ctx: Context) {
        receiver?.let { try { ctx.applicationContext.unregisterReceiver(it) } catch (e: Exception) { } }
        receiver = null
    }

    fun isTrusted(app: FlexApp, label: String): Boolean {
        val list = app.settings.trusted.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val l = label.lowercase()
        return list.any { l.contains(it) || it.filter { c -> c.isDigit() }.let { d -> d.length >= 7 && l.filter { c -> c.isDigit() }.endsWith(d.takeLast(10)) } }
    }

    /** True once per sender per 30 minutes so nobody gets spammed. */
    fun shouldReply(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastReply[key] ?: 0L
        if (now - last < COOLDOWN_MS) return false
        lastReply[key] = now
        return true
    }

    class CallReceiver : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING) return
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            if (number.isNullOrBlank()) return
            val app = FlexApp.instance
            if (!app.settings.schoolMode) return
            val name = ContactResolver(context).nameForNumber(number)
            if (isTrusted(app, number) || (name != null && isTrusted(app, name))) {
                app.memory.addLog("Call from trusted ${name ?: number}: left alone")
                return
            }
            if (!shouldReply("call:" + number.takeLast(10))) return
            val who = name ?: number
            if (app.settings.declineCalls && Build.VERSION.SDK_INT >= 28 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    @Suppress("MissingPermission")
                    context.getSystemService(TelecomManager::class.java).endCall()
                    app.memory.addLog("Declined call from $who")
                } catch (e: Exception) { app.memory.addLog("Could not decline call from $who: ${e.message}") }
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                    val text = app.settings.schoolReply
                    sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
                    app.memory.addLog("SMS sent to $who after their call")
                } catch (e: Exception) { app.memory.addLog("SMS to $who failed: ${e.message}") }
            } else app.memory.addLog("Call from $who: SMS permission is off, no reply sent")
        }
    }
}
