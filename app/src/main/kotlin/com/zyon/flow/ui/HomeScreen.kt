/**
 * Flow — home: imported photos plus saved projects.
 */

package com.zyon.flow.ui

import com.zyon.flow.R

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.edit.ImageOps
import com.zyon.flow.edit.LutPack
import com.zyon.flow.edit.PhotoStore
import com.zyon.flow.edit.Project
import com.zyon.flow.edit.ProjectStore
import com.zyon.flow.ui.theme.FlowBlack
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowSurface
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onOpenPhoto: (Uri, Project?) -> Unit,
    onOpenBatch: () -> Unit,
    onDeleteProject: (Project) -> Unit,
    /** Bumped by the caller whenever a project is saved, to refresh thumbnails. */
    refreshKey: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var projectThumbs by remember { mutableStateOf<Map<Long, Bitmap>>(emptyMap()) }

    LaunchedEffect(refreshKey) {
        projects = ProjectStore.load(context)
        projectThumbs = withContext(Dispatchers.IO) {
            val map = mutableMapOf<Long, Bitmap>()
            projects.take(24).forEach { p ->
                val lut = p.lutKey?.let { runCatching { LutPack.load(context, it) }.getOrNull() }
                val bmp = runCatching { ImageOps.load(context, p.uri(), 320) }.getOrNull()
                if (bmp != null) {
                    val rendered = runCatching { ImageOps.render(bmp, p.toState(), lut) }.getOrNull()
                    if (rendered != null) map[p.id] = rendered
                }
            }
            map
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val local = PhotoStore.import(context, uri)
                onOpenPhoto(local, null)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ---- header ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = FlowText,
                    style = MaterialTheme.typography.headlineMedium,
                    letterSpacing = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    color = FlowTextDim,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(FlowElevated)
                    .border(1.dp, FlowLine, RoundedCornerShape(11.dp))
                    .clickable { onOpenBatch() }
                    .padding(horizontal = 13.dp, vertical = 11.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_batch),
                    color = FlowText,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(FlowCream)
                    .clickable {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    .padding(horizontal = 15.dp, vertical = 11.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_import),
                    color = FlowBlack,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                SectionLabel(if (projects.isEmpty()) "PROJECTS" else "PROJECTS · ${projects.size}")
            }

            if (projects.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    EmptyProjects()
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    val thumb = projectThumbs[project.id]
                    var askingDelete by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(FlowElevated)
                            .combinedClickable(
                                onClick = { onOpenPhoto(project.uri(), project) },
                                onLongClick = { askingDelete = true },
                            ),
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // cube badge
                        project.lutKey?.let { key ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(5.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(FlowBlack.copy(alpha = 0.72f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = key.uppercase(),
                                    color = FlowCream,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    letterSpacing = 0.5.sp,
                                )
                            }
                        }

                        // delete affordance + confirmation, only while asking
                        if (askingDelete) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(FlowBlack.copy(alpha = 0.82f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.project_delete_q),
                                        color = FlowCream,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontSize = 10.sp,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(FlowCream)
                                                .clickable {
                                                    askingDelete = false
                                                    onDeleteProject(project)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.yes),
                                                color = FlowBlack,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(7.dp))
                                                .border(1.dp, FlowLine, RoundedCornerShape(7.dp))
                                                .clickable { askingDelete = false }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.no),
                                                color = FlowTextDim,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("NEW PHOTO")
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlowSurface)
                        .border(1.dp, FlowLine, RoundedCornerShape(14.dp))
                        .clickable {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(FlowElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlusGlyph(FlowCream)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.home_new_photo_hint),
                            color = FlowText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.home_cubes_note),
                            color = FlowTextDim,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = FlowTextDim,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun EmptyProjects() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlowSurface)
            .border(1.dp, FlowLine, RoundedCornerShape(14.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            color = FlowText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            color = FlowTextDim,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Small plus drawn with strokes so it matches the rest of the icon set. */
@Composable
fun PlusGlyph(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.1f
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.2f),
            androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.8f),
            strokeWidth = w,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(s * 0.2f, s * 0.5f),
            androidx.compose.ui.geometry.Offset(s * 0.8f, s * 0.5f),
            strokeWidth = w,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}
