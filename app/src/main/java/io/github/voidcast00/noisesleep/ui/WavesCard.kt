package io.github.voidcast00.noisesleep.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.voidcast00.noisesleep.NoiseGenerator
import io.github.voidcast00.noisesleep.NoiseSettings
import io.github.voidcast00.noisesleep.R
import kotlin.math.sqrt

private val WAVE_SECONDS = (4..20).toList()
private val STRENGTH_STEPS = listOf(1, 2, 3, 4, 5)

@Composable
fun WavesCard(
    settings: NoiseSettings,
    onSettingsChange: ((NoiseSettings) -> NoiseSettings) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Stored as 0..1, shown as five steps from gentle to strong
    val step = (settings.waveStrength * 4f + 0.5f).toInt() + 1
    val strengthName = stringResource(
        when (step) {
            1 -> R.string.strength_1
            2 -> R.string.strength_2
            3 -> R.string.strength_3
            4 -> R.string.strength_4
            else -> R.string.strength_5
        },
    )
    ExpandableCard(
        title = stringResource(R.string.ocean_waves),
        summary = if (settings.wavesEnabled) {
            stringResource(R.string.waves_short, settings.waveSeconds, strengthName)
        } else {
            stringResource(R.string.waves_off_hint)
        },
        highlightSummary = false,
        checked = settings.wavesEnabled,
        onCheckedChange = { on -> onSettingsChange { it.copy(wavesEnabled = on) } },
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
                Column {
                    WavePreview(strength = settings.waveStrength)
                    Spacer(Modifier.height(16.dp))
                    StepSlider(
                        label = stringResource(R.string.wave_every),
                        valueText = stringResource(R.string.seconds, settings.waveSeconds),
                        options = WAVE_SECONDS,
                        value = settings.waveSeconds,
                        onValueChange = { sec -> onSettingsChange { it.copy(waveSeconds = sec) } },
                    )
                    StepSlider(
                        label = stringResource(R.string.wave_strength),
                        valueText = strengthName,
                        options = STRENGTH_STEPS,
                        value = step,
                        onValueChange = { n -> onSettingsChange { it.copy(waveStrength = (n - 1) / 4f) } },
                    )
                }
    }
}

/** Three waves of loudness, each a little different in length like the real thing. */
@Composable
private fun WavePreview(strength: Float) {
    val line = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outline
    val lengths = floatArrayOf(1f, 0.82f, 1.18f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        val w = size.width
        val h = size.height
        val top = 4.dp.toPx()
        val total = lengths.sum()
        val samples = 120
        val curve = Path()
        for (i in 0..samples) {
            // Which wave this point falls in, and how far through it
            var t = total * i / samples
            var phase = 0f
            for (len in lengths) {
                if (t <= len) {
                    phase = t / len
                    break
                }
                t -= len
            }
            val amplitude = 1f - strength * NoiseGenerator.MAX_WAVE_DEPTH * (1f - NoiseGenerator.waveLevel(phase))
            val level = sqrt(amplitude) // roughly how loud it sounds
            val x = w * i / samples
            val y = top + (h - top) * (1f - level)
            if (i == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(curve)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawLine(guide, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
        drawPath(fill, line.copy(alpha = 0.22f))
        drawPath(curve, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
