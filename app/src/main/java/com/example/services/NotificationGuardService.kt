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
import java.util.concurrent.ConcurrentHashMap

/**
 * Cached session for in-app RemoteInput replies.
 */
data class RemoteInputSession(
    val packageName: String,
    val senderTitle: String,
    val actionIntent: PendingIntent,
    val remoteInput: RemoteInput,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tracks VoIP calls that have just been declined and are awaiting a follow-up
 * missed-call notification to execute an in-app RemoteInput auto-reply.
 */
data class PendingVoipReply(
    val packageName: String,
    val callerTitle: String,
    val activeProfile: FocusProfile,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * NotificationGuardService
 *
 * Provides:
 * 1. VoIP call rejection (WhatsApp, Telegram, Messenger, Instagram, Discord).
 * 2. 3-Minute Repeat Caller Breakthrough for VoIP calls.
 * 3. Two-Stage In-App VoIP Auto-Reply Engine with Zero-Airtime Guardrail (no cellular SMS,
 *    strictly native RemoteInput messaging).
 * 4. Per-profile whitelisting, Smart Urgent Scanner (OTPs, 2FA, logistics), and notification suppression.
 */
class NotificationGuardService : NotificationListenerService() {

    // Cache of active RemoteInput sessions by "$packageName|$normalizedTitle"
    private val remoteInputSessions = ConcurrentHashMap<String, RemoteInputSession>()

    // Trackers for VoIP calls declined within the last 30 seconds awaiting post-decline missed-call notification
    private val pendingVoipReplies = ConcurrentHashMap<String, PendingVoipReply>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val stateManager = FocusStateManager.getInstance(applicationContext)
        val activeProfile = stateManager.getActiveProfile()

        // Cache any valid RemoteInput from messaging or VoIP notifications for Stage 1 pre-warming
        cacheRemoteInputFromNotification(sbn)

        // Evict expired pending replies (> 30 seconds old)
        evictExpiredPendingReplies()

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
        // STAGE 2: Post-Decline Missed-Call Catch & Auto-Reply Execution
        // =========================================================================
        val handledPostDecline = handlePostDeclineVoipCatch(sbn, activeProfile, stateManager)
        if (handledPostDecline) {
            return
        }

        // =========================================================================
        // STAGE 1: VoIP & Third-Party App Call Rejection (WhatsApp, Telegram, Discord)
        // =========================================================================
        if (isVoipCall) {
            val callerName = extractCallerTitle(sbn)
            val sessionKey = buildSessionKey(sbn.packageName, callerName)
            Log.d(TAG, "Incoming VoIP call detected from '$callerName' (${sbn.packageName})")

            // 1. 3-Minute Repeat Caller Logic for VoIP Calls
            val isRepeatBreakthrough = stateManager.isAllowRepeatCallers() &&
                    !isGamingLockdown &&
                    stateManager.checkVoipRepeatBreakthrough(callerName)

            if (isRepeatBreakthrough) {
                Log.d(TAG, "VoIP 3-minute repeat breakthrough confirmed for '$callerName'! Allowing call to ring.")
                triggerBreakthroughHaptic()
                return // Allow call to ring through
            }

            // 2. Programmatically Hang Up VoIP Call
            Log.d(TAG, "Rejecting VoIP call from '$callerName' under profile ${activeProfile.displayName}")
            stateManager.incrementShieldedCall()

            val hangUpSuccessful = attemptProgrammaticHangUp(sbn)

            // 3. Stage 1 Auto-Reply Attempt (Immediate via pre-cached RemoteInputSession)
            if (stateManager.isAutoReplyEnabled() && stateManager.shouldSendAutoReply(callerName)) {
                val cachedSession = remoteInputSessions[sessionKey]
                if (cachedSession != null && (System.currentTimeMillis() - cachedSession.timestamp) < CACHE_MAX_AGE_MS) {
                    val message = stateManager.getAutoReplyMessage(activeProfile)
                    val replied = sendCachedRemoteInputReply(cachedSession, message)
                    if (replied) {
                        stateManager.recordAutoReplySent(callerName)
                        Log.i(TAG, "Stage 1: Dispatched immediate VoIP chat auto-reply to '$callerName' via cached RemoteInput")
                    }
                } else {
                    // Mark as pending for Stage 2 (post-decline missed-call catch)
                    pendingVoipReplies[sessionKey] = PendingVoipReply(
                        packageName = sbn.packageName,
                        callerTitle = callerName,
                        activeProfile = activeProfile,
                        timestamp = System.currentTimeMillis()
                    )
                    Log.d(TAG, "Stage 1: Registered pending auto-reply for '$sessionKey' awaiting Stage 2 missed-call notification")
                }
            }

            if (!hangUpSuccessful) {
                // Fallback: Snooze or cancel notification immediately to suppress popup over screen
                try {
                    snoozeNotification(sbn.key, 60_000L)
                    Log.d(TAG, "VoIP call fallback snooze applied for ${sbn.key}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to snooze VoIP call notification: ${e.message}")
                    try {
                        cancelNotification(sbn.key)
                    } catch (_: Exception) {}
                }
            }
            return
        }

        // =========================================================================
        // Regular Notifications: Whitelist, Smart Scanner, Auto-Reply, and Snooze
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

        // C. Focus Status Auto-Reply via RemoteInput (Zero-Airtime Guardrail: strictly native RemoteInput)
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

    /**
     * STAGE 2: Post-Decline Missed-Call Catch
     * Intercepts missed call / follow-up chat notifications posted right after declining a VoIP call,
     * fires the automated in-app reply via RemoteInput, and suppresses the notification.
     */
    private fun handlePostDeclineVoipCatch(
        sbn: StatusBarNotification,
        activeProfile: FocusProfile,
        stateManager: FocusStateManager
    ): Boolean {
        if (pendingVoipReplies.isEmpty()) return false

        val pkg = sbn.packageName
        val title = extractCallerTitle(sbn)
        val normTitle = normalizeTitle(title)
        val sessionKey = buildSessionKey(pkg, title)

        // Find matching pending reply by session key or matching title in same package
        val matchedEntry = pendingVoipReplies.entries.firstOrNull { (key, pending) ->
            pending.packageName == pkg && (
                    key == sessionKey ||
                            normTitle.contains(normalizeTitle(pending.callerTitle)) ||
                            normalizeTitle(pending.callerTitle).contains(normTitle)
                    )
        } ?: return false

        val pending = matchedEntry.value
        Log.i(TAG, "Stage 2 Post-Decline catch matched for '${pending.callerTitle}' on ${sbn.packageName}")

        var replied = false
        val message = stateManager.getAutoReplyMessage(pending.activeProfile)

        // 1. Try RemoteInput on this newly posted notification first
        val actions = sbn.notification?.actions
        if (actions != null) {
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    if (remoteInput.allowFreeFormInput || remoteInput.resultKey != null) {
                        val replyBundle = Bundle().apply {
                            putCharSequence(remoteInput.resultKey, message)
                        }
                        val replyIntent = Intent()
                        RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, replyBundle)
                        try {
                            action.actionIntent.send(applicationContext, 0, replyIntent)
                            replied = true
                            Log.i(TAG, "Stage 2: Successfully sent in-app VoIP auto-reply via notification RemoteInput")
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "Error executing Stage 2 RemoteInput: ${e.message}")
                        }
                    }
                }
                if (replied) break
            }
        }

        // 2. If newly posted notification lacked RemoteInput, try cached session
        if (!replied) {
            val cachedSession = remoteInputSessions[matchedEntry.key]
            if (cachedSession != null) {
                replied = sendCachedRemoteInputReply(cachedSession, message)
                if (replied) {
                    Log.i(TAG, "Stage 2: Successfully sent in-app VoIP auto-reply via cached session")
                }
            }
        }

        if (replied) {
            stateManager.recordAutoReplySent(pending.callerTitle)
        }

        // Remove from pending list
        pendingVoipReplies.remove(matchedEntry.key)

        // Suppress the missed-call notification to protect user flow
        stateManager.incrementShieldedNotification()
        try {
            cancelNotification(sbn.key)
            Log.d(TAG, "Stage 2: Suppressed post-decline missed-call notification ${sbn.key}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel post-decline notification: ${e.message}")
        }

        return true
    }

    private fun cacheRemoteInputFromNotification(sbn: StatusBarNotification) {
        val actions = sbn.notification?.actions ?: return
        val title = sbn.notification?.extras?.getString(Notification.EXTRA_TITLE)
            ?: sbn.notification?.extras?.getString(Notification.EXTRA_TITLE_BIG)
            ?: return

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.allowFreeFormInput || remoteInput.resultKey != null) {
                    val key = buildSessionKey(sbn.packageName, title)
                    remoteInputSessions[key] = RemoteInputSession(
                        packageName = sbn.packageName,
                        senderTitle = title,
                        actionIntent = action.actionIntent,
                        remoteInput = remoteInput,
                        timestamp = System.currentTimeMillis()
                    )
                    Log.d(TAG, "Cached RemoteInputSession for $key")
                    return
                }
            }
        }
    }

    private fun sendCachedRemoteInputReply(
        session: RemoteInputSession,
        message: String
    ): Boolean {
        val replyBundle = Bundle().apply {
            putCharSequence(session.remoteInput.resultKey, message)
        }
        val replyIntent = Intent()
        RemoteInput.addResultsToIntent(arrayOf(session.remoteInput), replyIntent, replyBundle)
        return try {
            session.actionIntent.send(applicationContext, 0, replyIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed sending cached RemoteInput reply to ${session.senderTitle}", e)
            false
        }
    }

    private fun evictExpiredPendingReplies() {
        val now = System.currentTimeMillis()
        val iterator = pendingVoipReplies.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.timestamp > PENDING_VOIP_TIMEOUT_MS) {
                iterator.remove()
            }
        }
    }

    private fun buildSessionKey(packageName: String, title: String): String {
        return "${packageName.lowercase(Locale.ROOT)}|${normalizeTitle(title)}"
    }

    private fun normalizeTitle(title: String): String {
        return title.trim().lowercase(Locale.ROOT)
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
        val isOtp = OTP_KEYWORDS.any { content.contains(it) }
        val isLogistics = LOGISTICS_KEYWORDS.any { content.contains(it) }
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
        private const val PENDING_VOIP_TIMEOUT_MS = 30_000L
        private const val CACHE_MAX_AGE_MS = 3_600_000L

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
