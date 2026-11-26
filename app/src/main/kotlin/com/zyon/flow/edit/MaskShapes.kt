/**
 * Flow — gradient mask shapes.
 *
 * A linear gradient is the classic sky/fade tool: one axis, two stops.
 * A radial mask is a soft spotlight. Both write into the same [MaskData] the
 * brush uses, so every effect that respects a mask gets them for free.
 */

package com.zyon.flow.edit

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

enum class MaskShape(val label: String) {
    BRUSH("Brush"),
    LINEAR("Linear"),
    RADIAL("Radial"),
    /** Selects pixels by how bright they are. */
    LUMA("Luma"),
    /** Selects pixels by how close their hue is to a target. */
    COLOUR("Colour"),
}

/** Normalised 0..1 control points inside the image. */
data class MaskGeometry(
    val startX: Float = 0.5f,
    val startY: Float = 0.15f,
    val endX: Float = 0.5f,
    val endY: Float = 0.85f,
    val feather: Float = 0.35f,
    val invert: Boolean = false,
)

object MaskShapes {

    /**
     * Fill [mask] from a linear gradient between two points.
     * Pixels before [start] get 1, after [end] get 0, smooth in between.
     */
    fun fillLinear(mask: MaskData, g: MaskGeometry, additive: Boolean = false) {
        val dx = g.endX - g.startX
        val dy = g.endY - g.startY
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-6f) return
        val feather = g.feather.coerceIn(0.02f, 0.98f)

        for (y in 0 until mask.height) {
            val ny = (y + 0.5f) / mask.height
            for (x in 0 until mask.width) {
                val nx = (x + 0.5f) / mask.width
                // projection of the pixel onto the gradient axis
                var t = ((nx - g.startX) * dx + (ny - g.startY) * dy) / len2
                t = t.coerceIn(0f, 1f)
                // remap so the middle feather band is the soft zone
                val lo = 0.5f - feather * 0.5f
                val hi = 0.5f + feather * 0.5f
                var v = when {
                    t <= lo -> 1f
                    t >= hi -> 0f
                    else -> 1f - ((t - lo) / (hi - lo))
                }
                if (g.invert) v = 1f - v
                val i = y * mask.width + x
                mask.values[i] = if (additive) maxOf(mask.values[i], v) else v
            }
        }
        mask.setHasContent(true)
    }

    /** Fill [mask] with a soft radial spotlight centred on start. */
    fun fillRadial(mask: MaskData, g: MaskGeometry, additive: Boolean = false) {
        val cx = g.startX
        val cy = g.startY
        // radius comes from the distance to the end point
        val radius = hypot(g.endX - cx, g.endY - cy).coerceAtLeast(0.02f)
        val feather = g.feather.coerceIn(0.02f, 0.98f)
        val inner = (radius * (1f - feather)).coerceAtLeast(0.001f)

        for (y in 0 until mask.height) {
            val ny = (y + 0.5f) / mask.height
            for (x in 0 until mask.width) {
                val nx = (x + 0.5f) / mask.width
                // squash to the image aspect so the spotlight stays circular
                val d = hypot(nx - cx, ny - cy)
                var v = when {
                    d <= inner -> 1f
                    d >= radius -> 0f
                    else -> 1f - ((d - inner) / (radius - inner))
                }
                // smoothstep the edge so it never shows a hard ring
                v = v * v * (3f - 2f * v)
                if (g.invert) v = 1f - v
                val i = y * mask.width + x
                mask.values[i] = if (additive) maxOf(mask.values[i], v) else v
            }
        }
        mask.setHasContent(true)
    }

    /**
     * Fill the mask from a luminance band. [minV] and [maxV] are 0..1 brightness;
     * [feather] softens both edges so the selection does not band.
     */
    fun fillLumaRange(
        mask: MaskData,
        src: android.graphics.Bitmap,
        minV: Float,
        maxV: Float,
        feather: Float = 0.12f,
    ) {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val lo = minOf(minV, maxV)
        val hi = maxOf(minV, maxV)
        val f = feather.coerceIn(0.01f, 0.5f)

        for (y in 0 until mask.height) {
            val sy = (y.toFloat() / mask.height * h).toInt().coerceIn(0, h - 1)
            for (x in 0 until mask.width) {
                val sx = (x.toFloat() / mask.width * w).toInt().coerceIn(0, w - 1)
                val p = px[sy * w + sx]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val luma = r * 0.299f + g * 0.587f + b * 0.114f

                var v = 1f
                if (luma < lo) v = ((luma - (lo - f)) / f).coerceIn(0f, 1f)
                else if (luma > hi) v = 1f - ((luma - hi) / f).coerceIn(0f, 1f)
                mask.values[y * mask.width + x] = v
            }
        }
        mask.setHasContent(true)
    }

    /**
     * Fill the mask from a hue band. [targetDeg] is the centre, [tolerance] the
     * half-width in degrees, [satFloor] ignores near-grey pixels.
     */
    fun fillColourRange(
        mask: MaskData,
        src: android.graphics.Bitmap,
        targetDeg: Float,
        tolerance: Float = 30f,
        satFloor: Float = 0.12f,
    ) {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val tol = tolerance.coerceIn(2f, 180f)
        for (y in 0 until mask.height) {
            val sy = (y.toFloat() / mask.height * h).toInt().coerceIn(0, h - 1)
            for (x in 0 until mask.width) {
                val sx = (x.toFloat() / mask.width * w).toInt().coerceIn(0, w - 1)
                val p = px[sy * w + sx]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f

                val mx = maxOf(r, maxOf(g, b))
                val mn = minOf(r, minOf(g, b))
                val delta = mx - mn
                if (mx <= 0.001f || delta / mx < satFloor) {
                    mask.values[y * mask.width + x] = 0f
                    continue
                }
                var hue = when {
                    mx == r -> 60f * (((g - b) / delta) % 6f)
                    mx == g -> 60f * (((b - r) / delta) + 2f)
                    else -> 60f * (((r - g) / delta) + 4f)
                }
                if (hue < 0f) hue += 360f

                var d = kotlin.math.abs(hue - targetDeg) % 360f
                if (d > 180f) d = 360f - d
                val v = if (d >= tol) 0f else 0.5f * (1f + kotlin.math.cos(Math.PI.toFloat() * d / tol))
                mask.values[y * mask.width + x] = v
            }
        }
        mask.setHasContent(true)
    }

    /** Angle and length of the linear handle, for drawing it in the UI. */
    fun linearHandle(g: MaskGeometry): Triple<Float, Float, Float> {
        val dx = g.endX - g.startX
        val dy = g.endY - g.startY
        return Triple(atan2(dy, dx), hypot(dx, dy), g.feather)
    }
}

/** Outline drawing helpers shared by the mask editor overlay. */
object MaskDrawing {
    /** Sample the mask at a normalised coordinate (0..1). */
    fun sampleAt(mask: MaskData, x: Float, y: Float): Float = mask.sample(x, y)

    /** Rough coverage percentage, shown as a readout while editing. */
    fun coverage(mask: MaskData): Int {
        if (!mask.hasContent()) return 0
        var sum = 0f
        for (v in mask.values) sum += v
        return ((sum / mask.values.size) * 100f).toInt().coerceIn(0, 100)
    }

    /** Distance helper used by the brush to size the dab. */
    fun normalizedRadius(px: Float, mask: MaskData): Float = px / min(mask.width, mask.height)

    fun cosAt(deg: Float): Float = cos(Math.toRadians(deg.toDouble()).toFloat())
    fun sinAt(deg: Float): Float = sin(Math.toRadians(deg.toDouble()).toFloat())
}
