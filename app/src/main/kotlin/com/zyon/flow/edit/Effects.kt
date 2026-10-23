/**
 * Flow — artistic effects, in the spirit of GrainRad.
 *
 * Every effect is a real pixel algorithm, not a filter preset:
 *   ASCII conversion, error-diffusion dithering, ordered dithering, halftone,
 *   pixelation, posterization, CRT scanlines, chromatic aberration, halation,
 *   bloom, colour noise and block displacement.
 *
 * Each effect exposes an [amount] (intensity 0..1) and one [param] (0..1) whose
 * meaning depends on the effect — see [Effect.paramLabel].
 */

package com.zyon.flow.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

data class Effect(
    val id: String,
    val name: String,
    val paramLabel: String = "",
    val paramDefault: Float = 0.5f,
    val apply: (Bitmap, Float, Float) -> Bitmap,
)

object Effects {

    val All: List<Effect> = listOf(
        Effect("none", "Original", apply = { b, _, _ -> b }),
        Effect("ascii", "ASCII", "Density", 0.5f, ::ascii),
        Effect("dither-fs", "Dither", "Threshold", 0.5f) { b, a, p -> ditherError(b, a, p) },
        Effect("bayer", "Bayer", "Levels", 0.5f, ::bayer),
        Effect("halftone", "Halftone", "Dot size", 0.5f, ::halftone),
        Effect("pixelate", "Pixelate", "Block", 0.35f, ::pixelate),
        Effect("posterize", "Posterize", "Levels", 0.5f, ::posterize),
        Effect("scanlines", "CRT", "Line gap", 0.5f, ::scanlines),
        Effect("chroma", "Chroma", "Shift", 0.4f, ::chroma),
        Effect("halation", "Halation", "Glow", 0.5f, ::halation),
        Effect("bloom", "Bloom", "Radius", 0.5f, ::bloom),
        Effect("noise", "Noise", "Amount", 0.4f, ::noise),
        Effect("mosh", "Datamosh", "Block", 0.4f, ::mosh),
        Effect("threshold", "Threshold", "Cut", 0.5f, ::threshold),
    )

    fun byId(id: String?): Effect = All.firstOrNull { it.id == id } ?: All.first()

    // ---------------------------------------------------------------- utils

    private fun rgba(src: Bitmap): Triple<IntArray, Int, Int> {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        return Triple(px, w, h)
    }

    private fun toBitmap(px: IntArray, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun lum(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun grey(v: Int): Int {
        val c = v.coerceIn(0, 255)
        return (0xFF shl 24) or (c shl 16) or (c shl 8) or c
    }

    /** Blend an effect result back onto the source by [amount]. */
    private fun mix(base: Bitmap, effected: Bitmap, amount: Float): Bitmap {
        if (amount >= 0.999f) return effected
        if (amount <= 0.001f) return base
        val w = base.width
        val h = base.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        base.getPixels(a, 0, w, 0, 0, w, h)
        effected.getPixels(b, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in out.indices) {
            val pa = a[i]
            val pb = b[i]
            val ar = (pa shr 16) and 0xFF
            val ag = (pa shr 8) and 0xFF
            val ab = pa and 0xFF
            val br = (pb shr 16) and 0xFF
            val bg = (pb shr 8) and 0xFF
            val bb = pb and 0xFF
            val r = (ar + (br - ar) * amount).roundToInt().coerceIn(0, 255)
            val g = (ag + (bg - ag) * amount).roundToInt().coerceIn(0, 255)
            val bl = (ab + (bb - ab) * amount).roundToInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
        return toBitmap(out, w, h)
    }

    /** Separable box blur used by halation and bloom. */
    internal fun blur(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return src.copyOf()
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val span = radius * 2 + 1
        for (y in 0 until h) {
            var sr = 0
            var sg = 0
            var sb = 0
            val row = y * w
            for (x in -radius..radius) {
                val p = src[row + x.coerceIn(0, w - 1)]
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (0xFF shl 24) or ((sr / span) shl 16) or ((sg / span) shl 8) or (sb / span)
                val add = src[row + (x + radius + 1).coerceAtMost(w - 1)]
                val rem = src[row + (x - radius).coerceAtLeast(0)]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
        for (x in 0 until w) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (y in -radius..radius) {
                val p = tmp[y.coerceIn(0, h - 1) * w + x]
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (y in 0 until h) {
                out[y * w + x] = (0xFF shl 24) or ((sb / span) shl 16) or ((sg / span) shl 8) or (sr / span)
                val add = tmp[(y + radius + 1).coerceAtMost(h - 1) * w + x]
                val rem = tmp[(y - radius).coerceAtLeast(0) * w + x]
                sr += (add and 0xFF) - (rem and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- ascii

    private val RAMP = " .:-=+*#%@".toCharArray()

    private fun ascii(src: Bitmap, amount: Float, param: Float): Bitmap {
        val w = src.width
        val h = src.height
        // cell size: denser with higher param
        val cell = (2 + (1f - param) * 10f).roundToInt().coerceIn(2, 12)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = cell.toFloat()
            color = Color.WHITE
        }
        val cols = (w / cell).coerceAtLeast(1)
        val rows = (h / cell).coerceAtLeast(1)
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                var sum = 0
                var n = 0
                val cx = c * cell
                val cy = r * cell
                for (yy in 0 until cell) {
                    val y = (cy + yy).coerceAtMost(h - 1)
                    for (xx in 0 until cell) {
                        val x = (cx + xx).coerceAtMost(w - 1)
                        sum += lum(px[y * w + x])
                        n++
                    }
                }
                val level = if (n == 0) 0 else sum / n
                val idx = (level * (RAMP.size - 1) / 255).coerceIn(0, RAMP.size - 1)
                if (RAMP[idx] != ' ') {
                    canvas.drawText(RAMP[idx].toString(), cx.toFloat(), cy + cell * 0.85f, paint)
                }
            }
        }
        return mix(src, out, amount)
    }

    // ---------------------------------------------------------------- dither

    private fun ditherError(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val g = IntArray(w * h) { lum(px[it]) }
        val thr = 110f + param * 60f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val old = g[i].toFloat()
                val newV = if (old < thr) 0 else 255
                g[i] = newV
                val err = old - newV
                if (x + 1 < w) g[i + 1] = (g[i + 1] + err * 7f / 16f).roundToInt().coerceIn(0, 255)
                if (y + 1 < h) {
                    if (x > 0) g[i + w - 1] = (g[i + w - 1] + err * 3f / 16f).roundToInt().coerceIn(0, 255)
                    g[i + w] = (g[i + w] + err * 5f / 16f).roundToInt().coerceIn(0, 255)
                    if (x + 1 < w) g[i + w + 1] = (g[i + w + 1] + err * 1f / 16f).roundToInt().coerceIn(0, 255)
                }
            }
        }
        val out = IntArray(w * h) { grey(g[it]) }
        return mix(src, toBitmap(out, w, h), amount)
    }

    private val BAYER8 = arrayOf(
        intArrayOf(0, 32, 8, 40, 2, 34, 10, 42),
        intArrayOf(48, 16, 56, 24, 50, 18, 58, 26),
        intArrayOf(12, 44, 4, 36, 14, 46, 6, 38),
        intArrayOf(60, 28, 52, 20, 62, 30, 54, 22),
        intArrayOf(3, 35, 11, 43, 1, 33, 9, 41),
        intArrayOf(51, 19, 59, 27, 49, 17, 57, 25),
        intArrayOf(15, 47, 7, 39, 13, 45, 5, 37),
        intArrayOf(63, 31, 55, 23, 61, 29, 53, 21),
    )

    private fun bayer(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val levels = (2 + (1f - param) * 6f).roundToInt().coerceIn(2, 8)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val v = lum(px[i])
                val t = (BAYER8[y % 8][x % 8] / 64f) - 0.5f
                val steps = levels - 1
                val q = ((v / 255f * steps) + t * 0.9f).roundToInt().coerceIn(0, steps)
                out[i] = grey((q * 255f / steps).roundToInt())
            }
        }
        return mix(src, toBitmap(out, w, h), amount)
    }

    // ---------------------------------------------------------------- halftone

    private fun halftone(src: Bitmap, amount: Float, param: Float): Bitmap {
        val w = src.width
        val h = src.height
        val cell = (3 + (1f - param) * 9f).roundToInt().coerceIn(3, 12)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        for (y in 0 until h step cell) {
            for (x in 0 until w step cell) {
                var sum = 0
                var n = 0
                for (yy in 0 until cell) {
                    val yy2 = (y + yy).coerceAtMost(h - 1)
                    for (xx in 0 until cell) {
                        val xx2 = (x + xx).coerceAtMost(w - 1)
                        sum += lum(px[yy2 * w + xx2])
                        n++
                    }
                }
                val dark = 1f - (sum.toFloat() / n / 255f)
                val radius = (cell * 0.5f) * dark
                if (radius > 0.4f) {
                    canvas.drawCircle(x + cell * 0.5f, y + cell * 0.5f, radius, paint)
                }
            }
        }
        return mix(src, out, amount)
    }

    // ---------------------------------------------------------------- pixelate

    private fun pixelate(src: Bitmap, amount: Float, param: Float): Bitmap {
        val w = src.width
        val h = src.height
        val block = (2 + param * 34f).roundToInt().coerceIn(2, 40)
        val sw = (w / block).coerceAtLeast(1)
        val sh = (h / block).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = Bitmap.createScaledBitmap(small, w, h, false)
        return mix(src, out, amount)
    }

    // ---------------------------------------------------------------- posterize

    private fun posterize(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val levels = (2 + (1f - param) * 8f).roundToInt().coerceIn(2, 10)
        val step = 255f / (levels - 1)
        val out = IntArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            val r = (((p shr 16) and 0xFF) / step).roundToInt() * step
            val g = (((p shr 8) and 0xFF) / step).roundToInt() * step
            val b = ((p and 0xFF) / step).roundToInt() * step
            out[i] = (0xFF shl 24) or
                (r.roundToInt().coerceIn(0, 255) shl 16) or
                (g.roundToInt().coerceIn(0, 255) shl 8) or
                b.roundToInt().coerceIn(0, 255)
        }
        return mix(src, toBitmap(out, w, h), amount)
    }

    // ---------------------------------------------------------------- scanlines

    private fun scanlines(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val step = (2 + (1f - param) * 6f).roundToInt().coerceIn(2, 8)
        val out = px.copyOf()
        for (y in 0 until h) {
            if (y % step == 0) {
                for (x in 0 until w) {
                    val i = y * w + x
                    val p = out[i]
                    val r = (((p shr 16) and 0xFF) * 0.62f).roundToInt()
                    val g = (((p shr 8) and 0xFF) * 0.62f).roundToInt()
                    val b = ((p and 0xFF) * 0.62f).roundToInt()
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return mix(src, toBitmap(out, w, h), amount)
    }

    // ---------------------------------------------------------------- chroma

    private fun chroma(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val shift = (1 + param * 18f).roundToInt().coerceIn(1, 20)
        val out = px.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val srcR = y * w + (x + shift).coerceAtMost(w - 1)
                val srcB = y * w + (x - shift).coerceAtLeast(0)
                val r = (px[srcR] shr 16) and 0xFF
                val g = (px[i] shr 8) and 0xFF
                val b = px[srcB] and 0xFF
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return mix(src, toBitmap(out, w, h), amount)
    }

    // ---------------------------------------------------------------- halation

    private fun halation(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val radius = (3 + param * 26f).roundToInt().coerceIn(3, 30)
        val blurred = blur(px, w, h, radius)
        val out = IntArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            val q = blurred[i]
            val br = (q shr 16) and 0xFF
            val bg = (q shr 8) and 0xFF
            val bb = q and 0xFF
            // only bright areas glow, and they glow warm
            val bright = (br * 0.3f + bg * 0.6f + bb * 0.1f) / 255f
            val gain = (bright * bright * 0.9f).coerceIn(0f, 1f)
            val r = (((p shr 16) and 0xFF) + br * gain * 0.55f).roundToInt().coerceIn(0, 255)
            val g = (((p shr 8) and 0xFF) + bg * gain * 0.34f).roundToInt().coerceIn(0, 255)
            val b = ((p and 0xFF) + bb * gain * 0.18f).roundToInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return mix(src, toBitmap(out, w, h), amount)
    }

    // ---------------------------------------------------------------- bloom

    private fun bloom(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val radius = (2 + param * 30f).roundToInt().coerceIn(2, 34)
        val blurred = blur(px, w, h, radius)
        val out = IntArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            val q = blurred[i]
            val r = (((p shr 16) and 0xFF) + ((q shr 16) and 0xFF) * 0.55f).roundToInt().coerceIn(0, 255)
            val g = (((p shr 8) and 0xFF) + ((q shr 8) and 0xFF) * 0.55f).roundToInt().coerceIn(0, 255)
            val b = ((p and 0xFF) + (q and 0xFF) * 0.55f).roundToInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return mix(src, toBitmap(out, w, h), amount)
    }

    // ---------------------------------------------------------------- noise

    private fun noise(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val rnd = Random(9001)
        val amp = (param * 90f).roundToInt().coerceIn(1, 90)
        val out = IntArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            val n = rnd.nextInt(-amp, amp + 1)
            val r = (((p shr 16) and 0xFF) + n).coerceIn(0, 255)
            val g = (((p shr 8) and 0xFF) + n).coerceIn(0, 255)
            val b = ((p and 0xFF) + n).coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return mix(src, toBitmap(out, w, h), amount)
    }

    // ---------------------------------------------------------------- datamosh

    private fun mosh(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val block = (8 + param * 60f).roundToInt().coerceIn(8, 70)
        val rnd = Random(4242)
        val out = px.copyOf()
        val rows = h / block
        val cols = w / block
        repeat((rows * cols / 6).coerceAtLeast(1)) {
            val bx = rnd.nextInt(0, cols.coerceAtLeast(1)) * block
            val by = rnd.nextInt(0, rows.coerceAtLeast(1)) * block
            val shiftX = rnd.nextInt(-block, block + 1)
            for (y in by until (by + block).coerceAtMost(h)) {
                for (x in bx until (bx + block).coerceAtMost(w)) {
                    val sx = (x + shiftX).coerceIn(0, w - 1)
                    out[y * w + x] = px[y * w + sx]
                }
            }
        }
        return mix(src, toBitmap(out, w, h), amount)
    }

    // ---------------------------------------------------------------- threshold

    private fun threshold(src: Bitmap, amount: Float, param: Float): Bitmap {
        val (px, w, h) = rgba(src)
        val cut = 40 + param * 175f
        val out = IntArray(w * h) {
            val v = lum(px[it])
            grey(if (v < cut) 0 else 255)
        }
        return mix(src, toBitmap(out, w, h), amount)
    }

    /** Thumbnail for the effects carousel: applies at full strength so it reads. */
    fun preview(base: Bitmap, effect: Effect, param: Float): Bitmap {
        if (effect.id == "none") return base
        return runCatching { effect.apply(base, 1f, param) }.getOrDefault(base)
    }

    /** Distance from identity, used to keep [mix] monotonic. */
    internal fun strengthOf(v: Float): Float = abs(v).coerceIn(0f, 1f).pow(1f)
}
