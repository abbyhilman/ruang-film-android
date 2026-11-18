package com.assessment.movies.ui.branding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.assessment.movies.R
import com.assessment.movies.ui.MovieStyle

internal class StartupRevealState(initiallyVisible: Boolean) {
    var visible by mutableStateOf(initiallyVisible)
        private set
    var started by mutableStateOf(false)
        private set
    var nativeIconBounds by mutableStateOf<Rect?>(null)
        private set
    var motionAllowed by mutableStateOf(true)
        private set

    fun handoff(bounds: Rect?, animate: Boolean) {
        if (!visible) return
        nativeIconBounds = bounds
        motionAllowed = animate
        started = true
        if (!animate) finish()
    }

    fun finish() { visible = false }
}

@Composable
internal fun StartupRevealHost(state: StartupRevealState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(if (state.visible) Modifier.clearAndSetSemantics { } else Modifier) { content() }
        if (state.visible) BrandRevealOverlay(state.nativeIconBounds, state.started, state.motionAllowed, state::finish)
    }
}

@Composable
internal fun BrandRevealOverlay(nativeIconBounds: Rect?, started: Boolean, motionEnabled: Boolean, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(started, motionEnabled) {
        if (started) {
            if (motionEnabled) progress.animateTo(1f, tween(RuangFilmBrand.RevealMillis, easing = FastOutSlowInEasing))
            finish()
        }
    }
    val label = stringResource(R.string.app_name)
    BrandRevealFrame(
        nativeIconBounds, if (started) progress.value else null,
        Modifier.fillMaxSize().testTag("brand-reveal").semantics { contentDescription = label }
            .focusRequester(focus).focusable()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
    )
}

@Composable
internal fun BrandRevealFrame(nativeIconBounds: Rect?, progress: Float?, modifier: Modifier = Modifier) {
    val mark = remember { PathParser().parsePathString(RuangFilmBrand.PathData).toPath() }
    Canvas(modifier.testTag("brand-reveal").graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(MovieStyle.Canvas)
        val geometry = revealGeometry(size, nativeIconBounds, 288.dp.toPx(), progress ?: 0f)
        withTransform({
            translate(geometry.origin.x, geometry.origin.y)
            scale(geometry.scale, geometry.scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            if (progress == null) drawPath(mark, MovieStyle.Gold)
            else drawPath(mark, Color.Black, blendMode = BlendMode.Clear)
        }
    }
}
