package com.yourname.pdftoolkit.ui.theme

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ColorTokens.darkPrimary,
    onPrimary = Ink950,
    primaryContainer = EyadBlueDark,
    onPrimaryContainer = EyadBlueLight,
    secondary = ColorTokens.darkSecondary,
    onSecondary = Ink950,
    secondaryContainer = EyadTealDark,
    onSecondaryContainer = EyadTealLight,
    tertiary = EyadAmber,
    onTertiary = Ink950,
    tertiaryContainer = ColorTokens.darkAmberContainer,
    onTertiaryContainer = EyadAmberLight,
    background = Ink950,
    onBackground = Cloud50,
    surface = DarkSurface,
    onSurface = Cloud50,
    surfaceVariant = DarkSurfaceRaised,
    onSurfaceVariant = Cloud200,
    outline = DarkOutline,
    outlineVariant = Ink700,
)

private val LightColorScheme = lightColorScheme(
    primary = EyadBlue,
    onPrimary = White,
    primaryContainer = EyadBlueLight,
    onPrimaryContainer = EyadBlueDark,
    secondary = EyadTeal,
    onSecondary = White,
    secondaryContainer = EyadTealLight,
    onSecondaryContainer = EyadTealDark,
    tertiary = EyadAmber,
    onTertiary = Ink950,
    tertiaryContainer = EyadAmberLight,
    onTertiaryContainer = Ink900,
    background = Cloud25,
    onBackground = Ink900,
    surface = White,
    onSurface = Ink900,
    surfaceVariant = Cloud100,
    onSurfaceVariant = Ink700,
    outline = Ink500,
    outlineVariant = Cloud200,
)

private object ColorTokens {
    val darkPrimary = androidx.compose.ui.graphics.Color(0xFF9DBEFF)
    val darkSecondary = androidx.compose.ui.graphics.Color(0xFF77D7CB)
    val darkAmberContainer = androidx.compose.ui.graphics.Color(0xFF4A3510)
}

@Composable
fun EyadPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val actualDarkTheme = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> darkTheme
    }

    // A fixed brand palette is deliberate. Dynamic colors made the application
    // look different on every device and weakened the Eyad PDF visual identity.
    val colorScheme = if (actualDarkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !actualDarkTheme
                isAppearanceLightNavigationBars = !actualDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
