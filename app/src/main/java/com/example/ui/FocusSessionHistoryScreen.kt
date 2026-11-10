package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.DailyFocusModeBarEntry
import com.example.core.FocusProfile
import com.example.core.FocusSessionHistoryEntity
import com.example.core.FocusSessionHistoryRepository
import com.example.core.FocusStateManager
import com.example.core.ModeTimeAllocation
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Distinct Recharts-inspired palette color per FocusProfile.
 */
fun focusModeBarColor(profile: FocusProfile): Color = when (profile) {
    FocusProfile.GAMING -> Color(0xFF38BDF8)        // Sky Cyan
    FocusProfile.WORK -> Color(0xFFA78BFA)          // Soft Violet
    FocusProfile.SLEEP -> Color(0xFF818CF8)         // Indigo Dusk
    FocusProfile.DO_NOT_DISTURB -> Color(0xFF34D399)// Emerald Mint
    FocusProfile.CINEMA -> Color(0xFFFBBF24)        // Warm Amber
    FocusProfile.OFF -> Color(0xFF94A3B8)
}

fun focusModeShortLabel(profile: FocusProfile): String = when (profile) {
    FocusProfile.GAMING -> "Gaming"
    FocusProfile.WORK -> "Work"
    FocusProfile.SLEEP -> "Sleep"
    FocusProfile.DO_NOT_DISTURB -> "DND"
    FocusProfile.CINEMA -> "Cinema"
    FocusProfile.OFF -> "Off"
}

fun focusModeIcon(profile: FocusProfile): ImageVector = when (profile) {
    FocusProfile.GAMING -> Icons.Rounded.SportsEsports
    FocusProfile.WORK -> Icons.Rounded.Work
    FocusProfile.SLEEP -> Icons.Rounded.Bedtime
    FocusProfile.DO_NOT_DISTURB -> Icons.Rounded.DoNotDisturbOn
    FocusProfile.CINEMA -> Icons.Rounded.Movie
    FocusProfile.OFF -> Icons.Rounded.Shield
}

/**
 * Full-screen Focus Session History screen powered by Room Database & Recharts-inspired Bar Chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSessionHistoryScreen(
    stateManager: FocusStateManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    historyViewModel: FocusSessionHistoryViewModel = viewModel(
        factory = FocusSessionHistoryViewModel.Factory(stateManager.focusHistoryRepository)
    )
) {
    val context = LocalContext.current
    val uiState by historyViewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        ExpressiveHaptics.playSpringTap(context)
        onBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("focus_session_history_screen"),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            onBack()
                        },
                        modifier = Modifier.testTag("session_history_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Dashboard"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Focus Session History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Room SQLite Persistence • Recharts Mode Analytics",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            historyViewModel.restoreDemoHistory()
                        },
                        modifier = Modifier.testTag("session_history_restore_seed_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Restore Sample History",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Primary Recharts-Inspired Bar Chart & KPI Card
            item(key = "history_recharts_card") {
                FocusSessionHistoryDashboardCard(
                    stateManager = stateManager,
                    historyViewModel = historyViewModel,
                    onOpenFullScreen = null
                )
            }

            // 2. Mode Filter Bar & Session Log Header
            item(key = "history_filter_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Logged Focus Sessions (${uiState.filteredSessions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Automatically recorded in Room when any Focus Mode finishes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (uiState.allSessions.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    ExpressiveHaptics.playNotificationDismissed(context)
                                    historyViewModel.clearAllHistory()
                                },
                                modifier = Modifier.testTag("clear_all_session_history_btn")
                            ) {
                                Text(
                                    text = "Clear All",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    ModeFilterChipStrip(
                        selectedProfile = uiState.selectedFilterProfile,
                        onSelectProfile = { profile ->
                            ExpressiveHaptics.playSpringTap(context)
                            historyViewModel.setFilterProfile(profile)
                        }
                    )
                }
            }

            // 3. Room Session Log Items
            if (uiState.filteredSessions.isEmpty()) {
                item(key = "history_empty_state") {
                    EmptySessionHistoryCard(
                        onRestoreSample = {
                            ExpressiveHaptics.playSpringTap(context)
                            historyViewModel.restoreDemoHistory()
                        }
                    )
                }
            } else {
                items(
                    items = uiState.filteredSessions,
                    key = { it.id }
                ) { session ->
                    FocusSessionHistoryItemCard(
                        session = session,
                        onDelete = {
                            ExpressiveHaptics.playNotificationDismissed(context)
                            historyViewModel.deleteSession(session.id)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Embedded & Full-Screen Recharts-inspired Bar Chart Card visualizing how much time users spend
 * in each Focus Mode backed by Room database.
 */
@Composable
fun FocusSessionHistoryDashboardCard(
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier,
    historyViewModel: FocusSessionHistoryViewModel = viewModel(
        factory = FocusSessionHistoryViewModel.Factory(stateManager.focusHistoryRepository)
    ),
    onOpenFullScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by historyViewModel.uiState.collectAsStateWithLifecycle()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("focus_session_history_card"),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Focus Session History",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Focus Session History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Time spent per mode • Room SQLite log",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onOpenFullScreen != null) {
                    FilledTonalButton(
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            onOpenFullScreen()
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("open_full_session_history_btn")
                    ) {
                        Text(
                            text = "Full Log →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Three KPI Summary Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HistoryKpiTile(
                    label = "TOTAL FOCUS",
                    value = formatGamingDurationMinutes(uiState.totalMinutesAllModes),
                    subLabel = "${uiState.totalSessionsCount} logged sessions",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_kpi_total_time")
                )
                HistoryKpiTile(
                    label = "TOP MODE",
                    value = focusModeShortLabel(uiState.topMode),
                    subLabel = formatGamingDurationMinutes(uiState.topModeMinutes),
                    accentColor = focusModeBarColor(uiState.topMode),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_kpi_top_mode")
                )
                HistoryKpiTile(
                    label = "AVG SESSION",
                    value = formatGamingDurationMinutes(uiState.averageSessionMinutes),
                    subLabel = "Per completed run",
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_kpi_avg_session")
                )
            }

            // 3. Recharts View Switcher (By Mode Bar Chart vs 7-Day Stacked Bar Chart)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.chartViewMode == FocusHistoryChartViewMode.BY_MODE) {
                        "Time Allocation by Focus Mode"
                    } else {
                        "7-Day Mode Distribution"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = uiState.chartViewMode == FocusHistoryChartViewMode.BY_MODE,
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            historyViewModel.setChartViewMode(FocusHistoryChartViewMode.BY_MODE)
                        },
                        label = {
                            Text(
                                text = "By Mode",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("history_chart_view_by_mode")
                    )

                    FilterChip(
                        selected = uiState.chartViewMode == FocusHistoryChartViewMode.DAILY_TIMELINE,
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            historyViewModel.setChartViewMode(FocusHistoryChartViewMode.DAILY_TIMELINE)
                        },
                        label = {
                            Text(
                                text = "7-Day",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("history_chart_view_daily")
                    )
                }
            }

            // 4. Recharts-Inspired Bar Chart Canvas Container
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("focus_history_recharts_bar_chart")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.chartViewMode == FocusHistoryChartViewMode.BY_MODE) {
                        RechartsModeAllocationBarChart(
                            allocations = uiState.modeAllocations,
                            selectedBarIndex = uiState.selectedBarIndex,
                            onSelectBar = { idx ->
                                ExpressiveHaptics.playSpringTap(context)
                                historyViewModel.selectBarIndex(idx)
                            }
                        )
                    } else {
                        RechartsDailyStackedModeBarChart(
                            days = uiState.dailyBreakdown,
                            selectedDayIndex = uiState.selectedBarIndex,
                            onSelectDay = { idx ->
                                ExpressiveHaptics.playSpringTap(context)
                                historyViewModel.selectBarIndex(idx)
                            }
                        )
                    }
                }
            }

            // 5. Recharts Interactive Tooltip Inspector
            if (uiState.chartViewMode == FocusHistoryChartViewMode.BY_MODE) {
                val selectedAllocation = uiState.modeAllocations.getOrNull(uiState.selectedBarIndex)
                if (selectedAllocation != null) {
                    ModeAllocationTooltipCard(allocation = selectedAllocation)
                }
            } else {
                val selectedDay = uiState.dailyBreakdown.getOrNull(uiState.selectedBarIndex)
                if (selectedDay != null) {
                    DailyModeBreakdownTooltipCard(day = selectedDay)
                }
            }

            // 6. Quick Session Logger Strip (Writes directly to Room Database)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Log Completed Session to Room DB",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FocusSessionHistoryRepository.TRACKED_MODES.forEach { profile ->
                        val defaultMins = when (profile) {
                            FocusProfile.GAMING -> 45
                            FocusProfile.WORK -> 60
                            FocusProfile.SLEEP -> 90
                            FocusProfile.DO_NOT_DISTURB -> 30
                            FocusProfile.CINEMA -> 120
                            FocusProfile.OFF -> 30
                        }
                        OutlinedButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                historyViewModel.logQuickSession(
                                    profile = profile,
                                    durationMinutes = defaultMins,
                                    triggerSource = "Quick Log"
                                )
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("quick_log_session_${profile.id.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = focusModeBarColor(profile),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+${defaultMins}m ${focusModeShortLabel(profile)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 7. Compact Recent Sessions Preview when embedded in Dashboard/Analytics
            if (onOpenFullScreen != null && uiState.allSessions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Room Session Entries",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Showing ${minOf(3, uiState.allSessions.size)} of ${uiState.allSessions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    uiState.allSessions.take(3).forEach { session ->
                        FocusSessionHistoryItemCard(
                            session = session,
                            onDelete = {
                                ExpressiveHaptics.playNotificationDismissed(context)
                                historyViewModel.deleteSession(session.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recharts-inspired BarChart showing time spent in each FocusProfile (Gaming, Deep Work, Sleep, DND, Cinema)
 * with dashed reference gridlines, Y-axis hour ticks, gradient rounded bars, and tap-to-inspect selection.
 */
@Composable
private fun RechartsModeAllocationBarChart(
    allocations: List<ModeTimeAllocation>,
    selectedBarIndex: Int,
    onSelectBar: (Int) -> Unit
) {
    val maxMinutesRaw = remember(allocations) {
        allocations.maxOfOrNull { it.totalMinutes } ?: 120
    }
    val maxAxisMinutes = remember(maxMinutesRaw) {
        ((maxMinutesRaw + 59) / 60 * 60).coerceAtLeast(120)
    }

    val barFractions = allocations.mapIndexed { idx, item ->
        val frac by animateFloatAsState(
            targetValue = (item.totalMinutes.toFloat() / maxAxisMinutes.toFloat()).coerceIn(0.03f, 1f),
            animationSpec = ExpressiveMotion.bouncySpatialSpring(),
            label = "mode_bar_frac_$idx"
        )
        frac
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Y-Axis Ticks
            Column(
                modifier = Modifier
                    .height(185.dp)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                listOf(maxAxisMinutes, (maxAxisMinutes * 2) / 3, maxAxisMinutes / 3, 0).forEach { tickMins ->
                    Text(
                        text = if (tickMins >= 60 && tickMins % 60 == 0) "${tickMins / 60}h" else "${tickMins}m",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bar Chart Plot Area
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(185.dp)
                    .pointerInput(allocations.size) {
                        detectTapGestures { tapOffset ->
                            if (allocations.isNotEmpty() && size.width > 0) {
                                val slotWidth = size.width.toFloat() / allocations.size
                                val idx = (tapOffset.x / slotWidth)
                                    .toInt()
                                    .coerceIn(0, allocations.size - 1)
                                onSelectBar(idx)
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val count = allocations.size.coerceAtLeast(1)
                val slotWidth = width / count
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                // Cartesian Dashed Horizontal Grid Lines
                for (step in 0..3) {
                    val y = height * (step / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (step < 3) dashEffect else null
                    )
                }

                // Active Bar Slot Highlight
                if (selectedBarIndex in allocations.indices) {
                    val colLeft = selectedBarIndex * slotWidth + slotWidth * 0.08f
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = Offset(colLeft, 0f),
                        size = Size(slotWidth * 0.84f, height),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )
                }

                // Individual Mode Bars
                for (i in allocations.indices) {
                    val item = allocations[i]
                    val barColor = focusModeBarColor(item.profile)
                    val isSelected = i == selectedBarIndex
                    val barWidth = (slotWidth * 0.52f).coerceAtMost(34.dp.toPx())
                    val barHeight = (barFractions[i] * (height - 10.dp.toPx())).coerceAtLeast(6.dp.toPx())
                    val left = (i + 0.5f) * slotWidth - barWidth / 2f
                    val top = height - barHeight

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                barColor.copy(alpha = if (isSelected) 1f else 0.85f),
                                barColor.copy(alpha = if (isSelected) 0.65f else 0.42f)
                            ),
                            startY = top,
                            endY = height
                        ),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                    )
                }
            }
        }

        // X-Axis Interactive Mode Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            allocations.forEachIndexed { index, item ->
                val isSelected = index == selectedBarIndex
                val modeColor = focusModeBarColor(item.profile)
                val pillCorner by animateDpAsState(
                    targetValue = if (isSelected) 14.dp else 8.dp,
                    animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                    label = "mode_axis_corner_$index"
                )
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) modeColor.copy(alpha = 0.18f) else Color.Transparent,
                    animationSpec = ExpressiveMotion.smoothColorSpring(),
                    label = "mode_axis_bg_$index"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(pillCorner))
                        .background(pillBg)
                        .clickable { onSelectBar(index) }
                        .padding(vertical = 6.dp)
                        .testTag("mode_bar_axis_${item.profile.id.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = focusModeIcon(item.profile),
                            contentDescription = item.profile.displayName,
                            tint = if (isSelected) modeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = focusModeShortLabel(item.profile),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) modeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recharts-inspired 7-Day Stacked Bar Chart showing daily focus minutes segmented by FocusProfile.
 */
@Composable
private fun RechartsDailyStackedModeBarChart(
    days: List<DailyFocusModeBarEntry>,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    val maxDayMinutes = remember(days) {
        days.maxOfOrNull { it.totalMinutes } ?: 180
    }
    val maxAxisMinutes = remember(maxDayMinutes) {
        ((maxDayMinutes + 59) / 60 * 60).coerceAtLeast(180)
    }

    val totalFractions = days.mapIndexed { idx, day ->
        val frac by animateFloatAsState(
            targetValue = (day.totalMinutes.toFloat() / maxAxisMinutes.toFloat()).coerceIn(0.02f, 1f),
            animationSpec = ExpressiveMotion.bouncySpatialSpring(),
            label = "daily_stacked_frac_$idx"
        )
        frac
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Legend Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FocusSessionHistoryRepository.TRACKED_MODES.forEach { profile ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(focusModeBarColor(profile))
                    )
                    Text(
                        text = focusModeShortLabel(profile),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Y-Axis Ticks
            Column(
                modifier = Modifier
                    .height(185.dp)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                listOf(maxAxisMinutes, (maxAxisMinutes * 2) / 3, maxAxisMinutes / 3, 0).forEach { tickMins ->
                    Text(
                        text = if (tickMins >= 60 && tickMins % 60 == 0) "${tickMins / 60}h" else "${tickMins}m",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Stacked Bar Plot Area
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(185.dp)
                    .pointerInput(days.size) {
                        detectTapGestures { tapOffset ->
                            if (days.isNotEmpty() && size.width > 0) {
                                val slotWidth = size.width.toFloat() / days.size
                                val idx = (tapOffset.x / slotWidth)
                                    .toInt()
                                    .coerceIn(0, days.size - 1)
                                onSelectDay(idx)
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val count = days.size.coerceAtLeast(1)
                val slotWidth = width / count
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                for (step in 0..3) {
                    val y = height * (step / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (step < 3) dashEffect else null
                    )
                }

                if (selectedDayIndex in days.indices) {
                    val colLeft = selectedDayIndex * slotWidth + slotWidth * 0.08f
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = Offset(colLeft, 0f),
                        size = Size(slotWidth * 0.84f, height),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                }

                for (i in days.indices) {
                    val day = days[i]
                    val isSel = i == selectedDayIndex
                    val barWidth = (slotWidth * 0.46f).coerceAtMost(24.dp.toPx())
                    val left = (i + 0.5f) * slotWidth - barWidth / 2f
                    val totalBarHeight = (totalFractions[i] * (height - 10.dp.toPx())).coerceAtLeast(4.dp.toPx())

                    if (day.totalMinutes <= 0) {
                        drawRoundRect(
                            color = gridColor,
                            topLeft = Offset(left, height - 4.dp.toPx()),
                            size = Size(barWidth, 4.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    } else {
                        var currentBottom = height
                        FocusSessionHistoryRepository.TRACKED_MODES.forEach { profile ->
                            val modeMins = day.minutesByProfile[profile] ?: 0
                            if (modeMins > 0) {
                                val segHeight = (modeMins.toFloat() / day.totalMinutes.toFloat()) * totalBarHeight
                                val top = (currentBottom - segHeight).coerceAtLeast(0f)
                                drawRoundRect(
                                    color = focusModeBarColor(profile).copy(alpha = if (isSel) 1f else 0.82f),
                                    topLeft = Offset(left, top),
                                    size = Size(barWidth, (currentBottom - top).coerceAtLeast(2.dp.toPx())),
                                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                                )
                                currentBottom = top
                            }
                        }
                    }
                }
            }
        }

        // X-Axis Day Selector Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEachIndexed { index, day ->
                val isSelected = index == selectedDayIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            else Color.Transparent
                        )
                        .clickable { onSelectDay(index) }
                        .padding(vertical = 6.dp)
                        .testTag("history_daily_axis_${day.dayShort.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.dayShort,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected || day.isToday) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeAllocationTooltipCard(
    allocation: ModeTimeAllocation
) {
    val modeColor = focusModeBarColor(allocation.profile)
    val pctFormatted = String.format(Locale.US, "%.1f%% of total focus", allocation.percentageOfTotal)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, modeColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_mode_tooltip_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = modeColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = focusModeIcon(allocation.profile),
                            contentDescription = null,
                            tint = modeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = allocation.profile.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = modeColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = pctFormatted,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = modeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${allocation.sessionCount} sessions • Avg ${formatGamingDurationMinutes(allocation.averageSessionMinutes)} • ${allocation.totalCallsDeclined} calls & ${allocation.totalAlertsSilenced} alerts shielded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = formatGamingDurationMinutes(allocation.totalMinutes),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = modeColor
            )
        }
    }
}

@Composable
private fun DailyModeBreakdownTooltipCard(
    day: DailyFocusModeBarEntry
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_daily_tooltip_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (day.isToday) "${day.dayFull} (Today)" else day.dayFull,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatGamingDurationMinutes(day.totalMinutes)} (${day.sessionCount} sessions)",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FocusSessionHistoryRepository.TRACKED_MODES.forEach { profile ->
                    val mins = day.minutesByProfile[profile] ?: 0
                    if (mins > 0) {
                        Text(
                            text = "${focusModeShortLabel(profile)}: ${formatGamingDurationMinutes(mins)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = focusModeBarColor(profile)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeFilterChipStrip(
    selectedProfile: FocusProfile?,
    onSelectProfile: (FocusProfile?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedProfile == null,
            onClick = { onSelectProfile(null) },
            label = {
                Text(
                    text = "All Modes",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            shape = CircleShape,
            modifier = Modifier.testTag("history_filter_all")
        )

        FocusSessionHistoryRepository.TRACKED_MODES.forEach { profile ->
            val isSelected = selectedProfile == profile
            FilterChip(
                selected = isSelected,
                onClick = { onSelectProfile(if (isSelected) null else profile) },
                label = {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = focusModeIcon(profile),
                        contentDescription = null,
                        tint = focusModeBarColor(profile),
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = CircleShape,
                modifier = Modifier.testTag("history_filter_${profile.id.lowercase()}")
            )
        }
    }
}

@Composable
private fun FocusSessionHistoryItemCard(
    session: FocusSessionHistoryEntity,
    onDelete: () -> Unit
) {
    val profile = FocusProfile.fromId(session.profileId)
    val accentColor = focusModeBarColor(profile)
    val dateTimeFmt = remember { SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault()) }
    val endFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeRangeText = "${dateTimeFmt.format(Date(session.startTimeMillis))} – ${endFmt.format(Date(session.endTimeMillis))}"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_history_item_${session.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = focusModeIcon(profile),
                            contentDescription = session.profileDisplayName,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = session.profileDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = session.triggerSource,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = timeRangeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = formatGamingDurationMinutes(session.durationMinutes),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_session_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete session entry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySessionHistoryCard(
    onRestoreSample: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No Focus Sessions in Filter",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Use the Quick Log buttons above or toggle any Focus Mode to record sessions in Room.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            FilledTonalButton(
                onClick = onRestoreSample,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restore Sample History")
            }
        }
    }
}

@Composable
private fun HistoryKpiTile(
    label: String,
    value: String,
    subLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 0.6.sp
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
