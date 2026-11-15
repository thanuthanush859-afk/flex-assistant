package com.flex.assistant.device

import android.content.Context
import android.content.Intent

data class AppInfo(val label: String, val packageName: String)

sealed class LaunchResult {
    data class Launched(val label: String) : LaunchResult()
    data class NotInstalled(val query: String) : LaunchResult()
    data class Failed(val label: String, val reason: String) : LaunchResult()
}

class AppLauncher(context: Context) {
    private val ctx = context.applicationContext
    private val pm = ctx.packageManager

    private val aliases = mapOf(
        "youtube" to listOf("com.google.android.youtube"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "instagram" to listOf("com.instagram.android"),
        "chrome" to listOf("com.android.chrome"),
        "maps" to listOf("com.google.android.apps.maps"),
        "googlemaps" to listOf("com.google.android.apps.maps"),
        "settings" to listOf("com.android.settings"),
        "camera" to listOf("com.sec.android.app.camera", "com.android.camera", "com.google.android.GoogleCamera"),
        "gallery" to listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
        "phone" to listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"),
        "contacts" to listOf("com.samsung.android.app.contacts", "com.google.android.contacts"),
        "messages" to listOf("com.samsung.android.messaging", "com.google.android.apps.messaging")
    )

    fun installedApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun find(query: String): AppInfo? {
        val q = norm(query)
        if (q.isEmpty()) return null
        val apps = installedApps()
        apps.firstOrNull { norm(it.label) == q }?.let { return it }
        aliases[q]?.forEach { pkg -> apps.firstOrNull { it.packageName == pkg }?.let { return it } }
        apps.firstOrNull { norm(it.label).startsWith(q) }?.let { return it }
        apps.firstOrNull { norm(it.label).contains(q) }?.let { return it }
        return apps.firstOrNull { norm(it.label).length >= 3 && q.contains(norm(it.label)) }
    }

    fun open(query: String): LaunchResult {
        val app = find(query) ?: return LaunchResult.NotInstalled(query)
        val intent = pm.getLaunchIntentForPackage(app.packageName)
            ?: return LaunchResult.Failed(app.label, "no launchable screen")
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            LaunchResult.Launched(app.label)
        } catch (e: Exception) {
            LaunchResult.Failed(app.label, e.message ?: "launch blocked")
        }
    }
}
