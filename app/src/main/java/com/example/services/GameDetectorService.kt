package com.example.services

import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * GameDetectorService
 * Real-time background game launch detector.
 *
 * Utilizes UsageStatsManager (PACKAGE_USAGE_STATS) and PackageManager metadata to detect
 * when the user launches any game (Android category CATEGORY_GAME, FLAG_IS_GAME, or custom game list).
 * Seamlessly transitions ZenShield into GAMING lockdown mode, and automatically restores the previous
 * profile once the game session concludes.
 */
class GameDetectorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var detectionJob: Job? = null
    private val gameClassificationCache = ConcurrentHashMap<String, Boolean>()
    private var lastKnownForegroundPackage: String? = null
    private var hasEnteredGameDuringSession: Boolean = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GameDetectorService initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stateManager = FocusStateManager.getInstance(applicationContext)

        if (intent?.action == ACTION_STOP_DETECTION) {
            Log.d(TAG, "Action STOP_DETECTION received")
            stopDetectionLoop()
            stopSelf()
            return START_NOT_STICKY
        }

        startDetectionLoop(stateManager)
        return START_STICKY
    }

    private fun startDetectionLoop(stateManager: FocusStateManager) {
        detectionJob?.cancel()
        detectionJob = serviceScope.launch {
            Log.i(TAG, "Starting Game Detection background loop")

            while (isActive) {
                val currentProfile = stateManager.state.value.activeProfile
                if (currentProfile != FocusProfile.GAMING) {
                    hasEnteredGameDuringSession = false
                }

                // Run detection when Auto-Game Detection is enabled OR while Gaming Mode is active (for 15s exit grace)
                if (!stateManager.isAutoGameDetectionEnabled() && currentProfile != FocusProfile.GAMING) {
                    Log.d(TAG, "Auto game detection disabled and Gaming profile inactive. Pausing loop.")
                    delay(POLL_INTERVAL_MS * 2)
                    continue
                }

                if (!hasUsageStatsPermission(applicationContext)) {
                    // Cannot poll without permission; sleep and wait for grant
                    delay(POLL_INTERVAL_MS * 3)
                    continue
                }

                try {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val isScreenInteractive = powerManager?.isInteractive ?: true

                    val detectedPackage = getForegroundPackage(applicationContext)
                    if (!detectedPackage.isNullOrBlank()) {
                        lastKnownForegroundPackage = detectedPackage
                    }

                    // A game is actively in foreground if screen is interactive AND top package is classified as game
                    val isGameInForeground = isScreenInteractive &&
                            lastKnownForegroundPackage != null &&
                            isGamePackage(lastKnownForegroundPackage!!, stateManager)

                    if (isGameInForeground) {
                        val pkg = lastKnownForegroundPackage!!
                        hasEnteredGameDuringSession = true
                        stateManager.notifyForegroundGameObserved(pkg)
                        // Game is actively running in foreground: cancel any pending deactivation countdown
                        stateManager.cancelGameExitedDeactivation()

                        val currentState = stateManager.state.value
                        if (currentState.activeProfile != FocusProfile.GAMING &&
                            stateManager.isAutoGameDetectionEnabled()
                        ) {
                            if (currentState.declinedGamePackageForSession == pkg ||
                                currentState.manuallyDismissedGamingWhilstInGame
                            ) {
                                // User tapped 'Not Now' or manually turned off Gaming Mode from the notification bar
                                // for this game session; wait until they close and relaunch the game.
                            } else if (currentState.askBeforeGameActivation) {
                                Log.i(TAG, "Game detected in foreground: $pkg. Showing 5s confirmation prompt.")
                                stateManager.requestGameLaunchConfirmation(pkg)
                            } else {
                                Log.i(TAG, "Game detected in foreground: $pkg. Triggering Gaming Profile immediately.")
                                stateManager.handleGameLaunched(pkg)
                            }
                        }
                    } else {
                        // Game is NOT in foreground: if screen is interactive and user switched to a non-game app/launcher,
                        // reset any per-session 'Not Now' or manual 'Turn Off' suppression so relaunching the game works.
                        if (isScreenInteractive && !detectedPackage.isNullOrBlank()) {
                            stateManager.clearDeclinedGameSessionIfExited(detectedPackage)
                        }
                        val currentState = stateManager.state.value
                        if (!currentState.isPendingGamePreview &&
                            currentState.pendingGameActivationPackage != null &&
                            !detectedPackage.isNullOrBlank() &&
                            detectedPackage != currentState.pendingGameActivationPackage
                        ) {
                            stateManager.cancelPendingGameActivationPrompt()
                        }

                        if (stateManager.state.value.activeProfile == FocusProfile.GAMING) {
                            // If the user just manually enabled Gaming Mode inside Sentinel and is still in Sentinel,
                            // do not start the 15s exit countdown until they launch a game or leave Sentinel.
                            val isStillInsideSentinelApp = isScreenInteractive &&
                                !hasEnteredGameDuringSession &&
                                (lastKnownForegroundPackage == null || lastKnownForegroundPackage == applicationContext.packageName)

                            if (isStillInsideSentinelApp) {
                                stateManager.cancelGameExitedDeactivation()
                            } else if (!stateManager.isGameExitedDeactivationScheduled()) {
                                Log.i(TAG, "Game exited or minimized (current: $lastKnownForegroundPackage, screenInteractive: $lastKnownForegroundPackage). Scheduling 15s auto-deactivation.")
                                stateManager.scheduleGameExitedDeactivation(FocusStateManager.GAME_EXIT_DEACTIVATION_DELAY_MS)
                            }
                        } else {
                            stateManager.cancelGameExitedDeactivation()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during game detection check: ${e.message}")
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun isGamePackage(pkg: String, stateManager: FocusStateManager): Boolean {
        if (pkg == applicationContext.packageName || TRANSIENT_SYSTEM_PACKAGES.contains(pkg)) {
            return false
        }

        // 1. Check custom games set by user
        if (stateManager.getCustomGamePackages().contains(pkg)) {
            return true
        }

        // 2. Check cached classification
        gameClassificationCache[pkg]?.let { return it }

        // 3. Inspect PackageManager ApplicationInfo metadata
        val isGame = try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            val isCategoryGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else {
                false
            }
            @Suppress("DEPRECATION")
            val isFlagGame = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0

            isCategoryGame || isFlagGame
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking application info for $pkg: ${e.message}")
            false
        }

        gameClassificationCache[pkg] = isGame
        return isGame
    }

    private fun getForegroundPackage(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val now = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(now - LOOKBACK_WINDOW_MS, now)
        val event = UsageEvents.Event()
        var lastForegroundPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName
                // Ignore transient system overlays (notification shade, volume HUD, permission dialogs)
                if (!pkg.isNullOrBlank() && !TRANSIENT_SYSTEM_PACKAGES.contains(pkg)) {
                    lastForegroundPackage = pkg
                }
            }
        }

        if (lastForegroundPackage != null) {
            return lastForegroundPackage
        }

        // Fallback for uninterrupted sessions longer than 5 minutes without a new ACTIVITY_RESUMED event
        return try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 3_600_000L,
                now
            )
            stats?.asSequence()
                ?.filter { !it.packageName.isNullOrBlank() && !TRANSIENT_SYSTEM_PACKAGES.contains(it.packageName) }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun stopDetectionLoop() {
        detectionJob?.cancel()
        detectionJob = null
    }

    override fun onDestroy() {
        stopDetectionLoop()
        Log.i(TAG, "GameDetectorService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "GameDetectorService"
        private const val POLL_INTERVAL_MS = 1000L
        private const val LOOKBACK_WINDOW_MS = 300_000L // 5-minute event lookback window
        const val ACTION_STOP_DETECTION = "com.example.zenshield.ACTION_STOP_GAME_DETECTION"

        private val TRANSIENT_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.samsung.android.permissioncontroller",
            "android"
        )

        fun hasUsageStatsPermission(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun start(context: Context) {
            val intent = Intent(context, GameDetectorService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GameDetectorService::class.java).apply {
                action = ACTION_STOP_DETECTION
            }
            context.startService(intent)
        }
    }
}
