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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flex.assistant.R
import kotlin.math.abs
import kotlin.math.sin

internal val Bg = Color(0xFF02060F)
internal val Cy = Color(0xFF12D8F5)
internal val Cy2 = Color(0xFF0AA6C4)
internal val Pan = Color(0xFF041225)
internal val Field = Color(0xFF091A33)
internal val Bd = Color(0xFF0D4A63)
internal val Mu = Color(0xFF6FB8CF)
internal val Sel = Color(0xFF0B3A56)
internal val Ink = Color(0xFF021A2B)

@Composable
fun FlexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Cy, background = Bg, surface = Pan), content = content)
}

@Composable
internal fun Header() {
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
internal fun StandbyToggle(standby: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun Dots(speaking: Boolean) {
    val t = rememberInfiniteTransition(label = "dots")
    val phase = t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "ph")
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val n = 18
        val d = 6.dp.toPx()
        val gap = 7.dp.toPx()
        val x0 = (size.width - (n * d + (n - 1) * gap)) / 2
        val p = if (speaking) phase.value else 0f
        for (i in 0 until n) {
            val a = if (speaking) 0.4f + 0.6f * abs(sin(p + i * 0.5f)) else 0.5f
            drawCircle(Cy2.copy(alpha = a), d / 2, Offset(x0 + i * (d + gap) + d / 2, size.height / 2))
        }
    }
}

@Composable
internal fun Orb(
    side: Dp, speaking: Boolean, busy: Boolean, listening: Boolean,
    level: State<Float>, onTap: () -> Unit, onCam: () -> Unit
) {
    val t = rememberInfiniteTransition(label = "orb")
    val a1 = t.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "a1")
    val a2 = t.animateFloat(360f, 0f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "a2")
    val pulse = t.animateFloat(1f, 1.06f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse")

    Box(
        Modifier.size(side).clickable(remember { MutableInteractionSource() }, null) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val sw = 2.dp.toPx()
            val ang = a1.value
            val ang2 = a2.value
            drawCircle(Brush.radialGradient(listOf(Cy.copy(alpha = 0.22f), Color.Transparent), radius = r), radius = r)
            drawCircle(Cy.copy(alpha = 0.12f), r * 0.97f, style = Stroke(10.dp.toPx()))
            drawCircle(Color(0xFF18B9D6), r * 0.97f, style = Stroke(sw))
            if (listening) {
                drawCircle(Cy.copy(alpha = 0.35f + level.value * 0.65f), r * 0.90f, style = Stroke(3.dp.toPx() + level.value * 10.dp.toPx()))
            }
            rotate(ang / 4f) {
                drawCircle(Color(0xFF1A8FA8), r * 0.80f,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))))
            }
            drawCircle(Cy2, r * 0.66f, style = Stroke(sw))
            drawCircle(Color(0xFF14C8E6), r * 0.60f, style = Stroke(sw))
            val seg = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
            val tl = Offset(r * 0.03f, r * 0.03f)
            val sz = Size(r * 1.94f, r * 1.94f)
            drawArc(Cy, ang, 14f, false, tl, sz, style = seg)
            drawArc(Cy, ang + 180f, 14f, false, tl, sz, style = seg)
            drawArc(Cy, ang2 + 90f, 10f, false, tl, sz, style = seg)
        }
        Image(
            painterResource(R.drawable.flex_logo), "FLEX AI",
            Modifier.fillMaxSize(0.54f)
                .graphicsLayer {
                    val sc = when {
                        speaking -> 1f + level.value * 0.18f
                        busy -> pulse.value
                        else -> 1f
                    }
                    scaleX = sc
                    scaleY = sc
                }
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.padding(end = side * 0.1f, bottom = side * 0.1f).size(side * 0.17f)
                    .background(Cy, CircleShape).border(3.dp, Ink, CircleShape).clickable { onCam() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(side * 0.085f)) {
                    val st = Stroke(2.dp.toPx())
                    drawRoundRect(Ink, Offset(0f, size.height * 0.2f), Size(size.width, size.height * 0.7f), CornerRadius(4.dp.toPx()), style = st)
                    drawCircle(Ink, size.width * 0.2f, Offset(size.width / 2, size.height * 0.55f), style = st)
                }
            }
        }
    }
}

@Composable
internal fun InputBar(value: String, onChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit, onMic: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .border(1.dp, Bd, RoundedCornerShape(22.dp)).background(Pan.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.weight(1f),
            placeholder = { Text("Ask Flex anything...", fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            singleLine = true, shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text("MIC", color = Cy, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            modifier = Modifier.border(1.dp, Bd, RoundedCornerShape(14.dp)).background(Field, RoundedCornerShape(14.dp))
                .clickable { onMic() }.padding(horizontal = 11.dp, vertical = 16.dp))
        Spacer(Modifier.width(6.dp))
        Button(
            onClick = onSend, enabled = enabled && value.isNotBlank(), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cy, contentColor = Ink)
        ) { Text("Send", fontWeight = FontWeight.Bold) }
    }
}

@Composable
internal fun NavBar(page: String, onPage: (String) -> Unit) {
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
                Text(label, color = if (on) Ink else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, Bd, RoundedCornerShape(16.dp))
            .background(Pan.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = Cy, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun SmallButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Sel, contentColor = Color.White)) {
        Text(label, fontSize = 12.sp)
    }
}
