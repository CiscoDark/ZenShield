package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.DailyFocusModeBarEntry
import com.example.core.FocusProfile
import com.example.core.FocusSessionHistoryEntity
import com.example.core.FocusSessionHistoryRepository
import com.example.core.ModeTimeAllocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val selectedFilterProfile: FocusProfile? = null,
    val chartViewMode: FocusHistoryChartViewMode = FocusHistoryChartViewMode.BY_MODE,
    val selectedBarIndex: Int = 0,
    val totalMinutesAllModes: Int = 0,
    val totalSessionsCount: Int = 0,
    val topMode: FocusProfile = FocusProfile.GAMING,
    val topModeMinutes: Int = 0,
    val averageSessionMinutes: Int = 0
)

class FocusSessionHistoryViewModel(
    private val repository: FocusSessionHistoryRepository
) : ViewModel() {

    private val _selectedFilterProfile = MutableStateFlow<FocusProfile?>(null)
    private val _chartViewMode = MutableStateFlow(FocusHistoryChartViewMode.BY_MODE)
    private val _selectedBarIndex = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            repository.seedInitialSessionsIfEmpty()
        }
    }

    val uiState: StateFlow<FocusSessionHistoryUiState> = combine(
        repository.allSessionsFlow,
        _selectedFilterProfile,
        _chartViewMode,
        _selectedBarIndex
    ) { sessions, filterProfile, chartMode, barIndex ->
        val modeAllocations = FocusSessionHistoryRepository.aggregateByMode(sessions)
        val dailyBreakdown = FocusSessionHistoryRepository.aggregateDailyBreakdown(sessions)

        val filtered = if (filterProfile == null) {
            sessions
        } else {
            sessions.filter { FocusProfile.fromId(it.profileId) == filterProfile }
        }

        val totalMins = sessions.sumOf { it.durationMinutes }
        val totalCount = sessions.size
        val avgMins = if (totalCount > 0) totalMins / totalCount else 0
        val topEntry = modeAllocations.maxByOrNull { it.totalMinutes }

        val maxIndex = when (chartMode) {
            FocusHistoryChartViewMode.BY_MODE -> (modeAllocations.size - 1).coerceAtLeast(0)
            FocusHistoryChartViewMode.DAILY_TIMELINE -> (dailyBreakdown.size - 1).coerceAtLeast(0)
        }
        val safeBarIndex = barIndex.coerceIn(0, maxIndex)

        FocusSessionHistoryUiState(
            allSessions = sessions,
            filteredSessions = filtered,
            modeAllocations = modeAllocations,
            dailyBreakdown = dailyBreakdown,
            selectedFilterProfile = filterProfile,
            chartViewMode = chartMode,
            selectedBarIndex = safeBarIndex,
            totalMinutesAllModes = totalMins,
            totalSessionsCount = totalCount,
            topMode = topEntry?.profile ?: FocusProfile.GAMING,
            topModeMinutes = topEntry?.totalMinutes ?: 0,
            averageSessionMinutes = avgMins
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FocusSessionHistoryUiState()
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

    fun selectBarIndex(index: Int) {
        _selectedBarIndex.value = index
    }

    fun logQuickSession(
        profile: FocusProfile,
        durationMinutes: Int = 30,
        triggerSource: String = "Quick Log"
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
                callsDeclined = 1,
                alertsSilenced = (durationMinutes / 5).coerceAtLeast(2)
            )
        }
    }

    fun deleteSession(sessionId: Long) {
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
            repository.resetToDefaultSeedSessions()
        }
    }

    class Factory(
        private val repository: FocusSessionHistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FocusSessionHistoryViewModel::class.java)) {
                return FocusSessionHistoryViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
