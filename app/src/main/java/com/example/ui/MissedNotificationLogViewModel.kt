package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AiDigestScope
import com.example.core.AiNotificationDigest
import com.example.core.AiNotificationDigestRepository
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import com.example.core.GeminiModelPreset
import com.example.core.GlobalFocusRuleAction
import com.example.core.MissedNotificationEntity
import com.example.core.NotificationCategory
import com.example.core.NotificationPriorityLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sealed UI state for AI Executive Notification Digest generation via user BYOK Gemini API key.
 */
sealed interface AiDigestUiState {
    data object Idle : AiDigestUiState
    data object Loading : AiDigestUiState
    data class Success(val digest: AiNotificationDigest) : AiDigestUiState
    data class Error(val message: String) : AiDigestUiState
}

/**
 * Sealed UI state for live BYOK Gemini API key verification.
 */
sealed interface ByokKeyVerificationState {
    data object Idle : ByokKeyVerificationState
    data object Verifying : ByokKeyVerificationState
    data class Verified(val message: String) : ByokKeyVerificationState
    data class Invalid(val errorMessage: String) : ByokKeyVerificationState
}

/**
 * Combined filter and search state for the Missed Notification & Call Log.
 */
data class MissedLogFilterState(
    val searchQuery: String = "",
    val selectedFocusProfile: FocusProfile? = null,
    val selectedPriorityLevel: NotificationPriorityLevel? = null,
    val selectedAppFilter: String? = null,
    val showUnreadOnly: Boolean = false
)

/**
 * Reactive ViewModel that manages the persistent Room Missed Notification Log,
 * search/filter pipelines, BYOK Gemini API key & model preset configuration,
 * and strictly BYOK Executive Digest generation.
 */
class MissedNotificationLogViewModel(
    private val repository: AiNotificationDigestRepository,
    private val focusStateManager: FocusStateManager? = null
) : ViewModel() {

    companion object {
        const val APP_FILTER_CALLS_ONLY = "__CALLS_ONLY__"
    }

    private val _filterState = MutableStateFlow(MissedLogFilterState())
    val filterState: StateFlow<MissedLogFilterState> = _filterState.asStateFlow()

    private val _selectedDigestScope = MutableStateFlow(AiDigestScope.UNREAD_ONLY)
    val selectedDigestScope: StateFlow<AiDigestScope> = _selectedDigestScope.asStateFlow()

    private val _digestUiState = MutableStateFlow<AiDigestUiState>(AiDigestUiState.Idle)
    val digestUiState: StateFlow<AiDigestUiState> = _digestUiState.asStateFlow()

    private val _verificationState = MutableStateFlow<ByokKeyVerificationState>(ByokKeyVerificationState.Idle)
    val verificationState: StateFlow<ByokKeyVerificationState> = _verificationState.asStateFlow()

    val allMissedNotifications: StateFlow<List<MissedNotificationEntity>> =
        repository.allMissedNotificationsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val unreadMissedNotifications: StateFlow<List<MissedNotificationEntity>> =
        repository.unreadMissedNotificationsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val latestDigest: StateFlow<AiNotificationDigest?> =
        repository.latestDigestFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val availableApps: StateFlow<List<Pair<String, String>>> =
        combine(allMissedNotifications) { (items) ->
            items
                .filter { !it.isCallBounce }
                .map { it.packageName to it.appName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val filteredMissedNotifications: StateFlow<List<MissedNotificationEntity>> =
        combine(allMissedNotifications, _filterState) { items, filter ->
            filterMissedNotifications(items, filter)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    init {
        // Observe Focus session completion triggers for automatic AI Executive Digest generation
        viewModelScope.launch {
            repository.sessionEndDigestTriggers.collect { trigger ->
                generateAiDigestForSessionEnd(
                    endedProfile = trigger.endedProfile,
                    sessionStartTimeMillis = trigger.sessionStartTimeMillis
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun selectFocusProfileFilter(profile: FocusProfile?) {
        val current = _filterState.value.selectedFocusProfile
        _filterState.value = _filterState.value.copy(
            selectedFocusProfile = if (current == profile) null else profile
        )
    }

    fun selectPriorityLevelFilter(level: NotificationPriorityLevel?) {
        val current = _filterState.value.selectedPriorityLevel
        _filterState.value = _filterState.value.copy(
            selectedPriorityLevel = if (current == level) null else level
        )
    }

    fun selectAppFilter(packageOrFilterKey: String?) {
        val current = _filterState.value.selectedAppFilter
        _filterState.value = _filterState.value.copy(
            selectedAppFilter = if (current == packageOrFilterKey) null else packageOrFilterKey
        )
    }

    fun setShowUnreadOnly(unreadOnly: Boolean) {
        _filterState.value = _filterState.value.copy(showUnreadOnly = unreadOnly)
    }

    fun clearAllFilters() {
        _filterState.value = MissedLogFilterState()
    }

    fun selectDigestScope(scope: AiDigestScope) {
        _selectedDigestScope.value = scope
    }

    fun setAutoSummarizeOnSessionEnd(enabled: Boolean) {
        focusStateManager?.setAutoSummarizeMissedOnSessionEnd(enabled)
    }

    fun saveUserApiKey(rawKey: String) {
        val cleaned = rawKey.trim()
        focusStateManager?.setUserGeminiApiKey(cleaned)
        if (cleaned.isNotBlank()) {
            _verificationState.value = ByokKeyVerificationState.Verified(
                "Personal BYOK Gemini API key saved (${AiNotificationDigestRepository.maskUserApiKey(cleaned)})"
            )
            if (_digestUiState.value is AiDigestUiState.Error) {
                _digestUiState.value = AiDigestUiState.Idle
            }
        } else {
            _verificationState.value = ByokKeyVerificationState.Invalid("Please enter a non-empty Gemini API key.")
        }
    }

    fun clearUserApiKey() {
        focusStateManager?.clearUserGeminiApiKey()
        _verificationState.value = ByokKeyVerificationState.Idle
    }

    fun selectGeminiModelPreset(preset: GeminiModelPreset) {
        focusStateManager?.setSelectedGeminiModelPreset(preset)
        if (_verificationState.value is ByokKeyVerificationState.Verified) {
            _verificationState.value = ByokKeyVerificationState.Idle
        }
    }

    fun verifyUserApiKey(rawKeyCandidate: String? = null) {
        val effectiveKey = rawKeyCandidate?.trim()?.takeIf { it.isNotBlank() }
            ?: focusStateManager?.state?.value?.userGeminiApiKey.orEmpty()
        val preset = focusStateManager?.state?.value?.selectedGeminiModelPreset
            ?: GeminiModelPreset.GEMINI_2_5_FLASH

        if (effectiveKey.isBlank()) {
            _verificationState.value = ByokKeyVerificationState.Invalid(
                "Enter your personal Gemini API key before tapping Verify Key."
            )
            return
        }

        // Persist the candidate key when verifying
        focusStateManager?.setUserGeminiApiKey(effectiveKey)

        viewModelScope.launch {
            _verificationState.value = ByokKeyVerificationState.Verifying
            try {
                val result = repository.verifyUserGeminiApiKey(
                    userApiKey = effectiveKey,
                    modelPreset = preset
                )
                result.fold(
                    onSuccess = { msg ->
                        _verificationState.value = ByokKeyVerificationState.Verified(msg)
                        if (_digestUiState.value is AiDigestUiState.Error) {
                            _digestUiState.value = AiDigestUiState.Idle
                        }
                    },
                    onFailure = { err ->
                        _verificationState.value = ByokKeyVerificationState.Invalid(
                            err.localizedMessage ?: "Gemini API key verification failed."
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _verificationState.value = ByokKeyVerificationState.Invalid(
                    e.localizedMessage ?: "Gemini API key verification failed."
                )
            }
        }
    }

    /**
     * Generates an on-demand AI Executive Digest strictly using the user's BYOK Gemini API key.
     */
    fun generateAiDigest(scope: AiDigestScope = _selectedDigestScope.value) {
        val userApiKey = focusStateManager?.state?.value?.userGeminiApiKey?.trim().orEmpty()
        val modelPreset = focusStateManager?.state?.value?.selectedGeminiModelPreset
            ?: GeminiModelPreset.GEMINI_2_5_FLASH

        if (userApiKey.isBlank()) {
            _digestUiState.value = AiDigestUiState.Error(
                "BYOK Gemini API Key Required: Enter and save your personal Gemini API key in the BYOK Key Vault above to generate AI summaries."
            )
            return
        }

        viewModelScope.launch {
            _digestUiState.value = AiDigestUiState.Loading
            try {
                val allItems = repository.getAllMissedNotificationsSnapshot()
                val targetItems: List<MissedNotificationEntity> = when (scope) {
                    AiDigestScope.UNREAD_ONLY -> {
                        val unread = allItems.filter { !it.isReviewed }
                        if (unread.isNotEmpty()) unread else allItems.take(25)
                    }
                    AiDigestScope.LAST_SESSION -> {
                        val latestProfileId = allItems.firstOrNull()?.focusProfileId
                        if (latestProfileId != null) {
                            val cutoff = (allItems.firstOrNull()?.timestampMillis ?: 0L) - 4 * 3600_000L
                            val sessionMatches = allItems.filter {
                                it.focusProfileId == latestProfileId && it.timestampMillis >= cutoff
                            }
                            if (sessionMatches.isNotEmpty()) sessionMatches else allItems.take(20)
                        } else {
                            allItems.take(20)
                        }
                    }
                    AiDigestScope.ALL_RECENT -> {
                        filterMissedNotifications(allItems, _filterState.value).take(35)
                    }
                }

                if (targetItems.isEmpty()) {
                    _digestUiState.value = AiDigestUiState.Error(
                        "No missed notifications or bounced calls found to summarize yet. Shield an incoming alert or tap '+5 Alerts' first."
                    )
                    return@launch
                }

                val dominantProfileDisplay = targetItems
                    .groupBy { it.focusProfileDisplayName }
                    .maxByOrNull { it.value.size }
                    ?.key ?: (focusStateManager?.state?.value?.activeProfile
                    ?.takeIf { it != FocusProfile.OFF }?.displayName ?: FocusProfile.WORK.displayName)

                val result = repository.generateExecutiveDigest(
                    items = targetItems,
                    scopeLabel = scope.label,
                    focusProfileDisplayName = dominantProfileDisplay,
                    userApiKey = userApiKey,
                    modelPreset = modelPreset
                )
                result.fold(
                    onSuccess = { digest ->
                        if (scope == AiDigestScope.UNREAD_ONLY) {
                            repository.markAllReviewed()
                        }
                        _digestUiState.value = AiDigestUiState.Success(digest)
                    },
                    onFailure = { error ->
                        _digestUiState.value = AiDigestUiState.Error(
                            error.localizedMessage ?: "Failed to generate AI Executive Digest. Check your BYOK Gemini API key."
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _digestUiState.value = AiDigestUiState.Error(
                    e.localizedMessage ?: "Failed to generate AI Executive Digest. Check your BYOK Gemini API key."
                )
            }
        }
    }

    private suspend fun generateAiDigestForSessionEnd(
        endedProfile: FocusProfile,
        sessionStartTimeMillis: Long
    ) {
        val userApiKey = focusStateManager?.state?.value?.userGeminiApiKey?.trim().orEmpty()
        val modelPreset = focusStateManager?.state?.value?.selectedGeminiModelPreset
            ?: GeminiModelPreset.GEMINI_2_5_FLASH

        if (userApiKey.isBlank()) {
            // Skip silent background call if user hasn't provided a BYOK key yet
            return
        }

        val sinceItems = repository.getMissedNotificationsSince(
            sinceMillis = (sessionStartTimeMillis - 5_000L).coerceAtLeast(0L)
        )
        val sessionItems = if (sinceItems.isNotEmpty()) {
            sinceItems
        } else {
            repository.getAllMissedNotificationsSnapshot().filter { !it.isReviewed }.take(20)
        }

        if (sessionItems.isEmpty()) return

        _digestUiState.value = AiDigestUiState.Loading
        try {
            val result = repository.generateExecutiveDigest(
                items = sessionItems,
                scopeLabel = "Session End (${endedProfile.displayName})",
                focusProfileDisplayName = endedProfile.displayName,
                userApiKey = userApiKey,
                modelPreset = modelPreset
            )
            result.fold(
                onSuccess = { digest ->
                    repository.markAllReviewed()
                    _digestUiState.value = AiDigestUiState.Success(digest)
                },
                onFailure = { error ->
                    _digestUiState.value = AiDigestUiState.Error(
                        error.localizedMessage ?: "Auto-summary after Focus session failed. Tap Retry to summarize."
                    )
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _digestUiState.value = AiDigestUiState.Error(
                e.localizedMessage ?: "Auto-summary after Focus session failed. Tap Retry to summarize."
            )
        }
    }

    fun markReviewed(id: Long) {
        viewModelScope.launch {
            repository.markReviewed(id, true)
        }
    }

    fun markAllReviewed() {
        viewModelScope.launch {
            repository.markAllReviewed()
        }
    }

    fun deleteMissedItem(id: Long) {
        viewModelScope.launch {
            repository.deleteMissedNotification(id)
        }
    }

    fun clearAllMissedItems() {
        viewModelScope.launch {
            repository.clearAllMissedNotifications()
            _digestUiState.value = AiDigestUiState.Idle
        }
    }

    /**
     * Logs a realistic batch of intercepted notifications and bounced calls into Room
     * so the user can immediately test search/filtering and BYOK AI Executive Digest summarization.
     */
    fun logSampleMissedNotificationsBatch() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val activeProfile = focusStateManager?.state?.value?.activeProfile
                ?.takeIf { it != FocusProfile.OFF } ?: FocusProfile.WORK

            repository.logMissedNotificationOrCall(
                focusProfile = activeProfile,
                sessionStartTimeMillis = now - 30 * 60_000L,
                appName = "Slack",
                packageName = "com.slack",
                senderOrTitle = "Priya Nair (#prod-release)",
                messagePreview = "Can you approve the hotfix PR #482 before 4:30 PM? Deployment pipeline is waiting on your sign-off.",
                category = NotificationCategory.WORK_AND_CALENDAR,
                priorityLevel = NotificationPriorityLevel.HIGH,
                appliedAction = GlobalFocusRuleAction.MUTE_SILENT,
                isCallBounce = false,
                timestampMillis = now - 14 * 60_000L
            )
            repository.logMissedNotificationOrCall(
                focusProfile = activeProfile,
                sessionStartTimeMillis = now - 30 * 60_000L,
                appName = "Call Bouncer",
                packageName = "com.android.server.telecom",
                senderOrTitle = "+1 (415) 891-4420",
                messagePreview = "1st call attempt bounced. Repeat-caller cooldown window active (180s).",
                category = NotificationCategory.SECURITY_AND_URGENT,
                priorityLevel = NotificationPriorityLevel.HIGH,
                appliedAction = GlobalFocusRuleAction.MUTE_SILENT,
                isCallBounce = true,
                timestampMillis = now - 11 * 60_000L
            )
            repository.logMissedNotificationOrCall(
                focusProfile = activeProfile,
                sessionStartTimeMillis = now - 30 * 60_000L,
                appName = "Gmail",
                packageName = "com.google.android.gm",
                senderOrTitle = "Marcus Vance — Q3 Board Deck",
                messagePreview = "Updated the revenue slides for tomorrow's 9 AM review. Please verify slide 7 metrics when you finish your focus block.",
                category = NotificationCategory.WORK_AND_CALENDAR,
                priorityLevel = NotificationPriorityLevel.HIGH,
                appliedAction = GlobalFocusRuleAction.MUTE_SILENT,
                isCallBounce = false,
                timestampMillis = now - 8 * 60_000L
            )
            repository.logMissedNotificationOrCall(
                focusProfile = FocusProfile.GAMING,
                sessionStartTimeMillis = now - 30 * 60_000L,
                appName = "WhatsApp",
                packageName = "com.whatsapp",
                senderOrTitle = "Alex Rivera",
                messagePreview = "Hey! Are we still on for dinner at 7:30 PM tonight? Let me know so I can confirm the reservation.",
                category = NotificationCategory.DIRECT_MESSAGES,
                priorityLevel = NotificationPriorityLevel.HIGH,
                appliedAction = GlobalFocusRuleAction.HIDE_COMPLETELY,
                isCallBounce = false,
                timestampMillis = now - 5 * 60_000L
            )
            repository.logMissedNotificationOrCall(
                focusProfile = FocusProfile.DO_NOT_DISTURB,
                sessionStartTimeMillis = now - 30 * 60_000L,
                appName = "Discord",
                packageName = "com.discord",
                senderOrTitle = "Apex Squad Lobby",
                messagePreview = "3 new messages in #ranked-grind: 'Jumping on in 20 mins'",
                category = NotificationCategory.SOCIAL_AND_COMMUNITY,
                priorityLevel = NotificationPriorityLevel.MEDIUM,
                appliedAction = GlobalFocusRuleAction.HIDE_COMPLETELY,
                isCallBounce = false,
                timestampMillis = now - 2 * 60_000L
            )
        }
    }

    class Factory(
        private val repository: AiNotificationDigestRepository,
        private val focusStateManager: FocusStateManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MissedNotificationLogViewModel::class.java)) {
                return MissedNotificationLogViewModel(repository, focusStateManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

/**
 * Pure filtering helper for unit testing and reactive StateFlow combination.
 */
internal fun filterMissedNotifications(
    items: List<MissedNotificationEntity>,
    filter: MissedLogFilterState
): List<MissedNotificationEntity> {
    val normalizedQuery = filter.searchQuery.trim().lowercase()
    return items.filter { item ->
        val matchesProfile = filter.selectedFocusProfile == null ||
            item.focusProfileId.equals(filter.selectedFocusProfile.id, ignoreCase = true) ||
            item.focusProfileDisplayName.equals(filter.selectedFocusProfile.displayName, ignoreCase = true) ||
            item.focusProfileId.equals(filter.selectedFocusProfile.name, ignoreCase = true)

        val matchesPriority = filter.selectedPriorityLevel == null ||
            item.priorityLevelName.equals(filter.selectedPriorityLevel.name, ignoreCase = true)

        val matchesApp = when (filter.selectedAppFilter) {
            null -> true
            MissedNotificationLogViewModel.APP_FILTER_CALLS_ONLY -> item.isCallBounce
            else -> item.packageName.equals(filter.selectedAppFilter, ignoreCase = true) ||
                item.appName.equals(filter.selectedAppFilter, ignoreCase = true)
        }

        val matchesUnread = !filter.showUnreadOnly || !item.isReviewed

        val matchesQuery = normalizedQuery.isEmpty() ||
            item.senderOrTitle.lowercase().contains(normalizedQuery) ||
            item.messagePreview.lowercase().contains(normalizedQuery) ||
            item.appName.lowercase().contains(normalizedQuery) ||
            item.packageName.lowercase().contains(normalizedQuery) ||
            item.focusProfileDisplayName.lowercase().contains(normalizedQuery) ||
            item.categoryName.lowercase().contains(normalizedQuery)

        matchesProfile && matchesPriority && matchesApp && matchesUnread && matchesQuery
    }
}
