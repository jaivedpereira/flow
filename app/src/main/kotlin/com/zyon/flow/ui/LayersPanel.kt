/**
 * Flow — the Layers panel.
 *
 * The user adds empty layers and fills each one with whatever they want: a cube,
 * a colour grade, an artistic effect, a caption or a sticker. The list is drawn
 * top-of-stack first, so what you see is what covers the photo. Each row can be
 * hidden, locked, moved and deleted; the selected row is what the other editing
 * tabs act on.
 */

package com.zyon.flow.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.R
import com.zyon.flow.edit.EditState
import com.zyon.flow.edit.LayerContent
import com.zyon.flow.edit.PhotoLayer
import com.zyon.flow.ui.theme.glass
import com.zyon.flow.ui.theme.glassControl
import com.zyon.flow.ui.theme.GlassTint
import com.zyon.flow.ui.theme.Frost
import com.zyon.flow.ui.theme.FrostedPanel
import com.zyon.flow.ui.theme.FlowBlack
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim

private val THUMB = 56.dp

@Composable
fun LayersPanel(
    state: EditState,
    thumbs: Map<Long, Bitmap>,
    baseThumb: Bitmap?,
    onAddLayer: () -> Unit,
    onPickContent: (PhotoLayer) -> Unit,
    onSelect: (Long?) -> Unit,
    onToggleVisible: (Long) -> Unit,
    onToggleLock: (Long) -> Unit,
    onMove: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // drawn top of stack first
    val stack = state.layers.reversed()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        // ---- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LayersStackGlyph(FlowCream)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.mode_layers),
                color = FlowText,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                letterSpacing = 0.6.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .glassControl(shape = RoundedCornerShape(10.dp), tint = GlassTint.ACTIVE)
                    .clickable(onClick = onAddLayer)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(
                    text = stringResource(R.string.layers_add),
                    color = FlowCream,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // ---- user layers, top first
        stack.forEachIndexed { index, layer ->
            LayerRow(
                layer = layer,
                thumb = thumbs[layer.id],
                selected = layer.id == state.selectedLayerId,
                isTop = index == 0,
                isBottom = index == stack.lastIndex,
                onClick = { onSelect(if (layer.id == state.selectedLayerId) null else layer.id) },
                onPickContent = { onPickContent(layer) },
                onToggleVisible = { onToggleVisible(layer.id) },
                onToggleLock = { onToggleLock(layer.id) },
                onUp = { onMove(layer.id, true) },
                onDown = { onMove(layer.id, false) },
                onDelete = { onDelete(layer.id) },
            )
        }

        if (state.layers.isEmpty()) {
            Text(
                text = stringResource(R.string.layers_empty),
                color = FlowTextDim,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        // ---- the photo, always at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 3.dp)
                .glass(shape = RoundedCornerShape(12.dp), tint = GlassTint.PANEL)
                .clickable { onSelect(null) }
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(THUMB)
                    .clip(RoundedCornerShape(7.dp))
                    .background(FlowBlack),
            ) {
                if (baseThumb != null) {
                    Image(
                        bitmap = baseThumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(FlowBlack.copy(alpha = 0.66f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LockGlyph(tint = FlowCream)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.layers_background),
                    color = if (state.selectedLayerId == null) FlowText else FlowTextDim,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                )
                Text(
                    text = stringResource(R.string.layers_base_note),
                    color = FlowTextDim.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                )
            }
            if (state.selectedLayerId == null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(FlowCream),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.layers_hint),
            color = FlowTextDim.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            lineHeight = 11.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun LayerRow(
    layer: PhotoLayer,
    thumb: Bitmap?,
    selected: Boolean,
    isTop: Boolean,
    isBottom: Boolean,
    onClick: () -> Unit,
    onPickContent: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLock: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .glass(
                shape = RoundedCornerShape(12.dp),
                tint = if (selected) GlassTint.ACTIVE else GlassTint.PANEL,
                rimAlpha = if (selected) 0.42f else 0.18f,
            )
            // An empty layer has nothing to select, so a tap on it opens the
            // picker instead of silently doing nothing.
            .clickable(onClick = if (layer.content == LayerContent.EMPTY) onPickContent else onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(THUMB)
                .clip(RoundedCornerShape(7.dp))
                .background(FlowBlack)
                // Tapping the thumbnail is the obvious way to say what a layer
                // holds; the eye and lock sit on top with their own targets.
                .clickable(onClick = onPickContent),
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = if (layer.visible) 1f else 0.30f,
                )
            }
            // eye
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(FlowBlack.copy(alpha = 0.66f))
                    .clickable(onClick = onToggleVisible),
                contentAlignment = Alignment.Center,
            ) {
                EyeGlyph(
                    open = layer.visible,
                    tint = if (layer.visible) FlowCream else FlowTextDim,
                )
            }
            // lock
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(FlowBlack.copy(alpha = 0.66f))
                    .clickable(onClick = onToggleLock),
                contentAlignment = Alignment.Center,
            ) {
                LockGlyph(tint = if (layer.locked) FlowCream else FlowTextDim.copy(alpha = 0.65f))
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onPickContent),
        ) {
            Text(
                text = layer.displayName,
                color = if (layer.visible) FlowText else FlowTextDim,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (layer.content == LayerContent.EMPTY) {
                    stringResource(R.string.layers_tap_to_fill)
                } else {
                    layer.content.label.uppercase()
                },
                color = FlowCream.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                letterSpacing = 0.5.sp,
            )
        }

        if (!layer.locked) {
            IconSquare(if (isTop) null else "↑", onUp)
            IconSquare(if (isBottom) null else "↓", onDown)
            BinSquare(onDelete)
        }
    }
}

@Composable
private fun IconSquare(label: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 3.dp)
            .size(26.dp)
            .glassControl(shape = RoundedCornerShape(7.dp), tint = GlassTint.CONTROL)
            .clickable(enabled = label != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label ?: "",
            color = if (label != null) FlowTextDim else Color.Transparent,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun BinSquare(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 3.dp)
            .size(26.dp)
            .glassControl(shape = RoundedCornerShape(7.dp), tint = GlassTint.CONTROL)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(11.dp)) {
            val w = size.minDimension * 0.11f
            val c = FlowTextDim
            drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height * 0.22f),
                androidx.compose.ui.geometry.Offset(size.width, size.height * 0.22f), strokeWidth = w)
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.22f),
                androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.10f), strokeWidth = w)
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.22f),
                androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.10f), strokeWidth = w)
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.10f),
                androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.10f), strokeWidth = w)
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.22f),
                androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height), strokeWidth = w)
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.84f, size.height * 0.22f),
                androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height), strokeWidth = w)
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height),
                androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height), strokeWidth = w)
        }
    }
}

/** Modal content picker shown when a layer is added or its body is tapped. */
@Composable
fun LayerContentPicker(
    layer: PhotoLayer,
    onPick: (LayerContent) -> Unit,
    onDismiss: () -> Unit,
    backdrop: android.graphics.Bitmap? = null,
) {
    androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
        androidx.compose.foundation.layout.Box(
            // widthIn first, then 90%: the cap has to be in the constraint chain
            // before the fraction is resolved, otherwise it does nothing.
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.90f),
            contentAlignment = Alignment.Center,
        ) {
        FrostedPanel(
            backdrop = backdrop,
            shape = RoundedCornerShape(22.dp),
            frost = Frost.MEDIUM,
            padding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.layers_choose),
                color = FlowText,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            val options = listOf(
                LayerContent.CUBE to stringResource(R.string.layers_kind_cube),
                LayerContent.COLOUR to stringResource(R.string.layers_kind_colour),
                LayerContent.EFFECT to stringResource(R.string.layers_kind_effect),
                LayerContent.TEXT to stringResource(R.string.layers_kind_text),
                LayerContent.STICKER to stringResource(R.string.layers_kind_sticker),
            )
            options.forEach { (content, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .glassControl(
                            shape = RoundedCornerShape(11.dp),
                            tint = if (layer.content == content) GlassTint.ACTIVE else GlassTint.PANEL,
                        )
                        .clickable { onPick(content) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(kindColor(content)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        color = FlowText,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 12.sp,
                    )
                }
            }

            if (layer.content != LayerContent.EMPTY) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.layers_clear),
                    color = FlowTextDim,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(LayerContent.EMPTY) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        }
        }
    }
}

private fun kindColor(content: LayerContent): Color = when (content) {
    LayerContent.EMPTY -> FlowLine
    LayerContent.CUBE -> Color(0xFFD67A68)
    LayerContent.COLOUR -> Color(0xFF6BB6FF)
    LayerContent.EFFECT -> Color(0xFF8E5BD0)
    LayerContent.TEXT -> Color(0xFFEDE7DC)
    LayerContent.STICKER -> Color(0xFFE08A3C)
}

@Composable
private fun EyeGlyph(open: Boolean, tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
        val w = size.minDimension * 0.10f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height / 2f)
            cubicTo(
                size.width * 0.25f, 0f,
                size.width * 0.75f, 0f,
                size.width, size.height / 2f,
            )
            cubicTo(
                size.width * 0.75f, size.height,
                size.width * 0.25f, size.height,
                0f, size.height / 2f,
            )
        }
        if (open) {
            drawPath(path, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w))
            drawCircle(tint, radius = size.minDimension * 0.12f)
        } else {
            drawPath(path, tint.copy(alpha = 0.45f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w))
            drawLine(
                tint,
                androidx.compose.ui.geometry.Offset(0f, size.height),
                androidx.compose.ui.geometry.Offset(size.width, 0f),
                strokeWidth = w * 1.4f,
            )
        }
    }
}

@Composable
private fun LockGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(9.dp)) {
        val w = size.minDimension * 0.11f
        drawRect(
            tint,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.48f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.46f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.14f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.42f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
        )
    }
}

@Composable
private fun LayersStackGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(15.dp)) {
        val s = size.minDimension
        val w = s * 0.08f
        repeat(3) { i ->
            val y = s * (0.26f + i * 0.22f)
            drawLine(tint, androidx.compose.ui.geometry.Offset(s * 0.5f, y),
                androidx.compose.ui.geometry.Offset(s * 0.88f, y + s * 0.11f),
                strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(tint, androidx.compose.ui.geometry.Offset(s * 0.5f, y),
                androidx.compose.ui.geometry.Offset(s * 0.12f, y + s * 0.11f),
                strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(tint, androidx.compose.ui.geometry.Offset(s * 0.12f, y + s * 0.11f),
                androidx.compose.ui.geometry.Offset(s * 0.5f, y + s * 0.22f),
                strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(tint, androidx.compose.ui.geometry.Offset(s * 0.88f, y + s * 0.11f),
                androidx.compose.ui.geometry.Offset(s * 0.5f, y + s * 0.22f),
                strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
