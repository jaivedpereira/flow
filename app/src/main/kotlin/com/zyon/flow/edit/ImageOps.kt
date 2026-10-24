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
    ): Bitmap = try {
        renderInternal(src, state, lut, mask, customTypeface)
    } catch (oom: OutOfMemoryError) {
        val half = Bitmap.createScaledBitmap(
            src,
            (src.width / 2).coerceAtLeast(1),
            (src.height / 2).coerceAtLeast(1),
            true,
        )
        try {
            val small = renderInternal(half, state, lut, mask, customTypeface)
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
    ): Bitmap {
        val preset = state.preset
        val adj = state.adjustments

        var work = Geometry.apply(src, state.transform)

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
        val toned = applyTone(work, adj.highlights, adj.shadows, adj.vibrance, adj.hueShift)
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

        val detail = applyDetail(work, adj.sharpen, adj.clarity)
        if (detail !== work && work !== src) work.recycle()

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
            val tile = noiseTile(256)
            // Two passes do the job of a blend mode on every API level:
            //   dark pass  = multiply-ish (ink)
            //   light pass = screen-ish (specular speckle)
            // Using OVERLAY with a mid-grey tile was a no-op, which is why the
            // grain slider appeared to do nothing at all.
            val amp = (grain * 0.62f).coerceIn(0f, 1f)
            val canvas = Canvas(work)
            val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                isFilterBitmap = true
                alpha = (amp * 132f).toInt().coerceIn(0, 255)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
            }
            canvas.drawRect(0f, 0f, work.width.toFloat(), work.height.toFloat(), dark)

            val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                isFilterBitmap = true
                alpha = (amp * 74f).toInt().coerceIn(0, 255)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
            }
            canvas.drawRect(0f, 0f, work.width.toFloat(), work.height.toFloat(), light)
        }

        if (!state.overlays.isEmpty) {
            val composed = state.overlays.apply(work, customTypeface)
            if (composed !== work) {
                if (work !== src) work.recycle()
                work = composed
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

    private fun applyDetail(src: Bitmap, sharpen: Float, clarity: Float): Bitmap {
        if (sharpen < 0.005f && clarity < 0.005f) return src

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
            unsharpInto(result, pixels, soft, w * h, sharpen * 1.15f)
            // soft falls out of scope here and is collected before the next pass,
            // so the peak is two buffers plus the blur scratch, not four.
        }
        if (clarity >= 0.005f) {
            val softWide = boxBlur(pixels, w, h, 3)
            unsharpInto(result, pixels, softWide, w * h, clarity * 0.7f)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
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

    suspend fun export(context: Context, bitmap: Bitmap): Boolean =
        exportUri(context, bitmap) != null

    /** Same as [export] but returns the saved uri, for share intents. */
    suspend fun exportUri(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val name = "Flow_${System.currentTimeMillis()}.jpg"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Flow")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                } ?: return@withContext null
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
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
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
    fun applyTone(
        src: Bitmap,
        highlights: Float,
        shadows: Float,
        vibrance: Float,
        hueShift: Float,
    ): Bitmap {
        if (kotlin.math.abs(highlights) < 0.004f &&
            kotlin.math.abs(shadows) < 0.004f &&
            kotlin.math.abs(vibrance) < 0.004f &&
            kotlin.math.abs(hueShift) < 0.004f
        ) {
            return src
        }
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        for (i in px.indices) {
            val p = px[i]
            var r = ((p shr 16) and 0xFF).toFloat() / 255f
            var g = ((p shr 8) and 0xFF).toFloat() / 255f
            var b = (p and 0xFF).toFloat() / 255f

            val l = r * 0.299f + g * 0.587f + b * 0.114f

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
