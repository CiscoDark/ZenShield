package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.core.WeeklyShieldSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-Time Live Focus Controller Foreground Service.
 *
 * Delivers an always-ticking Live Notification in the notification shade and lock screen:
 * - Promoted Top-of-Shade & Lock-Screen visibility (IMPORTANCE_LOW + VISIBILITY_PUBLIC + setOnlyAlertOnce)
 * - Continuous 1-second live Chronometer (countdown when timed, elapsed stopwatch when untimed)
 * - Live ProgressBar reflecting remaining session percentage or 30s Game Exit Grace countdown
 * - Real-time blocked telemetry counters (Calls Declined & Alerts Silenced)
 * - Instant in-place pill controls (-15m, +15m, +30m, Mode, Turn Off)
 */
class ZenShieldForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observerJob: Job? = null
    private var tickerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var systemTickReceiverRegistered = false

    private val systemTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val stateManager = FocusStateManager.getInstance(applicationContext)
            if (stateManager.checkAndExpireIfNeeded()) {
                stopForegroundService()
                return
            }
            stateManager.evaluateWorkSchedule()
            stateManager.evaluateSleepSchedule()
            val currentState = stateManager.state.value
            if (currentState.activeProfile == FocusProfile.OFF) {
                stopForegroundService()
            } else {
                publishNotificationUpdate(currentState, stateManager.weeklyStatsFlow.value)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ZenShieldForegroundService initialized")
        createNotificationChannel()
        acquireTemporaryWakeLock()
        registerSystemTickReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stateManager = FocusStateManager.getInstance(applicationContext)
        val action = intent?.action

        when (action) {
            ACTION_STOP_FOCUS -> {
                Log.d(TAG, "Received ACTION_STOP_FOCUS: terminating focus session")
                if (stateManager.state.value.pendingGameActivationPackage != null) {
                    stateManager.declinePendingGameActivation()
                }
                if (stateManager.state.value.activeProfile != FocusProfile.OFF) {
                    stateManager.setProfile(FocusProfile.OFF)
                }
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_CONFIRM_GAME_PROMPT -> {
                Log.d(TAG, "Received ACTION_CONFIRM_GAME_PROMPT: activating Gaming Mode immediately")
                stateManager.confirmPendingGameActivation()
            }
            ACTION_DECLINE_GAME_PROMPT -> {
                Log.d(TAG, "Received ACTION_DECLINE_GAME_PROMPT: declining Gaming Mode for current game session")
                stateManager.declinePendingGameActivation()
                if (stateManager.state.value.activeProfile == FocusProfile.OFF) {
                    stopForegroundService()
                    return START_NOT_STICKY
                }
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
            ACTION_TOGGLE_AUTO_REPLY -> {
                Log.d(TAG, "Received ACTION_TOGGLE_AUTO_REPLY: toggling declined call auto-reply")
                stateManager.toggleAutoReply()
            }
            ACTION_UPDATE_NOTIFICATION -> {
                Log.d(TAG, "Received ACTION_UPDATE_NOTIFICATION: refreshing Live Notification UI")
            }
        }

        stateManager.checkAndExpireIfNeeded()
        val currentState = stateManager.state.value
        if (currentState.activeProfile == FocusProfile.OFF) {
            Log.d(TAG, "Current profile is OFF. Stopping service immediately.")
            stopForegroundService()
            return START_NOT_STICKY
        }

        val weeklyStats = stateManager.weeklyStatsFlow.value
        val notification = buildLiveFocusNotification(
            state = currentState,
            weeklyStats = weeklyStats
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startObservingFocusState(stateManager)
        startLiveNotificationTicker(stateManager)
        requestListenerRebind()

        return START_STICKY
    }

    /**
     * Runs a continuous 1-second live heartbeat while Focus is active so:
     * 1. Timed sessions expire cleanly on the exact second at 00:00 (never counting negative) and update the progress bar.
     * 2. Game exit 30-second countdown updates live in the notification shade.
     */
    /**
     * Runs a lightweight background monitor while Focus is active so:
     * 1. Timed sessions expire cleanly on the exact second at 00:00 (never counting negative).
     * 2. Game exit 15-second countdown updates live in the notification shade.
     * 3. Progress bar refreshes periodically without triggering Android's background notify() rate limit,
     *    while native SystemUI Chronometer handles 1fps second-by-second countdown rendering.
     */
    private fun startLiveNotificationTicker(stateManager: FocusStateManager) {
        if (tickerJob?.isActive == true) return

        tickerJob = serviceScope.launch {
            var tickCounter = 0
            while (isActive) {
                if (stateManager.checkAndExpireIfNeeded()) {
                    stopForegroundService()
                    break
                }

                val state = stateManager.state.value
                if (state.activeProfile == FocusProfile.OFF) {
                    stopForegroundService()
                    break
                }

                val expiryTime = state.expiresAtMillis
                if (expiryTime != null && expiryTime > 0L) {
                    val remainingMs = expiryTime - System.currentTimeMillis()
                    if (remainingMs <= 0L) {
                        Log.i(TAG, "Active timer countdown reached 00:00. Reverting profile to FocusProfile.OFF")
                        stateManager.setProfile(FocusProfile.OFF)
                        stopForegroundService()
                        break
                    }
                }

                tickCounter++
                // Update every second only during the 15s game-exit grace badge, or every 15s for the progress bar.
                // Second-by-second timer digits are ticked natively by Android SystemUI Chronometer!
                if (state.gameExitCountdownSeconds != null || (expiryTime != null && tickCounter % 15 == 0)) {
                    publishNotificationUpdate(state, stateManager.weeklyStatsFlow.value)
                }

                delay(1000L)
            }
        }
    }

    private fun startObservingFocusState(stateManager: FocusStateManager) {
        if (observerJob?.isActive == true) return

        observerJob = serviceScope.launch {
            combine(stateManager.state, stateManager.weeklyStatsFlow) { state, weeklyStats ->
                state to weeklyStats
            }.collect { (state, weeklyStats) ->
                if (state.activeProfile == FocusProfile.OFF) {
                    Log.d(TAG, "Focus profile changed to OFF. Stopping foreground service and removing Live Notification.")
                    stopForegroundService()
                } else {
                    publishNotificationUpdate(state, weeklyStats)
                }
            }
        }
    }

    private fun publishNotificationUpdate(
        state: FocusSessionState,
        weeklyStats: WeeklyShieldSummary
    ) {
        val notification = buildLiveFocusNotification(state = state, weeklyStats = weeklyStats)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatNonNegativeCountdown(remainingMillis: Long): String {
        val clampedSeconds = maxOf(0L, remainingMillis / 1000L)
        val hours = clampedSeconds / 3600L
        val minutes = (clampedSeconds % 3600L) / 60L
        val seconds = clampedSeconds % 60L
        return if (hours > 0L) {
            String.format("%02d:%02d:%02d left", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d left", minutes, seconds)
        }
    }

    private fun buildLiveFocusNotification(
        state: FocusSessionState,
        weeklyStats: WeeklyShieldSummary
    ): Notification {
        val profile = state.activeProfile
        val expiryTime = state.expiresAtMillis
        val now = System.currentTimeMillis()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cyclePendingIntent = createServicePendingIntent(ACTION_CYCLE_MODE, 101)
        val stopPendingIntent = createServicePendingIntent(ACTION_STOP_FOCUS, 102)
        val sub15PendingIntent = createServicePendingIntent(ACTION_SUBTRACT_15_MIN, 103)
        val add15PendingIntent = createServicePendingIntent(ACTION_ADD_15_MIN, 104)
        val add30PendingIntent = createServicePendingIntent(ACTION_ADD_30_MIN, 105)
        val toggleAutoReplyPendingIntent = createServicePendingIntent(ACTION_TOGGLE_AUTO_REPLY, 106)

        val isTimed = expiryTime != null
        val remainingMs = if (expiryTime != null) maxOf(0L, expiryTime - now) else 0L
        val countdownChronometerBase = SystemClock.elapsedRealtime() + remainingMs
        val clampedCountdownText = formatNonNegativeCountdown(remainingMs)
        val elapsedMs = maxOf(0L, now - (state.sessionStartTimeMillis ?: now))
        val elapsedChronometerBase = SystemClock.elapsedRealtime() - elapsedMs

        // Compute live progress percentage (0..100)
        val graceSeconds = state.gameExitCountdownSeconds
        val progressPercent = when {
            graceSeconds != null -> ((graceSeconds / 15f) * 100f).toInt().coerceIn(3, 100)
            isTimed && expiryTime != null -> {
                val startMillis = state.sessionStartTimeMillis ?: (expiryTime - 3600_000L)
                val totalSpan = (expiryTime - startMillis).coerceAtLeast(60_000L)
                ((remainingMs.toFloat() / totalSpan.toFloat()) * 100f).toInt().coerceIn(0, 100)
            }
            else -> 100
        }

        // Today's live telemetry counts
        val todayStats = weeklyStats.days.lastOrNull()
        val liveCallsDeclined = todayStats?.declinedCalls ?: state.totalShieldedCalls
        val liveAlertsSilenced = todayStats?.silencedNotifications ?: state.totalShieldedNotifications

        val modeTitle = when (profile) {
            FocusProfile.GAMING -> "Gaming Focus"
            FocusProfile.DO_NOT_DISTURB -> "Do Not Disturb"
            FocusProfile.WORK -> "Work Focus"
            FocusProfile.SLEEP -> "Sleep Shield"
            FocusProfile.CINEMA -> "Cinema Shield"
            FocusProfile.OFF -> "Sentinal Standby"
        }

        val ruleSummary = buildString {
            append(if (state.autoReplyEnabled) "Call Auto-Reply ON (3m Cooldown)" else "Silent Call Bouncer • Reply OFF")
            append(" • ")
            append(if (state.declineAllCallsRegardless) "Zero-Tolerance" else "Alerts Silenced")
        }

        // 1. Collapsed Live Notification RemoteViews (Material 3 Tonal Card)
        val collapsedViews = RemoteViews(packageName, R.layout.notification_live_focus).apply {
            setTextViewText(R.id.tv_title, modeTitle)
            if (graceSeconds != null) {
                setTextViewText(R.id.tv_live_badge, "Exit ${graceSeconds}s")
                setTextColor(R.id.tv_live_badge, Color.parseColor("#FFDCC1"))
                setInt(R.id.tv_live_badge, "setBackgroundResource", R.drawable.notification_chip_grace_bg)
            } else {
                setTextViewText(R.id.tv_live_badge, "Active")
                setTextColor(R.id.tv_live_badge, Color.parseColor("#D3E3FD"))
                setInt(R.id.tv_live_badge, "setBackgroundResource", R.drawable.notification_chip_bg)
            }

            setTextViewText(
                R.id.tv_subtitle,
                "• $liveCallsDeclined Calls • $liveAlertsSilenced Alerts • ${if (state.autoReplyEnabled) "Reply ON" else "Reply OFF"}"
            )

            if (isTimed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Use native OS Chronometer countdown so SystemUI ticks every second even in Doze/background
                    setViewVisibility(R.id.tv_collapsed_duration, View.GONE)
                    setViewVisibility(R.id.chrono_timer, View.VISIBLE)
                    setChronometerCountDown(R.id.chrono_timer, true)
                    setChronometer(R.id.chrono_timer, countdownChronometerBase, "%s left", true)
                } else {
                    setViewVisibility(R.id.chrono_timer, View.GONE)
                    setViewVisibility(R.id.tv_collapsed_duration, View.VISIBLE)
                    setTextViewText(R.id.tv_collapsed_duration, clampedCountdownText)
                }
            } else {
                setViewVisibility(R.id.tv_collapsed_duration, View.GONE)
                setViewVisibility(R.id.chrono_timer, View.VISIBLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setChronometerCountDown(R.id.chrono_timer, false)
                }
                setChronometer(R.id.chrono_timer, elapsedChronometerBase, "%s", true)
            }

            setProgressBar(R.id.pb_collapsed_progress, 100, progressPercent, false)

            setOnClickPendingIntent(R.id.btn_quick_add, add15PendingIntent)
            setOnClickPendingIntent(R.id.btn_quick_stop, stopPendingIntent)
        }

        // 2. Expanded Live Notification RemoteViews (Material 3 Tonal Card)
        val expandedViews = RemoteViews(packageName, R.layout.notification_live_focus_expanded).apply {
            setTextViewText(R.id.tv_expanded_title, modeTitle)
            setTextViewText(R.id.tv_expanded_subtitle, ruleSummary)

            setTextViewText(
                R.id.btn_toggle_auto_reply,
                if (state.autoReplyEnabled) "Auto-Reply: ON" else "Auto-Reply: OFF"
            )
            setTextColor(
                R.id.btn_toggle_auto_reply,
                if (state.autoReplyEnabled) Color.parseColor("#D3E3FD") else Color.parseColor("#C4C6D0")
            )
            setInt(
                R.id.btn_toggle_auto_reply,
                "setBackgroundResource",
                if (state.autoReplyEnabled) R.drawable.notification_chip_bg else R.drawable.notification_button_bg
            )
            setOnClickPendingIntent(R.id.btn_toggle_auto_reply, toggleAutoReplyPendingIntent)

            if (graceSeconds != null) {
                setTextViewText(R.id.tv_expanded_status, "Exiting in ${graceSeconds}s")
                setTextColor(R.id.tv_expanded_status, Color.parseColor("#FFDCC1"))
                setInt(R.id.tv_expanded_status, "setBackgroundResource", R.drawable.notification_chip_grace_bg)
            } else {
                setTextViewText(R.id.tv_expanded_status, "Active")
                setTextColor(R.id.tv_expanded_status, Color.parseColor("#D3E3FD"))
                setInt(R.id.tv_expanded_status, "setBackgroundResource", R.drawable.notification_chip_bg)
            }

            if (isTimed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Use native OS Chronometer countdown so SystemUI ticks every second without freezing
                    setViewVisibility(R.id.tv_expanded_duration, View.GONE)
                    setViewVisibility(R.id.chrono_expanded, View.VISIBLE)
                    setChronometerCountDown(R.id.chrono_expanded, true)
                    setChronometer(R.id.chrono_expanded, countdownChronometerBase, "%s left", true)
                } else {
                    setViewVisibility(R.id.chrono_expanded, View.GONE)
                    setViewVisibility(R.id.tv_expanded_duration, View.VISIBLE)
                    setTextViewText(R.id.tv_expanded_duration, clampedCountdownText)
                }
            } else {
                setViewVisibility(R.id.tv_expanded_duration, View.GONE)
                setViewVisibility(R.id.chrono_expanded, View.VISIBLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setChronometerCountDown(R.id.chrono_expanded, false)
                }
                setChronometer(R.id.chrono_expanded, elapsedChronometerBase, "Elapsed %s", true)
            }

            setProgressBar(R.id.pb_expanded_progress, 100, progressPercent, false)
            setTextViewText(R.id.tv_live_calls_count, liveCallsDeclined.toString())
            setTextViewText(R.id.tv_live_alerts_count, liveAlertsSilenced.toString())

            setOnClickPendingIntent(R.id.btn_cycle_mode, cyclePendingIntent)
            setOnClickPendingIntent(R.id.btn_stop_focus, stopPendingIntent)
            setOnClickPendingIntent(R.id.btn_sub_15, sub15PendingIntent)
            setOnClickPendingIntent(R.id.btn_add_15, add15PendingIntent)
            setOnClickPendingIntent(R.id.btn_add_30, add30PendingIntent)
        }

        val builder = NotificationCompat.Builder(this, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("$modeTitle • Live Shield")
            .setContentText("$liveCallsDeclined Calls Declined • $liveAlertsSilenced Alerts Silenced")
            .setContentIntent(contentIntent)
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setCustomHeadsUpContentView(collapsedViews)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (isTimed && expiryTime != null) {
            builder.setWhen(expiryTime)
            builder.setUsesChronometer(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true)
            }
            if (remainingMs > 0L) {
                builder.setTimeoutAfter(remainingMs)
            }
        } else {
            builder.setWhen(state.sessionStartTimeMillis ?: now)
            builder.setUsesChronometer(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(false)
            }
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
                "Sentinal Live Focus Activity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pinned real-time Live Notification with ticking chronometer, progress bar, and live blocked call/alert telemetry"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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

    private fun registerSystemTickReceiver() {
        if (systemTickReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(systemTickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(systemTickReceiver, filter)
            }
            systemTickReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register systemTickReceiver: ${e.message}")
        }
    }

    private fun unregisterSystemTickReceiver() {
        if (!systemTickReceiverRegistered) return
        try {
            unregisterReceiver(systemTickReceiver)
        } catch (_: Exception) {
        } finally {
            systemTickReceiverRegistered = false
        }
    }

    private fun stopForegroundService() {
        tickerJob?.cancel()
        tickerJob = null
        observerJob?.cancel()
        observerJob = null
        unregisterSystemTickReceiver()
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
        tickerJob?.cancel()
        tickerJob = null
        observerJob?.cancel()
        observerJob = null
        unregisterSystemTickReceiver()
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
        const val LIVE_CHANNEL_ID = "zenshield_live_activity_channel_v2"
        const val NOTIFICATION_ID = 9001

        const val ACTION_CYCLE_MODE = "com.example.zenshield.ACTION_CYCLE_MODE"
        const val ACTION_ADD_15_MIN = "com.example.zenshield.ACTION_ADD_15_MIN"
        const val ACTION_ADD_30_MIN = "com.example.zenshield.ACTION_ADD_30_MIN"
        const val ACTION_SUBTRACT_15_MIN = "com.example.zenshield.ACTION_SUBTRACT_15_MIN"
        const val ACTION_TOGGLE_AUTO_REPLY = "com.example.zenshield.ACTION_TOGGLE_AUTO_REPLY"
        const val ACTION_STOP_FOCUS = "com.example.zenshield.ACTION_STOP_FOCUS"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.zenshield.ACTION_UPDATE_NOTIFICATION"
        const val ACTION_CONFIRM_GAME_PROMPT = "com.example.zenshield.ACTION_CONFIRM_GAME_PROMPT"
        const val ACTION_DECLINE_GAME_PROMPT = "com.example.zenshield.ACTION_DECLINE_GAME_PROMPT"

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
