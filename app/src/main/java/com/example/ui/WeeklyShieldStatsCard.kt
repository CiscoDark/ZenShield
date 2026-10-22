package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.DailyShieldStats
import com.example.core.FocusStateManager
import com.example.core.WeeklyShieldSummary

private enum class ChartMetricFilter {
    ALL,
    NOTIFICATIONS,
    CALLS
}

/**
 * Recharts-inspired Interactive Data Visualization Component for Jetpack Compose.
 * Displays summary statistics and interactive dual-bar graphs of silenced notifications
 * and declined calls over the week with live tooltips, filters, and daily breakdowns.
 */
@Composable
fun WeeklyShieldStatsCard(
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val summary by stateManager.weeklyStatsFlow.collectAsState()
    var selectedMetricFilter by remember { mutableStateOf(ChartMetricFilter.ALL) }
    var selectedDayIndex by remember {
        // Default to today (last index in 7-day window)
        mutableIntStateOf(summary.days.size - 1)
    }
    var isBreakdownExpanded by remember { mutableStateOf(false) }

    // Ensure selectedDayIndex remains within bounds if list size shifts
    val activeIndex = if (selectedDayIndex in summary.days.indices) selectedDayIndex else (summary.days.size - 1).coerceAtLeast(0)
    val selectedDay: DailyShieldStats? = summary.days.getOrNull(activeIndex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_stats_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Metric Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = "Shield Statistics",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Weekly Shield Telemetry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Silenced alerts vs declined calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Filter Chips (All, Notifications, Calls)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMetricFilter == ChartMetricFilter.ALL,
                    onClick = { selectedMetricFilter = ChartMetricFilter.ALL },
                    label = { Text("All (Combined)", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_chip_all")
                )
                FilterChip(
                    selected = selectedMetricFilter == ChartMetricFilter.NOTIFICATIONS,
                    onClick = { selectedMetricFilter = ChartMetricFilter.NOTIFICATIONS },
                    label = { Text("Alerts Only", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_chip_notifs")
                )
                FilterChip(
                    selected = selectedMetricFilter == ChartMetricFilter.CALLS,
                    onClick = { selectedMetricFilter = ChartMetricFilter.CALLS },
                    label = { Text("Calls Only", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    modifier = Modifier.testTag("filter_chip_calls")
                )
            }

            // KPI Summary Row (Weekly totals & peak day)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    title = "Silenced Alerts",
                    value = summary.totalSilencedNotifications.toString(),
                    subtitle = "Avg ${String.format("%.1f", summary.averageSilencedPerDay)} / day",
                    icon = Icons.Filled.NotificationsOff,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                KpiMetricCard(
                    title = "Declined Calls",
                    value = summary.totalDeclinedCalls.toString(),
                    subtitle = "Peak on ${summary.peakDayLabel}",
                    icon = Icons.Filled.PhoneDisabled,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Interactive Tooltip Card (Recharts style floating pill)
            selectedDay?.let { day ->
                RechartsTooltipPill(
                    day = day,
                    filter = selectedMetricFilter,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dual-Bar Chart Canvas & Layout
            RechartsDualBarChart(
                summary = summary,
                filter = selectedMetricFilter,
                selectedDayIndex = activeIndex,
                onDaySelected = { selectedDayIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )

            // Chart Legend
            ChartLegendRow(
                filter = selectedMetricFilter,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Expandable Daily Breakdown Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isBreakdownExpanded = !isBreakdownExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Daily Breakdown Details",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { isBreakdownExpanded = !isBreakdownExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isBreakdownExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isBreakdownExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isBreakdownExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summary.days.forEachIndexed { index, day ->
                        DailyBreakdownRow(
                            day = day,
                            isSelected = (index == activeIndex),
                            onClick = { selectedDayIndex = index }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recharts-style Tooltip Pill showing live details of the tapped/hovered day.
 */
@Composable
private fun RechartsTooltipPill(
    day: DailyShieldStats,
    filter: ChartMetricFilter,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("chart_tooltip"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = day.dayFull,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (day.isToday) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "TODAY",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "Tap any bar to inspect daily stats",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filter != ChartMetricFilter.CALLS) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${day.silencedNotifications}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Silenced",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (filter != ChartMetricFilter.NOTIFICATIONS) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${day.declinedCalls}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Calls",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recharts Dual Bar Chart layout in pure Jetpack Compose.
 * Calculates proportional bar heights with smooth spring animations,
 * reference grid lines, and interactive selection.
 */
@Composable
private fun RechartsDualBarChart(
    summary: WeeklyShieldSummary,
    filter: ChartMetricFilter,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = summary.days
    if (days.isEmpty()) return

    // Find highest value across the dataset to scale bars accurately
    val maxVal = remember(days, filter) {
        val peak = days.maxOfOrNull { day ->
            when (filter) {
                ChartMetricFilter.ALL -> maxOf(day.silencedNotifications, day.declinedCalls)
                ChartMetricFilter.NOTIFICATIONS -> day.silencedNotifications
                ChartMetricFilter.CALLS -> day.declinedCalls
            }
        } ?: 10
        peak.coerceAtLeast(10)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recharts_dual_bar_chart")
    ) {
        // Background Grid Lines (Recharts style reference guide)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }

        // Chart Bars & Day Labels
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { index, day ->
                val isSelected = (index == selectedDayIndex)

                val notifRatio = (day.silencedNotifications.toFloat() / maxVal).coerceIn(0.04f, 1.0f)
                val callsRatio = (day.declinedCalls.toFloat() / maxVal).coerceIn(0.04f, 1.0f)

                val animatedNotifHeight by animateFloatAsState(
                    targetValue = if (filter == ChartMetricFilter.CALLS) 0f else notifRatio,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "notif_bar_height"
                )

                val animatedCallHeight by animateFloatAsState(
                    targetValue = if (filter == ChartMetricFilter.NOTIFICATIONS) 0f else callsRatio,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "call_bar_height"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onDaySelected(index) }
                        .testTag("chart_day_$index"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Bar pillars container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Silenced Notifications Bar (Primary Gradient)
                            if (filter != ChartMetricFilter.CALLS) {
                                Box(
                                    modifier = Modifier
                                        .width(if (filter == ChartMetricFilter.ALL) 10.dp else 18.dp)
                                        .fillMaxHeight(animatedNotifHeight)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                                )
                                            )
                                        )
                                        .then(
                                            if (isSelected) Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.onPrimaryContainer,
                                                RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                                            ) else Modifier
                                        )
                                )
                            }

                            // Declined Calls Bar (Tertiary Gradient)
                            if (filter != ChartMetricFilter.NOTIFICATIONS) {
                                Box(
                                    modifier = Modifier
                                        .width(if (filter == ChartMetricFilter.ALL) 10.dp else 18.dp)
                                        .fillMaxHeight(animatedCallHeight)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.tertiary,
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                                                )
                                            )
                                        )
                                        .then(
                                            if (isSelected) Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.onTertiaryContainer,
                                                RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                                            ) else Modifier
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // X-Axis Day Label (with active pill if selected or today)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            day.isToday -> MaterialTheme.colorScheme.surfaceVariant
                            else -> Color.Transparent
                        },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = day.dayShort,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                day.isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Metric KPI Box for high-level statistics summary.
 */
@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = accentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Legend indicator row explaining color coding.
 */
@Composable
private fun ChartLegendRow(
    filter: ChartMetricFilter,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (filter != ChartMetricFilter.CALLS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "Silenced Notifications",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (filter == ChartMetricFilter.ALL) {
            Spacer(modifier = Modifier.width(18.dp))
        }

        if (filter != ChartMetricFilter.NOTIFICATIONS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
                Text(
                    text = "Declined Calls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Row displaying detailed numbers for a single day in the expandable breakdown.
 */
@Composable
private fun DailyBreakdownRow(
    day: DailyShieldStats,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = day.dayShort,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(36.dp)
                )
                if (day.isToday) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "🔔 ${day.silencedNotifications} alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "📞 ${day.declinedCalls} calls",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
