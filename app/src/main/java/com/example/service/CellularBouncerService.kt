package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
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
        val focusState = stateManager.state.value

        // If focus mode is OFF, allow all calls unconditionally
        if (focusState.activeProfile == FocusProfile.OFF) {
            passCallThrough(callDetails)
            return
        }

        // Only process incoming calls
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            passCallThrough(callDetails)
            return
        }

        val incomingHandle = callDetails.handle
        val rawPhoneNumber = incomingHandle?.schemeSpecificPart ?: ""

        val isStarred = if (rawPhoneNumber.isNotBlank()) {
            checkIfStarredContact(applicationContext, rawPhoneNumber)
        } else {
            false
        }

        val shouldBlock = when {
            // Priority/Starred callers bypass the shield if enabled
            focusState.allowStarredContactsOnly && isStarred -> false
            // Strict DND or Gaming blocks non-starred callers
            focusState.activeProfile == FocusProfile.GAMING -> true
            focusState.activeProfile == FocusProfile.SLEEP -> !isStarred
            focusState.activeProfile == FocusProfile.DO_NOT_DISTURB -> !isStarred
            focusState.activeProfile == FocusProfile.WORK -> !isStarred
            else -> false
        }

        if (shouldBlock) {
            Log.d(TAG, "Blocking incoming call from $rawPhoneNumber under profile ${focusState.activeProfile.displayName}")
            stateManager.incrementShieldedCall()

            val callResponse = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .setSkipCallLog(false)
                .build()

            respondToCall(callDetails, callResponse)
        } else {
            Log.d(TAG, "Allowing priority call from $rawPhoneNumber under profile ${focusState.activeProfile.displayName}")
            passCallThrough(callDetails)
        }
    }

    private fun passCallThrough(callDetails: Call.Details) {
        val callResponse = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, callResponse)
    }

    private fun checkIfStarredContact(context: Context, phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
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

    companion object {
        private const val TAG = "CellularBouncerService"
    }
}
