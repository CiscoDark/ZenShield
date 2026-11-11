package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.core.SentinalRustCoreBridge
import com.example.core.jni.SentinalRustEngineClient
import com.example.core.jni.SentinalRustJniWireCodec
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.JetBrainsMonoFontFamily

enum class RustEngineModule(
    val title: String,
    val fileName: String
) {
    FOCUS_FSM("Focus FSM", "focus_fsm.rs"),
    NOTIF_CLASSIFIER("Notif Classifier", "notif_classifier.rs"),
    CALL_BOUNCER("Call Bouncer", "call_bouncer.rs"),
    JNI_BRIDGE("JNI Bridge", "jni_bridge.rs")
}

/**
 * Interactive Rust Core Engine & JNI Interface Diagnostics Card.
 * Showcases Sentinal's Rust Core modules (`focus_fsm.rs`, `notif_classifier.rs`, `call_bouncer.rs`,
 * and `jni_bridge.rs` / `SentinalRustJni.kt`), allows running live deterministic test vectors
 * through the Kotlin JNI interfaces, and displays the Rust source code.
 */
@Composable
fun RustCoreInspectorCard(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedModule by remember { mutableStateOf(RustEngineModule.FOCUS_FSM) }
    var showRustSource by remember { mutableStateOf(false) }

    // Live diagnostic output state
    var lastVectorTitle by remember {
        mutableStateOf("RustFocusStateMachineInterface.stepStateMachine()")
    }
    var lastRustOutput by remember {
        val initialFsm = SentinalRustCoreBridge.RustFocusEngineState(
            activeMode = FocusProfile.GAMING,
            activeGamePackage = "com.supercell.clashofclans"
        )
        val outcome = stateManager.rustFocusStateMachine.stepStateMachine(
            current = initialFsm,
            event = SentinalRustCoreBridge.RustFocusEvent.ManualSetMode(
                targetMode = FocusProfile.OFF
            )
        )
        mutableStateOf(
            buildString {
                appendLine("TransitionOutcome {")
                appendLine("  active_mode: ${outcome.state.activeMode.id},")
                appendLine("  declined_game_pkg: \"${outcome.state.declinedGamePackageForSession}\",")
                appendLine("  suppress_in_game: ${outcome.state.manuallyDismissedGamingWhilstInGame},")
                appendLine("  commands: [${outcome.commands.joinToString { it::class.simpleName ?: "" }}]")
                append("}")
            }
        )
    }
    var lastLatencyMicros by remember { mutableStateOf(2L) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rust_core_inspector_card"),
        shape = RoundedCornerShape(28.dp),
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
            // 1. Header Row
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
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = "Rust Core Engine",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
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
                                text = "Rust Core & JNI Bridge",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "v${SentinalRustCoreBridge.CRATE_VERSION}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "sentinal_rust_core • JNI C-ABI & Kotlin Interfaces",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        showRustSource = !showRustSource
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("toggle_rust_source_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (showRustSource) "Hide .rs" else "View .rs",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. Module Selector Chips (focus_fsm.rs | notif_classifier.rs | call_bouncer.rs | jni_bridge.rs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RustEngineModule.entries.forEach { module ->
                    val isSelected = selectedModule == module
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            selectedModule = module
                        },
                        label = {
                            Text(
                                text = "${module.title} (${module.fileName})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (module) {
                                    RustEngineModule.FOCUS_FSM -> Icons.Rounded.SportsEsports
                                    RustEngineModule.NOTIF_CLASSIFIER -> Icons.Rounded.Notifications
                                    RustEngineModule.CALL_BOUNCER -> Icons.Rounded.PhoneCallback
                                    RustEngineModule.JNI_BRIDGE -> Icons.Rounded.Code
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("rust_module_chip_${module.name.lowercase()}")
                    )
                }
            }

            // 3. One-Tap Deterministic Test Vectors for the Selected Engine
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedModule) {
                    RustEngineModule.FOCUS_FSM -> {
                        FilledTonalButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                val currentFsm = SentinalRustCoreBridge.RustFocusEngineState(
                                    activeMode = FocusProfile.GAMING,
                                    activeGamePackage = "com.activision.callofduty.shooter"
                                )
                                val outcome = stateManager.rustFocusStateMachine.stepStateMachine(
                                    current = currentFsm,
                                    event = SentinalRustCoreBridge.RustFocusEvent.ManualSetMode(
                                        targetMode = FocusProfile.OFF
                                    )
                                )
                                lastVectorTitle = "RustFocusStateMachineInterface.stepStateMachine(Off)"
                                lastLatencyMicros = outcome.executionMicros
                                lastRustOutput = buildString {
                                    appendLine("TransitionOutcome {")
                                    appendLine("  active_mode: ${outcome.state.activeMode.id},")
                                    appendLine("  declined_game_pkg: \"${outcome.state.declinedGamePackageForSession}\",")
                                    appendLine("  suppress_in_game: ${outcome.state.manuallyDismissedGamingWhilstInGame},")
                                    appendLine("  summary: \"${outcome.summary}\"")
                                    append("}")
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("rust_vector_fsm_manual_off")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Shade Turn-Off Suppression")
                        }

                        FilledTonalButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                val inWindow = stateManager.rustFocusStateMachine.isTimeWithinScheduleWindow(
                                    startHour = state.sleepScheduleStartHour,
                                    startMinute = state.sleepScheduleStartMinute,
                                    endHour = state.sleepScheduleEndHour,
                                    endMinute = state.sleepScheduleEndMinute,
                                    currentDayOfWeek = java.util.Calendar.MONDAY,
                                    yesterdayDayOfWeek = java.util.Calendar.SUNDAY,
                                    activeDays = state.sleepScheduleDays,
                                    currentHour = 23,
                                    currentMinute = 15
                                )
                                lastVectorTitle = "is_time_within_schedule_window(22:30..07:00 @ 23:15)"
                                lastLatencyMicros = 1L
                                lastRustOutput = buildString {
                                    appendLine("ScheduleWindowOutcome {")
                                    appendLine("  window: \"${String.format("%02d:%02d", state.sleepScheduleStartHour, state.sleepScheduleStartMinute)} -> ${String.format("%02d:%02d", state.sleepScheduleEndHour, state.sleepScheduleEndMinute)}\",")
                                    appendLine("  probe_time: \"23:15 (Overnight Span)\",")
                                    appendLine("  is_active: $inWindow")
                                    append("}")
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("rust_vector_fsm_schedule")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Overnight Sleep Window")
                        }
                    }

                    RustEngineModule.NOTIF_CLASSIFIER -> {
                        FilledTonalButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                val res = stateManager.rustNotificationClassifier.classifyAndEvaluateNotification(
                                    packageName = "com.google.android.apps.authenticator2",
                                    title = "Security Verification Code",
                                    body = "Your one-time login OTP code is 842-910",
                                    priorityRules = state.priorityLevelRules,
                                    categoryOverrides = state.categoryPriorityOverrides,
                                    smartUrgentBreakthroughEnabled = state.smartUrgentBreakthroughEnabled
                                )
                                lastVectorTitle = "RustNotificationClassifierInterface(2FA OTP)"
                                lastLatencyMicros = res.executionMicros
                                lastRustOutput = buildString {
                                    appendLine("ClassificationOutcome {")
                                    appendLine("  category: ${res.category.name},")
                                    appendLine("  priority: \"${res.priority.badgeLabel}\",")
                                    appendLine("  action: ${res.action.name} (${res.action.badgeText}),")
                                    appendLine("  urgent_breakthrough: ${res.matchedUrgentBreakthrough}")
                                    append("}")
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("rust_vector_notif_otp")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test 2FA OTP Breakthrough")
                        }

                        FilledTonalButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                val res = stateManager.rustNotificationClassifier.classifyAndEvaluateNotification(
                                    packageName = "com.amazon.mShop.android.shopping",
                                    title = "50% OFF Weekend Flash Sale",
                                    body = "Limited-time coupon expires tonight",
                                    priorityRules = state.priorityLevelRules,
                                    categoryOverrides = state.categoryPriorityOverrides,
                                    smartUrgentBreakthroughEnabled = state.smartUrgentBreakthroughEnabled
                                )
                                lastVectorTitle = "RustNotificationClassifierInterface(Promo Alert)"
                                lastLatencyMicros = res.executionMicros
                                lastRustOutput = buildString {
                                    appendLine("ClassificationOutcome {")
                                    appendLine("  category: ${res.category.name},")
                                    appendLine("  priority: \"${res.priority.badgeLabel}\",")
                                    appendLine("  action: ${res.action.name} (${res.action.badgeText}),")
                                    appendLine("  reason: \"${res.reason}\"")
                                    append("}")
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("rust_vector_notif_promo")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Promo Suppression")
                        }
                    }

                    RustEngineModule.CALL_BOUNCER -> {
                        FilledTonalButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                val now = System.currentTimeMillis()
                                val decision = stateManager.rustCallBouncer.evaluateIncomingCall(
                                    rawCaller = "+1 (415) 555-0199",
                                    isStarredContact = false,
                                    lastIncomingCallMs = now - 75_000L, // 75s ago (< 180s window)
                                    lastOutgoingCallToCallerMs = null,
                                    lastAutoReplyToCallerMs = now - 75_000L,
                                    nowMs = now
                                )
                                lastVectorTitle = "RustCallBouncerInterface(2nd Call @ +75s <= 180s)"
                                lastLatencyMicros = decision.executionMicros
                                lastRustOutput = buildString {
                                    appendLine("CallBounceDecision {")
                                    appendLine("  normalized_key: \"${decision.normalizedCallerKey}\",")
                                    appendLine("  filter_action: ${decision.filterAction.name},")
                                    appendLine("  dispatch_auto_reply: ${decision.dispatchAutoReply},")
                                    appendLine("  reason: \"${decision.reason}\"")
                                    append("}")
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("rust_vector_call_repeat")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test 180s Repeat Breakthrough")
                        }

                        FilledTonalButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                val now = System.currentTimeMillis()
                                val decision = stateManager.rustCallBouncer.evaluateIncomingCall(
                                    rawCaller = "+1 (415) 555-0199",
                                    isStarredContact = false,
                                    lastIncomingCallMs = null,
                                    lastOutgoingCallToCallerMs = now - 120_000L, // User dialed them 2m ago
                                    lastAutoReplyToCallerMs = null,
                                    nowMs = now
                                )
                                lastVectorTitle = "RustCallBouncerInterface(Recipient Dialed 2m Ago)"
                                lastLatencyMicros = decision.executionMicros
                                lastRustOutput = buildString {
                                    appendLine("CallBounceDecision {")
                                    appendLine("  normalized_key: \"${decision.normalizedCallerKey}\",")
                                    appendLine("  filter_action: ${decision.filterAction.name},")
                                    appendLine("  dispatch_auto_reply: ${decision.dispatchAutoReply} (Suppressed!),")
                                    appendLine("  reason: \"${decision.reason}\"")
                                    append("}")
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("rust_vector_call_outgoing_guard")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Outgoing Auto-Reply Guard")
                        }
                    }

                    RustEngineModule.JNI_BRIDGE -> {
                        FilledTonalButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                val baseState = SentinalRustCoreBridge.RustFocusEngineState(
                                    activeMode = FocusProfile.GAMING,
                                    activeGamePackage = "com.miHoYo.GenshinImpact"
                                )
                                val outcome = stateManager.rustFocusStateMachine.stepStateMachine(
                                    current = baseState,
                                    event = SentinalRustCoreBridge.RustFocusEvent.ManualSetMode(
                                        targetMode = FocusProfile.OFF
                                    )
                                )
                                val wirePayload = SentinalRustJniWireCodec.encodeTransitionOutcomeToWire(outcome)
                                val decoded = SentinalRustJniWireCodec.decodeTransitionOutcomeFromWire(
                                    baseState = baseState,
                                    wirePayload = wirePayload
                                )
                                lastVectorTitle = "SentinalRustJniNative.nativeStepFocusStateMachine()"
                                lastLatencyMicros = outcome.executionMicros
                                lastRustOutput = buildString {
                                    appendLine("JniWireRoundTrip {")
                                    appendLine("  c_abi_wire: \"$wirePayload\",")
                                    appendLine("  decoded_mode: ${decoded.state.activeMode.id},")
                                    appendLine("  decoded_commands: ${decoded.commands.size}")
                                    append("}")
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("rust_vector_jni_fsm_wire")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test JNI FSM Wire Codec")
                        }

                        FilledTonalButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                val outcome = stateManager.rustNotificationClassifier.classifyAndEvaluateNotification(
                                    packageName = "com.Slack",
                                    title = "#prod-incidents",
                                    body = "URGENT: PagerDuty deployment alert",
                                    priorityRules = state.priorityLevelRules,
                                    categoryOverrides = state.categoryPriorityOverrides,
                                    smartUrgentBreakthroughEnabled = state.smartUrgentBreakthroughEnabled
                                )
                                val wirePayload = SentinalRustJniWireCodec.encodeClassificationOutcomeToWire(outcome)
                                val decoded = SentinalRustJniWireCodec.decodeClassificationOutcomeFromWire(wirePayload)
                                lastVectorTitle = "SentinalRustJniNative.nativeClassifyNotification()"
                                lastLatencyMicros = outcome.executionMicros
                                lastRustOutput = buildString {
                                    appendLine("JniNotifWireRoundTrip {")
                                    appendLine("  c_abi_wire: \"$wirePayload\",")
                                    appendLine("  decoded_category: ${decoded.category.name},")
                                    appendLine("  decoded_action: ${decoded.action.name}")
                                    append("}")
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("rust_vector_jni_notif_wire")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test JNI Classifier Wire Codec")
                        }
                    }
                }
            }

            // 4. Live Rust Evaluation Struct Output Box
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rust_evaluation_output_box")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lastVectorTitle,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${lastLatencyMicros}µs",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text(
                        text = lastRustOutput,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 5. Expandable Portable Rust Crate Source Viewer (/rust_core/src/*.rs)
            AnimatedVisibility(visible = showRustSource) {
                val rustSourceText = when (selectedModule) {
                    RustEngineModule.FOCUS_FSM -> SentinalRustCoreBridge.FOCUS_FSM_RS_SNIPPET
                    RustEngineModule.NOTIF_CLASSIFIER -> SentinalRustCoreBridge.NOTIF_CLASSIFIER_RS_SNIPPET
                    RustEngineModule.CALL_BOUNCER -> SentinalRustCoreBridge.CALL_BOUNCER_RS_SNIPPET
                    RustEngineModule.JNI_BRIDGE -> SentinalRustEngineClient.JNI_BRIDGE_RS_SNIPPET
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rust_crate_source_viewer")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Portable Rust Crate • /rust_core/src/${selectedModule.fileName}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = rustSourceText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
