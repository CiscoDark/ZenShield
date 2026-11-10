package com.example

import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import com.example.ui.CINEMA_DURATION_PRESETS
import org.junit.Assert.*
import org.junit.Test

/**
 * Local unit tests verifying core Sentinel & Cinema Mode configuration logic.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun cinemaMode_presetsAndProfileConfigurationAreValid() {
        assertEquals("Cinema", FocusProfile.CINEMA.displayName)
        assertEquals(120L, DurationOption.FOR_TWO_HOURS.durationMinutes)
        assertEquals(150L, DurationOption.FOR_TWO_AND_HALF_HOURS.durationMinutes)
        assertEquals(180L, DurationOption.FOR_THREE_HOURS.durationMinutes)

        val labels = CINEMA_DURATION_PRESETS.map { it.label }
        assertTrue(labels.contains("2h"))
        assertTrue(labels.contains("2.5h"))
        assertTrue(labels.contains("3h"))
        assertTrue(FocusStateManager.DEFAULT_CINEMA_AUTO_REPLY.contains("movie theater", ignoreCase = true))
    }

    @Test
    fun gamingActivationPrompt_defaultCountdownAndStateAreValid() {
        assertEquals(5, FocusStateManager.GAME_CONFIRMATION_COUNTDOWN_SECONDS)
        val prompt = com.example.ui.GamingActivationPromptState(
            packageName = "com.activision.callofduty.shooter",
            gameLabel = "Call of Duty Mobile"
        )
        assertEquals(5, prompt.remainingSeconds)
        assertEquals(5, prompt.totalSeconds)
    }

    @Test
    fun gamingWeeklyComparison_durationFormattingAndSummaryAreValid() {
        assertEquals("45m", com.example.ui.formatGamingDurationMinutes(45))
        assertEquals("2h 0m", com.example.ui.formatGamingDurationMinutes(120))
        assertEquals("2h 30m", com.example.ui.formatGamingDurationMinutes(150))
    }

    @Test
    fun sleepSchedule_defaultsAndOvernightConfigurationAreValid() {
        val defaultState = com.example.core.FocusSessionState()
        assertFalse(defaultState.sleepScheduleEnabled)
        assertEquals(22, defaultState.sleepScheduleStartHour)
        assertEquals(30, defaultState.sleepScheduleStartMinute)
        assertEquals(7, defaultState.sleepScheduleEndHour)
        assertEquals(0, defaultState.sleepScheduleEndMinute)
        assertEquals(7, defaultState.sleepScheduleDays.size)
        assertEquals(
            com.example.core.FocusSessionState.DEFAULT_SLEEP_DAYS,
            defaultState.sleepScheduleDays
        )
    }

    @Test
    fun notificationCategorization_andPriorityLevelRulesAreValid() {
        val defaultState = com.example.core.FocusSessionState()
        assertTrue(defaultState.globalFocusRulesEnabled)
        assertEquals(
            com.example.core.GlobalFocusRuleAction.ALLOW_ALL,
            defaultState.priorityLevelRules[com.example.core.NotificationPriorityLevel.CRITICAL]
        )
        assertEquals(
            com.example.core.GlobalFocusRuleAction.MUTE_SILENT,
            defaultState.priorityLevelRules[com.example.core.NotificationPriorityLevel.HIGH]
        )
        assertEquals(
            com.example.core.GlobalFocusRuleAction.HIDE_COMPLETELY,
            defaultState.priorityLevelRules[com.example.core.NotificationPriorityLevel.MEDIUM]
        )
        assertEquals(
            com.example.core.GlobalFocusRuleAction.HIDE_COMPLETELY,
            defaultState.priorityLevelRules[com.example.core.NotificationPriorityLevel.LOW]
        )
        assertEquals(
            com.example.core.NotificationPriorityLevel.CRITICAL,
            defaultState.categoryPriorityOverrides[com.example.core.NotificationCategory.SECURITY_AND_URGENT]
        )
        assertEquals(
            com.example.core.NotificationPriorityLevel.LOW,
            defaultState.categoryPriorityOverrides[com.example.core.NotificationCategory.PROMOTIONS_AND_DEALS]
        )
    }

    @Test
    fun gamingModeSessionSuppression_andHeadsUpPromptActionsAreValid() {
        val initialState = com.example.core.FocusSessionState()
        assertNull(initialState.activeGamePackage)
        assertNull(initialState.declinedGamePackageForSession)
        assertFalse(initialState.manuallyDismissedGamingWhilstInGame)
        assertFalse(initialState.isPendingGamePreview)

        val suppressedState = initialState.copy(
            activeGamePackage = "com.supercell.clashofclans",
            declinedGamePackageForSession = "com.supercell.clashofclans",
            manuallyDismissedGamingWhilstInGame = true
        )
        assertEquals("com.supercell.clashofclans", suppressedState.declinedGamePackageForSession)
        assertTrue(suppressedState.manuallyDismissedGamingWhilstInGame)

        assertEquals(9002, com.example.ui.GamingModePopupManager.PROMPT_NOTIFICATION_ID)
        assertEquals(
            "com.example.zenshield.ACTION_CONFIRM_GAME_PROMPT",
            com.example.services.ZenShieldForegroundService.ACTION_CONFIRM_GAME_PROMPT
        )
        assertEquals(
            "com.example.zenshield.ACTION_DECLINE_GAME_PROMPT",
            com.example.services.ZenShieldForegroundService.ACTION_DECLINE_GAME_PROMPT
        )

        val m3Banner = com.example.ui.GamingPopupBannerState()
        assertEquals("Gaming Mode Active", m3Banner.title)
        assertEquals(0xFFA8C7FA, m3Banner.accentColorHex)
    }

    @Test
    fun quickSettingsTiles_gamingAndSleepDirectToggleLogicIsValid() {
        // Gaming Mode Quick Settings Tile toggles GAMING on from OFF or any other profile, and OFF when already GAMING
        assertEquals(
            FocusProfile.GAMING,
            com.example.services.GamingModeTileService.computeToggledGamingProfile(FocusProfile.OFF)
        )
        assertEquals(
            FocusProfile.GAMING,
            com.example.services.GamingModeTileService.computeToggledGamingProfile(FocusProfile.SLEEP)
        )
        assertEquals(
            FocusProfile.OFF,
            com.example.services.GamingModeTileService.computeToggledGamingProfile(FocusProfile.GAMING)
        )

        // Sleep Mode Quick Settings Tile toggles SLEEP on from OFF or any other profile, and OFF when already SLEEP
        assertEquals(
            FocusProfile.SLEEP,
            com.example.services.SleepModeTileService.computeToggledSleepProfile(FocusProfile.OFF)
        )
        assertEquals(
            FocusProfile.SLEEP,
            com.example.services.SleepModeTileService.computeToggledSleepProfile(FocusProfile.GAMING)
        )
        assertEquals(
            FocusProfile.OFF,
            com.example.services.SleepModeTileService.computeToggledSleepProfile(FocusProfile.SLEEP)
        )
    }

    @Test
    fun focusSessionHistory_roomSeedAndRechartsAggregationAreValid() {
        val seedSessions = com.example.core.FocusSessionHistoryRepository.buildRealisticSeedSessions()
        assertTrue(seedSessions.isNotEmpty())

        val modeAllocations = com.example.core.FocusSessionHistoryRepository.aggregateByMode(seedSessions)
        assertEquals(5, modeAllocations.size)
        assertTrue(modeAllocations.all { it.totalMinutes > 0 && it.sessionCount > 0 })

        val dailyBreakdown = com.example.core.FocusSessionHistoryRepository.aggregateDailyBreakdown(seedSessions)
        assertEquals(7, dailyBreakdown.size)
        assertTrue(dailyBreakdown.last().isToday)
        assertEquals(
            seedSessions.sumOf { it.durationMinutes },
            modeAllocations.sumOf { it.totalMinutes }
        )
    }

    @Test
    fun rustCoreEngine_fsmClassifierAndCallBouncerAreValid() {
        // 1. Engine 1: Focus FSM manual turn-off while in game suppresses re-activation until exit
        val initialFsm = com.example.core.SentinalRustCoreBridge.RustFocusEngineState(
            activeMode = FocusProfile.GAMING,
            activeGamePackage = "com.supercell.clashofclans",
            sessionStartMs = 1_000_000L
        )
        val afterManualOff = com.example.core.SentinalRustCoreBridge.stepFocusStateMachine(
            current = initialFsm,
            event = com.example.core.SentinalRustCoreBridge.RustFocusEvent.ManualSetMode(
                targetMode = FocusProfile.OFF,
                nowMs = 2_200_000L
            )
        )
        assertEquals(FocusProfile.OFF, afterManualOff.state.activeMode)
        assertTrue(afterManualOff.state.manuallyDismissedGamingWhilstInGame)
        assertEquals("com.supercell.clashofclans", afterManualOff.state.declinedGamePackageForSession)

        // Same game still in foreground -> suppressed
        val sameGamePoll = com.example.core.SentinalRustCoreBridge.stepFocusStateMachine(
            current = afterManualOff.state,
            event = com.example.core.SentinalRustCoreBridge.RustFocusEvent.ForegroundAppObserved(
                packageName = "com.supercell.clashofclans",
                isGame = true,
                nowMs = 2_205_000L
            )
        )
        assertEquals(FocusProfile.OFF, sameGamePoll.state.activeMode)
        assertTrue(sameGamePoll.commands.isEmpty())

        // 2. Engine 2: Notification Priority Classifier (2FA OTP vs Promo)
        val otpOutcome = com.example.core.SentinalRustCoreBridge.classifyAndEvaluateNotification(
            packageName = "com.google.android.apps.authenticator2",
            title = "Security Verification Code",
            body = "Your one-time OTP code is 491-820"
        )
        assertEquals(com.example.core.NotificationCategory.SECURITY_AND_URGENT, otpOutcome.category)
        assertEquals(com.example.core.NotificationPriorityLevel.CRITICAL, otpOutcome.priority)
        assertEquals(com.example.core.GlobalFocusRuleAction.ALLOW_ALL, otpOutcome.action)

        val promoOutcome = com.example.core.SentinalRustCoreBridge.classifyAndEvaluateNotification(
            packageName = "com.amazon.mShop.android.shopping",
            title = "50% OFF Flash Sale",
            body = "Use coupon code SAVE50 tonight"
        )
        assertEquals(com.example.core.NotificationCategory.PROMOTIONS_AND_DEALS, promoOutcome.category)
        assertEquals(com.example.core.NotificationPriorityLevel.LOW, promoOutcome.priority)
        assertEquals(com.example.core.GlobalFocusRuleAction.HIDE_COMPLETELY, promoOutcome.action)

        // 3. Engine 3: Call Bouncer E.164 normalization, 180s repeat breakthrough, and 600s outgoing guard
        val firstCall = com.example.core.SentinalRustCoreBridge.evaluateIncomingCall(
            rawCaller = "+1 (415) 555-0199",
            isStarredContact = false,
            lastIncomingCallMs = null,
            lastOutgoingCallToCallerMs = null,
            lastAutoReplyToCallerMs = null,
            nowMs = 1_000_000L
        )
        assertEquals("155550199", firstCall.normalizedCallerKey)
        assertEquals(
            com.example.core.SentinalRustCoreBridge.RustCallFilterAction.BOUNCE_AND_DECLINE_CALL,
            firstCall.filterAction
        )
        assertTrue(firstCall.dispatchAutoReply)

        val secondCallWithin3m = com.example.core.SentinalRustCoreBridge.evaluateIncomingCall(
            rawCaller = "415-555-0199",
            isStarredContact = false,
            lastIncomingCallMs = firstCall.updatedLastCallTimestampMs,
            lastOutgoingCallToCallerMs = null,
            lastAutoReplyToCallerMs = firstCall.updatedLastAutoReplyTimestampMs,
            nowMs = 1_090_000L
        )
        assertEquals(
            com.example.core.SentinalRustCoreBridge.RustCallFilterAction.ALLOW_RING_BREAKTHROUGH,
            secondCallWithin3m.filterAction
        )
        assertFalse(secondCallWithin3m.dispatchAutoReply)
    }
}
