package com.nils.artifact.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArtifactLightColors = lightColorScheme(
    primary = Color(0xFF315C7D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E4F4),
    onPrimaryContainer = Color(0xFF0B2D45),
    secondary = Color(0xFF496A54),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCBE9D3),
    onSecondaryContainer = Color(0xFF0C2A17),
    tertiary = Color(0xFF8A5A2B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDBE),
    onTertiaryContainer = Color(0xFF2E1600),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF17202A),
    surface = Color.White,
    onSurface = Color(0xFF17202A),
    surfaceVariant = Color(0xFFE6EBEF),
    onSurfaceVariant = Color(0xFF3F4A54),
    outline = Color(0xFF7A8792),
)

private val ArtifactDarkColors = darkColorScheme(
    primary = Color(0xFFA5C9E8),
    onPrimary = Color(0xFF0B2D45),
    primaryContainer = Color(0xFF174564),
    onPrimaryContainer = Color(0xFFD4E4F4),
    secondary = Color(0xFFAED3B8),
    onSecondary = Color(0xFF173722),
    secondaryContainer = Color(0xFF304F39),
    onSecondaryContainer = Color(0xFFCBE9D3),
    tertiary = Color(0xFFE9BE8E),
    onTertiary = Color(0xFF4A2808),
    tertiaryContainer = Color(0xFF684014),
    onTertiaryContainer = Color(0xFFFFDDBE),
    background = Color(0xFF121416),
    onBackground = Color(0xFFE4E7EA),
    surface = Color(0xFF1A1D1F),
    onSurface = Color(0xFFE4E7EA),
    surfaceVariant = Color(0xFF3F464C),
    onSurfaceVariant = Color(0xFFC3CAD1),
    outline = Color(0xFF8D969E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun ArtifactTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        ArtifactDarkColors
    } else {
        ArtifactLightColors
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
