/**
 * Flow — tone curves and borders.
 *
 * Curves use monotone cubic interpolation (Fritsch–Carlson) so the curve never
 * overshoots between control points, which is what keeps skin tones from
 * banding. Each channel gets its own 256-entry lookup table.
 */

package com.zyon.flow.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

data class CurvePoint(val x: Float, val y: Float)

data class Curve(
    val points: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
) {
    /** 256-entry LUT built with monotone cubic interpolation. */
    fun lut(): IntArray {
        // Drop points that sit on top of each other: a zero-width segment makes
        // the slope undefined and used to produce a divide-by-huge value.
        val raw = points.sortedBy { it.x }
        val pts = ArrayList<CurvePoint>(raw.size)
        for (p in raw) {
            val q = CurvePoint(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))
            if (pts.isEmpty() || q.x - pts.last().x >= 1e-4f) pts.add(q)
        }
        val out = IntArray(256)
        if (pts.size < 2) {
            for (i in 0..255) out[i] = i
            return out
        }
        val n = pts.size
        val xs = FloatArray(n) { pts[it].x.coerceIn(0f, 1f) }
        val ys = FloatArray(n) { pts[it].y.coerceIn(0f, 1f) }

        // slopes
        val d = FloatArray(n - 1)
        for (i in 0 until n - 1) {
            val dx = (xs[i + 1] - xs[i]).coerceAtLeast(1e-5f)
            d[i] = (ys[i + 1] - ys[i]) / dx
        }
        val m = FloatArray(n)
        m[0] = d[0]
        m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) {
            if (d[i - 1] * d[i] <= 0f) {
                m[i] = 0f          // local extremum: flatten to avoid overshoot
            } else {
                m[i] = (d[i - 1] + d[i]) / 2f
            }
        }
        // Fritsch–Carlson limiter
        for (i in 0 until n - 1) {
            if (d[i] == 0f) {
                m[i] = 0f
                m[i + 1] = 0f
                continue
            }
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / kotlin.math.sqrt(s)
                m[i] = t * a * d[i]
                m[i + 1] = t * b * d[i]
            }
        }

        for (i in 0..255) {
            val x = i / 255f
            var seg = 0
            while (seg < n - 2 && x > xs[seg + 1]) seg++
            val h = (xs[seg + 1] - xs[seg]).coerceAtLeast(1e-5f)
            val t = ((x - xs[seg]) / h).coerceIn(0f, 1f)
            val t2 = t * t
            val t3 = t2 * t
            val h00 = 2 * t3 - 3 * t2 + 1
            val h10 = t3 - 2 * t2 + t
            val h01 = -2 * t3 + 3 * t2
            val h11 = t3 - t2
            val y = h00 * ys[seg] + h10 * h * m[seg] + h01 * ys[seg + 1] + h11 * h * m[seg + 1]
            out[i] = kotlin.math.round(y.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        }
        return out
    }

    val isIdentity: Boolean
        get() = points.size == 2 &&
            points[0].x == 0f && points[0].y == 0f &&
            points[1].x == 1f && points[1].y == 1f
}

data class Curves(
    val master: Curve = Curve(),
    val red: Curve = Curve(),
    val green: Curve = Curve(),
    val blue: Curve = Curve(),
) {
    val isIdentity: Boolean
        get() = master.isIdentity && red.isIdentity && green.isIdentity && blue.isIdentity

    fun apply(src: Bitmap): Bitmap {
        if (isIdentity) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val mLut = master.lut()
        val rLut = red.lut()
        val gLut = green.lut()
        val bLut = blue.lut()
        val out = IntArray(w * h)

        for (i in px.indices) {
            val p = px[i]
            var r = mLut[(p shr 16) and 0xFF]
            var g = mLut[(p shr 8) and 0xFF]
            var b = mLut[p and 0xFF]
            r = rLut[r]
            g = gLut[g]
            b = bLut[b]
            out[i] = (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }

        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }
}

/** Frame drawn over the photo. Width is a fraction of the shorter edge. */
data class Border(
    val width: Float = 0f,        // 0..0.10
    val color: Int = 0xFFFFFFFF.toInt(),
    val rounded: Float = 0f,      // corner radius as fraction of short edge
) {
    val isNone: Boolean get() = width <= 0.001f

    fun apply(src: Bitmap): Bitmap {
        if (isNone) return src
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(src, 0f, 0f, null)

        val short = minOf(w, h).toFloat()
        val t = (width.coerceIn(0f, 0.10f) * short)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = t * 2f   // half sits outside the edge, so the visible band is t
            this.color = this@Border.color
        }
        if (rounded > 0.001f) {
            val r = rounded.coerceIn(0f, 0.4f) * short
            canvas.drawRoundRect(
                RectF(t * 0.5f, t * 0.5f, w - t * 0.5f, h - t * 0.5f),
                r, r, paint,
            )
        } else {
            canvas.drawRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        }
        return out
    }

    companion object {
        val Palette = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFF000000.toInt(),
            0xFFEDE7DC.toInt(),
            0xFFE8C39E.toInt(),
            0xFFB5523B.toInt(),
            0xFF3B5B7A.toInt(),
            0xFF4E6151.toInt(),
            0xFF8E6C88.toInt(),
        )
    }
}
