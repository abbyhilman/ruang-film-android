package com.assessment.movies.ui.branding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

internal object RuangFilmBrand {
    const val PathData = "M40 36H136C174 36 198 54 206 84H160C156 78 148 76 136 76H84V116H136C148 116 156 114 160 108H208C206 132 194 148 172 156L216 216H160L116 156H84V216H40Z"
    const val SplashViewport = 432f
    const val RevealMillis = 900
    val InkPivot = Offset(128f, 135f)
    const val InkRadius = 17f
}

internal data class RevealGeometry(val origin: Offset, val scale: Float)

internal fun revealGeometry(size: Size, fallbackIconSize: Float, progress: Float): RevealGeometry {
    val side = minOf(fallbackIconSize, size.width, size.height).coerceAtLeast(1f)
    val anchor = Offset(size.width / 2f, size.height / 2f)
    val initialScale = side / RuangFilmBrand.SplashViewport
    val distance = hypot(anchor.x, anchor.y)
    // The pivot lies inside the R crossbar, so its final hole covers every corner.
    val finalScale = maxOf(initialScale, distance / RuangFilmBrand.InkRadius * 1.05f)
    val scale = (initialScale * exp(ln((finalScale / initialScale).toDouble()) * progress.coerceIn(0f, 1f))).toFloat()
    return RevealGeometry(anchor - RuangFilmBrand.InkPivot * scale, scale)
}
