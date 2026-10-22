package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.DailyShieldStats
import com.example.core.FocusStateManager
import com.example.core.WeeklyShieldSummary
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.JetBrainsMonoFontFamily

private enum class ChartMetricFilter {
    ALL,
    NOTIFICATIONS,
    CALLS
}

/**
 * Recharts-inspired Interactive Data Visualization Component for Jetpack Compose
 * upgraded with Material 3 Expressive shape morphing and bouncy spring animations.
 */
@Composable
fun WeeklyShieldStatsCard(
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier,
    showSimulationControls: Boolean = true
) {
    val summary by stateManager.weeklyStatsFlow.collectAsState()
    var selectedMetricFilter by remember { mutableStateOf(ChartMetricFilter.ALL) }
    var selectedDayIndex by remember {
        mutableIntStateOf((summary.days.size - 1).coerceAtLeast(0))
    }
    var isBreakdownExpanded by remember { mutableStateOf(true) }

    val activeIndex = if (selectedDayIndex in summary.days.indices) {
        selectedDayIndex
    } else {
        (summary.days.size - 1).coerceAtLeast(0)
    }
    val selectedDay: DailyShieldStats? = summary.days.getOrNull(activeIndex)

    val outerCorner by animateDpAsState(
        targetValue = if (isBreakdownExpanded) 32.dp else 24.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "weekly_card_corner"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_stats_card"),
        shape = RoundedCornerShape(outerCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Live Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = "Shield Statistics",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Weekly Shield Telemetry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "7-day silenced alerts & declined calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Peak: ${summary.peakDayLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Expressive Morphing Filter Chips (All, Notifications, Calls)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allCorner by animateDpAsState(
                    targetValue = if (selectedMetricFilter == ChartMetricFilter.ALL) 24.dp else 12.dp,
                    animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                    label = "chip_all_corner"
                )
                val notifsCorner by animateDpAsState(
                    targetValue = if (selectedMetricFilter == ChartMetricFilter.NOTIFICATIONS) 24.dp else 12.dp,
                    animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                    label = "chip_notifs_corner"
                )
                val callsCorner by animateDpAsState(
                    targetValue = if (selectedMetricFilter == ChartMetricFilter.CALLS) 24.dp else 12.dp,
                    animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                    label = "chip_calls_corner"
                )

                FilterChip(
                    selected = selectedMetricFilter == ChartMetricFilter.ALL,
                    onClick = { selectedMetricFilter = ChartMetricFilter.ALL },
                    label = { Text("Combined", style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(allCorner),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_chip_all")
                )
                FilterChip(
                    selected = selectedMetricFilter == ChartMetricFilter.NOTIFICATIONS,
                    onClick = { selectedMetricFilter = ChartMetricFilter.NOTIFICATIONS },
                    label = { Text("Alerts", style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(notifsCorner),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_chip_notifs")
                )
                FilterChip(
                    selected = selectedMetricFilter == ChartMetricFilter.CALLS,
                    onClick = { selectedMetricFilter = ChartMetricFilter.CALLS },
                    label = { Text("Calls", style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(callsCorner),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_chip_calls")
                )
            }

            // KPI Summary Row (Weekly totals & averages)
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
                    isHighlighted = selectedMetricFilter == ChartMetricFilter.NOTIFICATIONS,
                    modifier = Modifier.weight(1f)
                )

                KpiMetricCard(
                    title = "Declined Calls",
                    value = summary.totalDeclinedCalls.toString(),
                    subtitle = "Avg ${String.format("%.1f", summary.averageCallsPerDay)} / day",
                    icon = Icons.Filled.PhoneDisabled,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    isHighlighted = selectedMetricFilter == ChartMetricFilter.CALLS,
                    modifier = Modifier.weight(1f)
                )
            }

            // Interactive Tooltip Card (Recharts style floating inspect pill)
            selectedDay?.let { day ->
                RechartsTooltipPill(
                    day = day,
                    filter = selectedMetricFilter,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dual-Bar + Trend Curve Chart Canvas & Layout
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RechartsDualBarChart(
                        summary = summary,
                        filter = selectedMetricFilter,
                        selectedDayIndex = activeIndex,
                        onDaySelected = { selectedDayIndex = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    )

                    ChartLegendRow(
                        filter = selectedMetricFilter,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Quick Live Telemetry Test Controls
            if (showSimulationControls) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            stateManager.incrementShieldedNotification()
                            selectedDayIndex = (summary.days.size - 1).coerceAtLeast(0)
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("simulate_silenced_notif_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Log Alert",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            stateManager.incrementShieldedCall()
                            selectedDayIndex = (summary.days.size - 1).coerceAtLeast(0)
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("simulate_declined_call_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Log Call",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Expandable Daily Breakdown Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isBreakdownExpanded = !isBreakdownExpanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "7-Day Activity Log",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { isBreakdownExpanded = !isBreakdownExpanded },
                    modifier = Modifier.size(28.dp)
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
                val maxDayTotal = (summary.days.maxOfOrNull { it.silencedNotifications + it.declinedCalls } ?: 1).coerceAtLeast(1)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summary.days.forEachIndexed { index, day ->
                        DailyBreakdownRow(
                            day = day,
                            maxTotal = maxDayTotal,
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
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = day.dayFull,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (day.isToday) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "TODAY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${day.silencedNotifications + day.declinedCalls} total distractions blocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filter != ChartMetricFilter.CALLS) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${day.silencedNotifications}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Alerts",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (filter != ChartMetricFilter.NOTIFICATIONS) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${day.declinedCalls}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
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
 * Recharts Dual Bar + Smooth Area Trend Line Chart in Jetpack Compose.
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

    val maxVal = remember(days, filter) {
        val peak = days.maxOfOrNull { day ->
            when (filter) {
                ChartMetricFilter.ALL -> maxOf(day.silencedNotifications, day.declinedCalls)
                ChartMetricFilter.NOTIFICATIONS -> day.silencedNotifications
                ChartMetricFilter.CALLS -> day.declinedCalls
            }
        } ?: 10
        peak.coerceAtLeast(8)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recharts_dual_bar_chart"),
        verticalAlignment = Alignment.Bottom
    ) {
        // Y-Axis Scale Labels
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(bottom = 28.dp, end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$maxVal",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${maxVal / 2}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Main Chart Plot Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Dashed Recharts Grid & Smooth Area Trend Backdrop
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 28.dp)
            ) {
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                val h = size.height
                val w = size.width

                // 3 horizontal grid lines (Top, Middle, Base)
                listOf(0f, h * 0.5f, h).forEach { y ->
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (y < h) dashEffect else null
                    )
                }

                // Draw subtle Recharts spline area curve behind bars
                if (days.size > 1) {
                    val stepX = w / days.size
                    val points = days.mapIndexed { idx, d ->
                        val v = if (filter == ChartMetricFilter.CALLS) d.declinedCalls else d.silencedNotifications
                        val ratio = (v.toFloat() / maxVal).coerceIn(0f, 1f)
                        Offset(stepX * idx + stepX / 2f, h * (1f - ratio))
                    }

                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val ctrlX = (p0.x + p1.x) / 2f
                            cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                        }
                    }

                    val areaPath = Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, h)
                        lineTo(points.first().x, h)
                        close()
                    }

                    val curveColor = if (filter == ChartMetricFilter.CALLS) tertiaryColor else primaryColor
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                curveColor.copy(alpha = 0.16f),
                                curveColor.copy(alpha = 0.01f)
                            )
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = curveColor.copy(alpha = 0.45f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Interactive Dual Bars & X-Axis Labels
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { index, day ->
                    val isSelected = (index == selectedDayIndex)

                    val notifRatio = (day.silencedNotifications.toFloat() / maxVal).coerceIn(0.05f, 1.0f)
                    val callsRatio = (day.declinedCalls.toFloat() / maxVal).coerceIn(0.05f, 1.0f)

                    val animatedNotifHeight by animateFloatAsState(
                        targetValue = if (filter == ChartMetricFilter.CALLS) 0f else notifRatio,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "notif_bar_height"
                    )

                    val animatedCallHeight by animateFloatAsState(
                        targetValue = if (filter == ChartMetricFilter.NOTIFICATIONS) 0f else callsRatio,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "call_bar_height"
                    )

                    val dayColCorner by animateDpAsState(
                        targetValue = if (isSelected) 16.dp else 10.dp,
                        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                        label = "day_col_corner_$index"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(dayColCorner))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else Color.Transparent
                            )
                            .clickable { onDaySelected(index) }
                            .padding(top = 6.dp)
                            .testTag("chart_day_$index"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
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
                                // Silenced Notifications Bar
                                if (filter != ChartMetricFilter.CALLS) {
                                    Box(
                                        modifier = Modifier
                                            .width(if (filter == ChartMetricFilter.ALL) 11.dp else 18.dp)
                                            .fillMaxHeight(animatedNotifHeight)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                    )
                                                )
                                            )
                                    )
                                }

                                // Declined Calls Bar
                                if (filter != ChartMetricFilter.NOTIFICATIONS) {
                                    Box(
                                        modifier = Modifier
                                            .width(if (filter == ChartMetricFilter.ALL) 11.dp else 18.dp)
                                            .fillMaxHeight(animatedCallHeight)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.tertiary,
                                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // X-Axis Day Label
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                day.isToday -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color.Transparent
                            }
                        ) {
                            Text(
                                text = day.dayShort,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    day.isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardCorner by animateDpAsState(
        targetValue = if (isHighlighted) 26.dp else 18.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "kpi_corner_$title"
    )
    Surface(
        shape = RoundedCornerShape(cardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isHighlighted) {
            androidx.compose.foundation.BorderStroke(1.5.dp, accentColor.copy(alpha = 0.6f))
        } else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = accentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

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
            Spacer(modifier = Modifier.width(20.dp))
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

@Composable
private fun DailyBreakdownRow(
    day: DailyShieldStats,
    maxTotal: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val progress = ((day.silencedNotifications + day.declinedCalls).toFloat() / maxTotal).coerceIn(0.05f, 1f)
    val rowCorner by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 14.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "breakdown_row_corner_${day.dayShort}"
    )

    Surface(
        shape = RoundedCornerShape(rowCorner),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.width(38.dp)
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
                        text = "${day.silencedNotifications} alerts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${day.declinedCalls} calls",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
