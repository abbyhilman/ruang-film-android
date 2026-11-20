@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.assessment.movies.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.keyframesWithSpline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.assessment.movies.data.Movie
import coil3.Image
import kotlin.math.roundToInt

enum class MovieArtworkOrigin { Poster, Featured }

data class MovieArtworkSelection(val movie: Movie, val origin: MovieArtworkOrigin, val mask: ArtworkSubjectMask? = null)

class MovieArtworkTransition(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val viewportSize: IntSize = IntSize.Zero,
    val masks: Map<String, ArtworkSubjectMask> = emptyMap(),
    val selection: MovieArtworkSelection? = null,
    val prepareMask: (String, Image) -> Unit = { _, _ -> },
) {
    internal val contentStates = mutableMapOf<String, SharedTransitionScope.SharedContentState>()
    companion object {
        const val DurationMillis = 600
        const val EdgeMillis = 96
    }

    fun mask(movieId: Int, origin: MovieArtworkOrigin): ArtworkSubjectMask? {
        if (selection?.movie?.id == movieId && selection.origin == origin) return selection.mask
        return masks.entries.firstOrNull { it.key.startsWith("${movieArtworkKey(movieId, origin)}/") }?.value
    }
}

fun movieArtworkKey(movieId: Int, origin: MovieArtworkOrigin) = "movie-artwork/$movieId/${origin.name}"

@Composable
fun Modifier.movieArtworkTransition(movieId: Int, origin: MovieArtworkOrigin, transition: MovieArtworkTransition?): Modifier {
    if (transition == null) return this
    val mask = transition.mask(movieId, origin)
    return with(transition.sharedScope) {
        val sharedState = rememberSharedContentState(movieArtworkKey(movieId, origin))
        transition.contentStates[movieArtworkKey(movieId, origin)] = sharedState
        this@movieArtworkTransition.sharedElement(
            sharedState,
            animatedVisibilityScope = transition.visibilityScope,
            boundsTransform = { start, end ->
                if (transition.viewportSize.width == 0 || transition.viewportSize.height == 0) {
                    tween(MovieArtworkTransition.DurationMillis, easing = FastOutSlowInEasing)
                } else keyframesWithSpline {
                    durationMillis = MovieArtworkTransition.DurationMillis
                    start at 0
                    start at MovieArtworkTransition.EdgeMillis
                    val focus = mask?.let { croppedSubjectFocus(it, end.width, end.height) } ?: Offset(.5f, .5f)
                    centeredArtworkBounds(start, end, transition.viewportSize, focus) at durationMillis / 2
                    end at durationMillis - MovieArtworkTransition.EdgeMillis
                    end at durationMillis
                }
            },
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
fun Modifier.movieArtworkMask(movieId: Int, origin: MovieArtworkOrigin, transition: MovieArtworkTransition?): Modifier {
    if (transition == null) return this
    val sharedState = transition.contentStates[movieArtworkKey(movieId, origin)] ?: return this
    val phase by transition.visibilityScope.transition.animateFloat(
        transitionSpec = { tween(MovieArtworkTransition.DurationMillis, easing = LinearEasing) }, label = "Subject reveal",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val mask = transition.mask(movieId, origin)
    return with(transition.sharedScope) {
        this@movieArtworkMask.graphicsLayer {
            compositingStrategy = if (mask != null && sharedState.isMatchFound && isTransitionActive) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            alpha = if (mask == null && sharedState.isMatchFound && isTransitionActive) 1f - artworkIsolation(phase) else 1f
        }.drawWithContent {
            drawContent()
            if (mask != null && sharedState.isMatchFound && isTransitionActive) {
                val scale = maxOf(size.width / mask.inverseAlpha.width, size.height / mask.inverseAlpha.height)
                val width = (mask.inverseAlpha.width * scale).roundToInt()
                val height = (mask.inverseAlpha.height * scale).roundToInt()
                drawImage(
                    mask.inverseAlpha,
                    dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
                    dstSize = IntSize(width, height),
                    alpha = artworkIsolation(phase), blendMode = BlendMode.DstOut,
                )
            }
        }
    }
}

internal fun artworkIsolation(progress: Float): Float {
    val edge = MovieArtworkTransition.EdgeMillis.toFloat() / MovieArtworkTransition.DurationMillis
    val t = (minOf(progress, 1f - progress) / edge).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun centeredArtworkBounds(start: Rect, end: Rect, viewport: IntSize, focus: Offset = Offset(.5f, .5f)): Rect {
    val halfWidth = (start.width + end.width) / 4f
    val halfHeight = (start.height + end.height) / 4f
    val centerX = viewport.width / 2f + halfWidth * (1f - 2f * focus.x)
    val centerY = viewport.height / 2f + halfHeight * (1f - 2f * focus.y)
    return Rect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
}

internal fun croppedSubjectFocus(mask: ArtworkSubjectMask, width: Float, height: Float): Offset {
    if (width <= 0 || height <= 0) return Offset(.5f, .5f)
    val scale = maxOf(width / mask.inverseAlpha.width, height / mask.inverseAlpha.height)
    return Offset(
        (.5f + (mask.focus.x - .5f) * mask.inverseAlpha.width * scale / width).coerceIn(0f, 1f),
        (.5f + (mask.focus.y - .5f) * mask.inverseAlpha.height * scale / height).coerceIn(0f, 1f),
    )
}

@Composable
fun Modifier.movieTitleTransition(movieId: Int, origin: MovieArtworkOrigin, transition: MovieArtworkTransition?): Modifier {
    if (transition == null) return this
    return with(transition.sharedScope) {
        this@movieTitleTransition.sharedBounds(
            rememberSharedContentState("movie-title/$movieId/${origin.name}"),
            animatedVisibilityScope = transition.visibilityScope,
            boundsTransform = { start, end -> keyframesWithSpline {
                durationMillis = MovieArtworkTransition.DurationMillis
                start at 0
                start at MovieArtworkTransition.EdgeMillis
                val width = (start.width + end.width) / 2f
                val height = (start.height + end.height) / 2f
                val x = transition.viewportSize.width / 2f
                val y = transition.viewportSize.height * .8f
                Rect(x - width / 2, y - height / 2, x + width / 2, y + height / 2) at durationMillis / 2
                end at durationMillis - MovieArtworkTransition.EdgeMillis
                end at durationMillis
            } },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            zIndexInOverlay = 3f,
        )
    }
}

@Composable
fun Modifier.aboveMovieArtwork(transition: MovieArtworkTransition?): Modifier {
    if (transition == null) return this
    val phase by transition.visibilityScope.transition.animateFloat(
        transitionSpec = { tween(MovieArtworkTransition.DurationMillis, easing = LinearEasing) }, label = "Hero chrome",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return with(transition.sharedScope) {
        this@aboveMovieArtwork.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 2f).graphicsLayer {
            alpha = if (isTransitionActive) 1f - artworkIsolation(phase) else 1f
        }
    }
}
