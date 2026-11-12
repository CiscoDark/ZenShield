package com.example.core

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.receivers.BootCompletedReceiver
import com.example.ui.theme.ExpressiveHaptics
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
import java.util.UUID

enum class InterceptType(val badgeLabel: String) {
    CALL_DECLINED("CALL BOUNCED"),
    VOIP_CALL_BOUNCED("VOIP DECLINED"),
    ALERT_SILENCED("SILENCED"),
    AUTO_REPLY_SENT("AUTO-REPLY")
}

data class InterceptEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val timeLabel: String,
    val type: InterceptType,
    val sourceLabel: String,
    val detail: String,
    val profileName: String
)

enum class NotificationPriorityLevel(
    val rank: Int,
    val label: String,
    val badgeLabel: String,
    val description: String
) {
    CRITICAL(
        rank = 4,
        label = "Critical",
        badgeLabel = "P1 • CRITICAL",
        description = "OTPs, 2FA security codes, ride/delivery tracking & emergency alerts"
    ),
    HIGH(
        rank = 3,
        label = "High",
        badgeLabel = "P2 • HIGH",
        description = "Direct messages, work chat mentions & calendar reminders"
    ),
    MEDIUM(
        rank = 2,
        label = "Medium",
        badgeLabel = "P3 • MEDIUM",
        description = "Social updates, group activity & general app notifications"
    ),
    LOW(
        rank = 1,
        label = "Low",
        badgeLabel = "P4 • LOW",
        description = "Promotions, shopping deals, newsletters & background digests"
    )
}

enum class GlobalFocusRuleAction(
    val label: String,
    val shortLabel: String,
    val badgeText: String,
    val description: String
) {
    ALLOW_ALL(
        label = "Allow Alert",
        shortLabel = "Allow",
        badgeText = "ALLOWED",
        description = "Breaks through with normal banner & sound"
    ),
    MUTE_SILENT(
        label = "Mute Audio",
        shortLabel = "Mute",
        badgeText = "MUTED",
        description = "Silences sound & vibration, suppresses heads-up popup"
    ),
    HIDE_COMPLETELY(
        label = "Hide Alert",
        shortLabel = "Hide",
        badgeText = "HIDDEN",
        description = "Completely hides & dismisses alert from status bar"
    )
}

enum class NotificationCategory(
    val title: String,
    val shortTitle: String,
    val subtitle: String,
    val defaultPriority: NotificationPriorityLevel
) {
    SECURITY_AND_URGENT(
        title = "Security, OTP & Urgent",
        shortTitle = "Security & OTP",
        subtitle = "2FA codes, banking, rides & URGENT tags",
        defaultPriority = NotificationPriorityLevel.CRITICAL
    ),
    DIRECT_MESSAGES(
        title = "Direct Messages & Chats",
        shortTitle = "Direct Messages",
        subtitle = "WhatsApp, Telegram, Signal, SMS & DMs",
        defaultPriority = NotificationPriorityLevel.HIGH
    ),
    WORK_AND_CALENDAR(
        title = "Work, Tasks & Calendar",
        shortTitle = "Work & Calendar",
        subtitle = "Slack, Teams, Email, Jira & meetings",
        defaultPriority = NotificationPriorityLevel.HIGH
    ),
    SOCIAL_AND_COMMUNITY(
        title = "Social & Community",
        shortTitle = "Social",
        subtitle = "Instagram, X, Reddit, YouTube & groups",
        defaultPriority = NotificationPriorityLevel.MEDIUM
    ),
    NEWS_AND_MEDIA(
        title = "News, Media & Digests",
        shortTitle = "News & Media",
        subtitle = "Breaking news, podcasts & status updates",
        defaultPriority = NotificationPriorityLevel.LOW
    ),
    PROMOTIONS_AND_DEALS(
        title = "Promotions & Marketing",
        shortTitle = "Promotions",
        subtitle = "Shopping sales, coupons & store promos",
        defaultPriority = NotificationPriorityLevel.LOW
    )
}

data class CategorizedNotificationEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val timeLabel: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val previewText: String,
    val category: NotificationCategory,
    val priorityLevel: NotificationPriorityLevel,
    val appliedAction: GlobalFocusRuleAction
)

data class FocusSessionState(
    val activeProfile: FocusProfile = FocusProfile.OFF,
    val expiresAtMillis: Long? = null,
    val durationOption: DurationOption = DurationOption.UNTIL_TURNED_OFF,
    val customDurationMinutes: Int = 60,
    val dimLockScreenEnabled: Boolean = true,
    val allowStarredContactsOnly: Boolean = true,
    val blockRepeatedCallers: Boolean = false,
    val strictGamingLockdownEnabled: Boolean = true,
    val allowRepeatCallersEnabled: Boolean = true,
    val silenceNotifications: Boolean = true,
    val autoReplyEnabled: Boolean = true,
    val autoReplyMessageTemplate: String = "🛡️ Sentinal: I currently have %s Focus active and notifications are silenced. Reply 'URGENT' to break through immediately.",
    val perProfileAutoReplyMessages: Map<FocusProfile, String> = emptyMap(),
    val autoDeclineVoipInGame: Boolean = true,
    val suppressHeadsUpBanners: Boolean = true,
    val callBouncerEnabled: Boolean = true,
    val repeatCallerCooldownMinutes: Int = 3,
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
    val sessionStartTimeMillis: Long? = null,
    val declineAllCallsRegardless: Boolean = false,
    val gameExitCountdownSeconds: Int? = null,
    val recentIntercepts: List<InterceptEvent> = emptyList(),
    val workScheduleEnabled: Boolean = false,
    val workScheduleStartHour: Int = 9,
    val workScheduleStartMinute: Int = 0,
    val workScheduleEndHour: Int = 17,
    val workScheduleEndMinute: Int = 0,
    val workScheduleDays: Set<Int> = DEFAULT_WORK_DAYS,
    val isAutoTriggeredByWorkSchedule: Boolean = false,
    val sleepScheduleEnabled: Boolean = false,
    val sleepScheduleStartHour: Int = 22,
    val sleepScheduleStartMinute: Int = 30,
    val sleepScheduleEndHour: Int = 7,
    val sleepScheduleEndMinute: Int = 0,
    val sleepScheduleDays: Set<Int> = DEFAULT_SLEEP_DAYS,
    val isAutoTriggeredBySleepSchedule: Boolean = false,
    val cinemaNotificationBlockingEnabled: Boolean = true,
    val cinemaZeroVibrationEnabled: Boolean = true,
    val cinemaExtraDimEnabled: Boolean = true,
    val cinemaPresetMinutes: Int = 120,
    val askBeforeGameActivation: Boolean = true,
    val pendingGameActivationPackage: String? = null,
    val pendingGameActivationLabel: String? = null,
    val pendingGameCountdownSeconds: Int? = null,
    val isPendingGamePreview: Boolean = false,
    val activeGamePackage: String? = null,
    val declinedGamePackageForSession: String? = null,
    val manuallyDismissedGamingWhilstInGame: Boolean = false,
    val globalFocusRulesEnabled: Boolean = true,
    val enforceGlobalRulesWhenStandby: Boolean = false,
    val priorityLevelRules: Map<NotificationPriorityLevel, GlobalFocusRuleAction> = DEFAULT_PRIORITY_LEVEL_RULES,
    val categoryPriorityOverrides: Map<NotificationCategory, NotificationPriorityLevel> = DEFAULT_CATEGORY_PRIORITIES,
    val categorizedNotifications: List<CategorizedNotificationEvent> = emptyList(),
    val categoryInterceptCounts: Map<NotificationCategory, Int> = DEFAULT_CATEGORY_COUNTS,
    val autoSummarizeMissedOnSessionEnd: Boolean = true,
    val userGeminiApiKey: String = "",
    val selectedGeminiModelPreset: GeminiModelPreset = GeminiModelPreset.GEMINI_2_5_FLASH
) {
    val hasUserGeminiApiKey: Boolean
        get() = userGeminiApiKey.isNotBlank()

    val maskedUserGeminiApiKey: String
        get() = AiNotificationDigestRepository.maskUserApiKey(userGeminiApiKey)

    companion object {
        val DEFAULT_WORK_DAYS: Set<Int> = setOf(
            java.util.Calendar.MONDAY,
            java.util.Calendar.TUESDAY,
            java.util.Calendar.WEDNESDAY,
            java.util.Calendar.THURSDAY,
            java.util.Calendar.FRIDAY
        )
        val DEFAULT_SLEEP_DAYS: Set<Int> = setOf(
            java.util.Calendar.SUNDAY,
            java.util.Calendar.MONDAY,
            java.util.Calendar.TUESDAY,
            java.util.Calendar.WEDNESDAY,
            java.util.Calendar.THURSDAY,
            java.util.Calendar.FRIDAY,
            java.util.Calendar.SATURDAY
        )
        val DEFAULT_PRIORITY_LEVEL_RULES: Map<NotificationPriorityLevel, GlobalFocusRuleAction> = mapOf(
            NotificationPriorityLevel.CRITICAL to GlobalFocusRuleAction.ALLOW_ALL,
            NotificationPriorityLevel.HIGH to GlobalFocusRuleAction.MUTE_SILENT,
            NotificationPriorityLevel.MEDIUM to GlobalFocusRuleAction.HIDE_COMPLETELY,
            NotificationPriorityLevel.LOW to GlobalFocusRuleAction.HIDE_COMPLETELY
        )
        val DEFAULT_CATEGORY_PRIORITIES: Map<NotificationCategory, NotificationPriorityLevel> =
            NotificationCategory.values().associateWith { it.defaultPriority }
        val DEFAULT_CATEGORY_COUNTS: Map<NotificationCategory, Int> = mapOf(
            NotificationCategory.SECURITY_AND_URGENT to 0,
            NotificationCategory.DIRECT_MESSAGES to 0,
            NotificationCategory.WORK_AND_CALENDAR to 0,
            NotificationCategory.SOCIAL_AND_COMMUNITY to 0,
            NotificationCategory.NEWS_AND_MEDIA to 0,
            NotificationCategory.PROMOTIONS_AND_DEALS to 0
        )
    }
}

data class DailyShieldStats(
    val dayKey: String,
    val dayShort: String,
    val dayFull: String,
    val isToday: Boolean,
    val silencedNotifications: Int,
    val declinedCalls: Int
)

data class WeeklyShieldSummary(
    val days: List<DailyShieldStats> = emptyList(),
    val totalSilencedNotifications: Int = 0,
    val totalDeclinedCalls: Int = 0,
    val peakDayLabel: String = "N/A",
    val averageSilencedPerDay: Float = 0f,
    val averageCallsPerDay: Float = 0f
)

data class DailyGamingComparison(
    val dayIndex: Int,
    val dayKeyThisWeek: String,
    val dayKeyLastWeek: String,
    val dayShort: String,
    val dayFullThisWeek: String,
    val dayFullLastWeek: String,
    val isToday: Boolean,
    val thisWeekMinutes: Int,
    val lastWeekMinutes: Int
)

data class GamingWeeklyComparisonSummary(
    val days: List<DailyGamingComparison> = emptyList(),
    val thisWeekTotalMinutes: Int = 0,
    val lastWeekTotalMinutes: Int = 0,
    val deltaMinutes: Int = 0,
    val deltaPercentage: Float = 0f,
    val peakDayThisWeekLabel: String = "N/A",
    val peakDayThisWeekMinutes: Int = 0,
    val averageDailyMinutesThisWeek: Float = 0f
)

class FocusStateManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var expirationJob: Job? = null
    private var gameExitDeactivationJob: Job? = null
    private var gameLaunchConfirmationJob: Job? = null
    private var workScheduleWatcherJob: Job? = null

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<FocusSessionState> = _state.asStateFlow()

    private val _activeProfileFlow = MutableStateFlow(_state.value.activeProfile)
    val activeProfileFlow: StateFlow<FocusProfile> = _activeProfileFlow.asStateFlow()

    @Volatile
    private var latestRoomSessions: List<FocusSessionHistoryEntity> = emptyList()

    private val _weeklyStatsFlow = MutableStateFlow(calculateWeeklyStats(emptyList()))
    val weeklyStatsFlow: StateFlow<WeeklyShieldSummary> = _weeklyStatsFlow.asStateFlow()

    private val _gamingWeeklyComparisonFlow = MutableStateFlow(calculateGamingWeeklyComparison(emptyList()))
    val gamingWeeklyComparisonFlow: StateFlow<GamingWeeklyComparisonSummary> = _gamingWeeklyComparisonFlow.asStateFlow()

    val focusHistoryRepository: FocusSessionHistoryRepository by lazy {
        FocusSessionHistoryRepository(
            FocusHistoryDatabase.getInstance(appContext).focusSessionHistoryDao()
        )
    }

    val aiNotificationDigestRepository: AiNotificationDigestRepository by lazy {
        AiNotificationDigestRepository(
            FocusHistoryDatabase.getInstance(appContext).missedNotificationDao()
        )
    }

    val rustEngineClient: com.example.core.jni.SentinalRustEngineClient =
        com.example.core.jni.SentinalRustEngineClient.getInstance()

    val rustFocusStateMachine: com.example.core.jni.RustFocusStateMachineInterface
        get() = rustEngineClient

    val rustNotificationClassifier: com.example.core.jni.RustNotificationClassifierInterface
        get() = rustEngineClient

    val rustCallBouncer: com.example.core.jni.RustCallBouncerInterface
        get() = rustEngineClient

    init {
        scope.launch {
            runCatching {
                focusHistoryRepository.allSessionsFlow.collect { roomSessions ->
                    latestRoomSessions = roomSessions
                    _weeklyStatsFlow.value = calculateWeeklyStats(roomSessions)
                    _gamingWeeklyComparisonFlow.value = calculateGamingWeeklyComparison(roomSessions)
                }
            }
        }
        scope.launch {
            _state.collect { sessionState ->
                if (_activeProfileFlow.value != sessionState.activeProfile) {
                    _activeProfileFlow.value = sessionState.activeProfile
                }
                _gamingWeeklyComparisonFlow.value = calculateGamingWeeklyComparison(latestRoomSessions)
            }
        }
        // Always reset test and breakthrough caches on startup so users never need to clear data
        resetSessionCaches()

        // Resume timer if active profile has an expiration timestamp
        val current = _state.value
        if (current.activeProfile != FocusProfile.OFF && current.expiresAtMillis != null) {
            startExpirationCountdown(current.expiresAtMillis)
        }

        // Auto-activate Game Detection Service on startup if enabled or if Gaming Mode is active
        if (current.autoGameDetectionEnabled || current.activeProfile == FocusProfile.GAMING) {
            runCatching {
                com.example.services.GameDetectorService.start(appContext)
            }
        }

        // Start automated Work Focus schedule watcher
        startWorkScheduleWatcher()
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
        val declineAllCalls = prefs.getBoolean(KEY_DECLINE_ALL_CALLS, false)
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
        val callBouncerEnabled = prefs.getBoolean(KEY_CALL_BOUNCER_ENABLED, true)
        val repeatCallerCooldownMins = prefs.getInt(
            KEY_REPEAT_CALLER_COOLDOWN_MINS,
            DEFAULT_REPEAT_CALLER_COOLDOWN_MINUTES
        ).coerceIn(MIN_REPEAT_CALLER_COOLDOWN_MINUTES, MAX_REPEAT_CALLER_COOLDOWN_MINUTES)
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
        val customDurationMins = prefs.getInt(KEY_CUSTOM_DURATION_MINS, 60).coerceIn(5, 480)
        val startTime = if (prefs.contains(KEY_SESSION_START)) prefs.getLong(KEY_SESSION_START, 0L).takeIf { it > 0 } else null
        val seedIntercepts = emptyList<InterceptEvent>()

        val workSchedEnabled = prefs.getBoolean(KEY_WORK_SCHEDULE_ENABLED, false)
        val workStartHour = prefs.getInt(KEY_WORK_START_HOUR, 9).coerceIn(0, 23)
        val workStartMinute = prefs.getInt(KEY_WORK_START_MIN, 0).coerceIn(0, 59)
        val workEndHour = prefs.getInt(KEY_WORK_END_HOUR, 17).coerceIn(0, 23)
        val workEndMinute = prefs.getInt(KEY_WORK_END_MIN, 0).coerceIn(0, 59)
        val workDaysRaw = prefs.getStringSet(KEY_WORK_SCHEDULE_DAYS, null)
        val workDays = workDaysRaw?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?.takeIf { it.isNotEmpty() } ?: FocusSessionState.DEFAULT_WORK_DAYS
        val isAutoWork = prefs.getBoolean(KEY_IS_AUTO_WORK_SCHEDULE, false)

        val sleepSchedEnabled = prefs.getBoolean(KEY_SLEEP_SCHEDULE_ENABLED, false)
        val sleepStartHour = prefs.getInt(KEY_SLEEP_START_HOUR, 22).coerceIn(0, 23)
        val sleepStartMinute = prefs.getInt(KEY_SLEEP_START_MIN, 30).coerceIn(0, 59)
        val sleepEndHour = prefs.getInt(KEY_SLEEP_END_HOUR, 7).coerceIn(0, 23)
        val sleepEndMinute = prefs.getInt(KEY_SLEEP_END_MIN, 0).coerceIn(0, 59)
        val sleepDaysRaw = prefs.getStringSet(KEY_SLEEP_SCHEDULE_DAYS, null)
        val sleepDays = sleepDaysRaw?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?.takeIf { it.isNotEmpty() } ?: FocusSessionState.DEFAULT_SLEEP_DAYS
        val isAutoSleep = prefs.getBoolean(KEY_IS_AUTO_SLEEP_SCHEDULE, false)

        val cinemaNotifBlocking = prefs.getBoolean(KEY_CINEMA_NOTIF_BLOCKING, true)
        val cinemaZeroVib = prefs.getBoolean(KEY_CINEMA_ZERO_VIBRATION, true)
        val cinemaExtraDim = prefs.getBoolean(KEY_CINEMA_EXTRA_DIM, true)
        val cinemaPresetMins = prefs.getInt(KEY_CINEMA_PRESET_MINS, 120)
        val askBeforeGame = prefs.getBoolean(KEY_ASK_BEFORE_GAME_ACTIVATION, true)

        val globalRulesEnabled = prefs.getBoolean(KEY_GLOBAL_FOCUS_RULES_ENABLED, true)
        val enforceGlobalStandby = prefs.getBoolean(KEY_ENFORCE_GLOBAL_RULES_STANDBY, false)
        val autoSummarizeMissed = prefs.getBoolean(KEY_AUTO_SUMMARIZE_MISSED, true)
        val savedUserGeminiKey = prefs.getString(KEY_USER_BYOK_GEMINI_API_KEY, "")?.trim().orEmpty()
        val savedModelPreset = GeminiModelPreset.fromModelIdOrName(
            prefs.getString(KEY_SELECTED_GEMINI_MODEL_PRESET, GeminiModelPreset.GEMINI_2_5_FLASH.name)
        )
        val priorityRules = mutableMapOf<NotificationPriorityLevel, GlobalFocusRuleAction>()
        for (level in NotificationPriorityLevel.values()) {
            val defaultAction = FocusSessionState.DEFAULT_PRIORITY_LEVEL_RULES[level] ?: GlobalFocusRuleAction.MUTE_SILENT
            val savedName = prefs.getString("key_priority_rule_${level.name}", defaultAction.name)
            val resolved = runCatching { GlobalFocusRuleAction.valueOf(savedName ?: defaultAction.name) }.getOrDefault(defaultAction)
            priorityRules[level] = resolved
        }
        val categoryPriorities = mutableMapOf<NotificationCategory, NotificationPriorityLevel>()
        for (cat in NotificationCategory.values()) {
            val defaultPriority = cat.defaultPriority
            val savedPriority = prefs.getString("key_category_priority_${cat.name}", defaultPriority.name)
            val resolved = runCatching { NotificationPriorityLevel.valueOf(savedPriority ?: defaultPriority.name) }.getOrDefault(defaultPriority)
            categoryPriorities[cat] = resolved
        }
        val categoryCounts = mutableMapOf<NotificationCategory, Int>()
        for (cat in NotificationCategory.values()) {
            val defaultCount = FocusSessionState.DEFAULT_CATEGORY_COUNTS[cat] ?: 0
            categoryCounts[cat] = prefs.getInt("user_category_count_${cat.name}", defaultCount)
        }
        val seedCategorized = emptyList<CategorizedNotificationEvent>()

        // If saved session already expired in the past, reset to OFF and clean persistent state
        if (profile != FocusProfile.OFF && expiresAt != null && System.currentTimeMillis() >= expiresAt) {
            prefs.edit()
                .putString(KEY_ACTIVE_PROFILE, FocusProfile.OFF.id)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_SESSION_START)
                .putBoolean(KEY_IS_AUTO_WORK_SCHEDULE, false)
                .putBoolean(KEY_IS_AUTO_SLEEP_SCHEDULE, false)
                .commit()
            return FocusSessionState(
                activeProfile = FocusProfile.OFF,
                expiresAtMillis = null,
                durationOption = DurationOption.UNTIL_TURNED_OFF,
                customDurationMinutes = customDurationMins,
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
                callBouncerEnabled = callBouncerEnabled,
                repeatCallerCooldownMinutes = repeatCallerCooldownMins,
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
                sessionStartTimeMillis = null,
                declineAllCallsRegardless = declineAllCalls,
                recentIntercepts = seedIntercepts,
                workScheduleEnabled = workSchedEnabled,
                workScheduleStartHour = workStartHour,
                workScheduleStartMinute = workStartMinute,
                workScheduleEndHour = workEndHour,
                workScheduleEndMinute = workEndMinute,
                workScheduleDays = workDays,
                isAutoTriggeredByWorkSchedule = false,
                sleepScheduleEnabled = sleepSchedEnabled,
                sleepScheduleStartHour = sleepStartHour,
                sleepScheduleStartMinute = sleepStartMinute,
                sleepScheduleEndHour = sleepEndHour,
                sleepScheduleEndMinute = sleepEndMinute,
                sleepScheduleDays = sleepDays,
                isAutoTriggeredBySleepSchedule = false,
                cinemaNotificationBlockingEnabled = cinemaNotifBlocking,
                cinemaZeroVibrationEnabled = cinemaZeroVib,
                cinemaExtraDimEnabled = cinemaExtraDim,
                cinemaPresetMinutes = cinemaPresetMins,
                askBeforeGameActivation = askBeforeGame,
                globalFocusRulesEnabled = globalRulesEnabled,
                enforceGlobalRulesWhenStandby = enforceGlobalStandby,
                priorityLevelRules = priorityRules,
                categoryPriorityOverrides = categoryPriorities,
                categorizedNotifications = seedCategorized,
                categoryInterceptCounts = categoryCounts,
                autoSummarizeMissedOnSessionEnd = autoSummarizeMissed,
                userGeminiApiKey = savedUserGeminiKey,
                selectedGeminiModelPreset = savedModelPreset
            )
        }

        return FocusSessionState(
            activeProfile = profile,
            expiresAtMillis = expiresAt,
            durationOption = durationOption,
            customDurationMinutes = customDurationMins,
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
            callBouncerEnabled = callBouncerEnabled,
            repeatCallerCooldownMinutes = repeatCallerCooldownMins,
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
            sessionStartTimeMillis = startTime,
            declineAllCallsRegardless = declineAllCalls,
            recentIntercepts = seedIntercepts,
            workScheduleEnabled = workSchedEnabled,
            workScheduleStartHour = workStartHour,
            workScheduleStartMinute = workStartMinute,
            workScheduleEndHour = workEndHour,
            workScheduleEndMinute = workEndMinute,
            workScheduleDays = workDays,
            isAutoTriggeredByWorkSchedule = isAutoWork,
            sleepScheduleEnabled = sleepSchedEnabled,
            sleepScheduleStartHour = sleepStartHour,
            sleepScheduleStartMinute = sleepStartMinute,
            sleepScheduleEndHour = sleepEndHour,
            sleepScheduleEndMinute = sleepEndMinute,
            sleepScheduleDays = sleepDays,
            isAutoTriggeredBySleepSchedule = isAutoSleep,
            cinemaNotificationBlockingEnabled = cinemaNotifBlocking,
            cinemaZeroVibrationEnabled = cinemaZeroVib,
            cinemaExtraDimEnabled = cinemaExtraDim,
            cinemaPresetMinutes = cinemaPresetMins,
            askBeforeGameActivation = askBeforeGame,
            globalFocusRulesEnabled = globalRulesEnabled,
            enforceGlobalRulesWhenStandby = enforceGlobalStandby,
            priorityLevelRules = priorityRules,
            categoryPriorityOverrides = categoryPriorities,
            categorizedNotifications = seedCategorized,
            categoryInterceptCounts = categoryCounts,
            autoSummarizeMissedOnSessionEnd = autoSummarizeMissed,
            userGeminiApiKey = savedUserGeminiKey,
            selectedGeminiModelPreset = savedModelPreset
        )
    }

    private fun createSeedCategorizedNotifications(
        categoryPriorities: Map<NotificationCategory, NotificationPriorityLevel>,
        priorityRules: Map<NotificationPriorityLevel, GlobalFocusRuleAction>
    ): List<CategorizedNotificationEvent> {
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        fun buildSeed(
            offsetMs: Long,
            appName: String,
            pkg: String,
            title: String,
            preview: String,
            category: NotificationCategory
        ): CategorizedNotificationEvent {
            val priority = categoryPriorities[category] ?: category.defaultPriority
            val action = priorityRules[priority] ?: GlobalFocusRuleAction.MUTE_SILENT
            val ts = now - offsetMs
            return CategorizedNotificationEvent(
                timestampMillis = ts,
                timeLabel = fmt.format(Date(ts)),
                appName = appName,
                packageName = pkg,
                title = title,
                previewText = preview,
                category = category,
                priorityLevel = priority,
                appliedAction = action
            )
        }
        return listOf(
            buildSeed(
                offsetMs = 95_000L,
                appName = "Authenticator • 2FA",
                pkg = "com.google.android.apps.authenticator2",
                title = "Security Verification Code",
                preview = "Your one-time login OTP code is 849-210. Expires in 5 minutes.",
                category = NotificationCategory.SECURITY_AND_URGENT
            ),
            buildSeed(
                offsetMs = 240_000L,
                appName = "Slack Workspace",
                pkg = "com.Slack",
                title = "#release-ops • @channel",
                preview = "Deployment pipeline verified for v2.4 build.",
                category = NotificationCategory.WORK_AND_CALENDAR
            ),
            buildSeed(
                offsetMs = 420_000L,
                appName = "WhatsApp",
                pkg = "com.whatsapp",
                title = "Maya Lin",
                preview = "Are we still meeting at 6:30 PM after your session?",
                category = NotificationCategory.DIRECT_MESSAGES
            ),
            buildSeed(
                offsetMs = 780_000L,
                appName = "Instagram",
                pkg = "com.instagram.android",
                title = "alex_design and 14 others",
                preview = "Liked your story highlight and left a comment.",
                category = NotificationCategory.SOCIAL_AND_COMMUNITY
            ),
            buildSeed(
                offsetMs = 1_150_000L,
                appName = "Flash Deals Store",
                pkg = "com.amazon.mShop.android.shopping",
                title = "40% OFF Weekend Tech Sale",
                preview = "Limited-time coupon expires at midnight tonight!",
                category = NotificationCategory.PROMOTIONS_AND_DEALS
            )
        )
    }

    private fun createSeedIntercepts(): List<InterceptEvent> {
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return listOf(
            InterceptEvent(
                timestampMillis = now - 180_000L,
                timeLabel = fmt.format(Date(now - 180_000L)),
                type = InterceptType.VOIP_CALL_BOUNCED,
                sourceLabel = "WhatsApp Voice Call",
                detail = "Declined incoming call & dispatched Focus auto-reply",
                profileName = "Gaming Focus"
            ),
            InterceptEvent(
                timestampMillis = now - 540_000L,
                timeLabel = fmt.format(Date(now - 540_000L)),
                type = InterceptType.ALERT_SILENCED,
                sourceLabel = "Group Chat Mention",
                detail = "Silenced & dismissed without auto-reply",
                profileName = "Do Not Disturb"
            ),
            InterceptEvent(
                timestampMillis = now - 1_200_000L,
                timeLabel = fmt.format(Date(now - 1_200_000L)),
                type = InterceptType.CALL_DECLINED,
                sourceLabel = "Cellular Incoming Call",
                detail = "Instant busy rejection • 3-min repeat window active",
                profileName = "Work Focus"
            )
        )
    }

    fun getActiveProfile(): FocusProfile {
        checkAndExpireIfNeeded()
        return _state.value.activeProfile
    }

    /**
     * Immediately deactivates Focus Shield if a timed session has reached or passed 00:00,
     * guaranteeing the timer never counts into negative numbers.
     */
    fun checkAndExpireIfNeeded(): Boolean {
        val current = _state.value
        val exp = current.expiresAtMillis
        if (current.activeProfile != FocusProfile.OFF && exp != null && System.currentTimeMillis() >= exp) {
            Log.i(TAG, "Focus timer reached 00:00. Auto-deactivating Focus Shield immediately.")
            setProfile(FocusProfile.OFF)
            return true
        }
        return false
    }

    fun isAllowStarredContacts(): Boolean = _state.value.allowStarredContactsOnly

    fun isStrictGamingLockdown(): Boolean = _state.value.strictGamingLockdownEnabled

    fun isAllowRepeatCallers(): Boolean = _state.value.allowRepeatCallersEnabled

    fun isCallBouncerEnabled(): Boolean = _state.value.callBouncerEnabled

    fun setCallBouncerEnabled(enabled: Boolean) {
        val updated = _state.value.copy(callBouncerEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_CALL_BOUNCER_ENABLED, enabled).apply()
        Log.d(TAG, "Call Bouncer engine enabled set to: $enabled")
        if (updated.activeProfile != FocusProfile.OFF) {
            runCatching { com.example.services.ZenShieldForegroundService.update(appContext) }
        }
    }

    fun toggleCallBouncer(): Boolean {
        val next = !_state.value.callBouncerEnabled
        setCallBouncerEnabled(next)
        return next
    }

    fun getRepeatCallerCooldownMinutes(): Int = _state.value.repeatCallerCooldownMinutes

    fun getRepeatCallerCooldownMillis(): Long {
        val mins = _state.value.repeatCallerCooldownMinutes.coerceIn(
            MIN_REPEAT_CALLER_COOLDOWN_MINUTES,
            MAX_REPEAT_CALLER_COOLDOWN_MINUTES
        )
        return mins * 60_000L
    }

    fun setRepeatCallerCooldownMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(
            MIN_REPEAT_CALLER_COOLDOWN_MINUTES,
            MAX_REPEAT_CALLER_COOLDOWN_MINUTES
        )
        val updated = _state.value.copy(repeatCallerCooldownMinutes = clamped)
        _state.value = updated
        prefs.edit().putInt(KEY_REPEAT_CALLER_COOLDOWN_MINS, clamped).apply()
        Log.d(TAG, "Repeat caller cooldown minutes set to: $clamped (${clamped * 60}s)")
    }

    fun isDeclineAllCallsRegardless(): Boolean = _state.value.declineAllCallsRegardless

    fun setDeclineAllCallsRegardless(enabled: Boolean) {
        val updated = _state.value.copy(declineAllCallsRegardless = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_DECLINE_ALL_CALLS, enabled).apply()
        Log.d(TAG, "Decline all calls regardless set to: $enabled")
        if (updated.activeProfile != FocusProfile.OFF) {
            runCatching { com.example.services.ZenShieldForegroundService.update(appContext) }
        }
    }

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
        if (updated.activeProfile != FocusProfile.OFF) {
            runCatching {
                com.example.services.ZenShieldForegroundService.update(appContext)
            }
        }
        runCatching {
            com.example.widgets.SentinelWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun toggleAutoReply(): Boolean {
        val next = !_state.value.autoReplyEnabled
        ExpressiveHaptics.playSpringTap(appContext)
        setAutoReplyEnabled(next)
        return next
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
        if (profile == FocusProfile.CINEMA && template == DEFAULT_AUTO_REPLY_TEMPLATE) {
            return DEFAULT_CINEMA_AUTO_REPLY
        }
        return try {
            if (template.contains("%s")) {
                String.format(template, profile.displayName)
            } else if (template.contains("{profile}")) {
                template.replace("{profile}", profile.displayName)
            } else {
                template
            }
        } catch (_: Exception) {
            "🛡️ Sentinal: I currently have ${profile.displayName} Focus active and notifications are silenced. Reply 'URGENT' to break through immediately."
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

    fun scheduleGameExitedDeactivation(delayMillis: Long = GAME_EXIT_DEACTIVATION_DELAY_MS) {
        if (_state.value.activeProfile != FocusProfile.GAMING) return
        if (gameExitDeactivationJob?.isActive == true) return

        val totalSeconds = maxOf(1, (delayMillis / 1000).toInt())
        _state.value = _state.value.copy(gameExitCountdownSeconds = totalSeconds)
        Log.i(TAG, "Game exited or minimized. Scheduling auto-deactivation in ${totalSeconds}s.")
        gameExitDeactivationJob = scope.launch {
            for (sec in (totalSeconds - 1) downTo 1) {
                delay(1000L)
                if (!isActive || _state.value.activeProfile != FocusProfile.GAMING) return@launch
                _state.value = _state.value.copy(gameExitCountdownSeconds = sec)
            }
            delay(1000L)
            if (!isActive || _state.value.activeProfile != FocusProfile.GAMING) return@launch
            _state.value = _state.value.copy(gameExitCountdownSeconds = null)
            Log.i(TAG, "15-second game exit grace period expired. Auto-deactivating Gaming mode.")
            handleGameExited()
        }
    }

    fun cancelGameExitedDeactivation() {
        if (gameExitDeactivationJob?.isActive == true || _state.value.gameExitCountdownSeconds != null) {
            Log.i(TAG, "Game resumed or focus changed. Cancelling pending auto-deactivation countdown.")
            gameExitDeactivationJob?.cancel()
            gameExitDeactivationJob = null
            _state.value = _state.value.copy(gameExitCountdownSeconds = null)
        }
    }

    fun isGameExitedDeactivationScheduled(): Boolean {
        return gameExitDeactivationJob?.isActive == true
    }

    fun setAskBeforeGameActivationEnabled(enabled: Boolean) {
        val updated = _state.value.copy(askBeforeGameActivation = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_ASK_BEFORE_GAME_ACTIVATION, enabled).apply()
        if (!enabled) {
            cancelPendingGameActivationPrompt()
        }
    }

    fun isAskBeforeGameActivationEnabled(): Boolean = _state.value.askBeforeGameActivation

    /**
     * Records that a game package is currently observed in the foreground so that if the user
     * manually turns off Gaming Mode from the notification bar, Sentinal knows which game session
     * to suppress until the user closes and relaunches that game.
     */
    fun notifyForegroundGameObserved(packageName: String) {
        if (packageName.isBlank()) return
        if (_state.value.activeGamePackage != packageName) {
            _state.value = _state.value.copy(activeGamePackage = packageName)
        }
    }

    /**
     * Shows the dedicated Gaming Mode Auto-Activation Confirmation Pop-up with Activate and Not Now
     * buttons and a 5-second auto-activate countdown. If the user does not tap Not Now before the
     * 5-second timer completes, Gaming Mode activates automatically.
     */
    fun requestGameLaunchConfirmation(
        packageName: String,
        appLabel: String? = null,
        countdownSeconds: Int = GAME_CONFIRMATION_COUNTDOWN_SECONDS,
        isManualPreview: Boolean = false
    ) {
        cancelGameExitedDeactivation()
        val current = _state.value
        if (!isManualPreview) {
            if (!current.autoGameDetectionEnabled) return
            if (current.activeProfile == FocusProfile.GAMING) return

            // Suppress re-asking during the same game session if the user tapped 'Not Now'
            // or manually turned off Gaming Mode from the notification bar while in the game
            if (current.declinedGamePackageForSession == packageName || current.manuallyDismissedGamingWhilstInGame) {
                return
            }
        }

        // If a confirmation prompt is already counting down for this exact game, keep it running
        if (!isManualPreview && current.pendingGameActivationPackage == packageName && gameLaunchConfirmationJob?.isActive == true) {
            return
        }

        val resolvedLabel = appLabel?.takeIf { it.isNotBlank() } ?: resolveReadableGameTitle(packageName)
        val totalSeconds = countdownSeconds.coerceIn(1, 15)

        gameLaunchConfirmationJob?.cancel()
        _state.value = current.copy(
            pendingGameActivationPackage = packageName,
            pendingGameActivationLabel = resolvedLabel,
            pendingGameCountdownSeconds = totalSeconds,
            isPendingGamePreview = isManualPreview,
            activeGamePackage = packageName
        )

        runCatching {
            com.example.ui.GamingModePopupManager.showGamingActivationPrompt(
                context = appContext,
                packageName = packageName,
                gameLabel = resolvedLabel,
                remainingSeconds = totalSeconds
            )
        }

        gameLaunchConfirmationJob = scope.launch {
            for (remaining in totalSeconds downTo 1) {
                _state.value = _state.value.copy(
                    pendingGameActivationPackage = packageName,
                    pendingGameActivationLabel = resolvedLabel,
                    pendingGameCountdownSeconds = remaining,
                    isPendingGamePreview = isManualPreview
                )
                runCatching {
                    com.example.ui.GamingModePopupManager.updateGamingActivationPromptCountdown(
                        context = appContext,
                        packageName = packageName,
                        gameLabel = resolvedLabel,
                        remainingSeconds = remaining
                    )
                }
                delay(1000L)
            }
            // Countdown reached 0 without user declining -> auto-activate Gaming Mode
            confirmPendingGameActivation()
        }
    }

    /**
     * Immediately confirms the pending Gaming Mode launch prompt (either when the user taps
     * "Activate" or when the 5-second countdown completes).
     */
    fun confirmPendingGameActivation() {
        val current = _state.value
        val pkg = current.pendingGameActivationPackage ?: current.activeGamePackage ?: "Game Session"
        val wasManualPreview = current.isPendingGamePreview
        gameLaunchConfirmationJob?.cancel()
        gameLaunchConfirmationJob = null
        _state.value = current.copy(
            pendingGameActivationPackage = null,
            pendingGameActivationLabel = null,
            pendingGameCountdownSeconds = null,
            isPendingGamePreview = false,
            activeGamePackage = pkg,
            declinedGamePackageForSession = null,
            manuallyDismissedGamingWhilstInGame = false
        )
        runCatching {
            com.example.ui.GamingModePopupManager.dismissGamingActivationPrompt(appContext)
        }
        handleGameLaunched(pkg, forceActivation = wasManualPreview)
    }

    /**
     * Declines the pending Gaming Mode launch prompt when the user taps "Not Now",
     * and suppresses asking again until the user closes/exits that game and relaunches it.
     */
    fun declinePendingGameActivation() {
        val current = _state.value
        val declinedPkg = current.pendingGameActivationPackage ?: current.activeGamePackage
        gameLaunchConfirmationJob?.cancel()
        gameLaunchConfirmationJob = null
        _state.value = current.copy(
            pendingGameActivationPackage = null,
            pendingGameActivationLabel = null,
            pendingGameCountdownSeconds = null,
            isPendingGamePreview = false,
            declinedGamePackageForSession = declinedPkg,
            manuallyDismissedGamingWhilstInGame = true
        )
        Log.i(TAG, "User tapped Not Now on Gaming Mode prompt for $declinedPkg. Suppressing until game closes.")
        runCatching {
            com.example.ui.GamingModePopupManager.dismissGamingActivationPrompt(appContext)
        }
    }

    fun cancelPendingGameActivationPrompt() {
        if (gameLaunchConfirmationJob?.isActive == true || _state.value.pendingGameActivationPackage != null) {
            gameLaunchConfirmationJob?.cancel()
            gameLaunchConfirmationJob = null
            _state.value = _state.value.copy(
                pendingGameActivationPackage = null,
                pendingGameActivationLabel = null,
                pendingGameCountdownSeconds = null,
                isPendingGamePreview = false
            )
            runCatching {
                com.example.ui.GamingModePopupManager.dismissGamingActivationPrompt(appContext)
            }
        }
    }

    /**
     * Resets the per-session 'Not Now' or manual notification-bar 'Turn Off' suppression
     * once the user genuinely leaves/closes the game to another non-system app or launcher,
     * so relaunching the game later will prompt or auto-activate again.
     */
    fun clearDeclinedGameSessionIfExited(currentForegroundPackage: String?) {
        val current = _state.value
        val declinedPkg = current.declinedGamePackageForSession
        if (declinedPkg == null && !current.manuallyDismissedGamingWhilstInGame && current.activeGamePackage == null) {
            return
        }
        // Ignore transient null polls or SystemUI (notification shade / heads-up banner interaction)
        if (currentForegroundPackage.isNullOrBlank() ||
            currentForegroundPackage == "com.android.systemui" ||
            currentForegroundPackage == "android"
        ) {
            return
        }
        val trackedGamePkg = declinedPkg ?: current.activeGamePackage
        if (trackedGamePkg == null || currentForegroundPackage != trackedGamePkg) {
            Log.i(TAG, "Game ($trackedGamePkg) closed/left for '$currentForegroundPackage'. Resetting session suppression state.")
            _state.value = current.copy(
                declinedGamePackageForSession = null,
                manuallyDismissedGamingWhilstInGame = false,
                activeGamePackage = null
            )
        }
    }

    private fun resolveReadableGameTitle(packageName: String): String {
        return try {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
                ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun handleGameLaunched(packageName: String, forceActivation: Boolean = false) {
        cancelGameExitedDeactivation()
        gameLaunchConfirmationJob?.cancel()
        gameLaunchConfirmationJob = null
        val current = _state.value
        if (!forceActivation) {
            if (!current.autoGameDetectionEnabled) return
            if (current.activeProfile == FocusProfile.GAMING) return
            if (current.declinedGamePackageForSession == packageName || current.manuallyDismissedGamingWhilstInGame) {
                Log.i(TAG, "Suppressing handleGameLaunched for $packageName because user declined or turned off Gaming Mode for this session.")
                return
            }
        }

        val prev = if (current.activeProfile == FocusProfile.GAMING) current.previousProfileBeforeGame else current.activeProfile
        Log.i(TAG, "Game launched ($packageName). Auto-activating GAMING focus profile. Previous profile: ${prev.displayName}")
        val updated = current.copy(
            activeProfile = FocusProfile.GAMING,
            previousProfileBeforeGame = prev,
            isAutoTriggeredByGame = true,
            isAutoTriggeredByWorkSchedule = false,
            durationOption = DurationOption.UNTIL_TURNED_OFF,
            expiresAtMillis = null,
            sessionStartTimeMillis = System.currentTimeMillis(),
            gameExitCountdownSeconds = null,
            pendingGameActivationPackage = null,
            pendingGameActivationLabel = null,
            pendingGameCountdownSeconds = null,
            isPendingGamePreview = false,
            activeGamePackage = packageName,
            declinedGamePackageForSession = null,
            manuallyDismissedGamingWhilstInGame = false
        )
        _state.value = updated
        _activeProfileFlow.value = FocusProfile.GAMING
        persistState(updated)
        syncInterruptionFilter(FocusProfile.GAMING)
        applyFocusLockScreen(appContext, FocusProfile.GAMING, updated.dimLockScreenEnabled)
        runCatching {
            com.example.ui.GamingModePopupManager.dismissGamingActivationPrompt(appContext)
        }
        runCatching {
            com.example.ui.GamingModePopupManager.showGamingModeActivatedPopup(appContext, packageName)
        }
        runCatching {
            com.example.services.ZenShieldForegroundService.start(appContext)
        }
        runCatching {
            com.example.services.NotificationGuardService.ensureBound(appContext)
        }
        runCatching {
            com.example.services.QuickSettingsTileSync.requestSyncAllTiles(appContext)
        }
    }

    fun handleGameExited() {
        cancelGameExitedDeactivation()
        val current = _state.value
        if (current.activeProfile != FocusProfile.GAMING) return

        val endedAt = System.currentTimeMillis()
        recordCompletedFocusSessionToRoom(current, endedAt)

        val targetProfile = if (current.isAutoTriggeredByGame) {
            current.previousProfileBeforeGame
        } else {
            FocusProfile.OFF
        }
        Log.i(TAG, "Game exited. Reverting focus profile from GAMING to ${targetProfile.displayName}")
        val updated = current.copy(
            activeProfile = targetProfile,
            isAutoTriggeredByGame = false,
            durationOption = DurationOption.UNTIL_TURNED_OFF,
            expiresAtMillis = null,
            gameExitCountdownSeconds = null
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
        runCatching {
            com.example.services.QuickSettingsTileSync.requestSyncAllTiles(appContext)
        }
    }

    fun getCallSharedPreferences(): SharedPreferences {
        return appContext.getSharedPreferences(CALL_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getVoipCacheSharedPreferences(): SharedPreferences {
        return appContext.getSharedPreferences(VOIP_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun normalizeCallerLookupKey(raw: String): String {
        return SentinalRustCoreBridge.normalizeCallerLookupKey(raw)
    }

    fun checkVoipRepeatBreakthrough(callerName: String): Boolean {
        if (callerName.isBlank()) return false
        val norm = callerName.trim().lowercase(Locale.ROOT)
        if (norm == "unknown caller" || norm == "unknown" || norm == "whatsapp" ||
            norm == "telegram" || norm == "incoming voice call" || norm == "incoming video call" ||
            norm.startsWith("com.") || norm.startsWith("org.")
        ) {
            return false
        }
        val cachePrefs = getVoipCacheSharedPreferences()
        val key = "voip_repeat_${normalizeCallerLookupKey(norm)}"
        val lastTimestamp = cachePrefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        val cooldownWindowMs = getRepeatCallerCooldownMillis()
        val isWithinCooldownWindow = lastTimestamp > 0L && (now - lastTimestamp) <= cooldownWindowMs

        if (isWithinCooldownWindow) {
            cachePrefs.edit().remove(key).apply()
            return true
        }

        // Record 1st call timestamp
        cachePrefs.edit().putLong(key, now).apply()
        return false
    }

    fun checkCellularRepeatBreakthrough(callerIdentifier: String): Boolean {
        if (callerIdentifier.isBlank()) return false
        val norm = callerIdentifier.trim().lowercase(Locale.ROOT)
        if (norm == "unknown caller" || norm == "unknown" || norm == "incoming call" ||
            norm.startsWith("com.")
        ) {
            return false
        }
        val prefs = getCallSharedPreferences()
        val normalized = normalizeCallerLookupKey(callerIdentifier)
        val key = "last_call_timestamp_$normalized"
        val lastTimestamp = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        val cooldownWindowMs = getRepeatCallerCooldownMillis()
        val isWithinCooldownWindow = lastTimestamp > 0L && (now - lastTimestamp) <= cooldownWindowMs

        if (isWithinCooldownWindow) {
            prefs.edit().remove(key).apply()
            return true
        }

        // Record 1st call timestamp
        prefs.edit().putLong(key, now).apply()
        return false
    }

    fun recordOutgoingCall(recipient: String) {
        if (recipient.isBlank()) return
        val cachePrefs = getVoipCacheSharedPreferences()
        val normalized = recipient.trim().lowercase()
        cachePrefs.edit().putLong("outgoing_call_timestamp_$normalized", System.currentTimeMillis()).apply()
        Log.i(TAG, "Recorded outgoing call to '$recipient' ($normalized). Auto-replies suppressed.")
    }

    fun isRecentOutgoingCall(recipient: String, cooldownMs: Long = OUTGOING_CALL_COOLDOWN_MS): Boolean {
        if (recipient.isBlank()) return false
        val cachePrefs = getVoipCacheSharedPreferences()
        val normalized = recipient.trim().lowercase()
        val lastTimestamp = cachePrefs.getLong("outgoing_call_timestamp_$normalized", 0L)
        if (lastTimestamp > 0L && (System.currentTimeMillis() - lastTimestamp) <= cooldownMs) {
            return true
        }
        return false
    }

    fun shouldSendAutoReply(senderName: String): Boolean {
        if (!_state.value.autoReplyEnabled) return false
        if (senderName.isBlank()) return false
        if (isRecentOutgoingCall(senderName)) {
            Log.i(TAG, "Suppressing auto-reply: user initiated outgoing call to '$senderName'")
            return false
        }
        val cachePrefs = getVoipCacheSharedPreferences()
        val key = "autoreply_cooldown_${senderName.trim().lowercase()}"
        val lastTimestamp = cachePrefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        // 3-minute (180,000 ms) per-caller auto-reply cooldown for declined calls
        return lastTimestamp == 0L || (now - lastTimestamp) > AUTO_REPLY_COOLDOWN_MS
    }

    fun recordAutoReplySent(senderName: String, timestampMillis: Long = System.currentTimeMillis()) {
        if (senderName.isBlank()) return
        val cachePrefs = getVoipCacheSharedPreferences()
        val key = "autoreply_cooldown_${senderName.trim().lowercase()}"
        cachePrefs.edit().putLong(key, timestampMillis).apply()
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
            if (_state.value.pendingGameActivationPackage != null) {
                declinePendingGameActivation()
            }
            runCatching {
                com.example.services.ZenShieldForegroundService.stop(appContext)
            }
            return
        }

        val currentBeforeChange = _state.value
        val wasGaming = currentBeforeChange.activeProfile == FocusProfile.GAMING && profile != FocusProfile.GAMING
        val hadPendingGamePrompt = currentBeforeChange.pendingGameActivationPackage != null && profile != FocusProfile.GAMING

        val now = System.currentTimeMillis()
        // Log any completed focus session into Room database when switching away or turning off
        if (currentBeforeChange.activeProfile != FocusProfile.OFF && currentBeforeChange.activeProfile != profile) {
            recordCompletedFocusSessionToRoom(currentBeforeChange, now)
        }

        // If user manually turns off or switches away from a scheduled WORK session, mark the current work window as manually overridden
        if (currentBeforeChange.activeProfile == FocusProfile.WORK && profile != FocusProfile.WORK && currentBeforeChange.workScheduleEnabled) {
            computeActiveWorkScheduleWindow(now)?.let { (windowStart, _) ->
                prefs.edit().putLong(KEY_DISMISSED_WORK_WINDOW_START, windowStart).apply()
            }
        }

        // If user manually turns off or switches away from a scheduled SLEEP session, mark the current sleep window as manually overridden
        if (currentBeforeChange.activeProfile == FocusProfile.SLEEP && profile != FocusProfile.SLEEP && currentBeforeChange.sleepScheduleEnabled) {
            computeActiveSleepScheduleWindow(now)?.let { (windowStart, _) ->
                prefs.edit().putLong(KEY_DISMISSED_SLEEP_WINDOW_START, windowStart).apply()
            }
        }

        val expiresAt = if (profile != FocusProfile.OFF) {
            duration.durationMinutes?.let { now + it * 60 * 1000L }
        } else {
            null
        }

        expirationJob?.cancel()
        expirationJob = null
        cancelGameExitedDeactivation()
        if (profile != FocusProfile.GAMING) {
            gameLaunchConfirmationJob?.cancel()
            gameLaunchConfirmationJob = null
            runCatching {
                com.example.ui.GamingModePopupManager.dismissGamingActivationPrompt(appContext)
            }
        }

        if (profile != FocusProfile.OFF && expiresAt != null) {
            startExpirationCountdown(expiresAt)
        } else {
            cancelExpirationAlarm()
        }

        // Clean repeat caller and auto-reply caches on every profile transition so testing or new sessions never block
        resetSessionCaches()

        // When user manually turns off or switches away from Gaming Mode (e.g. from notification bar),
        // record the active game session as declined so GameDetectorService will NOT re-enable Gaming Mode
        // until the user closes and relaunches the game.
        val suppressedGamePkg = if (wasGaming || hadPendingGamePrompt) {
            currentBeforeChange.activeGamePackage
                ?: currentBeforeChange.pendingGameActivationPackage
                ?: currentBeforeChange.declinedGamePackageForSession
        } else if (profile == FocusProfile.GAMING) {
            null
        } else {
            currentBeforeChange.declinedGamePackageForSession
        }

        val suppressCurrentGameSession = when {
            wasGaming || hadPendingGamePrompt -> true
            profile == FocusProfile.GAMING -> false
            else -> currentBeforeChange.manuallyDismissedGamingWhilstInGame
        }

        // Preserve user's custom Call Bouncer & Notification toggles across all profile switches
        val updated = _state.value.copy(
            activeProfile = profile,
            expiresAtMillis = expiresAt,
            durationOption = duration,
            sessionStartTimeMillis = if (profile != FocusProfile.OFF) now else null,
            isAutoTriggeredByGame = false,
            previousProfileBeforeGame = FocusProfile.OFF,
            isAutoTriggeredByWorkSchedule = false,
            isAutoTriggeredBySleepSchedule = false,
            pendingGameActivationPackage = if (profile != FocusProfile.GAMING) null else _state.value.pendingGameActivationPackage,
            pendingGameActivationLabel = if (profile != FocusProfile.GAMING) null else _state.value.pendingGameActivationLabel,
            pendingGameCountdownSeconds = if (profile != FocusProfile.GAMING) null else _state.value.pendingGameCountdownSeconds,
            isPendingGamePreview = if (profile != FocusProfile.GAMING) false else _state.value.isPendingGamePreview,
            declinedGamePackageForSession = suppressedGamePkg,
            manuallyDismissedGamingWhilstInGame = suppressCurrentGameSession
        )

        _state.value = updated
        _activeProfileFlow.value = profile
        persistState(updated)

        // Sync Android Interruption Filter
        syncInterruptionFilter(profile)

        // Sync Focus Lock Screen
        applyFocusLockScreen(appContext, profile, updated.dimLockScreenEnabled)

        // Trigger Spring-Correlated Haptic Feedback on Focus Toggle
        if (profile != FocusProfile.OFF) {
            ExpressiveHaptics.playFocusActivated(appContext)
        } else {
            ExpressiveHaptics.playFocusDeactivated(appContext)
        }

        // Sync Bulletproof Watchdog & Live Focus Controller Foreground Service
        if (profile != FocusProfile.OFF) {
            if (profile == FocusProfile.GAMING) {
                runCatching {
                    com.example.services.GameDetectorService.start(appContext)
                }
                runCatching {
                    com.example.ui.GamingModePopupManager.showGamingModeActivatedPopup(appContext, "Focus Shield")
                }
            }
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
        runCatching {
            com.example.services.QuickSettingsTileSync.requestSyncAllTiles(appContext)
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
                // Subtracting time when untimed or already at 00:00 deactivates immediately
                setProfile(FocusProfile.OFF)
                return
            }
        } else {
            val adjusted = currentExpiry + (deltaMinutes * 60_000L)
            if (adjusted <= now) {
                // When remaining time reaches or drops below 00:00, immediately deactivate Focus Shield
                setProfile(FocusProfile.OFF)
                return
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
            FocusProfile.CINEMA -> FocusProfile.DO_NOT_DISTURB
            FocusProfile.OFF -> FocusProfile.DO_NOT_DISTURB
        }

        val leavingGaming = current.activeProfile == FocusProfile.GAMING && nextProfile != FocusProfile.GAMING
        val suppressedGamePkg = if (leavingGaming) {
            current.activeGamePackage ?: current.pendingGameActivationPackage ?: current.declinedGamePackageForSession
        } else if (nextProfile == FocusProfile.GAMING) {
            null
        } else {
            current.declinedGamePackageForSession
        }

        val now = System.currentTimeMillis()
        if (current.activeProfile != FocusProfile.OFF && current.activeProfile != nextProfile) {
            recordCompletedFocusSessionToRoom(current, now)
        }

        // Preserve user's custom Call Bouncer & Notification toggles across all profile switches
        val updated = current.copy(
            activeProfile = nextProfile,
            sessionStartTimeMillis = now,
            isAutoTriggeredByGame = false,
            previousProfileBeforeGame = FocusProfile.OFF,
            isAutoTriggeredByWorkSchedule = false,
            isAutoTriggeredBySleepSchedule = false,
            declinedGamePackageForSession = suppressedGamePkg,
            manuallyDismissedGamingWhilstInGame = when {
                leavingGaming -> true
                nextProfile == FocusProfile.GAMING -> false
                else -> current.manuallyDismissedGamingWhilstInGame
            }
        )
        _state.value = updated
        _activeProfileFlow.value = nextProfile
        persistState(updated)
        syncInterruptionFilter(nextProfile)
        applyFocusLockScreen(appContext, nextProfile, updated.dimLockScreenEnabled)
        ExpressiveHaptics.playFocusActivated(appContext)

        if (nextProfile == FocusProfile.GAMING) {
            runCatching {
                com.example.services.GameDetectorService.start(appContext)
            }
            runCatching {
                com.example.ui.GamingModePopupManager.showGamingModeActivatedPopup(appContext, "Quick Mode Cycle")
            }
        }

        runCatching {
            com.example.services.ZenShieldForegroundService.update(context)
        }
        runCatching {
            com.example.services.QuickSettingsTileSync.requestSyncAllTiles(appContext)
        }
    }

    // =========================================================================
    // WORK FOCUS SCHEDULE ENGINE (MANUAL + AUTOMATED SCHEDULE)
    // =========================================================================

    fun setWorkScheduleEnabled(enabled: Boolean) {
        val updated = _state.value.copy(workScheduleEnabled = enabled)
        _state.value = updated
        prefs.edit()
            .putBoolean(KEY_WORK_SCHEDULE_ENABLED, enabled)
            .remove(KEY_DISMISSED_WORK_WINDOW_START)
            .apply()

        if (enabled) {
            evaluateWorkSchedule()
            scheduleNextWorkScheduleAlarm()
        } else {
            cancelWorkScheduleAlarm()
            if (updated.isAutoTriggeredByWorkSchedule && updated.activeProfile == FocusProfile.WORK) {
                setProfile(FocusProfile.OFF)
            }
        }
    }

    fun setWorkScheduleTime(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val sh = startHour.coerceIn(0, 23)
        val sm = startMinute.coerceIn(0, 59)
        val eh = endHour.coerceIn(0, 23)
        val em = endMinute.coerceIn(0, 59)
        val updated = _state.value.copy(
            workScheduleStartHour = sh,
            workScheduleStartMinute = sm,
            workScheduleEndHour = eh,
            workScheduleEndMinute = em
        )
        _state.value = updated
        prefs.edit()
            .putInt(KEY_WORK_START_HOUR, sh)
            .putInt(KEY_WORK_START_MIN, sm)
            .putInt(KEY_WORK_END_HOUR, eh)
            .putInt(KEY_WORK_END_MIN, em)
            .remove(KEY_DISMISSED_WORK_WINDOW_START)
            .apply()

        if (updated.workScheduleEnabled) {
            evaluateWorkSchedule()
            scheduleNextWorkScheduleAlarm()
        }
    }

    fun toggleWorkScheduleDay(calendarDayOfWeek: Int) {
        val currentDays = _state.value.workScheduleDays
        val nextDays = if (currentDays.contains(calendarDayOfWeek)) {
            if (currentDays.size > 1) currentDays - calendarDayOfWeek else currentDays
        } else {
            currentDays + calendarDayOfWeek
        }
        setWorkScheduleDays(nextDays)
    }

    fun setWorkScheduleDays(days: Set<Int>) {
        if (days.isEmpty()) return
        val updated = _state.value.copy(workScheduleDays = days)
        _state.value = updated
        prefs.edit()
            .putStringSet(KEY_WORK_SCHEDULE_DAYS, days.map { it.toString() }.toSet())
            .remove(KEY_DISMISSED_WORK_WINDOW_START)
            .apply()

        if (updated.workScheduleEnabled) {
            evaluateWorkSchedule()
            scheduleNextWorkScheduleAlarm()
        }
    }

    /**
     * Computes the active `[windowStartMillis, windowEndMillis)` if `nowMillis` falls inside
     * a scheduled Work Focus window (supporting both daytime e.g. 09:00-17:00 and overnight e.g. 22:00-06:00).
     */
    fun computeActiveWorkScheduleWindow(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        val s = _state.value
        if (!s.workScheduleEnabled || s.workScheduleDays.isEmpty()) return null

        val startTotalMins = s.workScheduleStartHour * 60 + s.workScheduleStartMinute
        val endTotalMins = s.workScheduleEndHour * 60 + s.workScheduleEndMinute
        if (startTotalMins == endTotalMins) return null

        val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentDayOfWeek = nowCal.get(java.util.Calendar.DAY_OF_WEEK)
        val currentMinsOfDay = nowCal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + nowCal.get(java.util.Calendar.MINUTE)

        if (startTotalMins < endTotalMins) {
            // Same-day schedule (e.g., 09:00 to 17:00)
            if (s.workScheduleDays.contains(currentDayOfWeek) && currentMinsOfDay in startTotalMins until endTotalMins) {
                val startCal = (nowCal.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, s.workScheduleStartHour)
                    set(java.util.Calendar.MINUTE, s.workScheduleStartMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val endCal = (nowCal.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, s.workScheduleEndHour)
                    set(java.util.Calendar.MINUTE, s.workScheduleEndMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return startCal.timeInMillis to endCal.timeInMillis
            }
        } else {
            // Overnight schedule (e.g., 22:00 to 06:00 next day)
            if (currentMinsOfDay >= startTotalMins && s.workScheduleDays.contains(currentDayOfWeek)) {
                val startCal = (nowCal.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, s.workScheduleStartHour)
                    set(java.util.Calendar.MINUTE, s.workScheduleStartMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val endCal = (nowCal.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, s.workScheduleEndHour)
                    set(java.util.Calendar.MINUTE, s.workScheduleEndMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return startCal.timeInMillis to endCal.timeInMillis
            } else if (currentMinsOfDay < endTotalMins) {
                val yesterdayCal = (nowCal.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_YEAR, -1)
                }
                val yesterdayDow = yesterdayCal.get(java.util.Calendar.DAY_OF_WEEK)
                if (s.workScheduleDays.contains(yesterdayDow)) {
                    val startCal = (yesterdayCal.clone() as java.util.Calendar).apply {
                        set(java.util.Calendar.HOUR_OF_DAY, s.workScheduleStartHour)
                        set(java.util.Calendar.MINUTE, s.workScheduleStartMinute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val endCal = (nowCal.clone() as java.util.Calendar).apply {
                        set(java.util.Calendar.HOUR_OF_DAY, s.workScheduleEndHour)
                        set(java.util.Calendar.MINUTE, s.workScheduleEndMinute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    return startCal.timeInMillis to endCal.timeInMillis
                }
            }
        }
        return null
    }

    /**
     * Evaluates whether Work Focus should automatically activate or expire based on the user's schedule.
     */
    fun evaluateWorkSchedule(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val current = _state.value
        if (!current.workScheduleEnabled) return false

        val activeWindow = computeActiveWorkScheduleWindow(nowMillis)
        if (activeWindow != null) {
            val (windowStart, windowEnd) = activeWindow
            val dismissedWindowStart = prefs.getLong(KEY_DISMISSED_WORK_WINDOW_START, -1L)
            if (dismissedWindowStart == windowStart) {
                // User manually dismissed this scheduled window; respect their override
                return false
            }

            if (current.activeProfile == FocusProfile.OFF) {
                Log.i(TAG, "Scheduled Work Focus window active ($windowStart..$windowEnd). Auto-engaging WORK Focus.")
                expirationJob?.cancel()
                startExpirationCountdown(windowEnd)
                resetSessionCaches()

                val updated = current.copy(
                    activeProfile = FocusProfile.WORK,
                    expiresAtMillis = windowEnd,
                    durationOption = DurationOption.UNTIL_TURNED_OFF,
                    sessionStartTimeMillis = nowMillis,
                    isAutoTriggeredByWorkSchedule = true,
                    isAutoTriggeredByGame = false
                )
                _state.value = updated
                _activeProfileFlow.value = FocusProfile.WORK
                persistState(updated)
                syncInterruptionFilter(FocusProfile.WORK)
                applyFocusLockScreen(appContext, FocusProfile.WORK, updated.dimLockScreenEnabled)
                runCatching { com.example.services.ZenShieldForegroundService.start(appContext) }
                runCatching { com.example.services.NotificationGuardService.ensureBound(appContext) }
                return true
            } else if (current.activeProfile == FocusProfile.WORK && current.isAutoTriggeredByWorkSchedule && current.expiresAtMillis != windowEnd) {
                // Update end timestamp if user edited schedule end time while active
                expirationJob?.cancel()
                startExpirationCountdown(windowEnd)
                val updated = current.copy(expiresAtMillis = windowEnd)
                _state.value = updated
                persistState(updated)
                runCatching { com.example.services.ZenShieldForegroundService.update(appContext) }
                return true
            }
        } else {
            // Outside scheduled window: if currently in auto-triggered Work Focus, deactivate cleanly
            if (current.activeProfile == FocusProfile.WORK && current.isAutoTriggeredByWorkSchedule) {
                Log.i(TAG, "Scheduled Work Focus window ended. Deactivating WORK Focus.")
                setProfile(FocusProfile.OFF)
                return true
            }
        }
        return false
    }

    private fun startWorkScheduleWatcher() {
        workScheduleWatcherJob?.cancel()
        scheduleNextWorkScheduleAlarm()
        scheduleNextSleepScheduleAlarm()
        workScheduleWatcherJob = scope.launch {
            while (isActive) {
                try {
                    checkAndExpireIfNeeded()
                    evaluateWorkSchedule()
                    evaluateSleepSchedule()
                } catch (e: Exception) {
                    Log.w(TAG, "Schedule watcher check failed: ${e.message}")
                }
                delay(15_000L)
            }
        }
    }

    /**
     * Schedules an exact Doze-safe AlarmManager wakeup at the next Work Focus start or end boundary.
     */
    fun scheduleNextWorkScheduleAlarm(nowMillis: Long = System.currentTimeMillis()) {
        val s = _state.value
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, BootCompletedReceiver::class.java).apply {
            action = BootCompletedReceiver.ACTION_EVALUATE_WORK_SCHEDULE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_WORK_SCHEDULE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!s.workScheduleEnabled || s.workScheduleDays.isEmpty()) {
            runCatching { alarmManager.cancel(pendingIntent) }
            return
        }

        val activeWindow = computeActiveWorkScheduleWindow(nowMillis)
        val nextTriggerMillis: Long? = if (activeWindow != null) {
            activeWindow.second
        } else {
            // Find next upcoming start time within the next 8 days
            var earliestStart: Long? = null
            for (dayOffset in 0..7) {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
                    set(java.util.Calendar.HOUR_OF_DAY, s.workScheduleStartHour)
                    set(java.util.Calendar.MINUTE, s.workScheduleStartMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                if (s.workScheduleDays.contains(dow) && cal.timeInMillis > nowMillis + 1000L) {
                    earliestStart = cal.timeInMillis
                    break
                }
            }
            earliestStart
        }

        if (nextTriggerMillis != null) {
            scheduleDozeSafeAlarm(alarmManager, nextTriggerMillis, pendingIntent)
        }
    }

    private fun cancelWorkScheduleAlarm() {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, BootCompletedReceiver::class.java).apply {
            action = BootCompletedReceiver.ACTION_EVALUATE_WORK_SCHEDULE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_WORK_SCHEDULE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager.cancel(pendingIntent) }
    }

    // =========================================================================
    // SLEEP FOCUS SCHEDULE ENGINE (MANUAL + AUTOMATED OVERNIGHT/DAILY SCHEDULE)
    // =========================================================================

    fun setSleepScheduleEnabled(enabled: Boolean) {
        val updated = _state.value.copy(sleepScheduleEnabled = enabled)
        _state.value = updated
        prefs.edit()
            .putBoolean(KEY_SLEEP_SCHEDULE_ENABLED, enabled)
            .remove(KEY_DISMISSED_SLEEP_WINDOW_START)
            .apply()

        if (enabled) {
            evaluateSleepSchedule()
            scheduleNextSleepScheduleAlarm()
        } else {
            cancelSleepScheduleAlarm()
            if (updated.isAutoTriggeredBySleepSchedule && updated.activeProfile == FocusProfile.SLEEP) {
                setProfile(FocusProfile.OFF)
            }
        }
    }

    fun setSleepScheduleTime(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val sh = startHour.coerceIn(0, 23)
        val sm = startMinute.coerceIn(0, 59)
        val eh = endHour.coerceIn(0, 23)
        val em = endMinute.coerceIn(0, 59)
        val updated = _state.value.copy(
            sleepScheduleStartHour = sh,
            sleepScheduleStartMinute = sm,
            sleepScheduleEndHour = eh,
            sleepScheduleEndMinute = em
        )
        _state.value = updated
        prefs.edit()
            .putInt(KEY_SLEEP_START_HOUR, sh)
            .putInt(KEY_SLEEP_START_MIN, sm)
            .putInt(KEY_SLEEP_END_HOUR, eh)
            .putInt(KEY_SLEEP_END_MIN, em)
            .remove(KEY_DISMISSED_SLEEP_WINDOW_START)
            .apply()

        if (updated.sleepScheduleEnabled) {
            evaluateSleepSchedule()
            scheduleNextSleepScheduleAlarm()
        }
    }

    fun toggleSleepScheduleDay(calendarDayOfWeek: Int) {
        val currentDays = _state.value.sleepScheduleDays
        val nextDays = if (currentDays.contains(calendarDayOfWeek)) {
            if (currentDays.size > 1) currentDays - calendarDayOfWeek else currentDays
        } else {
            currentDays + calendarDayOfWeek
        }
        setSleepScheduleDays(nextDays)
    }

    fun setSleepScheduleDays(days: Set<Int>) {
        if (days.isEmpty()) return
        val updated = _state.value.copy(sleepScheduleDays = days)
        _state.value = updated
        prefs.edit()
            .putStringSet(KEY_SLEEP_SCHEDULE_DAYS, days.map { it.toString() }.toSet())
            .remove(KEY_DISMISSED_SLEEP_WINDOW_START)
            .apply()

        if (updated.sleepScheduleEnabled) {
            evaluateSleepSchedule()
            scheduleNextSleepScheduleAlarm()
        }
    }

    /**
     * Computes the active `[windowStartMillis, windowEndMillis)` if `nowMillis` falls inside
     * a scheduled Sleep Focus window (supporting both overnight e.g. 22:30-07:00 and same-day e.g. 00:30-08:00).
     */
    fun computeActiveSleepScheduleWindow(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        val s = _state.value
        if (!s.sleepScheduleEnabled || s.sleepScheduleDays.isEmpty()) return null

        val startTotalMins = s.sleepScheduleStartHour * 60 + s.sleepScheduleStartMinute
        val endTotalMins = s.sleepScheduleEndHour * 60 + s.sleepScheduleEndMinute
        if (startTotalMins == endTotalMins) return null

        val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentDayOfWeek = nowCal.get(java.util.Calendar.DAY_OF_WEEK)
        val currentMinsOfDay = nowCal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + nowCal.get(java.util.Calendar.MINUTE)

        if (startTotalMins < endTotalMins) {
            // Same-day sleep window (e.g., 00:30 to 08:00)
            if (s.sleepScheduleDays.contains(currentDayOfWeek) && currentMinsOfDay in startTotalMins until endTotalMins) {
                val startCal = (nowCal.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, s.sleepScheduleStartHour)
                    set(java.util.Calendar.MINUTE, s.sleepScheduleStartMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val endCal = (nowCal.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, s.sleepScheduleEndHour)
                    set(java.util.Calendar.MINUTE, s.sleepScheduleEndMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return startCal.timeInMillis to endCal.timeInMillis
            }
        } else {
            // Overnight sleep window (e.g., 22:30 to 07:00 next morning)
            if (currentMinsOfDay >= startTotalMins && s.sleepScheduleDays.contains(currentDayOfWeek)) {
                val startCal = (nowCal.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, s.sleepScheduleStartHour)
                    set(java.util.Calendar.MINUTE, s.sleepScheduleStartMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val endCal = (nowCal.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, s.sleepScheduleEndHour)
                    set(java.util.Calendar.MINUTE, s.sleepScheduleEndMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return startCal.timeInMillis to endCal.timeInMillis
            } else if (currentMinsOfDay < endTotalMins) {
                val yesterdayCal = (nowCal.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_YEAR, -1)
                }
                val yesterdayDow = yesterdayCal.get(java.util.Calendar.DAY_OF_WEEK)
                if (s.sleepScheduleDays.contains(yesterdayDow)) {
                    val startCal = (yesterdayCal.clone() as java.util.Calendar).apply {
                        set(java.util.Calendar.HOUR_OF_DAY, s.sleepScheduleStartHour)
                        set(java.util.Calendar.MINUTE, s.sleepScheduleStartMinute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val endCal = (nowCal.clone() as java.util.Calendar).apply {
                        set(java.util.Calendar.HOUR_OF_DAY, s.sleepScheduleEndHour)
                        set(java.util.Calendar.MINUTE, s.sleepScheduleEndMinute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    return startCal.timeInMillis to endCal.timeInMillis
                }
            }
        }
        return null
    }

    /**
     * Evaluates whether Sleep Focus should automatically activate at bedtime or expire at wake-up.
     */
    fun evaluateSleepSchedule(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val current = _state.value
        if (!current.sleepScheduleEnabled) return false

        val activeWindow = computeActiveSleepScheduleWindow(nowMillis)
        if (activeWindow != null) {
            val (windowStart, windowEnd) = activeWindow
            val dismissedWindowStart = prefs.getLong(KEY_DISMISSED_SLEEP_WINDOW_START, -1L)
            if (dismissedWindowStart == windowStart) {
                // User manually dismissed this scheduled sleep window; respect their override
                return false
            }

            if (current.activeProfile == FocusProfile.OFF) {
                Log.i(TAG, "Scheduled Sleep Focus window active ($windowStart..$windowEnd). Auto-engaging SLEEP Focus.")
                expirationJob?.cancel()
                startExpirationCountdown(windowEnd)
                resetSessionCaches()

                val updated = current.copy(
                    activeProfile = FocusProfile.SLEEP,
                    expiresAtMillis = windowEnd,
                    durationOption = DurationOption.UNTIL_TURNED_OFF,
                    sessionStartTimeMillis = nowMillis,
                    isAutoTriggeredBySleepSchedule = true,
                    isAutoTriggeredByWorkSchedule = false,
                    isAutoTriggeredByGame = false
                )
                _state.value = updated
                _activeProfileFlow.value = FocusProfile.SLEEP
                persistState(updated)
                syncInterruptionFilter(FocusProfile.SLEEP)
                applyFocusLockScreen(appContext, FocusProfile.SLEEP, updated.dimLockScreenEnabled)
                runCatching { com.example.services.ZenShieldForegroundService.start(appContext) }
                runCatching { com.example.services.NotificationGuardService.ensureBound(appContext) }
                return true
            } else if (current.activeProfile == FocusProfile.SLEEP && current.isAutoTriggeredBySleepSchedule && current.expiresAtMillis != windowEnd) {
                // Update wake-up timestamp if user edited schedule wake-up time while active
                expirationJob?.cancel()
                startExpirationCountdown(windowEnd)
                val updated = current.copy(expiresAtMillis = windowEnd)
                _state.value = updated
                persistState(updated)
                runCatching { com.example.services.ZenShieldForegroundService.update(appContext) }
                return true
            }
        } else {
            // Outside scheduled window: if currently in auto-triggered Sleep Focus, deactivate cleanly at wake-up
            if (current.activeProfile == FocusProfile.SLEEP && current.isAutoTriggeredBySleepSchedule) {
                Log.i(TAG, "Scheduled Sleep Focus window ended. Deactivating SLEEP Focus.")
                setProfile(FocusProfile.OFF)
                return true
            }
        }
        return false
    }

    /**
     * Schedules an exact Doze-safe AlarmManager wakeup at the next Sleep Focus bedtime or wake-up boundary.
     */
    fun scheduleNextSleepScheduleAlarm(nowMillis: Long = System.currentTimeMillis()) {
        val s = _state.value
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, BootCompletedReceiver::class.java).apply {
            action = BootCompletedReceiver.ACTION_EVALUATE_SLEEP_SCHEDULE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_SLEEP_SCHEDULE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!s.sleepScheduleEnabled || s.sleepScheduleDays.isEmpty()) {
            runCatching { alarmManager.cancel(pendingIntent) }
            return
        }

        val activeWindow = computeActiveSleepScheduleWindow(nowMillis)
        val nextTriggerMillis: Long? = if (activeWindow != null) {
            activeWindow.second
        } else {
            // Find next upcoming bedtime within the next 8 days
            var earliestStart: Long? = null
            for (dayOffset in 0..7) {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
                    set(java.util.Calendar.HOUR_OF_DAY, s.sleepScheduleStartHour)
                    set(java.util.Calendar.MINUTE, s.sleepScheduleStartMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                if (s.sleepScheduleDays.contains(dow) && cal.timeInMillis > nowMillis + 1000L) {
                    earliestStart = cal.timeInMillis
                    break
                }
            }
            earliestStart
        }

        if (nextTriggerMillis != null) {
            scheduleDozeSafeAlarm(alarmManager, nextTriggerMillis, pendingIntent)
        }
    }

    private fun cancelSleepScheduleAlarm() {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, BootCompletedReceiver::class.java).apply {
            action = BootCompletedReceiver.ACTION_EVALUATE_SLEEP_SCHEDULE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_SLEEP_SCHEDULE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager.cancel(pendingIntent) }
    }

    /**
     * Restores an active focus session or scheduled Work/Sleep Focus on device reboot/update
     * without resetting the remaining countdown timer.
     */
    fun restoreAfterBootOrScheduleTick() {
        scheduleNextWorkScheduleAlarm()
        scheduleNextSleepScheduleAlarm()
        if (checkAndExpireIfNeeded()) {
            evaluateWorkSchedule()
            evaluateSleepSchedule()
            return
        }
        if (evaluateWorkSchedule() || evaluateSleepSchedule()) {
            return
        }
        val current = _state.value
        if (current.activeProfile != FocusProfile.OFF) {
            if (current.expiresAtMillis != null) {
                startExpirationCountdown(current.expiresAtMillis)
            }
            syncInterruptionFilter(current.activeProfile)
            applyFocusLockScreen(appContext, current.activeProfile, current.dimLockScreenEnabled)
            runCatching { com.example.services.ZenShieldForegroundService.start(appContext) }
            runCatching { com.example.services.NotificationGuardService.ensureBound(appContext) }
        }
    }

    fun setDuration(duration: DurationOption) {
        val currentProfile = _state.value.activeProfile
        val customMins = duration.durationMinutes?.toInt() ?: _state.value.customDurationMinutes
        if (currentProfile != FocusProfile.OFF) {
            setProfile(currentProfile, duration)
            _state.value = _state.value.copy(customDurationMinutes = customMins)
        } else {
            val updated = _state.value.copy(
                durationOption = duration,
                customDurationMinutes = customMins
            )
            _state.value = updated
            persistState(updated)
        }
    }

    /**
     * Sets a custom timer duration (in minutes, from 5m to 480m / 8h) from the interactive
     * Material 3 Expressive duration slider. If a Focus profile is currently active, immediately
     * updates the live countdown expiration.
     */
    fun setCustomDurationMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(5, 480)
        val current = _state.value
        val now = System.currentTimeMillis()
        val newExpires = if (current.activeProfile != FocusProfile.OFF) {
            now + clamped * 60_000L
        } else {
            current.expiresAtMillis
        }

        if (current.activeProfile != FocusProfile.OFF && newExpires != null) {
            expirationJob?.cancel()
            startExpirationCountdown(newExpires)
        }

        val matchingPreset = when (clamped) {
            45 -> DurationOption.FOR_45_MINUTES
            60 -> DurationOption.FOR_ONE_HOUR
            120 -> DurationOption.FOR_TWO_HOURS
            150 -> DurationOption.FOR_TWO_AND_HALF_HOURS
            180 -> DurationOption.FOR_THREE_HOURS
            else -> current.durationOption
        }

        val updated = current.copy(
            customDurationMinutes = clamped,
            durationOption = matchingPreset,
            expiresAtMillis = newExpires
        )
        _state.value = updated
        persistState(updated)
        if (current.activeProfile != FocusProfile.OFF) {
            runCatching {
                com.example.services.ZenShieldForegroundService.update(appContext)
            }
        }
    }

    // =========================================================================
    // CINEMA / MOVIE THEATER SHIELD ENGINE
    // =========================================================================

    fun setCinemaNotificationBlockingEnabled(enabled: Boolean) {
        val current = _state.value
        val updated = current.copy(
            cinemaNotificationBlockingEnabled = enabled,
            silenceNotifications = if (current.activeProfile == FocusProfile.CINEMA) enabled else current.silenceNotifications,
            suppressHeadsUpBanners = if (current.activeProfile == FocusProfile.CINEMA && enabled) true else current.suppressHeadsUpBanners
        )
        _state.value = updated
        prefs.edit().putBoolean(KEY_CINEMA_NOTIF_BLOCKING, enabled).apply()
        if (updated.activeProfile != FocusProfile.OFF) {
            runCatching { com.example.services.ZenShieldForegroundService.update(appContext) }
        }
    }

    fun setCinemaZeroVibrationEnabled(enabled: Boolean) {
        val updated = _state.value.copy(cinemaZeroVibrationEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_CINEMA_ZERO_VIBRATION, enabled).apply()
    }

    fun setCinemaExtraDimEnabled(enabled: Boolean) {
        val updated = _state.value.copy(
            cinemaExtraDimEnabled = enabled,
            dimLockScreenEnabled = if (_state.value.activeProfile == FocusProfile.CINEMA) enabled else _state.value.dimLockScreenEnabled
        )
        _state.value = updated
        prefs.edit().putBoolean(KEY_CINEMA_EXTRA_DIM, enabled).apply()
        if (updated.activeProfile == FocusProfile.CINEMA) {
            applyFocusLockScreen(appContext, FocusProfile.CINEMA, enabled)
        }
    }

    fun setCinemaPresetMinutes(minutes: Int) {
        val durationOption = when (minutes) {
            120 -> DurationOption.FOR_TWO_HOURS
            150 -> DurationOption.FOR_TWO_AND_HALF_HOURS
            180 -> DurationOption.FOR_THREE_HOURS
            0 -> DurationOption.UNTIL_TURNED_OFF
            else -> DurationOption.FOR_TWO_HOURS
        }
        val current = _state.value
        prefs.edit().putInt(KEY_CINEMA_PRESET_MINS, minutes).apply()

        if (current.activeProfile == FocusProfile.CINEMA) {
            setProfile(FocusProfile.CINEMA, durationOption)
            _state.value = _state.value.copy(
                cinemaPresetMinutes = minutes,
                customDurationMinutes = if (minutes > 0) minutes else current.customDurationMinutes
            )
        } else {
            val updated = current.copy(
                cinemaPresetMinutes = minutes,
                durationOption = durationOption,
                customDurationMinutes = if (minutes > 0) minutes else current.customDurationMinutes
            )
            _state.value = updated
            persistState(updated)
        }
    }

    fun activateCinemaMode(presetMinutes: Int = _state.value.cinemaPresetMinutes) {
        val durationOption = when (presetMinutes) {
            120 -> DurationOption.FOR_TWO_HOURS
            150 -> DurationOption.FOR_TWO_AND_HALF_HOURS
            180 -> DurationOption.FOR_THREE_HOURS
            0 -> DurationOption.UNTIL_TURNED_OFF
            else -> DurationOption.FOR_TWO_HOURS
        }
        val current = _state.value
        _state.value = current.copy(
            cinemaPresetMinutes = presetMinutes,
            customDurationMinutes = if (presetMinutes > 0) presetMinutes else current.customDurationMinutes,
            silenceNotifications = current.cinemaNotificationBlockingEnabled,
            suppressHeadsUpBanners = if (current.cinemaNotificationBlockingEnabled) true else current.suppressHeadsUpBanners,
            dimLockScreenEnabled = current.cinemaExtraDimEnabled
        )
        setProfile(FocusProfile.CINEMA, durationOption)
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

    fun incrementShieldedCall(
        callerLabel: String = "Incoming Call",
        isVoip: Boolean = false
    ) {
        val current = _state.value
        val count = current.totalShieldedCalls + 1
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        val activeName = if (current.activeProfile != FocusProfile.OFF) current.activeProfile.displayName else "Shield Guard"

        val callEvent = InterceptEvent(
            timestampMillis = now,
            timeLabel = timeStr,
            type = if (isVoip) InterceptType.VOIP_CALL_BOUNCED else InterceptType.CALL_DECLINED,
            sourceLabel = callerLabel,
            detail = if (current.autoReplyEnabled) {
                "Call declined • Auto-reply sent (3m cooldown)"
            } else {
                "Call declined silently • Auto-reply OFF"
            },
            profileName = activeName
        )

        val updatedIntercepts = (listOf(callEvent) + current.recentIntercepts).take(15)
        _state.value = current.copy(
            totalShieldedCalls = count,
            recentIntercepts = updatedIntercepts
        )
        prefs.edit().putInt(KEY_SHIELDED_CALLS, count).apply()

        val todayKey = getTodayDateKey()
        val callsKey = "user_daily_calls_$todayKey"
        val currentDaily = prefs.getInt(callsKey, 0) + 1
        prefs.edit().putInt(callsKey, currentDaily).apply()
        _weeklyStatsFlow.value = calculateWeeklyStats()
        ExpressiveHaptics.playNotificationDismissed(appContext)

        scope.launch {
            runCatching {
                aiNotificationDigestRepository.logMissedNotificationOrCall(
                    focusProfile = current.activeProfile,
                    sessionStartTimeMillis = current.sessionStartTimeMillis,
                    appName = if (isVoip) "VoIP Call Bouncer" else "Cellular Call Bouncer",
                    packageName = if (isVoip) "com.whatsapp" else "com.android.server.telecom",
                    senderOrTitle = callerLabel,
                    messagePreview = callEvent.detail,
                    category = NotificationCategory.DIRECT_MESSAGES,
                    priorityLevel = NotificationPriorityLevel.CRITICAL,
                    appliedAction = GlobalFocusRuleAction.HIDE_COMPLETELY,
                    isCallBounce = true,
                    timestampMillis = now
                )
            }
        }
    }

    fun incrementShieldedNotification(
        sourceApp: String = "Incoming Notification",
        detail: String = "Silenced & dismissed without auto-reply",
        alsoLogToRoom: Boolean = true
    ) {
        val current = _state.value
        val count = current.totalShieldedNotifications + 1
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        val activeName = if (current.activeProfile != FocusProfile.OFF) current.activeProfile.displayName else "Shield Guard"

        val notifEvent = InterceptEvent(
            timestampMillis = now,
            timeLabel = timeStr,
            type = InterceptType.ALERT_SILENCED,
            sourceLabel = sourceApp,
            detail = detail,
            profileName = activeName
        )

        val updatedIntercepts = (listOf(notifEvent) + current.recentIntercepts).take(15)
        _state.value = current.copy(
            totalShieldedNotifications = count,
            recentIntercepts = updatedIntercepts
        )
        prefs.edit().putInt(KEY_SHIELDED_NOTIFS, count).apply()

        val todayKey = getTodayDateKey()
        val notifsKey = "user_daily_notifs_$todayKey"
        val currentDaily = prefs.getInt(notifsKey, 0) + 1
        prefs.edit().putInt(notifsKey, currentDaily).apply()
        _weeklyStatsFlow.value = calculateWeeklyStats()
        ExpressiveHaptics.playNotificationDismissed(appContext)

        if (alsoLogToRoom) {
            scope.launch {
                runCatching {
                    val category = classifyIncomingNotification(sourceApp, null, sourceApp, detail)
                    val priority = getPriorityForCategory(category)
                    val action = getRuleActionForPriority(priority)
                    aiNotificationDigestRepository.logMissedNotificationOrCall(
                        focusProfile = current.activeProfile,
                        sessionStartTimeMillis = current.sessionStartTimeMillis,
                        appName = sourceApp.substringBefore(" • ").trim().ifBlank { "Notification" },
                        packageName = "com.sentinal.intercept",
                        senderOrTitle = sourceApp.substringAfter(" • ", sourceApp).trim(),
                        messagePreview = detail,
                        category = category,
                        priorityLevel = priority,
                        appliedAction = action,
                        isCallBounce = false,
                        timestampMillis = now
                    )
                }
            }
        }
    }

    fun setAutoSummarizeMissedOnSessionEnd(enabled: Boolean) {
        val updated = _state.value.copy(autoSummarizeMissedOnSessionEnd = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_AUTO_SUMMARIZE_MISSED, enabled).apply()
    }

    fun setUserGeminiApiKey(apiKey: String) {
        val cleaned = apiKey.trim()
        val updated = _state.value.copy(userGeminiApiKey = cleaned)
        _state.value = updated
        prefs.edit().putString(KEY_USER_BYOK_GEMINI_API_KEY, cleaned).apply()
    }

    fun clearUserGeminiApiKey() {
        val updated = _state.value.copy(userGeminiApiKey = "")
        _state.value = updated
        prefs.edit().remove(KEY_USER_BYOK_GEMINI_API_KEY).apply()
    }

    fun setSelectedGeminiModelPreset(preset: GeminiModelPreset) {
        val updated = _state.value.copy(selectedGeminiModelPreset = preset)
        _state.value = updated
        prefs.edit().putString(KEY_SELECTED_GEMINI_MODEL_PRESET, preset.name).apply()
    }

    // =========================================================================
    // NOTIFICATION CATEGORIZATION & GLOBAL PRIORITY FOCUS RULES ENGINE
    // =========================================================================

    fun setGlobalFocusRulesEnabled(enabled: Boolean) {
        val updated = _state.value.copy(globalFocusRulesEnabled = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_GLOBAL_FOCUS_RULES_ENABLED, enabled).apply()
    }

    fun setEnforceGlobalRulesWhenStandby(enabled: Boolean) {
        val updated = _state.value.copy(enforceGlobalRulesWhenStandby = enabled)
        _state.value = updated
        prefs.edit().putBoolean(KEY_ENFORCE_GLOBAL_RULES_STANDBY, enabled).apply()
    }

    fun setPriorityLevelRule(priority: NotificationPriorityLevel, action: GlobalFocusRuleAction) {
        val current = _state.value
        val updatedRules = current.priorityLevelRules.toMutableMap().apply {
            put(priority, action)
        }
        val refreshedFeed = current.categorizedNotifications.map { item ->
            if (item.priorityLevel == priority) {
                item.copy(appliedAction = action)
            } else {
                item
            }
        }
        val updated = current.copy(
            priorityLevelRules = updatedRules,
            categorizedNotifications = refreshedFeed
        )
        _state.value = updated
        prefs.edit().putString("key_priority_rule_${priority.name}", action.name).apply()
    }

    fun applyGlobalFocusRulePreset(presetId: String) {
        val newRules: Map<NotificationPriorityLevel, GlobalFocusRuleAction> = when (presetId.uppercase(Locale.ROOT)) {
            "STRICT" -> mapOf(
                NotificationPriorityLevel.CRITICAL to GlobalFocusRuleAction.MUTE_SILENT,
                NotificationPriorityLevel.HIGH to GlobalFocusRuleAction.HIDE_COMPLETELY,
                NotificationPriorityLevel.MEDIUM to GlobalFocusRuleAction.HIDE_COMPLETELY,
                NotificationPriorityLevel.LOW to GlobalFocusRuleAction.HIDE_COMPLETELY
            )
            "URGENT_ONLY" -> mapOf(
                NotificationPriorityLevel.CRITICAL to GlobalFocusRuleAction.ALLOW_ALL,
                NotificationPriorityLevel.HIGH to GlobalFocusRuleAction.HIDE_COMPLETELY,
                NotificationPriorityLevel.MEDIUM to GlobalFocusRuleAction.HIDE_COMPLETELY,
                NotificationPriorityLevel.LOW to GlobalFocusRuleAction.HIDE_COMPLETELY
            )
            else -> FocusSessionState.DEFAULT_PRIORITY_LEVEL_RULES // "BALANCED"
        }

        val current = _state.value
        val refreshedFeed = current.categorizedNotifications.map { item ->
            val priority = current.categoryPriorityOverrides[item.category] ?: item.category.defaultPriority
            val action = newRules[priority] ?: GlobalFocusRuleAction.MUTE_SILENT
            item.copy(priorityLevel = priority, appliedAction = action)
        }
        val updated = current.copy(
            priorityLevelRules = newRules,
            categorizedNotifications = refreshedFeed
        )
        _state.value = updated
        val editor = prefs.edit()
        for ((level, action) in newRules) {
            editor.putString("key_priority_rule_${level.name}", action.name)
        }
        editor.apply()
    }

    fun setCategoryPriorityLevel(category: NotificationCategory, priority: NotificationPriorityLevel) {
        val current = _state.value
        val updatedOverrides = current.categoryPriorityOverrides.toMutableMap().apply {
            put(category, priority)
        }
        val effectiveAction = current.priorityLevelRules[priority] ?: GlobalFocusRuleAction.MUTE_SILENT
        val refreshedFeed = current.categorizedNotifications.map { item ->
            if (item.category == category) {
                item.copy(priorityLevel = priority, appliedAction = effectiveAction)
            } else {
                item
            }
        }
        val updated = current.copy(
            categoryPriorityOverrides = updatedOverrides,
            categorizedNotifications = refreshedFeed
        )
        _state.value = updated
        prefs.edit().putString("key_category_priority_${category.name}", priority.name).apply()
    }

    fun cycleCategoryPriorityLevel(category: NotificationCategory): NotificationPriorityLevel {
        val currentPriority = getPriorityForCategory(category)
        val nextPriority = when (currentPriority) {
            NotificationPriorityLevel.CRITICAL -> NotificationPriorityLevel.HIGH
            NotificationPriorityLevel.HIGH -> NotificationPriorityLevel.MEDIUM
            NotificationPriorityLevel.MEDIUM -> NotificationPriorityLevel.LOW
            NotificationPriorityLevel.LOW -> NotificationPriorityLevel.CRITICAL
        }
        setCategoryPriorityLevel(category, nextPriority)
        return nextPriority
    }

    fun getPriorityForCategory(category: NotificationCategory): NotificationPriorityLevel {
        return _state.value.categoryPriorityOverrides[category] ?: category.defaultPriority
    }

    fun getRuleActionForPriority(priority: NotificationPriorityLevel): GlobalFocusRuleAction {
        return _state.value.priorityLevelRules[priority] ?: GlobalFocusRuleAction.MUTE_SILENT
    }

    fun getEffectiveRuleForCategory(category: NotificationCategory): GlobalFocusRuleAction {
        val priority = getPriorityForCategory(category)
        return getRuleActionForPriority(priority)
    }

    /**
     * Evaluates a Focus State Machine transition on the current session state via the
     * Rust JNI interface ([com.example.core.jni.RustFocusStateMachineInterface]).
     */
    fun evaluateFocusStateMachineViaRust(
        event: SentinalRustCoreBridge.RustFocusEvent
    ): SentinalRustCoreBridge.RustTransitionOutcome {
        val currentRustState = SentinalRustCoreBridge.RustFocusEngineState.fromSessionState(_state.value)
        return rustFocusStateMachine.stepStateMachine(currentRustState, event)
    }

    /**
     * Evaluates an incoming notification via the Rust JNI interface
     * ([com.example.core.jni.RustNotificationClassifierInterface]).
     */
    fun evaluateNotificationViaRust(
        packageName: String,
        title: String,
        body: String
    ): SentinalRustCoreBridge.RustClassificationOutcome {
        val s = _state.value
        return rustNotificationClassifier.classifyAndEvaluateNotification(
            packageName = packageName,
            title = title,
            body = body,
            priorityRules = s.priorityLevelRules,
            categoryOverrides = s.categoryPriorityOverrides,
            smartUrgentBreakthroughEnabled = s.smartUrgentBreakthroughEnabled
        )
    }

    /**
     * Categorizes an incoming notification based on its package name, Android Notification.category,
     * title, and text content.
     */
    fun classifyIncomingNotification(
        packageName: String,
        androidCategory: String?,
        title: String,
        text: String
    ): NotificationCategory {
        val pkg = packageName.lowercase(Locale.ROOT)
        val combined = "$title $text".lowercase(Locale.ROOT)

        // 1. Security, OTP, 2FA, Banking & Urgent keywords
        val urgentOrSecurityKeywords = listOf(
            "urgent", "emergency", "verification code", "security code", "one-time",
            "otp", "2fa", "passcode", "auth code", "fraud alert", "bank", "driver arriving",
            "delivery arriving", "your ride"
        )
        if (urgentOrSecurityKeywords.any { combined.contains(it) } ||
            pkg.contains("authenticator") || pkg.contains("1password") || pkg.contains("bitwarden") ||
            androidCategory == android.app.Notification.CATEGORY_ALARM
        ) {
            return NotificationCategory.SECURITY_AND_URGENT
        }

        // 2. Work, Tasks & Calendar
        val workKeywords = listOf("slack", "teams", "outlook", "gmail", "calendar", "jira", "notion", "asana", "linear", "zoom", "meet")
        if (workKeywords.any { pkg.contains(it) } ||
            androidCategory == android.app.Notification.CATEGORY_EVENT ||
            androidCategory == android.app.Notification.CATEGORY_REMINDER ||
            androidCategory == android.app.Notification.CATEGORY_EMAIL ||
            combined.contains("meeting in ") || combined.contains("pull request") || combined.contains("standup")
        ) {
            return NotificationCategory.WORK_AND_CALENDAR
        }

        // 3. Direct Messages & Chats
        val messagingPackages = listOf(
            "whatsapp", "telegram", "signal", "mms", "messaging", "orca", "discord", "viber", "line"
        )
        if (messagingPackages.any { pkg.contains(it) } ||
            androidCategory == android.app.Notification.CATEGORY_MESSAGE
        ) {
            return NotificationCategory.DIRECT_MESSAGES
        }

        // 4. Promotions, Shopping & Deals
        val promoKeywords = listOf(
            "% off", "sale", "deal", "coupon", "promo", "discount", "limited time",
            "free shipping", "cashback", "black friday", "flash sale", "Unsubscribe"
        )
        val shoppingPackages = listOf("amazon", "ebay", "shopify", "aliexpress", "temu", "shein", "walmart", "target")
        if (androidCategory == android.app.Notification.CATEGORY_PROMO ||
            promoKeywords.any { combined.contains(it.lowercase(Locale.ROOT)) } ||
            shoppingPackages.any { pkg.contains(it) }
        ) {
            return NotificationCategory.PROMOTIONS_AND_DEALS
        }

        // 5. Social & Community
        val socialPackages = listOf("instagram", "twitter", "zhiliaoapp", "tiktok", "reddit", "facebook", "snapchat", "threads", "youtube", "linkedin")
        if (androidCategory == android.app.Notification.CATEGORY_SOCIAL ||
            socialPackages.any { pkg.contains(it) } ||
            combined.contains("liked your") || combined.contains("commented on") || combined.contains("started following")
        ) {
            return NotificationCategory.SOCIAL_AND_COMMUNITY
        }

        // 6. Default to News, Media & Digests
        return NotificationCategory.NEWS_AND_MEDIA
    }

    /**
     * Classifies an incoming notification, determines its user-configured priority level and
     * global focus rule action (ALLOW_ALL, MUTE_SILENT, HIDE_COMPLETELY), records it in the
     * categorized stream, and returns the action for NotificationGuardService to enforce.
     */
    fun evaluateAndRecordCategorizedNotification(
        packageName: String,
        appLabel: String,
        androidCategory: String?,
        title: String,
        text: String
    ): GlobalFocusRuleAction {
        val category = classifyIncomingNotification(packageName, androidCategory, title, text)
        val priority = getPriorityForCategory(category)
        val action = getRuleActionForPriority(priority)

        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        val event = CategorizedNotificationEvent(
            timestampMillis = now,
            timeLabel = timeStr,
            appName = appLabel.ifBlank { packageName },
            packageName = packageName,
            title = title.ifBlank { category.shortTitle },
            previewText = text.ifBlank { "Categorized as ${category.shortTitle} (${priority.label} priority)" },
            category = category,
            priorityLevel = priority,
            appliedAction = action
        )

        val current = _state.value
        val newCategoryCount = (current.categoryInterceptCounts[category] ?: 0) + 1
        val updatedCounts = current.categoryInterceptCounts.toMutableMap().apply {
            put(category, newCategoryCount)
        }
        val updatedFeed = (listOf(event) + current.categorizedNotifications).take(25)

        _state.value = current.copy(
            categorizedNotifications = updatedFeed,
            categoryInterceptCounts = updatedCounts
        )
        prefs.edit().putInt("user_category_count_${category.name}", newCategoryCount).apply()

        if (action != GlobalFocusRuleAction.ALLOW_ALL) {
            scope.launch {
                runCatching {
                    aiNotificationDigestRepository.logMissedNotificationOrCall(
                        focusProfile = current.activeProfile,
                        sessionStartTimeMillis = current.sessionStartTimeMillis,
                        appName = event.appName,
                        packageName = event.packageName,
                        senderOrTitle = event.title,
                        messagePreview = event.previewText,
                        category = category,
                        priorityLevel = priority,
                        appliedAction = action,
                        isCallBounce = false,
                        timestampMillis = now
                    )
                }
            }
        }

        return action
    }

    /**
     * Simulates an incoming notification for a specific category so the user can test their
     * Priority Level & Global Focus Rules in real time from the dashboard.
     */
    fun simulateCategorizedNotification(category: NotificationCategory): CategorizedNotificationEvent {
        val (pkg, appName, sampleTitle, sampleText) = when (category) {
            NotificationCategory.SECURITY_AND_URGENT -> listOf(
                "com.google.android.apps.authenticator2",
                "Security Shield 2FA",
                "Login Verification OTP: 492-817",
                "URGENT: Use code 492-817 to verify your sign-in."
            )
            NotificationCategory.DIRECT_MESSAGES -> listOf(
                "com.whatsapp",
                "WhatsApp • Direct Message",
                "Liam Carter",
                "Hey! Let me know when your focus block wraps up."
            )
            NotificationCategory.WORK_AND_CALENDAR -> listOf(
                "com.Slack",
                "Slack • #engineering",
                "Sprint Planning Reminder",
                "Meeting in 10 minutes • Architecture review deck attached."
            )
            NotificationCategory.SOCIAL_AND_COMMUNITY -> listOf(
                "com.instagram.android",
                "Instagram",
                "design_daily & 8 others",
                "Liked your post and mentioned you in a story."
            )
            NotificationCategory.NEWS_AND_MEDIA -> listOf(
                "com.google.android.apps.magazines",
                "Daily Tech Briefing",
                "Evening Digest Ready",
                "Top 5 stories in Android & systems engineering today."
            )
            NotificationCategory.PROMOTIONS_AND_DEALS -> listOf(
                "com.amazon.mShop.android.shopping",
                "Shopping Deals",
                "Flash Sale: 50% Off Accessories",
                "Use promo code FOCUS50 before midnight for free shipping."
            )
        }

        val action = evaluateAndRecordCategorizedNotification(
            packageName = pkg,
            appLabel = appName,
            androidCategory = null,
            title = sampleTitle,
            text = sampleText
        )

        if (action != GlobalFocusRuleAction.ALLOW_ALL) {
            incrementShieldedNotification(
                sourceApp = "$appName • $sampleTitle",
                detail = "${action.badgeText} by Global Rule (${category.shortTitle} → ${getPriorityForCategory(category).label})",
                alsoLogToRoom = false
            )
        } else {
            ExpressiveHaptics.playSpringTap(appContext)
        }

        return _state.value.categorizedNotifications.first()
    }

    fun dismissInterceptEvent(eventId: String) {
        val current = _state.value
        val filtered = current.recentIntercepts.filterNot { it.id == eventId }
        _state.value = current.copy(recentIntercepts = filtered)
        ExpressiveHaptics.playNotificationDismissed(appContext)
    }

    fun clearInterceptHistory() {
        val current = _state.value
        _state.value = current.copy(recentIntercepts = emptyList())
        ExpressiveHaptics.playNotificationDismissed(appContext)
    }

    private fun getTodayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getDailyStandaloneNotificationsMap(nowMillis: Long = System.currentTimeMillis()): Map<String, Int> {
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val map = mutableMapOf<String, Int>()
        for (i in 6 downTo 0) {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(java.util.Calendar.DAY_OF_YEAR, -i)
            }
            val key = keyFormat.format(cal.time)
            map[key] = prefs.getInt("user_daily_notifs_$key", 0)
        }
        return map
    }

    fun getDailyStandaloneCallsMap(nowMillis: Long = System.currentTimeMillis()): Map<String, Int> {
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val map = mutableMapOf<String, Int>()
        for (i in 6 downTo 0) {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(java.util.Calendar.DAY_OF_YEAR, -i)
            }
            val key = keyFormat.format(cal.time)
            map[key] = prefs.getInt("user_daily_calls_$key", 0)
        }
        return map
    }

    fun getDailyExtraGamingMinutesMap(nowMillis: Long = System.currentTimeMillis()): Map<String, Int> {
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayKey = keyFormat.format(Date(nowMillis))
        val currentState = _state.value
        val liveGamingMinutesToday = if (
            currentState.activeProfile == FocusProfile.GAMING &&
            currentState.sessionStartTimeMillis != null
        ) {
            ((nowMillis - currentState.sessionStartTimeMillis) / 60_000L)
                .toInt()
                .coerceAtLeast(1)
        } else {
            0
        }

        val map = mutableMapOf<String, Int>()
        for (i in 13 downTo 0) {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(java.util.Calendar.DAY_OF_YEAR, -i)
            }
            val key = keyFormat.format(cal.time)
            val stored = prefs.getInt("user_daily_gaming_mins_$key", 0)
            map[key] = if (key == todayKey) stored + liveGamingMinutesToday else stored
        }
        return map
    }

    fun getWeeklyStats(): WeeklyShieldSummary = calculateWeeklyStats(latestRoomSessions)

    fun calculateWeeklyStats(
        roomSessions: List<FocusSessionHistoryEntity> = latestRoomSessions
    ): WeeklyShieldSummary {
        val nowMillis = System.currentTimeMillis()
        return FocusSessionHistoryRepository.aggregateWeeklyShieldSummary(
            sessions = roomSessions,
            nowMillis = nowMillis,
            extraNotifsByDateKey = getDailyStandaloneNotificationsMap(nowMillis),
            extraCallsByDateKey = getDailyStandaloneCallsMap(nowMillis)
        )
    }

    fun getGamingWeeklyComparison(): GamingWeeklyComparisonSummary =
        calculateGamingWeeklyComparison(latestRoomSessions)

    /**
     * Records additional minutes spent in Gaming Mode for a given day offset (`0` = today,
     * `1..6` = earlier this week, `7..13` = corresponding day last week), persists the session
     * to Room when appropriate, and updates `gamingWeeklyComparisonFlow`.
     */
    fun recordGamingSessionMinutes(
        minutesToAdd: Int,
        dayOffsetFromToday: Int = 0,
        alsoInsertIntoRoom: Boolean = true
    ) {
        if (minutesToAdd <= 0) return
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -dayOffsetFromToday.coerceIn(0, 30))
        }
        val dateKey = keyFormat.format(cal.time)
        val prefKey = "user_daily_gaming_mins_$dateKey"
        val currentMins = prefs.getInt(prefKey, 0)
        prefs.edit().putInt(prefKey, currentMins + minutesToAdd).apply()
        if (alsoInsertIntoRoom) {
            val endMs = cal.timeInMillis
            val startMs = endMs - (minutesToAdd * 60_000L)
            scope.launch {
                runCatching {
                    focusHistoryRepository.logSession(
                        profile = FocusProfile.GAMING,
                        startTimeMillis = startMs,
                        endTimeMillis = endMs,
                        durationMinutesOverride = minutesToAdd,
                        triggerSource = "Gaming Session"
                    )
                }
            }
        }
        _gamingWeeklyComparisonFlow.value = calculateGamingWeeklyComparison(latestRoomSessions)
    }

    /**
     * Computes the 7-day Gaming Mode duration summary comparing This Week (last 7 days)
     * against Last Week (prior 7 days) using Room focus session rows and live Gaming Mode state.
     */
    fun calculateGamingWeeklyComparison(
        roomSessions: List<FocusSessionHistoryEntity> = latestRoomSessions
    ): GamingWeeklyComparisonSummary {
        val nowMillis = System.currentTimeMillis()
        return FocusSessionHistoryRepository.aggregateGamingWeeklyComparison(
            sessions = roomSessions,
            nowMillis = nowMillis,
            extraMinutesByDateKey = getDailyExtraGamingMinutesMap(nowMillis)
        )
    }

    private fun startExpirationCountdown(targetMillis: Long) {
        expirationJob?.cancel()
        scheduleExpirationAlarm(targetMillis)
        expirationJob = scope.launch {
            while (isActive) {
                val remaining = targetMillis - System.currentTimeMillis()
                if (remaining <= 0L) {
                    Log.i(TAG, "Focus session countdown reached 00:00. Deactivating Focus Shield.")
                    setProfile(FocusProfile.OFF)
                    break
                }
                delay(minOf(250L, remaining.coerceAtLeast(50L)))
            }
        }
    }

    private fun scheduleExpirationAlarm(targetMillis: Long) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, BootCompletedReceiver::class.java).apply {
            action = BootCompletedReceiver.ACTION_EXPIRE_FOCUS_SESSION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_EXPIRATION_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleDozeSafeAlarm(alarmManager, targetMillis, pendingIntent)
    }

    private fun cancelExpirationAlarm() {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, BootCompletedReceiver::class.java).apply {
            action = BootCompletedReceiver.ACTION_EXPIRE_FOCUS_SESSION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_EXPIRATION_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager.cancel(pendingIntent) }
    }

    private fun scheduleDozeSafeAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to schedule exact alarm: ${e.message}")
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
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
            if (profile != FocusProfile.OFF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Configure NotificationManager.Policy so incoming call notifications (including WhatsApp/VoIP)
                // still generate their full Notification.Action PendingIntents for NotificationGuardService
                // to intercept and decline, while suppressing visual popups/peeking.
                val existingPolicy = runCatching { notificationManager.notificationPolicy }.getOrNull()
                val priorityCategories = (existingPolicy?.priorityCategories ?: 0) or
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
                val suppressedEffects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK
                } else {
                    0
                }
                val policy = NotificationManager.Policy(
                    priorityCategories,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    existingPolicy?.priorityMessageSenders ?: NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    suppressedEffects
                )
                runCatching { notificationManager.notificationPolicy = policy }
            }
            notificationManager.setInterruptionFilter(profile.interruptionFilter)
            Log.d(TAG, "Successfully synced interruption filter to ${profile.interruptionFilter}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while setting interruption filter", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting interruption filter", e)
        }
    }

    /**
     * Applies a profile-specific minimalist pitch-black (#000000) OLED lockscreen bitmap
     * whenever any Focus profile (DO_NOT_DISTURB, GAMING, WORK, SLEEP) is active and
     * dimLockScreenEnabled is true. Clears FLAG_LOCK to restore the user's wallpaper when OFF.
     */
    fun applyFocusLockScreen(context: Context, profile: FocusProfile, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }

        val shouldDimLockScreen = enabled && profile != FocusProfile.OFF

        scope.launch(Dispatchers.IO) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)

                if (!shouldDimLockScreen) {
                    // Clear the lock screen override to restore default user wallpaper
                    try {
                        wallpaperManager.clear(WallpaperManager.FLAG_LOCK)
                        Log.d(TAG, "Cleared lock screen wallpaper override for profile ${profile.displayName}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to clear lock screen wallpaper: ${e.message}")
                    }
                    return@launch
                }

                // Generate minimalist pitch-black profile-specific lockscreen bitmap
                val displayMetrics = context.resources.displayMetrics
                val width = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
                val height = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 2400

                val bitmap = generateLockscreenBitmap(width, height, profile)
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                Log.d(TAG, "Applied minimalist OLED focus lockscreen for ${profile.displayName}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied for setting wallpaper: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply focus lockscreen bitmap", e)
            }
        }
    }

    /**
     * Generates a minimalist pitch-black (#000000) OLED lockscreen wallpaper inspired by the
     * user's reference artwork, with distinct dark-slate vector line art and a bottom-right
     * status badge ("DND", "GAME", "WORK", "SLEEP") for each active Focus Profile.
     */
    private fun generateLockscreenBitmap(width: Int, height: Int, profile: FocusProfile): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Pure Pitch-Black (#000000) OLED Canvas
        canvas.drawColor(android.graphics.Color.BLACK)

        // 2. Muted Dark-Slate Vector Outline Paints (matching reference image aesthetic)
        val primaryStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2E3543")
            style = Paint.Style.STROKE
            strokeWidth = (width * 0.0115f).coerceAtLeast(6f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val secondaryStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#282F3C")
            style = Paint.Style.STROKE
            strokeWidth = (width * 0.0095f).coerceAtLeast(5f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // 3. Bottom-Right Badge Paints
        val badgeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6E7785")
            style = Paint.Style.FILL
        }
        val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6E7785")
            style = Paint.Style.STROKE
            strokeWidth = (width * 0.0055f).coerceAtLeast(3f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6E7785")
            textSize = width * 0.033f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }

        val cx = width * 0.50f
        val cy = height * 0.445f
        val r = width * 0.135f

        val sx = width * 0.715f
        val sy = height * 0.655f
        val sr = width * 0.044f

        val bx = width * 0.855f
        val by = height * 0.875f
        val br = width * 0.022f

        when (profile) {
            FocusProfile.DO_NOT_DISTURB, FocusProfile.OFF -> {
                // Centerpiece: Large Outlined Crescent Moon
                drawCrescentPath(canvas, cx, cy, r, primaryStrokePaint)
                // Secondary Lower-Right Outlined Crescent Moon
                drawCrescentPath(canvas, sx, sy, sr, secondaryStrokePaint)
                // Bottom-Right Badge: Filled Crescent Moon + "DND"
                drawCrescentPath(canvas, bx, by, br, badgeIconPaint)
                canvas.drawText("DND", bx, by + width * 0.062f, badgeTextPaint)
            }

            FocusProfile.GAMING -> {
                // Centerpiece: Minimalist Outlined Tactical Gamepad Emblem
                drawGamepadEmblem(canvas, cx, cy, r, primaryStrokePaint)
                // Secondary Lower-Right Outlined Tactical D-Pad Crosshair
                drawCrosshairEmblem(canvas, sx, sy, sr, secondaryStrokePaint)
                // Bottom-Right Badge: Outlined Mini Gamepad + "GAME"
                drawGamepadEmblem(canvas, bx, by, br * 1.15f, badgeStrokePaint)
                canvas.drawText("GAME", bx, by + width * 0.062f, badgeTextPaint)
            }

            FocusProfile.WORK -> {
                // Centerpiece: Minimalist Outlined Executive Briefcase
                drawBriefcaseEmblem(canvas, cx, cy, r, primaryStrokePaint)
                // Secondary Lower-Right Outlined Focus Diamond Node
                drawFourPointStar(canvas, sx, sy, sr * 1.05f, secondaryStrokePaint)
                // Bottom-Right Badge: Outlined Mini Briefcase + "WORK"
                drawBriefcaseEmblem(canvas, bx, by, br * 1.1f, badgeStrokePaint)
                canvas.drawText("WORK", bx, by + width * 0.062f, badgeTextPaint)
            }

            FocusProfile.SLEEP -> {
                // Centerpiece: Outlined Crescent Moon with Constellation Stars
                drawCrescentPath(canvas, cx, cy, r, primaryStrokePaint)
                drawFourPointStar(canvas, cx + r * 0.75f, cy - r * 0.55f, r * 0.24f, primaryStrokePaint)
                // Secondary Lower-Right Outlined Four-Point Star
                drawFourPointStar(canvas, sx, sy, sr * 1.1f, secondaryStrokePaint)
                // Bottom-Right Badge: Filled Crescent Moon + "SLEEP"
                drawCrescentPath(canvas, bx, by, br, badgeIconPaint)
                canvas.drawText("SLEEP", bx, by + width * 0.062f, badgeTextPaint)
            }

            FocusProfile.CINEMA -> {
                // Ultra-Dim 95% Theater Blackout with Warm Amber Low-Luminance Clapperboard / Reel Emblem
                val cinemaStrokePaint = Paint(primaryStrokePaint).apply {
                    color = android.graphics.Color.parseColor("#3D2E14")
                }
                val cinemaBadgePaint = Paint(badgeStrokePaint).apply {
                    color = android.graphics.Color.parseColor("#8A6B32")
                }
                val cinemaTextPaint = Paint(badgeTextPaint).apply {
                    color = android.graphics.Color.parseColor("#8A6B32")
                }
                drawCinemaReelEmblem(canvas, cx, cy, r, cinemaStrokePaint)
                drawFourPointStar(canvas, sx, sy, sr, secondaryStrokePaint)
                drawCinemaReelEmblem(canvas, bx, by, br * 1.1f, cinemaBadgePaint)
                canvas.drawText("CINEMA", bx, by + width * 0.062f, cinemaTextPaint)
            }
        }

        return bitmap
    }

    private fun drawCinemaReelEmblem(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        // Outer cinema film reel circle
        canvas.drawCircle(cx, cy, radius, paint)
        // Inner hub
        canvas.drawCircle(cx, cy, radius * 0.24f, paint)
        // 4 film sprocket chambers
        val offset = radius * 0.54f
        val holeR = radius * 0.16f
        canvas.drawCircle(cx, cy - offset, holeR, paint)
        canvas.drawCircle(cx, cy + offset, holeR, paint)
        canvas.drawCircle(cx - offset, cy, holeR, paint)
        canvas.drawCircle(cx + offset, cy, holeR, paint)
    }

    private fun drawCrescentPath(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val outer = Path().apply {
            addCircle(cx, cy, radius, Path.Direction.CW)
        }
        val inner = Path().apply {
            addCircle(cx + radius * 0.44f, cy - radius * 0.22f, radius * 0.84f, Path.Direction.CW)
        }
        val crescent = Path()
        if (crescent.op(outer, inner, Path.Op.DIFFERENCE)) {
            canvas.drawPath(crescent, paint)
        } else {
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawFourPointStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val inner = radius * 0.28f
        val path = Path().apply {
            moveTo(cx, cy - radius)
            quadTo(cx + inner * 0.4f, cy - inner * 0.4f, cx + radius, cy)
            quadTo(cx + inner * 0.4f, cy + inner * 0.4f, cx, cy + radius)
            quadTo(cx - inner * 0.4f, cy + inner * 0.4f, cx - radius, cy)
            quadTo(cx - inner * 0.4f, cy - inner * 0.4f, cx, cy - radius)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawCrosshairEmblem(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        canvas.drawCircle(cx, cy, radius * 0.78f, paint)
        canvas.drawLine(cx - radius, cy, cx - radius * 0.35f, cy, paint)
        canvas.drawLine(cx + radius * 0.35f, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy - radius, cx, cy - radius * 0.35f, paint)
        canvas.drawLine(cx, cy + radius * 0.35f, cx, cy + radius, paint)
    }

    private fun drawGamepadEmblem(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val w = radius * 1.15f
        val h = radius * 0.72f
        val rect = RectF(cx - w, cy - h, cx + w, cy + h)
        canvas.drawRoundRect(rect, h * 0.75f, h * 0.75f, paint)

        // Left D-Pad cross
        val dpadX = cx - w * 0.44f
        val dpadSize = radius * 0.24f
        canvas.drawLine(dpadX - dpadSize, cy, dpadX + dpadSize, cy, paint)
        canvas.drawLine(dpadX, cy - dpadSize, dpadX, cy + dpadSize, paint)

        // Right Action Buttons
        val btnX = cx + w * 0.44f
        val btnR = radius * 0.10f
        canvas.drawCircle(btnX + btnR * 1.4f, cy - btnR * 1.1f, btnR, paint)
        canvas.drawCircle(btnX - btnR * 1.4f, cy + btnR * 1.1f, btnR, paint)
    }

    private fun drawBriefcaseEmblem(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val bw = radius * 0.98f
        val bh = radius * 0.72f
        val bodyRect = RectF(cx - bw, cy - bh * 0.8f, cx + bw, cy + bh)
        val corner = radius * 0.22f
        canvas.drawRoundRect(bodyRect, corner, corner, paint)

        // Top handle arch
        val hw = radius * 0.38f
        val hh = radius * 0.28f
        val handleRect = RectF(cx - hw, cy - bh * 0.8f - hh, cx + hw, cy - bh * 0.8f + hh)
        canvas.drawArc(handleRect, 180f, 180f, false, paint)

        // Center divider line & clasp
        canvas.drawLine(cx - bw, cy + bh * 0.05f, cx + bw, cy + bh * 0.05f, paint)
        val claspW = radius * 0.18f
        val claspH = radius * 0.15f
        canvas.drawRoundRect(
            RectF(cx - claspW, cy + bh * 0.05f - claspH, cx + claspW, cy + bh * 0.05f + claspH),
            claspW * 0.4f,
            claspW * 0.4f,
            paint
        )
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
            .putBoolean(KEY_CALL_BOUNCER_ENABLED, state.callBouncerEnabled)
            .putInt(KEY_REPEAT_CALLER_COOLDOWN_MINS, state.repeatCallerCooldownMinutes)
            .putBoolean(KEY_NETWORK_REJECTION, state.networkLevelCallRejection)
            .putBoolean(KEY_AUTO_DECLINE_VOIP, state.autoDeclineVoipCalls)
            .putBoolean(KEY_SMART_URGENT, state.smartUrgentBreakthroughEnabled)
            .putBoolean(KEY_AUTO_GAME_DETECTION, state.autoGameDetectionEnabled)
            .putBoolean(KEY_IS_AUTO_TRIGGERED_GAME, state.isAutoTriggeredByGame)
            .putString(KEY_PREVIOUS_PROFILE_BEFORE_GAME, state.previousProfileBeforeGame.id)
            .putStringSet(KEY_CUSTOM_GAME_PACKAGES, state.customGamePackages)
            .putBoolean(KEY_DECLINE_ALL_CALLS, state.declineAllCallsRegardless)
            .putInt(KEY_CUSTOM_DURATION_MINS, state.customDurationMinutes)
            .putBoolean(KEY_WORK_SCHEDULE_ENABLED, state.workScheduleEnabled)
            .putInt(KEY_WORK_START_HOUR, state.workScheduleStartHour)
            .putInt(KEY_WORK_START_MIN, state.workScheduleStartMinute)
            .putInt(KEY_WORK_END_HOUR, state.workScheduleEndHour)
            .putInt(KEY_WORK_END_MIN, state.workScheduleEndMinute)
            .putStringSet(KEY_WORK_SCHEDULE_DAYS, state.workScheduleDays.map { it.toString() }.toSet())
            .putBoolean(KEY_IS_AUTO_WORK_SCHEDULE, state.isAutoTriggeredByWorkSchedule)
            .putBoolean(KEY_SLEEP_SCHEDULE_ENABLED, state.sleepScheduleEnabled)
            .putInt(KEY_SLEEP_START_HOUR, state.sleepScheduleStartHour)
            .putInt(KEY_SLEEP_START_MIN, state.sleepScheduleStartMinute)
            .putInt(KEY_SLEEP_END_HOUR, state.sleepScheduleEndHour)
            .putInt(KEY_SLEEP_END_MIN, state.sleepScheduleEndMinute)
            .putStringSet(KEY_SLEEP_SCHEDULE_DAYS, state.sleepScheduleDays.map { it.toString() }.toSet())
            .putBoolean(KEY_IS_AUTO_SLEEP_SCHEDULE, state.isAutoTriggeredBySleepSchedule)
            .putBoolean(KEY_CINEMA_NOTIF_BLOCKING, state.cinemaNotificationBlockingEnabled)
            .putBoolean(KEY_CINEMA_ZERO_VIBRATION, state.cinemaZeroVibrationEnabled)
            .putBoolean(KEY_CINEMA_EXTRA_DIM, state.cinemaExtraDimEnabled)
            .putInt(KEY_CINEMA_PRESET_MINS, state.cinemaPresetMinutes)
            .putBoolean(KEY_ASK_BEFORE_GAME_ACTIVATION, state.askBeforeGameActivation)
            .putBoolean(KEY_GLOBAL_FOCUS_RULES_ENABLED, state.globalFocusRulesEnabled)
            .putBoolean(KEY_ENFORCE_GLOBAL_RULES_STANDBY, state.enforceGlobalRulesWhenStandby)
            .putBoolean(KEY_AUTO_SUMMARIZE_MISSED, state.autoSummarizeMissedOnSessionEnd)

        for ((level, action) in state.priorityLevelRules) {
            editor.putString("key_priority_rule_${level.name}", action.name)
        }
        for ((cat, priority) in state.categoryPriorityOverrides) {
            editor.putString("key_category_priority_${cat.name}", priority.name)
        }

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
        runCatching {
            com.example.widgets.SentinelWidgetProvider.updateAllWidgets(appContext)
        }
    }

    private fun recordCompletedFocusSessionToRoom(
        previousState: FocusSessionState,
        endedAtMillis: Long
    ) {
        val profile = previousState.activeProfile
        if (profile == FocusProfile.OFF) return
        val startedAt = previousState.sessionStartTimeMillis ?: (endedAtMillis - 60_000L)
        val elapsedMinutes = ((endedAtMillis - startedAt) / 60_000L).toInt().coerceAtLeast(1)
        if (profile == FocusProfile.GAMING) {
            recordGamingSessionMinutes(
                minutesToAdd = elapsedMinutes,
                dayOffsetFromToday = 0,
                alsoInsertIntoRoom = false
            )
        }
        val triggerSource = when {
            previousState.isAutoTriggeredByGame -> "Auto Game Detector"
            previousState.isAutoTriggeredByWorkSchedule || previousState.isAutoTriggeredBySleepSchedule -> "Scheduled"
            else -> "Manual"
        }
        scope.launch {
            runCatching {
                focusHistoryRepository.logSession(
                    profile = profile,
                    startTimeMillis = startedAt,
                    endTimeMillis = endedAtMillis,
                    durationMinutesOverride = elapsedMinutes,
                    triggerSource = triggerSource
                )
            }
            if (previousState.autoSummarizeMissedOnSessionEnd) {
                runCatching {
                    aiNotificationDigestRepository.notifyFocusSessionEndedForAutoDigest(
                        sessionStartTimeMillis = startedAt,
                        endedProfile = profile
                    )
                }
            }
        }
    }

    companion object {
        fun getActiveProfile(context: Context): FocusProfile {
            return getInstance(context).getActiveProfile()
        }

        fun isCinemaZeroVibrationActive(context: Context): Boolean {
            val s = getInstance(context).state.value
            return s.activeProfile == FocusProfile.CINEMA && s.cinemaZeroVibrationEnabled
        }

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
        const val MIN_REPEAT_CALLER_COOLDOWN_MINUTES = 1
        const val MAX_REPEAT_CALLER_COOLDOWN_MINUTES = 15
        const val DEFAULT_REPEAT_CALLER_COOLDOWN_MINUTES = 3
        const val AUTO_REPLY_COOLDOWN_MS = THREE_MINUTES_MS // 3-minute per-caller cooldown for declined call auto-replies
        const val THIRTY_MINUTES_MS = THREE_MINUTES_MS
        const val GAME_EXIT_DEACTIVATION_DELAY_MS = 15_000L // 15-second grace period after exiting a game
        const val GAME_CONFIRMATION_COUNTDOWN_SECONDS = 5 // 5-second auto-activate countdown on game launch prompt
        const val OUTGOING_CALL_COOLDOWN_MS = 600_000L // 10 minutes
        const val DEFAULT_AUTO_REPLY_TEMPLATE = "🛡️ Sentinal: I currently have %s Focus active and notifications are silenced. Reply 'URGENT' to break through immediately."
        const val DEFAULT_CINEMA_AUTO_REPLY = "🍿 Sentinel: I'm currently in a movie theater and my phone is on silent. I'll get back to you when the film ends."

        private const val KEY_ACTIVE_PROFILE = "key_active_profile"
        private const val KEY_EXPIRES_AT = "key_expires_at"
        private const val KEY_DURATION_OPTION = "key_duration_option"
        private const val KEY_CUSTOM_DURATION_MINS = "key_custom_duration_mins"
        private const val KEY_SESSION_START = "key_session_start"
        private const val KEY_DIM_LOCK_SCREEN = "key_dim_lock_screen"
        private const val KEY_ALLOW_STARRED_ONLY = "key_allow_starred_only"
        private const val KEY_BLOCK_REPEATED = "key_block_repeated"
        private const val KEY_STRICT_GAMING = "key_strict_gaming"
        private const val KEY_ALLOW_REPEAT_CALLERS = "key_allow_repeat_callers"
        private const val KEY_CALL_BOUNCER_ENABLED = "key_call_bouncer_enabled"
        private const val KEY_REPEAT_CALLER_COOLDOWN_MINS = "key_repeat_caller_cooldown_mins"
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
        private const val KEY_DECLINE_ALL_CALLS = "key_decline_all_calls"
        private const val KEY_WORK_SCHEDULE_ENABLED = "key_work_schedule_enabled"
        private const val KEY_WORK_START_HOUR = "key_work_start_hour"
        private const val KEY_WORK_START_MIN = "key_work_start_min"
        private const val KEY_WORK_END_HOUR = "key_work_end_hour"
        private const val KEY_WORK_END_MIN = "key_work_end_min"
        private const val KEY_WORK_SCHEDULE_DAYS = "key_work_schedule_days"
        private const val KEY_IS_AUTO_WORK_SCHEDULE = "key_is_auto_work_schedule"
        private const val KEY_DISMISSED_WORK_WINDOW_START = "key_dismissed_work_window_start"
        private const val KEY_SLEEP_SCHEDULE_ENABLED = "key_sleep_schedule_enabled"
        private const val KEY_SLEEP_START_HOUR = "key_sleep_start_hour"
        private const val KEY_SLEEP_START_MIN = "key_sleep_start_min"
        private const val KEY_SLEEP_END_HOUR = "key_sleep_end_hour"
        private const val KEY_SLEEP_END_MIN = "key_sleep_end_min"
        private const val KEY_SLEEP_SCHEDULE_DAYS = "key_sleep_schedule_days"
        private const val KEY_IS_AUTO_SLEEP_SCHEDULE = "key_is_auto_sleep_schedule"
        private const val KEY_DISMISSED_SLEEP_WINDOW_START = "key_dismissed_sleep_window_start"
        private const val KEY_CINEMA_NOTIF_BLOCKING = "key_cinema_notif_blocking"
        private const val KEY_CINEMA_ZERO_VIBRATION = "key_cinema_zero_vibration"
        private const val KEY_CINEMA_EXTRA_DIM = "key_cinema_extra_dim"
        private const val KEY_CINEMA_PRESET_MINS = "key_cinema_preset_mins"
        private const val KEY_ASK_BEFORE_GAME_ACTIVATION = "key_ask_before_game_activation"
        private const val KEY_GLOBAL_FOCUS_RULES_ENABLED = "key_global_focus_rules_enabled"
        private const val KEY_ENFORCE_GLOBAL_RULES_STANDBY = "key_enforce_global_rules_standby"
        private const val KEY_AUTO_SUMMARIZE_MISSED = "key_auto_summarize_missed_on_session_end"
        private const val KEY_USER_BYOK_GEMINI_API_KEY = "key_user_byok_gemini_api_key"
        private const val KEY_SELECTED_GEMINI_MODEL_PRESET = "key_selected_gemini_model_preset"
        private const val REQUEST_CODE_EXPIRATION_ALARM = 9401
        private const val REQUEST_CODE_WORK_SCHEDULE_ALARM = 9402
        private const val REQUEST_CODE_SLEEP_SCHEDULE_ALARM = 9403

        @Volatile
        private var INSTANCE: FocusStateManager? = null

        fun getInstance(context: Context): FocusStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FocusStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
