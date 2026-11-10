package com.example.core

import java.util.Locale

/**
 * SentinalRustCoreBridge
 *
 * 1:1 Kotlin algebraic mirror of the `sentinal_rust_core` crate (`/rust_core/src/`):
 * 1. `focus_fsm.rs`: Deterministic Focus State Machine & Schedule Evaluation Engine
 * 2. `notif_classifier.rs`: Notification Priority Classifier & Global Focus Rules Engine
 * 3. `call_bouncer.rs`: Call Bouncer Cooldown & Repeat-Caller Matcher
 */
object SentinalRustCoreBridge {

    const val CRATE_NAME = "sentinal_rust_core"
    const val CRATE_VERSION = "1.0.0"
    const val REPEAT_CALLER_WINDOW_MS = 180_000L
    const val AUTO_REPLY_COOLDOWN_MS = 180_000L
    const val OUTGOING_CALL_EXEMPTION_MS = 600_000L

    // =========================================================================
    // ENGINE 1: FOCUS STATE MACHINE & SCHEDULE EVALUATION ENGINE (focus_fsm.rs)
    // =========================================================================

    data class RustFocusEngineState(
        val activeMode: FocusProfile = FocusProfile.OFF,
        val previousModeBeforeGame: FocusProfile = FocusProfile.OFF,
        val sessionStartMs: Long? = null,
        val expiresAtMs: Long? = null,
        val autoGameDetectionEnabled: Boolean = true,
        val askBeforeGameActivation: Boolean = true,
        val isAutoTriggeredByGame: Boolean = false,
        val isAutoTriggeredByWork: Boolean = false,
        val isAutoTriggeredBySleep: Boolean = false,
        val activeGamePackage: String? = null,
        val pendingGamePackage: String? = null,
        val pendingGameCountdownSec: Int? = null,
        val declinedGamePackageForSession: String? = null,
        val manuallyDismissedGamingWhilstInGame: Boolean = false
    ) {
        companion object {
            fun fromSessionState(state: FocusSessionState): RustFocusEngineState {
                return RustFocusEngineState(
                    activeMode = state.activeProfile,
                    previousModeBeforeGame = state.previousProfileBeforeGame,
                    sessionStartMs = state.sessionStartTimeMillis,
                    expiresAtMs = state.expiresAtMillis,
                    autoGameDetectionEnabled = state.autoGameDetectionEnabled,
                    askBeforeGameActivation = state.askBeforeGameActivation,
                    isAutoTriggeredByGame = state.isAutoTriggeredByGame,
                    isAutoTriggeredByWork = state.isAutoTriggeredByWorkSchedule,
                    isAutoTriggeredBySleep = state.isAutoTriggeredBySleepSchedule,
                    activeGamePackage = state.activeGamePackage,
                    pendingGamePackage = state.pendingGameActivationPackage,
                    pendingGameCountdownSec = state.pendingGameCountdownSeconds,
                    declinedGamePackageForSession = state.declinedGamePackageForSession,
                    manuallyDismissedGamingWhilstInGame = state.manuallyDismissedGamingWhilstInGame
                )
            }
        }
    }

    sealed interface RustFocusEvent {
        data class ManualSetMode(
            val targetMode: FocusProfile,
            val durationMinutes: Int? = null,
            val nowMs: Long = System.currentTimeMillis()
        ) : RustFocusEvent

        data class QuickTileToggle(
            val tileMode: FocusProfile,
            val nowMs: Long = System.currentTimeMillis()
        ) : RustFocusEvent

        data class ForegroundAppObserved(
            val packageName: String,
            val isGame: Boolean,
            val nowMs: Long = System.currentTimeMillis()
        ) : RustFocusEvent

        data class ConfirmPendingGamePrompt(
            val nowMs: Long = System.currentTimeMillis()
        ) : RustFocusEvent

        data object DeclinePendingGamePrompt : RustFocusEvent

        data class EvaluateScheduleBoundary(
            val scheduleMode: FocusProfile,
            val inActiveWindow: Boolean,
            val windowStartMs: Long,
            val windowEndMs: Long,
            val dismissedWindowStartMs: Long? = null,
            val nowMs: Long = System.currentTimeMillis()
        ) : RustFocusEvent
    }

    sealed interface RustEngineCommand {
        data class SyncInterruptionFilter(val mode: FocusProfile) : RustEngineCommand
        data class StartForegroundShield(val mode: FocusProfile) : RustEngineCommand
        data object StopForegroundShield : RustEngineCommand
        data class ShowGameConfirmPrompt(val packageName: String, val countdownSeconds: Int) : RustEngineCommand
        data object DismissGameConfirmPrompt : RustEngineCommand
        data class ShowGamingActivatedHud(val packageName: String) : RustEngineCommand
        data object SyncQuickSettingsTiles : RustEngineCommand
        data class LogCompletedSessionToRoom(
            val mode: FocusProfile,
            val durationMinutes: Int,
            val triggerSource: String
        ) : RustEngineCommand
    }

    data class RustTransitionOutcome(
        val state: RustFocusEngineState,
        val commands: List<RustEngineCommand>,
        val summary: String,
        val executionMicros: Long = 1L
    )

    fun isTimeWithinScheduleWindow(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        currentDayOfWeek: Int,
        yesterdayDayOfWeek: Int,
        activeDays: Set<Int>,
        currentHour: Int,
        currentMinute: Int
    ): Boolean {
        val startTotal = startHour * 60 + startMinute
        val endTotal = endHour * 60 + endMinute
        if (startTotal == endTotal || activeDays.isEmpty()) return false
        val currentTotal = currentHour * 60 + currentMinute

        return if (startTotal < endTotal) {
            activeDays.contains(currentDayOfWeek) && currentTotal in startTotal until endTotal
        } else {
            when {
                currentTotal >= startTotal -> activeDays.contains(currentDayOfWeek)
                currentTotal < endTotal -> activeDays.contains(yesterdayDayOfWeek)
                else -> false
            }
        }
    }

    fun stepFocusStateMachine(
        current: RustFocusEngineState,
        event: RustFocusEvent
    ): RustTransitionOutcome {
        val startNs = System.nanoTime()
        val commands = mutableListOf<RustEngineCommand>()

        val outcomeStateAndSummary: Pair<RustFocusEngineState, String> = when (event) {
            is RustFocusEvent.ManualSetMode -> {
                val target = event.targetMode
                val now = event.nowMs
                val wasGaming = current.activeMode == FocusProfile.GAMING && target != FocusProfile.GAMING
                val hadPending = current.pendingGamePackage != null && target != FocusProfile.GAMING

                if (current.activeMode != FocusProfile.OFF && current.activeMode != target) {
                    val started = current.sessionStartMs ?: (now - 60_000L)
                    val elapsedMins = (((now - started) / 60_000L).toInt()).coerceAtLeast(1)
                    val source = when {
                        current.isAutoTriggeredByGame -> "Auto Game Detector"
                        current.isAutoTriggeredByWork || current.isAutoTriggeredBySleep -> "Scheduled"
                        else -> "Manual"
                    }
                    commands += RustEngineCommand.LogCompletedSessionToRoom(
                        mode = current.activeMode,
                        durationMinutes = elapsedMins,
                        triggerSource = source
                    )
                }

                val suppressedPkg = when {
                    wasGaming || hadPending -> current.activeGamePackage
                        ?: current.pendingGamePackage
                        ?: current.declinedGamePackageForSession
                    target == FocusProfile.GAMING -> null
                    else -> current.declinedGamePackageForSession
                }

                val suppressInGame = when {
                    wasGaming || hadPending -> true
                    target == FocusProfile.GAMING -> false
                    else -> current.manuallyDismissedGamingWhilstInGame
                }

                val next = current.copy(
                    activeMode = target,
                    pendingGamePackage = null,
                    pendingGameCountdownSec = null,
                    isAutoTriggeredByGame = false,
                    isAutoTriggeredByWork = false,
                    isAutoTriggeredBySleep = false,
                    sessionStartMs = if (target != FocusProfile.OFF) now else null,
                    expiresAtMs = if (target != FocusProfile.OFF) {
                        event.durationMinutes?.let { now + it * 60_000L }
                    } else null,
                    declinedGamePackageForSession = suppressedPkg,
                    manuallyDismissedGamingWhilstInGame = suppressInGame
                )

                commands += RustEngineCommand.DismissGameConfirmPrompt
                commands += RustEngineCommand.SyncInterruptionFilter(target)
                if (target != FocusProfile.OFF) {
                    commands += RustEngineCommand.StartForegroundShield(target)
                } else {
                    commands += RustEngineCommand.StopForegroundShield
                }
                commands += RustEngineCommand.SyncQuickSettingsTiles

                next to "ManualSetMode -> ${target.id} (suppress_in_game=$suppressInGame)"
            }

            is RustFocusEvent.QuickTileToggle -> {
                val target = if (current.activeMode == event.tileMode) FocusProfile.OFF else event.tileMode
                return stepFocusStateMachine(
                    current,
                    RustFocusEvent.ManualSetMode(
                        targetMode = target,
                        durationMinutes = null,
                        nowMs = event.nowMs
                    )
                )
            }

            is RustFocusEvent.ForegroundAppObserved -> {
                if (event.isGame) {
                    val suppressed = current.manuallyDismissedGamingWhilstInGame ||
                        current.declinedGamePackageForSession == event.packageName
                    val withGameObserved = current.copy(activeGamePackage = event.packageName)

                    if (!current.autoGameDetectionEnabled ||
                        current.activeMode == FocusProfile.GAMING ||
                        suppressed
                    ) {
                        withGameObserved to "GameObserved(${event.packageName}) -> Suppressed (in_game_dismissed=$suppressed)"
                    } else if (current.askBeforeGameActivation) {
                        commands += RustEngineCommand.ShowGameConfirmPrompt(
                            packageName = event.packageName,
                            countdownSeconds = 5
                        )
                        withGameObserved.copy(
                            pendingGamePackage = event.packageName,
                            pendingGameCountdownSec = 5
                        ) to "GameObserved(${event.packageName}) -> Prompting 5s Confirmation"
                    } else {
                        commands += RustEngineCommand.SyncInterruptionFilter(FocusProfile.GAMING)
                        commands += RustEngineCommand.StartForegroundShield(FocusProfile.GAMING)
                        commands += RustEngineCommand.ShowGamingActivatedHud(event.packageName)
                        commands += RustEngineCommand.SyncQuickSettingsTiles
                        withGameObserved.copy(
                            previousModeBeforeGame = current.activeMode,
                            activeMode = FocusProfile.GAMING,
                            isAutoTriggeredByGame = true,
                            sessionStartMs = event.nowMs
                        ) to "GameObserved(${event.packageName}) -> Auto-Activated Gaming Mode"
                    }
                } else {
                    val isSystemUi = event.packageName == "com.android.systemui" || event.packageName == "android"
                    val next = if (!isSystemUi) {
                        current.copy(
                            declinedGamePackageForSession = null,
                            manuallyDismissedGamingWhilstInGame = false,
                            activeGamePackage = null
                        )
                    } else current
                    next to "ForegroundNonGame(${event.packageName}) -> Session suppression reset=${!isSystemUi}"
                }
            }

            is RustFocusEvent.ConfirmPendingGamePrompt -> {
                val pkg = current.pendingGamePackage ?: current.activeGamePackage ?: "GameSession"
                commands += RustEngineCommand.DismissGameConfirmPrompt
                commands += RustEngineCommand.SyncInterruptionFilter(FocusProfile.GAMING)
                commands += RustEngineCommand.StartForegroundShield(FocusProfile.GAMING)
                commands += RustEngineCommand.ShowGamingActivatedHud(pkg)
                commands += RustEngineCommand.SyncQuickSettingsTiles

                current.copy(
                    previousModeBeforeGame = current.activeMode,
                    activeMode = FocusProfile.GAMING,
                    isAutoTriggeredByGame = true,
                    sessionStartMs = event.nowMs,
                    pendingGamePackage = null,
                    pendingGameCountdownSec = null,
                    activeGamePackage = pkg,
                    declinedGamePackageForSession = null,
                    manuallyDismissedGamingWhilstInGame = false
                ) to "ConfirmPendingGamePrompt($pkg) -> Gaming Active"
            }

            RustFocusEvent.DeclinePendingGamePrompt -> {
                val declinedPkg = current.pendingGamePackage ?: current.activeGamePackage
                commands += RustEngineCommand.DismissGameConfirmPrompt
                current.copy(
                    declinedGamePackageForSession = declinedPkg,
                    manuallyDismissedGamingWhilstInGame = true,
                    pendingGamePackage = null,
                    pendingGameCountdownSec = null
                ) to "DeclinePendingGamePrompt($declinedPkg) -> Suppressed until game exit"
            }

            is RustFocusEvent.EvaluateScheduleBoundary -> {
                if (event.inActiveWindow) {
                    if (event.dismissedWindowStartMs == event.windowStartMs) {
                        current to "Schedule window manually snoozed by user"
                    } else if (current.activeMode == FocusProfile.OFF) {
                        commands += RustEngineCommand.SyncInterruptionFilter(event.scheduleMode)
                        commands += RustEngineCommand.StartForegroundShield(event.scheduleMode)
                        commands += RustEngineCommand.SyncQuickSettingsTiles
                        current.copy(
                            activeMode = event.scheduleMode,
                            sessionStartMs = event.nowMs,
                            expiresAtMs = event.windowEndMs,
                            isAutoTriggeredByWork = event.scheduleMode == FocusProfile.WORK,
                            isAutoTriggeredBySleep = event.scheduleMode == FocusProfile.SLEEP
                        ) to "Schedule engaged ${event.scheduleMode.id}"
                    } else {
                        current to "Schedule evaluated -> Already in ${current.activeMode.id}"
                    }
                } else {
                    val isMatchingAuto = (event.scheduleMode == FocusProfile.WORK &&
                        current.activeMode == FocusProfile.WORK &&
                        current.isAutoTriggeredByWork) ||
                        (event.scheduleMode == FocusProfile.SLEEP &&
                            current.activeMode == FocusProfile.SLEEP &&
                            current.isAutoTriggeredBySleep)
                    if (isMatchingAuto) {
                        return stepFocusStateMachine(
                            current,
                            RustFocusEvent.ManualSetMode(
                                targetMode = FocusProfile.OFF,
                                durationMinutes = null,
                                nowMs = event.nowMs
                            )
                        )
                    }
                    current to "Schedule evaluated -> Outside window, no transition"
                }
            }
        }

        val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)
        return RustTransitionOutcome(
            state = outcomeStateAndSummary.first,
            commands = commands,
            summary = outcomeStateAndSummary.second,
            executionMicros = elapsedMicros
        )
    }

    // =========================================================================
    // ENGINE 2: NOTIFICATION PRIORITY CLASSIFIER & FOCUS RULES (notif_classifier.rs)
    // =========================================================================

    data class RustClassificationOutcome(
        val category: NotificationCategory,
        val priority: NotificationPriorityLevel,
        val action: GlobalFocusRuleAction,
        val matchedUrgentBreakthrough: Boolean,
        val reason: String,
        val executionMicros: Long = 1L
    )

    fun classifyAndEvaluateNotification(
        packageName: String,
        title: String,
        body: String,
        priorityRules: Map<NotificationPriorityLevel, GlobalFocusRuleAction> = FocusSessionState.DEFAULT_PRIORITY_LEVEL_RULES,
        categoryOverrides: Map<NotificationCategory, NotificationPriorityLevel> = FocusSessionState.DEFAULT_CATEGORY_PRIORITIES,
        smartUrgentBreakthroughEnabled: Boolean = true
    ): RustClassificationOutcome {
        val startNs = System.nanoTime()
        val pkgLower = packageName.lowercase(Locale.ROOT)
        val combinedLower = "$title $body".lowercase(Locale.ROOT)

        val hasUrgentKeyword = combinedLower.contains("urgent") ||
            combinedLower.contains("emergency") ||
            combinedLower.contains("verification code") ||
            combinedLower.contains("security code") ||
            combinedLower.contains("one-time") ||
            combinedLower.contains("otp") ||
            combinedLower.contains("2fa") ||
            combinedLower.contains("your driver") ||
            combinedLower.contains("arriving now")

        val isAuthenticatorPkg = pkgLower.contains("authenticator") ||
            pkgLower.contains("authy") ||
            pkgLower.contains("bank") ||
            pkgLower.contains("uber") ||
            pkgLower.contains("lyft")

        if (hasUrgentKeyword || isAuthenticatorPkg) {
            val category = NotificationCategory.SECURITY_AND_URGENT
            val priority = categoryOverrides[category] ?: NotificationPriorityLevel.CRITICAL
            val action = if (smartUrgentBreakthroughEnabled) {
                GlobalFocusRuleAction.ALLOW_ALL
            } else {
                priorityRules[priority] ?: GlobalFocusRuleAction.ALLOW_ALL
            }
            val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)
            return RustClassificationOutcome(
                category = category,
                priority = priority,
                action = action,
                matchedUrgentBreakthrough = smartUrgentBreakthroughEnabled,
                reason = "Matched OTP/2FA/Urgent breakthrough signature",
                executionMicros = elapsedMicros
            )
        }

        val isPromo = combinedLower.contains("% off") ||
            combinedLower.contains("flash sale") ||
            combinedLower.contains("coupon") ||
            combinedLower.contains("promo code") ||
            combinedLower.contains("limited-time deal") ||
            pkgLower.contains("shopping") ||
            pkgLower.contains("amazon")

        val isWork = pkgLower.contains("slack") ||
            pkgLower.contains("teams") ||
            pkgLower.contains("calendar") ||
            pkgLower.contains("outlook") ||
            pkgLower.contains("gm") ||
            pkgLower.contains("jira") ||
            combinedLower.contains("meeting") ||
            combinedLower.contains("deployment")

        val isDm = pkgLower.contains("whatsapp") ||
            pkgLower.contains("telegram") ||
            pkgLower.contains("signal") ||
            pkgLower.contains("mms") ||
            pkgLower.contains("messaging") ||
            pkgLower.contains("discord")

        val isNews = pkgLower.contains("news") ||
            pkgLower.contains("nytimes") ||
            pkgLower.contains("bbc") ||
            pkgLower.contains("podcast") ||
            combinedLower.contains("breaking news") ||
            combinedLower.contains("daily digest")

        val (category, reason) = when {
            isPromo -> NotificationCategory.PROMOTIONS_AND_DEALS to "Matched promotional/shopping keyword or package"
            isWork -> NotificationCategory.WORK_AND_CALENDAR to "Matched enterprise workspace or calendar package"
            isDm -> NotificationCategory.DIRECT_MESSAGES to "Matched direct messaging or chat package"
            isNews -> NotificationCategory.NEWS_AND_MEDIA to "Matched news or digest signature"
            else -> NotificationCategory.SOCIAL_AND_COMMUNITY to "Classified under Social & Community notifications"
        }

        val priority = categoryOverrides[category] ?: category.defaultPriority
        val action = priorityRules[priority] ?: GlobalFocusRuleAction.MUTE_SILENT
        val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)

        return RustClassificationOutcome(
            category = category,
            priority = priority,
            action = action,
            matchedUrgentBreakthrough = false,
            reason = reason,
            executionMicros = elapsedMicros
        )
    }

    // =========================================================================
    // ENGINE 3: CALL BOUNCER COOLDOWN & REPEAT-CALLER MATCHER (call_bouncer.rs)
    // =========================================================================

    enum class RustCallFilterAction {
        ALLOW_RING_BREAKTHROUGH,
        BOUNCE_AND_DECLINE_CALL
    }

    data class RustCallPolicyConfig(
        val declineAllCallsRegardless: Boolean = false,
        val strictGamingLockdown: Boolean = true,
        val isGamingModeActive: Boolean = false,
        val allowStarredContacts: Boolean = true,
        val allowRepeatCallersWithin3m: Boolean = true,
        val autoReplyEnabled: Boolean = true
    )

    data class RustCallBounceDecision(
        val normalizedCallerKey: String,
        val filterAction: RustCallFilterAction,
        val dispatchAutoReply: Boolean,
        val updatedLastCallTimestampMs: Long?,
        val updatedLastAutoReplyTimestampMs: Long?,
        val reason: String,
        val executionMicros: Long = 1L
    )

    fun normalizeCallerLookupKey(raw: String): String {
        val trimmed = raw.trim().lowercase(Locale.ROOT)
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length >= 7) {
            return digits.takeLast(9)
        }
        return trimmed
    }

    fun evaluateIncomingCall(
        rawCaller: String,
        isStarredContact: Boolean,
        lastIncomingCallMs: Long?,
        lastOutgoingCallToCallerMs: Long?,
        lastAutoReplyToCallerMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        policy: RustCallPolicyConfig = RustCallPolicyConfig()
    ): RustCallBounceDecision {
        val startNs = System.nanoTime()
        val key = normalizeCallerLookupKey(rawCaller)

        if (policy.declineAllCallsRegardless) {
            val sendReply = shouldDispatchAutoReply(
                normalizedKey = key,
                lastOutgoingCallMs = lastOutgoingCallToCallerMs,
                lastAutoReplyMs = lastAutoReplyToCallerMs,
                nowMs = nowMs,
                autoReplyEnabled = policy.autoReplyEnabled
            )
            val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)
            return RustCallBounceDecision(
                normalizedCallerKey = key,
                filterAction = RustCallFilterAction.BOUNCE_AND_DECLINE_CALL,
                dispatchAutoReply = sendReply,
                updatedLastCallTimestampMs = nowMs,
                updatedLastAutoReplyTimestampMs = if (sendReply) nowMs else lastAutoReplyToCallerMs,
                reason = "Decline All Calls Regardless lockdown active",
                executionMicros = elapsedMicros
            )
        }

        val gamingOverridesStarred = policy.isGamingModeActive && policy.strictGamingLockdown
        if (isStarredContact && policy.allowStarredContacts && !gamingOverridesStarred) {
            val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)
            return RustCallBounceDecision(
                normalizedCallerKey = key,
                filterAction = RustCallFilterAction.ALLOW_RING_BREAKTHROUGH,
                dispatchAutoReply = false,
                updatedLastCallTimestampMs = null,
                updatedLastAutoReplyTimestampMs = lastAutoReplyToCallerMs,
                reason = "Starred priority contact allowed through shield",
                executionMicros = elapsedMicros
            )
        }

        if (policy.allowRepeatCallersWithin3m && key.isNotEmpty() && key != "unknown") {
            if (lastIncomingCallMs != null && lastIncomingCallMs > 0L &&
                (nowMs - lastIncomingCallMs) <= REPEAT_CALLER_WINDOW_MS
            ) {
                val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)
                return RustCallBounceDecision(
                    normalizedCallerKey = key,
                    filterAction = RustCallFilterAction.ALLOW_RING_BREAKTHROUGH,
                    dispatchAutoReply = false,
                    updatedLastCallTimestampMs = null,
                    updatedLastAutoReplyTimestampMs = lastAutoReplyToCallerMs,
                    reason = "Second call within 180s repeat-caller emergency window",
                    executionMicros = elapsedMicros
                )
            }
        }

        val sendReply = shouldDispatchAutoReply(
            normalizedKey = key,
            lastOutgoingCallMs = lastOutgoingCallToCallerMs,
            lastAutoReplyMs = lastAutoReplyToCallerMs,
            nowMs = nowMs,
            autoReplyEnabled = policy.autoReplyEnabled
        )
        val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)

        return RustCallBounceDecision(
            normalizedCallerKey = key,
            filterAction = RustCallFilterAction.BOUNCE_AND_DECLINE_CALL,
            dispatchAutoReply = sendReply,
            updatedLastCallTimestampMs = nowMs,
            updatedLastAutoReplyTimestampMs = if (sendReply) nowMs else lastAutoReplyToCallerMs,
            reason = if (sendReply) {
                "Call bounced • Auto-reply dispatched (3m cooldown started)"
            } else {
                "Call bounced silently (auto-reply suppressed by cooldown or outgoing guard)"
            },
            executionMicros = elapsedMicros
        )
    }

    fun shouldDispatchAutoReply(
        normalizedKey: String,
        lastOutgoingCallMs: Long?,
        lastAutoReplyMs: Long?,
        nowMs: Long,
        autoReplyEnabled: Boolean
    ): Boolean {
        if (!autoReplyEnabled || normalizedKey.isEmpty() || normalizedKey == "unknown") {
            return false
        }
        if (lastOutgoingCallMs != null && lastOutgoingCallMs > 0L &&
            (nowMs - lastOutgoingCallMs) <= OUTGOING_CALL_EXEMPTION_MS
        ) {
            return false
        }
        if (lastAutoReplyMs != null && lastAutoReplyMs > 0L &&
            (nowMs - lastAutoReplyMs) <= AUTO_REPLY_COOLDOWN_MS
        ) {
            return false
        }
        return true
    }

    // =========================================================================
    // EMBEDDED PORTABLE RUST CRATE SOURCE PREVIEWS FOR IN-APP INSPECTOR
    // =========================================================================

    val FOCUS_FSM_RS_SNIPPET = """
// /rust_core/src/focus_fsm.rs
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum FocusMode {
    Off = 0, DoNotDisturb = 1, Gaming = 2,
    Work = 3, Sleep = 4, Cinema = 5,
}

pub fn step_focus_state_machine(
    current: &FocusEngineState,
    event: FocusEvent,
) -> TransitionOutcome {
    // Pure algebraic transition:
    // ManualSetMode | QuickTileToggle | ForegroundAppObserved
    // ConfirmPendingGamePrompt | DeclinePendingGamePrompt | EvaluateScheduleBoundary
    // -> TransitionOutcome { state, commands, summary }
}
""".trimIndent()

    val NOTIF_CLASSIFIER_RS_SNIPPET = """
// /rust_core/src/notif_classifier.rs
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum NotificationCategory {
    SecurityAndUrgent = 0, DirectMessages = 1, WorkAndCalendar = 2,
    SocialAndCommunity = 3, NewsAndMedia = 4, PromotionsAndDeals = 5,
}

pub fn classify_and_evaluate_notification(
    package_name: &str, title: &str, body: &str,
    rules: &PriorityRuleTable,
    category_priority_override: Option<PriorityLevel>,
) -> ClassificationOutcome { /* P1..P4 -> AllowAll | MuteSilent | HideCompletely */ }
""".trimIndent()

    val CALL_BOUNCER_RS_SNIPPET = """
// /rust_core/src/call_bouncer.rs
pub const REPEAT_CALLER_WINDOW_MS: i64 = 180_000;    // 3m breakthrough
pub const AUTO_REPLY_COOLDOWN_MS: i64 = 180_000;     // 3m reply cooldown
pub const OUTGOING_CALL_EXEMPTION_MS: i64 = 600_000; // 10m dial guard

pub fn evaluate_incoming_call(
    raw_caller: &str, is_starred_contact: bool,
    last_incoming_call_ms: Option<i64>,
    last_outgoing_call_to_caller_ms: Option<i64>,
    last_auto_reply_to_caller_ms: Option<i64>,
    now_ms: i64, policy: &CallPolicyConfig,
) -> CallBounceDecision { /* E.164 trailing-9 key + breakthrough + cooldown */ }
""".trimIndent()
}
