package com.storybrain.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF161616),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF161616),
    secondary = Color(0xFF4A4A4A),
    secondaryContainer = Color(0xFFEDEDED),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF565656),
    outline = Color(0xFF777777),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF161616),
    primaryContainer = Color(0xFF303030),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFD0D0D0),
    secondaryContainer = Color(0xFF383838),
    background = Color(0xFF101010),
    surface = Color(0xFF171717),
    surfaceVariant = Color(0xFF303030),
    onSurfaceVariant = Color(0xFFD0D0D0),
    outline = Color(0xFF999999),
    error = Color(0xFFFFB4AB)
)

private val StoryShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(12.dp)
)

private val StoryTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 29.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif),
        bodyMedium = bodyMedium.copy(lineHeight = 24.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
    )
}

@Composable
fun StoryBrainTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = StoryTypography,
        shapes = StoryShapes,
        content = content
    )
}
