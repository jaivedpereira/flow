/**
 * Flow — 3D LUT engine.
 *
 * A LUT is a size^3 cube of RGB triples. Files (.cube / .xray text) list them as
 * "r g b" floats, red varying fastest. We sample with trilinear interpolation so
 * a 17^3 cube reads smooth at full resolution.
 */

package com.zyon.flow.edit

import android.graphics.Bitmap
import java.io.InputStream

class Lut(
    val size: Int,
    /** size^3 * 3 floats, ordered r fastest, then g, then b. */
    val data: FloatArray,
) {
    init {
        require(data.size >= size * size * size * 3) { "LUT data too small" }
    }

    /**
     * Trilinear sample. Inputs are 0..1; output is 0..1 per channel.
     */
    fun sample(r: Float, g: Float, b: Float): FloatArray {
        val maxIdx = size - 1
        val fr = (r.coerceIn(0f, 1f) * maxIdx)
        val fg = (g.coerceIn(0f, 1f) * maxIdx)
        val fb = (b.coerceIn(0f, 1f) * maxIdx)

        val r0 = fr.toInt().coerceAtMost(maxIdx)
        val g0 = fg.toInt().coerceAtMost(maxIdx)
        val b0 = fb.toInt().coerceAtMost(maxIdx)
        val r1 = (r0 + 1).coerceAtMost(maxIdx)
        val g1 = (g0 + 1).coerceAtMost(maxIdx)
        val b1 = (b0 + 1).coerceAtMost(maxIdx)

        val dr = fr - r0
        val dg = fg - g0
        val db = fb - b0

        fun at(ri: Int, gi: Int, bi: Int, c: Int): Float {
            val idx = ((bi * size + gi) * size + ri) * 3 + c
            return data[idx]
        }

        val out = FloatArray(3)
        for (c in 0..2) {
            val c000 = at(r0, g0, b0, c)
            val c100 = at(r1, g0, b0, c)
            val c010 = at(r0, g1, b0, c)
            val c110 = at(r1, g1, b0, c)
            val c001 = at(r0, g0, b1, c)
            val c101 = at(r1, g0, b1, c)
            val c011 = at(r0, g1, b1, c)
            val c111 = at(r1, g1, b1, c)

            val c00 = c000 + (c100 - c000) * dr
            val c10 = c010 + (c110 - c010) * dr
            val c01 = c001 + (c101 - c001) * dr
            val c11 = c011 + (c111 - c011) * dr

            val c0 = c00 + (c10 - c00) * dg
            val c1 = c01 + (c11 - c01) * dg

            out[c] = c0 + (c1 - c0) * db
        }
        return out
    }

    companion object {
        /** Parse the VSCO-style text cube: one "r g b" triple per line, 0..1. */
        fun fromText(stream: InputStream): Lut? {
            val values = ArrayList<Float>(17 * 17 * 17 * 3)
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith("#")) return@forEach
                    val parts = t.split(' ', '\t').filter { it.isNotEmpty() }
                    if (parts.size < 3) return@forEach
                    for (i in 0..2) {
                        parts[i].toFloatOrNull()?.let { values.add(it) } ?: return null
                    }
                }
            }
            val n = values.size / 3
            val size = Math.round(Math.cbrt(n.toDouble())).toInt()
            if (size < 2 || size * size * size != n) return null
            return Lut(size, values.toFloatArray())
        }

        /** Apply the LUT to a whole bitmap (CPU, trilinear). */
        fun apply(src: Bitmap, lut: Lut, strength: Float = 1f): Bitmap {
            if (strength <= 0.001f) return src
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)

            val s = strength.coerceIn(0f, 1f)
            for (i in px.indices) {
                val v = px[i]
                // Android bitmaps are ARGB ints: red lives in bits 16..23.
                val r = ((v shr 16) and 0xFF) / 255f
                val g = ((v shr 8) and 0xFF) / 255f
                val b = (v and 0xFF) / 255f
                val o = lut.sample(r, g, b)
                val nr = (r + (o[0].coerceIn(0f, 1f) - r) * s)
                val ng = (g + (o[1].coerceIn(0f, 1f) - g) * s)
                val nb = (b + (o[2].coerceIn(0f, 1f) - b) * s)
                px[i] = (v and 0xFF000000.toInt()) or
                    ((nr * 255f).toInt().coerceIn(0, 255) shl 16) or
                    ((ng * 255f).toInt().coerceIn(0, 255) shl 8) or
                    (nb * 255f).toInt().coerceIn(0, 255)
            }

            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(px, 0, w, 0, 0, w, h)
            return out
        }
    }
}
