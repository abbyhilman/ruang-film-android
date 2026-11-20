package com.assessment.movies.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.assessment.movies.ui.MovieStyle

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, motionEnabled: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
    val animate = motionEnabled && ValueAnimator.areAnimatorsEnabled() && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val phase: State<Float>? = if (animate) rememberInfiniteTransition(label = "skeleton-shimmer").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-position",
    ) else null
    Box(
        modifier.clearAndSetSemantics { }.drawWithCache {
            val corner = CornerRadius(MovieStyle.Corner.toPx())
            val band = size.width.coerceAtLeast(80.dp.toPx()) * .65f
            onDrawBehind {
                drawRoundRect(MovieStyle.Skeleton, cornerRadius = corner)
                if (phase != null) {
                    val center = phase.value * (size.width + band)
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, MovieStyle.SkeletonHighlight, Color.Transparent),
                            Offset(center - band, 0f), Offset(center + band, size.height),
                        ),
                        cornerRadius = corner,
                    )
                }
            }
        },
    )
}

@Composable
fun PosterCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.testTag("poster-card-skeleton"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        SkeletonBlock(Modifier.fillMaxWidth(.8f).height(16.dp))
        SkeletonBlock(Modifier.fillMaxWidth(.55f).height(12.dp))
    }
}

@Composable
fun DetailSkeleton(showHero: Boolean = true) {
    Column(
        Modifier.fillMaxWidth().testTag("detail-skeleton").semantics {
            contentDescription = "Memuat detail film"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHero) SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(.9f))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(.6f).height(20.dp))
            SkeletonBlock(Modifier.fillMaxWidth(.8f).height(26.dp))
            repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(14.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { SkeletonBlock(Modifier.size(72.dp, 90.dp)) }
            }
            SkeletonBlock(Modifier.fillMaxWidth().height(48.dp))
        }
    }
}

@Composable
fun ReviewSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(Modifier.size(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(Modifier.size(130.dp, 16.dp))
                SkeletonBlock(Modifier.size(80.dp, 12.dp))
            }
        }
        repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(14.dp)) }
    }
}

@Composable
fun PlayerSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.testTag("player-skeleton")) { SkeletonBlock(Modifier.fillMaxSize()) }
}
