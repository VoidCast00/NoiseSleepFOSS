package io.github.voidcast00.noisesleep.ui

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.voidcast00.noisesleep.NoiseService
import io.github.voidcast00.noisesleep.NoiseSettings
import io.github.voidcast00.noisesleep.PlaybackState
import io.github.voidcast00.noisesleep.R
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.sqrt

/** 5-minute steps up to an hour, then 15-minute steps up to 3 hours. */
private val STOP_OPTIONS = (5..60 step 5).toList() + (75..180 step 15).toList()
private val FADE_OPTIONS = listOf(0, 1, 2, 3, 5, 10, 15, 20, 30)

@Composable
fun SleepTimerCard(
    state: PlaybackState,
    settings: NoiseSettings,
    /** Changes that restart the countdown (on/off, length). */
    onTimerChange: ((NoiseSettings) -> NoiseSettings) -> Unit,
    /** Changes that apply live without restarting (fade length). */
    onSettingsChange: ((NoiseSettings) -> NoiseSettings) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val now = rememberTicker(state.timerEndsAt)
    val end = state.timerEndsAt
    val running = end != null
    // While running, the graph shows what is left from now; before, the whole planned timer
    val totalMs = if (end != null) (end - now).coerceAtLeast(0L) else settings.timerMinutes * 60_000L
    val fadeMs = settings.fadeMinutes * 60_000L
    val fadeStartMs = (totalMs - fadeMs).coerceAtLeast(0L)
    fun clock(offsetMs: Long): String =
        DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + offsetMs))

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.titleMedium)
                    if (running) {
                        Text(
                            stringResource(R.string.timer_left, duration(totalMs)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                        )
                    }
                }
                Switch(
                    checked = settings.timerEnabled,
                    onCheckedChange = { on -> onTimerChange { it.copy(timerEnabled = on) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary),
                )
            }

            AnimatedVisibility(
                visible = settings.timerEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    MarkerLabel(
                        text = if (settings.fadeMinutes > 0 && fadeStartMs > 0) {
                            stringResource(
                                R.string.graph_fade_from,
                                if (running) clock(fadeStartMs) else "+" + minutes(fadeStartMs),
                            )
                        } else {
                            ""
                        },
                        fraction = if (totalMs > 0) fadeStartMs.toFloat() / totalMs else 0f,
                    )
                    Spacer(Modifier.height(4.dp))
                    VolumeGraph(totalMs = totalMs, fadeMinutes = settings.fadeMinutes)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val style = MaterialTheme.typography.labelMedium
                        Text(
                            stringResource(if (running) R.string.graph_now else R.string.graph_start),
                            style = style, color = colors.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.graph_silent, if (running) clock(totalMs) else "+" + minutes(totalMs)),
                            style = style, color = colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    StepSlider(
                        label = stringResource(R.string.stop_after),
                        valueText = minutes(settings.timerMinutes * 60_000L),
                        options = STOP_OPTIONS,
                        value = settings.timerMinutes,
                        onValueChange = { m ->
                            // A fade can never be longer than the timer itself
                            onTimerChange { it.copy(timerMinutes = m, fadeMinutes = minOf(it.fadeMinutes, m)) }
                        },
                    )
                    StepSlider(
                        label = stringResource(R.string.fade_out_over),
                        valueText = if (settings.fadeMinutes == 0) {
                            stringResource(R.string.no_fade)
                        } else {
                            minutes(settings.fadeMinutes * 60_000L)
                        },
                        options = FADE_OPTIONS,
                        value = settings.fadeMinutes,
                        onValueChange = { m -> onSettingsChange { it.copy(fadeMinutes = minOf(m, it.timerMinutes)) } },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    !settings.timerEnabled -> stringResource(R.string.timer_none)
                    running && settings.fadeMinutes == 0 -> stringResource(R.string.summary_stops_at, clock(totalMs))
                    running && fadeStartMs == 0L -> stringResource(R.string.summary_fading_now, clock(totalMs))
                    running -> stringResource(R.string.summary_running, clock(fadeStartMs), clock(totalMs))
                    settings.fadeMinutes == 0 -> stringResource(R.string.summary_plan_no_fade, minutes(totalMs))
                    else -> stringResource(R.string.summary_plan, minutes(fadeStartMs), minutes(fadeMs))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** Loudness over time: flat at full volume, then a slope down to silence. */
@Composable
private fun VolumeGraph(totalMs: Long, fadeMinutes: Int) {
    val line = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outline
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val w = size.width
        val h = size.height
        val top = 4.dp.toPx()
        val samples = 96
        val curve = Path()
        for (i in 0..samples) {
            val t = totalMs * i / samples
            // Perceived loudness: the square root of the amplitude the engine plays
            val level = sqrt(NoiseService.fadeLevel(totalMs - t, fadeMinutes))
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

        val fadeStart = (totalMs - fadeMinutes * 60_000L).coerceAtLeast(0L)
        if (fadeMinutes > 0 && fadeStart > 0 && totalMs > 0) {
            val x = w * fadeStart / totalMs
            drawLine(
                guide, Offset(x, top), Offset(x, h), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }
    }
}

/** A label centred over [fraction] of the width, kept inside the edges. */
@Composable
private fun MarkerLabel(text: String, fraction: Float) {
    Layout(
        content = {
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val label = measurables.single().measure(constraints.copy(minWidth = 0))
        val w = constraints.maxWidth
        layout(w, label.height) {
            val x = (w * fraction - label.width / 2f).toInt().coerceIn(0, (w - label.width).coerceAtLeast(0))
            label.place(x, 0)
        }
    }
}

@Composable
private fun StepSlider(
    label: String,
    valueText: String,
    options: List<Int>,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val index = options.indexOfLast { it <= value }.coerceAtLeast(0)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { v ->
                val m = options[v.toInt().coerceIn(0, options.lastIndex)]
                if (m != value) onValueChange(m)
            },
            valueRange = 0f..options.lastIndex.toFloat(),
            steps = options.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.outline,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** Current [SystemClock.elapsedRealtime], ticking every second while a timer runs. */
@Composable
private fun rememberTicker(end: Long?): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(end) {
        now = SystemClock.elapsedRealtime()
        if (end == null) return@LaunchedEffect
        while (true) {
            delay(1000L - (end - now).mod(1000L))
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}

/** "42:13" or "1:02:13". */
private fun duration(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "40 min" or "1 h 15 min". */
private fun minutes(ms: Long): String {
    val total = ((ms + 30_000) / 60_000).toInt()
    val h = total / 60
    val m = total % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}
