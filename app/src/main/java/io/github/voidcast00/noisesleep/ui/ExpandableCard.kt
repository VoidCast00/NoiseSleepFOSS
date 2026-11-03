package io.github.voidcast00.noisesleep.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.voidcast00.noisesleep.R

/**
 * A one-line row (title, summary, on/off switch) that opens to show its settings when tapped.
 * Kept closed by default so the whole screen fits without scrolling.
 */
@Composable
internal fun ExpandableCard(
    title: String,
    summary: String,
    highlightSummary: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val open = checked && expanded
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "arrow")
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = checked) { onExpandedChange(!expanded) }
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (highlightSummary) colors.primary else colors.onSurfaceVariant,
                    )
                }
                if (checked) {
                    Icon(
                        painterResource(R.drawable.ic_expand),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(24.dp)
                            .rotate(arrow),
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = { on ->
                        onCheckedChange(on)
                        // Turning something on shows its settings straight away
                        if (on) onExpandedChange(true)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary),
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), content = content)
            }
        }
    }
}
