/**
 * Flow — surfaces.
 *
 * These were translucent "glass" panels: a faint wash over whatever photo happened
 * to be behind them, plus a sheen and a lit rim. Over a bright image the text lost
 * contrast, and the effect competed with the photograph instead of staying out of
 * its way.
 *
 * Now they are solid. An editor's chrome should be quiet: the photo is the only
 * thing on screen that is allowed to be interesting.
 *
 * The function names are unchanged so every call site keeps working; what they
 * draw is completely different.
 */

package com.zyon.flow.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How prominent a surface is. Kept for API compatibility with the old tints. */
enum class GlassTint {
    /** Panels and sheets that hold content. */
    PANEL,

    /** Small controls that sit on top of the editor. */
    CONTROL,

    /** The chosen option in a group. */
    ACTIVE,
}

private fun fill(tint: GlassTint): Color = when (tint) {
    GlassTint.PANEL -> FlowSurface
    GlassTint.CONTROL -> FlowElevated
    GlassTint.ACTIVE -> FlowRaised
}

private fun rim(tint: GlassTint): Color = when (tint) {
    GlassTint.PANEL -> FlowLine
    GlassTint.CONTROL -> FlowLine
    GlassTint.ACTIVE -> FlowCream.copy(alpha = 0.55f)
}

/**
 * A solid surface with a hairline rim.
 *
 * The rim is what separates a panel from the canvas behind it now that there is no
 * translucency to do the job; without it an elevated panel just looks like a hole.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(14.dp),
    tint: GlassTint = GlassTint.PANEL,
    elevation: Dp = 0.dp,
    rimAlpha: Float = 0.20f,
): Modifier = this
    .then(
        if (elevation > 0.dp) {
            Modifier.shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.7f),
                spotColor = Color.Black.copy(alpha = 0.8f),
            )
        } else {
            Modifier
        },
    )
    .clip(shape)
    .background(fill(tint))
    .border(width = 1.dp, color = rim(tint), shape = shape)

/** Same surface, tuned for pill-shaped buttons and small controls. */
fun Modifier.glassControl(
    shape: Shape = RoundedCornerShape(12.dp),
    tint: GlassTint = GlassTint.CONTROL,
    elevation: Dp = 0.dp,
): Modifier = this
    .then(
        if (elevation > 0.dp) {
            Modifier.shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.7f),
            )
        } else {
            Modifier
        },
    )
    .clip(shape)
    .background(fill(tint))
    .border(width = 1.dp, color = rim(tint), shape = shape)

/** The container used by the editing panels. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    tint: GlassTint = GlassTint.PANEL,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .glass(shape = shape, tint = tint, elevation = 10.dp)
            .padding(padding),
        content = content,
    )
}
