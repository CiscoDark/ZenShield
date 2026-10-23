package com.example.services

import android.Manifest
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
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
import android.telecom.TelecomManager
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
 * 2. VoIP Call Bouncing (WhatsApp, WhatsApp Business, Telegram, Messenger, Instagram, Discord, Signal, etc.)
 *    with full support for Android 12-15 Notification.CallStyle (EXTRA_DECLINE_INTENT / EXTRA_HANG_UP_INTENT),
 *    Android 14/15 Background Activity Launch (BAL) PendingIntent privileges, same-call notification update
 *    burst coalescing, and multi-stage follow-up hang-up verification.
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

    // Trackers for VoIP/Cellular calls currently in the middle of being declined (prevents WhatsApp's
    // rapid notification updates during a single call from triggering a false 3-minute repeat breakthrough)
    private val activelyDecliningCalls = ConcurrentHashMap<String, Long>()
    private val notificationKeyToSessionKey = ConcurrentHashMap<String, String>()

    // Trackers for calls that were allowed to ring via repeat-caller breakthrough so subsequent
    // notification updates for the same ringing call continue to be allowed cleanly
    private val activeBreakthroughCalls = ConcurrentHashMap<String, Long>()

    // Trackers for recently placed outgoing calls to completely suppress auto-replies
    private val recentlyPlacedOutgoingCalls = ConcurrentHashMap<String, Long>()

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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        // When a ringing call notification is removed after being declined or answered,
        // clear the active breakthrough and active-decline burst trackers for that notification
        // so a subsequent redial within 3 minutes is immediately recognized as a new call.
        activeBreakthroughCalls.remove(sbn.key)
        activelyDecliningCalls.remove(sbn.key)
        notificationKeyToSessionKey.remove(sbn.key)?.let { sessionKey ->
            activelyDecliningCalls.remove(sessionKey)
        }
    }

    internal fun handleNotificationPostedInternal(sbn: StatusBarNotification) {
        // Suppress our own notifications from triggering shield loops
        if (sbn.packageName == packageName) {
            return
        }

        val stateManager = FocusStateManager.getInstance(applicationContext)
        val activeProfile = stateManager.getActiveProfile()

        // Evict expired pending replies, bounced calls, active decline bursts, and outgoing calls
        evictExpiredPendingReplies()
        evictExpiredBouncedCalls()
        evictExpiredActivelyDecliningCalls()
        evictExpiredOutgoingCalls()

        val isVoipCall = isIncomingVoipCall(sbn)
        val isCellularCall = isIncomingCellularCall(sbn)

        // Cache any valid RemoteInput from non-call messaging notifications for Stage 1 pre-warming.
        // Do not overwrite an existing chat RemoteInput session with a ringing call's action intent.
        if (!isVoipCall && !isCellularCall) {
            cacheRemoteInputFromNotification(sbn)
        }

        // If Focus Shield is OFF, do not intervene
        if (activeProfile == FocusProfile.OFF) {
            return
        }

        // Check for Outgoing Calls placed by the user (Dialer, WhatsApp, Telegram, etc.)
        // Only classify as outgoing if it is NOT detected as an incoming VoIP or cellular call.
        if (!isVoipCall && !isCellularCall && isOutgoingCall(sbn)) {
            val recipient = extractCallerTitle(sbn)
            recordOutgoingCall(recipient, stateManager)
            Log.i(TAG, "Outgoing call detected to '$recipient' (${sbn.packageName}). Suppressing auto-replies and allowing call without intervention.")
            return // Let user's outgoing call proceed completely undisturbed
        }

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
                    k.isNotBlank() && normalizedCaller.isNotBlank() &&
                        (k.contains(normalizedCaller) || normalizedCaller.contains(k))
                }?.value

            val now = System.currentTimeMillis()
            if (lastBounced != null && (now - lastBounced) <= RECENT_BOUNCE_WINDOW_MS) {
                Log.i(TAG, "Zero-Trace Cleanup: Suppressing post-decline missed call notification for '$callerName'")
                stateManager.incrementShieldedNotification(
                    sourceApp = callerName,
                    detail = "Suppressed post-decline missed call alert"
                )
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
        if (!isVoipCall && !isCellularCall) {
            val handledPostDecline = handlePostDeclineVoipCatch(sbn, activeProfile, stateManager)
            if (handledPostDecline) {
                return
            }
        }

        // =========================================================================
        // CELLULAR CALL INTERCEPTION & BOUNCER (Role-Free Notification + Telecom)
        // =========================================================================
        if (isCellularCall) {
            val callerName = extractCallerTitle(sbn)
            val sessionKey = buildSessionKey(sbn.packageName, callerName)
            Log.d(TAG, "Incoming Cellular call detected from '$callerName' (${sbn.packageName})")

            val declineAllRegardless = stateManager.isDeclineAllCallsRegardless()

            // Respect networkLevelCallRejection toggle unless Decline All Calls Lockdown is active
            if (!declineAllRegardless && !stateManager.state.value.networkLevelCallRejection) {
                Log.d(TAG, "Cellular call rejection disabled in Rules; skipping hang up for '$callerName'")
                return
            }

            // If this exact call is already in the middle of being declined (notification update burst),
            // fire hang-up again without re-running repeat-caller breakthrough or double-counting telemetry.
            val activeDeclineStart = activelyDecliningCalls[sessionKey] ?: activelyDecliningCalls[sbn.key]
            val now = System.currentTimeMillis()
            if (activeDeclineStart != null && (now - activeDeclineStart) <= SAME_CALL_DECLINE_BURST_MS) {
                Log.d(TAG, "Same-call cellular notification update for '$callerName' — refreshing burst & re-firing hang up")
                activelyDecliningCalls[sessionKey] = now
                activelyDecliningCalls[sbn.key] = now
                notificationKeyToSessionKey[sbn.key] = sessionKey
                val hungUp = attemptProgrammaticHangUp(sbn) || attemptTelecomEndCall()
                Log.d(TAG, "Same-call cellular hang-up retry result=$hungUp")
                return
            }

            // If this call already broke through via repeat-caller or starred rules, allow its updates
            val activeBreakthrough = activeBreakthroughCalls[sbn.key] ?: activeBreakthroughCalls[sessionKey]
            if (!declineAllRegardless && activeBreakthrough != null && (now - activeBreakthrough) <= SAME_CALL_BREAKTHROUGH_WINDOW_MS) {
                activeBreakthroughCalls[sbn.key] = now
                activeBreakthroughCalls[sessionKey] = now
                return
            }

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
                        activeBreakthroughCalls[sbn.key] = now
                        activeBreakthroughCalls[sessionKey] = now
                        return // Let call ring through undisturbed
                    }
                }

                // 2. 3-Minute Repeat Caller Breakthrough
                val isRepeatBreakthrough = stateManager.isAllowRepeatCallers() &&
                        !isGamingLockdown &&
                        stateManager.checkCellularRepeatBreakthrough(callerName)

                if (isRepeatBreakthrough) {
                    Log.d(TAG, "Cellular 3-minute repeat breakthrough confirmed for '$callerName'! Allowing call to ring.")
                    activeBreakthroughCalls[sbn.key] = now
                    activeBreakthroughCalls[sessionKey] = now
                    triggerBreakthroughHaptic()
                    return // Let call ring through
                }
            } else {
                Log.i(TAG, "Decline All Calls Regardless enabled: immediately declining cellular call from '$callerName' with ZERO exceptions")
            }

            // 3. Actively Decline Cellular Call at Network/Dialer Level
            Log.d(TAG, "Actively declining cellular call from '$callerName' under profile ${activeProfile.displayName} (declineAllRegardless=$declineAllRegardless)")
            activelyDecliningCalls[sessionKey] = now
            activelyDecliningCalls[sbn.key] = now
            notificationKeyToSessionKey[sbn.key] = sessionKey
            stateManager.incrementShieldedCall(callerLabel = callerName, isVoip = false)

            // Record in recently bounced list for post-decline missed call suppression (Zero-Trace Cleanup)
            val normalizedCaller = normalizeTitle(callerName)
            recentlyBouncedCellularCalls[normalizedCaller] = now

            val notifHangUp = attemptProgrammaticHangUp(sbn)
            val telecomHangUp = attemptTelecomEndCall()
            val hangUpSuccessful = notifHangUp || telecomHangUp
            Log.d(TAG, "Cellular call programmatic hang up executed (notif=$notifHangUp, telecom=$telecomHangUp)")
            scheduleFollowUpHangUpRetries(sbn.key, sbn.packageName)

            if (!hangUpSuccessful) {
                // Fallback: Cancel/snooze incoming notification if no hangup action was available
                try {
                    cancelNotification(sbn.key)
                    Log.d(TAG, "Zero-trace cleanup: cancelled ringing notification ${sbn.key}")
                } catch (e: Exception) {
                    try {
                        snoozeNotification(sbn.key, 60_000L)
                    } catch (_: Exception) {}
                }
            }
            return
        }

        // =========================================================================
        // VOIP CALL REJECTION (WhatsApp, Telegram, Messenger, Instagram, Discord)
        // =========================================================================
        if (isVoipCall) {
            val callerName = extractCallerTitle(sbn)
            val hasResolvedCaller = hasResolvedCallerIdentity(callerName, sbn.packageName)
            val sessionKey = buildSessionKey(sbn.packageName, callerName)
            val now = System.currentTimeMillis()
            Log.d(TAG, "Incoming VoIP call detected from '$callerName' (resolved=$hasResolvedCaller, pkg=${sbn.packageName})")

            val declineAllRegardless = stateManager.isDeclineAllCallsRegardless()

            // Check if VoIP auto-decline is enabled for the current mode (unless Decline All Calls Regardless is on)
            val voipDeclineEnabled = if (activeProfile == FocusProfile.GAMING) {
                stateManager.state.value.autoDeclineVoipInGame || stateManager.state.value.autoDeclineVoipCalls
            } else {
                stateManager.state.value.autoDeclineVoipCalls
            }
            if (!declineAllRegardless && !voipDeclineEnabled) {
                Log.d(TAG, "VoIP auto-decline is disabled in Rules; allowing VoIP call from '$callerName'")
                return
            }

            // If WhatsApp posts an initial 0ms foreground-service connection placeholder ("WhatsApp — Incoming voice call")
            // with NO resolved caller name and NO actions yet, wait for the real caller notification update (100ms later)
            // unless Decline All Calls Regardless is active.
            if (!declineAllRegardless && !hasResolvedCaller && sbn.notification?.actions.isNullOrEmpty() &&
                !hasCallStyleExtraIntent(sbn.notification?.extras ?: Bundle.EMPTY, EXTRA_DECLINE_INTENT_KEY)
            ) {
                Log.d(TAG, "VoIP placeholder notification without caller identity or actions yet; scheduling follow-up check")
                scheduleFollowUpHangUpRetries(sbn.key, sbn.packageName)
                return
            }

            // CRITICAL FIX: WhatsApp posts multiple rapid notification updates (at 0ms, 150ms when avatar loads,
            // 400ms when ring state settles) for a SINGLE incoming call.
            // If we already started declining this exact resolved caller session (or notification key when caller wasn't upgraded),
            // immediately re-fire attemptProgrammaticHangUp(sbn) without triggering a false 3-minute repeat breakthrough!
            val existingSessionDecline = activelyDecliningCalls[sessionKey]
            val existingKeyDecline = activelyDecliningCalls[sbn.key]
            val previousSessionKeyForSbn = notificationKeyToSessionKey[sbn.key]
            val isCallerIdentityJustResolved = hasResolvedCaller &&
                existingSessionDecline == null &&
                previousSessionKeyForSbn != null &&
                previousSessionKeyForSbn != sessionKey

            val activeDeclineStart = if (isCallerIdentityJustResolved) null else (existingSessionDecline ?: existingKeyDecline)
            if (activeDeclineStart != null && (now - activeDeclineStart) <= SAME_CALL_DECLINE_BURST_MS) {
                activelyDecliningCalls[sessionKey] = now
                activelyDecliningCalls[sbn.key] = now
                notificationKeyToSessionKey[sbn.key] = sessionKey
                val retrySuccess = attemptProgrammaticHangUp(sbn)
                Log.d(TAG, "Same-call VoIP notification update for '$callerName' — re-fired hang up (success=$retrySuccess)")
                return
            }

            // If this call already broke through via repeat-caller or starred contact rules, allow its updates
            val activeBreakthrough = activeBreakthroughCalls[sbn.key] ?: activeBreakthroughCalls[sessionKey]
            if (!declineAllRegardless && activeBreakthrough != null && (now - activeBreakthrough) <= SAME_CALL_BREAKTHROUGH_WINDOW_MS) {
                activeBreakthroughCalls[sbn.key] = now
                activeBreakthroughCalls[sessionKey] = now
                Log.d(TAG, "Allowing ongoing VoIP breakthrough call update for '$callerName'")
                return
            }

            if (!declineAllRegardless) {
                // 1. Starred Contacts Check (when not in Strict Gaming Lockdown)
                val isStarred = if (stateManager.isAllowStarredContacts() && !isGamingLockdown && hasResolvedCaller) {
                    checkIfStarredContact(applicationContext, callerName)
                } else {
                    false
                }

                if (isStarred) {
                    Log.d(TAG, "Allowing VoIP call from Starred Contact: '$callerName'")
                    activeBreakthroughCalls[sbn.key] = now
                    activeBreakthroughCalls[sessionKey] = now
                    return
                }

                // 2. 3-Minute Repeat Caller Logic for VoIP Calls
                val isRepeatBreakthrough = stateManager.isAllowRepeatCallers() &&
                        !isGamingLockdown &&
                        hasResolvedCaller &&
                        stateManager.checkVoipRepeatBreakthrough(callerName)

                if (isRepeatBreakthrough) {
                    Log.d(TAG, "VoIP 3-minute repeat breakthrough confirmed for '$callerName'! Allowing call to ring.")
                    activeBreakthroughCalls[sbn.key] = now
                    activeBreakthroughCalls[sessionKey] = now
                    triggerBreakthroughHaptic()
                    return // Allow call to ring through
                }
            } else {
                Log.i(TAG, "Decline All Calls Regardless enabled: immediately rejecting VoIP call from '$callerName' with ZERO exceptions")
            }

            // 3. Programmatically Hang Up VoIP Call
            Log.d(TAG, "Rejecting VoIP call from '$callerName' under profile ${activeProfile.displayName} (declineAllRegardless=$declineAllRegardless)")
            activelyDecliningCalls[sessionKey] = now
            activelyDecliningCalls[sbn.key] = now
            notificationKeyToSessionKey[sbn.key] = sessionKey
            if (!isCallerIdentityJustResolved) {
                stateManager.incrementShieldedCall(callerLabel = callerName, isVoip = true)
            }

            val hangUpSuccessful = attemptProgrammaticHangUp(sbn)
            Log.i(TAG, "VoIP call initial programmatic hang up for '$callerName' executed (success=$hangUpSuccessful)")

            // Schedule follow-up hang-up retries at 250ms, 550ms, and 1000ms in case WhatsApp's VoiceService
            // was still in PRE_ACCEPT_RECEIVED state at 0ms and ignored the first PendingIntent.
            scheduleFollowUpHangUpRetries(sbn.key, sbn.packageName)

            // 4. Register Auto-Reply (Stage 1 delayed + Stage 2 post-decline missed-call catch)
            if (hasResolvedCaller && stateManager.isAutoReplyEnabled() && stateManager.shouldSendAutoReply(callerName)) {
                // Always register for Stage 2 so if WhatsApp posts a "Missed voice call" notification with
                // a fresh RemoteInput action after the call terminates, Stage 2 catches and replies to it.
                pendingVoipReplies[sessionKey] = PendingVoipReply(
                    packageName = sbn.packageName,
                    callerTitle = callerName,
                    activeProfile = activeProfile,
                    timestamp = now
                )

                val cachedSession = remoteInputSessions[sessionKey]
                if (cachedSession != null && (now - cachedSession.timestamp) < CACHE_MAX_AGE_MS) {
                    // Delay Stage 1 reply by 500ms so the RemoteInput PendingIntent never collides with
                    // WhatsApp's VoiceService call-decline PendingIntent at t = 0ms.
                    serviceScope.launch {
                        delay(500L)
                        if (pendingVoipReplies.containsKey(sessionKey) &&
                            stateManager.isAutoReplyEnabled() &&
                            stateManager.shouldSendAutoReply(callerName)
                        ) {
                            val message = FocusStateManager.getAutoReplyMessage(applicationContext)
                            val replied = sendCachedRemoteInputReply(cachedSession, message)
                            if (replied) {
                                pendingVoipReplies.remove(sessionKey)
                                stateManager.recordAutoReplySent(callerName)
                                Log.i(TAG, "Stage 1: Dispatched delayed VoIP chat auto-reply to '$callerName' via cached RemoteInput")
                            }
                        }
                    }
                }
            }

            if (!hangUpSuccessful) {
                // Fallback only when no Decline PendingIntent could be found/fired:
                // try deleteIntent first, then snooze/cancel to suppress the heads-up banner.
                val deleteSent = sbn.notification?.deleteIntent?.let { sendPendingIntentSafely(it) } ?: false
                if (!deleteSent) {
                    try {
                        snoozeNotification(sbn.key, 60_000L)
                        Log.d(TAG, "VoIP call fallback snooze applied for ${sbn.key}")
                    } catch (e: Exception) {
                        try {
                            cancelNotification(sbn.key)
                        } catch (_: Exception) {}
                    }
                }
            }
            return
        }

        // =========================================================================
        // REGULAR NOTIFICATIONS: Whitelist, Smart Scanner, Auto-Reply, and Snooze
        // =========================================================================

        // Respect the user's silenceNotifications rule toggle (unless in Strict Gaming Lockdown with suppressHeadsUpBanners)
        if (!stateManager.state.value.silenceNotifications && !(isGamingLockdown && stateManager.state.value.suppressHeadsUpBanners)) {
            Log.d(TAG, "Notification silencing disabled in settings; allowing notification from ${sbn.packageName}")
            return
        }

        // A. Whitelisted Package Check (Per-Profile Whitelist)
        if (stateManager.isPackageWhitelisted(activeProfile, sbn.packageName)) {
            Log.d(TAG, "Allowing notification from whitelisted package: ${sbn.packageName} for ${activeProfile.displayName}")
            return
        }

        // B. Smart Breakthrough Scanner (OTP, 2FA, Logistics, Urgent Keywords)
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val combinedContent = "$title $text $bigText".lowercase(Locale.ROOT)

        val isUrgentBreakthrough = stateManager.state.value.smartUrgentBreakthroughEnabled &&
                matchesUrgentKeywords(combinedContent)
        if (isUrgentBreakthrough && !isGamingLockdown) {
            Log.d(TAG, "Smart Urgent Breakthrough triggered for notification from ${sbn.packageName} ('$title')")
            triggerBreakthroughHaptic()
            return // Allow visible notification
        }

        // C. Zero-Trace Immediate Dismissal (Clean silence & dismissal without replying to text messages)
        val sourceLabel = title.ifBlank { sbn.packageName }
        stateManager.incrementShieldedNotification(
            sourceApp = sourceLabel,
            detail = "Silenced & dismissed without auto-reply"
        )
        try {
            cancelNotification(sbn.key)
            Log.d(TAG, "Zero-trace cleanup: silenced and dismissed shielded notification from ${sbn.packageName}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel notification, falling back to temporary snooze: ${e.message}")
            try {
                snoozeNotification(sbn.key, 60_000L)
            } catch (_: Exception) {}
        }
    }

    /**
     * Schedules follow-up hang-up checks at 350ms and 800ms after the initial decline attempt.
     * In WhatsApp, the initial foreground notification at t=0ms can fire while the VoIP state machine
     * is still in PRE_ACCEPT_RECEIVED; re-firing the Decline PendingIntent once the call settles
     * into RINGING guarantees reliable call termination.
     */
    private fun scheduleFollowUpHangUpRetries(notificationKey: String, pkgName: String) {
        serviceScope.launch {
            for (delayMs in listOf(350L, 800L)) {
                delay(delayMs)
                try {
                    val currentActive = runCatching { activeNotifications }.getOrNull() ?: break
                    val matchingSbn = currentActive.firstOrNull {
                        it.key == notificationKey ||
                            (it.packageName == pkgName && (isIncomingVoipCall(it) || isIncomingCellularCall(it)))
                    } ?: break
                    val retryResult = attemptProgrammaticHangUp(matchingSbn)
                    Log.d(TAG, "Follow-up hang-up retry (+${delayMs}ms) for $pkgName executed (success=$retryResult)")
                } catch (e: Exception) {
                    Log.w(TAG, "Follow-up hang-up retry failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Sweeps currently active notifications when Focus Mode activates or when the listener binds,
     * ensuring an already-ringing WhatsApp or cellular call is immediately declined.
     */
    private fun sweepActiveNotificationsForCalls() {
        serviceScope.launch {
            try {
                val activeList = runCatching { activeNotifications }.getOrNull() ?: return@launch
                for (sbn in activeList) {
                    if (isIncomingVoipCall(sbn) || isIncomingCellularCall(sbn)) {
                        handleNotificationPostedInternal(sbn)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not sweep active notifications: ${e.message}")
            }
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

        // If user initiated an outgoing call to this recipient, suppress Stage 2 auto-reply completely
        if (isRecentOutgoingCall(title) || stateManager.isRecentOutgoingCall(title)) {
            Log.i(TAG, "Stage 2: Suppressing post-decline reply for '$title' because user initiated an outgoing call to them.")
            return false
        }

        val normTitle = normalizeTitle(title)
        val sessionKey = buildSessionKey(pkg, title)

        // Find matching pending reply by session key or matching title in same package
        val matchedEntry = pendingVoipReplies.entries.firstOrNull { (key, pending) ->
            pending.packageName == pkg && (
                    key == sessionKey ||
                            (normTitle.isNotBlank() && normalizeTitle(pending.callerTitle).let { normPending ->
                                normPending.isNotBlank() && (normTitle.contains(normPending) || normPending.contains(normTitle))
                            })
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
                val actionIntent = action.actionIntent ?: continue
                for (remoteInput in remoteInputs) {
                    if (remoteInput.allowFreeFormInput || remoteInput.resultKey != null) {
                        val replyBundle = Bundle().apply {
                            putCharSequence(remoteInput.resultKey, message)
                        }
                        val replyIntent = Intent()
                        RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, replyBundle)
                        if (sendPendingIntentSafely(actionIntent, replyIntent)) {
                            replied = true
                            Log.i(TAG, "Stage 2: Successfully sent in-app VoIP auto-reply via notification RemoteInput")
                            break
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
        stateManager.incrementShieldedNotification(
            sourceApp = pending.callerTitle,
            detail = if (replied) "Suppressed missed VoIP call & sent auto-reply" else "Suppressed missed VoIP call alert"
        )
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
            val cleanIdentifier = stripDirectionalFormatting(callerIdentifier).trim()
            // 1. Phone number lookup
            val digitsOnly = cleanIdentifier.replace(Regex("[^0-9+]"), "").trim()
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
            val selectionArgs = arrayOf(cleanIdentifier, cleanIdentifier)
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
        val title = extractCallerTitle(sbn).takeIf {
            it.isNotBlank() && it != "Unknown Caller" && it != sbn.packageName
        } ?: return

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            val actionIntent = action.actionIntent ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.allowFreeFormInput || remoteInput.resultKey != null) {
                    val key = buildSessionKey(sbn.packageName, title)
                    remoteInputSessions[key] = RemoteInputSession(
                        packageName = sbn.packageName,
                        senderTitle = title,
                        actionIntent = actionIntent,
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
        return sendPendingIntentSafely(session.actionIntent, replyIntent)
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

    private fun evictExpiredActivelyDecliningCalls() {
        val now = System.currentTimeMillis()
        val declineIterator = activelyDecliningCalls.entries.iterator()
        while (declineIterator.hasNext()) {
            val entry = declineIterator.next()
            if (now - entry.value > SAME_CALL_DECLINE_BURST_MS) {
                declineIterator.remove()
            }
        }
        val breakthroughIterator = activeBreakthroughCalls.entries.iterator()
        while (breakthroughIterator.hasNext()) {
            val entry = breakthroughIterator.next()
            if (now - entry.value > SAME_CALL_BREAKTHROUGH_WINDOW_MS) {
                breakthroughIterator.remove()
            }
        }
    }

    private fun evictExpiredOutgoingCalls() {
        val now = System.currentTimeMillis()
        val iterator = recentlyPlacedOutgoingCalls.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > OUTGOING_CALL_COOLDOWN_MS) {
                iterator.remove()
            }
        }
    }

    private fun recordOutgoingCall(recipient: String, stateManager: FocusStateManager) {
        if (recipient.isBlank()) return
        val norm = normalizeTitle(recipient)
        recentlyPlacedOutgoingCalls[norm] = System.currentTimeMillis()
        stateManager.recordOutgoingCall(recipient)
        Log.i(TAG, "Recorded outgoing call to '$recipient' ($norm). Auto-replies suppressed.")
    }

    private fun isRecentOutgoingCall(recipient: String): Boolean {
        if (recipient.isBlank()) return false
        val norm = normalizeTitle(recipient)
        val now = System.currentTimeMillis()
        val lastTimestamp = recentlyPlacedOutgoingCalls[norm]
            ?: recentlyPlacedOutgoingCalls.entries.firstOrNull { (k, _) ->
                k.isNotBlank() && norm.isNotBlank() && (k.contains(norm) || norm.contains(k))
            }?.value

        if (lastTimestamp != null && (now - lastTimestamp) <= OUTGOING_CALL_COOLDOWN_MS) {
            return true
        }
        return false
    }

    /**
     * Determines if a notification represents an outgoing or active ongoing call placed by the user.
     * Prevents false-positive incoming call classification and blocks erroneous auto-replies,
     * while strictly guaranteeing incoming calls are NEVER misclassified as outgoing.
     */
    internal fun isOutgoingCall(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val pkg = sbn.packageName.lowercase(Locale.ROOT)
        val isDialer = KNOWN_DIALER_PACKAGES.any { pkg.contains(it) }
        val isVoip = isKnownVoipApp(pkg)
        val isCallCategory = notification.category == Notification.CATEGORY_CALL

        if (!isDialer && !isVoip && !isCallCategory) {
            return false
        }

        val extras = notification.extras ?: Bundle.EMPTY

        // 1. Android 12+ CallStyle check:
        // CALL_TYPE_INCOMING (1) or CALL_TYPE_SCREENING (3) is strictly INCOMING, never outgoing!
        val callType = getCallStyleType(extras)
        if (callType == 1 || callType == 3) {
            return false
        }

        // 2. If extras contain EXTRA_ANSWER_INTENT or EXTRA_DECLINE_INTENT, it is strictly an INCOMING call.
        if (hasCallStyleExtraIntent(extras, EXTRA_ANSWER_INTENT_KEY) ||
            hasCallStyleExtraIntent(extras, EXTRA_DECLINE_INTENT_KEY)
        ) {
            return false
        }

        // 3. If notification has an Answer / Accept / Join action or an explicit Incoming-only Decline/Reject action,
        // it is an INCOMING call, not outgoing.
        val hasIncomingAction = notification.actions?.any { action ->
            val actionTitle = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
            ANSWER_KEYWORDS.any { actionTitle.contains(it) } ||
                STRICT_INCOMING_DECLINE_KEYWORDS.any { actionTitle.contains(it) }
        } == true

        if (hasIncomingAction) {
            return false
        }

        // 4. Inspect notification text, subtext, and big text for incoming vs outgoing keywords
        val combinedContent = extractCombinedNotificationText(notification)
        if (INCOMING_CALL_KEYWORDS.any { combinedContent.contains(it) }) {
            return false
        }

        // 5. Android 12+ CallStyle: CALL_TYPE_ONGOING (2) is an active/outgoing call
        if (callType == 2) {
            return true
        }

        // 6. Explicit outgoing / active ongoing call keywords
        if (OUTGOING_CALL_KEYWORDS.any { combinedContent.contains(it) }) {
            return true
        }

        // 7. If it's a dialer or VoIP call notification without any incoming indicators, no fullScreenIntent,
        // and has Mute / Speaker / Hold / Return to call action
        val hasOngoingInCallControls = notification.actions?.any { action ->
            val actionTitle = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
            actionTitle.contains("mute") || actionTitle.contains("speaker") ||
                actionTitle.contains("hold") || actionTitle.contains("return")
        } == true

        if ((isDialer || isVoip || isCallCategory) && hasOngoingInCallControls && notification.fullScreenIntent == null) {
            return true
        }

        return false
    }

    private fun buildSessionKey(packageName: String, title: String): String {
        return "${packageName.lowercase(Locale.ROOT)}|${normalizeTitle(title)}"
    }

    private fun normalizeTitle(title: String): String {
        return stripDirectionalFormatting(title).trim().lowercase(Locale.ROOT)
    }

    private fun stripDirectionalFormatting(input: String): String {
        return input.replace(DIRECTIONAL_CHARS_REGEX, "")
    }

    private fun isKnownVoipApp(packageName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        return KNOWN_VOIP_PACKAGES.any { pkg.contains(it) }
    }

    private fun getCallStyleType(extras: Bundle): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
            } else {
                extras.getInt("android.callType", -1)
            }
        } catch (_: Exception) {
            -1
        }
    }

    private fun hasCallStyleExtraIntent(extras: Bundle, key: String): Boolean {
        return getCallStylePendingIntent(extras, key) != null
    }

    @Suppress("DEPRECATION")
    private fun getCallStylePendingIntent(extras: Bundle, key: String): PendingIntent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(key, PendingIntent::class.java)
            } else {
                extras.getParcelable(key) as? PendingIntent
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCombinedNotificationText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        val ticker = notification.tickerText?.toString() ?: ""
        return stripDirectionalFormatting("$title $bigTitle $text $subText $bigText $summaryText $ticker")
            .lowercase(Locale.ROOT)
    }

    internal fun isIncomingCellularCall(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val pkg = sbn.packageName.lowercase(Locale.ROOT)

        // Cannot be our own app or known VoIP apps
        if (pkg == packageName.lowercase(Locale.ROOT) || isKnownVoipApp(pkg)) {
            return false
        }

        val isDialerPkg = KNOWN_DIALER_PACKAGES.any { pkg.contains(it) }
        val isCallCategory = notification.category == Notification.CATEGORY_CALL
        if (!isDialerPkg && !isCallCategory) {
            return false
        }

        val extras = notification.extras ?: Bundle.EMPTY
        val callType = getCallStyleType(extras)
        if (callType == 1 || callType == 3) {
            return true
        }
        if (callType == 2) {
            return false
        }

        if (hasCallStyleExtraIntent(extras, EXTRA_DECLINE_INTENT_KEY) ||
            hasCallStyleExtraIntent(extras, EXTRA_ANSWER_INTENT_KEY)
        ) {
            return true
        }

        val hasAnswerAction = notification.actions?.any { action ->
            val title = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
            ANSWER_KEYWORDS.any { title.contains(it) }
        } == true

        val content = extractCombinedNotificationText(notification)
        val hasIncomingKeywords = INCOMING_CALL_KEYWORDS.any { content.contains(it) }

        if (hasAnswerAction || hasIncomingKeywords) {
            return true
        }

        if (isOutgoingCall(sbn)) {
            return false
        }

        if (notification.fullScreenIntent != null) {
            val hasDeclineAction = notification.actions?.any { action ->
                val aTitle = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
                DECLINE_KEYWORDS.any { aTitle.contains(it) }
            } == true
            if (hasDeclineAction) {
                return true
            }
        }

        return false
    }

    private fun isDialerMissedCall(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName.lowercase(Locale.ROOT)
        val isDialerPkg = KNOWN_DIALER_PACKAGES.any { pkg.contains(it) }
        val notification = sbn.notification ?: return false

        val isMissedCategory = notification.category == Notification.CATEGORY_MISSED_CALL
        val content = extractCombinedNotificationText(notification)
        val hasMissedText = content.contains("missed call") || content.contains("missed voice call") ||
            content.contains("missed video call")

        return (isDialerPkg || isMissedCategory) && (isMissedCategory || hasMissedText)
    }

    /**
     * Detects incoming VoIP calls across WhatsApp, WhatsApp Business, Telegram, Discord, Instagram,
     * Messenger, Signal, Teams, etc., supporting:
     * - Android 12-15 Notification.CallStyle (EXTRA_CALL_TYPE == 1, EXTRA_DECLINE_INTENT, EXTRA_ANSWER_INTENT)
     * - Standard and localized Notification.Action buttons
     * - System DND-silenced WhatsApp call banners ("Silenced by Do Not Disturb", "WhatsApp voice call")
     * - Full-screen and heads-up call notifications
     */
    internal fun isIncomingVoipCall(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val pkg = sbn.packageName.lowercase(Locale.ROOT)

        if (!isKnownVoipApp(pkg)) {
            return false
        }

        val extras = notification.extras ?: Bundle.EMPTY
        val content = extractCombinedNotificationText(notification)

        // Never classify a "Missed voice/video call" summary notification as a live ringing call
        if (notification.category == Notification.CATEGORY_MISSED_CALL ||
            content.contains("missed voice call") ||
            content.contains("missed video call") ||
            content.contains("missed call") ||
            content.contains("missed group call")
        ) {
            return false
        }

        // 1. Android 12+ Notification.CallStyle explicit incoming call type (1 = CALL_TYPE_INCOMING, 3 = CALL_TYPE_SCREENING)
        val callType = getCallStyleType(extras)
        if (callType == 1 || callType == 3) {
            return true
        }
        if (callType == 2) {
            // 2 = CALL_TYPE_ONGOING (already answered or outgoing call in progress)
            return false
        }

        // 2. Android 12+ CallStyle EXTRA_DECLINE_INTENT or EXTRA_ANSWER_INTENT
        if (hasCallStyleExtraIntent(extras, EXTRA_DECLINE_INTENT_KEY) ||
            hasCallStyleExtraIntent(extras, EXTRA_ANSWER_INTENT_KEY)
        ) {
            return true
        }

        // 3. Action buttons check: Answer/Accept/Join action or strict Incoming Decline/Reject action
        val hasAnswerAction = notification.actions?.any { action ->
            val actionTitle = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
            ANSWER_KEYWORDS.any { actionTitle.contains(it) }
        } == true

        val hasStrictDeclineAction = notification.actions?.any { action ->
            val actionTitle = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
            action.remoteInputs.isNullOrEmpty() && STRICT_INCOMING_DECLINE_KEYWORDS.any { actionTitle.contains(it) }
        } == true

        if (hasAnswerAction || hasStrictDeclineAction) {
            return true
        }

        // 4. Explicit incoming call text keywords (e.g., "Incoming voice call", "Silenced by Do Not Disturb", "is calling")
        if (INCOMING_CALL_KEYWORDS.any { content.contains(it) }) {
            return true
        }

        // 5. Bypass if clearly an outgoing call
        if (isOutgoingCall(sbn)) {
            return false
        }

        // 6. Category CALL or fullScreenIntent with any decline/hangup capability or WhatsApp voice/video call text
        val isCallCategory = notification.category == Notification.CATEGORY_CALL
        val hasFullScreenIntent = notification.fullScreenIntent != null
        val hasAnyDeclineAction = notification.actions?.any { action ->
            val actionTitle = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
            action.remoteInputs.isNullOrEmpty() && DECLINE_KEYWORDS.any { actionTitle.contains(it) }
        } == true
        val hasVoipCallDescriptor = VOIP_CALL_DESCRIPTOR_KEYWORDS.any { content.contains(it) }

        if ((isCallCategory || hasFullScreenIntent) && (hasAnyDeclineAction || hasVoipCallDescriptor)) {
            return true
        }

        // 7. If a known VoIP app posts a CATEGORY_CALL notification with >= 2 non-RemoteInput actions
        // (e.g., localized [Decline, Answer] buttons), classify as incoming call
        if (isCallCategory) {
            val nonReplyActionsCount = notification.actions?.count { it.remoteInputs.isNullOrEmpty() } ?: 0
            if (nonReplyActionsCount >= 2) {
                return true
            }
        }

        return false
    }

    /**
     * Extracts the caller's display name or phone number reliably from:
     * 1. Android 12+ Notification.EXTRA_CALL_PERSON (Person.getName())
     * 2. Notification.EXTRA_TITLE / EXTRA_TITLE_BIG / EXTRA_CONVERSATION_TITLE as CharSequence (supporting SpannableString)
     * 3. Strips invisible Unicode Bidi markers (\u200E, \u202A-\u202C, etc.) injected by WhatsApp.
     */
    internal fun extractCallerTitle(sbn: StatusBarNotification): String {
        val notification = sbn.notification ?: return "Unknown Caller"
        val extras = notification.extras ?: return "Unknown Caller"

        // 1. Check Android 12+ CallStyle Person (Notification.EXTRA_CALL_PERSON = "android.callPerson")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val callPerson = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable("android.callPerson", Person::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable("android.callPerson") as? Person
                }
            } catch (_: Exception) {
                null
            }
            val personName = callPerson?.name?.toString()?.let { stripDirectionalFormatting(it).trim() }
            if (!personName.isNullOrBlank() && !isGenericAppOrCallLabel(personName)) {
                return personName
            }
        }

        // 2. Check EXTRA_CONVERSATION_TITLE, EXTRA_TITLE, and EXTRA_TITLE_BIG using getCharSequence (critical for SpannableString!)
        val candidates = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
        )

        for (candidate in candidates) {
            val cleaned = stripDirectionalFormatting(candidate).trim()
            if (cleaned.isNotBlank() && !isGenericAppOrCallLabel(cleaned)) {
                return cleaned
            }
        }

        // 3. Fallback to first non-blank title even if generic, or EXTRA_TEXT
        for (candidate in candidates) {
            val cleaned = stripDirectionalFormatting(candidate).trim()
            if (cleaned.isNotBlank()) return cleaned
        }

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?.let { stripDirectionalFormatting(it).trim() }
        if (!text.isNullOrBlank()) return text

        return sbn.packageName
    }

    private fun hasResolvedCallerIdentity(callerName: String, pkgName: String): Boolean {
        val cleaned = stripDirectionalFormatting(callerName).trim()
        if (cleaned.isBlank()) return false
        val lower = cleaned.lowercase(Locale.ROOT)
        if (lower == "unknown caller" || lower == "unknown" || lower == pkgName.lowercase(Locale.ROOT)) {
            return false
        }
        return !isGenericAppOrCallLabel(cleaned)
    }

    private fun isGenericAppOrCallLabel(label: String): Boolean {
        val lower = label.lowercase(Locale.ROOT)
        return lower == "whatsapp" ||
            lower == "whatsapp business" ||
            lower == "telegram" ||
            lower == "messenger" ||
            lower == "instagram" ||
            lower == "discord" ||
            lower == "incoming voice call" ||
            lower == "incoming video call" ||
            lower == "incoming audio call" ||
            lower == "incoming call" ||
            lower == "whatsapp voice call" ||
            lower == "whatsapp video call" ||
            lower == "calling..." ||
            lower == "ringing..." ||
            lower == "connecting..." ||
            lower.startsWith("com.") ||
            lower.startsWith("org.")
    }

    /**
     * Native TelecomManager fallback to drop incoming cellular calls at the telephony layer
     * when ANSWER_PHONE_CALLS is granted.
     */
    @Suppress("DEPRECATION", "MissingPermission")
    internal fun attemptTelecomEndCall(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
            val ended = telecomManager.endCall()
            if (ended) {
                Log.i(TAG, "TelecomManager.endCall() succeeded in dropping cellular call")
            }
            ended
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager.endCall() failed: ${e.message}")
            false
        }
    }

    /**
     * Multi-vector programmatic call hang-up engine:
     * Vector 1: Android 12-15 Notification.CallStyle extras (EXTRA_DECLINE_INTENT & EXTRA_HANG_UP_INTENT)
     * Vector 2: Notification.Action & WearableExtender matching DECLINE_KEYWORDS or semantic decline/delete actions
     * Vector 3: Positional action fallback for incoming call notifications ([Decline, Answer] layout convention)
     * Vector 4: Custom RemoteViews PendingIntent reflection for OEM/custom VoIP call layouts
     */
    internal fun attemptProgrammaticHangUp(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val extras = notification.extras ?: Bundle.EMPTY
        var hungUp = false
        val firedIntents = mutableSetOf<PendingIntent>()

        // Vector 1: Android 12+ Notification.CallStyle EXTRA_DECLINE_INTENT ("android.declineIntent")
        // and EXTRA_HANG_UP_INTENT ("android.hangUpIntent")
        val declineExtraIntent = getCallStylePendingIntent(extras, EXTRA_DECLINE_INTENT_KEY)
        if (declineExtraIntent != null) {
            if (sendPendingIntentSafely(declineExtraIntent)) {
                firedIntents.add(declineExtraIntent)
                hungUp = true
                Log.i(TAG, "Vector 1: Fired CallStyle EXTRA_DECLINE_INTENT for ${sbn.packageName}")
            }
        }

        val hangUpExtraIntent = getCallStylePendingIntent(extras, EXTRA_HANG_UP_INTENT_KEY)
        if (hangUpExtraIntent != null && !firedIntents.contains(hangUpExtraIntent)) {
            if (sendPendingIntentSafely(hangUpExtraIntent)) {
                firedIntents.add(hangUpExtraIntent)
                hungUp = true
                Log.i(TAG, "Vector 1b: Fired CallStyle EXTRA_HANG_UP_INTENT for ${sbn.packageName}")
            }
        }

        val answerExtraIntent = getCallStylePendingIntent(extras, EXTRA_ANSWER_INTENT_KEY)

        // Combine standard notification.actions and WearableExtender actions
        val allActions = mutableListOf<Notification.Action>()
        notification.actions?.let { allActions.addAll(it) }
        runCatching {
            val wearableActions = Notification.WearableExtender(notification).actions
            if (!wearableActions.isNullOrEmpty()) {
                allActions.addAll(wearableActions)
            }
        }

        // Vector 2: Inspect actions for explicit Decline / Reject / Hang up / End action
        if (allActions.isNotEmpty()) {
            for (action in allActions) {
                val actionIntent = action.actionIntent ?: continue
                if (firedIntents.contains(actionIntent)) continue

                // Never fire an action that expects RemoteInput (e.g., WhatsApp "Reply" / "Message" button)
                if (!action.remoteInputs.isNullOrEmpty()) continue

                // Never fire the Answer intent
                if (answerExtraIntent != null && actionIntent == answerExtraIntent) continue

                val title = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
                if (ANSWER_KEYWORDS.any { title.contains(it) }) continue
                // Guard against "decline with message" / "reject with sms" secondary actions
                if (title.contains("message") || title.contains("sms") || title.contains("reply")) continue

                val isDeclineTitle = DECLINE_KEYWORDS.any { title.contains(it) }
                val isDeclineSemantic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    action.semanticAction == Notification.Action.SEMANTIC_ACTION_THUMBS_DOWN ||
                        action.semanticAction == Notification.Action.SEMANTIC_ACTION_DELETE
                } else {
                    false
                }

                if (isDeclineTitle || isDeclineSemantic) {
                    if (sendPendingIntentSafely(actionIntent)) {
                        firedIntents.add(actionIntent)
                        hungUp = true
                        Log.i(TAG, "Vector 2: Fired decline actionIntent '$title' for ${sbn.packageName}")
                        break
                    }
                }
            }

            // Vector 3: Positional fallback for Incoming Calls when actions are localized/unlabeled
            // Standard Android & WhatsApp incoming call layout places [Decline, Answer] (plus optional [Reply] with RemoteInput).
            if (!hungUp) {
                val nonReplyActions = allActions.filter { action ->
                    val t = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
                    action.actionIntent != null &&
                        action.remoteInputs.isNullOrEmpty() &&
                        !t.contains("message") &&
                        !t.contains("reply")
                }
                if (nonReplyActions.size >= 2) {
                    val candidateDecline = nonReplyActions.firstOrNull { action ->
                        val title = stripDirectionalFormatting(action.title?.toString() ?: "").lowercase(Locale.ROOT)
                        val isAnswer = (answerExtraIntent != null && action.actionIntent == answerExtraIntent) ||
                            ANSWER_KEYWORDS.any { title.contains(it) }
                        !isAnswer
                    } ?: nonReplyActions[0]

                    candidateDecline.actionIntent?.let { pi ->
                        if (sendPendingIntentSafely(pi)) {
                            firedIntents.add(pi)
                            hungUp = true
                            Log.i(TAG, "Vector 3: Fired positional decline action '${candidateDecline.title}' for ${sbn.packageName}")
                        }
                    }
                }
            }
        }

        // Vector 4: Custom RemoteViews reflection (headsUpContentView, bigContentView, contentView)
        if (!hungUp) {
            @Suppress("DEPRECATION")
            val remoteViewsList = listOfNotNull(
                notification.headsUpContentView,
                notification.bigContentView,
                notification.contentView
            )
            for (remoteViews in remoteViewsList) {
                val extractedIntents = extractPendingIntentsFromRemoteViews(remoteViews)
                    .filter { pi ->
                        pi != notification.contentIntent &&
                            pi != answerExtraIntent &&
                            !firedIntents.contains(pi)
                    }
                if (extractedIntents.isNotEmpty()) {
                    // In custom call RemoteViews ([Decline, Answer]), the first clickable button intent is Decline
                    val declineCandidate = extractedIntents.first()
                    if (sendPendingIntentSafely(declineCandidate)) {
                        firedIntents.add(declineCandidate)
                        hungUp = true
                        Log.i(TAG, "Vector 4: Fired RemoteViews decline PendingIntent for ${sbn.packageName}")
                        break
                    }
                }
            }
        }

        return hungUp
    }

    /**
     * Reflectively extracts clickable PendingIntents from custom RemoteViews (mActions)
     * when OEM dialers or VoIP apps use custom notification layouts instead of Notification.Action.
     */
    private fun extractPendingIntentsFromRemoteViews(remoteViews: RemoteViews): List<PendingIntent> {
        val results = mutableListOf<PendingIntent>()
        try {
            var clazz: Class<*>? = remoteViews.javaClass
            var mActionsField: java.lang.reflect.Field? = null
            while (clazz != null && mActionsField == null) {
                mActionsField = runCatching { clazz.getDeclaredField("mActions") }.getOrNull()
                clazz = clazz.superclass
            }
            mActionsField?.isAccessible = true
            val actionsList = mActionsField?.get(remoteViews) as? Iterable<*> ?: return emptyList()
            for (actionObj in actionsList) {
                if (actionObj == null) continue
                for (field in actionObj.javaClass.declaredFields) {
                    field.isAccessible = true
                    val value = runCatching { field.get(actionObj) }.getOrNull()
                    if (value is PendingIntent) {
                        results.add(value)
                    } else if (value != null && value.javaClass.name.contains("PendingIntent")) {
                        runCatching {
                            val getPiMethod = value.javaClass.getDeclaredMethod("getPendingIntent")
                            getPiMethod.isAccessible = true
                            (getPiMethod.invoke(value) as? PendingIntent)?.let { results.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "RemoteViews reflection fallback skipped: ${e.message}")
        }
        return results.distinct()
    }

    /**
     * Sends a PendingIntent with Android 14/15 (API 34+) Background Activity Launch (BAL) allowance
     * so WhatsApp and system dialer Decline PendingIntents are never blocked by ActivityTaskManager.
     */
    private fun sendPendingIntentSafely(pendingIntent: PendingIntent, fillInIntent: Intent? = null): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val options = ActivityOptions.makeBasic()
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                pendingIntent.send(
                    applicationContext,
                    0,
                    fillInIntent,
                    null,
                    null,
                    null,
                    options.toBundle()
                )
                return true
            } catch (e: Exception) {
                Log.d(TAG, "API 34+ ActivityOptions PendingIntent send fallback: ${e.message}")
            }
        }

        return try {
            if (fillInIntent != null) {
                pendingIntent.send(applicationContext, 0, fillInIntent)
            } else {
                pendingIntent.send()
            }
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "PendingIntent was canceled: ${e.message}")
            false
        } catch (e: Exception) {
            try {
                pendingIntent.send(applicationContext, 0, fillInIntent ?: Intent())
                true
            } catch (inner: Exception) {
                Log.w(TAG, "Error executing PendingIntent: ${inner.message}")
                false
            }
        }
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
        instance = this
        Log.i(TAG, "NotificationGuardService active and listening")
        sweepActiveNotificationsForCalls()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) {
            instance = null
        }
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
        if (instance === this) {
            instance = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotificationGuard"

        @Volatile
        private var instance: NotificationGuardService? = null

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
            instance?.sweepActiveNotificationsForCalls()
        }

        private const val EXTRA_DECLINE_INTENT_KEY = "android.declineIntent"
        private const val EXTRA_ANSWER_INTENT_KEY = "android.answerIntent"
        private const val EXTRA_HANG_UP_INTENT_KEY = "android.hangUpIntent"

        private const val PENDING_VOIP_TIMEOUT_MS = 30_000L
        private const val RECENT_BOUNCE_WINDOW_MS = 20_000L
        private const val SAME_CALL_DECLINE_BURST_MS = 15_000L
        private const val SAME_CALL_BREAKTHROUGH_WINDOW_MS = 45_000L
        private const val CACHE_MAX_AGE_MS = 3_600_000L
        private const val OUTGOING_CALL_COOLDOWN_MS = 600_000L

        private val DIRECTIONAL_CHARS_REGEX = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

        private val KNOWN_VOIP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.instagram.android",
            "com.discord",
            "com.skype.raider",
            "org.thoughtcrime.securesms",
            "com.slack",
            "com.microsoft.teams",
            "com.viber.voip",
            "jp.naver.line.android"
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

        private val INCOMING_CALL_KEYWORDS = setOf(
            "incoming voice call",
            "incoming video call",
            "incoming audio call",
            "incoming group call",
            "incoming call",
            "whatsapp voice call",
            "whatsapp video call",
            "whatsapp audio call",
            "encrypted voice call",
            "encrypted video call",
            "silenced by do not disturb",
            "is calling",
            "calling you",
            "wants you to join",
            "invited you to a group call",
            "call from"
        )

        private val VOIP_CALL_DESCRIPTOR_KEYWORDS = setOf(
            "voice call",
            "video call",
            "audio call",
            "group call",
            "end-to-end encrypted"
        )

        private val OUTGOING_CALL_KEYWORDS = setOf(
            "outgoing call",
            "outgoing voice call",
            "outgoing video call",
            "dialing",
            "ongoing call",
            "call in progress",
            "tap to return to call",
            "return to call"
        )

        private val ANSWER_KEYWORDS = setOf(
            "answer",
            "accept",
            "join",
            "pick up",
            "respond",
            "responder",
            "accepter",
            "annehmen",
            "accetta",
            "aceitar",
            "ответить",
            "kabul",
            "terima"
        )

        private val STRICT_INCOMING_DECLINE_KEYWORDS = setOf(
            "decline",
            "reject",
            "ignore",
            "refuse",
            "rechazar",
            "refuser",
            "ablehnen",
            "rifiuta",
            "recusar",
            "отклонить",
            "reddet",
            "tolak"
        )

        private val DECLINE_KEYWORDS = setOf(
            "decline",
            "reject",
            "hang up",
            "hangup",
            "end",
            "dismiss",
            "ignore",
            "drop",
            "cancel",
            "leave",
            "refuse",
            "rechazar",
            "refuser",
            "ablehnen",
            "rifiuta",
            "recusar",
            "отклонить",
            "reddet",
            "tolak",
            "từ chối",
            "거절",
            "拒否",
            "拒绝",
            "拒絕",
            "رفض"
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

