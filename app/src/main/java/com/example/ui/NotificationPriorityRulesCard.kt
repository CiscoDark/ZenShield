package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.CategorizedNotificationEvent
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.core.GlobalFocusRuleAction
import com.example.core.NotificationCategory
import com.example.core.NotificationPriorityLevel
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.JetBrainsMonoFontFamily

private enum class StreamActionFilter(val label: String, val matchingAction: GlobalFocusRuleAction?) {
    ALL("All Alerts", null),
    HIDDEN("Hidden", GlobalFocusRuleAction.HIDE_COMPLETELY),
    MUTED("Muted", GlobalFocusRuleAction.MUTE_SILENT),
    ALLOWED("Allowed", GlobalFocusRuleAction.ALLOW_ALL)
}

/**
 * NotificationPriorityRulesCard
 *
 * Comprehensive Material 3 Expressive Notification Management Component that:
 * 1. Categorizes incoming notifications into 6 semantic categories (Security & OTP, Direct Messages,
 *    Work & Calendar, Social & Community, News & Media, Promotions & Marketing).
 * 2. Allows users to assign each category to a user-defined Priority Level (CRITICAL, HIGH, MEDIUM, LOW).
 * 3. Allows users to configure Global Focus Rules per Priority Level to automatically Allow, Mute, or Hide alerts.
 * 4. Displays a live filterable stream of categorized notifications with a 1-tap category simulator.
 */
@Composable
fun NotificationPriorityRulesCard(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCategoryMapperExpanded by remember { mutableStateOf(true) }
    var isLiveStreamExpanded by remember { mutableStateOf(true) }
    var selectedStreamFilter by remember { mutableStateOf(StreamActionFilter.ALL) }

    val cardCorner by animateDpAsState(
        targetValue = if (state.globalFocusRulesEnabled) 30.dp else 22.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "notif_priority_card_corner"
    )

    val hiddenCount = state.categorizedNotifications.count { it.appliedAction == GlobalFocusRuleAction.HIDE_COMPLETELY }
    val mutedCount = state.categorizedNotifications.count { it.appliedAction == GlobalFocusRuleAction.MUTE_SILENT }
    val allowedCount = state.categorizedNotifications.count { it.appliedAction == GlobalFocusRuleAction.ALLOW_ALL }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("notification_priority_rules_card"),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            width = if (state.globalFocusRulesEnabled) 1.5.dp else 1.dp,
            color = if (state.globalFocusRulesEnabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Row: Emblem + Title + Active Posture Pill + Master Switch
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
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (state.globalFocusRulesEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.FilterList,
                                contentDescription = "Notification Categorizer & Global Focus Rules",
                                tint = if (state.globalFocusRulesEnabled) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Notification Priority & Focus Rules",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Auto-categorize incoming alerts & mute or hide by priority level",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = state.globalFocusRulesEnabled,
                    onCheckedChange = { enabled ->
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setGlobalFocusRulesEnabled(enabled)
                    },
                    modifier = Modifier.testTag("switch_global_focus_rules_enabled")
                )
            }

            // 2. KPI Summary & 24/7 Standby Enforcement Toggle Banner
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RuleSummaryStatTile(
                            label = "HIDDEN",
                            count = hiddenCount,
                            accentColor = Color(0xFFFF6B6B),
                            modifier = Modifier.weight(1f)
                        )
                        RuleSummaryStatTile(
                            label = "MUTED",
                            count = mutedCount,
                            accentColor = Color(0xFFFFB300),
                            modifier = Modifier.weight(1f)
                        )
                        RuleSummaryStatTile(
                            label = "ALLOWED",
                            count = allowedCount,
                            accentColor = Color(0xFF38EF7D),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ExpressiveHaptics.playSpringTap(context)
                                stateManager.setEnforceGlobalRulesWhenStandby(!state.enforceGlobalRulesWhenStandby)
                            }
                            .testTag("row_enforce_global_rules_standby"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enforce Global Rules 24/7 (Even When Shield OFF)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.enforceGlobalRulesWhenStandby) {
                                    "Active 24/7 • Automatically mutes/hides low-priority categories at all times"
                                } else {
                                    "Active during Focus • Rules apply whenever any Focus profile is active"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = state.enforceGlobalRulesWhenStandby,
                            onCheckedChange = { enabled ->
                                ExpressiveHaptics.playSpringTap(context)
                                stateManager.setEnforceGlobalRulesWhenStandby(enabled)
                            },
                            modifier = Modifier.testTag("switch_global_rules_always_on")
                        )
                    }
                }
            }

            // 3. Global Focus Rules by Priority Level (CRITICAL, HIGH, MEDIUM, LOW)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Global Priority Level Rules",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Quick Global Rule Presets
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlobalRulePresetChip(
                            label = "Balanced",
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                stateManager.applyGlobalFocusRulePreset("BALANCED")
                            },
                            testTag = "preset_global_rule_balanced"
                        )
                        GlobalRulePresetChip(
                            label = "Urgent Only",
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                stateManager.applyGlobalFocusRulePreset("URGENT_ONLY")
                            },
                            testTag = "preset_global_rule_urgent_only"
                        )
                        GlobalRulePresetChip(
                            label = "Lockdown",
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                stateManager.applyGlobalFocusRulePreset("STRICT")
                            },
                            testTag = "preset_global_rule_strict"
                        )
                    }
                }

                NotificationPriorityLevel.values().forEach { level ->
                    val currentAction = state.priorityLevelRules[level] ?: GlobalFocusRuleAction.MUTE_SILENT
                    PriorityLevelRuleRow(
                        level = level,
                        selectedAction = currentAction,
                        onActionSelected = { newAction ->
                            ExpressiveHaptics.playSpringTap(context)
                            stateManager.setPriorityLevelRule(level, newAction)
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 4. Incoming Notification Category Priority Mapper (6 Categories)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            ExpressiveHaptics.playSpringTap(context)
                            isCategoryMapperExpanded = !isCategoryMapperExpanded
                        }
                        .padding(vertical = 4.dp)
                        .testTag("btn_toggle_category_mapper"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Category Priority Assignment (6 Categories)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap a category's priority badge to cycle P1 Critical → P2 High → P3 Medium → P4 Low",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isCategoryMapperExpanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = if (isCategoryMapperExpanded) "Collapse categories" else "Expand categories",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = isCategoryMapperExpanded,
                    enter = expandVertically(animationSpec = ExpressiveMotion.bouncySpatialSpring()) +
                        fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()),
                    exit = shrinkVertically(animationSpec = ExpressiveMotion.snappySpatialSpring()) +
                        fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring())
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotificationCategory.values().forEach { category ->
                            val assignedPriority = state.categoryPriorityOverrides[category] ?: category.defaultPriority
                            val effectiveAction = state.priorityLevelRules[assignedPriority] ?: GlobalFocusRuleAction.MUTE_SILENT
                            val count = state.categoryInterceptCounts[category] ?: 0

                            CategoryPriorityRow(
                                category = category,
                                assignedPriority = assignedPriority,
                                effectiveAction = effectiveAction,
                                count = count,
                                onCyclePriority = {
                                    ExpressiveHaptics.playSpringTap(context)
                                    stateManager.cycleCategoryPriorityLevel(category)
                                },
                                onSimulateAlert = {
                                    stateManager.simulateCategorizedNotification(category)
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 5. Live Categorized Notification Stream & Filter Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            ExpressiveHaptics.playSpringTap(context)
                            isLiveStreamExpanded = !isLiveStreamExpanded
                        }
                        .padding(vertical = 4.dp)
                        .testTag("btn_toggle_categorized_stream"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Live Categorized Alert Stream",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Real-time classification & applied priority rule log",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = if (isLiveStreamExpanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = if (isLiveStreamExpanded) "Collapse stream" else "Expand stream",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = isLiveStreamExpanded,
                    enter = expandVertically(animationSpec = ExpressiveMotion.bouncySpatialSpring()) +
                        fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()),
                    exit = shrinkVertically(animationSpec = ExpressiveMotion.snappySpatialSpring()) +
                        fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring())
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Filter Chips Row (All, Hidden, Muted, Allowed)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StreamActionFilter.values().forEach { filter ->
                                val isSelected = selectedStreamFilter == filter
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            ExpressiveHaptics.playSpringTap(context)
                                            selectedStreamFilter = filter
                                        }
                                        .testTag("categorized_filter_${filter.name.lowercase()}")
                                ) {
                                    Text(
                                        text = filter.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        val filteredEvents = remember(state.categorizedNotifications, selectedStreamFilter) {
                            val targetAction = selectedStreamFilter.matchingAction
                            if (targetAction == null) {
                                state.categorizedNotifications.take(6)
                            } else {
                                state.categorizedNotifications.filter { it.appliedAction == targetAction }.take(6)
                            }
                        }

                        if (filteredEvents.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FilterList,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "No categorized alerts match '${selectedStreamFilter.label}' yet",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tap the ▶ Test button on any category above to simulate an incoming notification.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            filteredEvents.forEach { event ->
                                CategorizedNotificationRow(event = event)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleSummaryStatTile(
    label: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GlobalRulePresetChip(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PriorityLevelRuleRow(
    level: NotificationPriorityLevel,
    selectedAction: GlobalFocusRuleAction,
    onActionSelected: (GlobalFocusRuleAction) -> Unit
) {
    val levelColor = priorityLevelAccentColor(level)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, levelColor.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("priority_level_row_${level.name}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = levelColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, levelColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = level.badgeLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = levelColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = level.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Segmented 3-Option Action Selector: Allow / Mute / Hide
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlobalFocusRuleAction.values().forEach { action ->
                    val isSelected = selectedAction == action
                    val actionColor = ruleActionAccentColor(action)
                    val chipCorner by animateDpAsState(
                        targetValue = if (isSelected) 16.dp else 10.dp,
                        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                        label = "priority_action_corner_${level.name}_${action.name}"
                    )

                    Surface(
                        shape = RoundedCornerShape(chipCorner),
                        color = if (isSelected) {
                            actionColor.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) {
                                actionColor
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(chipCorner))
                            .clickable { onActionSelected(action) }
                            .testTag("priority_rule_chip_${level.name}_${action.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (action) {
                                    GlobalFocusRuleAction.ALLOW_ALL -> Icons.Rounded.NotificationsActive
                                    GlobalFocusRuleAction.MUTE_SILENT -> Icons.Rounded.NotificationsOff
                                    GlobalFocusRuleAction.HIDE_COMPLETELY -> Icons.Rounded.VisibilityOff
                                },
                                contentDescription = null,
                                tint = if (isSelected) actionColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = action.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) actionColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPriorityRow(
    category: NotificationCategory,
    assignedPriority: NotificationPriorityLevel,
    effectiveAction: GlobalFocusRuleAction,
    count: Int,
    onCyclePriority: () -> Unit,
    onSimulateAlert: () -> Unit
) {
    val priorityColor = priorityLevelAccentColor(assignedPriority)
    val actionColor = ruleActionAccentColor(effectiveAction)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_row_${category.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = priorityColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = category.title,
                            tint = priorityColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = category.shortTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "$count",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = category.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tappable Priority Assignment Chip (Cycles CRITICAL -> HIGH -> MEDIUM -> LOW)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = priorityColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, priorityColor.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCyclePriority() }
                        .testTag("category_priority_btn_${category.name}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = assignedPriority.label.uppercase(),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = priorityColor
                        )
                        Text(
                            text = effectiveAction.badgeText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = actionColor
                        )
                    }
                }

                // 1-Tap Simulate Incoming Alert Button for this Category
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onSimulateAlert() }
                        .testTag("btn_test_category_${category.name}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Simulate ${category.shortTitle} notification",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorizedNotificationRow(
    event: CategorizedNotificationEvent
) {
    val actionColor = ruleActionAccentColor(event.appliedAction)
    val priorityColor = priorityLevelAccentColor(event.priorityLevel)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, actionColor.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("categorized_event_row_${event.category.name}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(actionColor)
                    )
                    Text(
                        text = event.appName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "• ${event.timeLabel}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = priorityColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "${event.category.shortTitle} (${event.priorityLevel.label})",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = actionColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, actionColor.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = event.appliedAction.badgeText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = actionColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = event.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = event.previewText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun priorityLevelAccentColor(level: NotificationPriorityLevel): Color {
    return when (level) {
        NotificationPriorityLevel.CRITICAL -> Color(0xFF38EF7D)
        NotificationPriorityLevel.HIGH -> Color(0xFF60A5FA)
        NotificationPriorityLevel.MEDIUM -> Color(0xFFFFB300)
        NotificationPriorityLevel.LOW -> Color(0xFFF472B6)
    }
}

private fun ruleActionAccentColor(action: GlobalFocusRuleAction): Color {
    return when (action) {
        GlobalFocusRuleAction.ALLOW_ALL -> Color(0xFF38EF7D)
        GlobalFocusRuleAction.MUTE_SILENT -> Color(0xFFFFB300)
        GlobalFocusRuleAction.HIDE_COMPLETELY -> Color(0xFFFF6B6B)
    }
}

private fun categoryIcon(category: NotificationCategory): ImageVector {
    return when (category) {
        NotificationCategory.SECURITY_AND_URGENT -> Icons.Rounded.Security
        NotificationCategory.DIRECT_MESSAGES -> Icons.Rounded.ChatBubble
        NotificationCategory.WORK_AND_CALENDAR -> Icons.Rounded.Work
        NotificationCategory.SOCIAL_AND_COMMUNITY -> Icons.Rounded.Group
        NotificationCategory.NEWS_AND_MEDIA -> Icons.Rounded.Newspaper
        NotificationCategory.PROMOTIONS_AND_DEALS -> Icons.Rounded.LocalOffer
    }
}
