/**
 * Flow — the Efeitos tab: GrainRad-style artistic effects with live thumbnails.
 */

package com.zyon.flow.ui

import com.zyon.flow.R

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.edit.Effect
import com.zyon.flow.edit.Effects
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim

@Composable
fun EffectsPanel(
    selectedId: String,
    thumbs: Map<String, Bitmap>,
    effectParam: Float,
    effectIntensity: Float,
    onSelect: (Effect) -> Unit,
    onParamChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effect = Effects.byId(selectedId)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = 4.dp),
    ) {
        // ---- effect tiles ----
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Effects.All, key = { it.id }) { e ->
                val selected = e.id == selectedId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { onSelect(e) },
                ) {
                    Box(
                        modifier = Modifier
                            .height(54.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(FlowElevated)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) FlowCream else FlowLine,
                                shape = RoundedCornerShape(9.dp),
                            ),
                    ) {
                        val bmp = thumbs[e.id]
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(7.dp)),
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "···",
                                    color = FlowTextDim,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = e.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = if (selected) FlowText else FlowTextDim,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        // ---- per-effect controls ----
        if (effect.id != "none") {
            Spacer(Modifier.height(8.dp))
            ParamRow(
                label = effect.paramLabel.ifBlank { "Amount" }.uppercase(),
                value = effectParam,
                onChange = onParamChange,
            )
            ParamRow(
                label = "INTENSITY",
                value = effectIntensity,
                onChange = onIntensityChange,
                accent = true,
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.effects_none_hint),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = FlowTextDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ParamRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    accent: Boolean = false,
) {
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
            letterSpacing = 1.2.sp,
            color = if (accent) FlowCream else FlowTextDim,
            modifier = Modifier.width(74.dp),
        )
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = FlowCream,
                activeTrackColor = FlowCream.copy(alpha = if (accent) 0.95f else 0.7f),
                inactiveTrackColor = FlowLine,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(value * 100).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (accent) FlowCream else FlowTextDim,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
    }
}
