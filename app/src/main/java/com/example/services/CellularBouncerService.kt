package com.example.services

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.FocusProfile
import com.example.core.FocusStateManager

class CellularBouncerService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val stateManager = FocusStateManager.getInstance(applicationContext)
        val activeProfile = stateManager.getActiveProfile()

        // 1. If focus mode is OFF, allow all calls unconditionally
        if (activeProfile == FocusProfile.OFF) {
            allowIncomingCall(callDetails)
            return
        }

        // 2. Only intercept incoming calls
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            allowIncomingCall(callDetails)
            return
        }

        val rawPhoneNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val normalizedNumber = normalizePhoneNumber(rawPhoneNumber)

        val isGamingLockdown = (activeProfile == FocusProfile.GAMING && stateManager.isStrictGamingLockdown())

        // 3. Starred Contacts Bypass Check
        val isStarred = if (normalizedNumber.isNotBlank()) {
            checkIfStarredContact(applicationContext, normalizedNumber)
        } else {
            false
        }

        if (stateManager.isAllowStarredContacts() && isStarred) {
            if (isGamingLockdown) {
                Log.d(TAG, "Gaming Lockdown active: blocking even starred contact $normalizedNumber")
                rejectIncomingCall(callDetails, stateManager, normalizedNumber)
                return
            } else {
                Log.d(TAG, "Allowing starred contact bypass for $normalizedNumber during ${activeProfile.displayName}")
                allowIncomingCall(callDetails)
                return
            }
        }

        // 4. 3-Minute Repeat Caller Breakthrough Engine
        val callPrefs = stateManager.getCallSharedPreferences()
        val repeatKey = "last_call_timestamp_$normalizedNumber"
        val lastCallTimestamp = if (normalizedNumber.isNotBlank()) callPrefs.getLong(repeatKey, 0L) else 0L
        val currentTimestamp = System.currentTimeMillis()
        val isWithinThreeMinutes = lastCallTimestamp > 0L && (currentTimestamp - lastCallTimestamp) <= THREE_MINUTES_MS

        if (stateManager.isAllowRepeatCallers() && isWithinThreeMinutes && !isGamingLockdown) {
            Log.d(TAG, "3-Minute repeat breakthrough detected from $normalizedNumber! Clearing cooldown and allowing call.")
            
            // Clear stored timestamp
            callPrefs.edit().remove(repeatKey).apply()

            // Temporarily set interruption filter to ALL so the device rings out loud
            setSystemInterruptionFilterAll()

            // Allow call to ring through
            allowIncomingCall(callDetails)
            return
        }

        // 5. First Call / Non-Repeat Call: Save timestamp & Terminate at cellular network level
        if (normalizedNumber.isNotBlank()) {
            callPrefs.edit().putLong(repeatKey, currentTimestamp).apply()
        }

        Log.d(TAG, "Terminating incoming call from $normalizedNumber under profile ${activeProfile.displayName} (Repeat window started)")
        rejectIncomingCall(callDetails, stateManager, normalizedNumber)
    }

    private fun allowIncomingCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }

    private fun rejectIncomingCall(
        callDetails: Call.Details,
        stateManager: FocusStateManager,
        normalizedNumber: String
    ) {
        stateManager.incrementShieldedCall()

        // Immediate "User Busy" signal sent to cellular tower
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()

        respondToCall(callDetails, response)
    }

    private fun setSystemInterruptionFilterAll() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                Log.d(TAG, "Interruption filter temporarily switched to ALL for breakthrough call")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set interruption filter to ALL", e)
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
            Log.w(TAG, "Error looking up contact starred status", e)
            false
        }
    }

    private fun normalizePhoneNumber(rawNumber: String): String {
        return rawNumber.replace(Regex("[^0-9+]"), "").trim()
    }

    companion object {
        private const val TAG = "CellularBouncerService"
        const val THREE_MINUTES_MS = 180_000L
    }
}
