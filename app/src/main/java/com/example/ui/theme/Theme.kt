package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.core.FocusProfile

data class FocusThemePreferences(
    val followSystemWallpaper: Boolean = true,
    val profileTintedAccent: Boolean = true,
    val amoledPureBlack: Boolean = false
)

private val DefaultZenDarkColorScheme = darkColorScheme(
    primary = ZenPrimaryDark,
    onPrimary = ZenOnPrimaryDark,
    primaryContainer = ZenPrimaryContainerDark,
    onPrimaryContainer = ZenOnPrimaryContainerDark,
    secondary = ZenSecondaryDark,
    onSecondary = ZenOnSecondaryDark,
    secondaryContainer = ZenSecondaryContainerDark,
    onSecondaryContainer = ZenOnSecondaryContainerDark,
    tertiary = ZenTertiaryDark,
    onTertiary = ZenOnTertiaryDark,
    tertiaryContainer = ZenTertiaryContainerDark,
    onTertiaryContainer = ZenOnTertiaryContainerDark,
    background = ZenBackgroundDark,
    onBackground = ZenOnBackgroundDark,
    surface = ZenSurfaceDark,
    onSurface = ZenOnSurfaceDark
)

private val DefaultZenLightColorScheme = lightColorScheme(
    primary = ZenPrimaryLight,
    onPrimary = ZenOnPrimaryLight,
    primaryContainer = ZenPrimaryContainerLight,
    onPrimaryContainer = ZenOnPrimaryContainerLight,
    secondary = ZenSecondaryLight,
    onSecondary = ZenOnSecondaryLight,
    secondaryContainer = ZenSecondaryContainerLight,
    onSecondaryContainer = ZenOnSecondaryContainerLight,
    tertiary = ZenTertiaryLight,
    onTertiary = ZenOnTertiaryLight,
    tertiaryContainer = ZenTertiaryContainerLight,
    onTertiaryContainer = ZenOnTertiaryContainerLight,
    background = ZenBackgroundLight,
    onBackground = ZenOnBackgroundLight,
    surface = ZenSurfaceLight,
    onSurface = ZenOnSurfaceLight
)

// Profile tailored dark color schemes
private val DndDarkColorScheme = DefaultZenDarkColorScheme.copy(
    primary = DndPrimaryDark,
    onPrimary = DndOnPrimaryDark,
    primaryContainer = DndPrimaryContainerDark,
    onPrimaryContainer = DndOnPrimaryContainerDark,
    secondary = DndSecondaryDark,
    tertiary = DndTertiaryDark
)

private val GamingDarkColorScheme = DefaultZenDarkColorScheme.copy(
    primary = GamingPrimaryDark,
    onPrimary = GamingOnPrimaryDark,
    primaryContainer = GamingPrimaryContainerDark,
    onPrimaryContainer = GamingOnPrimaryContainerDark,
    secondary = GamingSecondaryDark,
    tertiary = GamingTertiaryDark
)

private val WorkDarkColorScheme = DefaultZenDarkColorScheme.copy(
    primary = WorkPrimaryDark,
    onPrimary = WorkOnPrimaryDark,
    primaryContainer = WorkPrimaryContainerDark,
    onPrimaryContainer = WorkOnPrimaryContainerDark,
    secondary = WorkSecondaryDark,
    tertiary = WorkTertiaryDark
)

private val SleepDarkColorScheme = DefaultZenDarkColorScheme.copy(
    primary = SleepPrimaryDark,
    onPrimary = SleepOnPrimaryDark,
    primaryContainer = SleepPrimaryContainerDark,
    onPrimaryContainer = SleepOnPrimaryContainerDark,
    secondary = SleepSecondaryDark,
    tertiary = SleepTertiaryDark
)

/**
 * DynamicTheme: Core Material 3 dynamic color theme wrapper for Material You design standards.
 * On Android 12+ (API 31+), it dynamically extracts and applies the user's personal wallpaper
 * palette via dynamicDarkColorScheme and dynamicLightColorScheme.
 */
@Composable
fun DynamicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    activeProfile: FocusProfile = FocusProfile.OFF,
    preferences: FocusThemePreferences = FocusThemePreferences(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val baseColorScheme: ColorScheme = when {
        // 1. Profile-Tinted Accent Mode when a profile is active
        preferences.profileTintedAccent && activeProfile != FocusProfile.OFF -> {
            when (activeProfile) {
                FocusProfile.DO_NOT_DISTURB -> DndDarkColorScheme
                FocusProfile.GAMING -> GamingDarkColorScheme
                FocusProfile.WORK -> WorkDarkColorScheme
                FocusProfile.SLEEP -> SleepDarkColorScheme
                FocusProfile.OFF -> if (darkTheme) DefaultZenDarkColorScheme else DefaultZenLightColorScheme
            }
        }

        // 2. Follow System Wallpaper (Material You Dynamic Colors on Android 12+)
        dynamicColor && preferences.followSystemWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // 3. Fallback Theme
        darkTheme -> DefaultZenDarkColorScheme
        else -> DefaultZenLightColorScheme
    }

    // 4. AMOLED Pure Black Dark Mode (preserves M3 primary/secondary/tertiary palettes while setting pure black backgrounds)
    val finalColorScheme = if (darkTheme && preferences.amoledPureBlack) {
        baseColorScheme.copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF070A0F),
            surfaceContainer = Color(0xFF0F141E),
            surfaceContainerHigh = Color(0xFF171E2B),
            surfaceContainerHighest = Color(0xFF1F2838),
            surfaceDim = Color.Black
        )
    } else {
        baseColorScheme
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun SentinalTheme(
    activeProfile: FocusProfile = FocusProfile.OFF,
    preferences: FocusThemePreferences = FocusThemePreferences(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = preferences.followSystemWallpaper,
    content: @Composable () -> Unit
) {
    DynamicTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        activeProfile = activeProfile,
        preferences = preferences,
        content = content
    )
}

@Composable
fun ZenShieldTheme(
    activeProfile: FocusProfile = FocusProfile.OFF,
    preferences: FocusThemePreferences = FocusThemePreferences(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    DynamicTheme(
        darkTheme = darkTheme,
        dynamicColor = preferences.followSystemWallpaper,
        activeProfile = activeProfile,
        preferences = preferences,
        content = content
    )
}

// Backwards-compatible alias for template references
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    DynamicTheme(darkTheme = darkTheme, content = content)
}
