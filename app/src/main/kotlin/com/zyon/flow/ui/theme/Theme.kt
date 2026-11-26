/**
 * Flow — open-source photo editor.
 * Theme: editorial black with a warm cream accent.
 */

package com.zyon.flow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zyon.flow.R

// A four-step ramp instead of black-on-black: each surface is visibly a different
// plane, so a panel reads as a panel without needing a translucent wash to
// separate it from the canvas.
val FlowBlack = Color(0xFF08080A)      // the canvas behind everything
val FlowSurface = Color(0xFF101014)    // sheets and panels
val FlowElevated = Color(0xFF191920)   // controls sitting on a sheet
val FlowRaised = Color(0xFF232331)     // the chosen option in a group
val FlowLine = Color(0x22FFFFFF)
val FlowCream = Color(0xFFEDE7DC)
val FlowText = Color(0xFFFFFFFF)
val FlowTextDim = Color(0xFF9A9AA6)
val FlowDanger = Color(0xFFFF5A4E)
val FlowAccent = Color(0xFF7BB0FF)

private val FlowScheme = darkColorScheme(
    primary = FlowCream,
    onPrimary = FlowBlack,
    primaryContainer = Color(0xFF2A2620),
    onPrimaryContainer = FlowCream,
    secondary = Color(0xFFB9B4A8),
    onSecondary = FlowBlack,
    tertiary = Color(0xFF7BB0FF),
    background = FlowBlack,
    onBackground = FlowText,
    surface = FlowSurface,
    onSurface = FlowText,
    surfaceVariant = FlowElevated,
    onSurfaceVariant = FlowTextDim,
    outline = FlowLine,
    outlineVariant = Color(0x14FFFFFF),
    error = FlowDanger,
    onError = Color.White,
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun TextStyle.inter(spacing: Double = -0.1) =
    copy(fontFamily = Inter, letterSpacing = spacing.sp)

val FlowTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.inter(-1.2),
        displayMedium = displayMedium.inter(-1.0),
        displaySmall = displaySmall.inter(-0.8),
        headlineLarge = headlineLarge.inter(-0.6),
        headlineMedium = headlineMedium.inter(-0.5),
        headlineSmall = headlineSmall.inter(-0.4),
        titleLarge = titleLarge.inter(-0.3),
        titleMedium = titleMedium.inter(-0.2),
        titleSmall = titleSmall.inter(-0.15),
        bodyLarge = bodyLarge.inter(),
        bodyMedium = bodyMedium.inter(),
        bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(0.2),
        labelMedium = labelMedium.inter(0.3),
        labelSmall = labelSmall.inter(0.4),
    )
}

@Composable
fun FlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlowScheme,
        typography = FlowTypography,
        content = content,
    )
}
