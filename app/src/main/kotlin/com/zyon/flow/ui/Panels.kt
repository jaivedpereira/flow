/**
 * Flow — bottom panels: crop/transform, recipes, live histogram.
 */

package com.zyon.flow.ui

import com.zyon.flow.R

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import com.zyon.flow.edit.ImageOps
import com.zyon.flow.ui.theme.glassControl
import com.zyon.flow.ui.theme.GlassTint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.edit.CropRatio
import com.zyon.flow.edit.Recipe
import com.zyon.flow.edit.Transform
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim

/** Small text action used in panel headers. */
@Composable
private fun MiniAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .glassControl(
                shape = RoundedCornerShape(8.dp),
                tint = if (enabled) GlassTint.CONTROL else GlassTint.PANEL,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) FlowText else FlowTextDim.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            letterSpacing = 0.4.sp,
        )
    }
}

/** Compact luminance histogram, drawn as a filled curve. */
@Composable
fun Histogram(
    bins: IntArray?,
    /** (shadow fraction, highlight fraction). Null hides the markers. */
    clipping: Pair<Float, Float>? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(FlowElevated, RoundedCornerShape(8.dp))
            .border(1.dp, FlowLine, RoundedCornerShape(8.dp)),
    ) {
        if (bins != null && bins.isNotEmpty()) {
            Canvas(Modifier.fillMaxWidth().height(52.dp).padding(6.dp)) {
                val max = bins.maxOfOrNull { it }?.coerceAtLeast(1) ?: 1
                val path = Path()
                path.moveTo(0f, size.height)
                bins.forEachIndexed { i, v ->
                    val x = size.width * (i / (bins.size - 1f))
                    val y = size.height - (v.toFloat() / max) * size.height
                    path.lineTo(x, y)
                }
                path.lineTo(size.width, size.height)
                path.close()
                drawPath(path, FlowCream.copy(alpha = 0.28f))
                // outline for definition
                val line = Path()
                bins.forEachIndexed { i, v ->
                    val x = size.width * (i / (bins.size - 1f))
                    val y = size.height - (v.toFloat() / max) * size.height
                    if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }
                drawPath(line, FlowCream.copy(alpha = 0.85f), style = Stroke(width = 1.6f))

                // Clipping markers: a red bar on the right when highlights are
                // blown, a blue bar on the left when shadows are crushed. Width
                // scales with how much of the frame is affected, so it is a
                // readout rather than a yes/no light.
                if (clipping != null) {
                    val (dark, bright) = clipping
                    if (dark > 0.001f) {
                        drawRect(
                            color = androidx.compose.ui.graphics.Color(0xFF4A7BD0),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width * dark.coerceIn(0f, 1f), 3.5f),
                        )
                    }
                    if (bright > 0.001f) {
                        drawRect(
                            color = androidx.compose.ui.graphics.Color(0xFFE04B4B),
                            topLeft = androidx.compose.ui.geometry.Offset(size.width * (1f - bright.coerceIn(0f, 1f)), 0f),
                            size = androidx.compose.ui.geometry.Size(size.width * bright.coerceIn(0f, 1f), 3.5f),
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.histogram),
                style = MaterialTheme.typography.labelSmall,
                color = FlowTextDim,
                letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Crop aspect + rotation + mirror + straighten. */
@Composable
fun CropPanel(
    transform: Transform,
    modifier: Modifier = Modifier,
    onChange: (Transform) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 10.dp, bottom = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.crop_ratio),
            style = MaterialTheme.typography.labelSmall,
            color = FlowTextDim,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(CropRatio.entries.toList()) { ratio ->
                val selected = (ratio.ratio == null && transform.aspect == null) ||
                    (ratio.ratio != null && transform.aspect == ratio.ratio)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) FlowCream else FlowElevated)
                        .border(1.dp, if (selected) FlowCream else FlowLine, RoundedCornerShape(10.dp))
                        .clickable { onChange(transform.copy(aspect = ratio.ratio)) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = ratio.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.Black else FlowText,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.crop_transform),
            style = MaterialTheme.typography.labelSmall,
            color = FlowTextDim,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionChip("ROTATE") {
                onChange(transform.copy(rotation = (transform.rotation + 90) % 360))
            }
            ActionChip("MIRROR") {
                onChange(transform.copy(flipH = !transform.flipH))
            }
            ActionChip("RESET") {
                onChange(Transform())
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.crop_straighten),
                style = MaterialTheme.typography.labelSmall,
                color = FlowTextDim,
                modifier = Modifier.width(96.dp),
            )
            androidx.compose.material3.Slider(
                value = transform.straighten,
                onValueChange = { onChange(transform.copy(straighten = it)) },
                valueRange = -15f..15f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = FlowCream,
                    activeTrackColor = FlowCream.copy(alpha = 0.7f),
                    inactiveTrackColor = FlowLine,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${transform.straighten.toInt()}°",
                style = MaterialTheme.typography.labelSmall,
                color = FlowTextDim,
                textAlign = TextAlign.End,
                modifier = Modifier.width(38.dp),
            )
        }

        // Keystone correction. Two axes is enough for the common cases: a
        // building shot from below, and a frame taken off-axis.
        PerspRow(
            label = stringResource(R.string.crop_persp_v),
            value = transform.perspectiveV,
        ) { onChange(transform.copy(perspectiveV = it)) }
        PerspRow(
            label = stringResource(R.string.crop_persp_h),
            value = transform.perspectiveH,
        ) { onChange(transform.copy(perspectiveH = it)) }
    }
}

@Composable
private fun PerspRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = FlowTextDim,
            modifier = Modifier.width(96.dp),
        )
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(
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

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FlowElevated)
            .border(1.dp, FlowLine, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = FlowText,
            letterSpacing = 1.2.sp,
        )
    }
}

/** Save the current look as a recipe, or apply a saved one. */
@Composable
fun RecipePanel(
    recipes: List<Recipe>,
    clipboardHasContent: Boolean,
    modifier: Modifier = Modifier,
    onApply: (Recipe) -> Unit,
    onSave: () -> Unit,
    onDelete: (Recipe) -> Unit,
    onCopyLook: () -> Unit,
    onPasteLook: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 10.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recipe_title),
                style = MaterialTheme.typography.labelSmall,
                color = FlowTextDim,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f),
            )
            MiniAction(stringResource(R.string.look_copy), true, onCopyLook)
            Spacer(Modifier.width(6.dp))
            MiniAction(stringResource(R.string.look_paste), clipboardHasContent, onPasteLook)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(FlowCream)
                    .clickable(onClick = onSave)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.recipe_save),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (recipes.isEmpty()) {
            Text(
                text = stringResource(R.string.recipe_empty),
                style = MaterialTheme.typography.bodySmall,
                color = FlowTextDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recipes, key = { it.name }) { recipe ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(92.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(FlowElevated)
                                .border(1.dp, FlowLine, RoundedCornerShape(12.dp))
                                .clickable { onApply(recipe) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = recipe.presetId.take(2).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = FlowCream,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = recipe.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = FlowText,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.recipe_delete_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = FlowTextDim.copy(alpha = 0.6f),
                            fontSize = 8.sp,
                            modifier = Modifier.clickable { onDelete(recipe) },
                        )
                    }
                }
            }
        }
    }
}

/** Build 256-bin luminance histogram from packed ARGB pixels. */

/** Fraction of pixels crushed to black or blown to white, 0..1 each. */
fun clippingOf(src: Bitmap, maxDim: Int = 200): Pair<Float, Float> {
    val small = ImageOps.sampleForHistogram(src, maxDim)
    val w = small.width
    val h = small.height
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    var dark = 0
    var bright = 0
    for (p in px) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        if (r <= 3 && g <= 3 && b <= 3) dark++
        if (r >= 252 && g >= 252 && b >= 252) bright++
    }
    val n = px.size.toFloat().coerceAtLeast(1f)
    return (dark / n) to (bright / n)
}

fun histogramOf(pixels: IntArray): IntArray {
    val bins = IntArray(256)
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val lum = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
        bins[lum]++
    }
    return bins
}
