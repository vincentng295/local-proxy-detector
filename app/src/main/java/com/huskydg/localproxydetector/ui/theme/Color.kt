package com.huskydg.localproxydetector.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Tokens that Material 3's ColorScheme has no slot for (success / warning / surface tiers). */
@Immutable
data class ExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surface4: Color,
    val isDark: Boolean,
)

// ---- Dark (same tokens as Magic V2Ray's style.css) ----
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF78DC77),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF005313),
    onPrimaryContainer = Color(0xFF94F990),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B39),
    onSecondaryContainer = Color(0xFFD4E8CF),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF111411),
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    surfaceContainerLow = Color(0xFF1A1D19),
    surfaceContainer = Color(0xFF1E211D),
    surfaceContainerHigh = Color(0xFF232622),
    surfaceContainerHighest = Color(0xFF282B27),
)

val DarkExtended = ExtendedColors(
    success = Color(0xFF6DD58C),
    successContainer = Color(0xFF0A3818),
    onSuccessContainer = Color(0xFF6DD58C),
    warning = Color(0xFFFFC457),
    surface1 = Color(0xFF1A1D19),
    surface2 = Color(0xFF1E211D),
    surface3 = Color(0xFF232622),
    surface4 = Color(0xFF252824),
    isDark = true,
)

// ---- Light (M3 green tonal palette) ----
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006E1C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF94F990),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF526350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31101D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FBF1),
    onBackground = Color(0xFF191D17),
    surface = Color(0xFFF7FBF1),
    onSurface = Color(0xFF191D17),
    surfaceVariant = Color(0xFFDEE5D9),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    surfaceContainerLow = Color(0xFFF1F5EC),
    surfaceContainer = Color(0xFFECF0E6),
    surfaceContainerHigh = Color(0xFFE6EAE1),
    surfaceContainerHighest = Color(0xFFE0E4DB),
)

val LightExtended = ExtendedColors(
    success = Color(0xFF1B7A3D),
    successContainer = Color(0xFFBFEFC9),
    onSuccessContainer = Color(0xFF00210D),
    warning = Color(0xFF9A6700),
    surface1 = Color(0xFFF1F5EC),
    surface2 = Color(0xFFECF0E6),
    surface3 = Color(0xFFE6EAE1),
    surface4 = Color(0xFFE0E4DB),
    isDark = false,
)
