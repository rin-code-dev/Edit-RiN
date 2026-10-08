package com.hikariatelier.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val bright = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (bright + 0.05f) / (dark + 0.05f)
    }

    private fun verifyReadableRoles(c: ColorScheme) {
        val textPairs = listOf(
            Triple("primary", c.onPrimary, c.primary),
            Triple("primary container", c.onPrimaryContainer, c.primaryContainer),
            Triple("secondary", c.onSecondary, c.secondary),
            Triple("secondary container", c.onSecondaryContainer, c.secondaryContainer),
            Triple("tertiary", c.onTertiary, c.tertiary),
            Triple("tertiary container", c.onTertiaryContainer, c.tertiaryContainer),
            Triple("background", c.onBackground, c.background),
            Triple("surface", c.onSurface, c.surface),
            Triple("surface variant", c.onSurfaceVariant, c.surfaceVariant),
            Triple("inverse surface", c.inverseOnSurface, c.inverseSurface),
            Triple("error", c.onError, c.error),
            Triple("error container", c.onErrorContainer, c.errorContainer)
        )
        for ((name, text, fill) in textPairs) {
            assertTrue("$name text contrast: ${contrast(text, fill)}", contrast(text, fill) >= 4.5f)
        }
        val surfaces = listOf(c.surface, c.surfaceDim, c.surfaceBright, c.surfaceContainerLowest,
            c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest)
        for (surface in surfaces) {
            assertTrue("Main text on container", contrast(c.onSurface, surface) >= 4.5f)
            assertTrue("Secondary text on container", contrast(c.onSurfaceVariant, surface) >= 4.5f)
            assertTrue("Accent text on container", contrast(c.primary, surface) >= 4.5f)
        }
        assertTrue("Control outline", contrast(c.outline, c.surface) >= 3f)
        assertTrue("Inverse accent", contrast(c.inversePrimary, c.inverseSurface) >= 4.5f)
    }

    @Test fun lightThemeKeepsTextReadableAcrossSurfaces() = verifyReadableRoles(LightColorScheme)
    @Test fun sumiThemeKeepsTextReadableAcrossSurfaces() = verifyReadableRoles(SumiColorScheme)
}
