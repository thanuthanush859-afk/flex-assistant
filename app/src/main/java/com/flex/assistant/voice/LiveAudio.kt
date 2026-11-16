package com.flex.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.flex.assistant.FlexRuntime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The single audio controller: one microphone capture and one playback queue. */
class LiveAudio(private val onMic: (ByteArray, Float) -> Unit) {
    private var rec: AudioRecord? = null
    @Volatile private var micOn = false
    private var track: AudioTrack? = null
    private var trackRate = 0
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var playOn = false

    fun pending() = queue.isNotEmpty()

    private fun rms(b: ByteArray, n: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < n) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
            sum += (s * s).toDouble()
            i += 2
        }
        val cnt = n / 2
        if (cnt == 0) return 0f
        return (Math.sqrt(sum / cnt) / 8000.0).toFloat().coerceIn(0f, 1f)
    }

    /** Returns null on success or a readable error. */
    @SuppressLint("MissingPermission")
    fun startMic(): String? {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return "Microphone is not available on this device."
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400))
        } catch (e: Exception) { return "Microphone error: ${e.message}" }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return "Microphone could not start. Another app may be using it."
        }
        try { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId)?.enabled = true } catch (e: Exception) { }
        try { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(r.audioSessionId)?.enabled = true } catch (e: Exception) { }
        rec = r
        micOn = true
        r.startRecording()
        Thread({
            val buf = ByteArray(3200)
            while (micOn) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) { val c = buf.copyOf(n); onMic(c, rms(c, n)) } else if (n < 0) break
            }
        }, "flex-mic").start()
        return null
    }

    fun play(data: ByteArray, rate: Int) {
        ensureTrack(rate)
        FlexRuntime.level.value = rms(data, data.size)
        queue.offer(data)
    }

    @Synchronized
    private fun ensureTrack(rate: Int) {
        if (track != null && trackRate == rate) return
        track?.release()
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(min, rate))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.play()
        track = t
        trackRate = rate
        if (!playOn) {
            playOn = true
            Thread({
                while (playOn) {
                    val c = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    try { track?.write(c, 0, c.size) } catch (e: Exception) { }
                }
            }, "flex-play").start()
        }
    }

    /** Drop everything that is queued so the old answer stops immediately. */
    fun flush() {
        queue.clear()
        try { track?.let { it.pause(); it.flush(); it.play() } } catch (e: Exception) { }
    }

    @Synchronized
    fun stop() {
        micOn = false
        playOn = false
        queue.clear()
        try { rec?.stop() } catch (e: Exception) { }
        try { rec?.release() } catch (e: Exception) { }
        rec = null
        try { track?.stop() } catch (e: Exception) { }
        try { track?.release() } catch (e: Exception) { }
        track = null
        trackRate = 0
    }
}
