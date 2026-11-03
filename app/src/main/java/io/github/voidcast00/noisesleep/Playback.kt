package io.github.voidcast00.noisesleep

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NoiseSettings(
    /** 0 = white, 0.5 = pink, 1 = brown, blended in between. */
    val color: Float = 0.75f,
    /** 0 = bright, 1 = deep (low-pass filter). */
    val warmth: Float = 0.25f,
    val volume: Float = 0.6f,
    val wavesEnabled: Boolean = false,
    /** Average length of one wave. */
    val waveSeconds: Int = 10,
    /** 0 = gentle, 1 = strong. */
    val waveStrength: Float = 0.5f,
    val timerEnabled: Boolean = true,
    /** How long to play before the sound is gone (includes the fade). */
    val timerMinutes: Int = 30,
    /** The last part of [timerMinutes] over which the volume slides to silence. 0 = no fade. */
    val fadeMinutes: Int = 5,
)

data class PlaybackState(
    val playing: Boolean = false,
    /** In [android.os.SystemClock.elapsedRealtime] time, or null when no timer is running. */
    val timerEndsAt: Long? = null,
)

/** Shared between the screen and [NoiseService]. The audio thread reads settings directly. */
object Playback {
    private val _settings = MutableStateFlow(NoiseSettings())
    val settings: StateFlow<NoiseSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var store: SettingsStore? = null

    fun init(context: Context) {
        if (store != null) return
        val s = SettingsStore(context.applicationContext)
        _settings.value = s.load()
        store = s
    }

    fun updateSettings(transform: (NoiseSettings) -> NoiseSettings) {
        _settings.update(transform)
        store?.save(_settings.value)
    }

    internal fun setState(state: PlaybackState) {
        _state.value = state
    }
}

private class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): NoiseSettings {
        val d = NoiseSettings()
        // Version 0.1.0 stored "timer off" as 0 minutes
        val minutes = prefs.getInt("timer_minutes", d.timerMinutes)
        return NoiseSettings(
            color = prefs.getFloat("color", d.color),
            warmth = prefs.getFloat("warmth", d.warmth),
            volume = prefs.getFloat("volume", d.volume),
            wavesEnabled = prefs.getBoolean("waves_enabled", d.wavesEnabled),
            waveSeconds = prefs.getInt("wave_seconds", d.waveSeconds),
            waveStrength = prefs.getFloat("wave_strength", d.waveStrength),
            timerEnabled = prefs.getBoolean("timer_enabled", minutes > 0),
            timerMinutes = if (minutes > 0) minutes else d.timerMinutes,
            fadeMinutes = prefs.getInt("fade_minutes", d.fadeMinutes),
        )
    }

    fun save(s: NoiseSettings) {
        prefs.edit()
            .putFloat("color", s.color)
            .putFloat("warmth", s.warmth)
            .putFloat("volume", s.volume)
            .putBoolean("waves_enabled", s.wavesEnabled)
            .putInt("wave_seconds", s.waveSeconds)
            .putFloat("wave_strength", s.waveStrength)
            .putBoolean("timer_enabled", s.timerEnabled)
            .putInt("timer_minutes", s.timerMinutes)
            .putInt("fade_minutes", s.fadeMinutes)
            .apply()
    }
}
