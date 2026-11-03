package io.github.voidcast00.noisesleep

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.voidcast00.noisesleep.ui.NoiseSleepScreen
import io.github.voidcast00.noisesleep.ui.NoiseSleepTheme

class MainActivity : ComponentActivity() {
    // Asked on the first play. Playback works either way; without it the notification is hidden.
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        NoiseService.play(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        Playback.init(this)
        // Opened from the Quick Settings tile when Android would not let it start playback itself
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_PLAY, false)) startPlaying()

        setContent {
            NoiseSleepTheme {
                val state by Playback.state.collectAsStateWithLifecycle()
                val settings by Playback.settings.collectAsStateWithLifecycle()
                NoiseSleepScreen(
                    state = state,
                    settings = settings,
                    onPlayPause = { if (state.playing) NoiseService.pause(this) else startPlaying() },
                    onSettingsChange = Playback::updateSettings,
                    onTimerChange = { transform ->
                        Playback.updateSettings(transform)
                        NoiseService.timerChanged(this)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_PLAY, false) && !Playback.state.value.playing) startPlaying()
    }

    private fun startPlaying() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Once denied for good, Android answers instantly without a dialog and we just play
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NoiseService.play(this)
        }
    }

    companion object {
        const val EXTRA_PLAY = "io.github.voidcast00.noisesleep.EXTRA_PLAY"
    }
}
