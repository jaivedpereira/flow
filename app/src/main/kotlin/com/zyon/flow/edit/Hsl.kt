/**
 * Flow — selective HSL.
 *
 * Eight hue bands with three knobs each (hue / saturation / luminance).
 * Each pixel is weighted by how close its hue is to the band centre, with a
 * smooth falloff, so adjustments blend instead of banding at the edges.
 *
 * Bands are centred on the classic 8-way split: red, orange, yellow, green,
 * cyan, blue, purple, magenta.
 */

package com.zyon.flow.edit

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class HslBand(val label: String, val centerDeg: Float, val swatch: Int) {
    RED("Red", 0f, 0xFFE04B4B.toInt()),
    ORANGE("Orange", 30f, 0xFFE08A3C.toInt()),
    YELLOW("Yellow", 60f, 0xFFD9C43C.toInt()),
    GREEN("Green", 120f, 0xFF4CAF50.toInt()),
    CYAN("Cyan", 180f, 0xFF3CB8C0.toInt()),
    BLUE("Blue", 240f, 0xFF4A7BD0.toInt()),
    PURPLE("Purple", 285f, 0xFF8E5BD0.toInt()),
    MAGENTA("Magenta", 330f, 0xFFD050A8.toInt()),
}

/** One band's three adjustments, each -1..1. */
data class HslShift(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
) {
    val isNeutral: Boolean
        get() = abs(hue) < 0.004f && abs(saturation) < 0.004f && abs(luminance) < 0.004f
}

data class Hsl(
    val shifts: Map<HslBand, HslShift> = emptyMap(),
) {
    val isIdentity: Boolean get() = shifts.values.all { it.isNeutral }

    fun shiftFor(band: HslBand): HslShift = shifts[band] ?: HslShift()

    fun with(band: HslBand, shift: HslShift): Hsl = copy(shifts = shifts + (band to shift))

    /** Apply to the whole image. Returns the source untouched when neutral. */
    fun apply(src: Bitmap): Bitmap {
        if (isIdentity) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        // Precompute band centres and weights, so the inner loop stays cheap.
        val bands = HslBand.entries
        val centres = FloatArray(bands.size) { bands[it].centerDeg }
        val hueShift = FloatArray(bands.size) { shiftFor(bands[it]).hue }
        val satShift = FloatArray(bands.size) { shiftFor(bands[it]).saturation }
        val lumShift = FloatArray(bands.size) { shiftFor(bands[it]).luminance }

        for (i in px.indices) {
            val p = px[i]
            var r = ((p shr 16) and 0xFF) / 255f
            var g = ((p shr 8) and 0xFF) / 255f
            var b = (p and 0xFF) / 255f

            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val delta = mx - mn
            // Grey pixels have no hue; leave them alone.
            if (delta < 0.02f) {
                out[i] = p
                continue
            }

            // hue in degrees
            var hue = when {
                mx == r -> 60f * (((g - b) / delta) % 6f)
                mx == g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
            if (hue < 0f) hue += 360f

            // weighted accumulation across bands
            var dHue = 0f
            var dSat = 0f
            var dLum = 0f
            var wsum = 0f
            for (k in bands.indices) {
                val d = angularDistance(hue, centres[k])          // 0..180
                val width = 45f                                   // half-band width
                if (d > width) continue
                // smooth cosine falloff
                val weight = 0.5f * (1f + cos(Math.PI.toFloat() * d / width))
                dHue += hueShift[k] * weight
                dSat += satShift[k] * weight
                dLum += lumShift[k] * weight
                wsum += weight
            }
            if (wsum <= 0.0001f) {
                out[i] = p
                continue
            }
            dHue /= wsum
            dSat /= wsum
            dLum /= wsum

            // hue rotation in HSV space
            val newHue = (hue + dHue * 30f + 360f) % 360f
            var sat = if (mx <= 0f) 0f else delta / mx
            var val_ = mx

            sat = (sat * (1f + dSat * 0.9f)).coerceIn(0f, 1f)
            // luminance is applied after the luma-preserving step below

            // HSV -> RGB
            val c = val_ * sat
            val x = c * (1f - abs(((newHue / 60f) % 2f) - 1f))
            val m = val_ - c
            var nr = 0f
            var ng = 0f
            var nb = 0f
            when {
                newHue < 60f -> { nr = c; ng = x; nb = 0f }
                newHue < 120f -> { nr = x; ng = c; nb = 0f }
                newHue < 180f -> { nr = 0f; ng = c; nb = x }
                newHue < 240f -> { nr = 0f; ng = x; nb = c }
                newHue < 300f -> { nr = x; ng = 0f; nb = c }
                else -> { nr = c; ng = 0f; nb = x }
            }
            var fr = (nr + m).coerceIn(0f, 1f)
            var fg = (ng + m).coerceIn(0f, 1f)
            var fb = (nb + m).coerceIn(0f, 1f)

            // HSV keeps V constant, so desaturating a vivid red makes it paler and
            // brighter than the original. Rescale to the original luma first, then
            // apply the requested luminance change. This is what makes band edits
            // read as "less saturated" instead of "washed out".
            val lumaBefore = r * 0.299f + g * 0.587f + b * 0.114f
            var lumaAfter = fr * 0.299f + fg * 0.587f + fb * 0.114f
            if (lumaAfter > 0.001f && lumaBefore > 0.001f) {
                val scale = (lumaBefore / lumaAfter).coerceIn(0.2f, 5f)
                fr = (fr * scale).coerceIn(0f, 1f)
                fg = (fg * scale).coerceIn(0f, 1f)
                fb = (fb * scale).coerceIn(0f, 1f)
            }
            if (dLum != 0f) {
                val gain = 1f + dLum * 0.6f
                fr = (fr * gain).coerceIn(0f, 1f)
                fg = (fg * gain).coerceIn(0f, 1f)
                fb = (fb * gain).coerceIn(0f, 1f)
            }
            r = fr
            g = fg
            b = fb

            out[i] = (p and 0xFF000000.toInt()) or
                ((r * 255f).toInt().coerceIn(0, 255) shl 16) or
                ((g * 255f).toInt().coerceIn(0, 255) shl 8) or
                (b * 255f).toInt().coerceIn(0, 255)
        }

        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    private fun angularDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    /** Small helper for the UI: spin the hue wheel by [deg]. */
    fun rotatedBy(band: HslBand, deg: Float, amount: Float): Int {
        val rad = Math.toRadians((band.centerDeg + deg).toDouble()).toFloat()
        val r = (0.5f + 0.45f * cos(rad)) * 255f
        val g = (0.5f + 0.45f * cos(rad - 2.094f)) * 255f
        val b = (0.5f + 0.45f * cos(rad - 4.189f)) * 255f
        return (0xFF shl 24) or
            (r.toInt().coerceIn(0, 255) shl 16) or
            (g.toInt().coerceIn(0, 255) shl 8) or
            (b.toInt().coerceIn(0, 255) * (1f - amount * 0.3f)).toInt().coerceIn(0, 255)
    }
}
