package com.flex.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flex.assistant.FlexApp
import com.flex.assistant.FlexRuntime
import com.flex.assistant.VoiceState
import com.flex.assistant.device.AppInfo
import com.flex.assistant.voice.LiveService

internal fun startVoice(ctx: Context) {
    ContextCompat.startForegroundService(ctx, Intent(ctx, LiveService::class.java).setAction(LiveService.ACTION_LISTEN))
}

internal fun startWakeService(ctx: Context) {
    ContextCompat.startForegroundService(ctx, Intent(ctx, LiveService::class.java).setAction(LiveService.ACTION_WAKE))
}

internal fun hasMic(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
fun HomeScreen(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val app = FlexApp.instance
    val s = vm.state
    val vs by FlexRuntime.voiceState.collectAsState()
    val verr by FlexRuntime.voiceError.collectAsState()
    val level = FlexRuntime.level.collectAsState()
    val req by FlexRuntime.requestedPage.collectAsState()
    var page by remember { mutableStateOf(if (app.settings.apiKey.isBlank()) "settings" else "home") }
    var input by remember { mutableStateOf("") }
    var onboarded by remember { mutableStateOf(app.settings.onboarded) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        FlexRuntime.resumeTick.value = FlexRuntime.resumeTick.value + 1
        if (granted) startVoice(ctx)
    }
    val toggleVoice = {
        if (!s.standby) {
            if (app.voice.isActive()) app.voice.stop()
            else if (hasMic(ctx)) startVoice(ctx)
            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(req) { req?.let { page = it; FlexRuntime.requestedPage.value = null } }
    LaunchedEffect(page) {
        if (page == "apps") vm.loadApps()
        if (page == "chat") vm.reloadMessages()
    }
    LaunchedEffect(Unit) {
        if (app.settings.wakeWord && hasMic(ctx) && !app.voice.isActive()) startWakeService(ctx)
    }

    val status = when {
        s.standby -> "Standby."
        s.busy -> "Thinking..."
        vs == VoiceState.ERROR -> verr ?: "Voice error. Tap the orb to retry."
        vs == VoiceState.CONNECTING -> "Connecting..."
        vs == VoiceState.LISTENING -> "Listening..."
        vs == VoiceState.THINKING -> "Thinking..."
        vs == VoiceState.EXECUTING -> "Executing action..."
        vs == VoiceState.SPEAKING -> "Speaking..."
        page == "vision" -> "Vision active."
        else -> s.note ?: "FLEX is ready."
    }
    val speaking = vs == VoiceState.SPEAKING
    val listening = vs == VoiceState.LISTENING || vs == VoiceState.THINKING

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF07203A), Bg), radius = 1400f))
                .statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Header()
            StandbyToggle(s.standby) { vm.setStandby(it) }
            Dots(speaking = speaking)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    "home" -> Column(Modifier.fillMaxSize()) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val side = minOf(maxWidth * 0.92f, maxHeight)
                            Orb(side, speaking, s.busy, listening, level, onTap = { toggleVoice() }, onCam = { page = "vision" })
                        }
                        Text(
                            status, color = if (vs == VoiceState.ERROR) Color(0xFFFF8A80) else Cy, fontSize = 12.sp, maxLines = 4,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)
                        )
                        InputBar(input, { input = it }, !s.busy && !s.standby, { vm.send(input); input = "" }, { toggleVoice() })
                    }
                    "chat" -> Column(Modifier.fillMaxSize()) {
                        ChatList(s.messages, Modifier.weight(1f))
                        Text(status, color = Cy, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        InputBar(input, { input = it }, !s.busy && !s.standby, { vm.send(input); input = "" }, { toggleVoice() })
                    }
                    "apps" -> AppsGrid(vm.apps) { vm.openApp(it) }
                    "vision" -> VisionPage(status = status, onClose = { page = "home" }, onToggleVoice = { toggleVoice() })
                    else -> SettingsPage(vm)
                }
            }
            NavBar(if (page == "vision") "home" else page) { page = it }
        }

        if (!onboarded) {
            Column(
                Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Welcome to FLEX", color = Cy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("FLEX asks only for what each feature needs. Allow what you want now; you can change any of it later in Settings.",
                    color = Mu, fontSize = 13.sp)
                Box(Modifier.weight(1f)) { PermissionsPanel() }
                SmallButton("Continue", {
                    app.settings.onboarded = true
                    onboarded = true
                }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ChatList(messages: List<ChatMessage>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1) }
    LazyColumn(modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(messages) { m ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                Text(
                    m.text, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(0.82f).background(if (m.fromUser) Sel else Field, RoundedCornerShape(16.dp))
                        .border(1.dp, if (m.fromUser) Cy2 else Bd, RoundedCornerShape(16.dp)).padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AppsGrid(apps: List<AppInfo>, onOpen: (AppInfo) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Text("INSTALLED APPS (${apps.size})", color = Cy, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(4.dp))
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps, key = { it.packageName }) { app ->
                val icon = remember(app.packageName) {
                    runCatching { ctx.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                }
                Column(Modifier.clickable { onOpen(app) }.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(56.dp).background(Field, RoundedCornerShape(16.dp)).border(1.dp, Bd, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (icon != null) Image(icon, app.label, Modifier.size(40.dp))
                        else Text(app.label.take(1), color = Cy, fontWeight = FontWeight.Bold)
                    }
                    Text(app.label, color = Mu, fontSize = 10.5.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
