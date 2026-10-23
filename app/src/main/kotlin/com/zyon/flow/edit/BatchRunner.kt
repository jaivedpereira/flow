/**
 * Flow — batch apply.
 *
 * Pick N photos, pick one saved look, export them all. The heavy work runs on
 * Dispatchers.Default one photo at a time so the phone never runs out of memory
 * on a large selection.
 */

package com.zyon.flow.edit

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BatchProgress(
    val done: Int,
    val total: Int,
    val currentName: String,
    val failed: Int,
)

object BatchRunner {

    /**
     * Apply [state] to every URI in [uris], exporting each to Pictures/Flow.
     * [onProgress] is called after each photo.
     */
    suspend fun run(
        context: Context,
        uris: List<Uri>,
        state: EditState,
        lut: Lut?,
        /** Optional caption applied to every photo; supports {n}, {total}, {name}. */
        caption: String? = null,
        onProgress: (BatchProgress) -> Unit,
    ): BatchProgress = withContext(Dispatchers.Default) {
        var done = 0
        var failed = 0
        for ((index, uri) in uris.withIndex()) {
            val name = uri.lastPathSegment?.takeLast(24) ?: "photo"
            try {
                // decode at export size, render, save
                val src = ImageOps.load(context, uri, 2400)
                if (src == null) {
                    failed++
                } else {
                    // Resolve placeholders per photo so a batch can still be individual.
                    val perPhoto = if (caption.isNullOrBlank()) {
                        state
                    } else {
                        val resolved = caption
                            .replace("{n}", (index + 1).toString())
                            .replace("{total}", uris.size.toString())
                            .replace("{name}", name)
                        val template = state.overlays.texts.firstOrNull()
                            ?: TextLayer(id = 0L, text = "")
                        state.copy(
                            overlays = state.overlays.copy(
                                texts = listOf(template.copy(id = 0L, text = resolved, visible = true)),
                            ),
                        )
                    }
                    val out = ImageOps.render(src, perPhoto, lut, null)
                    val ok = ImageOps.export(context, out)
                    if (!ok) failed++
                    if (out !== src) out.recycle()
                    src.recycle()
                }
            } catch (t: Throwable) {
                failed++
            }
            done++
            withContext(Dispatchers.Main) {
                onProgress(BatchProgress(done, uris.size, name, failed))
            }
        }
        BatchProgress(done, uris.size, "", failed)
    }
}
