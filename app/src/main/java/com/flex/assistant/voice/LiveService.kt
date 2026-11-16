package com.flex.assistant.voice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.flex.assistant.FlexApp
import com.flex.assistant.FlexRuntime
import com.flex.assistant.MainActivity
import com.flex.assistant.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service so FLEX keeps listening and talking in the background, with a visible notification. */
class LiveService : Service() {
    companion object {
        const val ACTION_LISTEN = "flex.LISTEN"
        const val ACTION_WAKE = "flex.WAKE"
        const val ACTION_STOP = "flex.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: WakeWordManager? = null
    private var observing = false
    private val app: FlexApp get() = application as FlexApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { app.voice.stop(); stopService(); return START_NOT_STICKY }
        startFg("FLEX is ready.")
        when (intent?.action) {
            ACTION_LISTEN -> app.voice.start()
            ACTION_WAKE -> startWake()
        }
        observe()
        return START_NOT_STICKY
    }

    private fun startWake() {
        if (wake == null) wake = WakeWordManager(this) { app.voice.start() }
        wake?.start()
    }

    private fun observe() {
        if (observing) return
        observing = true
        scope.launch {
            FlexRuntime.voiceState.collect { st ->
                startFg(label(st))
                if (st == VoiceState.IDLE || st == VoiceState.ERROR) {
                    if (app.settings.wakeWord) startWake() else stopService()
                } else wake?.stop()
            }
        }
    }

    private fun label(s: VoiceState) = when (s) {
        VoiceState.IDLE -> if (app.settings.wakeWord) "Waiting for \"A Flex\"" else "FLEX is ready."
        VoiceState.CONNECTING -> "Connecting..."
        VoiceState.LISTENING -> "Listening..."
        VoiceState.THINKING -> "Thinking..."
        VoiceState.EXECUTING -> "Doing it..."
        VoiceState.SPEAKING -> "Speaking..."
        VoiceState.ERROR -> "Error. Open FLEX for details."
    }

    private fun startFg(text: String) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, LiveService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "flex_live")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("FLEX microphone is on")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(11, n)
    }

    private fun stopService() {
        wake?.stop()
        wake = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
