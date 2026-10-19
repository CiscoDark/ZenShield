package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZenShieldWatchdogService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ZenShieldWatchdogService initialized")
        createNotificationChannel()
        acquireTemporaryWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stateManager = FocusStateManager.getInstance(applicationContext)

        if (intent?.action == ACTION_STOP_FOCUS) {
            Log.d(TAG, "Action STOP_FOCUS received from notification action")
            stateManager.setProfile(FocusProfile.OFF)
            stopForegroundService()
            return START_NOT_STICKY
        }

        startObservingFocusState(stateManager)

        // Ensure notification listener service is bound
        requestListenerRebind()

        return START_STICKY
    }

    private fun startObservingFocusState(stateManager: FocusStateManager) {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            stateManager.state.collect { state ->
                if (state.activeProfile == FocusProfile.OFF) {
                    Log.d(TAG, "Focus profile is OFF. Stopping watchdog foreground service.")
                    stopForegroundService()
                } else {
                    val notification = buildWatchdogNotification(state.activeProfile)
                    startForeground(NOTIFICATION_ID, notification)
                    Log.d(TAG, "Updated watchdog foreground notification for ${state.activeProfile.displayName}")
                }
            }
        }
    }

    private fun buildWatchdogNotification(profile: FocusProfile): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ZenShieldWatchdogService::class.java).apply {
                action = ACTION_STOP_FOCUS
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("ZenShield • ${profile.displayName} Active")
            .setContentText("Shielding calls, silencing noise, and guarding focus")
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_launcher_monochrome, "Turn Off", stopIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZenShield Active Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ZenShield focus screening and notification filtering active in background"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestListenerRebind() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val component = ComponentName(this, NotificationGuardService::class.java)
                NotificationListenerService.requestRebind(component)
                Log.d(TAG, "Requested rebind for NotificationGuardService")
            } catch (e: Exception) {
                Log.w(TAG, "Could not request rebind for NotificationGuardService: ${e.message}")
            }
        }
    }

    private fun acquireTemporaryWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZenShield:WatchdogWakeLock").apply {
                // Hold briefly for 5 seconds to transition into foreground reliably
                acquire(5000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire temporary WakeLock: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        observerJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        Log.i(TAG, "ZenShieldWatchdogService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ZenShieldWatchdog"
        const val CHANNEL_ID = "zenshield_watchdog_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_STOP_FOCUS = "com.example.zenshield.ACTION_STOP_FOCUS"

        fun start(context: Context) {
            val intent = Intent(context, ZenShieldWatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ZenShieldWatchdogService::class.java).apply {
                action = ACTION_STOP_FOCUS
            }
            context.startService(intent)
        }
    }
}
