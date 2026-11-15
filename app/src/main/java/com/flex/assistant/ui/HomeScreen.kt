package com.flex.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flex.assistant.FlexConfig

private val Cyan = Color(0xFF00E5FF)
private val Blue = Color(0xFF2979FF)
private val Navy = Color(0xFF050B1E)

@Composable
fun FlexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Cyan, background = Color.Black, surface = Navy),
        content = content
    )
}

@Composable
fun HomeScreen(vm: MainViewModel = viewModel()) {
    val s = vm.state
    var input by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(!s.hasKey) }
    val listState = rememberLazyListState()

    LaunchedEffect(s.messages.size) {
        if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.size - 1)
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black, Navy)))
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("FLEX", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showKey = true }) { Text("Settings") }
        }

        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            Orb(busy = s.busy)
        }
        Text(s.status, color = Cyan, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(s.messages) { m ->
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Text(
                        m.text, color = Color.White,
                        modifier = Modifier
                            .background(
                                if (m.fromUser) Blue.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask FLEX or say: open YouTube") },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = !s.busy && input.isNotBlank(), onClick = { vm.send(input); input = "" }) {
                Text("Send")
            }
        }
        Text(
            FlexConfig.CREDIT, color = Color.Gray, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
        )
    }

    if (showKey) KeyDialog(onSave = { vm.saveKey(it); showKey = false }, onClose = { showKey = false })
}

@Composable
private fun Orb(busy: Boolean) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse"
    )
    val scale = if (busy) pulse else 1f // pulses only while FLEX is really working
    Canvas(Modifier.size(160.dp)) {
        val r = size.minDimension / 2 * 0.55f * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Cyan.copy(alpha = 0.9f), Blue.copy(alpha = 0.35f), Color.Transparent),
                center = center, radius = r * 1.8f
            ),
            radius = r * 1.8f
        )
        drawCircle(Cyan, radius = r, style = Stroke(3.dp.toPx()))
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
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = { TextButton(enabled = key.isNotBlank(), onClick = { onSave(key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
