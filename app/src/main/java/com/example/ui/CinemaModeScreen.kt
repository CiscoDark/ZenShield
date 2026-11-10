package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import com.example.ui.theme.CinemaOnPrimaryContainerDark
import com.example.ui.theme.CinemaPrimaryContainerDark
import com.example.ui.theme.CinemaSeedColor
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.ExpressiveMotion

/**
 * Data model representing a Cinema Mode film duration preset.
 */
data class CinemaDurationPreset(
    val label: String,
    val subtitle: String,
    val minutes: Int,
    val durationOption: DurationOption,
    val testTag: String
)

val CINEMA_DURATION_PRESETS = listOf(
    CinemaDurationPreset(
        label = "2h",
        subtitle = "Standard Film (120m)",
        minutes = 120,
        durationOption = DurationOption.FOR_TWO_HOURS,
        testTag = "cinema_preset_2h"
    ),
    CinemaDurationPreset(
        label = "2.5h",
        subtitle = "Blockbuster (150m)",
        minutes = 150,
        durationOption = DurationOption.FOR_TWO_AND_HALF_HOURS,
        testTag = "cinema_preset_2_5h"
    ),
    CinemaDurationPreset(
        label = "3h",
        subtitle = "Feature Epic (180m)",
        minutes = 180,
        durationOption = DurationOption.FOR_THREE_HOURS,
        testTag = "cinema_preset_3h"
    ),
    CinemaDurationPreset(
        label = "Until Off",
        subtitle = "Manual Cutoff",
        minutes = 0,
        durationOption = DurationOption.UNTIL_TURNED_OFF,
        testTag = "cinema_preset_until_off"
    )
)

/**
 * Standalone full-screen 'Cinema Mode' dashboard screen using Jetpack Compose.
 * Displays duration presets (2h, 2.5h, 3h, Until Off), a toggle for theater-specific
 * notification blocking, zero-vibration hardware silence, 95% theater blackout lockscreen,
 * and the theater auto-reply responder.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CinemaModeScreen(
    modifier: Modifier = Modifier,
    stateManager: FocusStateManager? = null,
    onBack: (() -> Unit)? = null,
    onCustomizeAutoReply: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedManager = remember(stateManager, context) {
        stateManager ?: FocusStateManager.getInstance(context)
    }
    val state by resolvedManager.state.collectAsStateWithLifecycle()
    val isCinemaActive = state.activeProfile == FocusProfile.CINEMA

    if (onBack != null) {
        BackHandler {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("cinema_mode_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = "Cinema Mode icon",
                            tint = CinemaSeedColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Cinema Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isCinemaActive) "THEATER SHIELD ENGAGED" else "THEATER DND & BLACKOUT CONFIG",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCinemaActive) CinemaSeedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                onBack()
                            },
                            modifier = Modifier.testTag("cinema_mode_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to Sentinel Dashboard"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                val remainingMs = state.expiresAtMillis?.let { maxOf(0L, it - System.currentTimeMillis()) } ?: 0L
                CinemaHeroBannerCard(
                    isCinemaActive = isCinemaActive,
                    selectedPresetMinutes = state.cinemaPresetMinutes,
                    remainingMillis = remainingMs,
                    onToggleCinemaMode = {
                        if (isCinemaActive) {
                            ExpressiveHaptics.playFocusDeactivated(context)
                            resolvedManager.setProfile(FocusProfile.OFF)
                        } else {
                            ExpressiveHaptics.playFocusActivated(context)
                            resolvedManager.activateCinemaMode(state.cinemaPresetMinutes)
                        }
                    }
                )
            }

            item {
                CinemaDurationPresetSection(
                    selectedPresetMinutes = state.cinemaPresetMinutes,
                    isCinemaActive = isCinemaActive,
                    onSelectPreset = { preset ->
                        ExpressiveHaptics.playSpringTap(context)
                        resolvedManager.setCinemaPresetMinutes(preset.minutes)
                        if (isCinemaActive) {
                            resolvedManager.activateCinemaMode(preset.minutes)
                        }
                    }
                )
            }

            item {
                CinemaTheaterControlsCard(
                    notificationBlockingEnabled = state.cinemaNotificationBlockingEnabled,
                    zeroVibrationEnabled = state.cinemaZeroVibrationEnabled,
                    extraDimEnabled = state.cinemaExtraDimEnabled,
                    onNotificationBlockingChanged = { enabled ->
                        ExpressiveHaptics.playSpringTap(context)
                        resolvedManager.setCinemaNotificationBlockingEnabled(enabled)
                    },
                    onZeroVibrationChanged = { enabled ->
                        ExpressiveHaptics.playSpringTap(context)
                        resolvedManager.setCinemaZeroVibrationEnabled(enabled)
                    },
                    onExtraDimChanged = { enabled ->
                        ExpressiveHaptics.playSpringTap(context)
                        resolvedManager.setCinemaExtraDimEnabled(enabled)
                    }
                )
            }

            item {
                CinemaAutoReplyPreviewCard(
                    autoReplyMessage = resolvedManager.getAutoReplyMessage(FocusProfile.CINEMA),
                    autoReplyEnabled = state.autoReplyEnabled,
                    onToggleAutoReply = { enabled ->
                        ExpressiveHaptics.playSpringTap(context)
                        resolvedManager.setAutoReplyEnabled(enabled)
                    },
                    onCustomizeAutoReply = onCustomizeAutoReply
                )
            }
        }
    }
}

/**
 * Embeddable 'Cinema Mode' dashboard card for Sentinel's Command & Rules tabs.
 * Exposes duration presets (2h, 2.5h, 3h, Until Off), the theater-specific notification
 * blocking toggle, zero-vibration & blackout indicators, and an instant activation CTA.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CinemaModeDashboardCard(
    modifier: Modifier = Modifier,
    stateManager: FocusStateManager? = null,
    onOpenFullScreen: (() -> Unit)? = null,
    onCustomizeAutoReply: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedManager = remember(stateManager, context) {
        stateManager ?: FocusStateManager.getInstance(context)
    }
    val state by resolvedManager.state.collectAsStateWithLifecycle()
    val isCinemaActive = state.activeProfile == FocusProfile.CINEMA

    val containerBg by animateColorAsState(
        targetValue = if (isCinemaActive) {
            CinemaPrimaryContainerDark.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "cinema_card_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isCinemaActive) {
            CinemaSeedColor
        } else {
            CinemaSeedColor.copy(alpha = 0.32f)
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "cinema_card_border"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(28.dp))
            .testTag("cinema_mode_dashboard_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        CinemaSeedColor.copy(alpha = 0.28f),
                                        CinemaPrimaryContainerDark
                                    )
                                )
                            )
                            .border(1.dp, CinemaSeedColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = "Cinema Mode",
                            tint = CinemaSeedColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Cinema Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isCinemaActive) CinemaSeedColor else CinemaSeedColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = if (isCinemaActive) "LIVE FILM" else "THEATER DND",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCinemaActive) Color(0xFF2B1700) else CinemaSeedColor
                                )
                            }
                        }
                        Text(
                            text = "Zero vibrations • 95% blackout • Film duration presets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onOpenFullScreen != null) {
                    IconButton(
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            onOpenFullScreen()
                        },
                        modifier = Modifier.testTag("cinema_open_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = "Open full Cinema Mode studio",
                            tint = CinemaSeedColor
                        )
                    }
                }
            }

            // Film Duration Presets (2h, 2.5h, 3h, Until Off)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FILM DURATION PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        color = CinemaSeedColor
                    )
                    Text(
                        text = when (state.cinemaPresetMinutes) {
                            120 -> "2h Standard Film"
                            150 -> "2.5h Blockbuster"
                            180 -> "3h Feature Epic"
                            else -> "Until Turned Off"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CINEMA_DURATION_PRESETS.forEach { preset ->
                        val isSelected = state.cinemaPresetMinutes == preset.minutes
                        CinemaPresetChip(
                            preset = preset,
                            isSelected = isSelected,
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                resolvedManager.setCinemaPresetMinutes(preset.minutes)
                                if (isCinemaActive) {
                                    resolvedManager.activateCinemaMode(preset.minutes)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Theater-Specific Notification Blocking Toggle Row
            CinemaSettingToggleRow(
                icon = Icons.Rounded.NotificationsOff,
                title = "Theater Notification Blocking",
                subtitle = "Suppress pop-ups, heads-up banners & wake-screen alerts during film",
                checked = state.cinemaNotificationBlockingEnabled,
                onCheckedChange = { enabled ->
                    ExpressiveHaptics.playSpringTap(context)
                    resolvedManager.setCinemaNotificationBlockingEnabled(enabled)
                },
                switchTestTag = "cinema_notification_blocking_toggle"
            )

            // Secondary compact toggles for Zero Vibrations & Extra Dim 95% Blackout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactCinemaFeaturePill(
                    icon = Icons.Rounded.Vibration,
                    label = "Zero Vibration",
                    enabled = state.cinemaZeroVibrationEnabled,
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        resolvedManager.setCinemaZeroVibrationEnabled(!state.cinemaZeroVibrationEnabled)
                    },
                    testTag = "cinema_zero_vibration_toggle",
                    modifier = Modifier.weight(1f)
                )
                CompactCinemaFeaturePill(
                    icon = Icons.Rounded.BrightnessLow,
                    label = "95% Blackout Dim",
                    enabled = state.cinemaExtraDimEnabled,
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        resolvedManager.setCinemaExtraDimEnabled(!state.cinemaExtraDimEnabled)
                    },
                    testTag = "cinema_extra_dim_toggle",
                    modifier = Modifier.weight(1f)
                )
            }

            // Action CTA Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (isCinemaActive) {
                            ExpressiveHaptics.playFocusDeactivated(context)
                            resolvedManager.setProfile(FocusProfile.OFF)
                        } else {
                            ExpressiveHaptics.playFocusActivated(context)
                            resolvedManager.activateCinemaMode(state.cinemaPresetMinutes)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cinema_mode_activate_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCinemaActive) MaterialTheme.colorScheme.errorContainer else CinemaSeedColor,
                        contentColor = if (isCinemaActive) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2B1700)
                    )
                ) {
                    Icon(
                        imageVector = if (isCinemaActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isCinemaActive) "End Cinema Mode" else "Start Cinema Mode",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCinemaActive) {
                            "End Cinema Shield"
                        } else {
                            val suffix = when (state.cinemaPresetMinutes) {
                                120 -> " (2h)"
                                150 -> " (2.5h)"
                                180 -> " (3h)"
                                else -> ""
                            }
                            "Start Cinema Mode$suffix"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (onCustomizeAutoReply != null) {
                    OutlinedButton(
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            onCustomizeAutoReply()
                        },
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("cinema_auto_reply_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CinemaSeedColor.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Message,
                            contentDescription = "Configure Theater Auto-Reply",
                            tint = CinemaSeedColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaHeroBannerCard(
    isCinemaActive: Boolean,
    selectedPresetMinutes: Int,
    remainingMillis: Long,
    onToggleCinemaMode: () -> Unit
) {
    val formattedRemaining = remember(remainingMillis) {
        if (remainingMillis <= 0L) {
            "UNTIL ENDED"
        } else {
            val totalSeconds = remainingMillis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = if (isCinemaActive) CinemaSeedColor else CinemaSeedColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCinemaActive) CinemaPrimaryContainerDark else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = CinemaSeedColor.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = CinemaSeedColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isCinemaActive) "AUDITORIUM LOCK ENGAGED" else "READY FOR SHOWTIME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = CinemaSeedColor
                        )
                    }
                }

                AnimatedVisibility(visible = isCinemaActive) {
                    Text(
                        text = formattedRemaining,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = CinemaOnPrimaryContainerDark
                    )
                }
            }

            Text(
                text = if (isCinemaActive) {
                    "Theater blackout is active. Physical vibrations and pop-up distractions are suppressed."
                } else {
                    "Engage total theater courtesy with one tap: zero motor vibration, silent ringer, 95% OLED lockscreen blackout, and automatic film duration cutoff."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onToggleCinemaMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("cinema_screen_primary_cta"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCinemaActive) MaterialTheme.colorScheme.errorContainer else CinemaSeedColor,
                    contentColor = if (isCinemaActive) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2B1700)
                )
            ) {
                Icon(
                    imageVector = if (isCinemaActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCinemaActive) {
                        "Deactivate Cinema Shield"
                    } else {
                        val presetText = when (selectedPresetMinutes) {
                            120 -> "2h Film Preset"
                            150 -> "2.5h Blockbuster Preset"
                            180 -> "3h Epic Preset"
                            else -> "Until Turned Off"
                        }
                        "Engage Cinema Shield • $presetText"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CinemaDurationPresetSection(
    selectedPresetMinutes: Int,
    isCinemaActive: Boolean,
    onSelectPreset: (CinemaDurationPreset) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = CinemaSeedColor,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Film Duration Presets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isCinemaActive) {
                            "Tap a preset to immediately adjust the remaining theater countdown"
                        } else {
                            "Choose how long Sentinel keeps your phone silent in the theater"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 2
            ) {
                CINEMA_DURATION_PRESETS.forEach { preset ->
                    val isSelected = selectedPresetMinutes == preset.minutes
                    CinemaPresetChip(
                        preset = preset,
                        isSelected = isSelected,
                        onClick = { onSelectPreset(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CinemaPresetChip(
    preset: CinemaDurationPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "preset_chip_scale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) {
            CinemaSeedColor.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "preset_chip_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) CinemaSeedColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "preset_chip_border"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(1.2.dp, borderColor, RoundedCornerShape(16.dp))
            .testTag(preset.testTag),
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) CinemaSeedColor else MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected preset",
                        tint = CinemaSeedColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CinemaTheaterControlsCard(
    notificationBlockingEnabled: Boolean,
    zeroVibrationEnabled: Boolean,
    extraDimEnabled: Boolean,
    onNotificationBlockingChanged: (Boolean) -> Unit,
    onZeroVibrationChanged: (Boolean) -> Unit,
    onExtraDimChanged: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Theater Protection & Courtesy Rules",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            CinemaSettingToggleRow(
                icon = Icons.Rounded.NotificationsOff,
                title = "Theater-Specific Notification Blocking",
                subtitle = "Intercept visual pop-ups, social pings, and lockscreen wake events during film playback",
                checked = notificationBlockingEnabled,
                onCheckedChange = onNotificationBlockingChanged,
                switchTestTag = "cinema_screen_notification_blocking_toggle"
            )

            CinemaSettingToggleRow(
                icon = Icons.Rounded.Vibration,
                title = "Zero Vibrations & Silent Audio",
                subtitle = "Suppress all haptic buzzes and physical motor ticks so your phone never hums in a quiet scene",
                checked = zeroVibrationEnabled,
                onCheckedChange = onZeroVibrationChanged,
                switchTestTag = "cinema_screen_zero_vibration_toggle"
            )

            CinemaSettingToggleRow(
                icon = Icons.Rounded.BrightnessLow,
                title = "Extra Dim 95% Blackout Lockscreen",
                subtitle = "Apply velvet-amber 95% dark overlay to prevent screen glare when checking the time",
                checked = extraDimEnabled,
                onCheckedChange = onExtraDimChanged,
                switchTestTag = "cinema_screen_extra_dim_toggle"
            )
        }
    }
}

@Composable
private fun CinemaSettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (checked) CinemaSeedColor.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) CinemaSeedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(switchTestTag),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2B1700),
                    checkedTrackColor = CinemaSeedColor
                )
            )
        }
    }
}

@Composable
private fun CompactCinemaFeaturePill(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (enabled) CinemaSeedColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) {
            CinemaSeedColor.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) CinemaSeedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) CinemaSeedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = if (enabled) "ENABLED" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CinemaAutoReplyPreviewCard(
    autoReplyMessage: String,
    autoReplyEnabled: Boolean,
    onToggleAutoReply: (Boolean) -> Unit,
    onCustomizeAutoReply: (() -> Unit)?
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Message,
                        contentDescription = null,
                        tint = CinemaSeedColor
                    )
                    Column {
                        Text(
                            text = "Theater Auto-Reply SMS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Replies automatically to callers & texts during your movie",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = autoReplyEnabled,
                    onCheckedChange = onToggleAutoReply,
                    modifier = Modifier.testTag("cinema_auto_reply_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF2B1700),
                        checkedTrackColor = CinemaSeedColor
                    )
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = autoReplyMessage,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (onCustomizeAutoReply != null) {
                OutlinedButton(
                    onClick = onCustomizeAutoReply,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CinemaSeedColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Edit Theater Auto-Reply Message",
                        color = CinemaSeedColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
