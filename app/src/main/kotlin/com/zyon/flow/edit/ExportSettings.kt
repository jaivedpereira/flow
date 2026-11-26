/**
 * Flow — export settings.
 *
 * Until now the app wrote a single fixed JPEG at 95. Format, quality and output
 * size are the three things people actually reach for after an edit, so they get a
 * real screen instead of a guess.
 */

package com.zyon.flow.edit

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp"),
    ;

    val compressFormat: android.graphics.Bitmap.CompressFormat
        get() = when (this) {
            JPEG -> android.graphics.Bitmap.CompressFormat.JPEG
            PNG -> android.graphics.Bitmap.CompressFormat.PNG
            WEBP -> android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
        }

    /** PNG is lossless, so the quality slider is meaningless for it. */
    val usesQuality: Boolean get() = this != PNG
}

enum class ExportSize(val label: String, val maxDim: Int?) {
    ORIGINAL("Original", null),
    PX_3000("3000 px", 3000),
    PX_2048("2048 px", 2048),
    PX_1080("1080 px", 1080),
}

data class ExportSettings(
    val format: ExportFormat = ExportFormat.JPEG,
    /** 70..100. Ignored for PNG. */
    val quality: Int = 95,
    val size: ExportSize = ExportSize.ORIGINAL,
) {
    /**
     * Scale a bitmap down to the requested long edge. Returns the input when no
     * scaling is needed, so the caller can compare identity before recycling.
     */
    fun fit(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val limit = size.maxDim ?: return src
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= limit) return src
        val scale = limit.toFloat() / longEdge
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
    }

    /** Rough estimate shown under the button, so the choice has a cost attached. */
    fun estimatedMb(width: Int, height: Int): Float {
        val px = width.toLong() * height.toLong()
        return when (format) {
            ExportFormat.PNG -> (px * 3.1f) / 1_000_000f
            ExportFormat.WEBP -> (px * (quality / 100f) * 0.55f) / 1_000_000f
            ExportFormat.JPEG -> (px * (quality / 100f) * 0.85f) / 1_000_000f
        }
    }
}
