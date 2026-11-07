package com.paperink.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = InkPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1012),
    onPrimaryContainer = Color(0xFFFFD6D6),
    secondary = InkSecondaryDark,
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF38383A),
    onSecondaryContainer = Color(0xFFE5E5EA),
    background = MidnightBackground,
    surface = MidnightSurface,
    surfaceVariant = MidnightSurfaceVariant,
    onBackground = MidnightTextPrimary,
    onSurface = MidnightTextPrimary,
    onSurfaceVariant = MidnightTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = InkPrimary,
    onPrimary = Color.White,
    primaryContainer = AdobeRedContainer,
    onPrimaryContainer = AdobeOnRedContainer,
    secondary = InkSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = Color(0xFF1C1C1E),
    background = PaperBackground,
    surface = PaperSurface,
    surfaceVariant = PaperSurfaceVariant,
    onBackground = InkTextPrimary,
    onSurface = InkTextPrimary,
    onSurfaceVariant = InkTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent branding colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
