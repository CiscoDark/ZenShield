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
    val silenceNotifications: Boolean = true,
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
        val silenceNotifs = prefs.getBoolean(KEY_SILENCE_NOTIFS, profile.defaultSilenceNotifications)

        val followSystem = prefs.getBoolean(KEY_THEME_FOLLOW_SYSTEM, true)
        val profileTint = prefs.getBoolean(KEY_THEME_PROFILE_TINT, true)
        val amoledBlack = prefs.getBoolean(KEY_THEME_AMOLED_BLACK, false)

        val shieldedCalls = prefs.getInt(KEY_SHIELDED_CALLS, 0)
        val shieldedNotifs = prefs.getInt(KEY_SHIELDED_NOTIFS, 0)
        val startTime = if (prefs.contains(KEY_SESSION_START)) prefs.getLong(KEY_SESSION_START, 0L).takeIf { it > 0 } else null

        // If saved session already expired in the past, reset to OFF
        if (profile != FocusProfile.OFF && expiresAt != null && System.currentTimeMillis() >= expiresAt) {
            return FocusSessionState(
                activeProfile = FocusProfile.OFF,
                dimLockScreenEnabled = dimLockScreen,
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
            silenceNotifications = silenceNotifs,
            themePreferences = FocusThemePreferences(followSystem, profileTint, amoledBlack),
            totalShieldedCalls = shieldedCalls,
            totalShieldedNotifications = shieldedNotifs,
            sessionStartTimeMillis = startTime
        )
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
            .putBoolean(KEY_SILENCE_NOTIFS, state.silenceNotifications)
            .putBoolean(KEY_DIM_LOCK_SCREEN, state.dimLockScreenEnabled)
            .apply()
    }

    companion object {
        private const val TAG = "FocusStateManager"
        private const val PREFS_NAME = "zenshield_focus_prefs"
        private const val KEY_ACTIVE_PROFILE = "key_active_profile"
        private const val KEY_EXPIRES_AT = "key_expires_at"
        private const val KEY_DURATION_OPTION = "key_duration_option"
        private const val KEY_SESSION_START = "key_session_start"
        private const val KEY_DIM_LOCK_SCREEN = "key_dim_lock_screen"
        private const val KEY_ALLOW_STARRED_ONLY = "key_allow_starred_only"
        private const val KEY_BLOCK_REPEATED = "key_block_repeated"
        private const val KEY_SILENCE_NOTIFS = "key_silence_notifs"
        private const val KEY_THEME_FOLLOW_SYSTEM = "key_theme_follow_system"
        private const val KEY_THEME_PROFILE_TINT = "key_theme_profile_tint"
        private const val KEY_THEME_AMOLED_BLACK = "key_theme_amoled_black"
        private const val KEY_SHIELDED_CALLS = "key_shielded_calls"
        private const val KEY_SHIELDED_NOTIFS = "key_shielded_notifs"

        @Volatile
        private var INSTANCE: FocusStateManager? = null

        fun getInstance(context: Context): FocusStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FocusStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
