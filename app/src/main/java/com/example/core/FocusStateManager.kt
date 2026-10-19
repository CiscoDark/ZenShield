package com.example.core

import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ui.theme.FocusThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FocusSessionState(
    val activeProfile: FocusProfile = FocusProfile.OFF,
    val expiresAtMillis: Long? = null,
    val durationOption: DurationOption = DurationOption.UNTIL_TURNED_OFF,
    val dimLockScreenEnabled: Boolean = true,
    val allowStarredContactsOnly: Boolean = true,
    val blockRepeatedCallers: Boolean = false,
    val strictGamingLockdownEnabled: Boolean = true,
    val allowRepeatCallersEnabled: Boolean = true,
    val silenceNotifications: Boolean = true,
    val autoReplyEnabled: Boolean = true,
    val autoReplyMessageTemplate: String = "🛡️ ZenShield: I currently have %s Focus active and notifications are silenced. Reply 'URGENT' to break through immediately.",
    val autoDeclineVoipInGame: Boolean = true,
    val suppressHeadsUpBanners: Boolean = true,
    val networkLevelCallRejection: Boolean = true,
    val autoDeclineVoipCalls: Boolean = true,
    val smartUrgentBreakthroughEnabled: Boolean = true,
    val whitelistedPackages: Set<String> = emptySet(),
    val profileWhitelists: Map<FocusProfile, Set<String>> = emptyMap(),
    val autoGameDetectionEnabled: Boolean = true,
    val isAutoTriggeredByGame: Boolean = false,
    val previousProfileBeforeGame: FocusProfile = FocusProfile.OFF,
    val customGamePackages: Set<String> = emptySet(),
    val themePreferences: FocusThemePreferences = FocusThemePreferences(),
    val totalShieldedCalls: Int = 0,
    val totalShieldedNotifications: Int = 0,
    val sessionStartTimeMillis: Long? = null
)

class FocusStateManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var expirationJob: Job? = null

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<FocusSessionState> = _state.asStateFlow()

    init {
        // Resume timer if active profile has an expiration timestamp
        val current = _state.value
        if (current.activeProfile != FocusProfile.OFF && current.expiresAtMillis != null) {
            startExpirationCountdown(current.expiresAtMillis)
        }
    }

    private fun loadInitialState(): FocusSessionState {
        val profileId = prefs.getString(KEY_ACTIVE_PROFILE, FocusProfile.OFF.id)
        val profile = FocusProfile.fromId(profileId)
        val expiresAt = if (prefs.contains(KEY_EXPIRES_AT)) prefs.getLong(KEY_EXPIRES_AT, 0L).takeIf { it > 0 } else null
        val durationName = prefs.getString(KEY_DURATION_OPTION, DurationOption.UNTIL_TURNED_OFF.name)
        val durationOption = runCatching { DurationOption.valueOf(durationName ?: DurationOption.UNTIL_TURNED_OFF.name) }.getOrDefault(DurationOption.UNTIL_TURNED_OFF)

        val dimLockScreen = prefs.getBoolean(KEY_DIM_LOCK_SCREEN, true)
        val allowStarredOnly = prefs.getBoolean(KEY_ALLOW_STARRED_ONLY, profile.defaultAllowStarredOnly)
        val blockRepeated = prefs.getBoolean(KEY_BLOCK_REPEATED, profile.defaultBlockRepeatedCalls)
        val strictGaming = prefs.getBoolean(KEY_STRICT_GAMING, true)
        val allowRepeat = prefs.getBoolean(KEY_ALLOW_REPEAT_CALLERS, true)
        val silenceNotifs = prefs.getBoolean(KEY_SILENCE_NOTIFS, profile.defaultSilenceNotifications)
        val autoReply = prefs.getBoolean(KEY_AUTO_REPLY, true)
        val autoReplyTemplate = prefs.getString(KEY_AUTO_REPLY_TEMPLATE, "🛡️ ZenShield: I currently have %s Focus active and notifications are silenced. Reply 'URGENT' to break through immediately.") ?: "🛡️ ZenShield: I currently have %s Focus active and notifications are silenced. Reply 'URGENT' to break through immediately."
        val whitelisted = prefs.getStringSet(KEY_WHITELISTED_PKGS, emptySet()) ?: emptySet()

        val autoDeclineVoipInGame = prefs.getBoolean(KEY_AUTO_DECLINE_VOIP_GAME, true)
        val suppressHeadsUp = prefs.getBoolean(KEY_SUPPRESS_HEADS_UP, true)
        val networkRejection = prefs.getBoolean(KEY_NETWORK_REJECTION, true)
        val autoDeclineVoip = prefs.getBoolean(KEY_AUTO_DECLINE_VOIP, true)
        val smartUrgent = prefs.getBoolean(KEY_SMART_URGENT, true)

        val followSystem = prefs.getBoolean(KEY_THEME_FOLLOW_SYSTEM, true)
        val profileTint = prefs.getBoolean(KEY_THEME_PROFILE_TINT, true)
        val amoledBlack = prefs.getBoolean(KEY_THEME_AMOLED_BLACK, false)

        val autoGameDetection = prefs.getBoolean(KEY_AUTO_GAME_DETECTION, true)
        val isAutoGame = prefs.getBoolean(KEY_IS_AUTO_TRIGGERED_GAME, false)
        val prevProfileId = prefs.getString(KEY_PREVIOUS_PROFILE_BEFORE_GAME, FocusProfile.OFF.id)
        val prevProfile = FocusProfile.fromId(prevProfileId)
        val customGames = prefs.getStringSet(KEY_CUSTOM_GAME_PACKAGES, emptySet()) ?: emptySet()

        val shieldedCalls = prefs.getInt(KEY_SHIELDED_CALLS, 0)
        val shieldedNotifs = prefs.getInt(KEY_SHIELDED_NOTIFS, 0)
        val startTime = if (prefs.contains(KEY_SESSION_START)) prefs.getLong(KEY_SESSION_START, 0L).takeIf { it > 0 } else null

        // If saved session already expired in the past, reset to OFF
        if (profile != FocusProfile.OFF && expiresAt != null && System.currentTimeMillis() >= expiresAt) {
            return FocusSessionState(
                activeProfile = FocusProfile.OFF,
                dimLockScreenEnabled = dimLockScreen,
                strictGamingLockdownEnabled = strictGaming,
                allowRepeatCallersEnabled = allowRepeat,
                autoReplyEnabled = autoReply,
                autoReplyMessageTemplate = autoReplyTemplate,
                autoDeclineVoipInGame = autoDeclineVoipInGame,
                suppressHeadsUpBanners = suppressHeadsUp,
                networkLevelCallRejection = networkRejection,
                autoDeclineVoipCalls = autoDeclineVoip,
                smartUrgentBreakthroughEnabled = smartUrgent,
                whitelistedPackages = whitelisted,
                autoGameDetectionEnabled = autoGameDetection,
                isAutoTriggeredByGame = false,
                previousProfileBeforeGame = FocusProfile.OFF,
                customGamePackages = customGames,
                themePreferences = FocusThemePreferences(followSystem, profileTint, amoledBlack),
                totalShieldedCalls = shieldedCalls,
                totalShieldedNotifications = shieldedNotifs
            )
        }

        return FocusSessionState(
            activeProfile = profile,
            expiresAtMillis = expiresAt,
            durationOption = durationOption,
            dimLockScreenEnabled = dimLockScreen,
            allowStarredContactsOnly = allowStarredOnly,
            blockRepeatedCallers = blockRepeated,
            strictGamingLockdownEnabled = strictGaming,
            allowRepeatCallersEnabled = allowRepeat,
            silenceNotifications = silenceNotifs,
            autoReplyEnabled = autoReply,
            autoReplyMessageTemplate = autoReplyTemplate,
            autoDeclineVoipInGame = autoDeclineVoipInGame,
            suppressHeadsUpBanners = suppressHeadsUp,
            networkLevelCallRejection = networkRejection,
            autoDeclineVoipCalls = autoDeclineVoip,
            smartUrgentBreakthroughEnabled = smartUrgent,
            whitelistedPackages = whitelisted,
            autoGameDetectionEnabled = autoGameDetection,
            isAutoTriggeredByGame = isAutoGame,
            previousProfileBeforeGame = prevProfile,
            customGamePackages = customGames,
            themePreferences = FocusThemePreferences(followSystem, profileTint, amoledBlack),
            totalShieldedCalls = shieldedCalls,
            totalShieldedNotifications = shieldedNotifs,
            sessionStartTimeMillis = startTime
        )
    }

    fun getActiveProfile(): FocusProfile = _state.value.activeProfile

    fun isAllowStarredContacts(): Boolean = _state.value.allowStarredContactsOnly

    fun isStrictGamingLockdown(): Boolean = _state.value.strictGamingLockdownEnabled

    fun isAllowRepeatCallers(): Boolean = _state.value.allowRepeatCallersEnabled

    fun isAutoReplyEnabled(): Boolean = _state.value.autoReplyEnabled

    fun setAutoDeclineVoipInGameEnabled(enabled: Boolean) {
        val updated = _state.value.copy(autoDeclineVoipInGame = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_AUTO_DECLINE_VOIP_GAME, enabled).apply()
    }

    fun setSuppressHeadsUpBannersEnabled(enabled: Boolean) {
        val updated = _state.value.copy(suppressHeadsUpBanners = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_SUPPRESS_HEADS_UP, enabled).apply()
    }

    fun setNetworkLevelCallRejectionEnabled(enabled: Boolean) {
        val updated = _state.value.copy(networkLevelCallRejection = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_NETWORK_REJECTION, enabled).apply()
    }

    fun setAutoDeclineVoipCallsEnabled(enabled: Boolean) {
        val updated = _state.value.copy(autoDeclineVoipCalls = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_AUTO_DECLINE_VOIP, enabled).apply()
    }

    fun setSmartUrgentBreakthroughEnabled(enabled: Boolean) {
        val updated = _state.value.copy(smartUrgentBreakthroughEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_SMART_URGENT, enabled).apply()
    }

    fun getAutoReplyMessage(profile: FocusProfile): String {
        return try {
            String.format(_state.value.autoReplyMessageTemplate, profile.displayName)
        } catch (_: Exception) {
            "🛡️ ZenShield: I currently have ${profile.displayName} Focus active and notifications are silenced. Reply 'URGENT' to break through immediately."
        }
    }

    fun setAutoReplyEnabled(enabled: Boolean) {
        val updated = _state.value.copy(autoReplyEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_AUTO_REPLY, enabled).apply()
    }

    fun setAutoReplyMessageTemplate(template: String) {
        val updated = _state.value.copy(autoReplyMessageTemplate = template)
        _state.value = updated
        prefs.edit().putString(KEY_AUTO_REPLY_TEMPLATE, template).apply()
    }

    fun isPackageWhitelisted(packageName: String): Boolean {
        return _state.value.whitelistedPackages.contains(packageName)
    }

    fun isPackageWhitelisted(profile: FocusProfile, packageName: String): Boolean {
        val perProfile = getWhitelistedPackages(profile)
        return perProfile.contains(packageName) || _state.value.whitelistedPackages.contains(packageName)
    }

    fun getWhitelistedPackages(profile: FocusProfile): Set<String> {
        val key = "whitelisted_pkgs_${profile.id.lowercase()}"
        return prefs.getStringSet(key, null) ?: _state.value.whitelistedPackages
    }

    fun setWhitelistedPackages(profile: FocusProfile, packages: Set<String>) {
        val key = "whitelisted_pkgs_${profile.id.lowercase()}"
        prefs.edit().putStringSet(key, packages).apply()
        val updated = _state.value.copy(
            profileWhitelists = _state.value.profileWhitelists + (profile to packages)
        )
        _state.value = updated
    }

    fun toggleWhitelistedPackage(profile: FocusProfile, packageName: String) {
        val current = getWhitelistedPackages(profile)
        val updated = if (current.contains(packageName)) {
            current - packageName
        } else {
            current + packageName
        }
        setWhitelistedPackages(profile, updated)
    }

    fun addWhitelistedPackage(packageName: String) {
        val updatedSet = _state.value.whitelistedPackages + packageName
        val updated = _state.value.copy(whitelistedPackages = updatedSet)
        _state.value = updated
        prefs.edit().putStringSet(KEY_WHITELISTED_PKGS, updatedSet).apply()
    }

    fun removeWhitelistedPackage(packageName: String) {
        val updatedSet = _state.value.whitelistedPackages - packageName
        val updated = _state.value.copy(whitelistedPackages = updatedSet)
        _state.value = updated
        prefs.edit().putStringSet(KEY_WHITELISTED_PKGS, updatedSet).apply()
    }

    fun setStrictGamingLockdownEnabled(enabled: Boolean) {
        val updated = _state.value.copy(strictGamingLockdownEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_STRICT_GAMING, enabled).apply()
    }

    fun setAllowRepeatCallersEnabled(enabled: Boolean) {
        val updated = _state.value.copy(allowRepeatCallersEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_ALLOW_REPEAT_CALLERS, enabled).apply()
    }

    fun isAutoGameDetectionEnabled(): Boolean = _state.value.autoGameDetectionEnabled

    fun setAutoGameDetectionEnabled(enabled: Boolean) {
        val updated = _state.value.copy(autoGameDetectionEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_AUTO_GAME_DETECTION, enabled).apply()
        if (enabled) {
            runCatching { com.example.services.GameDetectorService.start(appContext) }
        } else {
            runCatching { com.example.services.GameDetectorService.stop(appContext) }
        }
    }

    fun getCustomGamePackages(): Set<String> = _state.value.customGamePackages

    fun addCustomGamePackage(packageName: String) {
        val updatedSet = _state.value.customGamePackages + packageName
        val updated = _state.value.copy(customGamePackages = updatedSet)
        _state.value = updated
        prefs.edit().putStringSet(KEY_CUSTOM_GAME_PACKAGES, updatedSet).apply()
    }

    fun removeCustomGamePackage(packageName: String) {
        val updatedSet = _state.value.customGamePackages - packageName
        val updated = _state.value.copy(customGamePackages = updatedSet)
        _state.value = updated
        prefs.edit().putStringSet(KEY_CUSTOM_GAME_PACKAGES, updatedSet).apply()
    }

    fun handleGameLaunched(packageName: String) {
        val current = _state.value
        if (!current.autoGameDetectionEnabled) return
        if (current.activeProfile == FocusProfile.GAMING) return

        val prev = current.activeProfile
        Log.i(TAG, "Game launched ($packageName). Auto-activating GAMING focus profile. Previous profile: ${prev.displayName}")
        val updated = current.copy(
            activeProfile = FocusProfile.GAMING,
            previousProfileBeforeGame = prev,
            isAutoTriggeredByGame = true,
            durationOption = DurationOption.UNTIL_TURNED_OFF,
            expiresAtMillis = null
        )
        _state.value = updated
        persistState(updated)
        syncInterruptionFilter(FocusProfile.GAMING)
        applyFocusLockScreen(appContext, FocusProfile.GAMING, updated.dimLockScreenEnabled)
        runCatching {
            com.example.services.ZenShieldWatchdogService.start(appContext)
        }
    }

    fun handleGameExited() {
        val current = _state.value
        if (!current.isAutoTriggeredByGame || current.activeProfile != FocusProfile.GAMING) return

        val targetProfile = current.previousProfileBeforeGame
        Log.i(TAG, "Game exited. Reverting focus profile from GAMING to ${targetProfile.displayName}")
        val updated = current.copy(
            activeProfile = targetProfile,
            isAutoTriggeredByGame = false,
            durationOption = DurationOption.UNTIL_TURNED_OFF,
            expiresAtMillis = null
        )
        _state.value = updated
        persistState(updated)
        syncInterruptionFilter(targetProfile)
        applyFocusLockScreen(appContext, targetProfile, updated.dimLockScreenEnabled)
        if (targetProfile != FocusProfile.OFF) {
            runCatching {
                com.example.services.ZenShieldWatchdogService.start(appContext)
            }
        } else {
            runCatching {
                com.example.services.ZenShieldWatchdogService.stop(appContext)
            }
        }
    }

    fun getCallSharedPreferences(): SharedPreferences {
        return appContext.getSharedPreferences(CALL_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getVoipCacheSharedPreferences(): SharedPreferences {
        return appContext.getSharedPreferences(VOIP_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun checkVoipRepeatBreakthrough(callerName: String): Boolean {
        if (callerName.isBlank()) return false
        val cachePrefs = getVoipCacheSharedPreferences()
        val key = "voip_repeat_${callerName.trim().lowercase()}"
        val lastTimestamp = cachePrefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        val isWithinThreeMinutes = lastTimestamp > 0L && (now - lastTimestamp) <= THREE_MINUTES_MS

        if (isWithinThreeMinutes) {
            cachePrefs.edit().remove(key).apply()
            return true
        }

        // Record 1st call timestamp
        cachePrefs.edit().putLong(key, now).apply()
        return false
    }

    fun shouldSendAutoReply(senderName: String): Boolean {
        if (senderName.isBlank()) return false
        val cachePrefs = getVoipCacheSharedPreferences()
        val key = "autoreply_cooldown_${senderName.trim().lowercase()}"
        val lastTimestamp = cachePrefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        // 30-minute cooldown (1,800,000 ms)
        return (now - lastTimestamp) > THIRTY_MINUTES_MS
    }

    fun recordAutoReplySent(senderName: String) {
        if (senderName.isBlank()) return
        val cachePrefs = getVoipCacheSharedPreferences()
        val key = "autoreply_cooldown_${senderName.trim().lowercase()}"
        cachePrefs.edit().putLong(key, System.currentTimeMillis()).apply()
    }

    fun setProfile(
        profile: FocusProfile,
        duration: DurationOption = DurationOption.UNTIL_TURNED_OFF
    ) {
        val now = System.currentTimeMillis()
        val expiresAt = duration.durationMinutes?.let { now + it * 60 * 1000L }

        expirationJob?.cancel()

        if (profile != FocusProfile.OFF && expiresAt != null) {
            startExpirationCountdown(expiresAt)
        }

        val updated = _state.value.copy(
            activeProfile = profile,
            expiresAtMillis = expiresAt,
            durationOption = duration,
            sessionStartTimeMillis = if (profile != FocusProfile.OFF) now else null,
            isAutoTriggeredByGame = false,
            allowStarredContactsOnly = profile.defaultAllowStarredOnly,
            blockRepeatedCallers = profile.defaultBlockRepeatedCalls,
            silenceNotifications = profile.defaultSilenceNotifications
        )

        _state.value = updated
        persistState(updated)

        // Sync Android Interruption Filter
        syncInterruptionFilter(profile)

        // Sync Focus Lock Screen
        applyFocusLockScreen(appContext, profile, updated.dimLockScreenEnabled)

        // Sync Bulletproof Watchdog Foreground Service
        if (profile != FocusProfile.OFF) {
            runCatching {
                com.example.services.ZenShieldWatchdogService.start(appContext)
            }
        } else {
            runCatching {
                com.example.services.ZenShieldWatchdogService.stop(appContext)
            }
        }
    }

    fun setDuration(duration: DurationOption) {
        val currentProfile = _state.value.activeProfile
        if (currentProfile != FocusProfile.OFF) {
            setProfile(currentProfile, duration)
        }
    }

    fun setDimLockScreenEnabled(enabled: Boolean) {
        val updated = _state.value.copy(dimLockScreenEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_DIM_LOCK_SCREEN, enabled).apply()

        // Re-apply or clear lockscreen immediately based on new setting
        applyFocusLockScreen(appContext, updated.activeProfile, enabled)
    }

    fun updateThemePreferences(preferences: FocusThemePreferences) {
        val updated = _state.value.copy(themePreferences = preferences)
        _state.value = updated
        prefs.edit()
            .putBoolean(KEY_THEME_FOLLOW_SYSTEM, preferences.followSystemWallpaper)
            .putBoolean(KEY_THEME_PROFILE_TINT, preferences.profileTintedAccent)
            .putBoolean(KEY_THEME_AMOLED_BLACK, preferences.amoledPureBlack)
            .apply()
    }

    fun updateProfileSettings(
        allowStarredOnly: Boolean,
        blockRepeatedCalls: Boolean,
        silenceNotifications: Boolean
    ) {
        val updated = _state.value.copy(
            allowStarredContactsOnly = allowStarredOnly,
            blockRepeatedCallers = blockRepeatedCalls,
            silenceNotifications = silenceNotifications
        )
        _state.value = updated
        prefs.edit()
            .putBoolean(KEY_ALLOW_STARRED_ONLY, allowStarredOnly)
            .putBoolean(KEY_BLOCK_REPEATED, blockRepeatedCalls)
            .putBoolean(KEY_SILENCE_NOTIFS, silenceNotifications)
            .apply()
    }

    fun incrementShieldedCall() {
        val count = _state.value.totalShieldedCalls + 1
        _state.value = _state.value.copy(totalShieldedCalls = count)
        prefs.edit().putInt(KEY_SHIELDED_CALLS, count).apply()
    }

    fun incrementShieldedNotification() {
        val count = _state.value.totalShieldedNotifications + 1
        _state.value = _state.value.copy(totalShieldedNotifications = count)
        prefs.edit().putInt(KEY_SHIELDED_NOTIFS, count).apply()
    }

    private fun startExpirationCountdown(targetMillis: Long) {
        expirationJob = scope.launch {
            while (isActive) {
                val remaining = targetMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    setProfile(FocusProfile.OFF)
                    break
                }
                delay(1000L)
            }
        }
    }

    fun isNotificationPolicyAccessGranted(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    private fun syncInterruptionFilter(profile: FocusProfile) {
        if (!isNotificationPolicyAccessGranted()) {
            Log.w(TAG, "Notification policy access not granted; skipping interruption filter sync")
            return
        }

        try {
            notificationManager.setInterruptionFilter(profile.interruptionFilter)
            Log.d(TAG, "Successfully synced interruption filter to ${profile.interruptionFilter}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while setting interruption filter", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting interruption filter", e)
        }
    }

    /**
     * Applies a minimalist dark slate lockscreen bitmap when SLEEP or DO_NOT_DISTURB (or active focus profile)
     * is active and dimLockScreen is enabled. Clears FLAG_LOCK when returning to OFF.
     */
    fun applyFocusLockScreen(context: Context, profile: FocusProfile, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)

                if (profile == FocusProfile.OFF || !enabled) {
                    // Clear the lock screen override to restore default user wallpaper
                    try {
                        wallpaperManager.clear(WallpaperManager.FLAG_LOCK)
                        Log.d(TAG, "Cleared lock screen wallpaper override")
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to clear lock screen wallpaper: ${e.message}")
                    }
                    return@launch
                }

                // Generate minimalist slate lockscreen bitmap
                val displayMetrics = context.resources.displayMetrics
                val width = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
                val height = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 2400

                val bitmap = generateLockscreenBitmap(width, height, profile)
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                Log.d(TAG, "Applied minimalist focus lockscreen for ${profile.displayName}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied for setting wallpaper: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply focus lockscreen bitmap", e)
            }
        }
    }

    private fun generateLockscreenBitmap(width: Int, height: Int, profile: FocusProfile): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Dark Slate Slate Background (#0B0F17)
        canvas.drawColor(android.graphics.Color.parseColor("#0B0F17"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 2. Subtle radial glow behind the emblem
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#152238")
            style = Paint.Style.FILL
        }
        val centerX = width / 2f
        val emblemY = height * 0.38f
        canvas.drawCircle(centerX, emblemY, width * 0.28f, glowPaint)

        // 3. Geometric Minimalist Shield Outline
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#38EF7D")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val shieldPath = Path().apply {
            val sWidth = width * 0.16f
            val sHeight = width * 0.22f
            moveTo(centerX, emblemY - sHeight * 0.5f)
            lineTo(centerX + sWidth, emblemY - sHeight * 0.35f)
            quadTo(centerX + sWidth, emblemY + sHeight * 0.2f, centerX, emblemY + sHeight * 0.6f)
            quadTo(centerX - sWidth, emblemY + sHeight * 0.2f, centerX - sWidth, emblemY - sHeight * 0.35f)
            close()
        }
        canvas.drawPath(shieldPath, shieldPaint)

        // 4. Crescent focus arc inside
        val crescentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, emblemY, width * 0.045f, crescentPaint)

        // 5. Typography: "ZenShield • [Profile] Active"
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#90A4AE")
            textSize = width * 0.038f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText("ZENSHIELD FOCUS", centerX, height * 0.52f, brandPaint)

        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = width * 0.054f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${profile.displayName} Active", centerX, height * 0.565f, statusPaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#78909C")
            textSize = width * 0.034f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Distractions Shielded • Sanctuary Mode", centerX, height * 0.605f, subtitlePaint)

        return bitmap
    }

    private fun persistState(state: FocusSessionState) {
        prefs.edit()
            .putString(KEY_ACTIVE_PROFILE, state.activeProfile.id)
            .putString(KEY_DURATION_OPTION, state.durationOption.name)
            .apply {
                if (state.expiresAtMillis != null) {
                    putLong(KEY_EXPIRES_AT, state.expiresAtMillis)
                } else {
                    remove(KEY_EXPIRES_AT)
                }
                if (state.sessionStartTimeMillis != null) {
                    putLong(KEY_SESSION_START, state.sessionStartTimeMillis)
                } else {
                    remove(KEY_SESSION_START)
                }
            }
            .putBoolean(KEY_ALLOW_STARRED_ONLY, state.allowStarredContactsOnly)
            .putBoolean(KEY_BLOCK_REPEATED, state.blockRepeatedCallers)
            .putBoolean(KEY_STRICT_GAMING, state.strictGamingLockdownEnabled)
            .putBoolean(KEY_ALLOW_REPEAT_CALLERS, state.allowRepeatCallersEnabled)
            .putBoolean(KEY_SILENCE_NOTIFS, state.silenceNotifications)
            .putBoolean(KEY_DIM_LOCK_SCREEN, state.dimLockScreenEnabled)
            .putBoolean(KEY_AUTO_REPLY, state.autoReplyEnabled)
            .putString(KEY_AUTO_REPLY_TEMPLATE, state.autoReplyMessageTemplate)
            .putStringSet(KEY_WHITELISTED_PKGS, state.whitelistedPackages)
            .putBoolean(KEY_AUTO_DECLINE_VOIP_GAME, state.autoDeclineVoipInGame)
            .putBoolean(KEY_SUPPRESS_HEADS_UP, state.suppressHeadsUpBanners)
            .putBoolean(KEY_NETWORK_REJECTION, state.networkLevelCallRejection)
            .putBoolean(KEY_AUTO_DECLINE_VOIP, state.autoDeclineVoipCalls)
            .putBoolean(KEY_SMART_URGENT, state.smartUrgentBreakthroughEnabled)
            .putBoolean(KEY_AUTO_GAME_DETECTION, state.autoGameDetectionEnabled)
            .putBoolean(KEY_IS_AUTO_TRIGGERED_GAME, state.isAutoTriggeredByGame)
            .putString(KEY_PREVIOUS_PROFILE_BEFORE_GAME, state.previousProfileBeforeGame.id)
            .putStringSet(KEY_CUSTOM_GAME_PACKAGES, state.customGamePackages)
            .apply()
    }

    companion object {
        private const val TAG = "FocusStateManager"
        private const val PREFS_NAME = "zenshield_focus_prefs"
        const val CALL_PREFS_NAME = "zenshield_call_bouncer_prefs"
        const val VOIP_CACHE_PREFS_NAME = "zenshield_voip_cache_prefs"
        const val THREE_MINUTES_MS = 180_000L
        const val THIRTY_MINUTES_MS = 1_800_000L

        private const val KEY_ACTIVE_PROFILE = "key_active_profile"
        private const val KEY_EXPIRES_AT = "key_expires_at"
        private const val KEY_DURATION_OPTION = "key_duration_option"
        private const val KEY_SESSION_START = "key_session_start"
        private const val KEY_DIM_LOCK_SCREEN = "key_dim_lock_screen"
        private const val KEY_ALLOW_STARRED_ONLY = "key_allow_starred_only"
        private const val KEY_BLOCK_REPEATED = "key_block_repeated"
        private const val KEY_STRICT_GAMING = "key_strict_gaming"
        private const val KEY_ALLOW_REPEAT_CALLERS = "key_allow_repeat_callers"
        private const val KEY_SILENCE_NOTIFS = "key_silence_notifs"
        private const val KEY_AUTO_REPLY = "key_auto_reply"
        private const val KEY_AUTO_REPLY_TEMPLATE = "key_auto_reply_template"
        private const val KEY_WHITELISTED_PKGS = "key_whitelisted_pkgs"
        private const val KEY_AUTO_DECLINE_VOIP_GAME = "key_auto_decline_voip_game"
        private const val KEY_SUPPRESS_HEADS_UP = "key_suppress_heads_up"
        private const val KEY_NETWORK_REJECTION = "key_network_rejection"
        private const val KEY_AUTO_DECLINE_VOIP = "key_auto_decline_voip"
        private const val KEY_SMART_URGENT = "key_smart_urgent"
        private const val KEY_THEME_FOLLOW_SYSTEM = "key_theme_follow_system"
        private const val KEY_THEME_PROFILE_TINT = "key_theme_profile_tint"
        private const val KEY_THEME_AMOLED_BLACK = "key_theme_amoled_black"
        private const val KEY_SHIELDED_CALLS = "key_shielded_calls"
        private const val KEY_SHIELDED_NOTIFS = "key_shielded_notifs"
        private const val KEY_AUTO_GAME_DETECTION = "key_auto_game_detection"
        private const val KEY_IS_AUTO_TRIGGERED_GAME = "key_is_auto_game"
        private const val KEY_PREVIOUS_PROFILE_BEFORE_GAME = "key_prev_profile_game"
        private const val KEY_CUSTOM_GAME_PACKAGES = "key_custom_game_packages"

        @Volatile
        private var INSTANCE: FocusStateManager? = null

        fun getInstance(context: Context): FocusStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FocusStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
