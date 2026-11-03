package io.github.voidcast00.noisesleep

import android.Manifest
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

        setContent {
            NoiseSleepTheme {
                val state by Playback.state.collectAsStateWithLifecycle()
                val settings by Playback.settings.collectAsStateWithLifecycle()
                NoiseSleepScreen(
                    state = state,
                    settings = settings,
                    onPlayPause = { if (state.playing) NoiseService.pause(this) else startPlaying() },
                    onSettingsChange = Playback::updateSettings,
                    onTimerChange = { minutes ->
                        Playback.updateSettings { it.copy(timerMinutes = minutes) }
                        NoiseService.timerChanged(this)
                    },
                )
            }
        }
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
}
