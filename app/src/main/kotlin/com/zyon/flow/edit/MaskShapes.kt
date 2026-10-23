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
