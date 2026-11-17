package com.assessment.movies.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MovieColors = lightColorScheme(
    primary = Color(0xFFB4232C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE7E5),
    onPrimaryContainer = Color(0xFF75151D),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF221E1C),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF221E1C),
    surfaceVariant = Color(0xFFF0E8E1),
    onSurfaceVariant = Color(0xFF625650),
    outline = Color(0xFF81746D),
    error = Color(0xFFB3261E),
)

@Composable
fun MovieTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MovieColors, content = content)
}
