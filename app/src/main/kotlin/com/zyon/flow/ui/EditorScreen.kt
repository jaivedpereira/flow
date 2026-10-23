/**
 * Flow — editor: preset carousel with live photo thumbnails.
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.edit.Adjustments
import com.zyon.flow.edit.EditState
import com.zyon.flow.edit.Effect
import com.zyon.flow.edit.Effects
import com.zyon.flow.edit.ImageOps
import com.zyon.flow.edit.Lut
import com.zyon.flow.edit.LutEntry
import com.zyon.flow.edit.LutPack
import com.zyon.flow.edit.MaskStore
import com.zyon.flow.edit.CubeIO
import com.zyon.flow.edit.MaskData
import com.zyon.flow.edit.MaskGeometry
import com.zyon.flow.edit.MaskShape
import com.zyon.flow.edit.MaskShapes
import com.zyon.flow.edit.Overlays
import com.zyon.flow.edit.TextLayer
import com.zyon.flow.edit.PhotoStore
import com.zyon.flow.edit.maskOverlayBitmap
import com.zyon.flow.edit.Project
import com.zyon.flow.edit.ProjectStore
import com.zyon.flow.edit.Recipe
import com.zyon.flow.edit.RecipeStore
import com.zyon.flow.ui.theme.FlowBlack
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowSurface
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_MAX = 900
private const val EXPORT_MAX = 3000

@Composable
fun EditorScreen(
    photoUri: Uri?,
    initial: Project?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var previewSrc by remember { mutableStateOf<Bitmap?>(null) }
    var exportSrc by remember { mutableStateOf<Bitmap?>(null) }
    var currentUri by remember { mutableStateOf<Uri?>(photoUri) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    /** Fully processed frame at 100% strength; the intensity slider blends toward it. */
    var lookStage by remember { mutableStateOf<Bitmap?>(null) }
    var state by remember { mutableStateOf(initial?.toState() ?: EditState()) }
    var activeLut by remember { mutableStateOf<Lut?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    var holdingOriginal by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var mask by remember { mutableStateOf(MaskData()) }
    var brushing by remember { mutableStateOf(false) }
    var brushRadius by remember { mutableStateOf(0.10f) }
    var maskVersion by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var splitMode by remember { mutableStateOf(false) }
    var splitFraction by remember { mutableStateOf(0.5f) }
    /** Which overlay the user is dragging on the canvas, if any. */
    var draggingOverlayId by remember { mutableStateOf<Long?>(null) }
    var selectedTextId by remember { mutableStateOf<Long?>(null) }
    var curvePresets by remember { mutableStateOf<List<com.zyon.flow.edit.CurvePreset>>(emptyList()) }
    var fonts by remember { mutableStateOf<List<com.zyon.flow.edit.FontEntry>>(emptyList()) }
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var histBins by remember { mutableStateOf<IntArray?>(null) }

    var allEntries by remember { mutableStateOf<List<LutEntry>>(emptyList()) }
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeGroup by remember { mutableStateOf("Legacy") }
    val thumbs = remember { mutableStateMapOf<String, Bitmap>() }
    val effectThumbs = remember { mutableStateMapOf<String, Bitmap>() }

    val cubePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = withContext(Dispatchers.IO) { CubeIO.importFrom(context, uri) }
            res.onSuccess { lut ->
                state = state.copy(
                    customLut = lut,
                    customLutName = CubeIO.displayName(uri),
                    lutKey = null,
                    lutIntensity = 1f,
                )
                activeLut = lut
                toast = "Cube loaded"
            }.onFailure {
                toast = "Invalid .cube file"
            }
        }
    }

    val cubeExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val lut = activeLut
        if (uri == null || lut == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        CubeIO.export(
                            lut,
                            java.io.OutputStreamWriter(os),
                            state.customLutName ?: "Flow Look",
                        )
                    } ?: error("no stream")
                }.isSuccess
            }
            toast = if (ok) "Cube exported" else "Could not export"
        }
    }

    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                com.zyon.flow.edit.FontStore.import(context, uri)
            }
            if (name != null) {
                fonts = withContext(Dispatchers.IO) { com.zyon.flow.edit.FontStore.list(context) }
                val sel = selectedTextId
                state = state.copy(
                    overlays = state.overlays.copy(
                        texts = state.overlays.texts.map {
                            if (sel == null || it.id == sel) it.copy(fontFile = name) else it
                        },
                    ),
                )
                toast = "Font added"
            } else {
                toast = "Unsupported font file"
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            currentUri = uri
            state = EditState()
            thumbs.clear()
            effectThumbs.clear()
            scope.launch {
                previewSrc = ImageOps.load(context, uri, PREVIEW_MAX)
                exportSrc = ImageOps.load(context, uri, EXPORT_MAX)
            }
        }
    }

    LaunchedEffect(photoUri) {
        recipes = RecipeStore.load(context)
        curvePresets = withContext(Dispatchers.IO) {
            com.zyon.flow.edit.CurveLibrary.BuiltIn + com.zyon.flow.edit.CurveLibrary.load(context)
        }
        val entries = withContext(Dispatchers.IO) { LutPack.entries(context) }
        allEntries = entries
        groups = buildGroups(entries)
        if (groups.isNotEmpty()) activeGroup = groups.first()
        val uri = photoUri ?: return@LaunchedEffect
        previewSrc = ImageOps.load(context, uri, PREVIEW_MAX)
        exportSrc = ImageOps.load(context, uri, EXPORT_MAX)
    }

    LaunchedEffect(state.lutKey) {
        val key = state.lutKey
        activeLut = if (key == null) null else withContext(Dispatchers.IO) {
            runCatching { LutPack.load(context, key) }.getOrNull()
        }
    }

    // Build carousel previews from the user's own photo, group by group.
    LaunchedEffect(previewSrc, activeGroup, allEntries) {
        val base = previewSrc ?: return@LaunchedEffect
        val group = allEntries.filter { groupOf(it) == activeGroup }
        withContext(Dispatchers.Default) {
            if (!thumbs.containsKey("__original")) {
                runCatching { ImageOps.filterThumb(base, null) }.getOrNull()?.let {
                    thumbs["__original"] = it
                }
            }
            group.forEach { entry ->
                if (thumbs.containsKey(entry.key)) return@forEach
                val lut = runCatching { LutPack.load(context, entry.key) }.getOrNull()
                runCatching { ImageOps.filterThumb(base, lut) }.getOrNull()?.let {
                    thumbs[entry.key] = it
                }
            }
        }
    }

    // Expensive pass: everything except the intensity blend. Re-runs only when the
    // look changes, so dragging an intensity slider stays real-time below.
    LaunchedEffect(
        previewSrc,
        activeLut,
        state.lutKey,
        state.effectId,
        state.effectParam,
        state.presetId,
        state.adjustments,
        state.transform,
        state.splitShadow,
        state.splitHighlight,
        state.curves,
        state.border,
        state.useMask,
        maskVersion,
        state.overlays,
        state.hsl,
    ) {
        val src = previewSrc ?: return@LaunchedEffect
        delay(50)
        val lut = activeLut
        val full = state.copy(lutIntensity = 1f, effectIntensity = 1f)
        val m = mask
        val resolver: (String?, Boolean) -> android.graphics.Typeface? = { name, bold ->
            com.zyon.flow.edit.FontStore.typeface(context, name, bold)
        }
        lookStage = withContext(Dispatchers.Default) { ImageOps.render(src, full, lut, m, resolver) }
    }

    // Cheap pass: a single cross-fade. This is what keeps the slider live.
    LaunchedEffect(lookStage, state.lutIntensity, state.effectIntensity, state.effectId, state.lutKey) {
        val src = previewSrc ?: return@LaunchedEffect
        val look = lookStage ?: return@LaunchedEffect
        val amount = when {
            state.effectId != "none" -> state.effectIntensity
            state.lutKey != null -> state.lutIntensity
            else -> 1f
        }
        rendered = withContext(Dispatchers.Default) {
            if (amount >= 0.999f) look else ImageOps.blend(src, look, amount)
        }
    }

    // Rebuild the mask whenever a gradient shape or its geometry changes.
    LaunchedEffect(state.maskShape, state.maskGeometry) {
        if (state.maskShape == MaskShape.BRUSH) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            when (state.maskShape) {
                MaskShape.LINEAR -> MaskShapes.fillLinear(mask, state.maskGeometry)
                MaskShape.RADIAL -> MaskShapes.fillRadial(mask, state.maskGeometry)
                else -> {}
            }
        }
        maskVersion++
    }

    // Effect tiles: each one is the user's photo with that effect at full strength.
    LaunchedEffect(previewSrc) {
        val base = previewSrc ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val small = ImageOps.filterThumb(base, null)
            Effects.All.forEach { e ->
                if (effectThumbs.containsKey(e.id)) return@forEach
                val bmp = if (e.id == "none") small else runCatching {
                    Effects.preview(small, e, e.paramDefault)
                }.getOrDefault(small)
                effectThumbs[e.id] = bmp
            }
        }
    }

    LaunchedEffect(rendered) {
        val src = rendered ?: return@LaunchedEffect
        histBins = withContext(Dispatchers.Default) {
            val small = ImageOps.sampleForHistogram(src)
            val px = IntArray(small.width * small.height)
            small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
            if (small !== src) small.recycle()
            histogramOf(px)
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1900)
            toast = null
        }
    }

    fun commit(label: String) {
        val src = exportSrc ?: return
        saving = true
        scope.launch {
            val out = withContext(Dispatchers.Default) {
                ImageOps.render(src, state, activeLut, mask) { name, bold ->
                    com.zyon.flow.edit.FontStore.typeface(context, name, bold)
                }
            }
            val ok = ImageOps.export(context, out)
            currentUri?.let { ProjectStore.upsert(context, Project.from(it, state)) }
            saving = false
            toast = if (ok) label else "Could not save"
            onSaved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ---- top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                CloseGlyph(tint = FlowText)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.save),
                color = if (exportSrc != null && !saving) FlowText else FlowTextDim,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = exportSrc != null && !saving) { commit("Saved to gallery") }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.publish),
                color = if (exportSrc != null && !saving) FlowCream else FlowTextDim,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = exportSrc != null && !saving) { commit("Exported — ready to share") }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }

        // ---- canvas ----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val shown = if (holdingOriginal) previewSrc else rendered
            if (shown == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_photo),
                        color = FlowTextDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(FlowCream)
                            .clickable {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_import),
                            color = FlowBlack,
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it },
                ) {
                    // Base layer: the edited frame.
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = "Edited photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Split compare: the original clipped to the left of the handle.
                    if (splitMode && previewSrc != null) {
                        Image(
                            bitmap = previewSrc!!.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    clipRect(right = size.width * splitFraction) {
                                        this@drawWithContent.drawContent()
                                    }
                                },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    val x = size.width * splitFraction
                                    drawLine(
                                        color = FlowCream,
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = 2.5f,
                                    )
                                    drawCircle(
                                        color = FlowCream,
                                        radius = 15f,
                                        center = Offset(x, size.height / 2f),
                                    )
                                },
                        )
                    }

                    // Brush overlay
                    if (brushing && mask.hasContent()) {
                        val ov = remember(maskVersion) { maskOverlayBitmap(mask) }
                        Image(
                            bitmap = ov.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            alpha = 0.55f,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Movable overlay markers, only while editing text or stickers.
                    if ((mode == 5 || mode == 6) && previewSrc != null) {
                        OverlayHandles(
                            overlays = state.overlays,
                            selectedTextId = selectedTextId,
                            canvasSize = canvasSize,
                            bitmap = previewSrc,
                        )
                    }

                    // Touch layer. Priority order matters:
                    //   split drag > overlay drag > brush paint > hold-to-compare.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(brushing, previewSrc, canvasSize, splitMode, mode, state.overlays) {
                                when {
                                    splitMode -> {
                                        // Dragging anywhere moves the divider.
                                        detectDragGestures(
                                            onDragStart = { off ->
                                                if (canvasSize.width > 0) {
                                                    splitFraction = (off.x / canvasSize.width).coerceIn(0f, 1f)
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                if (canvasSize.width > 0) {
                                                    splitFraction =
                                                        (change.position.x / canvasSize.width).coerceIn(0f, 1f)
                                                }
                                            },
                                        )
                                    }

                                    brushing -> {
                                        var last: Offset? = null
                                        detectDragGestures(
                                            onDragStart = { off ->
                                                val n = deviceToImage(off, canvasSize, previewSrc)
                                                if (n != null) {
                                                    mask.paint(n.x, n.y, brushRadius)
                                                    maskVersion++
                                                    last = off
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                val n = deviceToImage(change.position, canvasSize, previewSrc)
                                                val prev = last?.let { deviceToImage(it, canvasSize, previewSrc) }
                                                if (n != null) {
                                                    if (prev != null) {
                                                        mask.stroke(prev.x, prev.y, n.x, n.y, brushRadius)
                                                    } else {
                                                        mask.paint(n.x, n.y, brushRadius)
                                                    }
                                                    maskVersion++
                                                    last = change.position
                                                }
                                            },
                                            onDragEnd = { last = null },
                                            onDragCancel = { last = null },
                                        )
                                    }

                                    mode == 5 || mode == 6 -> {
                                        // Drag text / stickers directly on the frame.
                                        var activeText: Long? = null
                                        var activeSticker: Long? = null
                                        detectDragGestures(
                                            onDragStart = { off ->
                                                val n = deviceToImage(off, canvasSize, previewSrc) ?: return@detectDragGestures
                                                val hit = hitTestOverlays(state.overlays, n.x, n.y)
                                                activeText = hit?.first
                                                activeSticker = hit?.second
                                                activeText?.let { selectedTextId = it }
                                            },
                                            onDrag = { change, _ ->
                                                val n = deviceToImage(change.position, canvasSize, previewSrc)
                                                    ?: return@detectDragGestures
                                                val tid = activeText
                                                val sid = activeSticker
                                                if (tid != null) {
                                                    state = state.copy(
                                                        overlays = state.overlays.copy(
                                                            texts = state.overlays.texts.map {
                                                                if (it.id == tid) it.copy(x = n.x, y = n.y) else it
                                                            },
                                                        ),
                                                    )
                                                } else if (sid != null) {
                                                    state = state.copy(
                                                        overlays = state.overlays.copy(
                                                            stickers = state.overlays.stickers.map {
                                                                if (it.id == sid) it.copy(x = n.x, y = n.y) else it
                                                            },
                                                        ),
                                                    )
                                                }
                                            },
                                            onDragEnd = {
                                                activeText = null
                                                activeSticker = null
                                            },
                                            onDragCancel = {
                                                activeText = null
                                                activeSticker = null
                                            },
                                        )
                                    }

                                    else -> {
                                        detectTapGestures(
                                            onPress = {
                                                holdingOriginal = true
                                                try {
                                                    awaitRelease()
                                                } finally {
                                                    holdingOriginal = false
                                                }
                                            },
                                        )
                                    }
                                }
                            },
                    )
                }
            }
            if (holdingOriginal && previewSrc != null) {
                Pill("ORIGINAL", Modifier.align(Alignment.TopCenter).padding(top = 10.dp), filled = false)
            }
            if (toast != null) {
                Pill(toast!!, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), filled = true)
            }
        }

        if (previewSrc != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FlowSurface),
            ) {
                when (mode) {
                    0 -> {
                        if (state.lutKey != null) {
                            IntensityRow(
                                value = state.lutIntensity,
                                onChange = { state = state.copy(lutIntensity = it) },
                            )
                        }
                        CubeCarousel(
                            entries = allEntries,
                            groups = groups,
                            activeGroup = activeGroup,
                            onGroupChange = { activeGroup = it },
                            selectedKey = state.lutKey,
                            thumbs = thumbs,
                            customName = state.customLutName,
                            onSelect = { key ->
                                state = state.copy(
                                    lutKey = key,
                                    presetId = "original",
                                    customLut = null,
                                    customLutName = null,
                                )
                            },
                            onImportCube = { cubePicker.launch(arrayOf("*/*")) },
                            onClearCube = { state = state.copy(customLut = null, customLutName = null) },
                            onExportCube = {
                                if (activeLut != null) {
                                    cubeExporter.launch(
                                        (state.customLutName ?: state.lutKey ?: "flow") + ".cube",
                                    )
                                } else {
                                    toast = "Select a cube first"
                                }
                            },
                        )
                    }
                    1 -> Column {
                        MaskShapeRow(
                            shape = state.maskShape,
                            geometry = state.maskGeometry,
                            additive = state.maskAdditive,
                            onAdditive = { state = state.copy(maskAdditive = it) },
                            onShape = {
                                state = state.copy(maskShape = it, useMask = true)
                                if (it == MaskShape.BRUSH) {
                                    mask.clear()
                                    maskVersion++
                                }
                            },
                            onGeometry = { state = state.copy(maskGeometry = it, useMask = true) },
                        )
                        MaskControls(
                            useMask = state.useMask,
                            brushing = brushing,
                            onToggleMask = { state = state.copy(useMask = !state.useMask) },
                            onToggleBrush = {
                                brushing = !brushing
                                if (brushing) {
                                    state = state.copy(useMask = true)
                                    splitMode = false
                                }
                            },
                            onClear = {
                                mask.clear()
                                maskVersion++
                            },
                            onInvert = {
                                mask.invert()
                                maskVersion++
                            },
                        )
                        EffectsPanel(
                        selectedId = state.effectId,
                        thumbs = effectThumbs,
                        effectParam = state.effectParam,
                        effectIntensity = state.effectIntensity,
                        onSelect = { effect: Effect ->
                            state = state.copy(
                                effectId = effect.id,
                                effectParam = effect.paramDefault,
                            )
                        },
                        onParamChange = { state = state.copy(effectParam = it) },
                        onIntensityChange = { state = state.copy(effectIntensity = it) },
                        )
                    }
                    2 -> AdjustPanel(state.adjustments) { state = state.copy(adjustments = it) }
                    3 -> CurvesPanel(
                        curves = state.curves,
                        presets = curvePresets,
                        onChange = { state = state.copy(curves = it) },
                        onSavePreset = {
                            val nm = "Set " + (curvePresets.size + 1)
                            curvePresets = com.zyon.flow.edit.CurveLibrary.add(context, nm, state.curves)
                            toast = "Curve set saved"
                        },
                        onApplyPreset = { preset ->
                            state = state.copy(curves = preset.toCurves())
                            toast = preset.name
                        },
                        onDeletePreset = { preset ->
                            curvePresets = com.zyon.flow.edit.CurveLibrary.remove(context, preset.name)
                        },
                    )
                    4 -> HslPanel(
                        hsl = state.hsl,
                        onChange = { state = state.copy(hsl = it) },
                    )
                    5 -> {
                        // Entering Text with nothing selected left the drag dead.
                        if (selectedTextId == null) {
                            selectedTextId = state.overlays.texts.firstOrNull()?.id
                        }
                        TextPanel(
                        overlays = state.overlays,
                        selectedId = selectedTextId,
                        fonts = fonts,
                        onSelect = { selectedTextId = it },
                        onChange = { state = state.copy(overlays = it) },
                        onImportFont = { fontPicker.launch(arrayOf("*/*")) },
                        onDeleteFont = { entry ->
                            entry.file?.let { file ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        com.zyon.flow.edit.FontStore.delete(context, file)
                                    }
                                    fonts = withContext(Dispatchers.IO) {
                                        com.zyon.flow.edit.FontStore.list(context)
                                    }
                                    state = state.copy(
                                        overlays = state.overlays.copy(
                                            texts = state.overlays.texts.map {
                                                if (it.fontFile == file) it.copy(fontFile = null) else it
                                            },
                                        ),
                                    )
                                    toast = "Font removed"
                                }
                            }
                        },
                        )
                    }
                    6 -> StickerPanel(
                        overlays = state.overlays,
                        onChange = { state = state.copy(overlays = it) },
                    )
                    7 -> CropPanel(transform = state.transform, onChange = { state = state.copy(transform = it) })
                    8 -> BorderPanel(
                        border = state.border,
                        onChange = { state = state.copy(border = it) },
                    )
                    else -> RecipePanel(
                        recipes = recipes,
                        onApply = { state = it.toState() },
                        onSave = {
                            val updated = recipes + Recipe.from("Look ${recipes.size + 1}", state)
                            recipes = updated
                            RecipeStore.save(context, updated)
                            toast = "Recipe saved"
                        },
                        onDelete = { r ->
                            val updated = recipes - r
                            recipes = updated
                            RecipeStore.save(context, updated)
                        },
                    )
                }

                ModeBar(
                    selected = mode,
                    splitActive = splitMode,
                    onSelect = { newMode ->
                        mode = newMode
                        // Split belongs to the look views; leaving it on across
                        // an editing tab made the canvas feel stuck.
                        if (newMode == 5 || newMode == 6 || newMode == 7) splitMode = false
                        // The brush captures drags, so it must release the
                        // canvas before overlay drag handlers can run.
                        if (newMode != 1) brushing = false
                    },
                    onToggleSplit = {
                        splitMode = !splitMode
                        brushing = false
                    },
                    onImport = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
        }
    }
}

private fun groupOf(e: LutEntry): String {
    val g = e.group
    return if (g.isNotBlank() && g != "Other" && g.length > 1) {
        g
    } else {
        e.key.firstOrNull()?.let { if (it.isLetter()) it.uppercase() else "Legacy" } ?: "Legacy"
    }
}

private fun buildGroups(entries: List<LutEntry>): List<String> {
    val known = entries.map { groupOf(it) }.filter { it.length > 1 }.toSet()
    val order = listOf(
        "Legacy", "Analog", "Alchemy", "Chromatic", "Instant", "Essence",
        "Valence", "Polychrome", "Minimalist", "Mood", "Clean", "B&W Classic",
        "B&W Fade", "Portraits", "Mountain", "Desert", "Forest", "Coast",
    )
    val sorted = order.filter { known.contains(it) } + known.filter { it !in order }.sorted()
    return sorted.ifEmpty { listOf("Legacy") }
}

// ---------------------------------------------------------------- carousel

@Composable
private fun CubeCarousel(
    entries: List<LutEntry>,
    groups: List<String>,
    activeGroup: String,
    onGroupChange: (String) -> Unit,
    selectedKey: String?,
    thumbs: Map<String, Bitmap>,
    customName: String? = null,
    onSelect: (String?) -> Unit,
    onImportCube: () -> Unit = {},
    onClearCube: () -> Unit = {},
    onExportCube: () -> Unit = {},
) {
    val visible = remember(entries, activeGroup) {
        entries.filter { groupOf(it) == activeGroup }.sortedBy { it.key }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .padding(top = 8.dp),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(34.dp),
        ) {
            items(groups, key = { it }) { g ->
                val selected = g == activeGroup
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) FlowElevated else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) FlowCream.copy(alpha = 0.5f) else FlowLine,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onGroupChange(g) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = g.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                        fontSize = 9.sp,
                        color = if (selected) FlowCream else FlowTextDim,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "__import") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(66.dp)
                        .clickable {
                            if (customName != null) onClearCube() else onImportCube()
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(FlowElevated)
                            .border(1.dp, FlowLine, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (customName != null) "●" else "+",
                            color = if (customName != null) FlowCream else FlowTextDim,
                            fontSize = 20.sp,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = customName?.take(7) ?: ".CUBE",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = if (customName != null) FlowCream else FlowTextDim,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item(key = "__export") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(66.dp)
                        .clickable { onExportCube() },
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(FlowElevated)
                            .border(1.dp, FlowLine, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⤓",
                            color = FlowCream,
                            fontSize = 18.sp,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = stringResource(R.string.cubes_save),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = FlowTextDim,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item(key = "__original") {
                Tile(
                    label = "ORIG",
                    bitmap = thumbs["__original"],
                    selected = selectedKey == null,
                    onClick = { onSelect(null) },
                )
            }
            items(visible, key = { it.key }) { entry ->
                Tile(
                    label = entry.name.substringBefore(" / ").ifBlank { entry.key.uppercase() },
                    bitmap = thumbs[entry.key],
                    selected = entry.key == selectedKey,
                    onClick = { onSelect(entry.key) },
                )
            }
        }
    }
}

@Composable
private fun Tile(
    label: String,
    bitmap: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(66.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(FlowElevated)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) FlowCream else FlowLine,
                    shape = RoundedCornerShape(9.dp),
                ),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(7.dp)),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 0.4.sp,
            color = if (selected) FlowText else FlowTextDim,
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun IntensityRow(value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.effects_intensity),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = FlowTextDim,
            modifier = Modifier.width(74.dp),
        )
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = FlowCream,
                activeTrackColor = FlowCream.copy(alpha = 0.85f),
                inactiveTrackColor = FlowLine,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(value * 100).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = FlowCream,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
    }
}

// ---------------------------------------------------------------- mode bar

@Composable
private fun ModeBar(
    selected: Int,
    splitActive: Boolean,
    onSelect: (Int) -> Unit,
    onToggleSplit: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Spacer(Modifier.width(4.dp))
        ModeItem(stringResource(R.string.mode_cubes), selected == 0, { CubeGlyph(if (selected == 0) FlowCream else FlowTextDim) }) { onSelect(0) }
        ModeItem(stringResource(R.string.mode_effects), selected == 1, { SparkGlyph(if (selected == 1) FlowCream else FlowTextDim) }) { onSelect(1) }
        ModeItem(stringResource(R.string.mode_adjust), selected == 2, { SlidersGlyph(if (selected == 2) FlowCream else FlowTextDim) }) { onSelect(2) }
        ModeItem(stringResource(R.string.mode_curves), selected == 3, { CurveGlyph(if (selected == 3) FlowCream else FlowTextDim) }) { onSelect(3) }
        ModeItem(stringResource(R.string.mode_hsl), selected == 4, { HslGlyph(if (selected == 4) FlowCream else FlowTextDim) }) { onSelect(4) }
        ModeItem(stringResource(R.string.mode_text), selected == 5, { TextGlyph(if (selected == 5) FlowCream else FlowTextDim) }) { onSelect(5) }
        ModeItem(stringResource(R.string.mode_sticker), selected == 6, { StickerGlyph(if (selected == 6) FlowCream else FlowTextDim) }) { onSelect(6) }
        ModeItem(stringResource(R.string.mode_crop), selected == 7, { CropGlyph(if (selected == 7) FlowCream else FlowTextDim) }) { onSelect(7) }
        ModeItem(stringResource(R.string.mode_border), selected == 8, { BorderGlyph(if (selected == 8) FlowCream else FlowTextDim) }) { onSelect(8) }
        ModeItem(stringResource(R.string.mode_recipe), selected == 9, { StarGlyph(if (selected == 9) FlowCream else FlowTextDim) }) { onSelect(9) }
        ModeItem(stringResource(R.string.mode_split), splitActive, { SplitGlyph(if (splitActive) FlowCream else FlowTextDim) }) { onToggleSplit() }
        ModeItem(stringResource(R.string.mode_import), false, { PlusGlyph2(FlowTextDim) }) { onImport() }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun ModeItem(
    label: String,
    active: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            letterSpacing = 0.3.sp,
            color = if (active) FlowCream else FlowTextDim,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ---------------------------------------------------------------- glyphs

@Composable
private fun CubeGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.09f
        drawRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(s * 0.12f, s * 0.12f),
            size = androidx.compose.ui.geometry.Size(s * 0.76f, s * 0.76f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
        )
        drawRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(s * 0.32f, s * 0.32f),
            size = androidx.compose.ui.geometry.Size(s * 0.36f, s * 0.36f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
        )
    }
}

@Composable
private fun SlidersGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.09f
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.12f, s * 0.34f),
            androidx.compose.ui.geometry.Offset(s * 0.88f, s * 0.34f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.12f, s * 0.68f),
            androidx.compose.ui.geometry.Offset(s * 0.88f, s * 0.68f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawCircle(tint, radius = s * 0.11f, center = androidx.compose.ui.geometry.Offset(s * 0.34f, s * 0.34f))
        drawCircle(tint, radius = s * 0.11f, center = androidx.compose.ui.geometry.Offset(s * 0.66f, s * 0.68f))
    }
}

@Composable
private fun CropGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.09f
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.26f, s * 0.08f),
            androidx.compose.ui.geometry.Offset(s * 0.26f, s * 0.74f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.08f, s * 0.26f),
            androidx.compose.ui.geometry.Offset(s * 0.74f, s * 0.26f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.74f, s * 0.26f),
            androidx.compose.ui.geometry.Offset(s * 0.74f, s * 0.92f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.26f, s * 0.74f),
            androidx.compose.ui.geometry.Offset(s * 0.92f, s * 0.74f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun StarGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.5f, s * 0.1f)
            lineTo(s * 0.61f, s * 0.39f)
            lineTo(s * 0.92f, s * 0.39f)
            lineTo(s * 0.67f, s * 0.58f)
            lineTo(s * 0.76f, s * 0.88f)
            lineTo(s * 0.5f, s * 0.7f)
            lineTo(s * 0.24f, s * 0.88f)
            lineTo(s * 0.33f, s * 0.58f)
            lineTo(s * 0.08f, s * 0.39f)
            lineTo(s * 0.39f, s * 0.39f)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = s * 0.09f,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun PlusGlyph2(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.1f
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.16f),
            androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.84f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.16f, s * 0.5f),
            androidx.compose.ui.geometry.Offset(s * 0.84f, s * 0.5f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, filled: Boolean) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) FlowCream else FlowBlack.copy(alpha = 0.7f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = if (filled) FlowBlack else FlowText,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------- panels

@Composable
private fun AdjustPanel(adjust: Adjustments, onChange: (Adjustments) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = 4.dp),
    ) {
        AdjustRow("Exposure", adjust.exposure) { onChange(adjust.copy(exposure = it)) }
        AdjustRow("Contrast", adjust.contrast) { onChange(adjust.copy(contrast = it)) }
        AdjustRow("Highlights", adjust.highlights) { onChange(adjust.copy(highlights = it)) }
        AdjustRow("Shadows", adjust.shadows) { onChange(adjust.copy(shadows = it)) }
        AdjustRow("Saturation", adjust.saturation) { onChange(adjust.copy(saturation = it)) }
        AdjustRow("Vibrance", adjust.vibrance) { onChange(adjust.copy(vibrance = it)) }
        AdjustRow("Warmth", adjust.warmth) { onChange(adjust.copy(warmth = it)) }
        AdjustRow("Hue", adjust.hueShift) { onChange(adjust.copy(hueShift = it)) }
        UnitAdjustRow("Fade", adjust.fade) { onChange(adjust.copy(fade = it)) }
        UnitAdjustRow("Vignette", adjust.vignette) { onChange(adjust.copy(vignette = it)) }
        UnitAdjustRow("Grain", adjust.grain) { onChange(adjust.copy(grain = it)) }
        UnitAdjustRow("Sharpen", adjust.sharpen) { onChange(adjust.copy(sharpen = it)) }
        UnitAdjustRow("Clarity", adjust.clarity) { onChange(adjust.copy(clarity = it)) }
    }
}

@Composable
private fun SparkGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.085f
        val c = androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.5f)
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.06f),
            androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.94f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.06f, s * 0.5f),
            androidx.compose.ui.geometry.Offset(s * 0.94f, s * 0.5f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.2f, s * 0.2f),
            androidx.compose.ui.geometry.Offset(s * 0.8f, s * 0.8f),
            strokeWidth = w * 0.8f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.8f, s * 0.2f),
            androidx.compose.ui.geometry.Offset(s * 0.2f, s * 0.8f),
            strokeWidth = w * 0.8f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawCircle(tint, radius = s * 0.1f, center = c)
    }
}


/** Map a touch point (ContentScale.Fit) to normalised image coordinates. */
private fun deviceToImage(
    pos: Offset,
    box: androidx.compose.ui.unit.IntSize,
    bitmap: Bitmap?,
): Offset? {
    if (bitmap == null || box.width == 0 || box.height == 0) return null
    val scale = minOf(
        box.width.toFloat() / bitmap.width,
        box.height.toFloat() / bitmap.height,
    )
    val dw = bitmap.width * scale
    val dh = bitmap.height * scale
    val ox = (box.width - dw) / 2f
    val oy = (box.height - dh) / 2f
    val nx = (pos.x - ox) / dw
    val ny = (pos.y - oy) / dh
    if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return null
    return Offset(nx, ny)
}

@Composable
private fun CurveGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.09f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.1f, s * 0.9f)
            cubicTo(s * 0.35f, s * 0.9f, s * 0.4f, s * 0.1f, s * 0.9f, s * 0.1f)
        }
        drawPath(
            p, tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

@Composable
private fun BorderGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.075f
        drawRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(s * 0.1f, s * 0.1f),
            size = androidx.compose.ui.geometry.Size(s * 0.8f, s * 0.8f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 2.2f),
        )
        drawRect(
            color = tint.copy(alpha = 0.45f),
            topLeft = androidx.compose.ui.geometry.Offset(s * 0.34f, s * 0.34f),
            size = androidx.compose.ui.geometry.Size(s * 0.32f, s * 0.32f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
        )
    }
}

@Composable
private fun HslGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.36f
        val cols = intArrayOf(
            0xFFE04B4B.toInt(), 0xFFE08A3C.toInt(), 0xFFD9C43C.toInt(), 0xFF4CAF50.toInt(),
            0xFF3CB8C0.toInt(), 0xFF4A7BD0.toInt(), 0xFF8E5BD0.toInt(), 0xFFD050A8.toInt(),
        )
        cols.forEachIndexed { i, c ->
            val a0 = (i / cols.size.toFloat()) * 360f
            drawArc(
                color = Color(c),
                startAngle = a0,
                sweepAngle = 360f / cols.size + 2f,
                useCenter = true,
                topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            )
        }
        drawCircle(
            color = Color.Black.copy(alpha = 0.75f),
            radius = r * 0.42f,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
        )
        drawCircle(
            color = tint,
            radius = r * 0.42f,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.06f),
        )
    }
}

@Composable
private fun SplitGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.085f
        drawRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(s * 0.1f, s * 0.16f),
            size = androidx.compose.ui.geometry.Size(s * 0.8f, s * 0.68f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
        )
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.16f),
            androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.84f),
            strokeWidth = w * 2f,
        )
    }
}

@Composable
private fun TextGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.085f
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.16f, s * 0.22f),
            androidx.compose.ui.geometry.Offset(s * 0.84f, s * 0.22f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.22f),
            androidx.compose.ui.geometry.Offset(s * 0.5f, s * 0.82f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint, androidx.compose.ui.geometry.Offset(s * 0.34f, s * 0.82f),
            androidx.compose.ui.geometry.Offset(s * 0.66f, s * 0.82f),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun StickerGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.075f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.5f, s * 0.1f)
            lineTo(s * 0.6f, s * 0.4f)
            lineTo(s * 0.9f, s * 0.4f)
            lineTo(s * 0.65f, s * 0.58f)
            lineTo(s * 0.74f, s * 0.9f)
            lineTo(s * 0.5f, s * 0.7f)
            lineTo(s * 0.26f, s * 0.9f)
            lineTo(s * 0.35f, s * 0.58f)
            lineTo(s * 0.1f, s * 0.4f)
            lineTo(s * 0.4f, s * 0.4f)
            close()
        }
        drawPath(
            path, tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Find the overlay nearest a touch point. Returns (textId, stickerId).
 * Text wins ties because it is usually the thing the user is aiming at.
 */
private fun hitTestOverlays(
    overlays: com.zyon.flow.edit.Overlays,
    nx: Float,
    ny: Float,
): Pair<Long?, Long?>? {
    // Generous radius: fingers are bigger than glyphs.
    val radius = 0.16f
    var bestText: Long? = null
    var bestTextDist = Float.MAX_VALUE
    overlays.texts.filter { !it.isBlank() }.forEach { t ->
        val d = kotlin.math.hypot(t.x - nx, t.y - ny)
        if (d < radius && d < bestTextDist) {
            bestTextDist = d
            bestText = t.id
        }
    }
    if (bestText != null) return bestText to null

    var bestSticker: Long? = null
    var bestStickerDist = Float.MAX_VALUE
    overlays.stickers.filter { it.visible }.forEach { s ->
        val d = kotlin.math.hypot(s.x - nx, s.y - ny)
        if (d < radius && d < bestStickerDist) {
            bestStickerDist = d
            bestSticker = s.id
        }
    }
    if (bestSticker != null) return null to bestSticker
    return null
}

/** Selection frame drawn around every movable overlay while editing text/stickers. */
@Composable
private fun OverlayHandles(
    overlays: com.zyon.flow.edit.Overlays,
    selectedTextId: Long?,
    canvasSize: androidx.compose.ui.unit.IntSize,
    bitmap: Bitmap?,
) {
    if (bitmap == null || canvasSize.width == 0) return
    val scale = minOf(
        canvasSize.width.toFloat() / bitmap.width,
        canvasSize.height.toFloat() / bitmap.height,
    )
    val dw = bitmap.width * scale
    val dh = bitmap.height * scale
    val ox = (canvasSize.width - dw) / 2f
    val oy = (canvasSize.height - dh) / 2f

    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        fun marker(x: Float, y: Float, selected: Boolean) {
            val cx = ox + x * dw
            val cy = oy + y * dh
            val r = if (selected) 26f else 20f
            drawCircle(
                color = if (selected) FlowCream else FlowCream.copy(alpha = 0.45f),
                radius = r,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (selected) 3f else 2f),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = r + 1.5f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
            )
        }
        overlays.texts.filter { !it.isBlank() }.forEach { marker(it.x, it.y, it.id == selectedTextId) }
        overlays.stickers.filter { it.visible }.forEach { marker(it.x, it.y, false) }
    }
}
