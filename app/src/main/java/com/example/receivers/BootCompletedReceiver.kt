package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.FocusProfile
import com.example.core.FocusStateManager

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "BootCompletedReceiver triggered with action: $action")

        val stateManager = FocusStateManager.getInstance(context)
        when (action) {
            ACTION_EXPIRE_FOCUS_SESSION -> {
                stateManager.checkAndExpireIfNeeded()
                stateManager.evaluateWorkSchedule()
            }
            ACTION_EVALUATE_WORK_SCHEDULE -> {
                stateManager.checkAndExpireIfNeeded()
                stateManager.evaluateWorkSchedule()
                stateManager.scheduleNextWorkScheduleAlarm()
            }
            else -> {
                stateManager.restoreAfterBootOrScheduleTick()
            }
        }

        val currentState = stateManager.state.value

        // Restart Game Detection Service if auto game detection is enabled or Gaming Mode is active
        if (currentState.autoGameDetectionEnabled || currentState.activeProfile == FocusProfile.GAMING) {
            runCatching {
                com.example.services.GameDetectorService.start(context)
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
        const val ACTION_EXPIRE_FOCUS_SESSION = "com.example.zenshield.ACTION_EXPIRE_FOCUS_SESSION"
        const val ACTION_EVALUATE_WORK_SCHEDULE = "com.example.zenshield.ACTION_EVALUATE_WORK_SCHEDULE"
    }
}

