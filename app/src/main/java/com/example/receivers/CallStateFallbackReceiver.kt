package com.example.receivers

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import com.example.services.CellularBouncerService

class CallStateFallbackReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val stateString = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (stateString != TelephonyManager.EXTRA_STATE_RINGING) {
            return
        }

        val stateManager = FocusStateManager.getInstance(context)
        val activeProfile = stateManager.getActiveProfile()

        // If Focus Shield is OFF, do not intervene
        if (activeProfile == FocusProfile.OFF) {
            return
        }

        @Suppress("DEPRECATION")
        val rawIncomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        val normalizedNumber = normalizePhoneNumber(rawIncomingNumber)

        val isGamingLockdown = (activeProfile == FocusProfile.GAMING && stateManager.isStrictGamingLockdown())

        // 1. Check Starred Contacts Bypass
        val isStarred = if (normalizedNumber.isNotBlank()) {
            checkIfStarredContact(context, normalizedNumber)
        } else {
            false
        }

        if (stateManager.isAllowStarredContacts() && isStarred) {
            if (isGamingLockdown) {
                Log.d(TAG, "Fallback: Gaming Lockdown active, dropping starred contact $normalizedNumber")
                dropCallImmediately(context, stateManager)
                return
            } else {
                Log.d(TAG, "Fallback: Starred contact bypass allowed for $normalizedNumber")
                return
            }
        }

        // 2. Check 3-Minute Repeat Caller Breakthrough
        val callPrefs = stateManager.getCallSharedPreferences()
        val repeatKey = "last_call_timestamp_$normalizedNumber"
        val lastCallTimestamp = if (normalizedNumber.isNotBlank()) callPrefs.getLong(repeatKey, 0L) else 0L
        val currentTimestamp = System.currentTimeMillis()
        val isWithinThreeMinutes = lastCallTimestamp > 0L && (currentTimestamp - lastCallTimestamp) <= CellularBouncerService.THREE_MINUTES_MS

        if (stateManager.isAllowRepeatCallers() && isWithinThreeMinutes && !isGamingLockdown) {
            Log.d(TAG, "Fallback: 3-Minute repeat breakthrough detected from $normalizedNumber! Clearing cooldown and allowing ringing.")
            callPrefs.edit().remove(repeatKey).apply()

            // Restore ringer to ALL
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback: Could not switch interruption filter to ALL", e)
            }
            return
        }

        // 3. First Call / Blocked Call: Record timestamp and drop immediately
        if (normalizedNumber.isNotBlank()) {
            callPrefs.edit().putLong(repeatKey, currentTimestamp).apply()
        }

        Log.d(TAG, "Fallback: Dropping disallowed ringing call from $normalizedNumber under profile ${activeProfile.displayName}")
        dropCallImmediately(context, stateManager)
    }

    private fun dropCallImmediately(context: Context, stateManager: FocusStateManager) {
        stateManager.incrementShieldedCall()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Fallback: Cannot end call without ANSWER_PHONE_CALLS permission")
            return
        }

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecomManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("MissingPermission")
                val callEnded = telecomManager.endCall()
                Log.d(TAG, "Fallback: telecomManager.endCall() result: $callEnded")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Fallback: SecurityException while invoking telecomManager.endCall()", e)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback: Exception invoking endCall", e)
        }
    }

    private fun checkIfStarredContact(context: Context, phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.STARRED)
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val starredIndex = it.getColumnIndex(ContactsContract.PhoneLookup.STARRED)
                    if (starredIndex != -1) {
                        return it.getInt(starredIndex) == 1
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Fallback: Error checking contact starred status", e)
            false
        }
    }

    private fun normalizePhoneNumber(rawNumber: String): String {
        return rawNumber.replace(Regex("[^0-9+]"), "").trim()
    }

    companion object {
        private const val TAG = "CallStateFallback"
    }
}
