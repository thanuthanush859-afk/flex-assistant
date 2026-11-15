package com.flex.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flex.assistant.FlexConfig
import com.flex.assistant.R
import com.flex.assistant.device.AppInfo
import kotlin.math.abs
import kotlin.math.sin

private val Bg = Color(0xFF02060F)
private val Cy = Color(0xFF12D8F5)
private val Cy2 = Color(0xFF0AA6C4)
private val Pan = Color(0xFF041225)
private val Field = Color(0xFF091A33)
private val Bd = Color(0xFF0D4A63)
private val Mu = Color(0xFF6FB8CF)
private val Sel = Color(0xFF0B3A56)

@Composable
fun FlexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Cy, background = Bg, surface = Pan),
        content = content
    )
}

@Composable
fun HomeScreen(vm: MainViewModel = viewModel()) {
    val s = vm.state
    var page by remember { mutableStateOf("home") }
    var showKey by remember { mutableStateOf(!s.hasKey) }
    var input by remember { mutableStateOf("") }

    LaunchedEffect(page) { if (page == "apps") vm.loadApps() }

    val send = { vm.send(input); input = "" }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF07203A), Bg), radius = 1400f))
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Header()
        StandbyToggle(s.standby) { vm.setStandby(it) }
        Dots(speaking = false)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                "home" -> Column(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val side = minOf(maxWidth * 0.92f, maxHeight)
                        Orb(side, speaking = false, busy = s.busy, onTap = { vm.onOrbTap() }, onCam = { vm.onCamTap() })
                    }
                    Text(s.status, color = Cy, fontSize = 12.sp, letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp))
                    InputBar(input, { input = it }, !s.busy && !s.standby, send)
                }
                "chat" -> Column(Modifier.fillMaxSize()) {
                    ChatList(s.messages, Modifier.weight(1f))
                    InputBar(input, { input = it }, !s.busy && !s.standby, send)
                }
                "apps" -> AppsGrid(vm.apps) { vm.openApp(it) }
                else -> SettingsPage(onKey = { showKey = true }, onClear = { vm.clearHistory() })
            }
        }
        NavBar(page) { page = it }
    }

    if (showKey) KeyDialog(onSave = { vm.saveKey(it); showKey = false }, onClose = { showKey = false })
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().border(1.dp, Bd, RoundedCornerShape(20.dp))
            .background(Pan.copy(alpha = 0.8f), RoundedCornerShape(20.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.flex_logo), "FLEX AI",
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF7A5CFF), RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("FLEX AI ASSISTANT", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Your Intelligent Companion", color = Cy, fontSize = 10.5.sp)
        }
        Text("DEVICES", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.border(1.5.dp, Cy, RoundedCornerShape(12.dp))
                .background(Color(0xFF0A2A4A), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 7.dp))
    }
}

@Composable
private fun StandbyToggle(standby: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.border(1.dp, Bd, RoundedCornerShape(16.dp)).background(Pan, RoundedCornerShape(16.dp)).padding(4.dp)) {
            listOf("ACTIVE" to false, "STANDBY" to true).forEach { (label, value) ->
                val on = standby == value
                Text(label, color = if (on) Cy else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (on) Sel else Color.Transparent)
                        .clickable { onChange(value) }
                        .padding(horizontal = 18.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun Dots(speaking: Boolean) {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "ph")
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
        for (i in 0 until 18) {
            val a = if (speaking) 0.4f + 0.6f * abs(sin(phase + i * 0.5f)) else 0.5f
            Box(Modifier.padding(horizontal = 3.5.dp).size(6.dp).background(Cy2.copy(alpha = a), CircleShape))
        }
    }
}

@Composable
private fun Orb(side: androidx.compose.ui.unit.Dp, speaking: Boolean, busy: Boolean, onTap: () -> Unit, onCam: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val a1 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "a1")
    val a2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "a2")
    val pulse by t.animateFloat(1f, 1.06f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse")
    val scale = if (speaking || busy) pulse else 1f

    Box(
        Modifier.size(side).clickable(remember { MutableInteractionSource() }, null) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val sw = 2.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(Cy.copy(alpha = 0.18f), Color.Transparent), radius = r), radius = r)
            drawCircle(Color(0xFF18B9D6), r * 0.97f, style = Stroke(sw))
            rotate(a1 / 4f) {
                drawCircle(Color(0xFF1A8FA8), r * 0.80f,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))))
            }
            drawCircle(Cy2, r * 0.66f, style = Stroke(sw))
            drawCircle(Color(0xFF14C8E6), r * 0.60f, style = Stroke(sw))
            val seg = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
            val tl = Offset(r * 0.03f, r * 0.03f)
            val sz = Size(r * 1.94f, r * 1.94f)
            drawArc(Cy, a1, 14f, false, tl, sz, style = seg)
            drawArc(Cy, a1 + 180f, 14f, false, tl, sz, style = seg)
            drawArc(Cy, a2 + 90f, 10f, false, tl, sz, style = seg)
        }
        Image(
            painterResource(R.drawable.flex_logo), "FLEX AI",
            Modifier.fillMaxSize(0.54f).scale(scale).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.padding(end = side * 0.1f, bottom = side * 0.1f).size(side * 0.17f)
                    .background(Cy, CircleShape).clickable { onCam() },
                contentAlignment = Alignment.Center
            ) { Text("CAM", color = Color(0xFF021A2B), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun InputBar(value: String, onChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .border(1.dp, Bd, RoundedCornerShape(22.dp)).background(Pan.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.weight(1f),
            placeholder = { Text("Ask Flex anything...", fontSize = 13.sp) }, singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onSend, enabled = enabled && value.isNotBlank(), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cy, contentColor = Color(0xFF021A2B))
        ) { Text("Send", fontWeight = FontWeight.Bold) }
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
                    Box(Modifier.size(56.dp).background(Field, RoundedCornerShape(16.dp)).border(1.dp, Bd, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center) {
                        if (icon != null) Image(icon, app.label, Modifier.size(40.dp))
                        else Text(app.label.take(1), color = Cy, fontWeight = FontWeight.Bold)
                    }
                    Text(app.label, color = Mu, fontSize = 10.5.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(onKey: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SETTINGS", color = Cy, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(4.dp))
        SettingRow("Chat model", FlexConfig.CHAT_MODEL)
        SettingRow("Live voice model", FlexConfig.LIVE_MODEL + " (voice comes next)")
        Button(onClick = onKey, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Sel, contentColor = Color.White)) { Text("Gemini API key") }
        Button(onClick = onClear, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Sel, contentColor = Color.White)) { Text("Clear conversation history") }
        Spacer(Modifier.height(8.dp))
        Text(FlexConfig.CREDIT, color = Mu, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun SettingRow(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().border(1.dp, Bd, RoundedCornerShape(16.dp))
        .background(Pan.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(title, color = Color.White, fontSize = 13.sp)
        Text(sub, color = Mu, fontSize = 11.sp)
    }
}

@Composable
private fun NavBar(page: String, onPage: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).border(1.dp, Bd, RoundedCornerShape(22.dp))
            .background(Pan.copy(alpha = 0.9f), RoundedCornerShape(22.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("home" to "HOME", "chat" to "AI CHAT", "apps" to "APPS", "settings" to "SETTINGS").forEach { (key, label) ->
            val on = page == key
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).background(if (on) Cy else Color.Transparent)
                    .clickable { onPage(key) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Color(0xFF021A2B) else Color.White, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
private fun KeyDialog(onSave: (String) -> Unit, onClose: () -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Gemini API key") },
        text = {
            Column {
                Text("Paste your key from Google AI Studio. It stays on this phone only.", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(enabled = key.isNotBlank(), onClick = { onSave(key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
