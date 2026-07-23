package com.storybrain.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5E4E82),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DFF8),
    secondary = Color(0xFF4F635A),
    secondaryContainer = Color(0xFFD2E8DD),
    background = Color(0xFFF7F4EE),
    surface = Color(0xFFFFFCF7),
    surfaceVariant = Color(0xFFEAE4DC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A32),
    onPrimary = Color(0xFF2A1100),
    primaryContainer = Color(0xFF713000),
    secondary = Color(0xFFAFC7D2),
    secondaryContainer = Color(0xFF344C57),
    background = Color(0xFF111315),
    surface = Color(0xFF1C2023),
    surfaceVariant = Color(0xFF34434A)
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
fun StoryBrainTheme(mode: AppThemeMode = AppThemeMode.DARK, content: @Composable () -> Unit) {
    val useDark = when (mode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !useDark
            isAppearanceLightNavigationBars = !useDark
        }
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = StoryTypography,
        shapes = StoryShapes,
        content = content
    )
}
