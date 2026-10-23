/**
 * Flow — brush mask: apply an effect only where the user paints.
 *
 * The mask is a low-resolution float grid (192×144) sampled bilinearly at full
 * resolution, so painting stays cheap and the render pass stays predictable.
 */

package com.zyon.flow.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.floor

class MaskData(
    val width: Int = 192,
    val height: Int = 144,
) {
    val values = FloatArray(width * height)

    var isEmpty: Boolean = true
        private set

    /** Used by MaskStore when restoring a mask from disk. */
    fun setHasContent(has: Boolean) {
        isEmpty = !has
    }

    fun clear() {
        java.util.Arrays.fill(values, 0f)
        isEmpty = true
    }

    fun invert() {
        for (i in values.indices) values[i] = 1f - values[i]
        isEmpty = values.all { it <= 0.001f }
    }

    fun hasContent(): Boolean = !isEmpty

    /** Paint a soft round dab. Coordinates are normalised 0..1. */
    fun paint(cx: Float, cy: Float, radius: Float, hardness: Float = 0.55f, erase: Boolean = false) {
        val px = cx * width
        val py = cy * height
        val r = (radius * minOf(width, height)).coerceAtLeast(1.5f)
        val y0 = floor(py - r).toInt().coerceAtLeast(0)
        val y1 = floor(py + r).toInt().coerceAtMost(height - 1)
        val x0 = floor(px - r).toInt().coerceAtLeast(0)
        val x1 = floor(px + r).toInt().coerceAtMost(width - 1)
        val hardEdge = r * hardness.coerceIn(0.05f, 0.99f)

        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = x + 0.5f - px
                val dy = y + 0.5f - py
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d > r) continue
                val falloff = if (d <= hardEdge) {
                    1f
                } else {
                    val t = (d - hardEdge) / (r - hardEdge).coerceAtLeast(0.001f)
                    (1f - t).coerceIn(0f, 1f)
                }
                val i = y * width + x
                values[i] = if (erase) {
                    (values[i] - falloff).coerceIn(0f, 1f)
                } else {
                    maxOf(values[i], falloff)
                }
            }
        }
        isEmpty = values.all { it <= 0.001f }
    }

    /** Connect two dabs so fast drags do not leave gaps. */
    fun stroke(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        erase: Boolean = false,
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val steps = (dist / (radius * 0.4f)).toInt().coerceIn(1, 64)
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            paint(x0 + dx * t, y0 + dy * t, radius, erase = erase)
        }
    }

    /** Bilinear sample at normalised coordinates. */
    fun sample(nx: Float, ny: Float): Float {
        val fx = (nx.coerceIn(0f, 0.9999f) * width) - 0.5f
        val fy = (ny.coerceIn(0f, 0.9999f) * height) - 0.5f
        val x0 = floor(fx).toInt().coerceIn(0, width - 1)
        val y0 = floor(fy).toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val tx = (fx - x0).coerceIn(0f, 1f)
        val ty = (fy - y0).coerceIn(0f, 1f)
        val a = values[y0 * width + x0]
        val b = values[y0 * width + x1]
        val c = values[y1 * width + x0]
        val d = values[y1 * width + x1]
        val top = a + (b - a) * tx
        val bot = c + (d - c) * tx
        return top + (bot - top) * ty
    }

    /** Translucent overlay so the user can see what is painted. */
    fun overlay(tint: Int = 0xFF7BB0FF.toInt()): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val px = IntArray(width * height)
        val r = (tint shr 16) and 0xFF
        val g = (tint shr 8) and 0xFF
        val b = tint and 0xFF
        for (i in px.indices) {
            val a = (values[i] * 150f).toInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, width, 0, 0, width, height)
        return bmp
    }

    fun copyInto(other: MaskData) {
        val n = minOf(values.size, other.values.size)
        for (i in 0 until n) other.values[i] = values[i]
        other.isEmpty = isEmpty
    }
}

/**
 * Per-pixel blend between [base] and [target] where the weight is
 * `amount * mask` — this is what makes an effect land only where painted.
 */
fun blendWithMask(
    base: Bitmap,
    target: Bitmap,
    mask: MaskData?,
    amount: Float,
): Bitmap {
    if (base.width != target.width || base.height != target.height) return target
    if (mask == null) {
        if (amount >= 0.999f) return target
    }
    val w = base.width
    val h = base.height
    val a = IntArray(w * h)
    val b = IntArray(w * h)
    base.getPixels(a, 0, w, 0, 0, w, h)
    target.getPixels(b, 0, w, 0, 0, w, h)
    val out = IntArray(w * h)

    if (mask == null) {
        for (i in out.indices) {
            val pa = a[i]
            val pb = b[i]
            val ar = (pa shr 16) and 0xFF
            val ag = (pa shr 8) and 0xFF
            val ab = pa and 0xFF
            val br = (pb shr 16) and 0xFF
            val bg = (pb shr 8) and 0xFF
            val bb = pb and 0xFF
            val r = (ar + (br - ar) * amount).toInt().coerceIn(0, 255)
            val g = (ag + (bg - ag) * amount).toInt().coerceIn(0, 255)
            val bl = (ab + (bb - ab) * amount).toInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
    } else {
        for (y in 0 until h) {
            val ny = y.toFloat() / h
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                val wgt = (mask.sample(x.toFloat() / w, ny) * amount).coerceIn(0f, 1f)
                if (wgt <= 0.002f) {
                    out[i] = a[i]
                    continue
                }
                val pa = a[i]
                val pb = b[i]
                val ar = (pa shr 16) and 0xFF
                val ag = (pa shr 8) and 0xFF
                val ab = pa and 0xFF
                val br = (pb shr 16) and 0xFF
                val bg = (pb shr 8) and 0xFF
                val bb = pb and 0xFF
                val r = (ar + (br - ar) * wgt).toInt().coerceIn(0, 255)
                val g = (ag + (bg - ag) * wgt).toInt().coerceIn(0, 255)
                val bl = (ab + (bb - ab) * wgt).toInt().coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
    }

    val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    res.setPixels(out, 0, w, 0, 0, w, h)
    return res
}

/** Render the mask to a bitmap the user can see while painting. */
fun maskOverlayBitmap(mask: MaskData): Bitmap = mask.overlay()

/** Keep the mask out of R8's reach in debug builds. */
internal val MaskNoop = Paint().apply { color = Color.TRANSPARENT }
