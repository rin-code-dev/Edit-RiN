package com.hikariatelier.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    SUMI,
    CUSTOM
}

val LocalCustomTheme = staticCompositionLocalOf { false }
val LocalAppThemeMode = staticCompositionLocalOf { AppThemeMode.SYSTEM }

data class CodeSyntaxPalette(
    val keyword: Color,
    val function: Color,
    val shaderType: Color,
    val shaderBuiltin: Color,
    val preprocessor: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val errorLineBackground: Color
)

internal val LightSyntaxPalette = CodeSyntaxPalette(
    keyword = Color(0xFF6A3D9A),
    function = Color(0xFF1A5A7A),
    shaderType = Color(0xFF28558A),
    shaderBuiltin = Color(0xFF1E635B),
    preprocessor = Color(0xFF8A5210),
    string = Color(0xFF2F6638),
    number = Color(0xFFA63E26),
    comment = Color(0xFF6B6258),
    errorLineBackground = Color(0x22BA1A1A)
)

internal val SumiSyntaxPalette = CodeSyntaxPalette(
    keyword = Color(0xFFD0B2EE),
    function = Color(0xFF93C4DA),
    shaderType = Color(0xFF9CBBE0),
    shaderBuiltin = Color(0xFF8DC8BD),
    preprocessor = Color(0xFFE9C484),
    string = Color(0xFFB4D3A3),
    number = Color(0xFFE8A084),
    comment = Color(0xFFA69E94),
    errorLineBackground = Color(0x3893000A)
)

internal val DarkSyntaxPalette = CodeSyntaxPalette(
    keyword = Color(0xFFCCA7F0),
    function = Color(0xFF8BB6FA),
    shaderType = Color(0xFF8BB6FA),
    shaderBuiltin = Color(0xFF80CBC4),
    preprocessor = Color(0xFFF0C678),
    string = Color(0xFFB5DA9B),
    number = Color(0xFFEC9B7A),
    comment = Color(0xFF88929D),
    errorLineBackground = Color(0x385A1A1A)
)

fun defaultSyntaxPalette(dark: Boolean): CodeSyntaxPalette =
    if (dark) DarkSyntaxPalette else LightSyntaxPalette

private fun syntaxContrastRatio(a: Color, b: Color): Float {
    val bright = maxOf(a.luminance(), b.luminance())
    val dark = minOf(a.luminance(), b.luminance())
    return (bright + 0.05f) / (dark + 0.05f)
}

private fun ensureReadableSyntaxColor(color: Color, surface: Color, dark: Boolean): Color {
    if (surface == Color.Unspecified || syntaxContrastRatio(color, surface) >= 4.5f) return color
    val target = if (dark) Color.White else Color.Black
    var adjusted = color
    for (step in 1..6) {
        adjusted = lerp(color, target, step * 0.14f)
        if (syntaxContrastRatio(adjusted, surface) >= 4.5f) return adjusted
    }
    return adjusted
}

fun codeSyntaxPalette(
    themeMode: AppThemeMode,
    dark: Boolean,
    surfaceColor: Color = Color.Unspecified
): CodeSyntaxPalette {
    val base = when (themeMode) {
        AppThemeMode.SUMI -> SumiSyntaxPalette
        AppThemeMode.LIGHT -> LightSyntaxPalette
        AppThemeMode.DARK -> DarkSyntaxPalette
        AppThemeMode.SYSTEM, AppThemeMode.CUSTOM -> {
            if (dark) {
                if (surfaceColor != Color.Unspecified && surfaceColor.luminance() > 0.025f) SumiSyntaxPalette
                else DarkSyntaxPalette
            } else LightSyntaxPalette
        }
    }
    if (themeMode != AppThemeMode.CUSTOM || surfaceColor == Color.Unspecified) return base
    return base.copy(
        keyword = ensureReadableSyntaxColor(base.keyword, surfaceColor, dark),
        function = ensureReadableSyntaxColor(base.function, surfaceColor, dark),
        shaderType = ensureReadableSyntaxColor(base.shaderType, surfaceColor, dark),
        shaderBuiltin = ensureReadableSyntaxColor(base.shaderBuiltin, surfaceColor, dark),
        preprocessor = ensureReadableSyntaxColor(base.preprocessor, surfaceColor, dark),
        string = ensureReadableSyntaxColor(base.string, surfaceColor, dark),
        number = ensureReadableSyntaxColor(base.number, surfaceColor, dark),
        comment = ensureReadableSyntaxColor(base.comment, surfaceColor, dark)
    )
}

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF183153),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFC2C7D0),
    onSecondary = Color(0xFF252830),
    secondaryContainer = Color(0xFF23252C),
    onSecondaryContainer = Color(0xFFE2E4EC),
    tertiary = Color(0xFFD8C2A8),
    onTertiary = Color(0xFF322618),
    tertiaryContainer = Color(0xFF423324),
    onTertiaryContainer = Color(0xFFF5E3CE),
    background = Color.Black,
    onBackground = Color(0xFFE6E6E9),
    surface = Color(0xFF09090B),
    onSurface = Color(0xFFE6E6E9),
    surfaceVariant = Color(0xFF16161A),
    onSurfaceVariant = Color(0xFFC5C6CE),
    surfaceTint = Color(0xFFA8C7FA),
    surfaceDim = Color(0xFF09090B),
    surfaceBright = Color(0xFF1F1F26),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D10),
    surfaceContainer = Color(0xFF121216),
    surfaceContainerHigh = Color(0xFF18181D),
    surfaceContainerHighest = Color(0xFF1F1F26),
    inverseSurface = Color(0xFFE6E6E9),
    inverseOnSurface = Color(0xFF141418),
    inversePrimary = Color(0xFF2456A5),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF303038),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000)
)

// Warm paper, charcoal and terracotta, shared with the bundled artworks.
// Light primary is deepened to keep small text readable on paper surfaces.
internal val SumiColorScheme = darkColorScheme(
    primary = Color(0xFFE7A083),
    onPrimary = Color(0xFF333132),
    primaryContainer = Color(0xFF503124),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFC8C2B8),
    onSecondary = Color(0xFF2B2825),
    secondaryContainer = Color(0xFF3B3530),
    onSecondaryContainer = Color(0xFFECE1D6),
    tertiary = Color(0xFFD4BBA4),
    onTertiary = Color(0xFF352B23),
    tertiaryContainer = Color(0xFF4B3C30),
    onTertiaryContainer = Color(0xFFF1DFCD),
    background = Color(0xFF333132),
    onBackground = Color(0xFFF2EFE7),
    surface = Color(0xFF333132),
    onSurface = Color(0xFFF2EFE7),
    surfaceVariant = Color(0xFF464240),
    onSurfaceVariant = Color(0xFFC8C2B8),
    surfaceTint = Color(0xFFD67856),
    surfaceDim = Color(0xFF333132),
    surfaceBright = Color(0xFF444243),
    surfaceContainerLowest = Color(0xFF2D2B2C),
    surfaceContainerLow = Color(0xFF383637),
    surfaceContainer = Color(0xFF3C3A3B),
    surfaceContainerHigh = Color(0xFF403E3F),
    surfaceContainerHighest = Color(0xFF444243),
    inverseSurface = Color(0xFFF2EFE7),
    inverseOnSurface = Color(0xFF2B2825),
    inversePrimary = Color(0xFF9F4029),
    outline = Color(0xFF9A9288),
    outlineVariant = Color(0xFF4B4540),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000)
)

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF9F4029),
    onPrimary = Color(0xFFFFF9F1),
    primaryContainer = Color(0xFFF5D8CA),
    onPrimaryContainer = Color(0xFF542313),
    secondary = Color(0xFF655C53),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7DED3),
    onSecondaryContainer = Color(0xFF352B24),
    tertiary = Color(0xFF796044),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1DFC6),
    onTertiaryContainer = Color(0xFF302414),
    background = Color(0xFFF2EFE7),
    onBackground = Color(0xFF2B2825),
    surface = Color(0xFFF2EFE7),
    onSurface = Color(0xFF2B2825),
    surfaceVariant = Color(0xFFE4DFD7),
    onSurfaceVariant = Color(0xFF5D554D),
    surfaceTint = Color(0xFF9F4029),
    surfaceDim = Color(0xFFDAD8CE),
    surfaceBright = Color(0xFFFAF8F1),
    surfaceContainerLowest = Color(0xFFFFFDF7),
    surfaceContainerLow = Color(0xFFEEEBE2),
    surfaceContainer = Color(0xFFE8E5DB),
    surfaceContainerHigh = Color(0xFFE2DFD4),
    surfaceContainerHighest = Color(0xFFDBD8CC),
    inverseSurface = Color(0xFF2B2825),
    inverseOnSurface = Color(0xFFF2EFE7),
    inversePrimary = Color(0xFFD67856),
    outline = Color(0xFF81776D),
    outlineVariant = Color(0xFFCEC5BA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000)
)

private fun contrastingText(color: Color): Color =
    if (color.luminance() > 0.179f) Color.Black else Color.White

private fun customColorScheme(background: Color, accent: Color): androidx.compose.material3.ColorScheme {
    val foreground = contrastingText(background)
    val base = if (foreground == Color.White) DarkColorScheme else LightColorScheme
    val container = lerp(background, accent, 0.18f)
    return base.copy(
        primary = accent, onPrimary = contrastingText(accent),
        primaryContainer = container, onPrimaryContainer = contrastingText(container),
        secondary = accent, onSecondary = contrastingText(accent),
        secondaryContainer = container, onSecondaryContainer = contrastingText(container),
        tertiary = accent, onTertiary = contrastingText(accent),
        tertiaryContainer = container, onTertiaryContainer = contrastingText(container),
        background = background, onBackground = foreground,
        surface = background, onSurface = foreground, surfaceTint = accent,
        surfaceVariant = lerp(background, foreground, 0.08f),
        onSurfaceVariant = lerp(background, foreground, 0.75f),
        surfaceDim = background, surfaceBright = lerp(background, foreground, 0.12f),
        surfaceContainerLowest = background,
        surfaceContainerLow = lerp(background, foreground, 0.03f),
        surfaceContainer = lerp(background, foreground, 0.05f),
        surfaceContainerHigh = lerp(background, foreground, 0.08f),
        surfaceContainerHighest = lerp(background, foreground, 0.12f),
        inverseSurface = foreground, inverseOnSurface = background,
        inversePrimary = accent,
        outline = lerp(background, foreground, 0.5f),
        outlineVariant = lerp(background, foreground, 0.22f)
    )
}

internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun AppTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    customFont: FontFamily? = null,
    ligatures: Boolean = false,
    customBackground: Color = Color(0xFF101014),
    customAccent: Color = Color(0xFFA8C7FA),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val custom = themeMode == AppThemeMode.CUSTOM
    val darkTheme = when (themeMode) {
        AppThemeMode.DARK, AppThemeMode.SUMI -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.CUSTOM -> contrastingText(customBackground) == Color.White
    }

    val colorScheme = when {
        custom -> customColorScheme(customBackground, customAccent)
        themeMode == AppThemeMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == AppThemeMode.SUMI -> SumiColorScheme
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
            fontFeatureSettings = if (ligatures) "'tnum' 1, 'liga' 1, 'clig' 1, 'calt' 1"
                else "'tnum' 1, 'liga' 0, 'clig' 0, 'calt' 0"
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
    CompositionLocalProvider(
        LocalCustomTheme provides custom,
        LocalAppThemeMode provides themeMode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
