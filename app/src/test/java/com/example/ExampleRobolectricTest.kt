package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sentinal", appName)
    }

    @Test
    fun `focus state manager sets and retrieves active profile`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.DO_NOT_DISTURB, DurationOption.FOR_ONE_HOUR)
        assertEquals(FocusProfile.DO_NOT_DISTURB, stateManager.getActiveProfile())
        assertTrue(stateManager.state.value.expiresAtMillis != null)

        stateManager.setProfile(FocusProfile.OFF)
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())
    }

    @Test
    fun `three minute repeat call engine records and validates breakthrough`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)
        val callPrefs = stateManager.getCallSharedPreferences()

        val testNumber = "+15551234567"
        val key = "last_call_timestamp_$testNumber"

        // First call: timestamp is recorded
        val now = System.currentTimeMillis()
        callPrefs.edit().putLong(key, now).apply()

        // Immediate second call (30 seconds later): within 3 minutes
        val callTwoTime = now + 30_000L
        val lastTimestamp = callPrefs.getLong(key, 0L)
        val isBreakthrough = (callTwoTime - lastTimestamp) <= FocusStateManager.THREE_MINUTES_MS
        assertTrue("Second call within 30s should qualify as breakthrough", isBreakthrough)

        // Repeat caller beyond 3 minutes (e.g. 4 minutes later)
        val callThreeTime = now + 240_000L
        val isExpired = (callThreeTime - lastTimestamp) > FocusStateManager.THREE_MINUTES_MS
        assertTrue("Call after 4 minutes should exceed 3-minute window", isExpired)
    }

    @Test
    fun `gaming lockdown setting persists and blocks calls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setStrictGamingLockdownEnabled(true)
        assertTrue(stateManager.isStrictGamingLockdown())

        stateManager.setProfile(FocusProfile.GAMING)
        val isLockdownActive = (stateManager.getActiveProfile() == FocusProfile.GAMING && stateManager.isStrictGamingLockdown())
        assertTrue("Gaming lockdown must be active when in GAMING profile", isLockdownActive)

        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `voip repeat breakthrough detects second call within 3 minutes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val caller = "Alice"
        // 1st call: recorded, returns false (not breakthrough yet)
        val firstResult = stateManager.checkVoipRepeatBreakthrough(caller)
        assertFalse("First VoIP call is not a breakthrough", firstResult)

        // 2nd call: within 3 minutes, returns true
        val secondResult = stateManager.checkVoipRepeatBreakthrough(caller)
        assertTrue("Second VoIP call within 3 mins must breakthrough", secondResult)
    }

    @Test
    fun `auto reply respects 30 minute cooldown`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val sender = "Bob"
        assertTrue("Should allow sending auto-reply initially", stateManager.shouldSendAutoReply(sender))

        stateManager.recordAutoReplySent(sender)
        assertFalse("Should block sending another auto-reply immediately", stateManager.shouldSendAutoReply(sender))
    }

    @Test
    fun `tile cycle transitions properly through all profiles`() {
        val cycle = listOf(
            FocusProfile.OFF to FocusProfile.DO_NOT_DISTURB,
            FocusProfile.DO_NOT_DISTURB to FocusProfile.GAMING,
            FocusProfile.GAMING to FocusProfile.WORK,
            FocusProfile.WORK to FocusProfile.SLEEP,
            FocusProfile.SLEEP to FocusProfile.OFF
        )

        for ((current, next) in cycle) {
            val evaluated = when (current) {
                FocusProfile.OFF -> FocusProfile.DO_NOT_DISTURB
                FocusProfile.DO_NOT_DISTURB -> FocusProfile.GAMING
                FocusProfile.GAMING -> FocusProfile.WORK
                FocusProfile.WORK -> FocusProfile.SLEEP
                FocusProfile.SLEEP -> FocusProfile.OFF
                FocusProfile.CINEMA -> FocusProfile.OFF
            }
            assertEquals("Cycle from $current must lead to $next", next, evaluated)
        }
    }

    @Test
    fun `boot completed receiver restores active profile without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.WORK, DurationOption.UNTIL_TURNED_OFF)
        assertEquals(FocusProfile.WORK, stateManager.getActiveProfile())

        val receiver = com.example.receivers.BootCompletedReceiver()
        val intent = android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)

        assertEquals(FocusProfile.WORK, stateManager.getActiveProfile())
        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `per profile app whitelist stores and verifies packages`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val pkg = "com.google.android.talk"
        assertFalse(stateManager.isPackageWhitelisted(FocusProfile.WORK, pkg))

        stateManager.toggleWhitelistedPackage(FocusProfile.WORK, pkg)
        assertTrue(stateManager.isPackageWhitelisted(FocusProfile.WORK, pkg))

        // Ensure other profile doesn't inherit unless explicitly added
        stateManager.toggleWhitelistedPackage(FocusProfile.WORK, pkg)
        assertFalse(stateManager.isPackageWhitelisted(FocusProfile.WORK, pkg))
    }

    @Test
    fun `auto game detection triggers gaming mode and reverts on exit`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        // Start in WORK profile
        stateManager.setProfile(FocusProfile.WORK, DurationOption.UNTIL_TURNED_OFF)
        assertEquals(FocusProfile.WORK, stateManager.getActiveProfile())
        stateManager.setAutoGameDetectionEnabled(true)

        // Launch game
        val gamePackage = "com.epicgames.portal"
        stateManager.handleGameLaunched(gamePackage)

        assertEquals(FocusProfile.GAMING, stateManager.getActiveProfile())
        assertEquals(FocusProfile.WORK, stateManager.state.value.previousProfileBeforeGame)
        assertTrue(stateManager.state.value.isAutoTriggeredByGame)

        // Exit game
        stateManager.handleGameExited()

        // Must revert to WORK
        assertEquals(FocusProfile.WORK, stateManager.getActiveProfile())
        assertFalse(stateManager.state.value.isAutoTriggeredByGame)

        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `custom game packages can be added removed and persist in state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val customEmulator = "org.ppsspp.ppsspp"
        assertFalse(stateManager.getCustomGamePackages().contains(customEmulator))

        stateManager.addCustomGamePackage(customEmulator)
        assertTrue(stateManager.getCustomGamePackages().contains(customEmulator))
        assertTrue(stateManager.state.value.customGamePackages.contains(customEmulator))

        stateManager.removeCustomGamePackage(customEmulator)
        assertFalse(stateManager.getCustomGamePackages().contains(customEmulator))
        assertFalse(stateManager.state.value.customGamePackages.contains(customEmulator))
    }

    @Test
    fun `zero airtime voip reply respects 30 minute cooldown without carrier sms`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val caller = "WhatsApp:Alex"
        assertTrue(stateManager.shouldSendAutoReply(caller))

        stateManager.recordAutoReplySent(caller)
        assertFalse("Should be in cooldown immediately after sending", stateManager.shouldSendAutoReply(caller))
    }

    @Test
    fun `cycleNextActiveProfile correctly loops through active profiles`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.DO_NOT_DISTURB)
        assertEquals(FocusProfile.DO_NOT_DISTURB, stateManager.getActiveProfile())

        stateManager.cycleNextActiveProfile(context)
        assertEquals(FocusProfile.GAMING, stateManager.getActiveProfile())

        stateManager.cycleNextActiveProfile(context)
        assertEquals(FocusProfile.WORK, stateManager.getActiveProfile())

        stateManager.cycleNextActiveProfile(context)
        assertEquals(FocusProfile.SLEEP, stateManager.getActiveProfile())

        stateManager.cycleNextActiveProfile(context)
        assertEquals(FocusProfile.DO_NOT_DISTURB, stateManager.getActiveProfile())

        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `adjustRemainingTime increments and decrements timer with minimum clamp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.WORK)
        // Untimed initially
        assertEquals(null, stateManager.state.value.expiresAtMillis)

        // Adding 15 min creates expiry from now
        val beforeAdd = System.currentTimeMillis()
        stateManager.adjustRemainingTime(context, 15)
        val expiresAfter15 = stateManager.state.value.expiresAtMillis
        assertTrue(expiresAfter15 != null)
        assertTrue(expiresAfter15!! >= beforeAdd + (14 * 60_000L))

        // Adding another 30 min
        stateManager.adjustRemainingTime(context, 30)
        val expiresAfter45 = stateManager.state.value.expiresAtMillis
        assertTrue(expiresAfter45!! >= expiresAfter15 + (29 * 60_000L))

        // Subtracting 15 min
        stateManager.adjustRemainingTime(context, -15)
        val expiresAfterSub = stateManager.state.value.expiresAtMillis
        assertTrue(expiresAfterSub!! < expiresAfter45)

        stateManager.setProfile(FocusProfile.OFF)
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())
    }

    @Test
    fun `users can edit custom auto reply message template and placeholder formatting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val customTemplate = "Hey! I am currently studying in %s mode. Text me URGENT if needed."
        stateManager.setAutoReplyMessageTemplate(customTemplate)

        assertEquals(customTemplate, stateManager.state.value.autoReplyMessageTemplate)
        val formatted = stateManager.getAutoReplyMessage(FocusProfile.WORK)
        assertTrue(formatted.contains("Work mode"))

        // Reset to default
        stateManager.resetAutoReplyTemplateToDefault()
        assertEquals(FocusStateManager.DEFAULT_AUTO_REPLY_TEMPLATE, stateManager.state.value.autoReplyMessageTemplate)
    }

    @Test
    fun `users can set dedicated per-profile custom auto reply message overrides`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val gamingCustomMessage = "🎮 In a tournament right now! Do not call unless life or death."
        stateManager.setPerProfileAutoReplyMessage(FocusProfile.GAMING, gamingCustomMessage)

        // Gaming should return the exact custom message
        assertEquals(gamingCustomMessage, stateManager.getAutoReplyMessage(FocusProfile.GAMING))

        // Work should still use the universal template
        val workMessage = stateManager.getAutoReplyMessage(FocusProfile.WORK)
        assertFalse(workMessage.contains("tournament"))
        assertTrue(workMessage.contains("Work"))

        // Clear override
        stateManager.setPerProfileAutoReplyMessage(FocusProfile.GAMING, "")
        val restoredGamingMessage = stateManager.getAutoReplyMessage(FocusProfile.GAMING)
        assertTrue(restoredGamingMessage.contains("Gaming Focus active"))
    }

    @Test
    fun `auto activate game mode DND on game launch activates and reverts cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.OFF)
        stateManager.setAutoGameDetectionEnabled(true)
        assertTrue(stateManager.state.value.autoGameDetectionEnabled)

        // Simulate game launch
        stateManager.handleGameLaunched("com.example.game")
        assertEquals(FocusProfile.GAMING, stateManager.getActiveProfile())
        assertTrue(stateManager.state.value.isAutoTriggeredByGame)

        // Simulate game exit
        stateManager.handleGameExited()
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())
        assertFalse(stateManager.state.value.isAutoTriggeredByGame)
    }

    @Test
    fun `decline all calls regardless option persists and updates state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setDeclineAllCallsRegardless(true)
        assertTrue("declineAllCallsRegardless must be true", stateManager.isDeclineAllCallsRegardless())
        assertTrue("state property must be true", stateManager.state.value.declineAllCallsRegardless)

        stateManager.setDeclineAllCallsRegardless(false)
        assertFalse("declineAllCallsRegardless must be false", stateManager.isDeclineAllCallsRegardless())
        assertFalse("state property must be false", stateManager.state.value.declineAllCallsRegardless)
    }

    @Test
    fun `game exit deactivation scheduling and cancellation behaves correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.OFF)
        stateManager.setAutoGameDetectionEnabled(true)

        // Launch game
        stateManager.handleGameLaunched("com.example.game")
        assertEquals(FocusProfile.GAMING, stateManager.getActiveProfile())
        assertFalse(stateManager.isGameExitedDeactivationScheduled())

        // Schedule deactivation
        stateManager.scheduleGameExitedDeactivation(30_000L)
        assertTrue(stateManager.isGameExitedDeactivationScheduled())

        // Cancel deactivation (e.g. user resumes game)
        stateManager.cancelGameExitedDeactivation()
        assertFalse(stateManager.isGameExitedDeactivationScheduled())
        assertEquals(null, stateManager.state.value.gameExitCountdownSeconds)
        assertEquals(FocusProfile.GAMING, stateManager.getActiveProfile())

        // Direct exit deactivation reverts profile
        stateManager.handleGameExited()
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())
    }

    @Test
    fun `outgoing call records timestamp and suppresses auto-reply for recipient`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val recipient = "Charlie Brown"

        // Initially without outgoing call, shouldSendAutoReply is true
        assertTrue("Auto reply should initially be allowed", stateManager.shouldSendAutoReply(recipient))

        // Record outgoing call placed by user
        stateManager.recordOutgoingCall(recipient)

        // isRecentOutgoingCall must return true
        assertTrue("isRecentOutgoingCall must be true for dialed recipient", stateManager.isRecentOutgoingCall(recipient))

        // shouldSendAutoReply must now return false (suppressed!)
        assertFalse("shouldSendAutoReply must be false for dialed recipient to prevent bug", stateManager.shouldSendAutoReply(recipient))

        // Other contacts should not be affected
        val otherContact = "David Miller"
        assertFalse("Other contact must not be marked as recent outgoing call", stateManager.isRecentOutgoingCall(otherContact))
        assertTrue("Other contact should still receive auto reply if sent to them", stateManager.shouldSendAutoReply(otherContact))
    }

    @Test
    fun `outgoing call case-insensitive and whitespace-tolerant matching`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val contactName = "Mom"
        stateManager.recordOutgoingCall(contactName)

        assertTrue(stateManager.isRecentOutgoingCall("mom"))
        assertTrue(stateManager.isRecentOutgoingCall("  MOM  "))
        assertFalse(stateManager.shouldSendAutoReply("MOM"))
    }

    @Test
    fun `auto reply is active for call rejection and auto-reply message formatting works`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        // Ensure auto reply is enabled
        stateManager.setAutoReplyEnabled(true)
        assertTrue(stateManager.isAutoReplyEnabled())

        // Active profile message generation works for calls
        stateManager.setProfile(FocusProfile.GAMING)
        val gamingMsg = FocusStateManager.getAutoReplyMessage(context)
        assertTrue(gamingMsg.contains("Gaming"))

        stateManager.setProfile(FocusProfile.WORK)
        val workMsg = FocusStateManager.getAutoReplyMessage(context)
        assertTrue(workMsg.contains("Work"))

        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `weekly shield stats calculates 7 days and increments telemetry properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        val initialStats = stateManager.getWeeklyStats()
        assertEquals(7, initialStats.days.size)

        val todayInitialNotifs = initialStats.days.last().silencedNotifications
        val todayInitialCalls = initialStats.days.last().declinedCalls

        stateManager.incrementShieldedNotification()
        stateManager.incrementShieldedCall()

        val updatedStats = stateManager.getWeeklyStats()
        assertEquals(7, updatedStats.days.size)
        assertEquals(todayInitialNotifs + 1, updatedStats.days.last().silencedNotifications)
        assertEquals(todayInitialCalls + 1, updatedStats.days.last().declinedCalls)
        assertTrue(updatedStats.totalSilencedNotifications > 0)
        assertTrue(updatedStats.totalDeclinedCalls > 0)
    }

    @Test
    fun `gaming mode activation triggers non-interactive popup banner state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.OFF)
        stateManager.setProfile(FocusProfile.GAMING)

        val popup = com.example.ui.GamingModePopupManager.popupState.value
        assertTrue("Non-interactive Gaming Mode pop-up must be emitted on GAMING activation", popup != null)
        assertEquals("GAMING MODE ACTIVE", popup?.title)

        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `material 3 expressive shapes motion tokens and workspace tabs are configured`() {
        val expressiveShapes = com.example.ui.theme.ExpressiveShapes
        assertTrue("ExpressiveShapes must be defined", expressiveShapes.extraLarge != expressiveShapes.extraSmall)

        val bouncySpring = com.example.ui.theme.ExpressiveMotion.bouncySpatialSpring<Float>()
        assertTrue("Bouncy spring must have positive stiffness", bouncySpring.stiffness > 0f)

        val tabs = SentinalWorkspaceTab.entries
        assertEquals(4, tabs.size)
        assertEquals("nav_tab_shield", tabs[0].tag)
        assertEquals("nav_tab_analytics", tabs[1].tag)
        assertEquals("nav_tab_rules", tabs[2].tag)
        assertEquals("nav_tab_system", tabs[3].tag)
    }

    @Test
    fun `expressive haptics correlates with focus activation deactivation and notification dismissal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.OFF)

        // Activating Focus triggers FOCUS_ACTIVATED haptic waveform
        stateManager.setProfile(FocusProfile.DO_NOT_DISTURB)
        assertEquals(
            com.example.ui.theme.ExpressiveHaptics.HapticPattern.FOCUS_ACTIVATED,
            com.example.ui.theme.ExpressiveHaptics.lastPlayedPattern.value
        )

        // Silencing a notification triggers NOTIFICATION_DISMISSED haptic waveform
        stateManager.incrementShieldedNotification("Slack", "Silenced workspace ping")
        assertEquals(
            com.example.ui.theme.ExpressiveHaptics.HapticPattern.NOTIFICATION_DISMISSED,
            com.example.ui.theme.ExpressiveHaptics.lastPlayedPattern.value
        )

        // Turning off Focus triggers FOCUS_DEACTIVATED haptic waveform
        stateManager.setProfile(FocusProfile.OFF)
        assertEquals(
            com.example.ui.theme.ExpressiveHaptics.HapticPattern.FOCUS_DEACTIVATED,
            com.example.ui.theme.ExpressiveHaptics.lastPlayedPattern.value
        )
    }

    @Test
    fun `custom duration slider updates customDurationMinutes and live expiry timestamp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.WORK, DurationOption.FOR_ONE_HOUR)
        stateManager.setCustomDurationMinutes(95)

        assertEquals(95, stateManager.state.value.customDurationMinutes)
        val expiresAt = stateManager.state.value.expiresAtMillis
        assertTrue("Expiry must be set for custom duration", expiresAt != null)
        assertTrue(
            "Expiry should be roughly 95 minutes from now",
            expiresAt!! > System.currentTimeMillis() + (90 * 60_000L)
        )

        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `live intercepts feed records events and supports single dismiss and clear`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.clearInterceptHistory()
        assertTrue(stateManager.state.value.recentIntercepts.isEmpty())

        stateManager.incrementShieldedCall("Test Caller", isVoip = false)
        stateManager.incrementShieldedNotification("Discord", "Silenced ping")

        val intercepts = stateManager.state.value.recentIntercepts
        assertEquals(2, intercepts.size)
        assertEquals("Discord", intercepts[0].sourceLabel)
        assertEquals("Test Caller", intercepts[1].sourceLabel)

        // Dismiss first item
        val firstId = intercepts[0].id
        stateManager.dismissInterceptEvent(firstId)
        assertEquals(1, stateManager.state.value.recentIntercepts.size)

        // Clear all
        stateManager.clearInterceptHistory()
        assertTrue(stateManager.state.value.recentIntercepts.isEmpty())
    }

    @Test
    fun `auto reply toggle and 3 minute cooldown work across all surfaces`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.resetSessionCaches()
        stateManager.setAutoReplyEnabled(true)
        assertEquals(180_000L, FocusStateManager.AUTO_REPLY_COOLDOWN_MS)

        val caller = "CallerThreeMin"
        assertTrue("Auto-reply should be allowed initially when enabled", stateManager.shouldSendAutoReply(caller))

        // Toggling auto-reply OFF suppresses auto-reply completely
        val toggledOff = stateManager.toggleAutoReply()
        assertFalse(toggledOff)
        assertFalse("Auto-reply must be suppressed when toggled OFF", stateManager.shouldSendAutoReply(caller))

        // Toggling auto-reply back ON restores auto-reply eligibility
        val toggledOn = stateManager.toggleAutoReply()
        assertTrue(toggledOn)
        assertTrue(stateManager.shouldSendAutoReply(caller))

        // Record auto-reply 2 minutes ago (120,000 ms < 180,000 ms): still within 3-minute cooldown
        val now = System.currentTimeMillis()
        stateManager.recordAutoReplySent(caller, now - 120_000L)
        assertFalse("Should still be in 3-minute cooldown at 2 minutes", stateManager.shouldSendAutoReply(caller))

        // Record auto-reply 3 minutes and 5 seconds ago (185,000 ms > 180,000 ms): cooldown expired
        stateManager.recordAutoReplySent(caller, now - 185_000L)
        assertTrue("Should allow auto-reply after 3-minute cooldown expires", stateManager.shouldSendAutoReply(caller))
    }

    @Test
    fun `timer reaching zero or subtracting to zero deactivates focus shield without negative count`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.DO_NOT_DISTURB)
        stateManager.adjustRemainingTime(context, 15)
        assertEquals(FocusProfile.DO_NOT_DISTURB, stateManager.getActiveProfile())

        // Subtracting 15 minutes brings timer to <= 00:00 and must immediately deactivate Focus Shield
        stateManager.adjustRemainingTime(context, -15)
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())
        assertEquals(null, stateManager.state.value.expiresAtMillis)
    }

    @Test
    fun `whatsapp incoming call with spannable title and callstyle decline intent is detected and hung up`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = org.robolectric.Robolectric.buildService(com.example.services.NotificationGuardService::class.java).create().get()

        var declineBroadcastReceived = false
        val declineAction = "com.example.TEST_WHATSAPP_DECLINE"
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                if (intent?.action == declineAction) {
                    declineBroadcastReceived = true
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter(declineAction),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val declinePendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            101,
            android.content.Intent(declineAction).setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val answerPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            102,
            android.content.Intent("com.example.TEST_WHATSAPP_ANSWER").setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Simulate Android 12+ CallStyle notification from com.whatsapp with SpannableString & Bidi markers
        val spannableCaller = android.text.SpannableString("\u202A+1 (555) 019-2834\u202C")
        val notification = android.app.Notification.Builder(context, "whatsapp_calls")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setCategory(android.app.Notification.CATEGORY_CALL)
            .build()
        notification.extras.putCharSequence(android.app.Notification.EXTRA_TITLE, spannableCaller)
        notification.extras.putCharSequence(android.app.Notification.EXTRA_TEXT, "Silenced by Do Not Disturb")
        notification.extras.putInt("android.callType", 1) // CALL_TYPE_INCOMING
        notification.extras.putParcelable("android.declineIntent", declinePendingIntent)
        notification.extras.putParcelable("android.answerIntent", answerPendingIntent)

        val sbn = android.service.notification.StatusBarNotification(
            "com.whatsapp",
            "com.whatsapp",
            23,
            "whatsapp_call",
            1000,
            1000,
            0,
            notification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        assertFalse("Incoming WhatsApp call must not be classified as outgoing", service.isOutgoingCall(sbn))
        assertTrue("Incoming WhatsApp CallStyle call must be detected", service.isIncomingVoipCall(sbn))
        assertEquals("+1 (555) 019-2834", service.extractCallerTitle(sbn))

        val hungUp = service.attemptProgrammaticHangUp(sbn)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("attemptProgrammaticHangUp must succeed for WhatsApp CallStyle notification", hungUp)
        assertTrue("Decline PendingIntent broadcast must be fired", declineBroadcastReceived)

        context.unregisterReceiver(receiver)
    }

    @Test
    fun `whatsapp rapid notification updates during single call do not trigger false repeat breakthrough`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)
        stateManager.resetSessionCaches()
        stateManager.setProfile(FocusProfile.DO_NOT_DISTURB)
        stateManager.setAllowRepeatCallersEnabled(true)
        stateManager.setDeclineAllCallsRegardless(false)

        val service = org.robolectric.Robolectric.buildService(com.example.services.NotificationGuardService::class.java).create().get()

        var declineCount = 0
        val declineAction = "com.example.TEST_WA_BURST_DECLINE"
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                if (intent?.action == declineAction) {
                    declineCount++
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter(declineAction),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val declinePendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            201,
            android.content.Intent(declineAction).setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val answerPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            202,
            android.content.Intent("com.example.TEST_WA_BURST_ANSWER").setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val buildWaSbn = { textLabel: String ->
            val notif = android.app.Notification.Builder(context, "wa_voip")
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setCategory(android.app.Notification.CATEGORY_CALL)
                .addAction(android.app.Notification.Action.Builder(null, "Decline", declinePendingIntent).build())
                .addAction(android.app.Notification.Action.Builder(null, "Answer", answerPendingIntent).build())
                .build()
            notif.extras.putCharSequence(android.app.Notification.EXTRA_TITLE, android.text.SpannableString("Sarah Connor"))
            notif.extras.putCharSequence(android.app.Notification.EXTRA_TEXT, textLabel)

            android.service.notification.StatusBarNotification(
                "com.whatsapp",
                "com.whatsapp",
                42,
                "wa_call_42",
                1000,
                1000,
                0,
                notif,
                android.os.Process.myUserHandle(),
                System.currentTimeMillis()
            )
        }

        val initialCalls = stateManager.state.value.totalShieldedCalls

        // 1st notification posted at t=0ms ("Calling...")
        service.handleNotificationPostedInternal(buildWaSbn("WhatsApp voice call"))
        // 2nd notification update posted 150ms later for the SAME call (avatar loaded / "Incoming voice call")
        service.handleNotificationPostedInternal(buildWaSbn("Incoming voice call"))

        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // Both notifications must trigger decline (not allowed through as repeat caller!), and telemetry increments once
        assertEquals("Both notification updates must fire the decline intent", 2, declineCount)
        assertEquals("Shielded call count must increment once for the same call burst", initialCalls + 1, stateManager.state.value.totalShieldedCalls)

        context.unregisterReceiver(receiver)
        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `custom rule toggles are preserved across all profile switches`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        // Set custom rule toggles
        stateManager.updateProfileSettings(
            allowStarredOnly = false,
            blockRepeatedCalls = true,
            silenceNotifications = false
        )
        assertFalse(stateManager.state.value.allowStarredContactsOnly)
        assertTrue(stateManager.state.value.blockRepeatedCallers)
        assertFalse(stateManager.state.value.silenceNotifications)

        // Switch across all profiles and verify custom toggles remain untouched
        for (profile in listOf(
            FocusProfile.DO_NOT_DISTURB,
            FocusProfile.GAMING,
            FocusProfile.WORK,
            FocusProfile.SLEEP,
            FocusProfile.OFF
        )) {
            stateManager.setProfile(profile)
            assertFalse("allowStarredContactsOnly must be preserved in $profile", stateManager.state.value.allowStarredContactsOnly)
            assertTrue("blockRepeatedCallers must be preserved in $profile", stateManager.state.value.blockRepeatedCallers)
            assertFalse("silenceNotifications must be preserved in $profile", stateManager.state.value.silenceNotifications)
        }

        // Restore defaults
        stateManager.updateProfileSettings(
            allowStarredOnly = true,
            blockRepeatedCalls = false,
            silenceNotifications = true
        )
    }

    @Test
    fun `game exit grace period is 15 seconds and manual gaming mode auto exits on handleGameExited`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        assertEquals(15_000L, FocusStateManager.GAME_EXIT_DEACTIVATION_DELAY_MS)

        // Manually turn on Gaming Mode
        stateManager.setProfile(FocusProfile.GAMING)
        assertEquals(FocusProfile.GAMING, stateManager.getActiveProfile())
        assertFalse(stateManager.state.value.isAutoTriggeredByGame)

        // Exiting game after 15s grace period must auto-exit Gaming Mode even when turned on manually
        stateManager.handleGameExited()
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())
    }

    @Test
    fun `whatsapp 0ms generic placeholder does not lock out 100ms real caller update`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)
        stateManager.resetSessionCaches()
        stateManager.setProfile(FocusProfile.DO_NOT_DISTURB)
        stateManager.setAllowRepeatCallersEnabled(true)
        stateManager.setDeclineAllCallsRegardless(false)
        stateManager.setAutoReplyEnabled(true)

        val service = org.robolectric.Robolectric.buildService(com.example.services.NotificationGuardService::class.java).create().get()

        var declineCount = 0
        val declineAction = "com.example.TEST_WA_PLACEHOLDER_DECLINE"
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                if (intent?.action == declineAction) {
                    declineCount++
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter(declineAction),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 1. T=0ms: WhatsApp posts foreground service connection placeholder ("WhatsApp" / "Incoming voice call") with 0 actions
        val placeholderNotif = android.app.Notification.Builder(context, "wa_voip")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setCategory(android.app.Notification.CATEGORY_CALL)
            .build()
        placeholderNotif.extras.putCharSequence(android.app.Notification.EXTRA_TITLE, "WhatsApp")
        placeholderNotif.extras.putCharSequence(android.app.Notification.EXTRA_TEXT, "Incoming voice call")

        val placeholderSbn = android.service.notification.StatusBarNotification(
            "com.whatsapp",
            "com.whatsapp",
            77,
            "wa_call_77",
            1000,
            1000,
            0,
            placeholderNotif,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )
        service.handleNotificationPostedInternal(placeholderSbn)

        // 2. T=100ms: WhatsApp updates same notification key with real caller name ("Emma Watson") and Decline action
        val declinePendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            301,
            android.content.Intent(declineAction).setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val answerPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            302,
            android.content.Intent("com.example.TEST_WA_PLACEHOLDER_ANSWER").setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val realNotif = android.app.Notification.Builder(context, "wa_voip")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setCategory(android.app.Notification.CATEGORY_CALL)
            .addAction(android.app.Notification.Action.Builder(null, "Decline", declinePendingIntent).build())
            .addAction(android.app.Notification.Action.Builder(null, "Answer", answerPendingIntent).build())
            .build()
        realNotif.extras.putCharSequence(android.app.Notification.EXTRA_TITLE, "Emma Watson")
        realNotif.extras.putCharSequence(android.app.Notification.EXTRA_TEXT, "Incoming voice call")

        val realSbn = android.service.notification.StatusBarNotification(
            "com.whatsapp",
            "com.whatsapp",
            77,
            "wa_call_77",
            1000,
            1000,
            0,
            realNotif,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )
        service.handleNotificationPostedInternal(realSbn)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Real caller update must fire the decline intent", 1, declineCount)
        assertTrue(
            "Latest intercept must record real caller Emma Watson",
            stateManager.state.value.recentIntercepts.any { it.sourceLabel.contains("Emma Watson") }
        )

        context.unregisterReceiver(receiver)
        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `work focus schedule activates during configured window and deactivates outside window`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.OFF)
        stateManager.setWorkScheduleTime(9, 0, 17, 0)
        stateManager.setWorkScheduleDays(setOf(java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY))
        stateManager.setWorkScheduleEnabled(true)

        // Upcoming Monday at 10:30 AM -> Inside window
        val mondayMorning = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 7)
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 10)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        stateManager.evaluateWorkSchedule(mondayMorning.timeInMillis)
        assertEquals(FocusProfile.WORK, stateManager.getActiveProfile())
        assertTrue(stateManager.state.value.isAutoTriggeredByWorkSchedule)
        assertTrue(stateManager.state.value.expiresAtMillis != null)

        // Monday at 18:00 (6:00 PM) -> Outside window -> Auto-reverts to OFF
        val mondayEvening = (mondayMorning.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 18)
            set(java.util.Calendar.MINUTE, 0)
        }
        stateManager.evaluateWorkSchedule(mondayEvening.timeInMillis)
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())
        assertFalse(stateManager.state.value.isAutoTriggeredByWorkSchedule)

        stateManager.setWorkScheduleEnabled(false)
        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `cinema mode supports 2h and 2_5h duration presets and theater notification blocking toggle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.OFF)

        // Verify 2h (120 min) preset activation
        stateManager.activateCinemaMode(120)
        assertEquals(FocusProfile.CINEMA, stateManager.getActiveProfile())
        assertEquals(120, stateManager.state.value.cinemaPresetMinutes)
        assertEquals(DurationOption.FOR_TWO_HOURS, stateManager.state.value.durationOption)

        // Verify 2.5h (150 min) preset activation
        stateManager.activateCinemaMode(150)
        assertEquals(FocusProfile.CINEMA, stateManager.getActiveProfile())
        assertEquals(150, stateManager.state.value.cinemaPresetMinutes)
        assertEquals(DurationOption.FOR_TWO_AND_HALF_HOURS, stateManager.state.value.durationOption)

        // Verify theater notification blocking toggle and zero-vibration posture
        stateManager.setCinemaNotificationBlockingEnabled(false)
        assertFalse(stateManager.state.value.cinemaNotificationBlockingEnabled)
        stateManager.setCinemaNotificationBlockingEnabled(true)
        assertTrue(stateManager.state.value.cinemaNotificationBlockingEnabled)

        assertTrue(FocusStateManager.isCinemaZeroVibrationActive(context))
        stateManager.setCinemaZeroVibrationEnabled(false)
        assertFalse(FocusStateManager.isCinemaZeroVibrationActive(context))
        stateManager.setCinemaZeroVibrationEnabled(true)

        // Verify theater auto-reply message
        val cinemaReply = stateManager.getAutoReplyMessage(FocusProfile.CINEMA)
        assertTrue(cinemaReply.contains("movie theater", ignoreCase = true))

        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `gaming mode confirmation prompt supports activate, not now session suppression, and reset on game exit`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.OFF)
        stateManager.setAutoGameDetectionEnabled(true)
        stateManager.setAskBeforeGameActivationEnabled(true)
        stateManager.clearDeclinedGameSessionIfExited(null)

        val gamePkg = "com.activision.callofduty.shooter"

        // 1. Trigger confirmation prompt on game launch
        stateManager.requestGameLaunchConfirmation(gamePkg, "Call of Duty Mobile", countdownSeconds = 5)
        assertEquals(gamePkg, stateManager.state.value.pendingGameActivationPackage)
        assertEquals(5, stateManager.state.value.pendingGameCountdownSeconds)
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())

        // 2. Tap 'Not Now' -> keeps Gaming Mode off and suppresses re-prompting for this game session
        stateManager.declinePendingGameActivation()
        assertEquals(null, stateManager.state.value.pendingGameActivationPackage)
        assertEquals(gamePkg, stateManager.state.value.declinedGamePackageForSession)
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())

        // 3. Re-detecting same game while still in session must NOT re-open prompt
        stateManager.requestGameLaunchConfirmation(gamePkg, "Call of Duty Mobile", countdownSeconds = 5)
        assertEquals(null, stateManager.state.value.pendingGameActivationPackage)

        // 4. Closing/leaving the game clears the session suppression
        stateManager.clearDeclinedGameSessionIfExited("com.android.launcher3")
        assertEquals(null, stateManager.state.value.declinedGamePackageForSession)

        // 5. Relaunching the game now shows the prompt again, and tapping 'Activate' engages Gaming Mode
        stateManager.requestGameLaunchConfirmation(gamePkg, "Call of Duty Mobile", countdownSeconds = 5)
        assertEquals(gamePkg, stateManager.state.value.pendingGameActivationPackage)
        stateManager.confirmPendingGameActivation()
        assertEquals(FocusProfile.GAMING, stateManager.getActiveProfile())
        assertTrue(stateManager.state.value.isAutoTriggeredByGame)

        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `sleep focus overnight schedule activates at bedtime and deactivates at wake up`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.setProfile(FocusProfile.OFF)
        stateManager.setSleepScheduleTime(22, 30, 7, 0)
        stateManager.setSleepScheduleDays(com.example.core.FocusSessionState.DEFAULT_SLEEP_DAYS)
        stateManager.setSleepScheduleEnabled(true)

        // Upcoming Monday night at 23:15 (11:15 PM) -> Inside overnight sleep window
        val mondayNight = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 7)
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 15)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        stateManager.evaluateSleepSchedule(mondayNight.timeInMillis)
        assertEquals(FocusProfile.SLEEP, stateManager.getActiveProfile())
        assertTrue(stateManager.state.value.isAutoTriggeredBySleepSchedule)
        assertTrue(stateManager.state.value.expiresAtMillis != null)

        // Next morning (Tuesday at 07:30 AM) -> Outside overnight sleep window -> Auto-reverts to OFF
        val tuesdayMorning = (mondayNight.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 7)
            set(java.util.Calendar.MINUTE, 30)
        }
        stateManager.evaluateSleepSchedule(tuesdayMorning.timeInMillis)
        assertEquals(FocusProfile.OFF, stateManager.getActiveProfile())
        assertFalse(stateManager.state.value.isAutoTriggeredBySleepSchedule)

        stateManager.setSleepScheduleEnabled(false)
        stateManager.setProfile(FocusProfile.OFF)
    }

    @Test
    fun `notification categorizer classifies incoming alerts and applies user priority focus rules`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateManager = FocusStateManager.getInstance(context)

        stateManager.applyGlobalFocusRulePreset("BALANCED")

        // 1. Classify Security/OTP as SECURITY_AND_URGENT -> CRITICAL -> ALLOW_ALL
        val otpCat = stateManager.classifyIncomingNotification(
            packageName = "com.google.android.apps.authenticator2",
            androidCategory = null,
            title = "Verification Code",
            text = "Your OTP code is 992-841"
        )
        assertEquals(com.example.core.NotificationCategory.SECURITY_AND_URGENT, otpCat)
        assertEquals(
            com.example.core.GlobalFocusRuleAction.ALLOW_ALL,
            stateManager.getEffectiveRuleForCategory(otpCat)
        )

        // 2. Classify Direct Message as DIRECT_MESSAGES -> HIGH -> MUTE_SILENT
        val dmCat = stateManager.classifyIncomingNotification(
            packageName = "com.whatsapp",
            androidCategory = android.app.Notification.CATEGORY_MESSAGE,
            title = "Maya Lin",
            text = "Hey, are you free?"
        )
        assertEquals(com.example.core.NotificationCategory.DIRECT_MESSAGES, dmCat)
        assertEquals(
            com.example.core.GlobalFocusRuleAction.MUTE_SILENT,
            stateManager.getEffectiveRuleForCategory(dmCat)
        )

        // 3. Classify Promotion as PROMOTIONS_AND_DEALS -> LOW -> HIDE_COMPLETELY
        val promoCat = stateManager.classifyIncomingNotification(
            packageName = "com.amazon.mShop.android.shopping",
            androidCategory = android.app.Notification.CATEGORY_PROMO,
            title = "Flash Sale 50% OFF",
            text = "Limited time discount coupon"
        )
        assertEquals(com.example.core.NotificationCategory.PROMOTIONS_AND_DEALS, promoCat)
        assertEquals(
            com.example.core.GlobalFocusRuleAction.HIDE_COMPLETELY,
            stateManager.getEffectiveRuleForCategory(promoCat)
        )

        // 4. User elevates DIRECT_MESSAGES to CRITICAL -> effective action updates to ALLOW_ALL
        stateManager.setCategoryPriorityLevel(
            com.example.core.NotificationCategory.DIRECT_MESSAGES,
            com.example.core.NotificationPriorityLevel.CRITICAL
        )
        assertEquals(
            com.example.core.GlobalFocusRuleAction.ALLOW_ALL,
            stateManager.getEffectiveRuleForCategory(com.example.core.NotificationCategory.DIRECT_MESSAGES)
        )

        // Restore default priority
        stateManager.setCategoryPriorityLevel(
            com.example.core.NotificationCategory.DIRECT_MESSAGES,
            com.example.core.NotificationPriorityLevel.HIGH
        )
    }
}


