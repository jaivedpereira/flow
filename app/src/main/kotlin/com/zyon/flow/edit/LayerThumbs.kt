/**
 * Flow — layer thumbnails.
 *
 * A layer card shows a small picture of what that layer holds: the photo for the
 * base, the actual caption over a transparency checkerboard for text, the glyph
 * for a sticker, a colour swatch for a grade and a tinted card for an effect.
 */

package com.zyon.flow.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object LayerThumbs {

    /** Checkerboard behind anything with transparency. */
    fun checker(size: Int, cell: Int = 8): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.color = Color.rgb(40, 40, 44)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.color = Color.rgb(28, 28, 32)
        var y = 0
        var row = 0
        while (y < size) {
            var x = 0
            var col = 0
            while (x < size) {
                if ((row + col) % 2 == 0) {
                    canvas.drawRect(
                        x.toFloat(), y.toFloat(),
                        (x + cell).toFloat(), (y + cell).toFloat(), paint,
                    )
                }
                x += cell
                col++
            }
            y += cell
            row++
        }
        return bmp
    }

    fun render(layer: PhotoLayer, photo: Bitmap?, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        when (layer.content) {
            LayerContent.EMPTY -> {
                canvas.drawColor(Color.rgb(34, 34, 38))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(150, 235, 231, 220)
                    textSize = size * 0.34f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("+", size / 2f, size * 0.64f, paint)
            }

            LayerContent.TEXT -> {
                canvas.drawBitmap(checker(size), 0f, 0f, null)
                val t = layer.text
                if (t != null) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = t.color
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val label = t.text.ifBlank { "Text" }
                    val maxW = size * 0.84f
                    var ts = size * 0.40f
                    paint.textSize = ts
                    while (ts > 6f && paint.measureText(label) > maxW) {
                        ts -= 1.5f
                        paint.textSize = ts
                    }
                    val m = paint.fontMetrics
                    canvas.drawText(
                        label, size / 2f,
                        size / 2f - (m.ascent + m.descent) / 2f, paint,
                    )
                }
            }

            LayerContent.STICKER -> {
                canvas.drawBitmap(checker(size), 0f, 0f, null)
                val s = layer.sticker
                if (s != null) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = size * 0.55f
                        textAlign = Paint.Align.CENTER
                        color = Color.WHITE
                    }
                    canvas.drawText(s.emoji, size / 2f, size * 0.72f, paint)
                }
            }

            LayerContent.CUBE -> {
                canvas.drawColor(Color.rgb(108, 56, 48))
                label(canvas, size, "3D", Color.argb(225, 245, 240, 232))
            }

            LayerContent.COLOUR -> {
                // a little gradient so it reads as a grade rather than a swatch
                val paint = Paint()
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(Color.rgb(70, 46, 40), Color.rgb(52, 64, 88), Color.rgb(40, 74, 62)),
                    null, android.graphics.Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
                label(canvas, size, "◐", Color.argb(220, 245, 240, 232))
            }

            LayerContent.EFFECT -> {
                canvas.drawColor(Color.rgb(84, 56, 106))
                label(canvas, size, "FX", Color.argb(225, 245, 240, 232))
            }
        }

        return out
    }

    /** The photo itself, used for the base row. */
    fun photo(photo: Bitmap?, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (photo == null) {
            canvas.drawColor(Color.rgb(26, 26, 30))
            return out
        }
        val scale = maxOf(size / photo.width.toFloat(), size / photo.height.toFloat())
        val w = photo.width * scale
        val h = photo.height * scale
        canvas.save()
        canvas.translate((size - w) / 2f, (size - h) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        return out
    }

    private fun label(canvas: Canvas, size: Int, text: String, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size * 0.34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val m = paint.fontMetrics
        canvas.drawText(text, size / 2f, size / 2f - (m.ascent + m.descent) / 2f, paint)
    }
}
