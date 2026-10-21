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
    val perProfileAutoReplyMessages: Map<FocusProfile, String> = emptyMap(),
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

    private val _activeProfileFlow = MutableStateFlow(_state.value.activeProfile)
    val activeProfileFlow: StateFlow<FocusProfile> = _activeProfileFlow.asStateFlow()

    init {
        scope.launch {
            _state.collect { sessionState ->
                if (_activeProfileFlow.value != sessionState.activeProfile) {
                    _activeProfileFlow.value = sessionState.activeProfile
                }
            }
        }
        // Always reset test and breakthrough caches on startup so users never need to clear data
        resetSessionCaches()

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
        val autoReplyTemplate = prefs.getString(KEY_AUTO_REPLY_TEMPLATE, DEFAULT_AUTO_REPLY_TEMPLATE) ?: DEFAULT_AUTO_REPLY_TEMPLATE
        val perProfileMessages = mutableMapOf<FocusProfile, String>()
        for (p in FocusProfile.values()) {
            val msg = prefs.getString("key_auto_reply_profile_${p.name}", null)
            if (!msg.isNullOrBlank()) {
                perProfileMessages[p] = msg
            }
        }
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

        // If saved session already expired in the past, reset to OFF and clean persistent state
        if (profile != FocusProfile.OFF && expiresAt != null && System.currentTimeMillis() >= expiresAt) {
            prefs.edit()
                .putString(KEY_ACTIVE_PROFILE, FocusProfile.OFF.id)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_SESSION_START)
                .commit()
            return FocusSessionState(
                activeProfile = FocusProfile.OFF,
                expiresAtMillis = null,
                durationOption = DurationOption.UNTIL_TURNED_OFF,
                dimLockScreenEnabled = dimLockScreen,
                allowStarredContactsOnly = profile.defaultAllowStarredOnly,
                blockRepeatedCallers = profile.defaultBlockRepeatedCalls,
                strictGamingLockdownEnabled = strictGaming,
                allowRepeatCallersEnabled = allowRepeat,
                silenceNotifications = profile.defaultSilenceNotifications,
                autoReplyEnabled = autoReply,
                autoReplyMessageTemplate = autoReplyTemplate,
                perProfileAutoReplyMessages = perProfileMessages,
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
                totalShieldedNotifications = shieldedNotifs,
                sessionStartTimeMillis = null
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
            perProfileAutoReplyMessages = perProfileMessages,
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

    fun isAutoReplyEnabled(): Boolean = _state.value.autoReplyEnabled
    fun isAutoReplyEnabled(context: Context): Boolean = _state.value.autoReplyEnabled

    fun setAutoReplyEnabled(enabled: Boolean) {
        val updated = _state.value.copy(autoReplyEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_AUTO_REPLY, enabled).commit()
    }

    fun setAutoReplyEnabled(context: Context, enabled: Boolean) {
        setAutoReplyEnabled(enabled)
    }

    fun getAutoReplyMessage(): String = getAutoReplyMessage(_state.value.activeProfile)
    fun getAutoReplyMessage(context: Context): String = getAutoReplyMessage(_state.value.activeProfile)

    fun getAutoReplyMessage(profile: FocusProfile): String {
        val perProfile = _state.value.perProfileAutoReplyMessages[profile]
        if (!perProfile.isNullOrBlank()) {
            return perProfile
        }
        val template = _state.value.autoReplyMessageTemplate
        return try {
            if (template.contains("%s")) {
                String.format(template, profile.displayName)
            } else if (template.contains("{profile}")) {
                template.replace("{profile}", profile.displayName)
            } else {
                template
            }
        } catch (_: Exception) {
            "🛡️ ZenShield: I currently have ${profile.displayName} Focus active and notifications are silenced. Reply 'URGENT' to break through immediately."
        }
    }

    fun setAutoReplyMessage(message: String) {
        setAutoReplyMessageTemplate(message)
    }

    fun setAutoReplyMessage(context: Context, message: String) {
        setAutoReplyMessageTemplate(message)
    }

    fun setAutoReplyMessageTemplate(template: String) {
        val updated = _state.value.copy(autoReplyMessageTemplate = template)
        _state.value = updated
        prefs.edit().putString(KEY_AUTO_REPLY_TEMPLATE, template).commit()
    }

    fun getPerProfileAutoReplyMessage(profile: FocusProfile): String? {
        return _state.value.perProfileAutoReplyMessages[profile]
    }

    fun setPerProfileAutoReplyMessage(profile: FocusProfile, message: String) {
        val updatedMap = _state.value.perProfileAutoReplyMessages.toMutableMap()
        if (message.isBlank()) {
            updatedMap.remove(profile)
            prefs.edit().remove("key_auto_reply_profile_${profile.name}").apply()
        } else {
            updatedMap[profile] = message
            prefs.edit().putString("key_auto_reply_profile_${profile.name}", message).apply()
        }
        _state.value = _state.value.copy(perProfileAutoReplyMessages = updatedMap)
    }

    fun resetAutoReplyTemplateToDefault() {
        setAutoReplyMessageTemplate(DEFAULT_AUTO_REPLY_TEMPLATE)
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
        _activeProfileFlow.value = FocusProfile.GAMING
        persistState(updated)
        syncInterruptionFilter(FocusProfile.GAMING)
        applyFocusLockScreen(appContext, FocusProfile.GAMING, updated.dimLockScreenEnabled)
        runCatching {
            com.example.services.ZenShieldForegroundService.start(appContext)
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
        _activeProfileFlow.value = targetProfile
        persistState(updated)
        syncInterruptionFilter(targetProfile)
        applyFocusLockScreen(appContext, targetProfile, updated.dimLockScreenEnabled)
        if (targetProfile != FocusProfile.OFF) {
            runCatching {
                com.example.services.ZenShieldForegroundService.start(appContext)
            }
        } else {
            runCatching {
                com.example.services.ZenShieldForegroundService.stop(appContext)
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
        val norm = callerName.trim().lowercase()
        if (norm == "unknown caller" || norm == "unknown" || norm.startsWith("com.")) {
            return false
        }
        val cachePrefs = getVoipCacheSharedPreferences()
        val key = "voip_repeat_$norm"
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

    fun checkCellularRepeatBreakthrough(callerIdentifier: String): Boolean {
        if (callerIdentifier.isBlank()) return false
        val norm = callerIdentifier.trim().lowercase()
        if (norm == "unknown caller" || norm == "unknown") {
            return false
        }
        val prefs = getCallSharedPreferences()
        val normalized = callerIdentifier.replace(Regex("[^0-9+]"), "").ifBlank { norm }
        val key = "last_call_timestamp_$normalized"
        val lastTimestamp = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        val isWithinThreeMinutes = lastTimestamp > 0L && (now - lastTimestamp) <= THREE_MINUTES_MS

        if (isWithinThreeMinutes) {
            prefs.edit().remove(key).apply()
            return true
        }

        // Record 1st call timestamp
        prefs.edit().putLong(key, now).apply()
        return false
    }

    fun shouldSendAutoReply(senderName: String): Boolean {
        if (senderName.isBlank()) return false
        val cachePrefs = getVoipCacheSharedPreferences()
        val key = "autoreply_cooldown_${senderName.trim().lowercase()}"
        val lastTimestamp = cachePrefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        // 15-second cooldown allows rapid back-to-back testing without requiring "Clear Data",
        // while still preventing rapid spam in active chat bursts.
        return (now - lastTimestamp) > FIFTEEN_SECONDS_MS
    }

    fun recordAutoReplySent(senderName: String) {
        if (senderName.isBlank()) return
        val cachePrefs = getVoipCacheSharedPreferences()
        val key = "autoreply_cooldown_${senderName.trim().lowercase()}"
        cachePrefs.edit().putLong(key, System.currentTimeMillis()).apply()
    }

    fun resetSessionCaches() {
        try {
            getCallSharedPreferences().edit().clear().commit()
            getVoipCacheSharedPreferences().edit().clear().commit()
            Log.d(TAG, "Cleared repeat call and auto-reply caches for new focus session")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing session caches: ${e.message}")
        }
    }

    fun setProfile(
        profile: FocusProfile,
        duration: DurationOption = DurationOption.UNTIL_TURNED_OFF
    ) {
        if (profile == FocusProfile.OFF && _state.value.activeProfile == FocusProfile.OFF) {
            runCatching {
                com.example.services.ZenShieldForegroundService.stop(appContext)
            }
            return
        }

        val now = System.currentTimeMillis()
        val expiresAt = if (profile != FocusProfile.OFF) {
            duration.durationMinutes?.let { now + it * 60 * 1000L }
        } else {
            null
        }

        expirationJob?.cancel()
        expirationJob = null

        if (profile != FocusProfile.OFF && expiresAt != null) {
            startExpirationCountdown(expiresAt)
        }

        // Clean repeat caller and auto-reply caches on every profile transition so testing or new sessions never block
        resetSessionCaches()

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
        _activeProfileFlow.value = profile
        persistState(updated)

        // Sync Android Interruption Filter
        syncInterruptionFilter(profile)

        // Sync Focus Lock Screen
        applyFocusLockScreen(appContext, profile, updated.dimLockScreenEnabled)

        // Sync Bulletproof Watchdog & Live Focus Controller Foreground Service
        if (profile != FocusProfile.OFF) {
            runCatching {
                com.example.services.ZenShieldForegroundService.start(appContext)
            }
            runCatching {
                com.example.services.NotificationGuardService.ensureBound(appContext)
            }
        } else {
            runCatching {
                com.example.services.ZenShieldForegroundService.stop(appContext)
            }
        }
    }

    fun adjustRemainingTime(context: Context, deltaMinutes: Long) {
        val current = _state.value
        if (current.activeProfile == FocusProfile.OFF) return

        val now = System.currentTimeMillis()
        val currentExpiry = current.expiresAtMillis
        val newExpiry: Long

        if (currentExpiry == null || currentExpiry <= now) {
            if (deltaMinutes > 0) {
                newExpiry = now + (deltaMinutes * 60_000L)
            } else {
                return
            }
        } else {
            val adjusted = currentExpiry + (deltaMinutes * 60_000L)
            if (adjusted <= now) {
                val remainingMs = currentExpiry - now
                if (remainingMs <= 5 * 60_000L) {
                    setProfile(FocusProfile.OFF)
                    return
                } else {
                    newExpiry = now + (5 * 60_000L)
                }
            } else {
                newExpiry = adjusted
            }
        }

        expirationJob?.cancel()
        startExpirationCountdown(newExpiry)

        val updated = current.copy(
            expiresAtMillis = newExpiry,
            durationOption = DurationOption.UNTIL_TURNED_OFF
        )
        _state.value = updated
        persistState(updated)

        runCatching {
            com.example.services.ZenShieldForegroundService.update(context)
        }
    }

    fun cycleNextActiveProfile(context: Context) {
        val current = _state.value
        val nextProfile = when (current.activeProfile) {
            FocusProfile.DO_NOT_DISTURB -> FocusProfile.GAMING
            FocusProfile.GAMING -> FocusProfile.WORK
            FocusProfile.WORK -> FocusProfile.SLEEP
            FocusProfile.SLEEP -> FocusProfile.DO_NOT_DISTURB
            FocusProfile.OFF -> FocusProfile.DO_NOT_DISTURB
        }

        val updated = current.copy(
            activeProfile = nextProfile,
            allowStarredContactsOnly = nextProfile.defaultAllowStarredOnly,
            blockRepeatedCallers = nextProfile.defaultBlockRepeatedCalls,
            silenceNotifications = nextProfile.defaultSilenceNotifications
        )
        _state.value = updated
        _activeProfileFlow.value = nextProfile
        persistState(updated)
        syncInterruptionFilter(nextProfile)
        applyFocusLockScreen(appContext, nextProfile, updated.dimLockScreenEnabled)

        runCatching {
            com.example.services.ZenShieldForegroundService.update(context)
        }
    }

    fun setDuration(duration: DurationOption) {
        val currentProfile = _state.value.activeProfile
        if (currentProfile != FocusProfile.OFF) {
            setProfile(currentProfile, duration)
        } else {
            val updated = _state.value.copy(durationOption = duration)
            _state.value = updated
            persistState(updated)
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
        val editor = prefs.edit()
            .putString(KEY_ACTIVE_PROFILE, state.activeProfile.id)
            .putString(KEY_DURATION_OPTION, state.durationOption.name)
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

        if (state.expiresAtMillis != null && state.expiresAtMillis > System.currentTimeMillis() && state.activeProfile != FocusProfile.OFF) {
            editor.putLong(KEY_EXPIRES_AT, state.expiresAtMillis)
        } else {
            editor.remove(KEY_EXPIRES_AT)
        }

        if (state.sessionStartTimeMillis != null && state.activeProfile != FocusProfile.OFF) {
            editor.putLong(KEY_SESSION_START, state.sessionStartTimeMillis)
        } else {
            editor.remove(KEY_SESSION_START)
        }

        editor.commit()
    }

    companion object {
        fun isAutoReplyEnabled(context: Context): Boolean {
            return getInstance(context).isAutoReplyEnabled()
        }

        fun setAutoReplyEnabled(context: Context, enabled: Boolean) {
            getInstance(context).setAutoReplyEnabled(enabled)
        }

        fun getAutoReplyMessage(context: Context): String {
            return getInstance(context).getAutoReplyMessage()
        }

        fun setAutoReplyMessage(context: Context, message: String) {
            getInstance(context).setAutoReplyMessage(message)
        }
        private const val TAG = "FocusStateManager"
        private const val PREFS_NAME = "zenshield_focus_prefs"
        const val CALL_PREFS_NAME = "zenshield_call_bouncer_prefs"
        const val VOIP_CACHE_PREFS_NAME = "zenshield_voip_cache_prefs"
        const val FIFTEEN_SECONDS_MS = 15_000L
        const val THREE_MINUTES_MS = 180_000L
        const val THIRTY_MINUTES_MS = 15_000L // 15s cooldown prevents spam while allowing easy testing without Clear Data
        const val DEFAULT_AUTO_REPLY_TEMPLATE = "🛡️ ZenShield: I currently have %s Focus active and notifications are silenced. Reply 'URGENT' to break through immediately."

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
