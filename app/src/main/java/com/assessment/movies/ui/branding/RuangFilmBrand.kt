package com.assessment.movies.ui.branding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

internal object RuangFilmBrand {
    const val PathData = "M40 36H136C174 36 198 54 206 84H160C156 78 148 76 136 76H84V116H136C148 116 156 114 160 108H208C206 132 194 148 172 156L216 216H160L116 156H84V216H40Z"
    const val SplashViewport = 432f
    const val SplashInset = 88f
    const val RevealMillis = 900
    val InkPivot = Offset(62f, 180f)
    const val InkRadius = 20f
}

internal data class RevealGeometry(val origin: Offset, val scale: Float)

internal fun revealGeometry(size: Size, nativeIcon: Rect?, fallbackIconSize: Float, progress: Float): RevealGeometry {
    val validIcon = nativeIcon?.takeIf { it.width > 0f && it.height > 0f }
    val side = validIcon?.let { minOf(it.width, it.height) } ?: fallbackIconSize.coerceAtLeast(1f)
    val center = validIcon?.center ?: Offset(size.width / 2f, size.height / 2f)
    val initialScale = side / RuangFilmBrand.SplashViewport
    val initialOrigin = center - Offset(side / 2f, side / 2f) + Offset(RuangFilmBrand.SplashInset, RuangFilmBrand.SplashInset) * initialScale
    val anchor = initialOrigin + RuangFilmBrand.InkPivot * initialScale
    val distance = maxOf(
        hypot(anchor.x, anchor.y), hypot(size.width - anchor.x, anchor.y),
        hypot(anchor.x, size.height - anchor.y), hypot(size.width - anchor.x, size.height - anchor.y),
    )
    // The pivot's 20-unit disk lies inside the R stem, so its final hole covers every corner.
    val finalScale = maxOf(initialScale, distance / RuangFilmBrand.InkRadius * 1.05f)
    val scale = (initialScale * exp(ln((finalScale / initialScale).toDouble()) * progress.coerceIn(0f, 1f))).toFloat()
    return RevealGeometry(anchor - RuangFilmBrand.InkPivot * scale, scale)
}
