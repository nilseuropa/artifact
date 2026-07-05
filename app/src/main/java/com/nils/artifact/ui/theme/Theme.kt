package com.nils.artifact.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArtifactColors = lightColorScheme(
    primary = Color(0xFF315C7D),
    onPrimary = Color.White,
    secondary = Color(0xFF496A54),
    onSecondary = Color.White,
    tertiary = Color(0xFF8A5A2B),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF17202A),
    surface = Color.White,
    onSurface = Color(0xFF17202A),
    surfaceVariant = Color(0xFFE6EBEF),
    onSurfaceVariant = Color(0xFF3F4A54),
    outline = Color(0xFF7A8792),
)

@Composable
fun ArtifactTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArtifactColors,
        content = content,
    )
}
