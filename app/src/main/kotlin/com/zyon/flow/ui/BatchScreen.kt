/**
 * Flow — batch screen: choose photos, choose a look, export all.
 */

package com.zyon.flow.ui

import com.zyon.flow.R

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.edit.BatchProgress
import com.zyon.flow.edit.BatchRunner
import com.zyon.flow.edit.Lut
import com.zyon.flow.edit.LutPack
import com.zyon.flow.edit.PhotoStore
import com.zyon.flow.edit.Project
import com.zyon.flow.edit.ProjectStore
import com.zyon.flow.ui.theme.FlowBlack
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim
import kotlinx.coroutines.launch

@Composable
fun BatchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<Project?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<BatchProgress?>(null) }
    var doneMsg by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        projects = ProjectStore.load(context)
        selectedProject = projects.firstOrNull()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30),
    ) { picked ->
        if (picked.isNotEmpty()) {
            scope.launch {
                val local = picked.map { PhotoStore.import(context, it) }
                uris = uris + local
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = FlowText, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.batch_title),
                    color = FlowText,
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.batch_subtitle),
                    color = FlowTextDim,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        SectionTitle("1 · LOOK")
        if (projects.isEmpty()) {
            Text(
                text = stringResource(R.string.batch_no_projects),
                color = FlowTextDim,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(projects, key = { it.id }) { p ->
                    val selected = p.id == selectedProject?.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(74.dp)
                            .clickable { selectedProject = p },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(FlowElevated)
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) FlowCream else FlowLine,
                                    RoundedCornerShape(10.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (p.lutKey ?: p.effectId.take(2)).uppercase(),
                                color = if (selected) FlowCream else FlowTextDim,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = p.lutKey ?: "custom",
                            color = if (selected) FlowText else FlowTextDim,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("2 · PHOTOS (${uris.size})")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BatchChip("ADD PHOTOS") {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            BatchChip("CLEAR") { uris = emptyList() }
        }

        if (uris.isEmpty()) {
            Text(
                text = stringResource(R.string.batch_nothing_selected),
                color = FlowTextDim,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                uris.forEachIndexed { i, u ->
                    Text(
                        text = "${i + 1}. ${u.lastPathSegment?.takeLast(28) ?: "photo"}",
                        color = FlowTextDim,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("3 · CAPTION (OPTIONAL)")
        androidx.compose.material3.OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            singleLine = true,
            placeholder = { Text("{n} · {total} · {name}", fontSize = 11.sp, color = FlowTextDim) },
            colors = androidx.compose.material3.TextFieldDefaults.colors(
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
        Text(
            text = stringResource(R.string.batch_caption_help),
            color = FlowTextDim,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("4 · EXPORT")

        val ready = uris.isNotEmpty() && selectedProject != null && !running

        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (ready) FlowCream else FlowElevated)
                .border(1.dp, if (ready) FlowCream else FlowLine, RoundedCornerShape(12.dp))
                .clickable(enabled = ready) {
                    val proj = selectedProject ?: return@clickable
                    running = true
                    doneMsg = null
                    progress = BatchProgress(0, uris.size, "", 0)
                    scope.launch {
                        val lut: Lut? = proj.lutKey?.let {
                            runCatching { LutPack.load(context, it) }.getOrNull()
                        }
                        val result = BatchRunner.run(
                            context = context,
                            uris = uris,
                            state = proj.toState(),
                            lut = lut,
                            caption = caption.takeIf { it.isNotBlank() },
                        ) { p -> progress = p }
                        running = false
                        doneMsg = if (result.failed == 0) {
                            "${result.total} photos exported to Pictures/Flow"
                        } else {
                            "${result.total - result.failed} exported · ${result.failed} failed"
                        }
                    }
                }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    running -> "EXPORTING…"
                    ready -> "APPLY TO ${uris.size} PHOTOS"
                    else -> "SELECT LOOK AND PHOTOS"
                },
                color = if (ready) FlowBlack else FlowTextDim,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }

        progress?.let { p ->
            if (running || doneMsg != null) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    LinearProgressIndicator(
                        progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                        color = FlowCream,
                        trackColor = FlowLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${p.done}/${p.total}" + if (p.failed > 0) "  ·  ${p.failed} failed" else "",
                        color = FlowTextDim,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                    )
                }
            }
        }

        doneMsg?.let { m ->
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(FlowElevated)
                    .padding(12.dp),
            ) {
                Text(
                    text = m,
                    color = FlowCream,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = FlowTextDim,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun BatchChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(FlowElevated)
            .border(1.dp, FlowLine, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = FlowText,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
        )
    }
}
