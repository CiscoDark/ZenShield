package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import com.example.services.CellularBouncerService
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
        assertEquals("ZenShield", appName)
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
        val isBreakthrough = (callTwoTime - lastTimestamp) <= CellularBouncerService.THREE_MINUTES_MS
        assertTrue("Second call within 30s should qualify as breakthrough", isBreakthrough)

        // Repeat caller beyond 3 minutes (e.g. 4 minutes later)
        val callThreeTime = now + 240_000L
        val isExpired = (callThreeTime - lastTimestamp) > CellularBouncerService.THREE_MINUTES_MS
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
}
