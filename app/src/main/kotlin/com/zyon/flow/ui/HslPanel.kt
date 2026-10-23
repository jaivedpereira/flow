/**
 * Flow — selective HSL panel: 8 colour bands, 3 knobs each.
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.edit.Hsl
import com.zyon.flow.edit.HslBand
import com.zyon.flow.edit.HslShift
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim

@Composable
fun HslPanel(
    hsl: Hsl,
    modifier: Modifier = Modifier,
    onChange: (Hsl) -> Unit,
) {
    var band by remember { mutableStateOf(HslBand.RED) }
    val shift = hsl.shiftFor(band)

    fun commit(s: HslShift) = onChange(hsl.with(band, s))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 6.dp),
    ) {
        // ---- band swatches ----
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(HslBand.entries.toList(), key = { it.name }) { b ->
                val selected = b == band
                val s = hsl.shiftFor(b)
                val active = !s.isNeutral
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(46.dp)
                        .clickable { band = b },
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(b.swatch))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) FlowCream else FlowLine,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = b.label.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = if (selected) FlowText else FlowTextDim,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        HslRow("HUE", shift.hue) { commit(shift.copy(hue = it)) }
        HslRow("SATURATION", shift.saturation) { commit(shift.copy(saturation = it)) }
        HslRow("LUMINANCE", shift.luminance) { commit(shift.copy(luminance = it)) }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HslChip("RESET BAND") { onChange(hsl.with(band, HslShift())) }
            HslChip("RESET ALL") { onChange(Hsl()) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HslRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = if (kotlin.math.abs(value) > 0.01f) FlowCream else FlowTextDim,
            modifier = Modifier.width(84.dp),
        )
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = FlowCream,
                activeTrackColor = FlowCream.copy(alpha = 0.8f),
                inactiveTrackColor = FlowLine,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = (value * 100).toInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = FlowTextDim,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp),
        )
    }
}

@Composable
private fun HslChip(label: String, onClick: () -> Unit) {
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
