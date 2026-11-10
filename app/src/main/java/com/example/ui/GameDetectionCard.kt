package com.example.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.services.GameDetectorService
import com.example.ui.theme.ExpressiveMotion

/**
 * GameDetectionCard
 *
 * Material 3 Expressive Card displaying:
 * 1. Real-Time Game Launch Detector toggle & Usage Stats permission alert.
 * 2. Zero-Airtime VoIP Call Auto-Reply Engine status & telemetry overview.
 * 3. Custom Game Library management for emulators and non-categorized games.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDetectionCard(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    onOpenAppPicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val hasUsageAccess = remember(state.autoGameDetectionEnabled, isExpanded) {
        GameDetectorService.hasUsageStatsPermission(context)
    }
    val hasOverlayPermission = remember(isExpanded, state.pendingGameCountdownSeconds) {
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(context)
    }

    val cardCorner by animateDpAsState(
        targetValue = if (state.autoGameDetectionEnabled) 30.dp else 22.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "game_card_corner"
    )
    val emblemCorner by animateDpAsState(
        targetValue = if (state.autoGameDetectionEnabled) 22.dp else 14.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "game_emblem_corner"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("game_detection_card"),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(emblemCorner),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Auto-Activate Game DND on Launch",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subtitleText = when {
                            !state.autoGameDetectionEnabled -> "Auto-activation on launch disabled"
                            state.pendingGameCountdownSeconds != null -> "🎮 Asking to activate for ${state.pendingGameActivationLabel ?: "Game"} (${state.pendingGameCountdownSeconds}s)"
                            state.gameExitCountdownSeconds != null -> "⏳ Game exited/minimized • Deactivating in ${state.gameExitCountdownSeconds}s"
                            state.activeProfile == FocusProfile.GAMING -> "🎮 Game active • In Gaming DND lockdown (Auto-exits 15s after leaving game)"
                            state.askBeforeGameActivation -> "Asks via 5s pop-up on launch • Auto-deactivates 15s after exit"
                            else -> "Auto-activates on launch • Auto-deactivates 15s after exit"
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                state.pendingGameCountdownSeconds != null -> MaterialTheme.colorScheme.primary
                                state.gameExitCountdownSeconds != null -> MaterialTheme.colorScheme.error
                                state.activeProfile == FocusProfile.GAMING -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Switch(
                        checked = state.autoGameDetectionEnabled,
                        onCheckedChange = { enabled ->
                            stateManager.setAutoGameDetectionEnabled(enabled)
                        },
                        modifier = Modifier.testTag("toggle_auto_game_detection")
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse Game Detection Details" else "Expand Game Detection Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = ExpressiveMotion.bouncySpatialSpring()) + fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()),
                exit = shrinkVertically(animationSpec = ExpressiveMotion.snappySpatialSpring()) + fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 15s Exit Countdown Banner if game was minimized/exited
                    if (state.gameExitCountdownSeconds != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().testTag("game_exit_countdown_banner")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Game exited: deactivating in ${state.gameExitCountdownSeconds}s",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                TextButton(
                                    onClick = { stateManager.handleGameExited() },
                                    modifier = Modifier.testTag("deactivate_now_btn")
                                ) {
                                    Text(
                                        text = "Deactivate Now",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Dedicated Auto-Activation Confirmation Pop-up Section (Activate / Not Now + 5s Countdown)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ask Before Activating (5s Countdown)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Shows an Activate / Not Now pop-up when a game launches and auto-engages after 5s if untouched",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.askBeforeGameActivation,
                                    onCheckedChange = { enabled ->
                                        stateManager.setAskBeforeGameActivationEnabled(enabled)
                                        if (enabled && !GamingModePopupManager.hasOverlayPermission(context)) {
                                            GamingModePopupManager.requestOverlayPermission(context)
                                        }
                                    },
                                    modifier = Modifier.testTag("toggle_ask_before_game_activation")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (state.activeProfile == FocusProfile.GAMING) {
                                            stateManager.setProfile(FocusProfile.OFF)
                                        }
                                        stateManager.requestGameLaunchConfirmation(
                                            packageName = "com.activision.callofduty.shooter",
                                            appLabel = "Call of Duty Mobile",
                                            isManualPreview = true
                                        )
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("test_game_confirmation_popup_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SportsEsports,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (state.pendingGameCountdownSeconds != null) {
                                            "Prompt Active (${state.pendingGameCountdownSeconds}s)"
                                        } else {
                                            "Preview 5s Launch Pop-up"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Zero-Airtime VoIP Call Auto-Reply Engine Section
                    VoipZeroAirtimeSection(state = state)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Custom Game Packages Library
                    CustomGameLibrarySection(
                        customGames = state.customGamePackages,
                        onAddGameClicked = onOpenAppPicker,
                        onRemoveGame = { pkg -> stateManager.removeCustomGamePackage(pkg) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoipZeroAirtimeSection(
    state: FocusSessionState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Zero-Airtime VoIP Call Auto-Reply Engine",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = "When incoming VoIP calls (WhatsApp, WhatsApp Business, Telegram, Discord, Messenger) are auto-declined, Sentinal sends the status reply natively through OS RemoteInput in-app chat. Zero carrier charges or airtime used.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPillTag(
                label = "VoIP Decline",
                status = "Active",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )

            StatusPillTag(
                label = "Auto-Exit",
                status = "15s Grace",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )

            StatusPillTag(
                label = "In-App Reply",
                status = "RemoteInput",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusPillTag(
    label: String,
    status: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomGameLibrarySection(
    customGames: Set<String>,
    onAddGameClicked: () -> Unit,
    onRemoveGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Custom Games Library",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${customGames.size} custom apps treated as games (emulators, non-store games)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onAddGameClicked,
                shape = CircleShape,
                modifier = Modifier.testTag("add_custom_game_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add Game",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (customGames.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Standard Android games are recognized automatically. Use 'Add Game' to include emulators or unlisted titles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                customGames.forEach { pkg ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveGame(pkg) },
                        shape = CircleShape,
                        label = {
                            Text(
                                text = pkg.substringAfterLast('.'),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove $pkg",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = null
                    )
                }
            }
        }
    }
}
