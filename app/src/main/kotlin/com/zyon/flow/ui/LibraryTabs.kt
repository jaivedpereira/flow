/**
 * Flow — the two library pages.
 *
 * HomeTab is the landing page: one big card that says what the app is for, then
 * the most recent edits as a strip. PhotosTab is the library itself: filters,
 * sorting, selection and albums.
 */

package com.zyon.flow.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zyon.flow.R
import com.zyon.flow.edit.Album
import com.zyon.flow.edit.AlbumStore
import com.zyon.flow.edit.Project
import com.zyon.flow.ui.theme.FlowBlack
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim
import com.zyon.flow.ui.theme.Frost
import com.zyon.flow.ui.theme.FrostedPanel
import com.zyon.flow.ui.theme.GlassTint
import com.zyon.flow.ui.theme.glass
import com.zyon.flow.ui.theme.glassControl

private enum class PhotoFilter { ALL, FAVOURITES, ALBUMS }

// ================================================================ INÍCIO

@Composable
internal fun HomeTab(
    projects: List<Project>,
    thumbs: Map<Long, Bitmap>,
    onImport: () -> Unit,
    onOpen: (Project) -> Unit,
    onSeeAll: () -> Unit,
    onDelete: (Project) -> Unit,
) {
    val recent = projects.take(6)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ---- hero
        item(span = { GridItemSpan(3) }) {
            HeroCard(
                cover = recent.firstOrNull()?.let { thumbs[it.id] },
                onImport = onImport,
            )
        }

        // ---- section title
        item(span = { GridItemSpan(3) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_recent),
                    color = FlowText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.home_see_all),
                    color = FlowTextDim,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = projects.isNotEmpty(), onClick = onSeeAll)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        if (recent.isEmpty()) {
            item(span = { GridItemSpan(3) }) { EmptyLibrary(onImport) }
        } else {
            items(recent, key = { it.id }) { p ->
                var askingDelete by remember { mutableStateOf(false) }
                PhotoTile(
                    thumb = thumbs[p.id],
                    label = p.label(),
                    edited = true,
                    favorite = p.favorite,
                    modifier = Modifier.aspectRatio(1f),
                    onClick = { onOpen(p) },
                    onLongClick = { askingDelete = true },
                )
                if (askingDelete) {
                    DeleteDialog(p, onDismiss = { askingDelete = false }) {
                        askingDelete = false
                        onDelete(p)
                    }
                }
            }

            item(span = { GridItemSpan(3) }) {
                LibraryFooter(projects.size)
            }
        }
    }
}

@Composable
private fun HeroCard(cover: Bitmap?, onImport: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(232.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(FlowElevated),
    ) {
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // readable text over any photo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.28f),
                            0.55f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(FlowCream.copy(alpha = 0.16f))
                    .border(1.dp, FlowCream.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SparkGlyph(FlowCream)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.hero_pill),
                    color = FlowCream,
                    fontSize = 7.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(R.string.hero_title),
                color = Color.White,
                fontSize = 23.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.hero_sub),
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(FlowCream.copy(alpha = 0.20f))
                    .border(1.dp, FlowCream.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onImport)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.hero_cta),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "↗", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SparkGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(9.dp)) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.5f, 0f)
            lineTo(size.width * 0.62f, size.height * 0.38f)
            lineTo(size.width, size.height * 0.5f)
            lineTo(size.width * 0.62f, size.height * 0.62f)
            lineTo(size.width * 0.5f, size.height)
            lineTo(size.width * 0.38f, size.height * 0.62f)
            lineTo(0f, size.height * 0.5f)
            lineTo(size.width * 0.38f, size.height * 0.38f)
            close()
        }
        drawPath(p, tint)
    }
}

// ================================================================ MINHAS FOTOS

@Composable
internal fun PhotosTab(
    projects: List<Project>,
    thumbs: Map<Long, Bitmap>,
    albums: List<Album>,
    onImport: () -> Unit,
    onOpen: (Project) -> Unit,
    onToggleFavorite: (Project) -> Unit,
    onDelete: (Project) -> Unit,
    onAlbumsChanged: (List<Album>) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var filter by remember { mutableStateOf(PhotoFilter.ALL) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var creatingAlbum by remember { mutableStateOf(false) }

    val favourites = projects.filter { it.favorite }
    val shown = when (filter) {
        PhotoFilter.ALL -> projects
        PhotoFilter.FAVOURITES -> favourites
        PhotoFilter.ALBUMS -> selectedAlbum?.let { a -> projects.filter { it.id in a.projectIds } } ?: projects
    }

    Column(Modifier.fillMaxSize()) {
        // ---- title row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 18.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nav_photos),
                    color = FlowText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.photos_sub),
                    color = FlowTextDim,
                    fontSize = 10.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .glassControl(shape = CircleShape, tint = GlassTint.CONTROL)
                    .clickable(onClick = onImport),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "+", color = FlowCream, fontSize = 19.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- filter pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FilterPill(
                text = stringResource(R.string.filter_all) + " " + projects.size,
                active = filter == PhotoFilter.ALL,
                icon = null,
            ) { filter = PhotoFilter.ALL; selectedAlbum = null }
            FilterPill(
                text = stringResource(R.string.filter_fav) + " " + favourites.size,
                active = filter == PhotoFilter.FAVOURITES,
                icon = "♥",
            ) { filter = PhotoFilter.FAVOURITES; selectedAlbum = null }
            FilterPill(
                text = stringResource(R.string.filter_albums),
                active = filter == PhotoFilter.ALBUMS,
                icon = "▤",
            ) { filter = PhotoFilter.ALBUMS }
        }

        // ---- toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.sort_recent) + " ▾",
                color = FlowTextDim,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.select_mode),
                color = FlowTextDim,
                fontSize = 10.sp,
            )
        }

        // ---- grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (filter == PhotoFilter.ALBUMS && selectedAlbum == null) {
                // album list
                items(albums, key = { "alb_" + it.id }) { album ->
                    AlbumTile(
                        album = album,
                        count = projects.count { it.id in album.projectIds },
                        onClick = { selectedAlbum = album },
                        onDelete = {
                            onAlbumsChanged(AlbumStore.delete(context, album.id))
                        },
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    CreateAlbumCard { creatingAlbum = true }
                }
                if (albums.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = stringResource(R.string.albums_hint),
                            color = FlowTextDim,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            } else {
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = stringResource(R.string.photos_empty),
                            color = FlowTextDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 30.dp),
                        )
                    }
                }
                items(shown, key = { it.id }) { p ->
                    var askingDelete by remember { mutableStateOf(false) }
                    PhotoTile(
                        thumb = thumbs[p.id],
                        label = p.label(),
                        edited = true,
                        favorite = p.favorite,
                        modifier = Modifier.aspectRatio(0.86f),
                        onClick = { onOpen(p) },
                        onLongClick = { askingDelete = true },
                        onFavorite = { onToggleFavorite(p) },
                    )
                    if (askingDelete) {
                        PhotoActionDialog(
                            albums = albums,
                            onDismiss = { askingDelete = false },
                            onAddToAlbum = { album ->
                                onAlbumsChanged(AlbumStore.add(context, album.id, listOf(p.id)))
                                askingDelete = false
                            },
                            onDelete = {
                                askingDelete = false
                                onDelete(p)
                            },
                        )
                    }
                }
            }

            if (filter != PhotoFilter.ALBUMS) {
                item(span = { GridItemSpan(2) }) {
                    LibraryFooter(shown.size)
                }
            }
        }
    }

    if (creatingAlbum) {
        AlbumNameDialog(
            onCreate = { name ->
                onAlbumsChanged(AlbumStore.create(context, name))
                creatingAlbum = false
            },
            onDismiss = { creatingAlbum = false },
        )
    }
}

// ================================================================ pieces

@Composable
private fun PhotoTile(
    thumb: Bitmap?,
    label: String,
    edited: Boolean,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorite: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FlowElevated)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // numbered badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(FlowBlack.copy(alpha = 0.62f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(text = label, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Medium)
        }

        // edited tag
        if (edited) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(FlowBlack.copy(alpha = 0.62f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.tag_edited),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 7.sp,
                )
            }
        }

        // favourite
        if (onFavorite != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(FlowBlack.copy(alpha = 0.55f))
                    .clickable(onClick = onFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (favorite) "♥" else "♡",
                    color = if (favorite) Color(0xFFFF6B8A) else Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    active: Boolean,
    icon: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .glassControl(
                shape = RoundedCornerShape(18.dp),
                tint = if (active) GlassTint.ACTIVE else GlassTint.PANEL,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Text(
                text = icon,
                color = if (active) FlowCream else FlowTextDim,
                fontSize = 9.sp,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = if (active) FlowCream else FlowTextDim,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AlbumTile(
    album: Album,
    count: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FlowElevated)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(text = "▤", color = FlowCream.copy(alpha = 0.8f), fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.name,
            color = FlowText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$count",
            color = FlowTextDim,
            fontSize = 9.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.delete),
            color = FlowTextDim.copy(alpha = 0.7f),
            fontSize = 8.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CreateAlbumCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FlowElevated)
            .border(1.dp, FlowLine, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.album_create),
                color = FlowText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.album_create_sub),
                color = FlowTextDim,
                fontSize = 9.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .glassControl(shape = CircleShape, tint = GlassTint.CONTROL),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", color = FlowCream, fontSize = 16.sp)
        }
    }
}

@Composable
private fun LibraryFooter(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = count.toString() + " " + stringResource(R.string.photos_footer),
            color = FlowTextDim.copy(alpha = 0.75f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            color = FlowText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            color = FlowTextDim,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .glassControl(shape = RoundedCornerShape(11.dp), tint = GlassTint.ACTIVE)
                .clickable(onClick = onImport)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.home_new_photo),
                color = FlowCream,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Long-press actions for a photo: put it in an album, or delete it. The album
 * list only appears once at least one album exists, so the common case stays a
 * two-choice dialog.
 */
@Composable
private fun PhotoActionDialog(
    albums: List<Album>,
    onDismiss: () -> Unit,
    onAddToAlbum: (Album) -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(20.dp), tint = GlassTint.PANEL, elevation = 16.dp)
                .padding(18.dp),
        ) {
            if (albums.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.add_to_album),
                    color = FlowTextDim,
                    fontSize = 8.sp,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(8.dp))
                albums.forEach { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(FlowElevated)
                            .clickable { onAddToAlbum(album) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "▤", color = FlowCream.copy(alpha = 0.8f), fontSize = 11.sp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = album.name,
                            color = FlowText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = stringResource(R.string.delete_photo_q),
                color = FlowText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.delete_photo_body),
                color = FlowTextDim,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassControl(shape = RoundedCornerShape(11.dp), tint = GlassTint.PANEL)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.cancel), color = FlowTextDim, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassControl(shape = RoundedCornerShape(11.dp), tint = GlassTint.ACTIVE)
                        .clickable(onClick = onDelete)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.delete), color = FlowCream, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DeleteDialog(project: Project, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(20.dp), tint = GlassTint.PANEL, elevation = 16.dp)
                .padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.delete_photo_q),
                color = FlowText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.delete_photo_body),
                color = FlowTextDim,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassControl(shape = RoundedCornerShape(11.dp), tint = GlassTint.PANEL)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.cancel), color = FlowTextDim, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassControl(shape = RoundedCornerShape(11.dp), tint = GlassTint.ACTIVE)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.delete), color = FlowCream, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AlbumNameDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(20.dp), tint = GlassTint.PANEL, elevation = 16.dp)
                .padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.album_name),
                color = FlowText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = FlowText, fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassControl(shape = RoundedCornerShape(11.dp), tint = GlassTint.PANEL)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.cancel), color = FlowTextDim, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassControl(shape = RoundedCornerShape(11.dp), tint = GlassTint.ACTIVE)
                        .clickable { onCreate(name) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.album_create_do), color = FlowCream, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Short badge shown on a tile: the project's own short id. */
private fun Project.label(): String {
    val s = id.toString()
    return s.takeLast(2).trimStart('0').ifBlank { s.takeLast(1) }
}
