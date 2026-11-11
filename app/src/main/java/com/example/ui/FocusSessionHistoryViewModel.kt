package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.DailyFocusModeBarEntry
import com.example.core.FocusProfile
import com.example.core.FocusSessionHistoryEntity
import com.example.core.FocusSessionHistoryRepository
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.core.GamingWeeklyComparisonSummary
import com.example.core.ModeTimeAllocation
import com.example.core.WeeklyShieldSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class FocusHistoryChartViewMode {
    BY_MODE,
    DAILY_TIMELINE
}

data class FocusSessionHistoryUiState(
    val allSessions: List<FocusSessionHistoryEntity> = emptyList(),
    val filteredSessions: List<FocusSessionHistoryEntity> = emptyList(),
    val modeAllocations: List<ModeTimeAllocation> = emptyList(),
    val dailyBreakdown: List<DailyFocusModeBarEntry> = emptyList(),
    val weeklyShieldSummary: WeeklyShieldSummary = WeeklyShieldSummary(),
    val gamingWeeklyComparison: GamingWeeklyComparisonSummary = GamingWeeklyComparisonSummary(),
    val selectedFilterProfile: FocusProfile? = null,
    val chartViewMode: FocusHistoryChartViewMode = FocusHistoryChartViewMode.BY_MODE,
    val showTrendLineOverlay: Boolean = true,
    val selectedBarIndex: Int = 0,
    val totalMinutesAllModes: Int = 0,
    val totalSessionsCount: Int = 0,
    val topMode: FocusProfile = FocusProfile.GAMING,
    val topModeMinutes: Int = 0,
    val averageSessionMinutes: Int = 0
)

private data class ChartControlsState(
    val filterProfile: FocusProfile?,
    val chartMode: FocusHistoryChartViewMode,
    val showTrendLine: Boolean,
    val barIndex: Int,
    val nowMillis: Long
)

class FocusSessionHistoryViewModel(
    private val repository: FocusSessionHistoryRepository,
    private val stateManager: FocusStateManager? = null
) : ViewModel() {

    private val _selectedFilterProfile = MutableStateFlow<FocusProfile?>(null)
    private val _chartViewMode = MutableStateFlow(FocusHistoryChartViewMode.BY_MODE)
    private val _showTrendLineOverlay = MutableStateFlow(true)
    private val _selectedBarIndex = MutableStateFlow(0)
    private val _liveClockTick = MutableStateFlow(System.currentTimeMillis())

    init {
        // Periodically refresh live elapsed minutes when a Focus Mode session is actively running
        viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                if (stateManager?.state?.value?.activeProfile != FocusProfile.OFF) {
                    _liveClockTick.value = System.currentTimeMillis()
                }
            }
        }
    }

    private val activeSessionFlow = stateManager?.state ?: flowOf(FocusSessionState())
    private val standaloneWeeklyFlow = stateManager?.weeklyStatsFlow ?: flowOf(WeeklyShieldSummary())
    private val standaloneGamingFlow = stateManager?.gamingWeeklyComparisonFlow ?: flowOf(GamingWeeklyComparisonSummary())

    private val controlsFlow = combine(
        _selectedFilterProfile,
        _chartViewMode,
        _showTrendLineOverlay,
        _selectedBarIndex,
        _liveClockTick
    ) { filter, mode, showLine, barIdx, tick ->
        ChartControlsState(
            filterProfile = filter,
            chartMode = mode,
            showTrendLine = showLine,
            barIndex = barIdx,
            nowMillis = tick
        )
    }

    val uiState: StateFlow<FocusSessionHistoryUiState> = combine(
        repository.allSessionsFlow,
        activeSessionFlow,
        controlsFlow,
        combine(standaloneWeeklyFlow, standaloneGamingFlow) { w, g -> w to g }
    ) { dbSessions, liveSessionState, controls, _ ->
        val currentMillis = maxOf(controls.nowMillis, System.currentTimeMillis())
        val activeEntity = if (liveSessionState.activeProfile != FocusProfile.OFF) {
            val startedAt = liveSessionState.sessionStartTimeMillis ?: currentMillis
            val liveMinutes = ((currentMillis - startedAt) / 60_000L).toInt().coerceAtLeast(1)
            FocusSessionHistoryEntity(
                id = -1L,
                profileId = liveSessionState.activeProfile.id,
                profileDisplayName = liveSessionState.activeProfile.displayName,
                startTimeMillis = startedAt,
                endTimeMillis = currentMillis,
                durationMinutes = liveMinutes,
                triggerSource = "Active Now",
                callsDeclined = 0,
                alertsSilenced = 0
            )
        } else {
            null
        }

        val sessions = if (activeEntity != null) {
            listOf(activeEntity) + dbSessions
        } else {
            dbSessions
        }

        val modeAllocations = FocusSessionHistoryRepository.aggregateByMode(sessions)
        val dailyBreakdown = FocusSessionHistoryRepository.aggregateDailyBreakdown(sessions, currentMillis)

        val extraNotifsMap = stateManager?.getDailyStandaloneNotificationsMap(currentMillis) ?: emptyMap()
        val extraCallsMap = stateManager?.getDailyStandaloneCallsMap(currentMillis) ?: emptyMap()
        val extraGamingMap = stateManager?.getDailyExtraGamingMinutesMap(currentMillis) ?: emptyMap()

        val weeklyShieldSummary = FocusSessionHistoryRepository.aggregateWeeklyShieldSummary(
            sessions = sessions,
            nowMillis = currentMillis,
            extraNotifsByDateKey = extraNotifsMap,
            extraCallsByDateKey = extraCallsMap
        )

        val gamingWeeklyComparison = FocusSessionHistoryRepository.aggregateGamingWeeklyComparison(
            sessions = sessions,
            nowMillis = currentMillis,
            extraMinutesByDateKey = extraGamingMap
        )

        val filtered = if (controls.filterProfile == null) {
            sessions
        } else {
            sessions.filter { FocusProfile.fromId(it.profileId) == controls.filterProfile }
        }

        val totalMins = sessions.sumOf { it.durationMinutes }
        val totalCount = sessions.size
        val avgMins = if (totalCount > 0) totalMins / totalCount else 0
        val topEntry = modeAllocations.maxByOrNull { it.totalMinutes }

        val maxIndex = when (controls.chartMode) {
            FocusHistoryChartViewMode.BY_MODE -> (modeAllocations.size - 1).coerceAtLeast(0)
            FocusHistoryChartViewMode.DAILY_TIMELINE -> (dailyBreakdown.size - 1).coerceAtLeast(0)
        }
        val safeBarIndex = controls.barIndex.coerceIn(0, maxIndex)

        FocusSessionHistoryUiState(
            allSessions = sessions,
            filteredSessions = filtered,
            modeAllocations = modeAllocations,
            dailyBreakdown = dailyBreakdown,
            weeklyShieldSummary = weeklyShieldSummary,
            gamingWeeklyComparison = gamingWeeklyComparison,
            selectedFilterProfile = controls.filterProfile,
            chartViewMode = controls.chartMode,
            showTrendLineOverlay = controls.showTrendLine,
            selectedBarIndex = safeBarIndex,
            totalMinutesAllModes = totalMins,
            totalSessionsCount = totalCount,
            topMode = if ((topEntry?.totalMinutes ?: 0) > 0) {
                topEntry!!.profile
            } else {
                liveSessionState.activeProfile.takeIf { it != FocusProfile.OFF } ?: FocusProfile.GAMING
            },
            topModeMinutes = topEntry?.totalMinutes ?: 0,
            averageSessionMinutes = avgMins
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FocusSessionHistoryUiState(
            modeAllocations = FocusSessionHistoryRepository.aggregateByMode(emptyList()),
            dailyBreakdown = FocusSessionHistoryRepository.aggregateDailyBreakdown(emptyList()),
            weeklyShieldSummary = FocusSessionHistoryRepository.aggregateWeeklyShieldSummary(emptyList()),
            gamingWeeklyComparison = FocusSessionHistoryRepository.aggregateGamingWeeklyComparison(emptyList())
        )
    )

    val weeklyShieldSummaryFlow: StateFlow<WeeklyShieldSummary> = uiState
        .map { it.weeklyShieldSummary }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = uiState.value.weeklyShieldSummary
        )

    val gamingWeeklyComparisonFlow: StateFlow<GamingWeeklyComparisonSummary> = uiState
        .map { it.gamingWeeklyComparison }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = uiState.value.gamingWeeklyComparison
        )

    fun setFilterProfile(profile: FocusProfile?) {
        _selectedFilterProfile.value = profile
        if (profile != null && _chartViewMode.value == FocusHistoryChartViewMode.BY_MODE) {
            val idx = FocusSessionHistoryRepository.TRACKED_MODES.indexOf(profile)
            if (idx >= 0) {
                _selectedBarIndex.value = idx
            }
        }
    }

    fun setChartViewMode(mode: FocusHistoryChartViewMode) {
        _chartViewMode.value = mode
        _selectedBarIndex.value = when (mode) {
            FocusHistoryChartViewMode.BY_MODE -> 0
            FocusHistoryChartViewMode.DAILY_TIMELINE -> 6 // Today
        }
    }

    fun setShowTrendLineOverlay(enabled: Boolean) {
        _showTrendLineOverlay.value = enabled
    }

    fun toggleTrendLineOverlay() {
        _showTrendLineOverlay.value = !_showTrendLineOverlay.value
    }

    fun selectBarIndex(index: Int) {
        _selectedBarIndex.value = index
    }

    fun logQuickSession(
        profile: FocusProfile,
        durationMinutes: Int = 30,
        triggerSource: String = "Manual Log",
        callsDeclined: Int = 0,
        alertsSilenced: Int = 0
    ) {
        if (profile == FocusProfile.OFF) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val start = now - durationMinutes * 60_000L
            repository.logSession(
                profile = profile,
                startTimeMillis = start,
                endTimeMillis = now,
                durationMinutesOverride = durationMinutes,
                triggerSource = triggerSource,
                callsDeclined = callsDeclined,
                alertsSilenced = alertsSilenced
            )
        }
    }

    fun deleteSession(sessionId: Long) {
        if (sessionId <= 0L) return
        viewModelScope.launch {
            repository.deleteSessionById(sessionId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllSessions()
        }
    }

    fun restoreDemoHistory() {
        viewModelScope.launch {
            repository.clearAllSessions()
        }
    }

    class Factory(
        private val repository: FocusSessionHistoryRepository,
        private val stateManager: FocusStateManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FocusSessionHistoryViewModel::class.java)) {
                return FocusSessionHistoryViewModel(repository, stateManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}


