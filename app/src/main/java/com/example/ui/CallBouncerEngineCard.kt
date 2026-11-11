package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.core.SentinalRustCoreBridge
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

val REPEAT_CALLER_COOLDOWN_PRESETS_MINUTES = listOf(1, 2, 3, 5, 10, 15)

fun formatRepeatCallerCooldownLabel(minutes: Int): String {
    val clamped = minutes.coerceIn(
        FocusStateManager.MIN_REPEAT_CALLER_COOLDOWN_MINUTES,
        FocusStateManager.MAX_REPEAT_CALLER_COOLDOWN_MINUTES
    )
    val seconds = clamped * 60
    return "${clamped}m (${seconds}s)"
}

/**
 * CallBouncerEngineCard
 *
 * Material 3 Expressive UI component allowing users to:
 * 1. Toggle the master 'Call Bouncer' engine on/off (`call_bouncer_master_switch`).
 * 2. Adjust the repeat-caller cooldown & breakthrough window (`1 min` to `15 min` / `60s` to `900s`)
 *    via an interactive M3 `Slider` (`call_bouncer_cooldown_slider`) or quick preset chips.
 * 3. Configure sub-guards (Repeat Caller Breakthrough, Starred Contacts Bypass, Network-Level Call
 *    Rejection, VoIP Auto-Decline, Decline All Lockdown) and test repeat-caller breakthroughs live.
 */
@Composable
fun CallBouncerEngineCard(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier,
    initiallyExpandedSubGuards: Boolean = true
) {
    val context = LocalContext.current
    val isBouncerEnabled = state.callBouncerEnabled
    val cooldownMinutes = state.repeatCallerCooldownMinutes.coerceIn(
        FocusStateManager.MIN_REPEAT_CALLER_COOLDOWN_MINUTES,
        FocusStateManager.MAX_REPEAT_CALLER_COOLDOWN_MINUTES
    )
    val cooldownSeconds = cooldownMinutes * 60
    val cooldownMillis = cooldownMinutes * 60_000L

    var sliderValue by remember(cooldownMinutes) {
        mutableFloatStateOf(cooldownMinutes.toFloat())
    }
    var showAdvancedGuards by remember { mutableStateOf(initiallyExpandedSubGuards) }

    // Live interactive repeat-caller test state
    var simulatedCallAttempt by remember { mutableIntStateOf(0) }
    var lastSimulatedDecision by remember {
        mutableStateOf<SentinalRustCoreBridge.RustCallBounceDecision?>(null)
    }

    val containerBg by animateColorAsState(
        targetValue = when {
            state.declineAllCallsRegardless -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
            isBouncerEnabled -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "call_bouncer_card_bg"
    )

    val borderStrokeColor by animateColorAsState(
        targetValue = when {
            state.declineAllCallsRegardless -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            isBouncerEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "call_bouncer_card_border"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("call_bouncer_engine_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = BorderStroke(1.2.dp, borderStrokeColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Row: Engine Icon, Title, Status Pill, and Master Call Bouncer Switch
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
                        color = when {
                            state.declineAllCallsRegardless -> MaterialTheme.colorScheme.errorContainer
                            isBouncerEnabled -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isBouncerEnabled || state.declineAllCallsRegardless) {
                                    Icons.Rounded.PhoneCallback
                                } else {
                                    Icons.Rounded.PhoneDisabled
                                },
                                contentDescription = "Call Bouncer Engine",
                                tint = when {
                                    state.declineAllCallsRegardless -> MaterialTheme.colorScheme.onErrorContainer
                                    isBouncerEnabled -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Call Bouncer Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    state.declineAllCallsRegardless -> MaterialTheme.colorScheme.errorContainer
                                    isBouncerEnabled -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Text(
                                    text = when {
                                        state.declineAllCallsRegardless -> "LOCKDOWN"
                                        isBouncerEnabled -> "ACTIVE • ${cooldownMinutes}M"
                                        else -> "PAUSED"
                                    },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        state.declineAllCallsRegardless -> MaterialTheme.colorScheme.onErrorContainer
                                        isBouncerEnabled -> MaterialTheme.colorScheme.onSecondaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isBouncerEnabled) {
                                "Cellular & VoIP shield active • ${cooldownMinutes}m (${cooldownSeconds}s) repeat-caller window"
                            } else {
                                "Engine bypassed • Incoming cellular & VoIP calls ring normally"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isBouncerEnabled,
                    onCheckedChange = { enabled ->
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setCallBouncerEnabled(enabled)
                    },
                    modifier = Modifier.testTag("call_bouncer_master_switch")
                )
            }

            // 2. Repeat-Caller Cooldown Period Slider Box
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Repeat-Caller Cooldown Period",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.allowRepeatCallersEnabled && !state.declineAllCallsRegardless) {
                                        "2nd call from same number within ${cooldownMinutes}m rings through"
                                    } else {
                                        "Cooldown window for repeat caller & auto-reply guard"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.testTag("call_bouncer_cooldown_value_label")
                        ) {
                            Text(
                                text = formatRepeatCallerCooldownLabel(sliderValue.roundToInt()),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Smooth M3 Slider (1m to 15m in 1-minute steps)
                    Slider(
                        value = sliderValue,
                        onValueChange = { rawValue ->
                            val snapped = rawValue.roundToInt().coerceIn(
                                FocusStateManager.MIN_REPEAT_CALLER_COOLDOWN_MINUTES,
                                FocusStateManager.MAX_REPEAT_CALLER_COOLDOWN_MINUTES
                            )
                            sliderValue = snapped.toFloat()
                            if (snapped != cooldownMinutes) {
                                stateManager.setRepeatCallerCooldownMinutes(snapped)
                            }
                        },
                        valueRange = FocusStateManager.MIN_REPEAT_CALLER_COOLDOWN_MINUTES.toFloat()..FocusStateManager.MAX_REPEAT_CALLER_COOLDOWN_MINUTES.toFloat(),
                        steps = (FocusStateManager.MAX_REPEAT_CALLER_COOLDOWN_MINUTES - FocusStateManager.MIN_REPEAT_CALLER_COOLDOWN_MINUTES - 1).coerceAtLeast(0),
                        enabled = isBouncerEnabled || state.declineAllCallsRegardless,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_bouncer_cooldown_slider")
                    )

                    // Scale Endpoints + Quick Preset Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        REPEAT_CALLER_COOLDOWN_PRESETS_MINUTES.forEach { presetMins ->
                            val isSelected = cooldownMinutes == presetMins
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    ExpressiveHaptics.playSpringTap(context)
                                    sliderValue = presetMins.toFloat()
                                    stateManager.setRepeatCallerCooldownMinutes(presetMins)
                                },
                                enabled = isBouncerEnabled || state.declineAllCallsRegardless,
                                label = {
                                    Text(
                                        text = if (presetMins == 3) "3m (Default)" else "${presetMins}m",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("call_bouncer_preset_${presetMins}m")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Repeat Caller Breakthrough Switch directly paired with the Cooldown Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${cooldownMinutes}-Minute Repeat Caller Breakthrough",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.declineAllCallsRegardless) {
                                    "Overridden: Decline All Calls lockdown is active"
                                } else {
                                    "Second call within ${cooldownSeconds}s rings out loud for real emergencies"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = state.allowRepeatCallersEnabled && !state.declineAllCallsRegardless,
                            onCheckedChange = { enabled ->
                                ExpressiveHaptics.playSpringTap(context)
                                stateManager.setAllowRepeatCallersEnabled(enabled)
                            },
                            enabled = isBouncerEnabled && !state.declineAllCallsRegardless,
                            modifier = Modifier.testTag("switch_repeat_breakthrough")
                        )
                    }
                }
            }

            // 3. Interactive Repeat-Caller Cooldown Simulator Strip
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Cooldown Window Verifier",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = lastSimulatedDecision?.reason
                                    ?: "Simulate 1st & 2nd call from +1 (415) 555-0199 against your ${cooldownMinutes}m window",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    ExpressiveHaptics.playSpringTap(context)
                                    val nowMs = System.currentTimeMillis()
                                    val isSecondCall = simulatedCallAttempt % 2 == 1
                                    val prevCallMs = if (isSecondCall) {
                                        nowMs - (cooldownMillis / 2L)
                                    } else {
                                        null
                                    }
                                    val decision = stateManager.rustCallBouncer.evaluateIncomingCall(
                                        rawCaller = "+1 (415) 555-0199",
                                        isStarredContact = false,
                                        lastIncomingCallMs = prevCallMs,
                                        lastOutgoingCallToCallerMs = null,
                                        lastAutoReplyToCallerMs = prevCallMs,
                                        nowMs = nowMs,
                                        policy = SentinalRustCoreBridge.RustCallPolicyConfig(
                                            callBouncerEnabled = isBouncerEnabled,
                                            repeatCallerWindowMs = cooldownMillis,
                                            declineAllCallsRegardless = state.declineAllCallsRegardless,
                                            strictGamingLockdown = state.strictGamingLockdownEnabled,
                                            isGamingModeActive = state.activeProfile == FocusProfile.GAMING,
                                            allowStarredContacts = state.allowStarredContactsOnly,
                                            allowRepeatCallersWithin3m = state.allowRepeatCallersEnabled,
                                            autoReplyEnabled = state.autoReplyEnabled
                                        )
                                    )
                                    lastSimulatedDecision = decision
                                    simulatedCallAttempt += 1
                                },
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                                modifier = Modifier.testTag("call_bouncer_test_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (simulatedCallAttempt % 2 == 0) "Test 1st Call" else "Test 2nd Call",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    ExpressiveHaptics.playNotificationDismissed(context)
                                    stateManager.resetSessionCaches()
                                    simulatedCallAttempt = 0
                                    lastSimulatedDecision = null
                                },
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                                modifier = Modifier.testTag("call_bouncer_reset_cooldown_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reset Cooldown Cache",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    lastSimulatedDecision?.let { decision ->
                        val isBreakthrough =
                            decision.filterAction == SentinalRustCoreBridge.RustCallFilterAction.ALLOW_RING_BREAKTHROUGH
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isBreakthrough) {
                                Color(0xFF059669).copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBreakthrough) {
                                            Icons.Rounded.CheckCircle
                                        } else {
                                            Icons.Rounded.PhoneDisabled
                                        },
                                        contentDescription = null,
                                        tint = if (isBreakthrough) {
                                            Color(0xFF38EF7D)
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (isBreakthrough) {
                                            "RING BREAKTHROUGH ALLOWED"
                                        } else {
                                            "CALL BOUNCED • AUTO-REPLY=${if (decision.dispatchAutoReply) "SENT" else "COOLDOWN"}"
                                        },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Key #${decision.normalizedCallerKey}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 4. Expandable Cellular & VoIP Bouncer Policy Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        ExpressiveHaptics.playSpringTap(context)
                        showAdvancedGuards = !showAdvancedGuards
                    }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cellular, VoIP & Starred Contact Policies",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showAdvancedGuards) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (showAdvancedGuards) "Collapse policies" else "Expand policies",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = showAdvancedGuards,
                enter = expandVertically(animationSpec = ExpressiveMotion.bouncySpatialSpring()) +
                    fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()),
                exit = shrinkVertically(animationSpec = ExpressiveMotion.snappySpatialSpring()) +
                    fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    BouncerPolicyToggleRow(
                        title = "Decline All Calls Regardless of Condition",
                        subtitle = "Zero-tolerance lockdown: drops all incoming cellular and VoIP calls, ignoring starred contacts and repeat callers",
                        checked = state.declineAllCallsRegardless,
                        onCheckedChange = {
                            ExpressiveHaptics.playSpringTap(context)
                            stateManager.setDeclineAllCallsRegardless(it)
                        },
                        testTag = "switch_decline_all_calls_regardless"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    BouncerPolicyToggleRow(
                        title = "Network-Level Call Rejection (Instant User Busy)",
                        subtitle = "Sends immediate busy signal to tower without ringing",
                        checked = state.networkLevelCallRejection,
                        enabled = isBouncerEnabled || state.declineAllCallsRegardless,
                        onCheckedChange = {
                            ExpressiveHaptics.playSpringTap(context)
                            stateManager.setNetworkLevelCallRejectionEnabled(it)
                        },
                        testTag = "switch_network_rejection"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    BouncerPolicyToggleRow(
                        title = "Auto-Decline WhatsApp/Telegram Calls",
                        subtitle = "Hangs up third-party VoIP calls via programmatic action intent",
                        checked = state.autoDeclineVoipCalls,
                        enabled = isBouncerEnabled || state.declineAllCallsRegardless,
                        onCheckedChange = {
                            ExpressiveHaptics.playSpringTap(context)
                            stateManager.setAutoDeclineVoipCallsEnabled(it)
                        },
                        testTag = "switch_voip_bouncer"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    BouncerPolicyToggleRow(
                        title = "Allow Starred Contacts",
                        subtitle = if (state.declineAllCallsRegardless) {
                            "Overridden: Decline All Calls is active"
                        } else {
                            "Passes priority contacts through cellular & VoIP filters"
                        },
                        checked = state.allowStarredContactsOnly && !state.declineAllCallsRegardless,
                        enabled = isBouncerEnabled && !state.declineAllCallsRegardless,
                        onCheckedChange = { checked ->
                            ExpressiveHaptics.playSpringTap(context)
                            stateManager.updateProfileSettings(
                                checked,
                                state.blockRepeatedCallers,
                                state.silenceNotifications
                            )
                        },
                        testTag = "switch_allow_starred"
                    )
                }
            }
        }
    }
}

@Composable
private fun BouncerPolicyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}
