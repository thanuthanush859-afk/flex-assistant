package com.flex.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.flex.assistant.FlexApp
import com.flex.assistant.FlexRuntime
import com.flex.assistant.VoiceState
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private fun toJpeg(p: ImageProxy): ByteArray {
    var bmp = p.toBitmap()
    val rot = p.imageInfo.rotationDegrees
    if (rot != 0) {
        val m = Matrix()
        m.postRotate(rot.toFloat())
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    val scale = 640f / maxOf(bmp.width, bmp.height)
    if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 60, out)
    return out.toByteArray()
}

/** Real CameraX preview. Frames go to Gemini Live about once a second while a voice session is connected. */
@Composable
fun VisionPage(status: String, onClose: () -> Unit, onToggleVoice: () -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as LifecycleOwner
    var hasCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }
    var front by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val vs by FlexRuntime.voiceState.collectAsState()
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(hasCam, front) {
        var provider: ProcessCameraProvider? = null
        if (hasCam) {
            val fut = ProcessCameraProvider.getInstance(ctx)
            fut.addListener({
                val p = fut.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                var last = 0L
                analysis.setAnalyzer(executor) { proxy ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - last >= 1000) {
                        last = now
                        try { FlexApp.instance.voice.sendFrame(toJpeg(proxy)) } catch (e: Exception) { }
                    }
                    proxy.close()
                }
                val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                p.unbindAll()
                camera = try { p.bindToLifecycle(owner, sel, preview, analysis) } catch (e: Exception) { null }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose {
            provider?.unbindAll()
            camera = null
        }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    LaunchedEffect(focusPoint) { if (focusPoint != null) { delay(900); focusPoint = null } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.5.dp, Cy, RoundedCornerShape(22.dp))) {
            if (hasCam) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().pointerInput(camera) {
                        detectTapGestures { off ->
                            val c = camera ?: return@detectTapGestures
                            val pt = previewView.meteringPointFactory.createPoint(off.x, off.y)
                            c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
                            focusPoint = off
                        }
                    }
                )
                focusPoint?.let { fp ->
                    Canvas(Modifier.fillMaxSize()) { drawCircle(Cy, 34.dp.toPx(), fp, style = Stroke(2.dp.toPx())) }
                }
            } else {
                Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Camera permission is off.", color = Mu, fontSize = 13.sp)
                    SmallButton("Allow camera", { camLauncher.launch(Manifest.permission.CAMERA) })
                }
            }
        }
        Text(
            if (camera != null) "Vision active. Tap the picture to focus. $status" else status,
            color = Cy, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton("Flip", { front = !front }, Modifier.weight(1f))
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                SmallButton(if (torch) "Flash off" else "Flash on", { torch = !torch; camera?.cameraControl?.enableTorch(torch) }, Modifier.weight(1f))
            }
            SmallButton(if (vs == VoiceState.IDLE || vs == VoiceState.ERROR) "Start voice" else "Stop voice", onToggleVoice, Modifier.weight(1f))
            SmallButton("Describe", {
                FlexApp.instance.voice.sendText("Look at the camera and tell me what you see in front of me.")
            }, Modifier.weight(1f), enabled = vs != VoiceState.IDLE && vs != VoiceState.ERROR && vs != VoiceState.CONNECTING)
            SmallButton("Close", onClose, Modifier.weight(1f))
        }
    }
}
