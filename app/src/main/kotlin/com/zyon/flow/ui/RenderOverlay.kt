/**
 * Flow — render and export progress.
 *
 * Rendering a full-resolution export takes a noticeable moment, and until now the
 * screen simply froze with no feedback. This overlay covers the editor with the
 * photo blurred behind it and a spinner that reports which stage is running.
 *
 * Deliberately blocking: while an export is in flight the edit state must not
 * change underneath it.
 */

package com.zyon.flow.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim

/** Which part of the pipeline is running, so the text is honest. */
enum class RenderStage(val labelRes: Int) {
    PREPARING(1),
    RENDERING(2),
    WRITING(3),
}

/**
 * A blocking progress overlay.
 *
 * [stage] drives the caption and the position of the determinate bar; [progress]
 * is 0..1 when a real fraction is known, or null to keep the bar indeterminate.
 */
@Composable
fun RenderOverlay(
    visible: Boolean,
    stage: RenderStage = RenderStage.RENDERING,
    progress: Float? = null,
    preview: Bitmap? = null,
    caption: String? = null,
) {
    if (!visible) return

    // the backdrop is the photo itself, heavily dimmed: the editor is still there,
    // just out of reach until the write finishes
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0D).copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Crop keeps the photo's proportion behind the overlay too
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.10f),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Spinner()

            Spacer(Modifier.height(22.dp))

            Text(
                text = caption ?: stageLabel(stage),
                color = FlowText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stageHint(stage),
                color = FlowTextDim,
                fontSize = 9.sp,
            )

            Spacer(Modifier.height(18.dp))

            ProgressBar(progress)
        }
    }
}

@Composable
private fun Spinner() {
    val transition = rememberInfiniteTransition(label = "render-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )

    Box(contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(46.dp)
                .rotate(angle),
        ) {
            val w = size.minDimension * 0.085f
            // faint full ring
            drawCircle(
                color = FlowCream.copy(alpha = 0.12f),
                radius = size.minDimension / 2f - w,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
            )
            // bright arc
            drawArc(
                color = FlowCream,
                startAngle = 0f,
                sweepAngle = 96f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = w,
                    cap = StrokeCap.Round,
                ),
            )
        }
        // the little dot in the middle, so it reads as a lens rather than a hole
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(FlowCream.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun ProgressBar(progress: Float?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(FlowCream.copy(alpha = 0.12f)),
    ) {
        if (progress != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(FlowCream.copy(alpha = 0.55f), FlowCream),
                        ),
                    ),
            )
        } else {
            // indeterminate: a short bright segment sliding across.
            // BoxWithConstraints gives the real width, which is the only clean way
            // to place by fraction without measuring by hand.
            val transition = rememberInfiniteTransition(label = "render-bar")
            val shift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1250, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shift",
            )
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val track = maxWidth
                val segment = track * 0.34f
                // travel from fully off the left edge to fully off the right
                val travel = track + segment
                Box(
                    modifier = Modifier
                        .offset(x = segment * -1 + travel * shift)
                        .width(segment)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(FlowCream),
                )
            }
        }
    }
}

private fun stageLabel(stage: RenderStage): String = when (stage) {
    RenderStage.PREPARING -> "Preparando"
    RenderStage.RENDERING -> "Renderizando"
    RenderStage.WRITING -> "Gravando"
}

private fun stageHint(stage: RenderStage): String = when (stage) {
    RenderStage.PREPARING -> "carregando a foto em tamanho real"
    RenderStage.RENDERING -> "aplicando camadas e ajustes"
    RenderStage.WRITING -> "salvando na galeria"
}
