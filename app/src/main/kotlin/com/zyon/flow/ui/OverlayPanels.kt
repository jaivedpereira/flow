/**
 * Flow — mask shape picker, text editor, sticker picker and batch export.
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.edit.FontEntry
import com.zyon.flow.edit.MaskGeometry
import com.zyon.flow.edit.MaskShape
import com.zyon.flow.edit.Overlays
import com.zyon.flow.edit.StickerLayer
import com.zyon.flow.edit.StickerPalette
import com.zyon.flow.edit.TextLayer
import com.zyon.flow.edit.TextStyle
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim

/** Shape chooser shown above the mask tools. */
@Composable
fun MaskShapeRow(
    shape: MaskShape,
    geometry: MaskGeometry,
    additive: Boolean,
    onShape: (MaskShape) -> Unit,
    onGeometry: (MaskGeometry) -> Unit,
    onAdditive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SHAPE",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                color = FlowTextDim,
                modifier = Modifier.width(46.dp),
            )
            MaskShape.entries.forEach { s ->
                val active = s == shape
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) FlowCream else FlowElevated)
                        .border(1.dp, if (active) FlowCream else FlowLine, RoundedCornerShape(8.dp))
                        .clickable { onShape(s) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = s.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        letterSpacing = 0.6.sp,
                        color = if (active) Color.Black else FlowTextDim,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        if (shape != MaskShape.BRUSH) {
            SliderRow("FEATHER", geometry.feather, 0f..1f) {
                onGeometry(geometry.copy(feather = it))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(FlowElevated)
                            .border(1.dp, FlowLine, RoundedCornerShape(8.dp))
                            .clickable { onGeometry(geometry.copy(invert = !geometry.invert)) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (geometry.invert) "INVERTED" else "INVERT",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            letterSpacing = 0.8.sp,
                            color = if (geometry.invert) FlowCream else FlowTextDim,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (additive) FlowCream else FlowElevated)
                            .border(1.dp, if (additive) FlowCream else FlowLine, RoundedCornerShape(8.dp))
                            .clickable { onAdditive(!additive) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (additive) "ADDING" else "REPLACE",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            letterSpacing = 0.8.sp,
                            color = if (additive) Color.Black else FlowTextDim,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
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
            color = FlowTextDim,
            modifier = Modifier.width(74.dp),
        )
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = FlowCream,
                activeTrackColor = FlowCream.copy(alpha = 0.85f),
                inactiveTrackColor = FlowLine,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (range.endInclusive <= 1.05f) "${(value * 100).toInt()}" else value.toInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = FlowCream,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp),
        )
    }
}

/** Text layer list plus the editor for the selected layer. */
@Composable
fun TextPanel(
    overlays: Overlays,
    selectedId: Long?,
    fonts: List<FontEntry>,
    onSelect: (Long) -> Unit,
    onChange: (Overlays) -> Unit,
    onImportFont: () -> Unit,
    onDeleteFont: (FontEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layer = overlays.texts.firstOrNull { it.id == selectedId } ?: overlays.texts.firstOrNull()

    fun update(l: TextLayer) {
        onChange(overlays.copy(texts = overlays.texts.map { if (it.id == l.id) l else it }))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 6.dp),
    ) {
        // add / select
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Chip("+ TEXT", active = true) {
                    val id = overlays.nextTextId()
                    val fresh = TextLayer(id = id, text = "FLOW")
                    onChange(overlays.copy(texts = overlays.texts + fresh))
                    onSelect(id)
                }
            }
            items(overlays.texts, key = { it.id }) { t ->
                Chip(t.text.take(8).ifBlank { "—" }, active = t.id == layer?.id) {
                    onSelect(t.id)
                }
            }
        }

        if (layer == null) {
            Text(
                text = "Tap + TEXT to add a caption.",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = FlowTextDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            return@Column
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = layer.text,
            onValueChange = { update(layer.copy(text = it)) },
            singleLine = true,
            label = { Text("Caption", fontSize = 11.sp) },
            colors = TextFieldDefaults.colors(
                focusedTextColor = FlowText,
                unfocusedTextColor = FlowText,
                focusedContainerColor = FlowElevated,
                unfocusedContainerColor = FlowElevated,
                focusedIndicatorColor = FlowCream,
                unfocusedIndicatorColor = FlowLine,
                cursorColor = FlowCream,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(6.dp))

        // fonts: built-in families plus anything the user imported
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Chip("+ FONT", active = false) { onImportFont() }
            }
            items(fonts, key = { it.name + (it.file ?: "") }) { f ->
                val active = if (f.file == null) {
                    layer.fontFile == null &&
                        layer.style == when (f.name) {
                            "Serif" -> TextStyle.SERIF
                            "Mono" -> TextStyle.MONO
                            else -> TextStyle.SANS
                        }
                } else {
                    layer.fontFile == f.file
                }
                Box {
                    Chip(f.name, active = active) {
                        update(
                            if (f.file == null) {
                                layer.copy(
                                    fontFile = null,
                                    style = when (f.name) {
                                        "Serif" -> TextStyle.SERIF
                                        "Mono" -> TextStyle.MONO
                                        else -> TextStyle.SANS
                                    },
                                )
                            } else {
                                layer.copy(fontFile = f.file)
                            },
                        )
                    }
                }
            }
            item { Chip("BOLD", active = layer.bold) { update(layer.copy(bold = !layer.bold)) } }
            item { Chip("SHADOW", active = layer.shadow) { update(layer.copy(shadow = !layer.shadow)) } }
        }

        // Manage imported fonts
        if (fonts.any { it.file != null }) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(fonts.filter { it.file != null }, key = { "del_" + (it.file ?: "") }) { f ->
                    Chip("✕ " + f.name.take(10), active = false) { onDeleteFont(f) }
                }
            }
        }

        SliderRow("SIZE", layer.size, 0.02f..0.30f) { update(layer.copy(size = it)) }
        SliderRow("TRACK", layer.letterSpacing, 0f..0.5f) { update(layer.copy(letterSpacing = it)) }
        SliderRow("Y", layer.y, 0.05f..0.95f) { update(layer.copy(y = it)) }
        SliderRow("X", layer.x, 0.05f..0.95f) { update(layer.copy(x = it)) }
        SliderRow("ROTATE", layer.rotation, -45f..45f) { update(layer.copy(rotation = it)) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("WHITE", active = layer.color == 0xFFFFFFFF.toInt()) {
                update(layer.copy(color = 0xFFFFFFFF.toInt()))
            }
            Chip("BLACK", active = layer.color == 0xFF000000.toInt()) {
                update(layer.copy(color = 0xFF000000.toInt()))
            }
            Chip("CREAM", active = layer.color == 0xFFEDE7DC.toInt()) {
                update(layer.copy(color = 0xFFEDE7DC.toInt()))
            }
            Chip("DELETE", active = false) {
                val rest = overlays.texts.filterNot { it.id == layer.id }
                onChange(overlays.copy(texts = rest))
                rest.firstOrNull()?.let { onSelect(it.id) }
            }
        }
    }
}

/** Sticker picker. */
@Composable
fun StickerPanel(
    overlays: Overlays,
    onChange: (Overlays) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 6.dp),
    ) {
        Text(
            text = "TAP TO PLACE",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 1.4.sp,
            color = FlowTextDim,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(StickerPalette, key = { it }) { glyph ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(FlowElevated)
                        .border(1.dp, FlowLine, RoundedCornerShape(9.dp))
                        .clickable {
                            val s = StickerLayer(
                                id = overlays.nextStickerId(),
                                emoji = glyph,
                                x = 0.5f,
                                y = 0.5f,
                            )
                            onChange(overlays.copy(stickers = overlays.stickers + s))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, color = FlowText, fontSize = 18.sp)
                }
            }
        }

        if (overlays.stickers.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${overlays.stickers.size} placed",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = FlowTextDim,
                    modifier = Modifier.weight(1f),
                )
                Chip("REMOVE LAST", active = false) {
                    onChange(overlays.copy(stickers = overlays.stickers.dropLast(1)))
                }
                Chip("CLEAR", active = false) {
                    onChange(overlays.copy(stickers = emptyList()))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) FlowCream else FlowElevated)
            .border(1.dp, if (active) FlowCream else FlowLine, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            letterSpacing = 0.7.sp,
            color = if (active) Color.Black else FlowText,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
