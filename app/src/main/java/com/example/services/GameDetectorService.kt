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
    private var nonGameDebounceCounter = 0

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
                if (!stateManager.isAutoGameDetectionEnabled()) {
                    Log.d(TAG, "Auto game detection disabled in settings. Pausing loop.")
                    delay(POLL_INTERVAL_MS * 2)
                    continue
                }

                if (!hasUsageStatsPermission(applicationContext)) {
                    // Cannot poll without permission; sleep and wait for grant
                    delay(POLL_INTERVAL_MS * 3)
                    continue
                }

                try {
                    val foregroundPackage = getForegroundPackage(applicationContext)

                    if (!foregroundPackage.isNullOrBlank() && !isIgnoredPackage(foregroundPackage)) {
                        val isCurrentGame = isGamePackage(foregroundPackage, stateManager)

                        if (isCurrentGame) {
                            nonGameDebounceCounter = 0
                            if (stateManager.state.value.activeProfile != FocusProfile.GAMING) {
                                Log.i(TAG, "Game detected in foreground: $foregroundPackage. Triggering Gaming Profile.")
                                stateManager.handleGameLaunched(foregroundPackage)
                            }
                        } else {
                            // Non-game app in foreground
                            if (stateManager.state.value.isAutoTriggeredByGame &&
                                stateManager.state.value.activeProfile == FocusProfile.GAMING
                            ) {
                                nonGameDebounceCounter++
                                // 2 consecutive intervals (~3.5 seconds) outside game before reverting
                                if (nonGameDebounceCounter >= DEBOUNCE_THRESHOLD) {
                                    Log.i(TAG, "Non-game detected for $nonGameDebounceCounter ticks ($foregroundPackage). Reverting profile.")
                                    stateManager.handleGameExited()
                                    nonGameDebounceCounter = 0
                                }
                            } else {
                                nonGameDebounceCounter = 0
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during game detection check: ${e.message}")
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun isIgnoredPackage(pkg: String): Boolean {
        return pkg == packageName ||
                pkg == "android" ||
                pkg == "com.android.systemui" ||
                pkg.contains("launcher") ||
                pkg.contains("inputmethod") ||
                pkg.contains("keyboard")
    }

    private fun isGamePackage(pkg: String, stateManager: FocusStateManager): Boolean {
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
                lastForegroundPackage = event.packageName
            }
        }

        return lastForegroundPackage
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
        private const val POLL_INTERVAL_MS = 1800L
        private const val LOOKBACK_WINDOW_MS = 10_000L
        private const val DEBOUNCE_THRESHOLD = 2
        const val ACTION_STOP_DETECTION = "com.example.zenshield.ACTION_STOP_GAME_DETECTION"

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
