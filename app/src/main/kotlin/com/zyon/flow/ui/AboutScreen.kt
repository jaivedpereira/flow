/**
 * Flow — about.
 *
 * What the app is, what it is built from, and where the source lives. The version
 * is read from the package at runtime rather than typed here, so it can never
 * drift from the actual build.
 */

package com.zyon.flow.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.ui.theme.FlowAccent
import com.zyon.flow.ui.theme.FlowBlack
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowRaised
import com.zyon.flow.ui.theme.FlowSurface
import com.zyon.flow.ui.theme.FlowText
import com.zyon.flow.ui.theme.FlowTextDim

private const val REPO_URL = "https://github.com/jaivedpereira/flow"
private const val RELEASES_URL = "https://github.com/jaivedpereira/flow/releases"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // read from the package, so the number cannot disagree with the build
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
            "${info.versionName}  ·  build $code"
        }.getOrDefault("—")
    }

    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ---- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(FlowElevated)
                    .border(1.dp, FlowLine, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "←", color = FlowText, fontSize = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Sobre",
                color = FlowText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            BrandBlock(version)
            Spacer(Modifier.height(22.dp))
            WhatItIs()
            Spacer(Modifier.height(18.dp))
            TechnicalBlock()
            Spacer(Modifier.height(18.dp))
            LinksBlock(
                onRepo = { open(REPO_URL) },
                onReleases = { open(RELEASES_URL) },
            )
            Spacer(Modifier.height(18.dp))
            PrivacyBlock()
            Spacer(Modifier.height(18.dp))
            Credits()
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---------------------------------------------------------------- blocks

@Composable
private fun BrandBlock(version: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // the mark: the real launcher icon, on the same #06050A background it ships
        // with, so About and the home screen icon are the same object
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF06050A)),
            contentAlignment = Alignment.Center,
        ) {
            CubeMark(Color(0xFF06050A))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "flow",
            color = FlowText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(
            text = "editor de fotos open source",
            color = FlowTextDim,
            fontSize = 9.sp,
            letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = version,
            color = FlowTextDim.copy(alpha = 0.75f),
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun WhatItIs() {
    Card {
        SectionLabel("O QUE É")
        Text(
            text = "Um editor de fotos para Android, gratuito e sem conta. " +
                "Abre a foto, aplica um look com cubos de cor, camadas e ajustes, " +
                "e salva de volta na galeria.",
            color = FlowText,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Nada sai do aparelho. Não tem login, servidor, telemetria " +
                "nem assinatura — o app inteiro funciona em modo avião.",
            color = FlowTextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun TechnicalBlock() {
    Card {
        SectionLabel("DETALHES TÉCNICOS")

        TechRow("Kotlin", "2.4")
        TechRow("Jetpack Compose", "Material 3")
        TechRow("minSdk · targetSdk", "26 · 36")
        TechRow("LUTs embutidos", "269 cubos · 17³")
        TechRow("Pipeline", "12 estágios")
        TechRow("Camadas", "cubo, cor, efeito, texto, adesivo")
        TechRow("Máscaras", "pincel, linear, radial, luminância, cor")
        TechRow("Exportação", "JPEG · PNG · WebP")
        TechRow("Rede", "nenhuma")

        Spacer(Modifier.height(8.dp))
        Text(
            text = "O render roda em CPU, por escolha: funciona igual em qualquer " +
                "aparelho, sem depender de driver ou versão de GPU.",
            color = FlowTextDim,
            fontSize = 10.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun TechRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = FlowTextDim,
            fontSize = 10.sp,
            modifier = Modifier.width(150.dp),
        )
        Text(
            text = value,
            color = FlowText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LinksBlock(onRepo: () -> Unit, onReleases: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinkRow(
            label = "Código-fonte",
            detail = "github.com/jaivedpereira/flow",
            onClick = onRepo,
        )
        LinkRow(
            label = "Versões",
            detail = "releases · APK direto",
            onClick = onReleases,
        )
    }
}

@Composable
private fun LinkRow(label: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlowSurface)
            .border(1.dp, FlowLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = FlowText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                color = FlowTextDim,
                fontSize = 9.sp,
            )
        }
        Text(text = "↗", color = FlowAccent, fontSize = 13.sp)
    }
}

@Composable
private fun PrivacyBlock() {
    Card {
        SectionLabel("PRIVACIDADE")
        Text(
            text = "O app não pede permissão de internet. As fotos que você importa " +
                "são copiadas para o armazenamento privado do app; o original na " +
                "galeria nunca é alterado.",
            color = FlowText,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun Credits() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "MIT License",
            color = FlowTextDim,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "feito por jaivedpereira",
            color = FlowTextDim.copy(alpha = 0.6f),
            fontSize = 9.sp,
        )
    }
}

// ---------------------------------------------------------------- atoms

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FlowSurface)
            .border(1.dp, FlowLine, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = FlowTextDim,
        fontSize = 8.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/**
 * The launcher icon, drawn at runtime.
 *
 * Same geometry as res/drawable/ic_launcher_foreground.xml: an isometric cube with
 * two dark faces, five LUT gradient bands across the top face, and a hairline rim
 * in #786060. Drawn rather than shipped as a bitmap so it stays crisp at any size
 * and there is only one source of truth for the shape.
 */
@Composable
private fun CubeMark(@Suppress("UNUSED_PARAMETER") tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(30.dp)) {
        val s = size.minDimension
        // the vector uses a 108x108 viewport; scale from that so the numbers below
        // match the drawable exactly
        val u = s / 108f
        fun p(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * u, y * u)

        fun poly(vararg pts: Pair<Float, Float>): androidx.compose.ui.graphics.Path =
            androidx.compose.ui.graphics.Path().apply {
                pts.forEachIndexed { i, (x, y) ->
                    if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u)
                }
                close()
            }

        // ---- the two side faces
        drawPath(
            poly(54f to 54f, 54f to 90f, 22.823f to 72f, 22.823f to 36f),
            Color(0xFF181222),
        )
        drawPath(
            poly(54f to 54f, 54f to 90f, 85.177f to 72f, 85.177f to 36f),
            Color(0xFF2E1C20),
        )

        // ---- the LUT bands, back to front
        val bands = listOf(
            Color(0xFFF7E2CE) to poly(54f to 54f, 22.823f to 36f, 29.058f to 32.4f, 60.235f to 50.4f),
            Color(0xFFF0B296) to poly(60.235f to 50.4f, 29.058f to 32.4f, 35.294f to 28.8f, 66.471f to 46.8f),
            Color(0xFFD67A68) to poly(66.471f to 46.8f, 35.294f to 28.8f, 41.529f to 25.2f, 72.706f to 43.2f),
            Color(0xFF965268) to poly(72.706f to 43.2f, 41.529f to 25.2f, 47.765f to 21.6f, 78.942f to 39.6f),
            Color(0xFF563A6C) to poly(78.942f to 39.6f, 47.765f to 21.6f, 54f to 18f, 85.177f to 36f),
        )
        bands.forEach { (color, path) -> drawPath(path, color) }

        // ---- rim
        val rim = Color(0xFF786060)
        val w = 1.15f * u
        listOf(
            poly(54f to 54f, 22.823f to 36f, 54f to 18f, 85.177f to 36f),
            poly(54f to 54f, 54f to 90f, 22.823f to 72f, 22.823f to 36f),
            poly(54f to 54f, 54f to 90f, 85.177f to 72f, 85.177f to 36f),
        ).forEach { path ->
            drawPath(
                path,
                rim,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = w,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
    }
}
