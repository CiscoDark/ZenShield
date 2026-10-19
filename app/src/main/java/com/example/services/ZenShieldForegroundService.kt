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
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Interactive Live Focus Controller Foreground Service.
 *
 * Implements Phase 9 specifications:
 * - Active-Only Lifecycle: only runs while an active focus profile is engaged.
 * - Live Notification in shade & lockscreen with native Chronometer countdown.
 * - Interactive RemoteViews for collapsed & expanded actions:
 *   - Swap Mode (cycles DND -> Gaming -> Work -> Sleep)
 *   - Turn Off (instantly stops focus & dismisses notification)
 *   - -15m, +15m, +30m real-time timer adjustments
 * - Background persistence watchdog: keeps NotificationGuardService bound.
 */
class ZenShieldForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ZenShieldForegroundService initialized")
        createNotificationChannel()
        acquireTemporaryWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stateManager = FocusStateManager.getInstance(applicationContext)
        val action = intent?.action

        when (action) {
            ACTION_STOP_FOCUS -> {
                Log.d(TAG, "Received ACTION_STOP_FOCUS: terminating focus session")
                stateManager.setProfile(FocusProfile.OFF)
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_CYCLE_MODE -> {
                Log.d(TAG, "Received ACTION_CYCLE_MODE: cycling active focus profile")
                stateManager.cycleNextActiveProfile(this)
            }
            ACTION_ADD_15_MIN -> {
                Log.d(TAG, "Received ACTION_ADD_15_MIN: adding 15 minutes to session")
                stateManager.adjustRemainingTime(this, 15)
            }
            ACTION_ADD_30_MIN -> {
                Log.d(TAG, "Received ACTION_ADD_30_MIN: adding 30 minutes to session")
                stateManager.adjustRemainingTime(this, 30)
            }
            ACTION_SUBTRACT_15_MIN -> {
                Log.d(TAG, "Received ACTION_SUBTRACT_15_MIN: subtracting 15 minutes from session")
                stateManager.adjustRemainingTime(this, -15)
            }
            ACTION_UPDATE_NOTIFICATION -> {
                Log.d(TAG, "Received ACTION_UPDATE_NOTIFICATION: refreshing notification UI")
            }
        }

        val currentState = stateManager.state.value
        if (currentState.activeProfile == FocusProfile.OFF) {
            Log.d(TAG, "Current profile is OFF. Stopping service immediately.")
            stopForegroundService()
            return START_NOT_STICKY
        }

        // Display or refresh the Live Notification
        val notification = buildLiveFocusNotification(
            profile = currentState.activeProfile,
            expiryTime = currentState.expiresAtMillis
        )
        startForeground(NOTIFICATION_ID, notification)

        // Ensure we continuously observe state changes
        startObservingFocusState(stateManager)

        // Ensure notification listener service is bound
        requestListenerRebind()

        return START_STICKY
    }

    private fun startObservingFocusState(stateManager: FocusStateManager) {
        if (observerJob != null) return

        observerJob = serviceScope.launch {
            stateManager.state.collect { state ->
                if (state.activeProfile == FocusProfile.OFF) {
                    Log.d(TAG, "Focus profile changed to OFF. Stopping foreground service and removing notification.")
                    stopForegroundService()
                } else {
                    val notification = buildLiveFocusNotification(
                        profile = state.activeProfile,
                        expiryTime = state.expiresAtMillis
                    )
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    Log.d(TAG, "Refreshed Live Focus Notification for ${state.activeProfile.displayName}")
                }
            }
        }
    }

    private fun buildLiveFocusNotification(
        profile: FocusProfile,
        expiryTime: Long?
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action PendingIntents
        val cyclePendingIntent = createServicePendingIntent(ACTION_CYCLE_MODE, 101)
        val stopPendingIntent = createServicePendingIntent(ACTION_STOP_FOCUS, 102)
        val sub15PendingIntent = createServicePendingIntent(ACTION_SUBTRACT_15_MIN, 103)
        val add15PendingIntent = createServicePendingIntent(ACTION_ADD_15_MIN, 104)
        val add30PendingIntent = createServicePendingIntent(ACTION_ADD_30_MIN, 105)

        val isTimed = expiryTime != null && expiryTime > System.currentTimeMillis()

        // 1. Collapsed RemoteViews
        val collapsedViews = RemoteViews(packageName, R.layout.notification_live_focus).apply {
            setTextViewText(R.id.tv_title, "🛡️ ${profile.displayName} Active")
            val subtitle = if (isTimed) {
                "Auto-bouncing calls & filtering alerts"
            } else {
                "Until turned off • Shielding calls"
            }
            setTextViewText(R.id.tv_subtitle, subtitle)

            if (isTimed && expiryTime != null) {
                setViewVisibility(R.id.chrono_timer, View.VISIBLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setChronometerCountDown(R.id.chrono_timer, true)
                }
                setChronometer(R.id.chrono_timer, expiryTime, "%s remaining", true)
            } else {
                setViewVisibility(R.id.chrono_timer, View.GONE)
            }

            setOnClickPendingIntent(R.id.btn_quick_add, add15PendingIntent)
            setOnClickPendingIntent(R.id.btn_quick_stop, stopPendingIntent)
        }

        // 2. Expanded RemoteViews
        val expandedViews = RemoteViews(packageName, R.layout.notification_live_focus_expanded).apply {
            setTextViewText(R.id.tv_expanded_title, "🛡️ ${profile.displayName} Active")
            val subtitle = if (isTimed) {
                "Auto-bouncing calls & filtering alerts"
            } else {
                "Until turned off • Shielding calls & silencing noise"
            }
            setTextViewText(R.id.tv_expanded_subtitle, subtitle)

            if (isTimed && expiryTime != null) {
                setViewVisibility(R.id.chrono_expanded, View.VISIBLE)
                setViewVisibility(R.id.tv_expanded_duration, View.GONE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setChronometerCountDown(R.id.chrono_expanded, true)
                }
                setChronometer(R.id.chrono_expanded, expiryTime, "%s remaining", true)
            } else {
                setViewVisibility(R.id.chrono_expanded, View.GONE)
                setViewVisibility(R.id.tv_expanded_duration, View.VISIBLE)
                setTextViewText(R.id.tv_expanded_duration, "Until Turned Off")
            }

            // Bind Actions
            setOnClickPendingIntent(R.id.btn_cycle_mode, cyclePendingIntent)
            setOnClickPendingIntent(R.id.btn_stop_focus, stopPendingIntent)
            setOnClickPendingIntent(R.id.btn_sub_15, sub15PendingIntent)
            setOnClickPendingIntent(R.id.btn_add_15, add15PendingIntent)
            setOnClickPendingIntent(R.id.btn_add_30, add30PendingIntent)
        }

        val builder = NotificationCompat.Builder(this, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(contentIntent)
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isTimed && expiryTime != null) {
            builder.setWhen(expiryTime)
            builder.setUsesChronometer(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true)
            }
        } else {
            builder.setShowWhen(false)
        }

        return builder.build()
    }

    private fun createServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ZenShieldForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LIVE_CHANNEL_ID,
                "ZenShield Live Focus Controller",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays the interactive Live Focus Controller notification and countdown timer"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
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
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZenShield:LiveServiceWakeLock").apply {
                acquire(5000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire temporary WakeLock: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        observerJob?.cancel()
        observerJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        observerJob = null
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        Log.i(TAG, "ZenShieldForegroundService destroyed and Live Notification canceled")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ZenShieldForeground"
        const val LIVE_CHANNEL_ID = "zenshield_live_focus_channel"
        const val NOTIFICATION_ID = 9001

        const val ACTION_CYCLE_MODE = "com.example.zenshield.ACTION_CYCLE_MODE"
        const val ACTION_ADD_15_MIN = "com.example.zenshield.ACTION_ADD_15_MIN"
        const val ACTION_ADD_30_MIN = "com.example.zenshield.ACTION_ADD_30_MIN"
        const val ACTION_SUBTRACT_15_MIN = "com.example.zenshield.ACTION_SUBTRACT_15_MIN"
        const val ACTION_STOP_FOCUS = "com.example.zenshield.ACTION_STOP_FOCUS"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.zenshield.ACTION_UPDATE_NOTIFICATION"

        fun start(context: Context) {
            val intent = Intent(context, ZenShieldForegroundService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ZenShieldForegroundService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ZenShieldForegroundService::class.java).apply {
                action = ACTION_STOP_FOCUS
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop ZenShieldForegroundService: ${e.message}", e)
            }
        }

        fun update(context: Context) {
            val intent = Intent(context, ZenShieldForegroundService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update ZenShieldForegroundService: ${e.message}", e)
            }
        }
    }
}
