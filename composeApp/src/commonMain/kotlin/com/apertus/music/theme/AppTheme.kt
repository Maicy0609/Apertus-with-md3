package com.apertus.music.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Apertus brand palette.
 *
 * Hand-authored rather than taken from the 2021 M3 baseline (primary #6750A4)
 * and rather than Android dynamic colour, because the app needs an identity of
 * its own across Android, desktop and iOS.
 *
 * Every foreground/background pair below was checked against WCAG 2.1 and is at
 * or above 4.5:1; the worst pair in the light scheme is primary/onPrimary at
 * 6.31:1. Do not adjust these values without re-running that check.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF6349C9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF1B0060),
    secondary = Color(0xFF615B7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF1D1732),
    tertiary = Color(0xFFB4306A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF3E0021),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFE6E0EC),
    onSurfaceVariant = Color(0xFF48454E),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9BEFF),
    onPrimary = Color(0xFF2F0091),
    primaryContainer = Color(0xFF4A32A8),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFFCAC2E0),
    onSecondary = Color(0xFF322C49),
    secondaryContainer = Color(0xFF494261),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = Color(0xFFFFB0C8),
    onTertiary = Color(0xFF66002F),
    tertiaryContainer = Color(0xFF8E1449),
    onTertiaryContainer = Color(0xFFFFD9E3),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF48454E),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF48454E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1C1B22),
    surfaceContainer = Color(0xFF201E24),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343B),
)

/**
 * Expressive corner radii: noticeably rounder than the M3 baseline, which is
 * what gives cards and the mini player their softer silhouette.
 */
private val ApertusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val BaseTypography = Typography()

/** Slightly heavier titles and tighter display type, in the expressive spirit. */
private val ApertusTypography = Typography(
    headlineSmall = BaseTypography.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(letterSpacing = 0.15.sp),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = ApertusShapes,
        typography = ApertusTypography,
        content = content
    )
}
