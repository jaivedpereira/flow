/**
 * Flow — spot removal.
 *
 * A spot is a soft circular patch copied from a nearby area. Deliberately not an
 * AI inpainting model: the honest version of this tool is a clone stamp, and it
 * fixes dust, blemishes and stray hairs without pretending to understand the
 * scene.
 *
 * Coordinates are normalised so a patch survives any export resolution.
 */

package com.zyon.flow.edit

import android.graphics.Bitmap
import kotlin.math.sqrt

data class Spot(
    val id: Long,
    /** Centre of the area to repair, 0..1 of width/height. */
    val x: Float,
    val y: Float,
    /** Radius as a fraction of the shorter edge. */
    val radius: Float = 0.045f,
    /** Where the replacement pixels come from, relative to the centre. */
    val offsetX: Float = 0f,
    val offsetY: Float = -0.10f,
)

object SpotRemoval {

    private var counter = 1L

    fun newSpot(x: Float, y: Float, radius: Float): Spot =
        Spot(id = System.nanoTime() + counter++, x = x, y = y, radius = radius)

    /**
     * Copy each patch in place, with a cosine falloff at the rim so there is no
     * visible circle. Samples are read from [src] and written to a copy, so
     * overlapping spots do not feed on each other.
     */
    fun apply(src: Bitmap, spots: List<Spot>): Bitmap {
        if (spots.isEmpty()) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()

        val short = minOf(w, h).toFloat()

        for (spot in spots) {
            val cx = spot.x * w
            val cy = spot.y * h
            val r = (spot.radius * short).coerceAtLeast(2f)
            val sx = cx + spot.offsetX * w
            val sy = cy + spot.offsetY * h
            val r2 = r * r

            val x0 = (cx - r).toInt().coerceAtLeast(0)
            val x1 = (cx + r).toInt().coerceAtMost(w - 1)
            val y0 = (cy - r).toInt().coerceAtLeast(0)
            val y1 = (cy + r).toInt().coerceAtMost(h - 1)

            for (y in y0..y1) {
                for (x in x0..x1) {
                    val dx = x - cx
                    val dy = y - cy
                    val d2 = dx * dx + dy * dy
                    if (d2 > r2) continue

                    // cosine falloff: 1 in the middle, 0 at the rim
                    val d = sqrt(d2)
                    val a = kotlin.math.cos((d / r) * (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)

                    val tx = (x + (sx - cx)).toInt().coerceIn(0, w - 1)
                    val ty = (y + (sy - cy)).toInt().coerceIn(0, h - 1)

                    val from = px[ty * w + tx]
                    val to = out[y * w + x]

                    val fr = (from shr 16) and 0xFF
                    val fg = (from shr 8) and 0xFF
                    val fb = from and 0xFF
                    val tr = (to shr 16) and 0xFF
                    val tg = (to shr 8) and 0xFF
                    val tb = to and 0xFF

                    val nr = (tr + (fr - tr) * a).toInt().coerceIn(0, 255)
                    val ng = (tg + (fg - tg) * a).toInt().coerceIn(0, 255)
                    val nb = (tb + (fb - tb) * a).toInt().coerceIn(0, 255)

                    out[y * w + x] = (to and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
                }
            }
        }

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }
}
