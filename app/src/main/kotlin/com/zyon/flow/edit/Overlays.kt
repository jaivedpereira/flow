/**
 * Flow — text and sticker overlays.
 *
 * Overlays are drawn last, after the border, so captions sit on top of the
 * frame. Positions are normalised so a look survives any export resolution.
 */

package com.zyon.flow.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.max

enum class TextStyle(val label: String, val family: Typeface) {
    SERIF("Serif", Typeface.SERIF),
    SANS("Sans", Typeface.SANS_SERIF),
    MONO("Mono", Typeface.MONOSPACE),
}

data class TextLayer(
    val id: Long,
    val text: String = "FLOW",
    val x: Float = 0.5f,          // normalised centre
    val y: Float = 0.85f,
    val size: Float = 0.08f,      // fraction of the shorter edge
    val color: Int = 0xFFFFFFFF.toInt(),
    val style: TextStyle = TextStyle.SANS,
    /** File name inside files/fonts; when set it overrides [style]. */
    val fontFile: String? = null,
    val bold: Boolean = true,
    val letterSpacing: Float = 0.15f,
    val shadow: Boolean = true,
    val rotation: Float = 0f,
    val visible: Boolean = true,
) {
    fun isBlank(): Boolean = text.isBlank() || !visible
}

data class StickerLayer(
    val id: Long,
    val emoji: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.14f,
    val rotation: Float = 0f,
    val visible: Boolean = true,
)

/** Everything drawn above the photo. */
data class Overlays(
    val texts: List<TextLayer> = emptyList(),
    val stickers: List<StickerLayer> = emptyList(),
) {
    val isEmpty: Boolean
        get() = texts.none { !it.isBlank() } && stickers.none { it.visible }

    /**
     * @param customTypeface resolves a stored font file name to a Typeface.
     *   Null keeps the built-in families, which is what batch/exports fall back to.
     */
    fun apply(
        src: Bitmap,
        customTypeface: ((String?, Boolean) -> Typeface?)? = null,
    ): Bitmap {
        if (isEmpty) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(src, 0f, 0f, null)

        val short = minOf(src.width, src.height).toFloat()

        stickers.filter { it.visible }.forEach { s ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = s.size * short
                textAlign = Paint.Align.CENTER
            }
            canvas.save()
            canvas.rotate(s.rotation, s.x * src.width, s.y * src.height)
            // emoji baseline sits below the visual centre; nudge by a third of the size
            canvas.drawText(
                s.emoji,
                s.x * src.width,
                s.y * src.height + paint.textSize * 0.33f,
                paint,
            )
            canvas.restore()
        }

        texts.filter { !it.isBlank() }.forEach { t ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = t.color
                textSize = t.size * short
                textAlign = Paint.Align.CENTER
                typeface = customTypeface?.invoke(t.fontFile, t.bold)
                    ?: Typeface.create(
                        t.style.family,
                        if (t.bold) Typeface.BOLD else Typeface.NORMAL,
                    )
                letterSpacing = t.letterSpacing
                if (t.shadow) {
                    setShadowLayer(max(2f, t.size * short * 0.06f), 0f, t.size * short * 0.02f,
                        Color.argb(150, 0, 0, 0))
                }
            }
            canvas.save()
            canvas.rotate(t.rotation, t.x * src.width, t.y * src.height)
            canvas.drawText(t.text, t.x * src.width, t.y * src.height, paint)
            canvas.restore()
        }

        return out
    }

    fun nextTextId(): Long = (texts.maxOfOrNull { it.id } ?: 0L) + 1
    fun nextStickerId(): Long = (stickers.maxOfOrNull { it.id } ?: 0L) + 1
}

/** Curated stickers: film-era marks that match the app's look. */
val StickerPalette: List<String> = listOf(
    "✕", "◯", "◇", "★", "✦", "▲", "❖", "◆",
    "→", "⟵", "↗", "⇄", "†", "§", "¶", "※",
    "▣", "▤", "◭", "◮", "❉", "❋", "✱", "✲",
    "①②③", "№", "™", "©", "®", "°", "‰", "∞",
)

/** Small helper so the UI can show a readable position. */
fun TextLayer.positionLabel(): String =
    "${(x * 100).toInt()}%, ${(y * 100).toInt()}%"
