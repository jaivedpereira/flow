/**
 * Flow — the library, rebuilt to the new shell.
 *
 * Two tabs: Início is a landing page with a hero card and the most recent edits;
 * Minhas fotos is the library itself, with filters, selection and albums.
 *
 * The header and bottom bar are drawn once here rather than duplicated per page.
 */

package com.zyon.flow.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.R
import com.zyon.flow.edit.Album
import com.zyon.flow.edit.AlbumStore
import com.zyon.flow.edit.ImageOps
import com.zyon.flow.edit.LutPack
import com.zyon.flow.edit.PhotoStore
import com.zyon.flow.edit.Project
import com.zyon.flow.edit.ProjectStore
import com.zyon.flow.ui.theme.FlowBlack
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim
import com.zyon.flow.ui.theme.GlassTint
import com.zyon.flow.ui.theme.glassControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab { HOME, PHOTOS }

@Composable
fun HomeScreen(
    onOpenPhoto: (Uri, Project?) -> Unit,
    onOpenBatch: () -> Unit,
    onDeleteProject: (Project) -> Unit,
    onOpenAbout: () -> Unit,
    refreshKey: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(LibraryTab.HOME) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var thumbs by remember { mutableStateOf<Map<Long, Bitmap>>(emptyMap()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }

    LaunchedEffect(refreshKey) {
        val loaded = ProjectStore.load(context)
        projects = loaded
        Project.rememberFavorites(loaded)
        albums = AlbumStore.load(context)
        thumbs = withContext(Dispatchers.IO) {
            val map = mutableMapOf<Long, Bitmap>()
            loaded.take(30).forEach { p ->
                val lut = p.lutKey?.let { runCatching { LutPack.load(context, it) }.getOrNull() }
                val bmp = runCatching { ImageOps.load(context, p.uri(), 340) }.getOrNull()
                if (bmp != null) {
                    runCatching { ImageOps.render(bmp, p.toState(), lut) }
                        .getOrNull()?.let { map[p.id] = it }
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

    fun importPhoto() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        LibraryHeader(onAbout = onOpenAbout)

        Box(Modifier.weight(1f)) {
            if (tab == LibraryTab.HOME) {
                HomeTab(
                    projects = projects,
                    thumbs = thumbs,
                    onImport = { importPhoto() },
                    onOpen = { p -> onOpenPhoto(p.uri(), p) },
                    onSeeAll = { tab = LibraryTab.PHOTOS },
                    onDelete = onDeleteProject,
                )
            } else {
                PhotosTab(
                    projects = projects,
                    thumbs = thumbs,
                    albums = albums,
                    onImport = { importPhoto() },
                    onOpen = { p -> onOpenPhoto(p.uri(), p) },
                    onToggleFavorite = { p ->
                        projects = ProjectStore.toggleFavorite(context, p.id)
                    },
                    onDelete = onDeleteProject,
                    onAlbumsChanged = { albums = it },
                )
            }
        }

        BottomNav(
            selected = tab,
            onSelect = { tab = it },
            onBatch = onOpenBatch,
        )
    }
}

// ---------------------------------------------------------------- chrome

@Composable
private fun LibraryHeader(onAbout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "flow",
                color = FlowText,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = stringResource(R.string.library_tagline),
                color = FlowTextDim,
                fontSize = 8.sp,
                letterSpacing = 2.2.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .glassControl(shape = CircleShape, tint = GlassTint.CONTROL)
                .clickable(onClick = onAbout),
            contentAlignment = Alignment.Center,
        ) {
            LibrarySlidersGlyph(FlowText)
        }
    }
}

@Composable
private fun BottomNav(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    onBatch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassControl(shape = RoundedCornerShape(20.dp), tint = GlassTint.PANEL),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab(
            label = stringResource(R.string.nav_home),
            icon = { HomeGlyph(it) },
            active = selected == LibraryTab.HOME,
            modifier = Modifier.weight(1f),
        ) { onSelect(LibraryTab.HOME) }
        NavTab(
            label = stringResource(R.string.nav_photos),
            icon = { PhotosGlyph(it) },
            active = selected == LibraryTab.PHOTOS,
            modifier = Modifier.weight(1f),
        ) { onSelect(LibraryTab.PHOTOS) }
        NavTab(
            label = stringResource(R.string.home_batch),
            icon = { BatchGlyph(it) },
            active = false,
            modifier = Modifier.weight(0.8f),
        ) { onBatch() }
    }
}

@Composable
private fun NavTab(
    label: String,
    icon: @Composable (Color) -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (active) FlowElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(if (active) FlowCream else FlowTextDim)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (active) FlowText else FlowTextDim,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.3.sp,
        )
    }
}

// ---------------------------------------------------------------- glyphs

@Composable
internal fun LibrarySlidersGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(15.dp)) {
        val w = size.minDimension * 0.09f
        for (i in 0 until 3) {
            val y = size.height * (0.22f + i * 0.28f)
            drawLine(
                tint,
                androidx.compose.ui.geometry.Offset(0f, y),
                androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = w,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawCircle(
                tint,
                radius = size.minDimension * 0.11f,
                center = androidx.compose.ui.geometry.Offset(size.width * (0.30f + i * 0.20f), y),
            )
        }
    }
}

@Composable
private fun HomeGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(17.dp)) {
        val w = size.minDimension * 0.10f
        val roof = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.48f)
            lineTo(size.width * 0.5f, size.height * 0.14f)
            lineTo(size.width * 0.90f, size.height * 0.48f)
        }
        drawPath(roof, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w))
        val box = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.46f)
            lineTo(size.width * 0.22f, size.height * 0.88f)
            lineTo(size.width * 0.78f, size.height * 0.88f)
            lineTo(size.width * 0.78f, size.height * 0.46f)
        }
        drawPath(box, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w))
    }
}

@Composable
private fun PhotosGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(17.dp)) {
        val w = size.minDimension * 0.10f
        drawRoundRect(
            tint,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.16f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.68f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.14f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
        )
        drawCircle(
            tint,
            radius = size.minDimension * 0.07f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.38f),
        )
    }
}

@Composable
private fun BatchGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(17.dp)) {
        val w = size.minDimension * 0.10f
        for (i in 0 until 3) {
            val off = size.width * (0.08f + i * 0.09f)
            drawRoundRect(
                tint.copy(alpha = 0.35f + i * 0.32f),
                topLeft = androidx.compose.ui.geometry.Offset(off, off),
                size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.64f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.10f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
            )
        }
    }
}
