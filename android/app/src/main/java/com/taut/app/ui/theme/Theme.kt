package com.taut.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * TAUT Compose Theme (§10.3)
 *
 * Defaults to dark mode per §1.3 — rationale:
 * - Outdoor use (sun glare makes white screens unreadable)
 * - Battery savings on AMOLED phones
 * - Better contrast for outdoor readability
 * - Operators work in semi-outdoor environments (bank sampah kiosks)
 *
 * Light mode available via [⚙️ Atur] → [Tema Terang/Gelap].
 */
private val TautDarkColorScheme = darkColorScheme(
    background = BG_PRIMARY,
    surface = BG_SECONDARY,
    surfaceVariant = BG_TERTIARY,
    onBackground = TEXT_PRIMARY,
    onSurface = TEXT_PRIMARY,
    onSurfaceVariant = TEXT_SECONDARY,
    primary = ACTION_GREEN,
    onPrimary = TEXT_PRIMARY,
    secondary = ACTION_BLUE,
    onSecondary = TEXT_PRIMARY,
    tertiary = ACTION_YELLOW,
    error = ACTION_RED,
    onError = TEXT_PRIMARY,
    outline = DIVIDER_DARK,
    outlineVariant = TEXT_DISABLED
)

private val TautLightColorScheme = lightColorScheme(
    background = BG_PRIMARY_LIGHT,
    surface = BG_SECONDARY_LIGHT,
    surfaceVariant = BG_TERTIARY_LIGHT,
    onBackground = TEXT_PRIMARY_LIGHT,
    onSurface = TEXT_PRIMARY_LIGHT,
    onSurfaceVariant = TEXT_SECONDARY_LIGHT,
    primary = ACTION_GREEN,
    onPrimary = TEXT_PRIMARY,
    secondary = ACTION_BLUE,
    onSecondary = TEXT_PRIMARY,
    tertiary = ACTION_YELLOW,
    error = ACTION_RED,
    onError = TEXT_PRIMARY,
    outline = DIVIDER_LIGHT,
    outlineVariant = TEXT_DISABLED
)

@Composable
fun TautTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),  // Default follows system; app defaults dark
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TautDarkColorScheme else TautLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TautTypography,
        content = content
    )
}
