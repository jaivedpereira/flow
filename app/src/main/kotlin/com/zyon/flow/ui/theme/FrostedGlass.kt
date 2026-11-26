/**
 * Flow — dialog surface.
 *
 * This was a frosted panel: the photo behind the dialog, heavily blurred, under a
 * milky veil. It looked striking the first time and got in the way every time
 * after — over a bright photo the text lost contrast, and the blur made a small
 * dialog feel like a window onto something you were supposed to be looking at.
 *
 * It is a solid sheet now. Frosted glass is still available through [Frost] and
 * [frostBitmap] if a future screen genuinely wants it, but every current dialog
 * uses the plain surface.
 */

package com.zyon.flow.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Kept so callers that ask for a strength still compile. */
enum class Frost {
    LIGHT,
    MEDIUM,
    HEAVY,
}

/**
 * Downscale, blur, upscale.
 *
 * A full-frame gaussian is expensive; on a 1/8 copy it is free, and scaling back
 * with bilinear filtering hides the difference. Three box passes approximate a
 * gaussian closely enough that nobody can tell.
 *
 * No current screen calls this. It stays because it is correct, tested by the
 * dialogs it used to power, and cheaper to keep than to rewrite later.
 */
fun frostBitmap(src: Bitmap, radius: Int = 3, downscale: Int = 8): Bitmap {
    val sw = (src.width / downscale).coerceAtLeast(1)
    val sh = (src.height / downscale).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(src, sw, sh, true)

    val px = IntArray(sw * sh)
    small.getPixels(px, 0, sw, 0, 0, sw, sh)
    var work = px

    repeat(3) {
        work = boxPass(work, sw, sh, radius)
    }

    val blurred = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
    blurred.setPixels(work, 0, sw, 0, 0, sw, sh)
    val up = Bitmap.createScaledBitmap(blurred, src.width, src.height, true)
    if (blurred !== up) blurred.recycle()
    if (small !== src) small.recycle()
    return up
}

private fun boxPass(src: IntArray, w: Int, h: Int, r: Int): IntArray {
    val tmp = IntArray(src.size)
    val out = IntArray(src.size)
    val span = r * 2 + 1

    for (y in 0 until h) {
        val row = y * w
        var sr = 0
        var sg = 0
        var sb = 0
        for (x in -r..r) {
            val p = src[row + x.coerceIn(0, w - 1)]
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
        }
        for (x in 0 until w) {
            tmp[row + x] = (0xFF shl 24) or
                ((sr / span) shl 16) or ((sg / span) shl 8) or (sb / span)
            val add = src[row + (x + r + 1).coerceIn(0, w - 1)]
            val sub = src[row + (x - r).coerceIn(0, w - 1)]
            sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            sb += (add and 0xFF) - (sub and 0xFF)
        }
    }

    for (x in 0 until w) {
        var sr = 0
        var sg = 0
        var sb = 0
        for (y in -r..r) {
            val p = tmp[y.coerceIn(0, h - 1) * w + x]
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
        }
        for (y in 0 until h) {
            out[y * w + x] = (0xFF shl 24) or
                ((sr / span) shl 16) or ((sg / span) shl 8) or (sb / span)
            val add = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
            val sub = tmp[(y - r).coerceIn(0, h - 1) * w + x]
            sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            sb += (add and 0xFF) - (sub and 0xFF)
        }
    }
    return out
}

/**
 * A dialog sheet.
 *
 * [backdrop] and [frost] are accepted and ignored, deliberately: the dialogs still
 * pass the rendered frame, and silently changing every call site just to drop an
 * argument would have been churn for no visual difference.
 */
@Composable
fun FrostedPanel(
    backdrop: Bitmap?,
    shape: Shape = RoundedCornerShape(22.dp),
    frost: Frost = Frost.MEDIUM,
    elevation: Dp = 22.dp,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.75f),
                spotColor = Color.Black.copy(alpha = 0.85f),
            )
            .clip(shape)
            .background(FlowSurface)
            .border(width = 1.dp, color = FlowLine, shape = shape),
    ) {
        Box(modifier = Modifier.padding(padding), content = content)
    }
}

/** Only used if a screen wants the blurred photo as a background layer. */
@Composable
fun FrostedBackdrop(backdrop: Bitmap?, alpha: Float = 0.10f) {
    if (backdrop == null) return
    Image(
        bitmap = backdrop.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(0.dp)),
        alpha = alpha,
    )
}
