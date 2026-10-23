/**
 * Flow — filter engine.
 *
 * A preset is a small set of photographic parameters; [buildMatrix] folds them
 * into one ColorMatrix. Vignette, grain, sharpen and clarity are applied on the
 * bitmap afterwards because they are spatial, not per-pixel colour operations.
 */

package com.zyon.flow.edit

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color

data class Preset(
    val id: String,
    val name: String,
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val brightness: Float = 1f,
    val warmth: Float = 0f,
    val fade: Float = 0f,
    val tintR: Float = 0f,
    val tintG: Float = 0f,
    val tintB: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
)

data class Adjustments(
    val exposure: Float = 0f,     // -1..1
    val contrast: Float = 0f,     // -1..1
    val saturation: Float = 0f,   // -1..1
    val warmth: Float = 0f,       // -1..1
    val fade: Float = 0f,         // 0..1
    val vignette: Float = 0f,     // 0..1
    val grain: Float = 0f,        // 0..1
    val sharpen: Float = 0f,      // 0..1
    val clarity: Float = 0f,      // 0..1
    val highlights: Float = 0f,   // -1..1
    val shadows: Float = 0f,      // -1..1
    val vibrance: Float = 0f,     // -1..1
    val hueShift: Float = 0f,     // -1..1  (one full turn)
)

val Presets: List<Preset> = listOf(
    Preset("original", "Original"),
    Preset("fade", "Fade", saturation = 0.82f, contrast = 0.88f, brightness = 1.04f, fade = 0.16f),
    Preset("noir", "Noir", saturation = 0f, contrast = 1.22f, brightness = 0.98f, vignette = 0.28f, grain = 0.18f),
    Preset("film", "Film", saturation = 0.9f, contrast = 1.06f, brightness = 1.02f, warmth = 0.08f, fade = 0.1f, grain = 0.16f),
    Preset("warm", "Warm", saturation = 1.05f, contrast = 1.04f, warmth = 0.22f),
    Preset("cool", "Cool", saturation = 0.98f, contrast = 1.05f, warmth = -0.22f),
    Preset("vintage", "Vintage", saturation = 0.72f, contrast = 0.94f, brightness = 1.03f, warmth = 0.14f, fade = 0.24f, grain = 0.2f),
    Preset("mono", "Mono", saturation = 0f, contrast = 1.1f, brightness = 1.02f),
    Preset("sepia", "Sepia", saturation = 0.35f, contrast = 0.98f, warmth = 0.3f, tintR = 0.06f, tintB = -0.04f),
    Preset("chrome", "Chrome", saturation = 1.15f, contrast = 1.18f, brightness = 1.0f),
    Preset("matte", "Matte", saturation = 0.86f, contrast = 0.9f, brightness = 1.05f, fade = 0.3f),
    Preset("punch", "Punch", saturation = 1.35f, contrast = 1.22f, brightness = 0.98f),
    Preset("soft", "Soft", saturation = 0.9f, contrast = 0.92f, brightness = 1.06f, fade = 0.12f),
    Preset("retro", "Retro", saturation = 0.8f, contrast = 1.02f, warmth = 0.18f, fade = 0.18f, tintG = 0.02f),
    Preset("dusk", "Dusk", saturation = 0.95f, contrast = 1.08f, brightness = 0.97f, warmth = -0.1f, tintB = 0.05f),
    Preset("dawn", "Dawn", saturation = 1.0f, contrast = 1.03f, brightness = 1.04f, warmth = 0.12f, tintR = 0.04f),
    Preset("clean", "Clean", saturation = 1.02f, contrast = 1.02f, brightness = 1.02f),
    Preset("bold", "Bold", saturation = 1.2f, contrast = 1.3f, brightness = 0.96f, vignette = 0.18f),
)

fun presetById(id: String?): Preset = Presets.firstOrNull { it.id == id } ?: Presets.first()

/**
 * Fold preset + manual adjustments + split tone into one 4x5 colour matrix.
 * Split tone is approximated with channel shifts, which reads correctly on photos
 * without costing a per-pixel luminance pass.
 */
fun buildMatrix(
    preset: Preset,
    adj: Adjustments,
    splitShadow: Float = 0f,
    splitHighlight: Float = 0f,
): android.graphics.ColorMatrix {
    val m = android.graphics.ColorMatrix()

    val brightness = (preset.brightness * (1f + adj.exposure * 0.55f)).coerceIn(0.2f, 2.4f)
    val contrast = (preset.contrast * (1f + adj.contrast * 0.6f)).coerceIn(0.3f, 2.4f)
    val saturation = (preset.saturation * (1f + adj.saturation * 0.75f)).coerceIn(0f, 2.4f)
    val fade = (preset.fade + adj.fade * 0.55f).coerceIn(0f, 0.6f)
    val warmth = (preset.warmth + adj.warmth * 0.6f).coerceIn(-0.6f, 0.6f)

    // brightness
    m.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                brightness, 0f, 0f, 0f, 0f,
                0f, brightness, 0f, 0f, 0f,
                0f, 0f, brightness, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    // contrast around mid-grey, then fade lifts the floor toward a matte look
    val c = contrast
    val t = (1f - c) * 127.5f + fade * 45f
    m.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    // saturation
    val sm = android.graphics.ColorMatrix()
    sm.setSaturation(saturation)
    m.postConcat(sm)

    // warmth + tint
    val rShift = warmth * 34f + preset.tintR * 255f
    val gShift = preset.tintG * 255f
    val bShift = -warmth * 34f + preset.tintB * 255f
    m.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, rShift,
                0f, 1f, 0f, 0f, gShift,
                0f, 0f, 1f, 0f, bShift,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    // split tone: shadow = teal(+, cool) / orange(-); highlight = warm(+) / cool(-)
    if (splitShadow != 0f || splitHighlight != 0f) {
        val sh = splitShadow * 16f
        val hi = splitHighlight * 16f
        m.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, hi - sh,
                    0f, 1f, 0f, 0f, (hi + sh) * 0.15f,
                    0f, 0f, 1f, 0f, sh - hi,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    // desaturate slightly as fade grows, for the washed matte look
    if (fade > 0f) {
        val fm = android.graphics.ColorMatrix()
        fm.setSaturation(1f - fade * 0.35f)
        m.postConcat(fm)
    }

    return m
}

fun effectiveVignette(preset: Preset, adj: Adjustments): Float =
    (preset.vignette + adj.vignette * 0.8f).coerceIn(0f, 1f)

fun effectiveGrain(preset: Preset, adj: Adjustments): Float =
    (preset.grain + adj.grain * 0.8f).coerceIn(0f, 1f)

/** Swatch colour for the preset tile. */
fun swatchColor(preset: Preset): Color {
    val base = 0.62f
    val r = (base + preset.warmth * 0.25f + preset.tintR).coerceIn(0f, 1f)
    val g = (base + preset.tintG).coerceIn(0f, 1f)
    val b = (base - preset.warmth * 0.25f + preset.tintB).coerceIn(0f, 1f)
    return if (preset.saturation < 0.2f) {
        Color(AndroidColor.rgb(150, 148, 144))
    } else {
        Color(AndroidColor.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt()))
    }
}
