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
    primary = Color(0xFF65558F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF006B5F),
    secondaryContainer = Color(0xFF9EF2E1),
    background = Color(0xFFF9F7FC),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFE8E0EB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF35265D),
    primaryContainer = Color(0xFF4D3D75),
    secondary = Color(0xFF82D5C5),
    secondaryContainer = Color(0xFF154F48),
    background = Color(0xFF141218),
    surface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFF49454F)
)

private val StoryShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

private val StoryTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 22.sp)
    )
}

@Composable
fun StoryBrainTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = StoryTypography,
        shapes = StoryShapes,
        content = content
    )
}
