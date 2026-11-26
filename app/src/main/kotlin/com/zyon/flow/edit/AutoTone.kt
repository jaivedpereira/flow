/**
 * Flow — one-tap auto tone.
 *
 * Looks at the histogram and the channel means of the photo and proposes values
 * for the same sliders a person would reach for first: exposure to centre the
 * midtones, warmth to neutralise a colour cast, and whites/blacks to use the ends
 * of the range instead of leaving a gap.
 *
 * Deliberately conservative. Auto that overshoots is worse than no auto, so every
 * number is clamped and nothing that is already good gets touched.
 */

package com.zyon.flow.edit

import android.graphics.Bitmap

object AutoTone {

    /** Result of a measurement pass; all values are 0..1 unless noted. */
    private class Stats(
        val medianLuma: Float,
        val p01: Float,
        val p99: Float,
        val meanR: Float,
        val meanG: Float,
        val meanB: Float,
        val meanSat: Float,
    )

    fun suggest(src: Bitmap): Adjustments {
        val stats = measure(src) ?: return Adjustments()
        return fromStats(stats)
    }

    private fun measure(src: Bitmap): Stats? {
        val small = ImageOps.sampleForHistogram(src, 200)
        val w = small.width
        val h = small.height
        if (w <= 0 || h <= 0) return null
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)

        val luma = FloatArray(px.size)
        var sr = 0f
        var sg = 0f
        var sb = 0f
        var ssat = 0f

        for (i in px.indices) {
            val p = px[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            luma[i] = r * 0.299f + g * 0.587f + b * 0.114f
            sr += r
            sg += g
            sb += b
            ssat += maxOf(r, g, b) - minOf(r, g, b)
        }

        luma.sort()
        val n = luma.size
        fun at(q: Float): Float = luma[(q * (n - 1)).toInt().coerceIn(0, n - 1)]

        return Stats(
            medianLuma = at(0.50f),
            p01 = at(0.01f),
            p99 = at(0.99f),
            meanR = sr / n,
            meanG = sg / n,
            meanB = sb / n,
            meanSat = ssat / n,
        )
    }

    private fun fromStats(s: Stats): Adjustments {
        // ---- exposure: bring the median to mid grey.
        // In sRGB, one stop roughly doubles the linear value, so work in log2.
        val target = 0.46f
        var exposure = 0f
        if (s.medianLuma > 0.02f) {
            val ratio = target / s.medianLuma
            exposure = (kotlin.math.ln(ratio.toDouble()) / kotlin.math.ln(2.0)).toFloat() * 0.42f
            exposure = exposure.coerceIn(-0.55f, 0.55f)
        }

        // ---- warmth: pull the colour cast back toward grey.
        // The red/blue imbalance is what the temperature slider moves, so derive
        // it from the ratio rather than from an absolute difference.
        val rb = (s.meanR - s.meanB)
        var warmth = (rb * 1.45f).coerceIn(-0.4f, 0.4f)
        // a very saturated photo is probably meant to look that way
        if (s.meanSat > 0.34f) warmth *= 0.45f

        // ---- whites / blacks: close the gap at the ends of the histogram.
        // p99 below 0.95 means the highlights never reach the top.
        var whites = 0f
        var blacks = 0f
        val topGap = 0.95f - s.p99
        if (topGap > 0.04f) whites = (topGap * 1.9f).coerceIn(0f, 0.42f)
        val botGap = s.p01 - 0.03f
        if (botGap > 0.04f) blacks = -(botGap * 1.7f).coerceIn(0f, 0.40f)

        // ---- contrast: only if the image is genuinely flat
        var contrast = 0f
        val spread = s.p99 - s.p01
        if (spread < 0.72f) {
            contrast = ((0.72f - spread) * 0.85f).coerceIn(0f, 0.35f)
        }

        return Adjustments(
            exposure = exposure,
            warmth = warmth,
            whites = whites,
            blacks = blacks,
            contrast = contrast,
        )
    }
}
