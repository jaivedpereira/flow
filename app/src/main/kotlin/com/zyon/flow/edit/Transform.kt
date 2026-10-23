/**
 * Flow — geometry and full edit state.
 */

package com.zyon.flow.edit

import android.graphics.Bitmap
import android.graphics.Matrix

/** Non-destructive geometry: rotation quarter-turns, mirror, straighten angle, crop aspect. */
data class Transform(
    val rotation: Int = 0,        // 0, 90, 180, 270
    val flipH: Boolean = false,
    val straighten: Float = 0f,   // -15..15 degrees
    val aspect: Float? = null,    // width/height ratio, null = original
)

/** Everything that defines the current look. */
data class EditState(
    val presetId: String = "original",
    /** VSCO cube key from LutPack; when set it replaces the built-in preset. */
    val lutKey: String? = null,
    /** 0..1 blend of the cube against the untouched photo. */
    val lutIntensity: Float = 1f,
    /** Artistic effect id from Effects.All. */
    val effectId: String = "none",
    /** 0..1 blend of the effect. */
    val effectIntensity: Float = 1f,
    /** Effect-specific knob, 0..1. */
    val effectParam: Float = 0.5f,
    /** Tone curves (master + per channel). */
    val curves: Curves = Curves(),
    /** Selective hue / saturation / luminance per colour band. */
    val hsl: Hsl = Hsl(),
    /** Frame drawn over the photo. */
    val border: Border = Border(),
    /** When true the effect only lands where the brush mask is painted. */
    val useMask: Boolean = false,
    /** How the mask was produced: brush, linear gradient or radial spotlight. */
    val maskShape: MaskShape = MaskShape.BRUSH,
    /** Control points for the gradient shapes. */
    val maskGeometry: MaskGeometry = MaskGeometry(),
    /** When true, a new gradient adds to the painted mask instead of replacing it. */
    val maskAdditive: Boolean = false,
    /** Text captions and stickers drawn above the photo. */
    val overlays: Overlays = Overlays(),
    /** A .cube the user imported this session; takes precedence over lutKey. */
    val customLut: Lut? = null,
    /** Display name of the imported cube. */
    val customLutName: String? = null,
    val adjustments: Adjustments = Adjustments(),
    val transform: Transform = Transform(),
    val splitShadow: Float = 0f,   // -1..1 teal/orange wash in shadows
    val splitHighlight: Float = 0f, // -1..1 warm/cool wash in highlights
) {
    val preset: Preset get() = presetById(presetId)
}

enum class CropRatio(val label: String, val ratio: Float?) {
    ORIGINAL("ORIG", null),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    STORY("9:16", 9f / 16f),
    LANDSCAPE("16:9", 16f / 9f),
    CLASSIC("3:2", 3f / 2f),
}

object Geometry {

    /** Apply rotation, mirror, straighten and crop in one pass. */
    fun apply(src: Bitmap, t: Transform): Bitmap {
        var out = src

        if (t.rotation % 360 != 0 || t.flipH || t.straighten != 0f) {
            val m = Matrix()
            if (t.flipH) m.postScale(-1f, 1f)
            m.postRotate(t.rotation.toFloat() + t.straighten)
            val rotated = Bitmap.createBitmap(out, 0, 0, out.width, out.height, m, true)
            // straighten leaves transparent corners; crop them away
            out = if (t.straighten != 0f) trimRotatedCorners(rotated, t.straighten) else rotated
        }

        val ratio = t.aspect
        if (ratio != null && ratio > 0f) {
            out = cropToAspect(out, ratio)
        }
        return out
    }

    /** Crop the transparent border produced by a small rotation. */
    private fun trimRotatedCorners(src: Bitmap, degrees: Float): Bitmap {
        val rad = Math.toRadians(degrees.toDouble()).toFloat()
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val cos = kotlin.math.abs(kotlin.math.cos(rad))
        val sin = kotlin.math.abs(kotlin.math.sin(rad))
        // largest axis-aligned rect fully inside the rotated original
        val scale = (w * cos - h * sin).let { a ->
            val b = (h * cos - w * sin)
            if (a > 0 && b > 0) kotlin.math.min(1f, kotlin.math.max(a / w, b / h).coerceAtMost(1f)) else 1f
        }
        val cw = (w * scale).toInt().coerceAtLeast(16)
        val ch = (h * scale).toInt().coerceAtLeast(16)
        val x = ((w - cw) / 2f).toInt().coerceAtLeast(0)
        val y = ((h - ch) / 2f).toInt().coerceAtLeast(0)
        return Bitmap.createBitmap(src, x, y, cw, ch)
    }

    fun cropToAspect(src: Bitmap, ratio: Float): Bitmap {
        val w = src.width
        val h = src.height
        var cw = w
        var ch = h
        if (w.toFloat() / h > ratio) {
            cw = (h * ratio).toInt()
        } else {
            ch = (w / ratio).toInt()
        }
        val x = ((w - cw) / 2).coerceAtLeast(0)
        val y = ((h - ch) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(src, x, y, cw, ch)
    }
}
