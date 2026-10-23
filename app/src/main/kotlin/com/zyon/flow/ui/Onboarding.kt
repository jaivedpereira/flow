/**
 * Flow — first-run onboarding.
 *
 * Three cards, one idea each. The point is not to explain every feature; it is
 * to make the value obvious in ten seconds and to teach the three gestures that
 * are otherwise impossible to discover (long-press compare, brush mask, drag text).
 */

package com.zyon.flow.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyon.flow.R
import com.zyon.flow.ui.theme.FlowBlack
import com.zyon.flow.ui.theme.FlowCream
import com.zyon.flow.ui.theme.FlowElevated
import com.zyon.flow.ui.theme.FlowLine
import com.zyon.flow.ui.theme.FlowTextDim
import kotlinx.coroutines.launch

private data class Slide(
    val titleRes: Int,
    val bodyRes: Int,
    val accent: List<Color>,
)

private val Slides = listOf(
    Slide(
        R.string.onboard_1_title,
        R.string.onboard_1_body,
        listOf(Color(0xFFF7E2CE), Color(0xFFD67A68), Color(0xFF563A6C)),
    ),
    Slide(
        R.string.onboard_2_title,
        R.string.onboard_2_body,
        listOf(Color(0xFF101822), Color(0xFF2E3A48), Color(0xFF0A0A0C)),
    ),
    Slide(
        R.string.onboard_3_title,
        R.string.onboard_3_body,
        listOf(Color(0xFFEDE7DC), Color(0xFF8C7A68), Color(0xFF18181B)),
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { Slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // skip, top right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = stringResource(R.string.onboard_skip),
                color = FlowTextDim,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDone() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
        ) { page ->
            val slide = Slides[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // abstract art per slide
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(slide.accent),
                        )
                        .border(1.dp, FlowLine, CircleShape),
                )

                Spacer(Modifier.height(48.dp))

                Text(
                    text = stringResource(slide.titleRes),
                    color = FlowCream,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp,
                )

                Spacer(Modifier.height(18.dp))

                Text(
                    text = stringResource(slide.bodyRes),
                    color = FlowTextDim,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )
            }
        }

        // dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(Slides.size) { i ->
                val active = pager.currentPage == i
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .size(width = if (active) 26.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (active) FlowCream else FlowElevated),
                )
            }
        }

        // primary button
        val isLast = pager.currentPage == Slides.lastIndex
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(FlowCream)
                .clickable {
                    if (isLast) {
                        onDone()
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                }
                .padding(vertical = 17.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(if (isLast) R.string.onboard_start else R.string.onboard_next),
                color = FlowBlack,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}
