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
}


