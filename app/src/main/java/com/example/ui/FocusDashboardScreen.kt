package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonePaused
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import kotlinx.coroutines.delay

import androidx.compose.material.icons.filled.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusDashboardScreen(
    stateManager: FocusStateManager,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by stateManager.state.collectAsState()
    val context = LocalContext.current
    val hasDndPermission = stateManager.isNotificationPolicyAccessGranted()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusPill(
                            isActive = state.activeProfile != FocusProfile.OFF,
                            activeProfile = state.activeProfile
                        )
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HeroFocusCard(
                    state = state,
                    onToggleActive = {
                        if (state.activeProfile == FocusProfile.OFF) {
                            stateManager.setProfile(FocusProfile.DO_NOT_DISTURB, state.durationOption)
                        } else {
                            stateManager.setProfile(FocusProfile.OFF)
                        }
                    }
                )
            }

            if (!hasDndPermission) {
                item {
                    PermissionAlertCard(
                        onGrantClicked = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            }
                        }
                    )
                }
            }

            item {
                Text(
                    text = "Focus Profiles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                ProfileSelectorList(
                    activeProfile = state.activeProfile,
                    onProfileSelected = { profile ->
                        stateManager.setProfile(profile, state.durationOption)
                    }
                )
            }

            item {
                Text(
                    text = "Session Duration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                DurationSelectorChips(
                    selectedDuration = state.durationOption,
                    onDurationSelected = { duration ->
                        stateManager.setDuration(duration)
                    }
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("theme_settings_navigation_card")
                        .clickable { onNavigateToSettings() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Theme Engine & Preferences",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Dynamic Colors, AMOLED Pure Black, VoIP & Auto-Reply rules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Open Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Lock Screen & Security Shield",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                LockScreenSettingsCard(
                    state = state,
                    onDimLockScreenToggled = { enabled ->
                        stateManager.setDimLockScreenEnabled(enabled)
                    },
                    onStrictGamingToggled = { enabled ->
                        stateManager.setStrictGamingLockdownEnabled(enabled)
                    },
                    onAllowRepeatToggled = { enabled ->
                        stateManager.setAllowRepeatCallersEnabled(enabled)
                    },
                    onProfileSettingsChanged = { starredOnly, blockRepeated, silenceNotifs ->
                        stateManager.updateProfileSettings(starredOnly, blockRepeated, silenceNotifs)
                    }
                )
            }

            item {
                WeeklyShieldStatsCard(stateManager = stateManager)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StatusPill(
    isActive: Boolean,
    activeProfile: FocusProfile,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        label = "pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pill_fg"
    )

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        modifier = modifier
            .padding(end = 12.dp)
            .height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Color(0xFF38EF7D) else Color.Gray)
            )
            Text(
                text = if (isActive) activeProfile.displayName else "Inactive",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
fun HeroFocusCard(
    state: FocusSessionState,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = state.activeProfile != FocusProfile.OFF

    var remainingSeconds by remember(state.expiresAtMillis) {
        mutableLongStateOf(
            state.expiresAtMillis?.let {
                maxOf(0L, (it - System.currentTimeMillis()) / 1000L)
            } ?: 0L
        )
    }

    LaunchedEffect(state.expiresAtMillis, isActive) {
        if (isActive && state.expiresAtMillis != null) {
            while (true) {
                val rem = maxOf(0L, (state.expiresAtMillis - System.currentTimeMillis()) / 1000L)
                remainingSeconds = rem
                if (rem <= 0L) break
                delay(1000L)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_focus_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = state.activeProfile.icon,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (isActive) state.activeProfile.displayName else "Sentinal Inactive",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isActive) state.activeProfile.subtitle else "Select a focus profile below to activate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isActive) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (state.expiresAtMillis != null) {
                                    val minutes = remainingSeconds / 60
                                    val seconds = remainingSeconds % 60
                                    String.format("%02d:%02d remaining", minutes, seconds)
                                } else {
                                    "Until turned off"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (state.dimLockScreenEnabled) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Wallpaper,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Slate Lock Dim",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onToggleActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("toggle_focus_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isActive) "Turn Off Focus Shield" else "Activate Focus Shield",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ProfileSelectorList(
    activeProfile: FocusProfile,
    onProfileSelected: (FocusProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FocusProfile.entries.forEach { profile ->
            val isSelected = activeProfile == profile
            val testTag = "profile_button_${profile.id.lowercase()}"

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
                    .clickable { onProfileSelected(profile) },
                shape = RoundedCornerShape(18.dp),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = profile.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = profile.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DurationSelectorChips(
    selectedDuration: DurationOption,
    onDurationSelected: (DurationOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("duration_selector"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DurationOption.entries.forEach { option ->
            val isSelected = selectedDuration == option
            FilterChip(
                selected = isSelected,
                onClick = { onDurationSelected(option) },
                label = {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ThemePreferencesCard(
    preferences: com.example.ui.theme.FocusThemePreferences,
    onPreferencesChanged: (com.example.ui.theme.FocusThemePreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemeToggleRow(
                icon = Icons.Filled.Wallpaper,
                title = stringResource(R.string.theme_follow_system),
                subtitle = "Uses Android 12+ wallpaper colors dynamically",
                checked = preferences.followSystemWallpaper,
                onCheckedChange = { checked ->
                    onPreferencesChanged(preferences.copy(followSystemWallpaper = checked))
                },
                testTag = "toggle_dynamic_theme"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ThemeToggleRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.theme_profile_tint),
                subtitle = "Subtle tonal shifts when Gaming, Sleep, Work, or DND is active",
                checked = preferences.profileTintedAccent,
                onCheckedChange = { checked ->
                    onPreferencesChanged(preferences.copy(profileTintedAccent = checked))
                },
                testTag = "toggle_profile_tint"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ThemeToggleRow(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.theme_amoled_black),
                subtitle = "Pure black background & surface preserving dynamic accents",
                checked = preferences.amoledPureBlack,
                onCheckedChange = { checked ->
                    onPreferencesChanged(preferences.copy(amoledPureBlack = checked))
                },
                testTag = "toggle_amoled_black"
            )
        }
    }
}

@Composable
fun ThemeToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun LockScreenSettingsCard(
    state: FocusSessionState,
    onDimLockScreenToggled: (Boolean) -> Unit,
    onStrictGamingToggled: (Boolean) -> Unit,
    onAllowRepeatToggled: (Boolean) -> Unit,
    onProfileSettingsChanged: (Boolean, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemeToggleRow(
                icon = Icons.Filled.Wallpaper,
                title = stringResource(R.string.dim_lock_screen),
                subtitle = stringResource(R.string.lockscreen_wallpaper_desc),
                checked = state.dimLockScreenEnabled,
                onCheckedChange = onDimLockScreenToggled,
                testTag = "toggle_dim_lock_screen"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ThemeToggleRow(
                icon = Icons.Filled.PhonePaused,
                title = "Starred Contacts Bypass",
                subtitle = "Call screening passes priority callers, shields spam",
                checked = state.allowStarredContactsOnly,
                onCheckedChange = { checked ->
                    onProfileSettingsChanged(checked, state.blockRepeatedCallers, state.silenceNotifications)
                },
                testTag = "toggle_starred_only"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ThemeToggleRow(
                icon = Icons.Filled.Timer,
                title = "3-Minute Repeat Breakthrough",
                subtitle = "Second call within 180 seconds rings out loud for emergencies",
                checked = state.allowRepeatCallersEnabled,
                onCheckedChange = onAllowRepeatToggled,
                testTag = "toggle_repeat_breakthrough"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ThemeToggleRow(
                icon = Icons.Filled.Shield,
                title = "Strict Gaming Lockdown",
                subtitle = "Block even starred contacts during gaming so play is never interrupted",
                checked = state.strictGamingLockdownEnabled,
                onCheckedChange = onStrictGamingToggled,
                testTag = "toggle_gaming_lockdown"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ThemeToggleRow(
                icon = Icons.Filled.NotificationsOff,
                title = "Silence Distractions",
                subtitle = "Suppress banner alerts while focus shield is engaged",
                checked = state.silenceNotifications,
                onCheckedChange = { checked ->
                    onProfileSettingsChanged(state.allowStarredContactsOnly, state.blockRepeatedCallers, checked)
                },
                testTag = "toggle_silence_notifs"
            )
        }
    }
}

@Composable
fun PermissionAlertCard(
    onGrantClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_alert_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permission_dnd_required),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Grant notification policy access to allow Sentinal to toggle system Do Not Disturb automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }

            Button(
                onClick = onGrantClicked,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TelemetryOverviewCard(
    shieldedCalls: Int,
    shieldedNotifications: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Shield Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryMetricBox(
                    count = shieldedCalls,
                    label = "Calls Screened",
                    modifier = Modifier.weight(1f)
                )

                TelemetryMetricBox(
                    count = shieldedNotifications,
                    label = "Alerts Shielded",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TelemetryMetricBox(
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
