package com.example.services

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import java.util.Locale

class NotificationGuardService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val stateManager = FocusStateManager.getInstance(applicationContext)
        val activeProfile = stateManager.getActiveProfile()

        // If Focus Shield is OFF, do not intervene
        if (activeProfile == FocusProfile.OFF) {
            return
        }

        // Do not suppress ongoing foreground services that are NOT incoming calls
        val isVoipCall = isIncomingVoipCall(sbn)
        if (sbn.isOngoing && !isVoipCall) {
            return
        }

        // Suppress our own notifications from triggering shield loops
        if (sbn.packageName == packageName) {
            return
        }

        val isGamingLockdown = (activeProfile == FocusProfile.GAMING && stateManager.isStrictGamingLockdown())

        // =========================================================================
        // 1. VoIP & Third-Party App Call Rejection (WhatsApp, Telegram, Discord, etc.)
        // =========================================================================
        if (isVoipCall) {
            val callerName = extractCallerTitle(sbn)
            Log.d(TAG, "Incoming VoIP call detected from '$callerName' (${sbn.packageName})")

            // 2. 3-Minute Repeat Caller Logic for VoIP Calls
            val isRepeatBreakthrough = stateManager.isAllowRepeatCallers() &&
                    !isGamingLockdown &&
                    stateManager.checkVoipRepeatBreakthrough(callerName)

            if (isRepeatBreakthrough) {
                Log.d(TAG, "VoIP 3-minute repeat breakthrough confirmed for '$callerName'! Allowing call to ring.")
                triggerBreakthroughHaptic()
                return // Allow call to ring through
            }

            // 3. Programmatically Hang Up VoIP Call on 1st call or Gaming Lockdown
            Log.d(TAG, "Rejecting VoIP call from '$callerName' under profile ${activeProfile.displayName}")
            stateManager.incrementShieldedCall()

            val hangUpSuccessful = attemptProgrammaticHangUp(sbn)
            if (!hangUpSuccessful) {
                // Fallback: Snooze notification immediately to suppress popup over screen
                try {
                    snoozeNotification(sbn.key, 60_000L)
                    Log.d(TAG, "VoIP call fallback snooze applied for ${sbn.key}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to snooze VoIP call notification", e)
                    try {
                        cancelNotification(sbn.key)
                    } catch (_: Exception) {}
                }
            }
            return
        }

        // =========================================================================
        // 2. Regular Notifications: Whitelist, Smart Scanner, Auto-Reply, and Snooze
        // =========================================================================

        // A. Whitelisted Package Check (Per-Profile Whitelist)
        if (stateManager.isPackageWhitelisted(activeProfile, sbn.packageName)) {
            Log.d(TAG, "Allowing notification from whitelisted package: ${sbn.packageName} for ${activeProfile.displayName}")
            return
        }

        // B. Smart Breakthrough Scanner (OTP, 2FA, Logistics, Urgent Keywords)
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = sbn.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val combinedContent = "$title $text $bigText".lowercase(Locale.ROOT)

        val isUrgentBreakthrough = matchesUrgentKeywords(combinedContent)
        if (isUrgentBreakthrough && !isGamingLockdown) {
            Log.d(TAG, "Smart Urgent Breakthrough triggered for notification from ${sbn.packageName} ('$title')")
            triggerBreakthroughHaptic()
            return // Allow visible notification
        }

        // C. Focus Status Auto-Reply via RemoteInput
        if (stateManager.isAutoReplyEnabled() && isMessagingApp(sbn.packageName)) {
            val senderName = if (title.isNotBlank()) title else sbn.packageName
            if (stateManager.shouldSendAutoReply(senderName)) {
                val replied = attemptRemoteInputAutoReply(sbn, activeProfile, stateManager)
                if (replied) {
                    stateManager.recordAutoReplySent(senderName)
                    Log.d(TAG, "Sent automatic focus status reply to '$senderName' via RemoteInput")
                }
            }
        }

        // D. Silent Hold / Snooze for 1 Hour (3,600,000 ms)
        stateManager.incrementShieldedNotification()
        try {
            snoozeNotification(sbn.key, 3_600_000L)
            Log.d(TAG, "Snoozed notification from ${sbn.packageName} for 1 hour")
        } catch (e: Exception) {
            Log.w(TAG, "Could not snooze notification, attempting cancelNotification: ${e.message}")
            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {}
        }
    }

    private fun isIncomingVoipCall(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false

        // Category check
        if (notification.category == Notification.CATEGORY_CALL) {
            return true
        }

        // Full screen intent check (standard pattern for incoming VoIP calls)
        if (notification.fullScreenIntent != null) {
            return true
        }

        // Known VoIP packages inspection
        val pkg = sbn.packageName.lowercase(Locale.ROOT)
        val isKnownVoipApp = KNOWN_VOIP_PACKAGES.any { pkg.contains(it) }
        if (isKnownVoipApp) {
            val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val content = "$title $text".lowercase(Locale.ROOT)

            val hasCallText = CALL_KEYWORDS.any { content.contains(it) }
            val hasCallActions = notification.actions?.any { action ->
                val actionTitle = action.title?.toString()?.lowercase(Locale.ROOT) ?: ""
                actionTitle.contains("decline") || actionTitle.contains("reject") || actionTitle.contains("answer")
            } == true

            if (hasCallText || hasCallActions) {
                return true
            }
        }

        return false
    }

    private fun extractCallerTitle(sbn: StatusBarNotification): String {
        val extras = sbn.notification?.extras ?: return "Unknown Caller"
        val title = extras.getString(Notification.EXTRA_TITLE)
        if (!title.isNullOrBlank()) return title

        val bigTitle = extras.getString(Notification.EXTRA_TITLE_BIG)
        if (!bigTitle.isNullOrBlank()) return bigTitle

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) return text

        return sbn.packageName
    }

    private fun attemptProgrammaticHangUp(sbn: StatusBarNotification): Boolean {
        val actions = sbn.notification?.actions ?: return false

        for (action in actions) {
            val title = action.title?.toString()?.lowercase(Locale.ROOT) ?: ""
            val isDeclineTitle = DECLINE_KEYWORDS.any { title.contains(it) }

            val isDeclineSemantic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                action.semanticAction == Notification.Action.SEMANTIC_ACTION_THUMBS_DOWN ||
                        action.semanticAction == Notification.Action.SEMANTIC_ACTION_DELETE
            } else {
                false
            }

            if (isDeclineTitle || isDeclineSemantic) {
                try {
                    action.actionIntent?.send()
                    Log.d(TAG, "Successfully fired VoIP hangup actionIntent: '$title'")
                    return true
                } catch (e: PendingIntent.CanceledException) {
                    Log.w(TAG, "Action intent canceled while hanging up VoIP call", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Error executing VoIP hang up action", e)
                }
            }
        }
        return false
    }

    private fun matchesUrgentKeywords(content: String): Boolean {
        // 1. OTPs and 2FA
        val isOtp = OTP_KEYWORDS.any { content.contains(it) }
        // 2. Logistics & Deliveries
        val isLogistics = LOGISTICS_KEYWORDS.any { content.contains(it) }
        // 3. Urgent / Emergency Overrides
        val isUrgent = URGENT_KEYWORDS.any { content.contains(it) }

        return isOtp || isLogistics || isUrgent
    }

    private fun isMessagingApp(packageName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        return KNOWN_MESSAGING_PACKAGES.any { pkg.contains(it) }
    }

    private fun attemptRemoteInputAutoReply(
        sbn: StatusBarNotification,
        activeProfile: FocusProfile,
        stateManager: FocusStateManager
    ): Boolean {
        val actions = sbn.notification?.actions ?: return false

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.allowFreeFormInput || remoteInput.resultKey != null) {
                    val message = stateManager.getAutoReplyMessage(activeProfile)

                    val replyBundle = Bundle().apply {
                        putCharSequence(remoteInput.resultKey, message)
                    }

                    val replyIntent = Intent()
                    RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, replyBundle)

                    try {
                        action.actionIntent.send(applicationContext, 0, replyIntent)
                        return true
                    } catch (e: PendingIntent.CanceledException) {
                        Log.w(TAG, "RemoteInput PendingIntent was canceled", e)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error firing RemoteInput auto reply", e)
                    }
                }
            }
        }
        return false
    }

    private fun triggerBreakthroughHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 140), intArrayOf(0, 180, 0, 255), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 140), intArrayOf(0, 180, 0, 255), -1)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic feedback unavailable: ${e.message}")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationGuardService active and listening")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "NotificationGuardService disconnected")
    }

    companion object {
        private const val TAG = "NotificationGuard"

        private val KNOWN_VOIP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.facebook.orca",
            "com.instagram.android",
            "com.discord",
            "com.skype.raider",
            "org.thoughtcrime.securesms",
            "com.slack",
            "com.microsoft.teams"
        )

        private val KNOWN_MESSAGING_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.facebook.orca",
            "com.google.android.apps.messaging",
            "com.instagram.android",
            "org.thoughtcrime.securesms",
            "com.discord"
        )

        private val CALL_KEYWORDS = setOf(
            "incoming voice call",
            "incoming video call",
            "incoming call",
            "is calling",
            "call in progress"
        )

        private val DECLINE_KEYWORDS = setOf(
            "decline",
            "reject",
            "hang up",
            "end",
            "dismiss",
            "ignore"
        )

        private val OTP_KEYWORDS = setOf(
            "otp",
            "verification code",
            "security code",
            "one-time",
            "passcode"
        )

        private val LOGISTICS_KEYWORDS = setOf(
            "driver is arriving",
            "your ride",
            "out for delivery",
            "order arriving",
            "approaching your location"
        )

        private val URGENT_KEYWORDS = setOf(
            "urgent",
            "emergency",
            "breakthrough"
        )
    }
}
