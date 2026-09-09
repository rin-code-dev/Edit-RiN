package com.hikariatelier.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF183153),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFC2C7D0),
    background = Color.Black,
    onBackground = Color(0xFFE6E6E9),
    surface = Color(0xFF09090B),
    onSurface = Color(0xFFE6E6E9),
    surfaceVariant = Color(0xFF16161A),
    onSurfaceVariant = Color(0xFFC5C6CE),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF303038),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71),
    background = Color.White,
    onBackground = Color(0xFF191C20),
    surface = Color.White,
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFF1F2F8),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun AppTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    customFont: FontFamily? = null,
    ligatures: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = when {
        themeMode == AppThemeMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val typography = remember(customFont, ligatures) {
        fun TextStyle.withFont() = copy(
            fontFamily = customFont ?: fontFamily,
            fontFeatureSettings = if (ligatures) "'liga' 1, 'clig' 1, 'calt' 1"
                else "'liga' 0, 'clig' 0, 'calt' 0"
        )
        Typography.copy(
            displayLarge = Typography.displayLarge.withFont(),
            displayMedium = Typography.displayMedium.withFont(),
            displaySmall = Typography.displaySmall.withFont(),
            headlineLarge = Typography.headlineLarge.withFont(),
            headlineMedium = Typography.headlineMedium.withFont(),
            headlineSmall = Typography.headlineSmall.withFont(),
            titleLarge = Typography.titleLarge.withFont(),
            titleMedium = Typography.titleMedium.withFont(),
            titleSmall = Typography.titleSmall.withFont(),
            bodyLarge = Typography.bodyLarge.withFont(),
            bodyMedium = Typography.bodyMedium.withFont(),
            bodySmall = Typography.bodySmall.withFont(),
            labelLarge = Typography.labelLarge.withFont(),
            labelMedium = Typography.labelMedium.withFont(),
            labelSmall = Typography.labelSmall.withFont()
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
