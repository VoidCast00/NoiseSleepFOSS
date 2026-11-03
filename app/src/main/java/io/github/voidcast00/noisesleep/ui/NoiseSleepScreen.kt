package io.github.voidcast00.noisesleep.ui

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.voidcast00.noisesleep.NoiseSettings
import io.github.voidcast00.noisesleep.PlaybackState
import io.github.voidcast00.noisesleep.R
import kotlinx.coroutines.delay

private val TIMER_OPTIONS = listOf(0, 15, 30, 60, 90)

@Composable
fun NoiseSleepScreen(
    state: PlaybackState,
    settings: NoiseSettings,
    onPlayPause: () -> Unit,
    onSettingsChange: ((NoiseSettings) -> NoiseSettings) -> Unit,
    onTimerChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Surface also sets the default text colour, otherwise unstyled text comes out black
    Surface(Modifier.fillMaxSize(), color = colors.background) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .safeDrawingPadding()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(48.dp))
            PlayButton(playing = state.playing, onClick = onPlayPause)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(colorName(settings.color)), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(40.dp))

            SettingSlider(
                label = R.string.colour, startLabel = R.string.white, endLabel = R.string.brown,
                value = settings.color,
                track = Brush.horizontalGradient(listOf(NoiseColors.White, NoiseColors.Pink, NoiseColors.Brown)),
                onValueChange = { v -> onSettingsChange { it.copy(color = v) } },
            )
            SettingSlider(
                label = R.string.warmth, startLabel = R.string.bright, endLabel = R.string.deep,
                value = settings.warmth,
                track = Brush.horizontalGradient(listOf(NoiseColors.Bright, NoiseColors.Deep)),
                onValueChange = { v -> onSettingsChange { it.copy(warmth = v) } },
            )
            SettingSlider(
                label = R.string.volume, startLabel = R.string.quiet, endLabel = R.string.loud,
                value = settings.volume,
                track = Brush.horizontalGradient(listOf(colors.surfaceVariant, colors.primary)),
                onValueChange = { v -> onSettingsChange { it.copy(volume = v) } },
            )

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TIMER_OPTIONS.forEachIndexed { index, minutes ->
                    SegmentedButton(
                        selected = settings.timerMinutes == minutes,
                        onClick = { onTimerChange(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(index, TIMER_OPTIONS.size),
                        icon = {},
                    ) {
                        Text(if (minutes == 0) stringResource(R.string.timer_off) else minutes.toString())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                timerStatus(state, settings),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
      }
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // A slow "breathing" halo while playing
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val haloAlpha by animateFloatAsState(if (playing) 0.18f else 0f, tween(800), label = "halo")
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(140.dp)
                .scale(breath)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = haloAlpha)),
        )
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = colors.primary,
            modifier = Modifier.size(128.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                    tint = colors.onPrimary,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingSlider(
    @StringRes label: Int,
    @StringRes startLabel: Int,
    @StringRes endLabel: Int,
    value: Float,
    track: Brush,
    onValueChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(track),
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                colors = SliderDefaults.colors(
                    thumbColor = colors.onBackground,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val small = MaterialTheme.typography.bodySmall
            Text(stringResource(startLabel), style = small, color = colors.onSurfaceVariant)
            Text(stringResource(endLabel), style = small, color = colors.onSurfaceVariant)
        }
    }
}

@StringRes
private fun colorName(color: Float): Int = when {
    color < 0.15f -> R.string.noise_white
    color < 0.38f -> R.string.noise_white_pink
    color < 0.62f -> R.string.noise_pink
    color < 0.85f -> R.string.noise_pink_brown
    else -> R.string.noise_brown
}

@Composable
private fun timerStatus(state: PlaybackState, settings: NoiseSettings): String {
    val end = state.timerEndsAt
    if (end != null) {
        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(end) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(1000L - (end - now).mod(1000L))
            }
        }
        val left = ((end - now).coerceAtLeast(0L) + 999L) / 1000L
        val h = left / 3600
        val m = left % 3600 / 60
        val s = left % 60
        val clock = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        return stringResource(R.string.timer_counting, clock)
    }
    return stringResource(
        when {
            settings.timerMinutes == 0 -> R.string.timer_none
            else -> R.string.timer_waiting
        },
    )
}
