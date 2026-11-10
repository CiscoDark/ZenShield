package com.example.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.services.GamingModeTileService
import com.example.services.QuickSettingsTileSync
import com.example.services.SleepModeTileService
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.ExpressiveMotion

/**
 * Interactive Material 3 Quick Settings Tiles Manager Card.
 * Allows users to:
 * 1. Add the custom Gaming Mode and Sleep Mode Quick Settings tiles directly to the system
 *    notification shade via Android 13+ StatusBarManager.requestAddTileService.
 * 2. Toggle Gaming Mode or Sleep Mode directly via live M3 Quick Settings tile pills.
 */
@Composable
fun QuickSettingsTilesCard(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    val isGamingActive = state.activeProfile == FocusProfile.GAMING
    val isSleepActive = state.activeProfile == FocusProfile.SLEEP

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_settings_tiles_card"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.DashboardCustomize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Notification Shade Quick Tiles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Toggle Gaming Mode or Sleep Mode directly from your system shade without opening Sentinal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Interactive System Shade Tile Pills (Gaming Mode + Sleep Mode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickSettingsShadeTilePill(
                    title = "Gaming Mode",
                    subtitle = if (isGamingActive) "Calls & Alerts Shielded" else "Off • Tap to toggle",
                    icon = Icons.Rounded.SportsEsports,
                    isActive = isGamingActive,
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        val nextProfile = GamingModeTileService.computeToggledGamingProfile(state.activeProfile)
                        stateManager.setProfile(
                            profile = nextProfile,
                            duration = if (nextProfile == FocusProfile.GAMING) state.durationOption else DurationOption.UNTIL_TURNED_OFF
                        )
                        QuickSettingsTileSync.requestSyncAllTiles(context)
                    },
                    testTag = "qs_tile_preview_gaming",
                    modifier = Modifier.weight(1f)
                )

                QuickSettingsShadeTilePill(
                    title = "Sleep Mode",
                    subtitle = if (isSleepActive) "Bedtime Shield Active" else "Off • Tap to toggle",
                    icon = Icons.Rounded.Bedtime,
                    isActive = isSleepActive,
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        val nextProfile = SleepModeTileService.computeToggledSleepProfile(state.activeProfile)
                        stateManager.setProfile(
                            profile = nextProfile,
                            duration = if (nextProfile == FocusProfile.SLEEP) state.durationOption else DurationOption.UNTIL_TURNED_OFF
                        )
                        QuickSettingsTileSync.requestSyncAllTiles(context)
                    },
                    testTag = "qs_tile_preview_sleep",
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // One-Tap System Shade Tile Installers (Android 13+ StatusBarManager.requestAddTileService)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        requestAddCustomQuickTile(
                            context = context,
                            serviceClass = GamingModeTileService::class.java,
                            tileLabel = context.getString(R.string.tile_gaming_label),
                            iconRes = R.drawable.ic_qs_gaming_mode,
                            onResult = { message -> statusFeedback = message }
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_gaming_qs_tile_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Gaming Tile",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        requestAddCustomQuickTile(
                            context = context,
                            serviceClass = SleepModeTileService::class.java,
                            tileLabel = context.getString(R.string.tile_sleep_label),
                            iconRes = R.drawable.ic_qs_sleep_mode,
                            onResult = { message -> statusFeedback = message }
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_sleep_qs_tile_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Sleep Tile",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (statusFeedback != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qs_tile_status_feedback")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = statusFeedback ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSettingsShadeTilePill(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "qs_tile_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "qs_tile_fg"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isActive) 28.dp else 20.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "qs_tile_corner"
    )

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun requestAddCustomQuickTile(
    context: Context,
    serviceClass: Class<*>,
    tileLabel: String,
    iconRes: Int,
    onResult: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            if (statusBarManager != null) {
                val componentName = ComponentName(context, serviceClass)
                val icon = Icon.createWithResource(context, iconRes)
                statusBarManager.requestAddTileService(
                    componentName,
                    tileLabel,
                    icon,
                    ContextCompat.getMainExecutor(context)
                ) { resultCode ->
                    val msg = when (resultCode) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                            "$tileLabel tile added to your Quick Settings shade!"
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                            "$tileLabel tile is already in your Quick Settings shade."
                        else ->
                            "Swipe down your notification shade and tap Edit (pencil) to place the $tileLabel tile."
                    }
                    onResult(msg)
                }
                return
            }
        } catch (_: Exception) {
            // Fall through to manual guidance
        }
    }
    onResult("Swipe down the notification shade and tap Edit (pencil) to drag the '$tileLabel' tile into Quick Settings.")
}
