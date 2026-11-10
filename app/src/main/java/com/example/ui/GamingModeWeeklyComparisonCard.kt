package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.DailyGamingComparison
import com.example.core.FocusStateManager
import com.example.core.GamingWeeklyComparisonSummary
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.abs

enum class GamingComparisonChartStyle {
    GROUPED_BARS,
    AREA_SPLINE
}

/**
 * Formats total minutes into human-readable hours and minutes (e.g., "10h 55m" or "45m").
 */
fun formatGamingDurationMinutes(totalMinutes: Int): String {
    val safeMins = totalMinutes.coerceAtLeast(0)
    val hours = safeMins / 60
    val mins = safeMins % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h 0m"
        else -> "${mins}m"
    }
}

/**
 * Recharts / D3-inspired Interactive Dashboard View comparing time spent in Gaming Mode
 * This Week vs. Last Week, featuring:
 * - Week-over-week KPI summary cards & percentage delta pill
 * - Interactive Grouped Bar Chart & Smooth Cubic Bezier Area Spline Chart
 * - Tap-to-inspect daily tooltip comparing This Week vs Last Week for any selected day
 * - Live session simulation buttons (+30m This Week / +30m Last Week)
 */
@Composable
fun GamingModeWeeklyComparisonCard(
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier,
    showSimulationControls: Boolean = true
) {
    val context = LocalContext.current
    val summary by stateManager.gamingWeeklyComparisonFlow.collectAsState()
    var chartStyle by remember { mutableStateOf(GamingComparisonChartStyle.GROUPED_BARS) }
    var selectedDayIndex by remember(summary.days.size) {
        mutableIntStateOf((summary.days.size - 1).coerceAtLeast(0))
    }

    val activeIndex = if (selectedDayIndex in summary.days.indices) {
        selectedDayIndex
    } else {
        (summary.days.size - 1).coerceAtLeast(0)
    }
    val selectedDay: DailyGamingComparison? = summary.days.getOrNull(activeIndex)

    val thisWeekAccent = Color(0xFF38EF7D)
    val lastWeekAccent = Color(0xFF818CF8)
    val isUp = summary.deltaMinutes >= 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gaming_weekly_comparison_card"),
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
            // 1. Header Row with Week-over-Week Delta Badge
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
                        color = thisWeekAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, thisWeekAccent.copy(alpha = 0.45f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = "Gaming Mode Analytics",
                                tint = thisWeekAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Gaming Mode Time",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "This Week vs. Last Week comparison",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Percentage Delta Badge
                val deltaColor = if (isUp) thisWeekAccent else Color(0xFFFFB300)
                val formattedPct = String.format(
                    Locale.US,
                    "%s%.1f%%",
                    if (isUp) "+" else "-",
                    abs(summary.deltaPercentage)
                )
                Surface(
                    shape = CircleShape,
                    color = deltaColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, deltaColor.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("gaming_comparison_delta_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isUp) {
                                Icons.AutoMirrored.Rounded.TrendingUp
                            } else {
                                Icons.AutoMirrored.Rounded.TrendingDown
                            },
                            contentDescription = null,
                            tint = deltaColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "$formattedPct vs Last Wk",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = deltaColor
                        )
                    }
                }
            }

            // 2. Three KPI Summary Tiles: This Week, Last Week, Net Change
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ComparisonKpiTile(
                    label = "THIS WEEK",
                    value = formatGamingDurationMinutes(summary.thisWeekTotalMinutes),
                    subLabel = "Avg ${formatGamingDurationMinutes(summary.averageDailyMinutesThisWeek.toInt())}/day",
                    accentColor = thisWeekAccent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_gaming_this_week")
                )

                ComparisonKpiTile(
                    label = "LAST WEEK",
                    value = formatGamingDurationMinutes(summary.lastWeekTotalMinutes),
                    subLabel = "Prior 7-day total",
                    accentColor = lastWeekAccent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_gaming_last_week")
                )

                val absDeltaFormatted = formatGamingDurationMinutes(abs(summary.deltaMinutes))
                ComparisonKpiTile(
                    label = "NET DELTA",
                    value = "${if (isUp) "+" else "-"}$absDeltaFormatted",
                    subLabel = "Peak: ${summary.peakDayThisWeekLabel} (${formatGamingDurationMinutes(summary.peakDayThisWeekMinutes)})",
                    accentColor = if (isUp) thisWeekAccent else Color(0xFFFFB300),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_gaming_net_delta")
                )
            }

            // 3. Chart Mode Switcher & Series Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LegendDotItem(color = thisWeekAccent, label = "This Week")
                    LegendDotItem(color = lastWeekAccent, label = "Last Week")
                }

                // Chart Mode Filter Chips (Grouped Bars vs Area Spline)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = chartStyle == GamingComparisonChartStyle.GROUPED_BARS,
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            chartStyle = GamingComparisonChartStyle.GROUPED_BARS
                        },
                        label = {
                            Text(
                                text = "Bars",
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
                        modifier = Modifier.testTag("gaming_chart_mode_bars")
                    )

                    FilterChip(
                        selected = chartStyle == GamingComparisonChartStyle.AREA_SPLINE,
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            chartStyle = GamingComparisonChartStyle.AREA_SPLINE
                        },
                        label = {
                            Text(
                                text = "Spline",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ShowChart,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("gaming_chart_mode_spline")
                    )
                }
            }

            // 4. Recharts / D3-Inspired Dual-Series Comparison Canvas Chart
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0B111E),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gaming_comparison_chart_canvas_container")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GamingComparisonCanvasChart(
                        days = summary.days,
                        selectedDayIndex = activeIndex,
                        chartStyle = chartStyle,
                        thisWeekColor = thisWeekAccent,
                        lastWeekColor = lastWeekAccent,
                        onSelectDay = { index ->
                            ExpressiveHaptics.playSpringTap(context)
                            selectedDayIndex = index
                        }
                    )

                    // X-Axis Interactive Day Selector Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        summary.days.forEachIndexed { index, day ->
                            val isSelected = index == activeIndex
                            val chipCorner by animateDpAsState(
                                targetValue = if (isSelected) 14.dp else 8.dp,
                                animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                                label = "day_axis_corner_$index"
                            )
                            val chipBg by animateColorAsState(
                                targetValue = if (isSelected) {
                                    thisWeekAccent.copy(alpha = 0.22f)
                                } else {
                                    Color.Transparent
                                },
                                animationSpec = ExpressiveMotion.smoothColorSpring(),
                                label = "day_axis_bg_$index"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(chipCorner))
                                    .background(chipBg)
                                    .clickable {
                                        ExpressiveHaptics.playSpringTap(context)
                                        selectedDayIndex = index
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("gaming_chart_day_${day.dayShort.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = day.dayShort,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected || day.isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = when {
                                            isSelected -> thisWeekAccent
                                            day.isToday -> Color.White
                                            else -> Color(0xFF94A3B8)
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                    if (day.isToday) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(thisWeekAccent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Interactive Selected Day Tooltip Inspector Card
            if (selectedDay != null) {
                SelectedDayComparisonTooltip(
                    day = selectedDay,
                    thisWeekAccent = thisWeekAccent,
                    lastWeekAccent = lastWeekAccent
                )
            }

            // 6. Live Simulation / Session Logging Controls
            if (showSimulationControls) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            val offsetFromToday = (6 - activeIndex).coerceIn(0, 6)
                            stateManager.recordGamingSessionMinutes(
                                minutesToAdd = 30,
                                dayOffsetFromToday = offsetFromToday
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_gaming_30m_this_week_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+30m This Wk (${selectedDay?.dayShort ?: "Today"})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            val offsetFromToday = ((6 - activeIndex) + 7).coerceIn(7, 13)
                            stateManager.recordGamingSessionMinutes(
                                minutesToAdd = 30,
                                dayOffsetFromToday = offsetFromToday
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_gaming_30m_last_week_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+30m Last Wk (${selectedDay?.dayShort ?: "Day"})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonKpiTile(
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
                letterSpacing = 0.7.sp
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LegendDotItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GamingComparisonCanvasChart(
    days: List<DailyGamingComparison>,
    selectedDayIndex: Int,
    chartStyle: GamingComparisonChartStyle,
    thisWeekColor: Color,
    lastWeekColor: Color,
    onSelectDay: (Int) -> Unit
) {
    val maxMinutesRaw = remember(days) {
        days.maxOfOrNull { maxOf(it.thisWeekMinutes, it.lastWeekMinutes) } ?: 120
    }
    val maxAxisMinutes = remember(maxMinutesRaw) {
        ((maxMinutesRaw + 59) / 60 * 60).coerceAtLeast(120)
    }

    // Animated fractions per day for smooth Recharts-like transitions
    val thisWeekFractions = days.mapIndexed { idx, day ->
        val frac by animateFloatAsState(
            targetValue = (day.thisWeekMinutes.toFloat() / maxAxisMinutes.toFloat()).coerceIn(0.02f, 1f),
            animationSpec = ExpressiveMotion.bouncySpatialSpring(),
            label = "this_wk_frac_$idx"
        )
        frac
    }

    val lastWeekFractions = days.mapIndexed { idx, day ->
        val frac by animateFloatAsState(
            targetValue = (day.lastWeekMinutes.toFloat() / maxAxisMinutes.toFloat()).coerceIn(0.02f, 1f),
            animationSpec = ExpressiveMotion.bouncySpatialSpring(),
            label = "last_wk_frac_$idx"
        )
        frac
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(185.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Y-Axis Labels (Hours/Minutes)
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
                    color = Color(0xFF64748B)
                )
            }
        }

        // Canvas Plot Area
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(185.dp)
                .pointerInput(days.size) {
                    detectTapGestures { tapOffset ->
                        if (days.isNotEmpty() && size.width > 0) {
                            val slotWidth = size.width.toFloat() / days.size
                            val tappedIndex = (tapOffset.x / slotWidth)
                                .toInt()
                                .coerceIn(0, days.size - 1)
                            onSelectDay(tappedIndex)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val count = days.size.coerceAtLeast(1)
            val slotWidth = width / count
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

            // Horizontal Reference Grid Lines
            for (step in 0..3) {
                val y = height * (step / 3f)
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (step < 3) dashEffect else null
                )
            }

            // Highlight column for selected day
            if (selectedDayIndex in days.indices) {
                val colLeft = selectedDayIndex * slotWidth + slotWidth * 0.08f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.05f),
                    topLeft = Offset(colLeft, 0f),
                    size = Size(slotWidth * 0.84f, height),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
            }

            if (chartStyle == GamingComparisonChartStyle.GROUPED_BARS) {
                // Grouped Side-by-Side Bars per Day (Last Week on left, This Week on right)
                for (i in days.indices) {
                    val centerX = (i + 0.5f) * slotWidth
                    val barWidth = (slotWidth * 0.28f).coerceAtMost(14.dp.toPx())
                    val gap = 3.dp.toPx()

                    val lastH = (lastWeekFractions[i] * (height - 8.dp.toPx())).coerceAtLeast(4.dp.toPx())
                    val thisH = (thisWeekFractions[i] * (height - 8.dp.toPx())).coerceAtLeast(4.dp.toPx())

                    val lastLeft = centerX - gap / 2f - barWidth
                    val thisLeft = centerX + gap / 2f

                    // Last Week Bar
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lastWeekColor.copy(alpha = if (i == selectedDayIndex) 0.95f else 0.7f),
                                lastWeekColor.copy(alpha = if (i == selectedDayIndex) 0.55f else 0.3f)
                            ),
                            startY = height - lastH,
                            endY = height
                        ),
                        topLeft = Offset(lastLeft, height - lastH),
                        size = Size(barWidth, lastH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // This Week Bar
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                thisWeekColor.copy(alpha = if (i == selectedDayIndex) 1f else 0.85f),
                                thisWeekColor.copy(alpha = if (i == selectedDayIndex) 0.65f else 0.38f)
                            ),
                            startY = height - thisH,
                            endY = height
                        ),
                        topLeft = Offset(thisLeft, height - thisH),
                        size = Size(barWidth, thisH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            } else {
                // Smooth Cubic Bezier Area Spline Comparison (D3 / Recharts AreaChart style)
                val thisPoints = days.indices.map { i ->
                    val x = (i + 0.5f) * slotWidth
                    val y = height - (thisWeekFractions[i] * (height - 12.dp.toPx()))
                    Offset(x, y)
                }
                val lastPoints = days.indices.map { i ->
                    val x = (i + 0.5f) * slotWidth
                    val y = height - (lastWeekFractions[i] * (height - 12.dp.toPx()))
                    Offset(x, y)
                }

                fun buildSmoothPath(points: List<Offset>): Path {
                    val path = Path()
                    if (points.isEmpty()) return path
                    path.moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val ctrlX = (p0.x + p1.x) / 2f
                        path.cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                    }
                    return path
                }

                if (lastPoints.size >= 2) {
                    val lastLinePath = buildSmoothPath(lastPoints)
                    val lastAreaPath = Path().apply {
                        addPath(lastLinePath)
                        lineTo(lastPoints.last().x, height)
                        lineTo(lastPoints.first().x, height)
                        close()
                    }
                    drawPath(
                        path = lastAreaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lastWeekColor.copy(alpha = 0.25f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )
                    drawPath(
                        path = lastLinePath,
                        color = lastWeekColor,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                if (thisPoints.size >= 2) {
                    val thisLinePath = buildSmoothPath(thisPoints)
                    val thisAreaPath = Path().apply {
                        addPath(thisLinePath)
                        lineTo(thisPoints.last().x, height)
                        lineTo(thisPoints.first().x, height)
                        close()
                    }
                    drawPath(
                        path = thisAreaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(thisWeekColor.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )
                    drawPath(
                        path = thisLinePath,
                        color = thisWeekColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Data nodes for both series
                for (i in days.indices) {
                    val isSel = i == selectedDayIndex
                    drawCircle(
                        color = lastWeekColor,
                        radius = if (isSel) 5.dp.toPx() else 3.5.dp.toPx(),
                        center = lastPoints[i]
                    )
                    drawCircle(
                        color = thisWeekColor,
                        radius = if (isSel) 6.dp.toPx() else 4.dp.toPx(),
                        center = thisPoints[i]
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayComparisonTooltip(
    day: DailyGamingComparison,
    thisWeekAccent: Color,
    lastWeekAccent: Color
) {
    val diffMins = day.thisWeekMinutes - day.lastWeekMinutes
    val isMoreThisWeek = diffMins >= 0
    val diffText = "${if (isMoreThisWeek) "+" else "-"}${formatGamingDurationMinutes(abs(diffMins))}"

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, thisWeekAccent.copy(alpha = 0.32f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gaming_selected_day_tooltip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (day.isToday) "${day.dayFullThisWeek} (Today)" else day.dayFullThisWeek,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (isMoreThisWeek) thisWeekAccent.copy(alpha = 0.16f) else Color(0xFFFFB300).copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "$diffText vs ${day.dayShort} Last Wk",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMoreThisWeek) thisWeekAccent else Color(0xFFFFB300),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "This Week: ${formatGamingDurationMinutes(day.thisWeekMinutes)}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = thisWeekAccent
                    )
                    Text(
                        text = "Last Week: ${formatGamingDurationMinutes(day.lastWeekMinutes)}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = lastWeekAccent
                    )
                }
            }
        }
    }
}
