package com.example.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Backward compatibility alias delegating to ZenShieldForegroundService.
 */
class ZenShieldWatchdogService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Forward start to the live foreground service
        ZenShieldForegroundService.start(applicationContext)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = ZenShieldForegroundService.LIVE_CHANNEL_ID
        const val NOTIFICATION_ID = ZenShieldForegroundService.NOTIFICATION_ID
        const val ACTION_STOP_FOCUS = ZenShieldForegroundService.ACTION_STOP_FOCUS

        fun start(context: Context) {
            ZenShieldForegroundService.start(context)
        }

        fun stop(context: Context) {
            ZenShieldForegroundService.stop(context)
        }
    }
}
