/**
 * Flow — bitmap pipeline: load, geometry, colour, detail, vignette, grain, export.
 *
 * Order matters:
 *   1. geometry (crop / rotate / straighten)  — changes the frame
 *   2. colour matrix + split tone              — per pixel
 *   3. sharpen / clarity (unsharp mask)         — spatial, needs the new frame
 *   4. vignette                                 — spatial lighting
 *   5. grain                                    — film texture on top
 */

package com.zyon.flow.edit

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.random.Random

object ImageOps {

    suspend fun load(context: Context, uri: Uri, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { loadUnsafe(context, uri, maxDim) }.getOrNull()
    }

    /** Throws on unreadable URIs; callers should use [load]. */
    private fun loadUnsafe(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w / 2 >= maxDim || h / 2 >= maxDim) {
            sample *= 2
            w /= 2
            h /= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /**
     * Full pipeline. Returns a new bitmap owned by the caller.
     *
     * The pipeline holds several full-frame buffers at once (unsharp masking
     * alone needs three), so a large source can exhaust the heap. Instead of
     * letting the process die, retry once at half resolution and scale back up.
     */
    fun render(
        src: Bitmap,
        state: EditState,
        lut: Lut? = null,
        mask: MaskData? = null,
        customTypeface: ((String?, Boolean) -> android.graphics.Typeface?)? = null,
        withFinish: Boolean = true,
        /** Resolves a cube key for layer CUBE entries. */
        lutResolver: ((String) -> Lut?)? = null,
    ): Bitmap = try {
        renderInternal(src, state, lut, mask, customTypeface, withFinish, lutResolver)
    } catch (oom: OutOfMemoryError) {
        val half = Bitmap.createScaledBitmap(
            src,
            (src.width / 2).coerceAtLeast(1),
            (src.height / 2).coerceAtLeast(1),
            true,
        )
        try {
            val small = renderInternal(half, state, lut, mask, customTypeface, withFinish, lutResolver)
            val up = Bitmap.createScaledBitmap(small, src.width, src.height, true)
            if (small !== up) small.recycle()
            up
        } catch (second: OutOfMemoryError) {
            src
        } finally {
            if (half !== src) half.recycle()
        }
    }

    private fun renderInternal(
        src: Bitmap,
        state: EditState,
        lut: Lut? = null,
        mask: MaskData? = null,
        customTypeface: ((String?, Boolean) -> android.graphics.Typeface?)? = null,
        withFinish: Boolean = true,
        lutResolver: ((String) -> Lut?)? = null,
    ): Bitmap {
        val preset = state.preset
        val adj = state.adjustments

        var work = Geometry.apply(src, state.transform)

        // Spot removal runs on the corrected frame, before any look is applied, so
        // the patches get graded along with the rest of the photo.
        if (state.spots.isNotEmpty()) {
            val healed = SpotRemoval.apply(work, state.spots)
            if (healed !== work) {
                if (work !== src) work.recycle()
                work = healed
            }
        }

        // Cube first: it is the base look, manual sliders refine on top of it.
        // Never recycle `src` here: with no geometry change `work === src` and the
        // caller (preview / export) still owns it.
        if (lut != null && state.lutIntensity > 0.004f) {
            val cubed = Lut.apply(work, lut, state.lutIntensity)
            if (work !== src) work.recycle()
            work = cubed
        }

        val matrix = buildMatrix(preset, adj, state.splitShadow, state.splitHighlight)
        val coloured = Bitmap.createBitmap(work.width, work.height, Bitmap.Config.ARGB_8888)
        Canvas(coloured).drawBitmap(
            work, 0f, 0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            },
        )
        if (work !== src) work.recycle()
        work = coloured

        // Luminance-dependent tone controls + artistic effect.
        val toned = applyTone(work, adj.highlights, adj.shadows, adj.vibrance, adj.hueShift, adj.whites, adj.blacks, adj.dehaze)
        if (toned !== work) {
            if (work !== src) work.recycle()
            work = toned
        }

        // Tone curves run on the toned image, before the artistic effect.
        if (!state.curves.isIdentity) {
            val curved = state.curves.apply(work)
            if (curved !== work) {
                if (work !== src) work.recycle()
                work = curved
            }
        }

        // Selective HSL sits between curves and the artistic effect.
        if (!state.hsl.isIdentity) {
            val hued = state.hsl.apply(work)
            if (hued !== work) {
                if (work !== src) work.recycle()
                work = hued
            }
        }

        if (state.effectId != "none" && state.effectIntensity > 0.004f) {
            val effect = Effects.byId(state.effectId)
            // The effect always renders at full strength; the blend (masked or not)
            // decides how much of it survives.
            val full = effect.apply(work, 1f, state.effectParam)
            val applied = if (state.useMask && mask != null && mask.hasContent()) {
                blendWithMask(work, full, mask, state.effectIntensity)
            } else {
                blendWithMask(work, full, null, state.effectIntensity)
            }
            if (applied !== work) {
                if (work !== src) work.recycle()
                work = applied
            }
            if (full !== work && full !== applied) full.recycle()
        }

        // Noise reduction sits immediately before sharpening: cleaning first is
        // the only order that makes sense, since sharpening amplifies whatever
        // noise is still there.
        if (adj.denoiseColour > 0.005f) {
            val cleaned = denoiseColour(work, adj.denoiseColour)
            if (cleaned !== work) {
                if (work !== src) work.recycle()
                work = cleaned
            }
        }
        if (adj.denoiseLuma > 0.005f) {
            val cleaned = denoiseLuma(work, adj.denoiseLuma)
            if (cleaned !== work) {
                if (work !== src) work.recycle()
                work = cleaned
            }
        }

        val detail = applyDetail(work, adj.sharpen, adj.texture, adj.clarity, adj.sharpenMask)
        if (detail !== work) {
            if (work !== src) work.recycle()
            work = detail
        }

        // ---- user layers, bottom of the stack first.
        // Everything above happened to the base photo. Layers composite on top in
        // order, so a caption below an effect layer gets graded with the image and
        // a caption above it stays clean.
        if (state.layers.isNotEmpty()) {
            for (layer in state.layers) {
                if (!layer.content.isAdjustment) continue
                val isBase = work === src
                work = when {
                    !layer.isRenderable -> work

                    layer.content == LayerContent.CUBE -> {
                        val cube = layer.lutKey?.let { key ->
                            lutResolver?.invoke(key)
                        }
                        if (cube == null) work else run {
                            val cubed = Lut.apply(work, cube, layer.strength)
                            if (!isBase && cubed !== work) work.recycle()
                            cubed
                        }
                    }

                    layer.content == LayerContent.COLOUR -> run {
                        val graded = applyColourLayer(work, layer.adjustments, layer.opacity)
                        if (!isBase && graded !== work) work.recycle()
                        graded
                    }

                    layer.content == LayerContent.EFFECT -> run {
                        val effect = Effects.byId(layer.effectId)
                        val full = effect.apply(work, 1f, layer.effectParam)
                        val applied = blendWithMask(work, full, null, layer.strength)
                        if (full !== applied && full !== work) full.recycle()
                        if (!isBase && applied !== work) work.recycle()
                        applied
                    }

                    else -> work
                }
            }
        }

        if (!withFinish) return work

        val vignette = effectiveVignette(preset, adj)
        val grain = effectiveGrain(preset, adj)

        if (vignette > 0.001f) {
            val radius = work.width.coerceAtLeast(work.height) * 0.78f
            val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    work.width / 2f, work.height / 2f, radius,
                    intArrayOf(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.argb((vignette * 210).toInt(), 0, 0, 0),
                    ),
                    floatArrayOf(0.45f, 0.72f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            Canvas(work).drawRect(0f, 0f, work.width.toFloat(), work.height.toFloat(), shade)
        }

        if (grain > 0.001f) {
            // Done on the CPU on purpose. Drawing a mid-grey tile with
            // PorterDuff.MULTIPLY onto the bitmap zeroed the image out: the
            // xfermode blends against the tile's own alpha, so a semi
            // transparent grey multiplied the destination down to black.
            val grained = applyGrain(work, grain)
            if (grained !== work) {
                if (work !== src) work.recycle()
                work = grained
            }
        }

        // Content layers and the base overlays, in stack order. Kept out of the
        // expensive pass on purpose: dragging a caption must not rebuild the look.
        if (!state.overlays.isEmpty || state.layers.any { !it.content.isAdjustment && it.isRenderable }) {
            val texts = ArrayList<TextLayer>()
            val stickers = ArrayList<StickerLayer>()
            if (!state.overlays.isEmpty) {
                texts.addAll(state.overlays.texts)
                stickers.addAll(state.overlays.stickers)
            }
            for (layer in state.layers) {
                if (layer.content.isAdjustment || !layer.isRenderable) continue
                when (layer.content) {
                    LayerContent.TEXT -> layer.text?.let { texts.add(it) }
                    LayerContent.STICKER -> layer.sticker?.let { stickers.add(it) }
                    else -> {}
                }
            }
            val stacked = Overlays(texts = texts, stickers = stickers)
            if (!stacked.isEmpty) {
                val composed = stacked.apply(work, customTypeface)
                if (composed !== work) {
                    if (work !== src) work.recycle()
                    work = composed
                }
            }
        }

        if (!state.border.isNone) {
            val framed = state.border.apply(work)
            if (framed !== work) {
                if (work !== src) work.recycle()
                work = framed
            }
        }

        return work
    }

    /**
     * Runs only the cheap tail of the pipeline (vignette, grain, overlays,
     * border) on a frame that already went through the expensive stages.
     *
     * This is what keeps the sliders live: dragging grain or vignette re-runs
     * ~15 ms of work instead of the whole pipeline.
     */
    fun applyFinish(
        src: Bitmap,
        state: EditState,
        customTypeface: ((String?, Boolean) -> android.graphics.Typeface?)? = null,
    ): Bitmap {
        val preset = state.preset
        val adj = state.adjustments
        var work = src
        val vignette = effectiveVignette(preset, adj)
        val grain = effectiveGrain(preset, adj)

        if (vignette > 0.001f) {
            val copy = work.copy(Bitmap.Config.ARGB_8888, true)
            if (copy != null) {
                val radius = copy.width.coerceAtLeast(copy.height) * 0.78f
                val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        copy.width / 2f, copy.height / 2f, radius,
                        intArrayOf(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.argb((vignette * 210).toInt(), 0, 0, 0),
                        ),
                        floatArrayOf(0.45f, 0.72f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                Canvas(copy).drawRect(0f, 0f, copy.width.toFloat(), copy.height.toFloat(), shade)
                work = copy
            }
        }

        if (grain > 0.001f) {
            val grained = applyGrain(work, grain)
            if (grained !== work && work !== src) work.recycle()
            work = grained
        }

        // Same stack as the full render, so the live frame matches the export.
        if (!state.overlays.isEmpty || state.layers.any { !it.content.isAdjustment && it.isRenderable }) {
            val texts = ArrayList<TextLayer>()
            val stickers = ArrayList<StickerLayer>()
            if (!state.overlays.isEmpty) {
                texts.addAll(state.overlays.texts)
                stickers.addAll(state.overlays.stickers)
            }
            for (layer in state.layers) {
                if (layer.content.isAdjustment || !layer.isRenderable) continue
                when (layer.content) {
                    LayerContent.TEXT -> layer.text?.let { texts.add(it) }
                    LayerContent.STICKER -> layer.sticker?.let { stickers.add(it) }
                    else -> {}
                }
            }
            val stacked = Overlays(texts = texts, stickers = stickers)
            if (!stacked.isEmpty) {
                val composed = stacked.apply(work, customTypeface)
                if (composed !== work) {
                    if (work !== src) work.recycle()
                    work = composed
                }
            }
        }

        if (!state.border.isNone) {
            val framed = state.border.apply(work)
            if (framed !== work) {
                if (work !== src) work.recycle()
                work = framed
            }
        }

        return work
    }

    // ---- detail ----------------------------------------------------------

    /** Above this many pixels, sharpening runs on a downscaled copy. */
    private const val MAX_DETAIL_PIXELS = 2_000_000L


    /**
     * Apply a manual grade as a layer. Runs the matrix and tone stages only, and
     * cross-fades by [opacity] so a half-strength layer reads as a real blend.
     */
    private fun applyColourLayer(src: Bitmap, adj: Adjustments, opacity: Float): Bitmap {
        if (!adj.hasAnyChange()) return src
        val matrix = buildMatrix(Preset("layer", "Layer"), adj, 0f, 0f)
        val graded = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(graded).drawBitmap(
            src, 0f, 0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            },
        )
        val toned = applyTone(graded, adj.highlights, adj.shadows, adj.vibrance, adj.hueShift, adj.whites, adj.blacks, adj.dehaze)
        if (toned !== graded) graded.recycle()
        return if (opacity >= 0.999f) toned else {
            val mixed = blendWithMask(src, toned, null, opacity)
            if (mixed !== toned) toned.recycle()
            mixed
        }
    }

    private fun applyDetail(
        src: Bitmap,
        sharpen: Float,
        texture: Float,
        clarity: Float,
        sharpenMask: Float = 0f,
    ): Bitmap {
        if (sharpen < 0.005f && texture < 0.005f && clarity < 0.005f) return src
        // Unsharp masking needs pixels + result + blur scratch: three full-frame
        // IntArrays. Past ~2 MP that costs more than the effect is worth, so run
        // it on a downscaled copy and scale the result back up.
        val totalPixels = src.width.toLong() * src.height.toLong()
        if (totalPixels > MAX_DETAIL_PIXELS) {
            val scale = kotlin.math.sqrt(MAX_DETAIL_PIXELS.toDouble() / totalPixels).toFloat()
            val small = Bitmap.createScaledBitmap(
                src,
                (src.width * scale).toInt().coerceAtLeast(1),
                (src.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            val blurred = applyDetail(small, sharpen, texture, clarity, sharpenMask)
            val back = Bitmap.createScaledBitmap(blurred, src.width, src.height, true)
            if (blurred !== small) blurred.recycle()
            if (small !== src) small.recycle()
            return back
        }

        // Unsharp masking needs two extra full-frame buffers. On a 3000px export
        // that used to be ~280 MB and killed the process. Everything is now sized
        // to the same budget and only the working arrays are kept alive.
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val result = pixels.copyOf()

        if (sharpen >= 0.005f) {
            val soft = boxBlur(pixels, w, h, 1)
            if (sharpenMask >= 0.005f) {
                unsharpMaskedInto(result, pixels, soft, w, h, sharpen * 1.15f, sharpenMask)
            } else {
                unsharpInto(result, pixels, soft, w * h, sharpen * 1.15f)
            }
            // soft falls out of scope here and is collected before the next pass,
            // so the peak is two buffers plus the blur scratch, not four.
        }
        if (texture >= 0.005f) {
            // medium radius: enough to lift skin and leaves, not enough to look
            // like a local-contrast pass
            val softMid = boxBlur(pixels, w, h, 2)
            unsharpInto(result, pixels, softMid, w * h, texture * 0.55f)
        }
        if (clarity >= 0.005f) {
            val softWide = boxBlur(pixels, w, h, 3)
            unsharpInto(result, pixels, softWide, w * h, clarity * 0.7f)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Unsharp, attenuated where the neighbourhood is flat.
     *
     * Smooth areas are exactly where sharpening turns noise into visible grain, so
     * the gain is scaled by the local range: strong edges keep full amount, quiet
     * patches get very little.
     */
    private fun unsharpMaskedInto(
        out: IntArray,
        base: IntArray,
        soft: IntArray,
        w: Int,
        h: Int,
        amount: Float,
        maskStrength: Float,
    ) {
        val m = maskStrength.coerceIn(0f, 1f)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                // local contrast from the four neighbours in luma
                val c = base[i]
                val l0 = ((c shr 16) and 0xFF) * 0.299f +
                    ((c shr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f
                var lo = l0
                var hi = l0
                for (k in 0 until 4) {
                    val xx = when (k) {
                        0 -> (x - 1).coerceIn(0, w - 1)
                        1 -> (x + 1).coerceIn(0, w - 1)
                        else -> x
                    }
                    val yy = when (k) {
                        2 -> (y - 1).coerceIn(0, h - 1)
                        3 -> (y + 1).coerceIn(0, h - 1)
                        else -> y
                    }
                    val q = base[yy * w + xx]
                    val lq = ((q shr 16) and 0xFF) * 0.299f +
                        ((q shr 8) and 0xFF) * 0.587f + (q and 0xFF) * 0.114f
                    if (lq < lo) lo = lq
                    if (lq > hi) hi = lq
                }
                val range = hi - lo
                // 0 at a flat patch, 1 once the edge is strong
                val edge = (range / 26f).coerceIn(0f, 1f)
                val gate = 1f - m * (1f - edge)

                val b = base[i]
                val s = soft[i]
                val a = amount * gate
                val br = (b shr 16) and 0xFF
                val bg = (b shr 8) and 0xFF
                val bb = b and 0xFF
                val sr = (s shr 16) and 0xFF
                val sg = (s shr 8) and 0xFF
                val sb = s and 0xFF
                val nr = (br + (br - sr) * a).toInt().coerceIn(0, 255)
                val ng = (bg + (bg - sg) * a).toInt().coerceIn(0, 255)
                val nb = (bb + (bb - sb) * a).toInt().coerceIn(0, 255)
                out[i] = (b and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
            }
        }
    }

    /** out = base + amount * (base - soft), per channel. */
    private fun unsharpInto(out: IntArray, base: IntArray, soft: IntArray, n: Int, amount: Float) {
        var i = 0
        while (i < n) {
            val b = base[i]
            val s = soft[i]
            val ar = (b shr 16) and 0xFF
            val ag = (b shr 8) and 0xFF
            val ab = b and 0xFF
            val sr = (s shr 16) and 0xFF
            val sg = (s shr 8) and 0xFF
            val sb = s and 0xFF
            val r = (ar + (ar - sr) * amount).toInt().coerceIn(0, 255)
            val g = (ag + (ag - sg) * amount).toInt().coerceIn(0, 255)
            val bl = (ab + (ab - sb) * amount).toInt().coerceIn(0, 255)
            out[i] = (b and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
            i++
        }
    }

    /**
     * Film grain. Additive per-channel noise around the original value, with the
     * strength scaled so the slider is usable across its whole range: 0 is a
     * clean frame, 1 is roughly ISO 3200.
     */
    private fun applyGrain(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val amt = amount.coerceIn(0f, 1f)
        // Chroma noise is what makes grain read as film rather than as digital
        // banding, so blue gets a slightly larger swing than red.
        val swing = 52f * amt
        // Prebuilt noise tile instead of four Random calls per pixel: same look,
        // roughly a quarter of the cost, which matters because this runs on every
        // slider tick.
        val tile = noiseTile(256)
        val tilePx = IntArray(256 * 256)
        tile.getPixels(tilePx, 0, 256, 0, 0, 256, 256)
        var t = 0
        val modulus = tilePx.size

        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val n = tilePx[t]
            if (t >= modulus - 1) t = 0 else t++
            val base = (n and 0xFF) - 128            // -128..+127
            val other = ((n shr 8) and 0xFF) - 128

            val luma = (base * swing / 128f).toInt()
            val dr = (other * swing * 0.35f / 128f).toInt()
            val db = (-other * swing * 0.45f / 128f).toInt()

            val nr = (r + luma + dr).coerceIn(0, 255)
            val ng = (g + luma).coerceIn(0, 255)
            val nb = (b + luma + db).coerceIn(0, 255)
            px[i] = (p and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Separable box blur, two passes. */
    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val span = r * 2 + 1

        for (y in 0 until h) {
            var sr = 0
            var sg = 0
            var sb = 0
            val row = y * w
            for (x in -r..r) {
                val xx = x.coerceIn(0, w - 1)
                val px = src[row + xx]
                sr += px and 0xFF
                sg += (px shr 8) and 0xFF
                sb += (px shr 16) and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (0xFF shl 24) or ((sb / span) shl 16) or ((sg / span) shl 8) or (sr / span)
                val addX = (x + r + 1).coerceAtMost(w - 1)
                val remX = (x - r).coerceAtLeast(0)
                val add = src[row + addX]
                val rem = src[row + remX]
                sr += (add and 0xFF) - (rem and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
            }
        }

        for (x in 0 until w) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (y in -r..r) {
                val yy = y.coerceIn(0, h - 1)
                val px = tmp[yy * w + x]
                sr += px and 0xFF
                sg += (px shr 8) and 0xFF
                sb += (px shr 16) and 0xFF
            }
            for (y in 0 until h) {
                out[y * w + x] = (0xFF shl 24) or ((sb / span) shl 16) or ((sg / span) shl 8) or (sr / span)
                val addY = (y + r + 1).coerceAtMost(h - 1)
                val remY = (y - r).coerceAtLeast(0)
                val add = tmp[addY * w + x]
                val rem = tmp[remY * w + x]
                sr += (add and 0xFF) - (rem and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
            }
        }
        return out
    }

    // ---- grain tile ------------------------------------------------------

    private var noiseCache: Bitmap? = null

    private fun noiseTile(size: Int): Bitmap {
        noiseCache?.let { if (it.width == size) return it }
        val rnd = Random(1337)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val px = IntArray(size * size)
        for (i in px.indices) {
            val v = 110 + rnd.nextInt(45)
            px[i] = android.graphics.Color.argb(v, v, v, v)
        }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        noiseCache = bmp
        return bmp
    }

    // ---- export ----------------------------------------------------------

    suspend fun export(
        context: Context,
        bitmap: Bitmap,
        settings: ExportSettings = ExportSettings(),
    ): Boolean = exportUri(context, bitmap, settings) != null

    /** Same as [export] but returns the saved uri, for share intents. */
    suspend fun exportUri(
        context: Context,
        bitmap: Bitmap,
        settings: ExportSettings = ExportSettings(),
    ): Uri? = withContext(Dispatchers.IO) {
        val fitted = settings.fit(bitmap)
        val name = "Flow_${System.currentTimeMillis()}.${settings.format.ext}"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, settings.format.mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Flow")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null
                resolver.openOutputStream(uri)?.use { stream ->
                    fitted.compress(settings.format.compressFormat, settings.quality, stream)
                } ?: return@withContext null
                if (fitted !== bitmap) fitted.recycle()
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Flow",
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                FileOutputStream(file).use { stream ->
                    fitted.compress(settings.format.compressFormat, settings.quality, stream)
                }
                if (fitted !== bitmap) fitted.recycle()
                @Suppress("DEPRECATION")
                Uri.fromFile(file)
            }
        } catch (t: Throwable) {
            null
        }
    }

    fun sampleForHistogram(src: Bitmap, maxDim: Int = 320): Bitmap {
        val scale = min(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height)
        if (scale >= 1f) return src
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    /**
     * Square preview tile for the filter carousel: the user's own photo with one
     * cube applied. Cheap because it runs on a ~72px source.
     */
    fun filterThumb(base: Bitmap, lut: Lut?, intensity: Float = 1f): Bitmap {
        val size = 96
        val src = if (base.width >= base.height) {
            val h = (size.toFloat() * base.height / base.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(base, size, h, true)
        } else {
            val w = (size.toFloat() * base.width / base.height).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(base, w, size, true)
        }
        // square-crop centre
        val side = minOf(src.width, src.height)
        val sq = Bitmap.createBitmap(
            src,
            (src.width - side) / 2,
            (src.height - side) / 2,
            side,
            side,
        )
        if (src !== base) src.recycle()
        if (lut == null) return sq
        val out = Lut.apply(sq, lut, intensity)
        if (out !== sq) sq.recycle()
        return out
    }
    /**
     * Highlights / shadows / vibrance / hue shift.
     * These need per-pixel luminance so they cannot be folded into a matrix.
     */

    /**
     * Dark channel prior dehaze.
     *
     * Haze puts a near-constant veil between the camera and the subject. In a
     * clean photo almost every patch has at least one very dark channel; in a
     * hazy one the minimum channel is lifted. Measuring that lift gives the veil
     * and where it is thickest, which is then subtracted out.
     *
     * I = J * t + A * (1 - t)   ->   J = (I - A) / t + A
     */
    private fun dehaze(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val dark = FloatArray(w * h)
        var sum = 0f
        for (i in px.indices) {
            val p = px[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val m = minOf(r, minOf(g, b))
            dark[i] = m
            sum += m
        }
        val meanDark = sum / px.size.coerceAtLeast(1)

        // atmospheric light: the brightest pixels in the dark channel
        val sorted = dark.copyOf()
        sorted.sort()
        val atm = sorted[(sorted.size * 0.999f).toInt().coerceIn(0, sorted.size - 1)]
            .coerceIn(0.35f, 1f)

        // omega controls how much veil is removed; keep it below 1 so some depth
        // survives instead of the image going flat and grey
        val omega = 0.72f * amount.coerceIn(-1f, 1f)
        val out = IntArray(w * h)

        for (i in px.indices) {
            val p = px[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            // transmission, floored so a very veiled patch cannot explode
            val t = (1f - omega * (dark[i] / atm)).coerceIn(0.22f, 1f)

            var nr = (r - atm) / t + atm
            var ng = (g - atm) / t + atm
            var nb = (b - atm) / t + atm

            // negative dehaze adds haze instead
            if (amount < 0f) {
                val k = (-amount).coerceIn(0f, 1f) * meanDark * 1.4f
                nr = nr * (1f - k) + atm * k
                ng = ng * (1f - k) + atm * k
                nb = nb * (1f - k) + atm * k
            }

            out[i] = (p and 0xFF000000.toInt()) or
                ((nr.coerceIn(0f, 1f) * 255f).toInt() shl 16) or
                ((ng.coerceIn(0f, 1f) * 255f).toInt() shl 8) or
                ((nb.coerceIn(0f, 1f) * 255f).toInt())
        }

        sorted.let { }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    /**
     * Colour noise reduction. A 3x3 median on the chroma difference only: luma is
     * rebuilt from the original so edge detail is untouched, which is the whole
     * point of doing it per channel instead of blurring the image.
     */
    private fun denoiseColour(src: Bitmap, strength: Float): Bitmap {
        if (strength < 0.005f) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        val k = strength.coerceIn(0f, 1f)
        val rs = IntArray(9)
        val bs = IntArray(9)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = px[i]
                var n = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, w - 1)
                        val q = px[yy * w + xx]
                        rs[n] = (q shr 16) and 0xFF
                        bs[n] = q and 0xFF
                        n++
                    }
                }
                java.util.Arrays.sort(rs, 0, n)
                java.util.Arrays.sort(bs, 0, n)
                val mr = rs[n / 2]
                val mb = bs[n / 2]

                val r0 = (p shr 16) and 0xFF
                val g0 = (p shr 8) and 0xFF
                val b0 = p and 0xFF
                // move toward the median but keep the original luma exactly
                val l0 = (r0 * 0.299f + g0 * 0.587f + b0 * 0.114f)
                var nr = r0 + (mr - r0) * k
                var nb = b0 + (mb - b0) * k
                var ng = g0 + ((mr + mb) / 2f - (r0 + b0) / 2f) * k
                val l1 = nr * 0.299f + ng * 0.587f + nb * 0.114f
                val fix = l0 - l1
                nr += fix
                ng += fix
                nb += fix

                out[i] = (p and 0xFF000000.toInt()) or
                    (nr.toInt().coerceIn(0, 255) shl 16) or
                    (ng.toInt().coerceIn(0, 255) shl 8) or
                    (nb.toInt().coerceIn(0, 255))
            }
        }

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    /** Luminance noise reduction: a gentle 3x3 average weighted by similarity. */
    private fun denoiseLuma(src: Bitmap, strength: Float): Bitmap {
        if (strength < 0.005f) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val k = strength.coerceIn(0f, 1f)
        // higher strength tolerates a bigger difference before it refuses to blend
        val tol = 10f + 46f * k

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = px[i]
                val r0 = (p shr 16) and 0xFF
                val g0 = (p shr 8) and 0xFF
                val b0 = p and 0xFF
                var sr = 0f
                var sg = 0f
                var sb = 0f
                var sw = 0f
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, w - 1)
                        val q = px[yy * w + xx]
                        val qr = (q shr 16) and 0xFF
                        val qg = (q shr 8) and 0xFF
                        val qb = q and 0xFF
                        val d = (kotlin.math.abs(qr - r0) + kotlin.math.abs(qg - g0) +
                            kotlin.math.abs(qb - b0)) / 3f
                        val weight = kotlin.math.exp(-(d * d) / (2f * tol * tol))
                        sr += qr * weight
                        sg += qg * weight
                        sb += qb * weight
                        sw += weight
                    }
                }
                if (sw <= 0f) {
                    out[i] = p
                } else {
                    val nr = r0 + (sr / sw - r0) * k
                    val ng = g0 + (sg / sw - g0) * k
                    val nb = b0 + (sb / sw - b0) * k
                    out[i] = (p and 0xFF000000.toInt()) or
                        (nr.toInt().coerceIn(0, 255) shl 16) or
                        (ng.toInt().coerceIn(0, 255) shl 8) or
                        (nb.toInt().coerceIn(0, 255))
                }
            }
        }

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    fun applyTone(
        src: Bitmap,
        highlights: Float,
        shadows: Float,
        vibrance: Float,
        hueShift: Float,
        whites: Float = 0f,
        blacks: Float = 0f,
        dehaze: Float = 0f,
    ): Bitmap {
        if (kotlin.math.abs(highlights) < 0.004f &&
            kotlin.math.abs(shadows) < 0.004f &&
            kotlin.math.abs(vibrance) < 0.004f &&
            kotlin.math.abs(hueShift) < 0.004f &&
            kotlin.math.abs(whites) < 0.004f &&
            kotlin.math.abs(blacks) < 0.004f &&
            kotlin.math.abs(dehaze) < 0.004f
        ) {
            return src
        }

        // Dehaze is a whole-image estimate, so it runs once before the loop.
        val veiled = if (kotlin.math.abs(dehaze) >= 0.004f) dehaze(src, dehaze) else src
        val w = veiled.width
        val h = veiled.height
        val px = IntArray(w * h)
        veiled.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        for (i in px.indices) {
            val p = px[i]
            var r = ((p shr 16) and 0xFF).toFloat() / 255f
            var g = ((p shr 8) and 0xFF).toFloat() / 255f
            var b = (p and 0xFF).toFloat() / 255f

            var l = r * 0.299f + g * 0.587f + b * 0.114f

            // Whites and blacks move the *ends* of the tone ramp rather than
            // lifting whole regions: white scales everything above the midpoint
            // toward clipping, black pulls the bottom toward zero. Highlights and
            // shadows below recover detail inside those regions instead.
            if (whites != 0f) {
                // pivot at mid grey so the shadows stay put
                val k = whites * 0.55f
                val gain = 1f + k
                r = 0.5f + (r - 0.5f) * gain
                g = 0.5f + (g - 0.5f) * gain
                b = 0.5f + (b - 0.5f) * gain
            }
            if (blacks != 0f) {
                // negative lifts the black point (washed out), positive crushes it
                val lift = -blacks * 0.22f
                r += lift
                g += lift
                b += lift
                if (blacks > 0f) {
                    // a touch of extra contrast only where the pixel is dark
                    val grip = (1f - l).coerceIn(0f, 1f) * blacks * 0.35f
                    r -= grip * r
                    g -= grip * g
                    b -= grip * b
                }
            }

            // shadows lift the low end, highlights recover the top end
            if (shadows != 0f) {
                val wgt = (1f - l).coerceIn(0f, 1f)
                val k = shadows * wgt * 0.45f
                r += k
                g += k
                b += k
            }
            if (highlights != 0f) {
                val wgt = l.coerceIn(0f, 1f)
                val k = highlights * wgt * 0.45f
                r += k
                g += k
                b += k
            }

            // vibrance: boost muted colours, leave saturated ones alone
            if (vibrance != 0f) {
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                val sat = mx - mn
                val boost = vibrance * (1f - sat) * 1.1f
                val avg = (r + g + b) / 3f
                r += (r - avg) * boost
                g += (g - avg) * boost
                b += (b - avg) * boost
            }

            // hue rotation around the luma axis
            if (hueShift != 0f) {
                val ang = hueShift * Math.PI.toFloat() * 2f
                val cs = kotlin.math.cos(ang)
                val sn = kotlin.math.sin(ang)
                val nr = (0.299f + 0.701f * cs + 0.168f * sn) * r +
                    (0.587f - 0.587f * cs + 0.330f * sn) * g +
                    (0.114f - 0.114f * cs - 0.497f * sn) * b
                val ng = (0.299f - 0.299f * cs - 0.328f * sn) * r +
                    (0.587f + 0.413f * cs + 0.035f * sn) * g +
                    (0.114f - 0.114f * cs + 0.292f * sn) * b
                val nb = (0.299f - 0.300f * cs + 1.250f * sn) * r +
                    (0.587f - 0.588f * cs - 1.050f * sn) * g +
                    (0.114f + 0.886f * cs - 0.203f * sn) * b
                r = nr
                g = ng
                b = nb
            }

            out[i] = (0xFF shl 24) or
                ((r.coerceIn(0f, 1f) * 255f).toInt() shl 16) or
                ((g.coerceIn(0f, 1f) * 255f).toInt() shl 8) or
                (b.coerceIn(0f, 1f) * 255f).toInt()
        }
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    /**
     * Cheap 0..1 cross-fade between the untouched photo and a fully-processed
     * render. Used so the intensity slider stays real-time: the expensive work
     * happens once, dragging only re-runs this lerp.
     */
    fun blend(base: Bitmap, target: Bitmap, amount: Float): Bitmap {
        if (amount >= 0.999f) return target
        if (amount <= 0.001f) return base
        if (base.width != target.width || base.height != target.height) return target

        val w = base.width
        val h = base.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        base.getPixels(a, 0, w, 0, 0, w, h)
        target.getPixels(b, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val inv = 1f - amount
        for (i in out.indices) {
            val pa = a[i]
            val pb = b[i]
            val r = (((pa shr 16) and 0xFF) * inv + ((pb shr 16) and 0xFF) * amount).toInt().coerceIn(0, 255)
            val g = (((pa shr 8) and 0xFF) * inv + ((pb shr 8) and 0xFF) * amount).toInt().coerceIn(0, 255)
            val bl = ((pa and 0xFF) * inv + (pb and 0xFF) * amount).toInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }}
