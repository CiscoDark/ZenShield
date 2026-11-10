package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.FocusProfile

data class FocusThemePreferences(
    val followSystemWallpaper: Boolean = true,
    val profileTintedAccent: Boolean = true,
    val amoledPureBlack: Boolean = false
)

/**
 * Material 3 Expressive Shape Scale:
 * Sculpted squircles through generous pill-like radii that pair with dynamic corner morphing.
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp)
)

/**
 * Material 3 Expressive Motion Tokens:
 * Physics-based bouncy springs for spatial morphing (corner radius, scale, pill expansion)
 * and smooth critically-damped springs for color/tonal transitions.
 */
object ExpressiveMotion {
    fun <T> bouncySpatialSpring() = spring<T>(
        dampingRatio = 0.62f,
        stiffness = 380f
    )

    fun <T> snappySpatialSpring() = spring<T>(
        dampingRatio = 0.76f,
        stiffness = 560f
    )

    fun <T> smoothColorSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

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

private val CinemaDarkColorScheme = DefaultZenDarkColorScheme.copy(
    primary = CinemaPrimaryDark,
    onPrimary = CinemaOnPrimaryDark,
    primaryContainer = CinemaPrimaryContainerDark,
    onPrimaryContainer = CinemaOnPrimaryContainerDark,
    secondary = CinemaSecondaryDark,
    tertiary = CinemaTertiaryDark
)

// Profile tailored light color schemes
private val DndLightColorScheme = DefaultZenLightColorScheme.copy(
    primary = Color(0xFFB24A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF3B1400),
    secondary = Color(0xFF755846),
    tertiary = Color(0xFF8C4A48)
)

private val GamingLightColorScheme = DefaultZenLightColorScheme.copy(
    primary = Color(0xFF7B1FA2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4D9FF),
    onPrimaryContainer = Color(0xFF310047),
    secondary = Color(0xFF68586B),
    tertiary = Color(0xFF006874)
)

private val WorkLightColorScheme = DefaultZenLightColorScheme.copy(
    primary = Color(0xFF006495),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6FF),
    onPrimaryContainer = Color(0xFF001E30),
    secondary = Color(0xFF50606F),
    tertiary = Color(0xFF006B60)
)

private val SleepLightColorScheme = DefaultZenLightColorScheme.copy(
    primary = Color(0xFF3F4DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF081662),
    secondary = Color(0xFF5B5D72),
    tertiary = Color(0xFF6B538C)
)

private val CinemaLightColorScheme = DefaultZenLightColorScheme.copy(
    primary = Color(0xFF7D5700),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA5),
    onPrimaryContainer = Color(0xFF271900),
    secondary = Color(0xFF6E5C40),
    tertiary = Color(0xFF904A45)
)

private fun getProfileColorScheme(profile: FocusProfile, darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        when (profile) {
            FocusProfile.DO_NOT_DISTURB -> DndDarkColorScheme
            FocusProfile.GAMING -> GamingDarkColorScheme
            FocusProfile.WORK -> WorkDarkColorScheme
            FocusProfile.SLEEP -> SleepDarkColorScheme
            FocusProfile.CINEMA -> CinemaDarkColorScheme
            FocusProfile.OFF -> DefaultZenDarkColorScheme
        }
    } else {
        when (profile) {
            FocusProfile.DO_NOT_DISTURB -> DndLightColorScheme
            FocusProfile.GAMING -> GamingLightColorScheme
            FocusProfile.WORK -> WorkLightColorScheme
            FocusProfile.SLEEP -> SleepLightColorScheme
            FocusProfile.CINEMA -> CinemaLightColorScheme
            FocusProfile.OFF -> DefaultZenLightColorScheme
        }
    }
}

/**
 * Harmonizes Android 12+ wallpaper-derived dynamic color surfaces with the active
 * Focus Profile's signature accent palette.
 */
fun harmonizeDynamicWithProfile(
    wallpaperScheme: ColorScheme,
    profileScheme: ColorScheme,
    darkTheme: Boolean
): ColorScheme {
    val accentBlend = 0.76f
    val containerBlend = 0.68f
    val surfaceHarmonize = if (darkTheme) 0.16f else 0.12f

    return wallpaperScheme.copy(
        primary = lerp(wallpaperScheme.primary, profileScheme.primary, accentBlend),
        onPrimary = profileScheme.onPrimary,
        primaryContainer = lerp(wallpaperScheme.primaryContainer, profileScheme.primaryContainer, containerBlend),
        onPrimaryContainer = profileScheme.onPrimaryContainer,
        secondary = lerp(wallpaperScheme.secondary, profileScheme.secondary, accentBlend),
        secondaryContainer = lerp(wallpaperScheme.secondaryContainer, profileScheme.primaryContainer, 0.45f),
        tertiary = lerp(wallpaperScheme.tertiary, profileScheme.tertiary, accentBlend),
        background = lerp(wallpaperScheme.background, profileScheme.background, surfaceHarmonize),
        surface = lerp(wallpaperScheme.surface, profileScheme.surface, surfaceHarmonize),
        surfaceVariant = lerp(wallpaperScheme.surfaceVariant, profileScheme.primaryContainer, surfaceHarmonize),
        surfaceContainerLowest = lerp(wallpaperScheme.surfaceContainerLowest, profileScheme.background, surfaceHarmonize),
        surfaceContainerLow = lerp(wallpaperScheme.surfaceContainerLow, profileScheme.primaryContainer, surfaceHarmonize * 0.7f),
        surfaceContainer = lerp(wallpaperScheme.surfaceContainer, profileScheme.primaryContainer, surfaceHarmonize * 0.9f),
        surfaceContainerHigh = lerp(wallpaperScheme.surfaceContainerHigh, profileScheme.primaryContainer, surfaceHarmonize * 1.1f),
        surfaceContainerHighest = lerp(wallpaperScheme.surfaceContainerHighest, profileScheme.primaryContainer, surfaceHarmonize * 1.3f)
    )
}

@Composable
fun animateHarmonizedColorScheme(target: ColorScheme): ColorScheme {
    val spec = ExpressiveMotion.smoothColorSpring<Color>()
    val primary by animateColorAsState(target.primary, spec, label = "cs_primary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "cs_onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "cs_primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "cs_onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, spec, label = "cs_secondary")
    val onSecondary by animateColorAsState(target.onSecondary, spec, label = "cs_onSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, spec, label = "cs_secondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, spec, label = "cs_onSecondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, spec, label = "cs_tertiary")
    val onTertiary by animateColorAsState(target.onTertiary, spec, label = "cs_onTertiary")
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, spec, label = "cs_tertiaryContainer")
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, spec, label = "cs_onTertiaryContainer")
    val background by animateColorAsState(target.background, spec, label = "cs_background")
    val onBackground by animateColorAsState(target.onBackground, spec, label = "cs_onBackground")
    val surface by animateColorAsState(target.surface, spec, label = "cs_surface")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "cs_onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "cs_surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "cs_onSurfaceVariant")
    val surfaceContainerLowest by animateColorAsState(target.surfaceContainerLowest, spec, label = "cs_sclowest")
    val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, spec, label = "cs_sclow")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, spec, label = "cs_sc")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, spec, label = "cs_schigh")
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, spec, label = "cs_schighest")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest
    )
}

/**
 * DynamicTheme: Core Material 3 Expressive dynamic color theme wrapper.
 * On Android 12+ (API 31+), it dynamically extracts and applies the user's personal wallpaper
 * palette via dynamicDarkColorScheme and dynamicLightColorScheme, and harmonizes it with the
 * active Focus Profile's signature accent palette when profileTintedAccent is enabled.
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
    val supportsWallpaperDynamic = dynamicColor && preferences.followSystemWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val baseColorScheme: ColorScheme = when {
        // 1. Both Wallpaper Dynamic Colors and Profile-Tinted Accents enabled -> Harmonize both!
        supportsWallpaperDynamic && preferences.profileTintedAccent && activeProfile != FocusProfile.OFF -> {
            val wallpaperScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            val profileScheme = getProfileColorScheme(activeProfile, darkTheme)
            harmonizeDynamicWithProfile(wallpaperScheme, profileScheme, darkTheme)
        }

        // 2. Follow System Wallpaper (Material You Dynamic Colors on Android 12+)
        supportsWallpaperDynamic -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // 3. Profile-Tinted Accent Mode when dynamic wallpaper colors are disabled or pre-Android 12
        preferences.profileTintedAccent && activeProfile != FocusProfile.OFF -> {
            getProfileColorScheme(activeProfile, darkTheme)
        }

        // 4. Fallback Theme
        darkTheme -> DefaultZenDarkColorScheme
        else -> DefaultZenLightColorScheme
    }

    // 5. AMOLED Pure Black Dark Mode (preserves M3 primary/secondary/tertiary palettes while setting pure black backgrounds)
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

    val animatedColorScheme = animateHarmonizedColorScheme(finalColorScheme)

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
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
