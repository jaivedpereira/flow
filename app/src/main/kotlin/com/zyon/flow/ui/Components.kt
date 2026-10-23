/**
 * Flow — shared UI atoms: top bar, preset strip, adjustment slider.
 */

package com.zyon.flow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.edit.Adjustments
import com.zyon.flow.edit.Preset
import com.zyon.flow.edit.Presets
import com.zyon.flow.edit.swatchColor
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim

@Composable
fun FlowTopBar(
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(onClick = onClose) {
            CloseGlyph(tint = FlowText)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = FlowText,
            letterSpacing = 5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        CircleButton(onClick = onSave, enabled = saveEnabled) {
            CheckGlyph(tint = if (saveEnabled) FlowCream else FlowTextDim)
        }
    }
}

@Composable
fun CircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (enabled) FlowElevated else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun PresetStrip(
    selectedId: String,
    onSelect: (Preset) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(Presets, key = { it.id }) { preset ->
            val selected = preset.id == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(58.dp)
                    .clickable { onSelect(preset) },
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(swatchColor(preset))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) FlowCream else FlowLine,
                            shape = CircleShape,
                        ),
                ) {
                    if (preset.id == "original") {
                        Text(
                            text = "0",
                            color = Color.Black.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) FlowText else FlowTextDim,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A single signed adjustment row with a thin slider. */
@Composable
fun AdjustRow(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FlowTextDim,
            modifier = Modifier.width(88.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = FlowCream,
                activeTrackColor = FlowCream.copy(alpha = 0.7f),
                inactiveTrackColor = FlowLine,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = (value * 100).toInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            color = FlowTextDim,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

/** A single 0..1 row used by fade / vignette / grain. */
@Composable
fun UnitAdjustRow(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FlowTextDim,
            modifier = Modifier.width(88.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = FlowCream,
                activeTrackColor = FlowCream.copy(alpha = 0.7f),
                inactiveTrackColor = FlowLine,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = (value * 100).toInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            color = FlowTextDim,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

/** Tabs at the bottom of the editor. */
@Composable
fun FlowTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        labels.forEachIndexed { i, label ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.4.sp,
                    color = if (selected == i) FlowCream else FlowTextDim,
                    fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Helper so callers can zero out adjustments. */
fun Adjustments.isNeutral(): Boolean =
    exposure == 0f && contrast == 0f && saturation == 0f &&
        warmth == 0f && fade == 0f && vignette == 0f && grain == 0f

/** Minimal X drawn with strokes — no system icon, matches the editorial line work. */
@Composable
fun CloseGlyph(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(16.dp)) {
        val s = size.minDimension
        val w = s * 0.09f
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(s * 0.18f, s * 0.18f),
            androidx.compose.ui.geometry.Offset(s * 0.82f, s * 0.82f),
            strokeWidth = w,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(s * 0.82f, s * 0.18f),
            androidx.compose.ui.geometry.Offset(s * 0.18f, s * 0.82f),
            strokeWidth = w,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/** Minimal check mark. */
@Composable
fun CheckGlyph(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(18.dp)) {
        val s = size.minDimension
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.16f, s * 0.54f)
            lineTo(s * 0.41f, s * 0.78f)
            lineTo(s * 0.86f, s * 0.24f)
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = s * 0.11f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}