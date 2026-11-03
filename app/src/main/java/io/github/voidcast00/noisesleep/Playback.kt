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
    /** 0 = off. */
    val timerMinutes: Int = 30,
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
        return NoiseSettings(
            color = prefs.getFloat("color", d.color),
            warmth = prefs.getFloat("warmth", d.warmth),
            volume = prefs.getFloat("volume", d.volume),
            timerMinutes = prefs.getInt("timer_minutes", d.timerMinutes),
        )
    }

    fun save(s: NoiseSettings) {
        prefs.edit()
            .putFloat("color", s.color)
            .putFloat("warmth", s.warmth)
            .putFloat("volume", s.volume)
            .putInt("timer_minutes", s.timerMinutes)
            .apply()
    }
}
