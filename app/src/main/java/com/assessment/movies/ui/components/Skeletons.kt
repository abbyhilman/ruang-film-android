package com.assessment.movies.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.assessment.movies.ui.MovieStyle

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(
        modifier.clearAndSetSemantics { }.background(
            Brush.linearGradient(listOf(MovieStyle.Skeleton, MovieStyle.SkeletonHighlight, MovieStyle.Skeleton)),
            RoundedCornerShape(MovieStyle.Corner),
        ),
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
fun DetailSkeleton() {
    Column(
        Modifier.fillMaxWidth().testTag("detail-skeleton").semantics {
            contentDescription = "Memuat detail film"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(.9f))
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
