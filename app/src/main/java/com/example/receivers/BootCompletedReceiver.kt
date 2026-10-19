package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import com.example.services.ZenShieldWatchdogService

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "BootCompletedReceiver triggered with action: $action")

        val stateManager = FocusStateManager.getInstance(context)
        val currentState = stateManager.state.value

        // Check if session has expired or is still active
        val isStillActive = currentState.activeProfile != FocusProfile.OFF &&
                (currentState.expiresAtMillis == null || currentState.expiresAtMillis > System.currentTimeMillis())

        if (isStillActive) {
            Log.d(TAG, "Restoring active profile '${currentState.activeProfile.displayName}' after reboot")

            // Re-apply interruption filter & dim lock screen
            stateManager.setProfile(currentState.activeProfile, currentState.durationOption)

            // Start bulletproof watchdog foreground service
            ZenShieldWatchdogService.start(context)
        } else {
            Log.d(TAG, "Focus profile is OFF or expired. No restoration required.")
            stateManager.setProfile(FocusProfile.OFF)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
