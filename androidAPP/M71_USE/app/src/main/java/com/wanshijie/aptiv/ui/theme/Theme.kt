package com.wanshijie.aptiv.ui.theme

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
    primary = PlantGreenLight,
    onPrimary = PlantGreenDark,
    primaryContainer = PlantGreen,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9BC9F5),
    onSecondary = ProcessBlueDark,
    secondaryContainer = ProcessBlue,
    onSecondaryContainer = Color.White,
    tertiary = WarningAmberLight,
    onTertiary = WarningAmber,
    error = Color(0xFFFFB4AB),
    background = Color(0xFF111817),
    onBackground = Color(0xFFE7ECEF),
    surface = Color(0xFF18211F),
    onSurface = Color(0xFFE7ECEF),
    surfaceVariant = Color(0xFF33413D),
    onSurfaceVariant = Color(0xFFCAD4D0),
    outline = Color(0xFF91A09A)
)

private val LightColorScheme = lightColorScheme(
    primary = PlantGreen,
    onPrimary = Color.White,
    primaryContainer = PlantGreenLight,
    onPrimaryContainer = PlantGreenDark,
    secondary = ProcessBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E9F8),
    onSecondaryContainer = ProcessBlueDark,
    tertiary = WarningAmber,
    onTertiary = Color.White,
    tertiaryContainer = WarningAmberLight,
    onTertiaryContainer = Color(0xFF3A1B00),
    error = DangerRed,
    background = PageGray,
    onBackground = TextDark,
    surface = Color.White,
    onSurface = TextDark,
    surfaceVariant = PanelGray,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF7D8B85)
)

@Composable
fun APTIVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
