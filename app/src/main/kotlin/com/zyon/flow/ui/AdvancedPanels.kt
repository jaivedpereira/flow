/**
 * Flow — Curves and Border panels, plus the brush-mask controls.
 */

package com.zyon.flow.ui

import com.zyon.flow.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.edit.Border
import com.zyon.flow.edit.Curve
import com.zyon.flow.edit.CurvePoint
import com.zyon.flow.edit.CurvePreset
import com.zyon.flow.edit.Curves
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim

private val CHANNELS = listOf("RGB", "R", "G", "B")

/** Cap on user-placed control points. Past this the curve stops responding. */
private const val MAX_CURVE_POINTS = 10

/** Minimum horizontal distance between neighbouring points, in 0..1 units. */
private const val CURVE_MIN_GAP = 0.012f

@Composable
fun CurvesPanel(
    curves: Curves,
    presets: List<CurvePreset>,
    modifier: Modifier = Modifier,
    onChange: (Curves) -> Unit,
    onSavePreset: () -> Unit,
    onApplyPreset: (CurvePreset) -> Unit,
    onDeletePreset: (CurvePreset) -> Unit,
) {
    var channel by remember { mutableIntStateOf(0) }
    val active = when (channel) {
        1 -> curves.red
        2 -> curves.green
        3 -> curves.blue
        else -> curves.master
    }

    fun commit(c: Curve) {
        onChange(
            when (channel) {
                1 -> curves.copy(red = c)
                2 -> curves.copy(green = c)
                3 -> curves.copy(blue = c)
                else -> curves.copy(master = c)
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        // channel chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CHANNELS, key = { it }) { name ->
                val idx = CHANNELS.indexOf(name)
                val selected = idx == channel
                val tint = when (name) {
                    "R" -> Color(0xFFFF6B6B)
                    "G" -> Color(0xFF7BE495)
                    "B" -> Color(0xFF6BB6FF)
                    else -> FlowCream
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) FlowElevated else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) tint else FlowLine,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { channel = idx }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                        fontSize = 9.sp,
                        color = if (selected) tint else FlowTextDim,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        CurveEditor(
            curve = active,
            tint = when (channel) {
                1 -> Color(0xFFFF6B6B)
                2 -> Color(0xFF7BE495)
                3 -> Color(0xFF6BB6FF)
                else -> FlowCream
            },
            onCommit = ::commit,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(170.dp),
        )

        Spacer(Modifier.height(6.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SmallChip("SAVE SET") { onSavePreset() }
            }
            items(presets, key = { it.name }) { preset ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(FlowElevated)
                        .border(1.dp, FlowLine, RoundedCornerShape(8.dp))
                        .clickable { onApplyPreset(preset) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        letterSpacing = 0.6.sp,
                        color = FlowText,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallChip("RESET") { commit(Curve()) }
            SmallChip("RESET ALL") { onChange(Curves()) }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun CurveEditor(
    curve: Curve,
    tint: Color,
    onCommit: (Curve) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragIndex by remember { mutableStateOf(-1) }
    var points by remember(curve) { mutableStateOf(curve.points) }

    fun moveTo(x: Float, y: Float) {
        val px = x.coerceIn(0f, 1f)
        val py = y.coerceIn(0f, 1f)
        val list = points.toMutableList()
        if (list.isEmpty()) return

        // The curve may have been replaced (reset, preset, another channel)
        // while a drag was in flight, which leaves dragIndex out of range and
        // would throw on list[dragIndex].
        if (dragIndex !in list.indices) dragIndex = -1

        if (dragIndex < 0) {
            if (list.size >= MAX_CURVE_POINTS) return
            list.add(CurvePoint(px, py))
            list.sortBy { it.x }
            // pick the point we just inserted by position, not by float equality
            dragIndex = list.indexOfLast { it.x <= px }.coerceIn(0, list.lastIndex)
        } else {
            if (dragIndex == 0 || dragIndex == list.lastIndex) {
                list[dragIndex] = list[dragIndex].copy(y = py)
            } else {
                val lo = list[dragIndex - 1].x + CURVE_MIN_GAP
                val hi = list[dragIndex + 1].x - CURVE_MIN_GAP
                // lo can be greater than hi when two neighbouring points sit
                // almost on top of each other. Float.coerceIn throws on an
                // empty range, so pick the midpoint instead.
                val nx = if (lo <= hi) px.coerceIn(lo, hi) else (lo + hi) * 0.5f
                list[dragIndex] = CurvePoint(nx.coerceIn(0f, 1f), py)
            }
        }
        points = list
        onCommit(Curve(list))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FlowElevated)
            .border(1.dp, FlowLine, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { off ->
                        dragIndex = -1
                        moveTo(off.x / size.width, 1f - off.y / size.height)
                    },
                    onDrag = { change, _ ->
                        moveTo(change.position.x / size.width, 1f - change.position.y / size.height)
                    },
                    onDragEnd = { dragIndex = -1 },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = size.minDimension * 0.06f
            val w = size.width - pad * 2
            val h = size.height - pad * 2

            // grid
            val grid = FlowTextDim.copy(alpha = 0.18f)
            for (i in 0..4) {
                val x = pad + w * i / 4f
                val y = pad + h * i / 4f
                drawLine(grid, Offset(x, pad), Offset(x, pad + h), strokeWidth = 1f)
                drawLine(grid, Offset(pad, y), Offset(pad + w, y), strokeWidth = 1f)
            }
            // identity diagonal
            drawLine(
                FlowTextDim.copy(alpha = 0.25f),
                Offset(pad, pad + h),
                Offset(pad + w, pad),
                strokeWidth = 1f,
            )

            // curve
            val lut = curve.lut()
            val path = Path()
            for (i in 0..255) {
                val x = pad + w * (i / 255f)
                val y = pad + h * (1f - lut[i] / 255f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, tint, style = Stroke(width = 2.2f))

            // control points
            curve.points.forEach { p ->
                drawCircle(
                    color = tint,
                    radius = 4.5f,
                    center = Offset(pad + w * p.x, pad + h * (1f - p.y)),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.55f),
                    radius = 4.5f,
                    center = Offset(pad + w * p.x, pad + h * (1f - p.y)),
                    style = Stroke(width = 1.2f),
                )
            }
        }
    }
}

@Composable
fun BorderPanel(
    border: Border,
    modifier: Modifier = Modifier,
    onChange: (Border) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 250.dp)
            .padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.border_width),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                color = FlowTextDim,
                modifier = Modifier.width(70.dp),
            )
            androidx.compose.material3.Slider(
                value = border.width,
                onValueChange = { onChange(border.copy(width = it)) },
                valueRange = 0f..0.10f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = FlowCream,
                    activeTrackColor = FlowCream.copy(alpha = 0.85f),
                    inactiveTrackColor = FlowLine,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(border.width * 1000).toInt()}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = FlowCream,
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.border_radius),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                color = FlowTextDim,
                modifier = Modifier.width(70.dp),
            )
            androidx.compose.material3.Slider(
                value = border.rounded,
                onValueChange = { onChange(border.copy(rounded = it)) },
                valueRange = 0f..0.30f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = FlowCream,
                    activeTrackColor = FlowCream.copy(alpha = 0.85f),
                    inactiveTrackColor = FlowLine,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(border.rounded * 100).toInt()}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = FlowCream,
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.border_colour),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = FlowTextDim,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Border.Palette.toList(), key = { it }) { c ->
                val selected = border.color == c
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) FlowCream else FlowLine,
                            shape = CircleShape,
                        )
                        .clickable { onChange(border.copy(color = c)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(FlowElevated)
            .border(1.dp, FlowLine, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            letterSpacing = 0.8.sp,
            color = FlowText,
        )
    }
}

/** Brush controls shown inside the Efeitos tab. */
@Composable
fun MaskControls(
    useMask: Boolean,
    brushing: Boolean,
    onToggleMask: () -> Unit,
    onToggleBrush: () -> Unit,
    onClear: () -> Unit,
    onInvert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.effects_mask),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = FlowTextDim,
            modifier = Modifier.width(40.dp),
        )
        MaskChip(if (useMask) "ON" else "OFF", active = useMask, onClick = onToggleMask)
        MaskChip(if (brushing) "PAINTING" else "BRUSH", active = brushing, onClick = onToggleBrush)
        MaskChip("CLEAR", active = false, onClick = onClear)
        MaskChip("INVERT", active = false, onClick = onInvert)
    }
}

@Composable
private fun MaskChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) FlowCream else FlowElevated)
            .border(1.dp, if (active) FlowCream else FlowLine, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            letterSpacing = 0.6.sp,
            color = if (active) Color.Black else FlowTextDim,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
