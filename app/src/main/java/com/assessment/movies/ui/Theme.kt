package com.assessment.movies.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assessment.movies.R

object MovieStyle {
    val Canvas = Color(0xFF1F1F29)
    val SearchSurface = Color(0xFF131316)
    val RaisedSurface = Color(0xFF312F35)
    val Genre = Color(0xFF515053)
    val SelectedGenre = Color(0xFF3A21D9)
    val Muted = Color(0xFFB9C1D9)
    val Rating = Color(0xFFFFFF00)
    val Gold = Color(0xFFF5C518)
    val Play = Color(0xFFF01818)
    val Skeleton = Color(0xFF35353F)
    val SkeletonHighlight = Color(0xFF4A4A58)
    val PagePadding = 16.dp
    val Gap = 10.dp
    val Corner = 10.dp
}

private val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

private val MovieTypography = Typography(
    headlineLarge = type(24, 32, FontWeight.Bold),
    headlineMedium = type(22, 30, FontWeight.SemiBold),
    headlineSmall = type(20, 28, FontWeight.SemiBold),
    titleLarge = type(20, 28, FontWeight.SemiBold),
    titleMedium = type(16, 22, FontWeight.SemiBold),
    titleSmall = type(14, 20, FontWeight.SemiBold),
    bodyLarge = type(14, 21),
    bodyMedium = type(14, 20),
    bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.SemiBold),
    labelMedium = type(12, 18, FontWeight.Medium),
    labelSmall = type(12, 16),
)

private val MovieColors = darkColorScheme(
    primary = MovieStyle.Gold,
    onPrimary = Color(0xFF17130B),
    primaryContainer = MovieStyle.SelectedGenre,
    onPrimaryContainer = Color.White,
    secondary = MovieStyle.SelectedGenre,
    onSecondary = Color.White,
    background = MovieStyle.Canvas,
    onBackground = Color.White,
    surface = MovieStyle.Canvas,
    onSurface = Color.White,
    surfaceVariant = MovieStyle.RaisedSurface,
    onSurfaceVariant = MovieStyle.Muted,
    outline = Color(0xFF73737F),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MovieTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MovieColors, typography = MovieTypography, content = content)
}
