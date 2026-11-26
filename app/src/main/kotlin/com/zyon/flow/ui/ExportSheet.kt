/**
 * Flow — the export sheet.
 *
 * A full-height sheet rather than a small dialog: export is a decision with four
 * parts (format, size, quality, where it goes) and cramming it into a narrow card
 * made every option feel cramped.
 *
 * Layout, top to bottom: a grab handle, the title, a preview of exactly what will
 * be written, then the three groups of options, then one large action button.
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.R
import com.zyon.flow.edit.ExportFormat
import com.zyon.flow.edit.ExportSettings
import com.zyon.flow.edit.ExportSize
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim
import com.zyon.flow.ui.theme.Frost
import com.zyon.flow.ui.theme.FrostedPanel
import com.zyon.flow.ui.theme.GlassTint
import com.zyon.flow.ui.theme.glassControl
import java.util.Locale

@Composable
fun ExportSheet(
    settings: ExportSettings,
    sourceWidth: Int,
    sourceHeight: Int,
    preview: Bitmap? = null,
    onChange: (ExportSettings) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: Bitmap? = null,
) {
    // the size that will actually be written
    val srcLong = maxOf(sourceWidth, sourceHeight).coerceAtLeast(1)
    val longEdge = (settings.size.maxDim ?: srcLong).coerceAtMost(srcLong)
    val scale = longEdge.toFloat() / srcLong
    val outW = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val outH = (sourceHeight * scale).toInt().coerceAtLeast(1)
    val estimatedMb = settings.estimatedMb(outW, outH)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp),
            contentAlignment = Alignment.Center,
        ) {
            FrostedPanel(
                backdrop = backdrop,
                shape = RoundedCornerShape(26.dp),
                frost = Frost.MEDIUM,
                padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    GrabHandle()

                    // ---- title row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.export_title),
                                color = FlowText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp,
                            )
                            Text(
                                text = stringResource(R.string.export_sub),
                                color = FlowTextDim,
                                fontSize = 10.sp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .glassControl(shape = CircleShape, tint = GlassTint.CONTROL)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            CloseGlyph(FlowTextDim)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ---- what gets written
                    OutputCard(
                        preview = preview ?: backdrop,
                        width = outW,
                        height = outH,
                        megabytes = estimatedMb,
                        format = settings.format.label,
                    )

                    Spacer(Modifier.height(20.dp))

                    // ---- format
                    SectionTitle(stringResource(R.string.export_format))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExportFormat.entries.forEach { f ->
                            FormatCard(
                                label = f.label,
                                hint = when (f) {
                                    ExportFormat.JPEG -> "menor"
                                    ExportFormat.PNG -> "lossless"
                                    ExportFormat.WEBP -> "moderno"
                                },
                                active = settings.format == f,
                                modifier = Modifier.weight(1f),
                            ) { onChange(settings.copy(format = f)) }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // ---- size
                    SectionTitle(stringResource(R.string.export_size))
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        ExportSize.entries.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { s ->
                                    SizeCard(
                                        label = s.label,
                                        hint = if (s.maxDim == null) "sem reduzir" else "lado maior",
                                        active = settings.size == s,
                                        modifier = Modifier.weight(1f),
                                    ) { onChange(settings.copy(size = s)) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    // ---- quality
                    if (settings.format.usesQuality) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionTitle(stringResource(R.string.export_quality), inline = true)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${settings.quality}",
                                color = FlowCream,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Slider(
                            value = settings.quality.toFloat(),
                            onValueChange = {
                                onChange(settings.copy(quality = it.toInt().coerceIn(70, 100)))
                            },
                            valueRange = 70f..100f,
                            steps = 29,
                            colors = SliderDefaults.colors(
                                thumbColor = FlowCream,
                                activeTrackColor = FlowCream.copy(alpha = 0.75f),
                                inactiveTrackColor = FlowLine,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.export_lossless_note),
                            color = FlowTextDim,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 22.dp),
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    // ---- action
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(FlowCream)
                            .clickable(onClick = onExport)
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExportGlyph(Color(0xFF101014))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.export_do),
                                color = Color(0xFF101014),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.export_destination),
                        color = FlowTextDim.copy(alpha = 0.75f),
                        fontSize = 8.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun GrabHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
        )
    }
}

@Composable
private fun OutputCard(
    preview: Bitmap?,
    width: Int,
    height: Int,
    megabytes: Float,
    format: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 62.dp, height = 62.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(FlowElevated),
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "$width × $height",
                color = FlowText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = format + "  ·  ~" + String.format(Locale.US, "%.1f", megabytes) + " MB",
                color = FlowTextDim,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, inline: Boolean = false) {
    Text(
        text = text,
        color = FlowTextDim,
        fontSize = 8.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.Medium,
        modifier = if (inline) Modifier else Modifier.padding(start = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun FormatCard(
    label: String,
    hint: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (active) FlowCream.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.20f))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) FlowCream.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (active) FlowCream else FlowText,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
        Text(
            text = hint,
            color = FlowTextDim.copy(alpha = if (active) 0.9f else 0.6f),
            fontSize = 7.sp,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun SizeCard(
    label: String,
    hint: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) FlowCream.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.20f))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) FlowCream.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (active) FlowCream else FlowText,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = hint,
                color = FlowTextDim.copy(alpha = 0.65f),
                fontSize = 7.sp,
            )
        }
        if (active) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(FlowCream),
            )
        }
    }
}

@Composable
private fun CloseGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(11.dp)) {
        val w = size.minDimension * 0.13f
        drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Offset(size.width, size.height),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(tint, androidx.compose.ui.geometry.Offset(size.width, 0f),
            androidx.compose.ui.geometry.Offset(0f, size.height),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun ExportGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(13.dp)) {
        val w = size.minDimension * 0.12f
        // tray
        val tray = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.58f)
            lineTo(size.width * 0.10f, size.height * 0.88f)
            lineTo(size.width * 0.90f, size.height * 0.88f)
            lineTo(size.width * 0.90f, size.height * 0.58f)
        }
        drawPath(tray, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w))
        // arrow up
        drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.70f),
            androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.10f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.10f),
            androidx.compose.ui.geometry.Offset(size.width * 0.26f, size.height * 0.34f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.10f),
            androidx.compose.ui.geometry.Offset(size.width * 0.74f, size.height * 0.34f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
