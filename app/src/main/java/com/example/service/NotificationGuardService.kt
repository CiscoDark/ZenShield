package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.core.FocusProfile
import com.example.core.FocusStateManager

class NotificationGuardService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val stateManager = FocusStateManager.getInstance(applicationContext)
        val focusState = stateManager.state.value

        // If focus mode is OFF, do not intervene
        if (focusState.activeProfile == FocusProfile.OFF) {
            return
        }

        // Do not suppress ongoing foreground services (like active timers, navigation, music playback)
        if (sbn.isOngoing) {
            return
        }

        // Suppress our own notifications from counting against user statistics
        if (sbn.packageName == packageName) {
            return
        }

        if (focusState.silenceNotifications) {
            Log.d(TAG, "Notification from ${sbn.packageName} suppressed during ${focusState.activeProfile.displayName}")
            stateManager.incrementShieldedNotification()

            // When in strict SLEEP or GAMING mode, active cancel can be executed if supported
            if (focusState.activeProfile == FocusProfile.SLEEP || focusState.activeProfile == FocusProfile.GAMING) {
                try {
                    cancelNotification(sbn.key)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Missing permission to cancel notification: ${e.message}")
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationGuardService connected and ready")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "NotificationGuardService disconnected")
    }

    companion object {
        private const val TAG = "NotificationGuard"
    }
}
