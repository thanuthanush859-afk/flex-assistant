package com.flex.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flex.assistant.FlexApp
import com.flex.assistant.FlexConfig
import com.flex.assistant.FlexRuntime
import com.flex.assistant.device.FlexAccessibilityService
import com.flex.assistant.school.SchoolModeManager
import com.flex.assistant.voice.LiveService

@Composable
fun SettingsPage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val app = FlexApp.instance
    val s = app.settings
    val st = vm.state

    var keyInput by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf(s.chatModel) }
    var live by remember { mutableStateOf(s.liveModel) }
    var pro by remember { mutableStateOf(s.proModel) }
    var imgFast by remember { mutableStateOf(s.imageFastModel) }
    var imgPro by remember { mutableStateOf(s.imageProModel) }
    var voice by remember { mutableStateOf(s.voiceName) }
    var wake by remember { mutableStateOf(s.wakeWord) }
    var school by remember { mutableStateOf(s.schoolMode) }
    var decline by remember { mutableStateOf(s.declineCalls) }
    var reply by remember { mutableStateOf(s.schoolReply) }
    var trusted by remember { mutableStateOf(s.trusted) }
    var factsTick by remember { mutableStateOf(0) }
    var logsTick by remember { mutableStateOf(0) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        FlexRuntime.resumeTick.value = FlexRuntime.resumeTick.value + 1
        if (granted) { s.wakeWord = true; wake = true; startWakeService(ctx) }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Section("API KEY AND CONNECTION") {
            OutlinedTextField(
                value = keyInput, onValueChange = { keyInput = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (st.hasKey) "Key saved (paste a new one to replace)" else "Gemini API key", fontSize = 12.sp) },
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Save", { vm.saveKey(keyInput); keyInput = "" }, enabled = keyInput.isNotBlank())
                SmallButton(if (st.testing) "Testing..." else "Test connection", { vm.testConnection() }, enabled = !st.testing && st.hasKey)
            }
            Text(st.keyStatus, color = if (st.keyStatus.startsWith("Connected")) Cy else Color.White, fontSize = 13.sp)
            st.testLines.forEach { Text(it, color = Mu, fontSize = 11.sp) }
        }

        Section("MODELS AND VOICE") {
            @Composable
            fun field(label: String, v: String, set: (String) -> Unit) =
                OutlinedTextField(value = v, onValueChange = set, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(label, fontSize = 11.sp) })
            field("Live voice model", live) { live = it }
            field("General brain model", chat) { chat = it }
            field("Coding / reasoning model", pro) { pro = it }
            field("Image model (fast)", imgFast) { imgFast = it }
            field("Image model (high quality)", imgPro) { imgPro = it }
            field("Voice name (e.g. Aoede, Kore, Puck)", voice) { voice = it }
            SmallButton("Save models", { vm.saveModels(chat, live, pro, imgFast, imgPro, voice) })
        }

        Section("WAKE WORD") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Listen for \"A Flex\" in the background", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = wake, onCheckedChange = { on ->
                    if (on) {
                        if (hasMic(ctx)) { s.wakeWord = true; wake = true; startWakeService(ctx) }
                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        s.wakeWord = false; wake = false
                        ctx.startService(Intent(ctx, LiveService::class.java).setAction(LiveService.ACTION_STOP))
                    }
                })
            }
            Text("The microphone notification stays visible while this is on. Nothing is recorded or stored.", color = Mu, fontSize = 11.sp)
        }

        Section("PERMISSIONS") { PermissionsPanel() }

        Section("SCHOOL MODE") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (school) "School Mode is ON" else "School Mode is off", color = if (school) Cy else Color.White, fontSize = 13.sp)
                Switch(checked = school, onCheckedChange = { SchoolModeManager.setEnabled(app, it); school = it; logsTick++ })
            }
            OutlinedTextField(value = reply, onValueChange = { reply = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Auto-reply message", fontSize = 11.sp) })
            OutlinedTextField(value = trusted, onValueChange = { trusted = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Trusted contacts (names or numbers, comma separated)", fontSize = 11.sp) })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Decline calls from others", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = decline, onCheckedChange = { decline = it; s.declineCalls = it })
            }
            SmallButton("Save School Mode settings", { s.schoolReply = reply; s.trusted = trusted; logsTick++ })
            Text("What it does: replies to WhatsApp messages from people who are not trusted, and texts callers by SMS (once per person every 30 minutes). " +
                "Android does not let apps speak on a normal call, so FLEX cannot talk to callers. It never records.", color = Mu, fontSize = 11.sp)
            val logs = remember(logsTick, school) { app.memory.recentLogs() }
            logs.forEach { Text(it, color = Mu, fontSize = 11.sp) }
        }

        Section("MEMORY") {
            val facts = remember(factsTick) { app.memory.facts() }
            Text(if (facts.isEmpty()) "No saved facts yet. Say \"remember that ...\" to FLEX." else "${facts.size} saved facts", color = Color.White, fontSize = 13.sp)
            facts.take(30).forEach { f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.text, color = Mu, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    SmallButton("Delete", { app.memory.deleteFact(f.id); factsTick++ })
                }
            }
            SmallButton("Clear conversation history", { vm.clearHistory() })
        }

        Section("ABOUT") {
            Text("FLEX Assistant v0.3.0", color = Color.White, fontSize = 13.sp)
            Text(FlexConfig.CREDIT, color = Mu, fontSize = 12.sp)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
    }
}

private data class PermItem(val title: String, val why: String, val perms: List<String>, val kind: String)

@Composable
fun PermissionsPanel() {
    val ctx = LocalContext.current
    val tick by FlexRuntime.resumeTick.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        FlexRuntime.resumeTick.value = FlexRuntime.resumeTick.value + 1
    }
    val items = remember {
        buildList {
            add(PermItem("Microphone", "Lets FLEX hear you for live voice conversations.", listOf(Manifest.permission.RECORD_AUDIO), "perm"))
            add(PermItem("Camera", "Vision mode: FLEX sees through your camera.", listOf(Manifest.permission.CAMERA), "perm"))
            add(PermItem("Contacts", "Find people by name for calls, WhatsApp and email.", listOf(Manifest.permission.READ_CONTACTS), "perm"))
            add(PermItem("Phone calls", "Call a contact directly when you ask.", listOf(Manifest.permission.CALL_PHONE), "perm"))
            add(PermItem("SMS", "School Mode texts callers that you are at school.", listOf(Manifest.permission.SEND_SMS), "perm"))
            add(PermItem("Call info", "School Mode: see who is calling and optionally decline.",
                listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.ANSWER_PHONE_CALLS), "perm"))
            if (Build.VERSION.SDK_INT >= 33) add(PermItem("Notifications", "Reminders and the voice-service notification.", listOf(Manifest.permission.POST_NOTIFICATIONS), "perm"))
            add(PermItem("Accessibility", "Lets FLEX press Send in WhatsApp or Gmail, tap videos, scroll and read the screen when you ask. You turn it on manually.", emptyList(), "acc"))
            add(PermItem("Notification access", "School Mode: lets FLEX see WhatsApp messages and reply to them.", emptyList(), "nls"))
            add(PermItem("Display over other apps", "Lets FLEX open apps by voice while it is in the background.", emptyList(), "overlay"))
        }
    }

    fun granted(it: PermItem): Boolean = when (it.kind) {
        "perm" -> it.perms.all { p -> ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED }
        "acc" -> FlexAccessibilityService.instance != null
        "nls" -> NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        else -> Settings.canDrawOverlays(ctx)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { it0 ->
            val ok = remember(tick) { granted(it0) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it0.title, color = Color.White, fontSize = 13.sp)
                    Text(
                        if (ok) (if (it0.kind == "acc") "Enabled" else "Granted") else (if (it0.kind == "perm") "Not granted" else "Disabled"),
                        color = if (ok) Cy else Color(0xFFFF8A80), fontSize = 12.sp
                    )
                }
                Text(it0.why, color = Mu, fontSize = 11.sp)
                if (!ok) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (it0.kind) {
                        "perm" -> {
                            SmallButton("Allow", { launcher.launch(it0.perms.toTypedArray()) })
                            SmallButton("App settings", { openAppSettings(ctx) })
                        }
                        "acc" -> SmallButton("Open Accessibility settings", { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
                        "nls" -> SmallButton("Open Notification access", { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
                        else -> SmallButton("Open settings", {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                        })
                    }
                }
            }
        }
    }
}

private fun openAppSettings(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
}
