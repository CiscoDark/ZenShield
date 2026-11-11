package com.example.core.jni

import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.GlobalFocusRuleAction
import com.example.core.NotificationCategory
import com.example.core.NotificationPriorityLevel
import com.example.core.SentinalRustCoreBridge
import java.util.Locale

/**
 * Low-level JNI C-ABI bindings corresponding to `/rust_core/src/jni_bridge.rs`.
 *
 * Each `@JvmStatic external fun` maps 1:1 to a `#[no_mangle] pub unsafe extern "system" fn`
 * symbol in `sentinal_rust_core`:
 * - `Java_com_example_core_jni_SentinalRustJniNative_nativeCrateVersion`
 * - `Java_com_example_core_jni_SentinalRustJniNative_nativeStepFocusStateMachine`
 * - `Java_com_example_core_jni_SentinalRustJniNative_nativeEvaluateScheduleWindow`
 * - `Java_com_example_core_jni_SentinalRustJniNative_nativeClassifyNotification`
 * - `Java_com_example_core_jni_SentinalRustJniNative_nativeNormalizeCallerKey`
 * - `Java_com_example_core_jni_SentinalRustJniNative_nativeEvaluateIncomingCall`
 */
object SentinalRustJniNative {

    const val NATIVE_LIBRARY_NAME = "sentinal_rust_core"

    val isNativeLibraryLoaded: Boolean = try {
        System.loadLibrary(NATIVE_LIBRARY_NAME)
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: SecurityException) {
        false
    }

    @JvmStatic
    external fun nativeCrateVersion(): String

    @JvmStatic
    external fun nativeStepFocusStateMachine(
        currentModeOrdinal: Int,
        previousModeBeforeGameOrdinal: Int,
        sessionStartMs: Long,
        expiresAtMs: Long,
        autoGameDetectionEnabled: Boolean,
        askBeforeGameActivation: Boolean,
        isAutoTriggeredByGame: Boolean,
        isAutoTriggeredByWork: Boolean,
        isAutoTriggeredBySleep: Boolean,
        activeGamePackage: String,
        pendingGamePackage: String,
        declinedGamePackageForSession: String,
        manuallyDismissedGamingWhilstInGame: Boolean,
        eventTypeOrdinal: Int,
        eventTargetModeOrdinal: Int,
        eventPackageName: String,
        eventIsGame: Boolean,
        eventDurationMinutes: Int,
        nowMs: Long
    ): String

    @JvmStatic
    external fun nativeEvaluateScheduleWindow(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        currentDayOfWeek: Int,
        yesterdayDayOfWeek: Int,
        activeDaysMask: Int,
        currentHour: Int,
        currentMinute: Int
    ): Boolean

    @JvmStatic
    external fun nativeClassifyNotification(
        packageName: String,
        title: String,
        body: String,
        criticalActionOrd: Int,
        highActionOrd: Int,
        mediumActionOrd: Int,
        lowActionOrd: Int,
        smartUrgentBreakthroughEnabled: Boolean,
        categoryPriorityOverrideRank: Int
    ): String

    @JvmStatic
    external fun nativeNormalizeCallerKey(rawCaller: String): String

    @JvmStatic
    external fun nativeEvaluateIncomingCall(
        rawCaller: String,
        isStarredContact: Boolean,
        lastIncomingCallMs: Long,
        lastOutgoingCallMs: Long,
        lastAutoReplyMs: Long,
        nowMs: Long,
        declineAllCallsRegardless: Boolean,
        strictGamingLockdown: Boolean,
        isGamingModeActive: Boolean,
        allowStarredContacts: Boolean,
        allowRepeatCallersWithin3m: Boolean,
        autoReplyEnabled: Boolean
    ): String
}

/**
 * Kotlin interface exposing the Rust-based Focus State Machine & Schedule Window Evaluator
 * (`focus_fsm.rs`) to the Android application layer.
 */
interface RustFocusStateMachineInterface {
    fun stepStateMachine(
        current: SentinalRustCoreBridge.RustFocusEngineState,
        event: SentinalRustCoreBridge.RustFocusEvent
    ): SentinalRustCoreBridge.RustTransitionOutcome

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
    ): Boolean
}

/**
 * Kotlin interface exposing the Rust-based Notification Priority Classifier & Global Focus Rules
 * (`notif_classifier.rs`) to the Android application layer.
 */
interface RustNotificationClassifierInterface {
    fun classifyAndEvaluateNotification(
        packageName: String,
        title: String,
        body: String,
        priorityRules: Map<NotificationPriorityLevel, GlobalFocusRuleAction> = FocusSessionState.DEFAULT_PRIORITY_LEVEL_RULES,
        categoryOverrides: Map<NotificationCategory, NotificationPriorityLevel> = FocusSessionState.DEFAULT_CATEGORY_PRIORITIES,
        smartUrgentBreakthroughEnabled: Boolean = true
    ): SentinalRustCoreBridge.RustClassificationOutcome

    fun classifyCategoryOnly(
        packageName: String,
        androidCategory: String?,
        title: String,
        body: String
    ): NotificationCategory
}

/**
 * Kotlin interface exposing the Rust-based Call Bouncer Cooldown & Repeat-Caller Matcher
 * (`call_bouncer.rs`) to the Android application layer.
 */
interface RustCallBouncerInterface {
    fun normalizeCallerLookupKey(rawCaller: String): String

    fun evaluateIncomingCall(
        rawCaller: String,
        isStarredContact: Boolean,
        lastIncomingCallMs: Long?,
        lastOutgoingCallToCallerMs: Long?,
        lastAutoReplyToCallerMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        policy: SentinalRustCoreBridge.RustCallPolicyConfig = SentinalRustCoreBridge.RustCallPolicyConfig()
    ): SentinalRustCoreBridge.RustCallBounceDecision
}

/**
 * Zero-reflection JNI wire codec that encodes and decodes the pipe-delimited `jstring` payloads
 * returned by `/rust_core/src/jni_bridge.rs`.
 */
object SentinalRustJniWireCodec {

    fun focusProfileToOrdinal(profile: FocusProfile): Int = when (profile) {
        FocusProfile.OFF -> 0
        FocusProfile.DO_NOT_DISTURB -> 1
        FocusProfile.GAMING -> 2
        FocusProfile.WORK -> 3
        FocusProfile.SLEEP -> 4
        FocusProfile.CINEMA -> 5
    }

    fun focusProfileFromOrdinal(ordinal: Int): FocusProfile = when (ordinal) {
        1 -> FocusProfile.DO_NOT_DISTURB
        2 -> FocusProfile.GAMING
        3 -> FocusProfile.WORK
        4 -> FocusProfile.SLEEP
        5 -> FocusProfile.CINEMA
        else -> FocusProfile.OFF
    }

    fun priorityToRank(priority: NotificationPriorityLevel): Int = priority.rank

    fun priorityFromRank(rank: Int): NotificationPriorityLevel? = when (rank) {
        4 -> NotificationPriorityLevel.CRITICAL
        3 -> NotificationPriorityLevel.HIGH
        2 -> NotificationPriorityLevel.MEDIUM
        1 -> NotificationPriorityLevel.LOW
        else -> null
    }

    fun ruleActionToOrdinal(action: GlobalFocusRuleAction): Int = when (action) {
        GlobalFocusRuleAction.ALLOW_ALL -> 0
        GlobalFocusRuleAction.MUTE_SILENT -> 1
        GlobalFocusRuleAction.HIDE_COMPLETELY -> 2
    }

    fun ruleActionFromOrdinal(ordinal: Int): GlobalFocusRuleAction = when (ordinal) {
        0 -> GlobalFocusRuleAction.ALLOW_ALL
        1 -> GlobalFocusRuleAction.MUTE_SILENT
        else -> GlobalFocusRuleAction.HIDE_COMPLETELY
    }

    fun categoryToOrdinal(category: NotificationCategory): Int = when (category) {
        NotificationCategory.SECURITY_AND_URGENT -> 0
        NotificationCategory.DIRECT_MESSAGES -> 1
        NotificationCategory.WORK_AND_CALENDAR -> 2
        NotificationCategory.SOCIAL_AND_COMMUNITY -> 3
        NotificationCategory.NEWS_AND_MEDIA -> 4
        NotificationCategory.PROMOTIONS_AND_DEALS -> 5
    }

    fun categoryFromOrdinal(ordinal: Int): NotificationCategory = when (ordinal) {
        0 -> NotificationCategory.SECURITY_AND_URGENT
        1 -> NotificationCategory.DIRECT_MESSAGES
        2 -> NotificationCategory.WORK_AND_CALENDAR
        3 -> NotificationCategory.SOCIAL_AND_COMMUNITY
        4 -> NotificationCategory.NEWS_AND_MEDIA
        else -> NotificationCategory.PROMOTIONS_AND_DEALS
    }

    /**
     * Converts a set of Calendar days (1 = Sunday .. 7 = Saturday) into a 7-bit bitmask
     * compatible with `focus_fsm::is_time_within_schedule_window` (`1 << (day - 1)`).
     */
    fun activeDaysToBitmask(activeDays: Set<Int>): Int {
        var mask = 0
        for (day in activeDays) {
            if (day in 1..7) {
                mask = mask or (1 shl (day - 1))
            }
        }
        return mask
    }

    /**
     * Encodes a [SentinalRustCoreBridge.RustTransitionOutcome] into the exact pipe-delimited
     * JNI wire format emitted by `Java_com_example_core_jni_SentinalRustJniNative_nativeStepFocusStateMachine`:
     * `"activeModeOrd|suppressInGame|declinedPkg|activeGamePkg|pendingPkg|commandsCsv|summary"`
     */
    fun encodeTransitionOutcomeToWire(
        outcome: SentinalRustCoreBridge.RustTransitionOutcome
    ): String {
        val commandsCsv = outcome.commands.joinToString(",") { cmd ->
            when (cmd) {
                is SentinalRustCoreBridge.RustEngineCommand.SyncInterruptionFilter ->
                    "SyncInterruptionFilter:${focusProfileToOrdinal(cmd.mode)}"
                is SentinalRustCoreBridge.RustEngineCommand.StartForegroundShield ->
                    "StartForegroundShield:${focusProfileToOrdinal(cmd.mode)}"
                SentinalRustCoreBridge.RustEngineCommand.StopForegroundShield ->
                    "StopForegroundShield"
                is SentinalRustCoreBridge.RustEngineCommand.ShowGameConfirmPrompt ->
                    "ShowGameConfirmPrompt:${cmd.packageName}:${cmd.countdownSeconds}"
                SentinalRustCoreBridge.RustEngineCommand.DismissGameConfirmPrompt ->
                    "DismissGameConfirmPrompt"
                is SentinalRustCoreBridge.RustEngineCommand.ShowGamingActivatedHud ->
                    "ShowGamingActivatedHud:${cmd.packageName}"
                SentinalRustCoreBridge.RustEngineCommand.SyncQuickSettingsTiles ->
                    "SyncQuickSettingsTiles"
                is SentinalRustCoreBridge.RustEngineCommand.LogCompletedSessionToRoom ->
                    "LogCompletedSessionToRoom:${focusProfileToOrdinal(cmd.mode)}:${cmd.durationMinutes}:${cmd.triggerSource}"
            }
        }

        return listOf(
            focusProfileToOrdinal(outcome.state.activeMode).toString(),
            if (outcome.state.manuallyDismissedGamingWhilstInGame) "1" else "0",
            outcome.state.declinedGamePackageForSession.orEmpty(),
            outcome.state.activeGamePackage.orEmpty(),
            outcome.state.pendingGamePackage.orEmpty(),
            commandsCsv,
            outcome.summary.replace('|', '/')
        ).joinToString("|")
    }

    /**
     * Decodes the pipe-delimited JNI wire string from `nativeStepFocusStateMachine` back into
     * a typed [SentinalRustCoreBridge.RustTransitionOutcome].
     */
    fun decodeTransitionOutcomeFromWire(
        baseState: SentinalRustCoreBridge.RustFocusEngineState,
        wirePayload: String,
        executionMicros: Long = 1L
    ): SentinalRustCoreBridge.RustTransitionOutcome {
        val parts = wirePayload.split("|", limit = 7)
        val activeModeOrd = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val suppressInGame = (parts.getOrNull(1)?.toIntOrNull() ?: 0) != 0
        val declinedPkg = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
        val activeGamePkg = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
        val pendingPkg = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
        val commandsRaw = parts.getOrNull(5).orEmpty()
        val summary = parts.getOrNull(6).orEmpty()

        val commands = if (commandsRaw.isBlank()) {
            emptyList()
        } else {
            commandsRaw.split(",").mapNotNull { token ->
                val segs = token.split(":")
                when (segs.firstOrNull()) {
                    "SyncInterruptionFilter" -> {
                        val ord = segs.getOrNull(1)?.toIntOrNull() ?: 0
                        SentinalRustCoreBridge.RustEngineCommand.SyncInterruptionFilter(
                            focusProfileFromOrdinal(ord)
                        )
                    }
                    "StartForegroundShield" -> {
                        val ord = segs.getOrNull(1)?.toIntOrNull() ?: 0
                        SentinalRustCoreBridge.RustEngineCommand.StartForegroundShield(
                            focusProfileFromOrdinal(ord)
                        )
                    }
                    "StopForegroundShield" -> SentinalRustCoreBridge.RustEngineCommand.StopForegroundShield
                    "ShowGameConfirmPrompt" -> {
                        val pkg = segs.getOrNull(1).orEmpty()
                        val secs = segs.getOrNull(2)?.toIntOrNull() ?: 5
                        SentinalRustCoreBridge.RustEngineCommand.ShowGameConfirmPrompt(pkg, secs)
                    }
                    "DismissGameConfirmPrompt" -> SentinalRustCoreBridge.RustEngineCommand.DismissGameConfirmPrompt
                    "ShowGamingActivatedHud" -> {
                        val pkg = segs.getOrNull(1).orEmpty()
                        SentinalRustCoreBridge.RustEngineCommand.ShowGamingActivatedHud(pkg)
                    }
                    "SyncQuickSettingsTiles" -> SentinalRustCoreBridge.RustEngineCommand.SyncQuickSettingsTiles
                    "LogCompletedSessionToRoom" -> {
                        val ord = segs.getOrNull(1)?.toIntOrNull() ?: 0
                        val mins = segs.getOrNull(2)?.toIntOrNull() ?: 1
                        val source = segs.getOrNull(3) ?: "Manual"
                        SentinalRustCoreBridge.RustEngineCommand.LogCompletedSessionToRoom(
                            mode = focusProfileFromOrdinal(ord),
                            durationMinutes = mins,
                            triggerSource = source
                        )
                    }
                    else -> null
                }
            }
        }

        val updatedState = baseState.copy(
            activeMode = focusProfileFromOrdinal(activeModeOrd),
            manuallyDismissedGamingWhilstInGame = suppressInGame,
            declinedGamePackageForSession = declinedPkg,
            activeGamePackage = activeGamePkg,
            pendingGamePackage = pendingPkg,
            pendingGameCountdownSec = if (pendingPkg != null) 5 else null
        )

        return SentinalRustCoreBridge.RustTransitionOutcome(
            state = updatedState,
            commands = commands,
            summary = summary,
            executionMicros = executionMicros
        )
    }

    /**
     * Encodes a [SentinalRustCoreBridge.RustClassificationOutcome] into the pipe-delimited JNI
     * wire format emitted by `nativeClassifyNotification`:
     * `"categoryOrd|priorityRank|actionOrd|matchedUrgentBreakthrough|reason"`
     */
    fun encodeClassificationOutcomeToWire(
        outcome: SentinalRustCoreBridge.RustClassificationOutcome
    ): String {
        return listOf(
            categoryToOrdinal(outcome.category).toString(),
            priorityToRank(outcome.priority).toString(),
            ruleActionToOrdinal(outcome.action).toString(),
            if (outcome.matchedUrgentBreakthrough) "1" else "0",
            outcome.reason.replace('|', '/')
        ).joinToString("|")
    }

    /**
     * Decodes the pipe-delimited JNI wire string from `nativeClassifyNotification` back into
     * a typed [SentinalRustCoreBridge.RustClassificationOutcome].
     */
    fun decodeClassificationOutcomeFromWire(
        wirePayload: String,
        executionMicros: Long = 1L
    ): SentinalRustCoreBridge.RustClassificationOutcome {
        val parts = wirePayload.split("|", limit = 5)
        val categoryOrd = parts.getOrNull(0)?.toIntOrNull() ?: 3
        val priorityRank = parts.getOrNull(1)?.toIntOrNull() ?: 2
        val actionOrd = parts.getOrNull(2)?.toIntOrNull() ?: 1
        val urgent = (parts.getOrNull(3)?.toIntOrNull() ?: 0) != 0
        val reason = parts.getOrNull(4).orEmpty()

        return SentinalRustCoreBridge.RustClassificationOutcome(
            category = categoryFromOrdinal(categoryOrd),
            priority = priorityFromRank(priorityRank) ?: NotificationPriorityLevel.MEDIUM,
            action = ruleActionFromOrdinal(actionOrd),
            matchedUrgentBreakthrough = urgent,
            reason = reason,
            executionMicros = executionMicros
        )
    }

    /**
     * Encodes a [SentinalRustCoreBridge.RustCallBounceDecision] into the pipe-delimited JNI
     * wire format emitted by `nativeEvaluateIncomingCall`:
     * `"normalizedKey|actionOrd|dispatchAutoReply|updatedLastCallMs|updatedLastAutoReplyMs|reason"`
     */
    fun encodeCallBounceDecisionToWire(
        decision: SentinalRustCoreBridge.RustCallBounceDecision
    ): String {
        val actionOrd = when (decision.filterAction) {
            SentinalRustCoreBridge.RustCallFilterAction.ALLOW_RING_BREAKTHROUGH -> 0
            SentinalRustCoreBridge.RustCallFilterAction.BOUNCE_AND_DECLINE_CALL -> 1
        }
        return listOf(
            decision.normalizedCallerKey,
            actionOrd.toString(),
            if (decision.dispatchAutoReply) "1" else "0",
            (decision.updatedLastCallTimestampMs ?: 0L).toString(),
            (decision.updatedLastAutoReplyTimestampMs ?: 0L).toString(),
            decision.reason.replace('|', '/')
        ).joinToString("|")
    }

    /**
     * Decodes the pipe-delimited JNI wire string from `nativeEvaluateIncomingCall` back into
     * a typed [SentinalRustCoreBridge.RustCallBounceDecision].
     */
    fun decodeCallBounceDecisionFromWire(
        wirePayload: String,
        executionMicros: Long = 1L
    ): SentinalRustCoreBridge.RustCallBounceDecision {
        val parts = wirePayload.split("|", limit = 6)
        val normKey = parts.getOrNull(0).orEmpty()
        val actionOrd = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val dispatchReply = (parts.getOrNull(2)?.toIntOrNull() ?: 0) != 0
        val updatedCallMs = parts.getOrNull(3)?.toLongOrNull()?.takeIf { it > 0L }
        val updatedReplyMs = parts.getOrNull(4)?.toLongOrNull()?.takeIf { it > 0L }
        val reason = parts.getOrNull(5).orEmpty()

        val filterAction = if (actionOrd == 0) {
            SentinalRustCoreBridge.RustCallFilterAction.ALLOW_RING_BREAKTHROUGH
        } else {
            SentinalRustCoreBridge.RustCallFilterAction.BOUNCE_AND_DECLINE_CALL
        }

        return SentinalRustCoreBridge.RustCallBounceDecision(
            normalizedCallerKey = normKey,
            filterAction = filterAction,
            dispatchAutoReply = dispatchReply,
            updatedLastCallTimestampMs = updatedCallMs,
            updatedLastAutoReplyTimestampMs = updatedReplyMs,
            reason = reason,
            executionMicros = executionMicros
        )
    }
}

/**
 * Production Android client implementing [RustFocusStateMachineInterface],
 * [RustNotificationClassifierInterface], and [RustCallBouncerInterface].
 *
 * Automatically invokes the native C-ABI functions in [SentinalRustJniNative] when
 * `libsentinal_rust_core.so` is loaded, and seamlessly falls back to the 1:1 algebraic
 * mirror in [SentinalRustCoreBridge] when running on JVM/emulator environments without NDK binaries.
 */
class SentinalRustEngineClient :
    RustFocusStateMachineInterface,
    RustNotificationClassifierInterface,
    RustCallBouncerInterface {

    val isRunningNativeJni: Boolean
        get() = SentinalRustJniNative.isNativeLibraryLoaded

    fun queryCrateRuntimeVersion(): String {
        if (SentinalRustJniNative.isNativeLibraryLoaded) {
            return runCatching { SentinalRustJniNative.nativeCrateVersion() }
                .getOrDefault("${SentinalRustCoreBridge.CRATE_NAME}/${SentinalRustCoreBridge.CRATE_VERSION}-jni")
        }
        return "${SentinalRustCoreBridge.CRATE_NAME}/${SentinalRustCoreBridge.CRATE_VERSION}-jvm-bridge"
    }

    override fun stepStateMachine(
        current: SentinalRustCoreBridge.RustFocusEngineState,
        event: SentinalRustCoreBridge.RustFocusEvent
    ): SentinalRustCoreBridge.RustTransitionOutcome {
        if (SentinalRustJniNative.isNativeLibraryLoaded && event !is SentinalRustCoreBridge.RustFocusEvent.EvaluateScheduleBoundary) {
            val startNs = System.nanoTime()
            val (eventTypeOrd, targetModeOrd, pkgName, isGame, durationMins, nowMs) = when (event) {
                is SentinalRustCoreBridge.RustFocusEvent.ManualSetMode -> EventWireParams(
                    eventTypeOrd = 0,
                    targetModeOrd = SentinalRustJniWireCodec.focusProfileToOrdinal(event.targetMode),
                    pkgName = "",
                    isGame = false,
                    durationMins = event.durationMinutes ?: 0,
                    nowMs = event.nowMs
                )
                is SentinalRustCoreBridge.RustFocusEvent.QuickTileToggle -> EventWireParams(
                    eventTypeOrd = 1,
                    targetModeOrd = SentinalRustJniWireCodec.focusProfileToOrdinal(event.tileMode),
                    pkgName = "",
                    isGame = false,
                    durationMins = 0,
                    nowMs = event.nowMs
                )
                is SentinalRustCoreBridge.RustFocusEvent.ForegroundAppObserved -> EventWireParams(
                    eventTypeOrd = 2,
                    targetModeOrd = 0,
                    pkgName = event.packageName,
                    isGame = event.isGame,
                    durationMins = 0,
                    nowMs = event.nowMs
                )
                is SentinalRustCoreBridge.RustFocusEvent.ConfirmPendingGamePrompt -> EventWireParams(
                    eventTypeOrd = 3,
                    targetModeOrd = 2,
                    pkgName = "",
                    isGame = true,
                    durationMins = 0,
                    nowMs = event.nowMs
                )
                SentinalRustCoreBridge.RustFocusEvent.DeclinePendingGamePrompt -> EventWireParams(
                    eventTypeOrd = 4,
                    targetModeOrd = 0,
                    pkgName = "",
                    isGame = false,
                    durationMins = 0,
                    nowMs = System.currentTimeMillis()
                )
                is SentinalRustCoreBridge.RustFocusEvent.EvaluateScheduleBoundary -> EventWireParams(
                    eventTypeOrd = 0,
                    targetModeOrd = 0,
                    pkgName = "",
                    isGame = false,
                    durationMins = 0,
                    nowMs = event.nowMs
                )
            }

            val nativeResult = runCatching {
                val wire = SentinalRustJniNative.nativeStepFocusStateMachine(
                    currentModeOrdinal = SentinalRustJniWireCodec.focusProfileToOrdinal(current.activeMode),
                    previousModeBeforeGameOrdinal = SentinalRustJniWireCodec.focusProfileToOrdinal(current.previousModeBeforeGame),
                    sessionStartMs = current.sessionStartMs ?: 0L,
                    expiresAtMs = current.expiresAtMs ?: 0L,
                    autoGameDetectionEnabled = current.autoGameDetectionEnabled,
                    askBeforeGameActivation = current.askBeforeGameActivation,
                    isAutoTriggeredByGame = current.isAutoTriggeredByGame,
                    isAutoTriggeredByWork = current.isAutoTriggeredByWork,
                    isAutoTriggeredBySleep = current.isAutoTriggeredBySleep,
                    activeGamePackage = current.activeGamePackage.orEmpty(),
                    pendingGamePackage = current.pendingGamePackage.orEmpty(),
                    declinedGamePackageForSession = current.declinedGamePackageForSession.orEmpty(),
                    manuallyDismissedGamingWhilstInGame = current.manuallyDismissedGamingWhilstInGame,
                    eventTypeOrdinal = eventTypeOrd,
                    eventTargetModeOrdinal = targetModeOrd,
                    eventPackageName = pkgName,
                    eventIsGame = isGame,
                    eventDurationMinutes = durationMins,
                    nowMs = nowMs
                )
                val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)
                SentinalRustJniWireCodec.decodeTransitionOutcomeFromWire(current, wire, elapsedMicros)
            }.getOrNull()

            if (nativeResult != null) {
                return nativeResult
            }
        }

        return SentinalRustCoreBridge.stepFocusStateMachine(current, event)
    }

    override fun isTimeWithinScheduleWindow(
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
        if (SentinalRustJniNative.isNativeLibraryLoaded) {
            val mask = SentinalRustJniWireCodec.activeDaysToBitmask(activeDays)
            val nativeRes = runCatching {
                SentinalRustJniNative.nativeEvaluateScheduleWindow(
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    currentDayOfWeek = currentDayOfWeek,
                    yesterdayDayOfWeek = yesterdayDayOfWeek,
                    activeDaysMask = mask,
                    currentHour = currentHour,
                    currentMinute = currentMinute
                )
            }.getOrNull()
            if (nativeRes != null) {
                return nativeRes
            }
        }

        return SentinalRustCoreBridge.isTimeWithinScheduleWindow(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            currentDayOfWeek = currentDayOfWeek,
            yesterdayDayOfWeek = yesterdayDayOfWeek,
            activeDays = activeDays,
            currentHour = currentHour,
            currentMinute = currentMinute
        )
    }

    override fun classifyAndEvaluateNotification(
        packageName: String,
        title: String,
        body: String,
        priorityRules: Map<NotificationPriorityLevel, GlobalFocusRuleAction>,
        categoryOverrides: Map<NotificationCategory, NotificationPriorityLevel>,
        smartUrgentBreakthroughEnabled: Boolean
    ): SentinalRustCoreBridge.RustClassificationOutcome {
        if (SentinalRustJniNative.isNativeLibraryLoaded) {
            val startNs = System.nanoTime()
            val critOrd = SentinalRustJniWireCodec.ruleActionToOrdinal(
                priorityRules[NotificationPriorityLevel.CRITICAL] ?: GlobalFocusRuleAction.ALLOW_ALL
            )
            val highOrd = SentinalRustJniWireCodec.ruleActionToOrdinal(
                priorityRules[NotificationPriorityLevel.HIGH] ?: GlobalFocusRuleAction.MUTE_SILENT
            )
            val medOrd = SentinalRustJniWireCodec.ruleActionToOrdinal(
                priorityRules[NotificationPriorityLevel.MEDIUM] ?: GlobalFocusRuleAction.HIDE_COMPLETELY
            )
            val lowOrd = SentinalRustJniWireCodec.ruleActionToOrdinal(
                priorityRules[NotificationPriorityLevel.LOW] ?: GlobalFocusRuleAction.HIDE_COMPLETELY
            )

            val nativeOutcome = runCatching {
                val wire = SentinalRustJniNative.nativeClassifyNotification(
                    packageName = packageName,
                    title = title,
                    body = body,
                    criticalActionOrd = critOrd,
                    highActionOrd = highOrd,
                    mediumActionOrd = medOrd,
                    lowActionOrd = lowOrd,
                    smartUrgentBreakthroughEnabled = smartUrgentBreakthroughEnabled,
                    categoryPriorityOverrideRank = 0
                )
                val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)
                SentinalRustJniWireCodec.decodeClassificationOutcomeFromWire(wire, elapsedMicros)
            }.getOrNull()

            if (nativeOutcome != null) {
                return nativeOutcome
            }
        }

        return SentinalRustCoreBridge.classifyAndEvaluateNotification(
            packageName = packageName,
            title = title,
            body = body,
            priorityRules = priorityRules,
            categoryOverrides = categoryOverrides,
            smartUrgentBreakthroughEnabled = smartUrgentBreakthroughEnabled
        )
    }

    override fun classifyCategoryOnly(
        packageName: String,
        androidCategory: String?,
        title: String,
        body: String
    ): NotificationCategory {
        if (androidCategory == android.app.Notification.CATEGORY_ALARM) {
            return NotificationCategory.SECURITY_AND_URGENT
        }
        if (androidCategory == android.app.Notification.CATEGORY_EVENT ||
            androidCategory == android.app.Notification.CATEGORY_REMINDER ||
            androidCategory == android.app.Notification.CATEGORY_EMAIL
        ) {
            return NotificationCategory.WORK_AND_CALENDAR
        }
        if (androidCategory == android.app.Notification.CATEGORY_MESSAGE) {
            return NotificationCategory.DIRECT_MESSAGES
        }
        if (androidCategory == android.app.Notification.CATEGORY_PROMO) {
            return NotificationCategory.PROMOTIONS_AND_DEALS
        }
        if (androidCategory == android.app.Notification.CATEGORY_SOCIAL) {
            return NotificationCategory.SOCIAL_AND_COMMUNITY
        }

        return classifyAndEvaluateNotification(
            packageName = packageName,
            title = title,
            body = body
        ).category
    }

    override fun normalizeCallerLookupKey(rawCaller: String): String {
        if (SentinalRustJniNative.isNativeLibraryLoaded) {
            val nativeKey = runCatching {
                SentinalRustJniNative.nativeNormalizeCallerKey(rawCaller)
            }.getOrNull()
            if (nativeKey != null) {
                return nativeKey
            }
        }
        return SentinalRustCoreBridge.normalizeCallerLookupKey(rawCaller)
    }

    override fun evaluateIncomingCall(
        rawCaller: String,
        isStarredContact: Boolean,
        lastIncomingCallMs: Long?,
        lastOutgoingCallToCallerMs: Long?,
        lastAutoReplyToCallerMs: Long?,
        nowMs: Long,
        policy: SentinalRustCoreBridge.RustCallPolicyConfig
    ): SentinalRustCoreBridge.RustCallBounceDecision {
        if (SentinalRustJniNative.isNativeLibraryLoaded) {
            val startNs = System.nanoTime()
            val nativeDecision = runCatching {
                val wire = SentinalRustJniNative.nativeEvaluateIncomingCall(
                    rawCaller = rawCaller,
                    isStarredContact = isStarredContact,
                    lastIncomingCallMs = lastIncomingCallMs ?: 0L,
                    lastOutgoingCallMs = lastOutgoingCallToCallerMs ?: 0L,
                    lastAutoReplyMs = lastAutoReplyToCallerMs ?: 0L,
                    nowMs = nowMs,
                    declineAllCallsRegardless = policy.declineAllCallsRegardless,
                    strictGamingLockdown = policy.strictGamingLockdown,
                    isGamingModeActive = policy.isGamingModeActive,
                    allowStarredContacts = policy.allowStarredContacts,
                    allowRepeatCallersWithin3m = policy.allowRepeatCallersWithin3m,
                    autoReplyEnabled = policy.autoReplyEnabled
                )
                val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)
                SentinalRustJniWireCodec.decodeCallBounceDecisionFromWire(wire, elapsedMicros)
            }.getOrNull()

            if (nativeDecision != null) {
                return nativeDecision
            }
        }

        return SentinalRustCoreBridge.evaluateIncomingCall(
            rawCaller = rawCaller,
            isStarredContact = isStarredContact,
            lastIncomingCallMs = lastIncomingCallMs,
            lastOutgoingCallToCallerMs = lastOutgoingCallToCallerMs,
            lastAutoReplyToCallerMs = lastAutoReplyToCallerMs,
            nowMs = nowMs,
            policy = policy
        )
    }

    private data class EventWireParams(
        val eventTypeOrd: Int,
        val targetModeOrd: Int,
        val pkgName: String,
        val isGame: Boolean,
        val durationMins: Int,
        val nowMs: Long
    )

    companion object {
        @Volatile
        private var INSTANCE: SentinalRustEngineClient? = null

        fun getInstance(): SentinalRustEngineClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SentinalRustEngineClient().also { INSTANCE = it }
            }
        }

        val JNI_BRIDGE_RS_SNIPPET = """
// /rust_core/src/jni_bridge.rs & SentinalRustJni.kt
#[no_mangle]
pub unsafe extern "system" fn
Java_com_example_core_jni_SentinalRustJniNative_nativeStepFocusStateMachine(
    env: *mut JNIEnv, _class: jclass, current_mode_ordinal: jint, ...
) -> jstring { /* Pipe-delimited zero-reflection wire codec */ }

#[no_mangle]
pub unsafe extern "system" fn
Java_com_example_core_jni_SentinalRustJniNative_nativeClassifyNotification(
    env: *mut JNIEnv, _class: jclass, package_name: jstring, title: jstring, ...
) -> jstring { /* Returns "categoryOrd|priorityRank|actionOrd|urgent|reason" */ }
""".trimIndent()
    }
}
