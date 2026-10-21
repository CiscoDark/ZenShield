package com.example.services

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
 * NotificationGuardService (Role-Free Call Bouncer & Notification Guard)
 *
 * Provides:
 * 1. Role-Free Cellular Call Bouncing: Intercepts incoming cellular calls via the Notification
 *    Listener, checks Starred Contacts & 3-Minute Repeat Caller breakthrough, actively drops
 *    the call via the system Decline action, and executes zero-trace status bar cleanup.
 * 2. VoIP Call Bouncing (WhatsApp, Telegram, Messenger, Instagram, Discord).
 * 3. Two-Stage In-App VoIP Auto-Reply Engine with Zero-Airtime Guardrail (no cellular SMS,
 *    strictly native RemoteInput messaging).
 * 4. Per-profile whitelisting, Smart Urgent Scanner (OTPs, 2FA, logistics), and notification suppression.
 */
class NotificationGuardService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Cache of active RemoteInput sessions by "$packageName|$normalizedTitle"
    private val remoteInputSessions = ConcurrentHashMap<String, RemoteInputSession>()

    // Trackers for VoIP calls declined awaiting post-decline missed-call notification
    private val pendingVoipReplies = ConcurrentHashMap<String, PendingVoipReply>()

    // Trackers for recently declined cellular calls for post-decline missed call suppression (Zero-Trace Cleanup)
    private val recentlyBouncedCellularCalls = ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        serviceScope.launch {
            try {
                handleNotificationPostedInternal(sbn)
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled exception in NotificationGuard coroutine: ${e.message}", e)
            }
        }
    }

    private fun handleNotificationPostedInternal(sbn: StatusBarNotification) {

        // Suppress our own notifications from triggering shield loops
        if (sbn.packageName == packageName) {
            return
        }

        val stateManager = FocusStateManager.getInstance(applicationContext)
        val activeProfile = stateManager.getActiveProfile()

        // Cache any valid RemoteInput from messaging or VoIP notifications for Stage 1 pre-warming
        cacheRemoteInputFromNotification(sbn)

        // Evict expired pending replies (> 30 seconds old) and bounced calls (> 20 seconds old)
        evictExpiredPendingReplies()
        evictExpiredBouncedCalls()

        // If Focus Shield is OFF, do not intervene
        if (activeProfile == FocusProfile.OFF) {
            return
        }

        val isVoipCall = isIncomingVoipCall(sbn)
        val isCellularCall = isIncomingCellularCall(sbn)

        // Do not suppress ongoing foreground services that are NOT incoming calls
        if (sbn.isOngoing && !isVoipCall && !isCellularCall) {
            return
        }

        val isGamingLockdown = (activeProfile == FocusProfile.GAMING && stateManager.isStrictGamingLockdown())

        // =========================================================================
        // ZERO-TRACE CLEANUP: Clean up dialer post-decline missed call notifications
        // =========================================================================
        if (isDialerMissedCall(sbn)) {
            val callerName = extractCallerTitle(sbn)
            val normalizedCaller = normalizeTitle(callerName)
            val lastBounced = recentlyBouncedCellularCalls[normalizedCaller]
                ?: recentlyBouncedCellularCalls.entries.firstOrNull { (k, _) ->
                    k.contains(normalizedCaller) || normalizedCaller.contains(k)
                }?.value

            val now = System.currentTimeMillis()
            if (lastBounced != null && (now - lastBounced) <= RECENT_BOUNCE_WINDOW_MS) {
                Log.i(TAG, "Zero-Trace Cleanup: Suppressing post-decline missed call notification for '$callerName'")
                stateManager.incrementShieldedNotification()
                try {
                    cancelNotification(sbn.key)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cancelling post-decline missed call: ${e.message}")
                }
                return
            }
        }

        // =========================================================================
        // STAGE 2: VoIP Post-Decline Missed-Call Catch & Auto-Reply Execution
        // =========================================================================
        val handledPostDecline = handlePostDeclineVoipCatch(sbn, activeProfile, stateManager)
        if (handledPostDecline) {
            return
        }

        // =========================================================================
        // CELLULAR CALL INTERCEPTION & BOUNCER (Role-Free Notification Listener)
        // =========================================================================
        if (isCellularCall) {
            val callerName = extractCallerTitle(sbn)
            Log.d(TAG, "Incoming Cellular call detected from '$callerName' (${sbn.packageName})")

            val declineAllRegardless = stateManager.isDeclineAllCallsRegardless()

            if (!declineAllRegardless) {
                // 1. Starred Contacts Check
                val isStarred = if (stateManager.isAllowStarredContacts()) {
                    checkIfStarredContact(applicationContext, callerName)
                } else {
                    false
                }

                if (isStarred) {
                    if (isGamingLockdown) {
                        Log.d(TAG, "Cellular call from Starred Contact '$callerName' blocked due to Strict Gaming Lockdown")
                    } else {
                        Log.d(TAG, "Allowing cellular call from Starred Contact: '$callerName'")
                        return // Let call ring through undisturbed
                    }
                }

                // 2. 3-Minute Repeat Caller Breakthrough
                val isRepeatBreakthrough = stateManager.isAllowRepeatCallers() &&
                        !isGamingLockdown &&
                        stateManager.checkCellularRepeatBreakthrough(callerName)

                if (isRepeatBreakthrough) {
                    Log.d(TAG, "Cellular 3-minute repeat breakthrough confirmed for '$callerName'! Allowing call to ring.")
                    triggerBreakthroughHaptic()
                    return // Let call ring through
                }
            } else {
                Log.i(TAG, "Decline All Calls Regardless enabled: immediately declining cellular call from '$callerName' with ZERO exceptions")
            }

            // 3. Actively Decline Cellular Call at Network/Dialer Level
            Log.d(TAG, "Actively declining cellular call from '$callerName' under profile ${activeProfile.displayName} (declineAllRegardless=$declineAllRegardless)")
            stateManager.incrementShieldedCall()

            // Record in recently bounced list for post-decline missed call suppression (Zero-Trace Cleanup)
            val normalizedCaller = normalizeTitle(callerName)
            recentlyBouncedCellularCalls[normalizedCaller] = System.currentTimeMillis()

            val hangUpSuccessful = attemptProgrammaticHangUp(sbn)
            Log.d(TAG, "Cellular call programmatic hang up executed (success=$hangUpSuccessful)")

            // 4. Zero-Trace Cleanup: Immediately cancel/snooze incoming notification so screen stays clean
            try {
                cancelNotification(sbn.key)
                Log.d(TAG, "Zero-trace cleanup: cancelled ringing notification ${sbn.key}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not cancel ringing notification: ${e.message}, falling back to snooze")
                try {
                    snoozeNotification(sbn.key, 60_000L)
                } catch (_: Exception) {}
            }
            return
        }

        // =========================================================================
        // VOIP CALL REJECTION (WhatsApp, Telegram, Messenger, Instagram, Discord)
        // =========================================================================
        if (isVoipCall) {
            val callerName = extractCallerTitle(sbn)
            val sessionKey = buildSessionKey(sbn.packageName, callerName)
            Log.d(TAG, "Incoming VoIP call detected from '$callerName' (${sbn.packageName})")

            val declineAllRegardless = stateManager.isDeclineAllCallsRegardless()

            if (!declineAllRegardless) {
                // 1. 3-Minute Repeat Caller Logic for VoIP Calls
                val isRepeatBreakthrough = stateManager.isAllowRepeatCallers() &&
                        !isGamingLockdown &&
                        stateManager.checkVoipRepeatBreakthrough(callerName)

                if (isRepeatBreakthrough) {
                    Log.d(TAG, "VoIP 3-minute repeat breakthrough confirmed for '$callerName'! Allowing call to ring.")
                    triggerBreakthroughHaptic()
                    return // Allow call to ring through
                }
            } else {
                Log.i(TAG, "Decline All Calls Regardless enabled: immediately rejecting VoIP call from '$callerName' with ZERO exceptions")
            }

            // 2. Programmatically Hang Up VoIP Call
            Log.d(TAG, "Rejecting VoIP call from '$callerName' under profile ${activeProfile.displayName} (declineAllRegardless=$declineAllRegardless)")
            stateManager.incrementShieldedCall()

            val hangUpSuccessful = attemptProgrammaticHangUp(sbn)

            // 3. Stage 1 Auto-Reply Attempt (Immediate via pre-cached RemoteInputSession)
            if (stateManager.isAutoReplyEnabled() && stateManager.shouldSendAutoReply(callerName)) {
                val cachedSession = remoteInputSessions[sessionKey]
                if (cachedSession != null && (System.currentTimeMillis() - cachedSession.timestamp) < CACHE_MAX_AGE_MS) {
                    val message = FocusStateManager.getAutoReplyMessage(applicationContext)
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
                // Fallback: Cancel notification cleanly to suppress heads-up popup
                try {
                    cancelNotification(sbn.key)
                    Log.d(TAG, "VoIP call fallback cancel applied for ${sbn.key}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cancel VoIP call notification: ${e.message}, falling back to short snooze")
                    try {
                        snoozeNotification(sbn.key, 60_000L)
                    } catch (_: Exception) {}
                }
            }
            return
        }

        // =========================================================================
        // REGULAR NOTIFICATIONS: Whitelist, Smart Scanner, Auto-Reply, and Snooze
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

        // D. Zero-Trace Immediate Dismissal
        stateManager.incrementShieldedNotification()
        try {
            cancelNotification(sbn.key)
            Log.d(TAG, "Zero-trace cleanup: cancelled shielded notification from ${sbn.packageName}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel notification, falling back to temporary snooze: ${e.message}")
            try {
                snoozeNotification(sbn.key, 60_000L)
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
        val message = FocusStateManager.getAutoReplyMessage(applicationContext)

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

    private fun checkIfStarredContact(context: Context, callerIdentifier: String): Boolean {
        if (callerIdentifier.isBlank()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot check starred contact without READ_CONTACTS permission")
            return false
        }

        return try {
            // 1. Phone number lookup
            val digitsOnly = callerIdentifier.replace(Regex("[^0-9+]"), "").trim()
            if (digitsOnly.length >= 3) {
                val phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(digitsOnly))
                val projection = arrayOf(ContactsContract.PhoneLookup.STARRED)
                context.contentResolver.query(phoneUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val starredIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.STARRED)
                        if (starredIdx != -1 && cursor.getInt(starredIdx) == 1) {
                            return true
                        }
                    }
                }
            }

            // 2. Display Name lookup
            val contactUri = ContactsContract.Contacts.CONTENT_URI
            val contactProj = arrayOf(ContactsContract.Contacts.STARRED)
            val selection = "${ContactsContract.Contacts.DISPLAY_NAME} = ? OR ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?"
            val selectionArgs = arrayOf(callerIdentifier.trim(), callerIdentifier.trim())
            context.contentResolver.query(contactUri, contactProj, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val starredIdx = cursor.getColumnIndex(ContactsContract.Contacts.STARRED)
                    if (starredIdx != -1 && cursor.getInt(starredIdx) == 1) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking contact starred status for '$callerIdentifier': ${e.message}")
            false
        }
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

    private fun evictExpiredBouncedCalls() {
        val now = System.currentTimeMillis()
        val iterator = recentlyBouncedCellularCalls.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > RECENT_BOUNCE_WINDOW_MS) {
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

    private fun isKnownVoipApp(packageName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        return KNOWN_VOIP_PACKAGES.any { pkg.contains(it) }
    }

    private fun isIncomingCellularCall(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val pkg = sbn.packageName.lowercase(Locale.ROOT)

        // Cannot be our own app or known VoIP apps
        if (pkg == packageName.lowercase(Locale.ROOT) || isKnownVoipApp(pkg)) {
            return false
        }

        val isDialerPkg = KNOWN_DIALER_PACKAGES.any { pkg.contains(it) }
        val isCallCategory = notification.category == Notification.CATEGORY_CALL

        if (isDialerPkg || isCallCategory) {
            // Check for call action buttons (Decline / Reject / Answer / Dismiss) or fullScreenIntent
            val hasCallActions = notification.actions?.any { action ->
                val title = action.title?.toString()?.lowercase(Locale.ROOT) ?: ""
                title.contains("decline") || title.contains("reject") || title.contains("answer") || title.contains("dismiss")
            } == true

            if (notification.fullScreenIntent != null || hasCallActions) {
                return true
            }

            if (isCallCategory) {
                val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val content = "$title $text".lowercase(Locale.ROOT)
                if (CALL_KEYWORDS.any { content.contains(it) }) {
                    return true
                }
            }
        }

        return false
    }

    private fun isDialerMissedCall(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName.lowercase(Locale.ROOT)
        val isDialerPkg = KNOWN_DIALER_PACKAGES.any { pkg.contains(it) }
        val notification = sbn.notification ?: return false

        val isMissedCategory = notification.category == Notification.CATEGORY_MISSED_CALL
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title $text".lowercase(Locale.ROOT)
        val hasMissedText = content.contains("missed call") || content.contains("missed voice call")

        return (isDialerPkg || isMissedCategory) && (isMissedCategory || hasMissedText)
    }

    private fun isIncomingVoipCall(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val pkg = sbn.packageName.lowercase(Locale.ROOT)

        if (!isKnownVoipApp(pkg)) {
            return false
        }

        // Category check
        if (notification.category == Notification.CATEGORY_CALL) {
            return true
        }

        // Full screen intent check (standard pattern for incoming VoIP calls)
        if (notification.fullScreenIntent != null) {
            return true
        }

        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title $text".lowercase(Locale.ROOT)

        val hasCallText = CALL_KEYWORDS.any { content.contains(it) }
        val hasCallActions = notification.actions?.any { action ->
            val actionTitle = action.title?.toString()?.lowercase(Locale.ROOT) ?: ""
            actionTitle.contains("decline") || actionTitle.contains("reject") || actionTitle.contains("answer")
        } == true

        return hasCallText || hasCallActions
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
                    Log.d(TAG, "Successfully fired decline/hangup actionIntent: '$title'")
                    return true
                } catch (e: PendingIntent.CanceledException) {
                    Log.w(TAG, "Action intent canceled while hanging up call", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Error executing hang up action", e)
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
                    val message = FocusStateManager.getAutoReplyMessage(applicationContext)

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(android.content.ComponentName(this, NotificationGuardService::class.java))
                Log.i(TAG, "NotificationGuardService requested automatic rebind on disconnect")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to rebind on disconnect: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotificationGuard"

        fun ensureBound(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val component = android.content.ComponentName(context, NotificationGuardService::class.java)
                    requestRebind(component)
                    Log.d(TAG, "Requested rebind for NotificationGuardService")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not request rebind for NotificationGuardService: ${e.message}")
                }
            }
        }

        private const val PENDING_VOIP_TIMEOUT_MS = 30_000L
        private const val RECENT_BOUNCE_WINDOW_MS = 20_000L
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

        private val KNOWN_DIALER_PACKAGES = setOf(
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.android.phone",
            "com.samsung.android.dialer",
            "com.oneplus.dialer",
            "com.miui.home",
            "com.android.incallui"
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
