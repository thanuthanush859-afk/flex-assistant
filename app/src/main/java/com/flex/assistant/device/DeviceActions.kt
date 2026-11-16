package com.flex.assistant.device

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.flex.assistant.notifications.ReminderReceiver
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Real phone actions: calls, WhatsApp, YouTube, email and reminders. */
class DeviceActions(private val ctx: Context) {
    private val contacts = ContactResolver(ctx)

    private fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun ok(m: String): JSONObject = JSONObject().put("success", true).put("message", m)
    fun fail(m: String): JSONObject = JSONObject().put("success", false).put("message", m)
    private fun start(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    private fun installed(pkg: String) = try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }

    private fun resolve(q: String, email: Boolean): Pair<ContactResolver.Hit?, String?> {
        val query = q.trim()
        if (!email && query.any { it.isDigit() } && query.none { it.isLetter() }) return ContactResolver.Hit(query, query) to null
        if (email && query.contains("@")) return ContactResolver.Hit(query, query) to null
        if (!has(Manifest.permission.READ_CONTACTS)) return null to "Contacts permission is off. Allow it in Settings > Permissions."
        var hits = if (email) contacts.emails(query) else contacts.phones(query)
        if (hits.isEmpty()) return null to "No contact matches '$query'."
        val exact = hits.filter { it.name.equals(query, true) }
        if (exact.isNotEmpty()) hits = exact
        val names = hits.map { it.name }.distinct()
        if (names.size > 1) return null to "Several contacts match '$query': ${names.take(5).joinToString()}. Ask the user which one."
        return hits.first() to null
    }

    fun call(q: String): JSONObject {
        val (hit, err) = resolve(q, false)
        if (hit == null) return fail(err ?: "Contact not found.")
        val uri = Uri.parse("tel:" + Uri.encode(hit.value))
        return if (has(Manifest.permission.CALL_PHONE)) {
            start(Intent(Intent.ACTION_CALL, uri)); ok("Calling ${hit.name}.")
        } else {
            start(Intent(Intent.ACTION_DIAL, uri))
            ok("Opened the dialer for ${hit.name}. Calling permission is off, so the user must press the call button.")
        }
    }

    suspend fun whatsapp(q: String, message: String): JSONObject {
        val (hit, err) = resolve(q, false)
        if (hit == null) return fail(err ?: "Contact not found.")
        var num = hit.value.filter { it.isDigit() }
        if (num.length == 11 && num.startsWith("0")) num = num.drop(1)
        if (num.length == 10) num = "91$num"
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { installed(it) } ?: return fail("WhatsApp is not installed.")
        start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=" + Uri.encode(message))).setPackage(pkg))
        val acc = FlexAccessibilityService.instance
            ?: return ok("WhatsApp is open on ${hit.name}'s chat with the message typed. Accessibility is off, so the user must press Send.")
        val pressed = acc.waitAndClick(listOf("Send"), true, 9000)
        return if (pressed) ok("Pressed Send in WhatsApp for ${hit.name}. Delivery cannot be verified.")
        else fail("Opened the chat and typed the message, but could not find the Send button. The user must press Send.")
    }

    suspend fun youtube(query: String): JSONObject {
        val pkg = "com.google.android.youtube"
        if (!installed(pkg)) return fail("YouTube is not installed.")
        try {
            start(Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra("query", query))
        } catch (e: Exception) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))).setPackage(pkg))
        }
        val acc = FlexAccessibilityService.instance
            ?: return ok("Opened YouTube results for '$query'. Accessibility is off, so the user must tap a video.")
        delay(2500)
        val played = acc.waitAndClick(listOf("play video"), false, 9000)
        return if (played) ok("Opened YouTube for '$query' and tapped the first video.")
        else ok("Opened YouTube results for '$query' but could not tap a video; the user can tap one.")
    }

    suspend fun email(to: String, subject: String, body: String): JSONObject {
        val (hit, err) = resolve(to, true)
        if (hit == null) return fail(err ?: "Recipient not found.")
        if (!installed("com.google.android.gm")) return fail("Gmail is not installed.")
        start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(hit.value))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
            .setPackage("com.google.android.gm"))
        val acc = FlexAccessibilityService.instance
            ?: return ok("Gmail draft to ${hit.value} is open. Accessibility is off, so the user must press Send.")
        val pressed = acc.waitAndClick(listOf("Send"), true, 9000)
        return if (pressed) ok("Pressed Send in Gmail to ${hit.value}.") else fail("Draft is ready but the Send button was not found; the user must press Send.")
    }

    fun reminder(text: String, minutes: Int): JSONObject {
        val m = minutes.coerceAtLeast(1)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, (System.currentTimeMillis() % 100000).toInt(),
            Intent(ctx, ReminderReceiver::class.java).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + m * 60_000L, pi)
        return ok("Reminder set for $m minute(s) from now.")
    }
}
