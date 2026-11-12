package com.example.ui.theme

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
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = Color(0xFFE0F7FA),
    secondary = IndigoSecondary,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = IndigoSecondaryContainer,
    onSecondaryContainer = Color(0xFFE0E7FF),
    background = StudioDarkBg,
    onBackground = TextPrimaryDark,
    surface = StudioDarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioDarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    error = RoseWarning,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = CyanOnPrimaryLight,
    primaryContainer = CyanPrimaryContainerLight,
    onPrimaryContainer = Color(0xFF00363A),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    background = StudioLightBg,
    onBackground = Color(0xFF0F172A),
    surface = StudioLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = StudioLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun LishVolumeCheckerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent audio-meter studio aesthetic by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> DarkColorScheme // Pro audio tools look best in sleek dark studio theme by default!
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
