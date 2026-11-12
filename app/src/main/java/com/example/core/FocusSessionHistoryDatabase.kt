package com.example.core

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Room Entity storing each completed or logged Focus Mode session.
 */
@Entity(tableName = "focus_session_history")
data class FocusSessionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: String,
    val profileDisplayName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Int,
    val triggerSource: String = "Manual",
    val callsDeclined: Int = 0,
    val alertsSilenced: Int = 0
)

/**
 * Room DAO for reactive observation and suspend CRUD operations on Focus Session History.
 */
@Dao
interface FocusSessionHistoryDao {
    @Query("SELECT * FROM focus_session_history ORDER BY endTimeMillis DESC")
    fun getAllSessionsFlow(): Flow<List<FocusSessionHistoryEntity>>

    @Query("SELECT * FROM focus_session_history WHERE profileId = :profileId ORDER BY endTimeMillis DESC")
    fun getSessionsByProfileFlow(profileId: String): Flow<List<FocusSessionHistoryEntity>>

    @Query("SELECT * FROM focus_session_history WHERE endTimeMillis >= :sinceMillis ORDER BY endTimeMillis DESC")
    fun getSessionsSinceFlow(sinceMillis: Long): Flow<List<FocusSessionHistoryEntity>>

    @Query("SELECT COUNT(*) FROM focus_session_history")
    suspend fun getSessionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<FocusSessionHistoryEntity>)

    @Query("DELETE FROM focus_session_history WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM focus_session_history")
    suspend fun clearAllSessions()
}

/**
 * Room Entity storing each notification silenced/hidden or call bounced while Sentinal Focus is active.
 */
@Entity(tableName = "missed_notification_log")
data class MissedNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestampMillis: Long = System.currentTimeMillis(),
    val sessionStartTimeMillis: Long = 0L,
    val focusProfileId: String,
    val focusProfileDisplayName: String,
    val appName: String,
    val packageName: String,
    val senderOrTitle: String,
    val messagePreview: String,
    val categoryName: String = NotificationCategory.DIRECT_MESSAGES.name,
    val priorityLevelName: String = NotificationPriorityLevel.HIGH.name,
    val appliedActionName: String = GlobalFocusRuleAction.MUTE_SILENT.name,
    val isCallBounce: Boolean = false,
    val isReviewed: Boolean = false
)

/**
 * Room Entity storing generated AI Notification Executive Digests.
 */
@Entity(tableName = "ai_notification_digests")
data class AiNotificationDigestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val generatedAtMillis: Long = System.currentTimeMillis(),
    val scopeLabel: String,
    val focusProfileDisplayName: String,
    val totalNotificationsAnalyzed: Int,
    val totalCallsAnalyzed: Int,
    val headline: String,
    val urgentActionItemsJson: String,
    val senderGroupsJson: String,
    val focusTip: String
)

/**
 * Room DAO for reactive observation and CRUD operations on the Missed Notification Log and AI Digests.
 */
@Dao
interface MissedNotificationDao {
    @Query("SELECT * FROM missed_notification_log ORDER BY timestampMillis DESC")
    fun getAllMissedNotificationsFlow(): Flow<List<MissedNotificationEntity>>

    @Query("SELECT * FROM missed_notification_log WHERE isReviewed = 0 ORDER BY timestampMillis DESC")
    fun getUnreadMissedNotificationsFlow(): Flow<List<MissedNotificationEntity>>

    @Query("SELECT * FROM missed_notification_log WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis DESC")
    suspend fun getMissedNotificationsSince(sinceMillis: Long): List<MissedNotificationEntity>

    @Query("SELECT * FROM missed_notification_log ORDER BY timestampMillis DESC")
    suspend fun getAllMissedNotificationsSnapshot(): List<MissedNotificationEntity>

    @Query("SELECT COUNT(*) FROM missed_notification_log")
    suspend fun getMissedNotificationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMissedNotification(entity: MissedNotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMissedNotifications(entities: List<MissedNotificationEntity>)

    @Query("UPDATE missed_notification_log SET isReviewed = :reviewed WHERE id = :id")
    suspend fun setReviewedStatus(id: Long, reviewed: Boolean = true)

    @Query("UPDATE missed_notification_log SET isReviewed = 1")
    suspend fun markAllAsReviewed()

    @Query("DELETE FROM missed_notification_log WHERE id = :id")
    suspend fun deleteMissedNotificationById(id: Long)

    @Query("DELETE FROM missed_notification_log")
    suspend fun clearAllMissedNotifications()

    @Query("SELECT * FROM ai_notification_digests ORDER BY generatedAtMillis DESC LIMIT 1")
    fun getLatestDigestFlow(): Flow<AiNotificationDigestEntity?>

    @Query("SELECT * FROM ai_notification_digests ORDER BY generatedAtMillis DESC")
    fun getAllDigestsFlow(): Flow<List<AiNotificationDigestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDigest(digest: AiNotificationDigestEntity): Long

    @Query("DELETE FROM ai_notification_digests")
    suspend fun clearAllDigests()
}

/**
 * Room Database holder for Sentinal Focus Session History, Missed Notification Log, and AI Digests.
 */
@Database(
    entities = [
        FocusSessionHistoryEntity::class,
        MissedNotificationEntity::class,
        AiNotificationDigestEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FocusHistoryDatabase : RoomDatabase() {
    abstract fun focusSessionHistoryDao(): FocusSessionHistoryDao
    abstract fun missedNotificationDao(): MissedNotificationDao

    companion object {
        private const val DATABASE_NAME = "sentinal_user_focus_sessions_v2.db"

        @Volatile
        private var INSTANCE: FocusHistoryDatabase? = null

        fun getInstance(context: Context): FocusHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusHistoryDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

/**
 * Aggregated time spent per FocusProfile for the Recharts-inspired Mode Bar Chart.
 */
data class ModeTimeAllocation(
    val profile: FocusProfile,
    val totalMinutes: Int,
    val sessionCount: Int,
    val averageSessionMinutes: Int,
    val percentageOfTotal: Float,
    val totalCallsDeclined: Int,
    val totalAlertsSilenced: Int
)

/**
 * Aggregated daily time spent across each FocusProfile for the 7-Day Recharts Bar Chart.
 */
data class DailyFocusModeBarEntry(
    val dayIndex: Int,
    val dayKey: String,
    val dayShort: String,
    val dayFull: String,
    val isToday: Boolean,
    val minutesByProfile: Map<FocusProfile, Int>,
    val totalMinutes: Int,
    val sessionCount: Int
)

/**
 * Repository abstracting Room data access for Focus Session History.
 */
class FocusSessionHistoryRepository(
    private val dao: FocusSessionHistoryDao
) {
    val allSessionsFlow: Flow<List<FocusSessionHistoryEntity>> = dao.getAllSessionsFlow()

    suspend fun logSession(
        profile: FocusProfile,
        startTimeMillis: Long,
        endTimeMillis: Long = System.currentTimeMillis(),
        durationMinutesOverride: Int? = null,
        triggerSource: String = "Manual",
        callsDeclined: Int = 0,
        alertsSilenced: Int = 0
    ): Long {
        if (profile == FocusProfile.OFF) return -1L
        val computedMinutes = durationMinutesOverride
            ?: (((endTimeMillis - startTimeMillis) / 60_000L).toInt().coerceAtLeast(1))
        val safeMinutes = computedMinutes.coerceIn(1, 1440)
        val safeStart = if (endTimeMillis > startTimeMillis) {
            startTimeMillis
        } else {
            endTimeMillis - (safeMinutes * 60_000L)
        }

        val entity = FocusSessionHistoryEntity(
            profileId = profile.id,
            profileDisplayName = profile.displayName,
            startTimeMillis = safeStart,
            endTimeMillis = endTimeMillis,
            durationMinutes = safeMinutes,
            triggerSource = triggerSource,
            callsDeclined = callsDeclined.coerceAtLeast(0),
            alertsSilenced = alertsSilenced.coerceAtLeast(0)
        )
        return dao.insertSession(entity)
    }

    suspend fun deleteSessionById(id: Long) {
        dao.deleteSessionById(id)
    }

    suspend fun clearAllSessions() {
        dao.clearAllSessions()
    }

    suspend fun seedInitialSessionsIfEmpty() {
        if (dao.getSessionCount() == 0) {
            dao.insertSessions(buildRealisticSeedSessions())
        }
    }

    suspend fun resetToDefaultSeedSessions() {
        dao.clearAllSessions()
        dao.insertSessions(buildRealisticSeedSessions())
    }

    companion object {
        val TRACKED_MODES: List<FocusProfile> = listOf(
            FocusProfile.GAMING,
            FocusProfile.WORK,
            FocusProfile.SLEEP,
            FocusProfile.DO_NOT_DISTURB,
            FocusProfile.CINEMA
        )

        fun buildRealisticSeedSessions(nowMillis: Long = System.currentTimeMillis()): List<FocusSessionHistoryEntity> {
            val sessions = mutableListOf<FocusSessionHistoryEntity>()

            fun createSeed(
                daysAgo: Int,
                hourOfDay: Int,
                minute: Int,
                durationMinutes: Int,
                profile: FocusProfile,
                triggerSource: String,
                callsDeclined: Int,
                alertsSilenced: Int
            ): FocusSessionHistoryEntity {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, -daysAgo)
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endMs = cal.timeInMillis.coerceAtMost(nowMillis - (daysAgo * 3600_000L + 300_000L))
                val startMs = endMs - durationMinutes * 60_000L
                return FocusSessionHistoryEntity(
                    profileId = profile.id,
                    profileDisplayName = profile.displayName,
                    startTimeMillis = startMs,
                    endTimeMillis = endMs,
                    durationMinutes = durationMinutes,
                    triggerSource = triggerSource,
                    callsDeclined = callsDeclined,
                    alertsSilenced = alertsSilenced
                )
            }

            // Today (0 days ago)
            sessions += createSeed(0, 21, 15, 95, FocusProfile.GAMING, "Auto Game Detector", 2, 18)
            sessions += createSeed(0, 15, 30, 110, FocusProfile.WORK, "Scheduled", 1, 24)
            sessions += createSeed(0, 7, 0, 180, FocusProfile.SLEEP, "Scheduled", 0, 14)

            // Yesterday (1 day ago)
            sessions += createSeed(1, 22, 10, 80, FocusProfile.GAMING, "Quick Settings Tile", 3, 15)
            sessions += createSeed(1, 14, 0, 90, FocusProfile.WORK, "Scheduled", 1, 19)
            sessions += createSeed(1, 19, 30, 45, FocusProfile.DO_NOT_DISTURB, "Manual", 1, 8)
            sessions += createSeed(1, 6, 45, 165, FocusProfile.SLEEP, "Scheduled", 0, 11)

            // 2 days ago
            sessions += createSeed(2, 20, 45, 120, FocusProfile.CINEMA, "Manual", 2, 22)
            sessions += createSeed(2, 11, 30, 75, FocusProfile.WORK, "Scheduled", 0, 16)
            sessions += createSeed(2, 23, 15, 65, FocusProfile.GAMING, "Auto Game Detector", 1, 12)

            // 3 days ago
            sessions += createSeed(3, 16, 0, 105, FocusProfile.WORK, "Scheduled", 2, 21)
            sessions += createSeed(3, 21, 30, 70, FocusProfile.GAMING, "Auto Game Detector", 1, 14)
            sessions += createSeed(3, 7, 10, 150, FocusProfile.SLEEP, "Quick Settings Tile", 0, 9)

            // 4 days ago
            sessions += createSeed(4, 18, 20, 60, FocusProfile.DO_NOT_DISTURB, "Manual", 2, 11)
            sessions += createSeed(4, 13, 45, 85, FocusProfile.WORK, "Scheduled", 1, 17)
            sessions += createSeed(4, 22, 0, 55, FocusProfile.GAMING, "Auto Game Detector", 1, 10)

            // 5 days ago
            sessions += createSeed(5, 20, 0, 115, FocusProfile.GAMING, "Auto Game Detector", 3, 26)
            sessions += createSeed(5, 15, 0, 60, FocusProfile.DO_NOT_DISTURB, "Manual", 0, 7)
            sessions += createSeed(5, 7, 0, 170, FocusProfile.SLEEP, "Scheduled", 1, 13)

            // 6 days ago
            sessions += createSeed(6, 21, 0, 130, FocusProfile.CINEMA, "Manual", 1, 19)
            sessions += createSeed(6, 14, 30, 95, FocusProfile.GAMING, "Quick Settings Tile", 2, 16)

            return sessions.sortedByDescending { it.endTimeMillis }
        }

        fun aggregateByMode(sessions: List<FocusSessionHistoryEntity>): List<ModeTimeAllocation> {
            val totalMinutesAll = sessions.sumOf { it.durationMinutes }.coerceAtLeast(1)
            val grouped = sessions.groupBy { FocusProfile.fromId(it.profileId) }

            return TRACKED_MODES.map { profile ->
                val list = grouped[profile].orEmpty()
                val totalMins = list.sumOf { it.durationMinutes }
                val count = list.size
                val avgMins = if (count > 0) totalMins / count else 0
                val pct = if (sessions.isNotEmpty()) {
                    (totalMins.toFloat() / totalMinutesAll.toFloat()) * 100f
                } else {
                    0f
                }
                ModeTimeAllocation(
                    profile = profile,
                    totalMinutes = totalMins,
                    sessionCount = count,
                    averageSessionMinutes = avgMins,
                    percentageOfTotal = pct,
                    totalCallsDeclined = list.sumOf { it.callsDeclined },
                    totalAlertsSilenced = list.sumOf { it.alertsSilenced }
                )
            }
        }

        fun aggregateDailyBreakdown(
            sessions: List<FocusSessionHistoryEntity>,
            nowMillis: Long = System.currentTimeMillis()
        ): List<DailyFocusModeBarEntry> {
            val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val shortFormat = SimpleDateFormat("EEE", Locale.US)
            val fullFormat = SimpleDateFormat("EEE, MMM d", Locale.US)

            val sessionsByDayKey = sessions.groupBy { entity ->
                keyFormat.format(Date(entity.endTimeMillis))
            }

            return (6 downTo 0).mapIndexed { index, daysAgo ->
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, -daysAgo)
                }
                val dayDate = cal.time
                val dayKey = keyFormat.format(dayDate)
                val daySessions = sessionsByDayKey[dayKey].orEmpty()

                val minutesMap = TRACKED_MODES.associateWith { profile ->
                    daySessions
                        .filter { FocusProfile.fromId(it.profileId) == profile }
                        .sumOf { it.durationMinutes }
                }

                DailyFocusModeBarEntry(
                    dayIndex = index,
                    dayKey = dayKey,
                    dayShort = shortFormat.format(dayDate),
                    dayFull = fullFormat.format(dayDate),
                    isToday = daysAgo == 0,
                    minutesByProfile = minutesMap,
                    totalMinutes = daySessions.sumOf { it.durationMinutes },
                    sessionCount = daySessions.size
                )
            }
        }

        /**
         * Aggregates Room [FocusSessionHistoryEntity] entries for [FocusProfile.GAMING] across
         * This Week (0..6 days ago) and Last Week (7..13 days ago), merging any additional
         * per-day minutes so both Grouped Bar and Area Spline Line charts re-render reactively
         * whenever new focus data is inserted into Room.
         */
        fun aggregateGamingWeeklyComparison(
            sessions: List<FocusSessionHistoryEntity>,
            nowMillis: Long = System.currentTimeMillis(),
            extraMinutesByDateKey: Map<String, Int> = emptyMap()
        ): GamingWeeklyComparisonSummary {
            val dayFormat = SimpleDateFormat("EEE", Locale.US)
            val fullFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
            val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayKey = keyFormat.format(Date(nowMillis))

            val gamingMinutesByDayKey = sessions
                .filter { FocusProfile.fromId(it.profileId) == FocusProfile.GAMING }
                .groupBy { keyFormat.format(Date(it.endTimeMillis)) }
                .mapValues { (_, list) -> list.sumOf { it.durationMinutes } }

            val comparisonDays = mutableListOf<DailyGamingComparison>()
            var sumThisWeek = 0
            var sumLastWeek = 0
            var peakDayLabel = "None"
            var peakDayMins = 0

            for (i in 6 downTo 0) {
                val idx = 6 - i
                val thisWeekCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, -i)
                }
                val lastWeekCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, -(i + 7))
                }

                val keyThis = keyFormat.format(thisWeekCal.time)
                val keyLast = keyFormat.format(lastWeekCal.time)
                val isToday = (keyThis == todayKey)

                val roomThis = gamingMinutesByDayKey[keyThis] ?: 0
                val extraThis = extraMinutesByDateKey[keyThis] ?: 0
                val minsThis = maxOf(roomThis, extraThis)

                val roomLast = gamingMinutesByDayKey[keyLast] ?: 0
                val extraLast = extraMinutesByDateKey[keyLast] ?: 0
                val minsLast = maxOf(roomLast, extraLast)

                val dayShort = dayFormat.format(thisWeekCal.time)
                val dayFullThis = fullFormat.format(thisWeekCal.time)
                val dayFullLast = fullFormat.format(lastWeekCal.time)

                comparisonDays.add(
                    DailyGamingComparison(
                        dayIndex = idx,
                        dayKeyThisWeek = keyThis,
                        dayKeyLastWeek = keyLast,
                        dayShort = dayShort,
                        dayFullThisWeek = dayFullThis,
                        dayFullLastWeek = dayFullLast,
                        isToday = isToday,
                        thisWeekMinutes = minsThis,
                        lastWeekMinutes = minsLast
                    )
                )

                sumThisWeek += minsThis
                sumLastWeek += minsLast
                if (minsThis > peakDayMins) {
                    peakDayMins = minsThis
                    peakDayLabel = dayShort
                }
            }

            val deltaMins = sumThisWeek - sumLastWeek
            val deltaPct = if (sumLastWeek > 0) {
                (deltaMins.toFloat() / sumLastWeek.toFloat()) * 100f
            } else if (sumThisWeek > 0) {
                100f
            } else {
                0f
            }

            return GamingWeeklyComparisonSummary(
                days = comparisonDays,
                thisWeekTotalMinutes = sumThisWeek,
                lastWeekTotalMinutes = sumLastWeek,
                deltaMinutes = deltaMins,
                deltaPercentage = deltaPct,
                peakDayThisWeekLabel = peakDayLabel,
                peakDayThisWeekMinutes = peakDayMins.coerceAtLeast(0),
                averageDailyMinutesThisWeek = sumThisWeek / 7f
            )
        }

        /**
         * Aggregates Room [FocusSessionHistoryEntity] silenced alerts and declined calls across
         * the last 7 days, merging any standalone daily counters so the Weekly Shield Dual-Bar
         * and Smooth Area Trend Line chart re-renders whenever new focus data is inserted into Room.
         */
        fun aggregateWeeklyShieldSummary(
            sessions: List<FocusSessionHistoryEntity>,
            nowMillis: Long = System.currentTimeMillis(),
            extraNotifsByDateKey: Map<String, Int> = emptyMap(),
            extraCallsByDateKey: Map<String, Int> = emptyMap()
        ): WeeklyShieldSummary {
            val dayFormat = SimpleDateFormat("EEE", Locale.US)
            val fullFormat = SimpleDateFormat("EEEE, MMM d", Locale.US)
            val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayKey = keyFormat.format(Date(nowMillis))

            val sessionsByDayKey = sessions.groupBy { keyFormat.format(Date(it.endTimeMillis)) }

            val daysList = mutableListOf<DailyShieldStats>()
            var sumNotifs = 0
            var sumCalls = 0
            var maxTotal = 0
            var peakDay = "None"

            for (i in 6 downTo 0) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, -i)
                }
                val key = keyFormat.format(cal.time)
                val isToday = (key == todayKey)

                val daySessions = sessionsByDayKey[key].orEmpty()
                val roomNotifs = daySessions.sumOf { it.alertsSilenced }
                val roomCalls = daySessions.sumOf { it.callsDeclined }

                val notifs = roomNotifs + (extraNotifsByDateKey[key] ?: 0)
                val calls = roomCalls + (extraCallsByDateKey[key] ?: 0)

                val dayShort = dayFormat.format(cal.time)
                val dayFull = fullFormat.format(cal.time)

                daysList.add(
                    DailyShieldStats(
                        dayKey = key,
                        dayShort = dayShort,
                        dayFull = dayFull,
                        isToday = isToday,
                        silencedNotifications = notifs,
                        declinedCalls = calls
                    )
                )

                sumNotifs += notifs
                sumCalls += calls
                val total = notifs + calls
                if (total > maxTotal) {
                    maxTotal = total
                    peakDay = dayShort
                }
            }

            return WeeklyShieldSummary(
                days = daysList,
                totalSilencedNotifications = sumNotifs,
                totalDeclinedCalls = sumCalls,
                peakDayLabel = peakDay,
                averageSilencedPerDay = sumNotifs / 7f,
                averageCallsPerDay = sumCalls / 7f
            )
        }
    }
}

