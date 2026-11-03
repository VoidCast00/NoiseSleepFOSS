package io.github.voidcast00.noisesleep

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Plays [NoiseGenerator] output through an [AudioTrack] on its own thread.
 * Starting fades in over 2 s and stopping fades out over 0.4 s, so there are no clicks.
 */
class NoiseEngine(
    private val settings: () -> NoiseSettings,
    /** 1 normally, gliding to 0 as the sleep timer runs out. */
    private val timerGain: () -> Float,
    /** Called on the audio thread once the timer has faded the sound to silence. */
    private val onTimerSilent: () -> Unit,
) {
    private val lock = Any()
    private var running = false // guarded by lock
    @Volatile private var fadingOut = false

    fun start() {
        synchronized(lock) {
            fadingOut = false
            if (running) return // still fading out: just fade back in
            running = true
            Thread({ loop() }, "NoiseEngine").start()
        }
    }

    /** Fades out; the audio thread then releases the speaker by itself. */
    fun stop() {
        fadingOut = true
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var exitedCleanly = false
        var track: AudioTrack? = null
        try {
            track = buildTrack()
            val generator = NoiseGenerator(System.nanoTime())
            val buf = FloatArray(FRAMES * 2)
            var env = 0f
            var timerReported = false
            track.play()
            while (true) {
                val s = settings()
                val t = timerGain()
                // Squared so the slider feels even: half way sounds like half as loud
                generator.render(
                    buf, s.color, s.warmth, s.volume * s.volume * t,
                    waveStrength = if (s.wavesEnabled) s.waveStrength else 0f,
                    waveSeconds = s.waveSeconds.toFloat(),
                )

                val target = if (fadingOut) 0f else 1f
                var i = 0
                while (i + 1 < buf.size) {
                    env = if (env < target) min(target, env + FADE_IN_STEP) else max(target, env - FADE_OUT_STEP)
                    val e = env * env
                    buf[i] = (buf[i] * e).coerceIn(-1f, 1f)
                    buf[i + 1] = (buf[i + 1] * e).coerceIn(-1f, 1f)
                    i += 2
                }
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)

                if (t <= 0f && !timerReported) {
                    timerReported = true
                    onTimerSilent()
                }
                if (fadingOut && env <= 0f) {
                    synchronized(lock) {
                        if (fadingOut) {
                            running = false
                            exitedCleanly = true
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NoiseEngine", "Audio thread failed", e)
        } finally {
            if (!exitedCleanly) synchronized(lock) { running = false }
            track?.let {
                runCatching { it.stop() }
                it.release()
            }
        }
    }

    private fun buildTrack(): AudioTrack {
        val format = AudioFormat.Builder()
            .setSampleRate(NoiseGenerator.SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            NoiseGenerator.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(AUDIO_ATTRIBUTES)
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBytes, FRAMES * 2 * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    companion object {
        private const val FRAMES = 1024 // ~23 ms per buffer
        private const val FADE_IN_STEP = 1f / (NoiseGenerator.SAMPLE_RATE * 2f)
        private const val FADE_OUT_STEP = 1f / (NoiseGenerator.SAMPLE_RATE * 0.4f)

        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
