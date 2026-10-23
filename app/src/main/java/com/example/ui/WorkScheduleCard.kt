package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Calendar
import java.util.Locale

private data class WorkDayOption(
    val calendarDay: Int,
    val shortLabel: String,
    val fullName: String
)

private val WORK_DAY_OPTIONS = listOf(
    WorkDayOption(Calendar.MONDAY, "M", "Monday"),
    WorkDayOption(Calendar.TUESDAY, "T", "Tuesday"),
    WorkDayOption(Calendar.WEDNESDAY, "W", "Wednesday"),
    WorkDayOption(Calendar.THURSDAY, "T", "Thursday"),
    WorkDayOption(Calendar.FRIDAY, "F", "Friday"),
    WorkDayOption(Calendar.SATURDAY, "S", "Saturday"),
    WorkDayOption(Calendar.SUNDAY, "S", "Sunday")
)

/**
 * WorkScheduleCard
 *
 * Material 3 Expressive card allowing Work Focus (FocusProfile.WORK) to be triggered
 * both manually with one tap and automatically on a recurring daily/weekday schedule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScheduleCard(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isWorkActive = state.activeProfile == FocusProfile.WORK
    var isDetailsExpanded by remember(state.workScheduleEnabled) { mutableStateOf(state.workScheduleEnabled) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val startLabel = String.format(Locale.getDefault(), "%02d:%02d", state.workScheduleStartHour, state.workScheduleStartMinute)
    val endLabel = String.format(Locale.getDefault(), "%02d:%02d", state.workScheduleEndHour, state.workScheduleEndMinute)

    val cardCorner by animateDpAsState(
        targetValue = if (isWorkActive || state.workScheduleEnabled) 30.dp else 22.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "work_schedule_card_corner"
    )
    val emblemCorner by animateDpAsState(
        targetValue = if (isWorkActive || state.workScheduleEnabled) 20.dp else 14.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "work_schedule_emblem_corner"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("work_schedule_card"),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isWorkActive) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Emblem, Title, Status Pill, and Auto-Schedule Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        ExpressiveHaptics.playSpringTap(context)
                        isDetailsExpanded = !isDetailsExpanded
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(emblemCorner),
                        color = if (isWorkActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Work,
                                contentDescription = null,
                                tint = if (isWorkActive) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Work Focus & Schedule",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isWorkActive && state.isAutoTriggeredByWorkSchedule -> Color(0xFF38EF7D).copy(alpha = 0.2f)
                                    isWorkActive -> MaterialTheme.colorScheme.primaryContainer
                                    state.workScheduleEnabled -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Text(
                                    text = when {
                                        isWorkActive && state.isAutoTriggeredByWorkSchedule -> "SCHEDULED ON"
                                        isWorkActive -> "MANUAL ON"
                                        state.workScheduleEnabled -> "$startLabel–$endLabel"
                                        else -> "MANUAL"
                                    },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isWorkActive && state.isAutoTriggeredByWorkSchedule -> Color(0xFF38EF7D)
                                        isWorkActive -> MaterialTheme.colorScheme.primary
                                        state.workScheduleEnabled -> MaterialTheme.colorScheme.onSecondaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = when {
                                isWorkActive && state.isAutoTriggeredByWorkSchedule -> "Scheduled Work session active until $endLabel"
                                isWorkActive -> "Work Focus active • Starred VIP priority enabled"
                                state.workScheduleEnabled -> "Auto-engages Work Focus from $startLabel to $endLabel"
                                else -> "Trigger manually below or enable automated daily schedule"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Switch(
                        checked = state.workScheduleEnabled,
                        onCheckedChange = { enabled ->
                            ExpressiveHaptics.playSpringTap(context)
                            stateManager.setWorkScheduleEnabled(enabled)
                            if (enabled) isDetailsExpanded = true
                        },
                        modifier = Modifier.testTag("switch_work_schedule_enabled")
                    )
                    Icon(
                        imageVector = if (isDetailsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isDetailsExpanded) "Collapse Work Schedule" else "Expand Work Schedule",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Manual One-Tap Trigger Button + Schedule Window Pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val manualBtnInteraction = remember { MutableInteractionSource() }
                val isPressed by manualBtnInteraction.collectIsPressedAsState()
                val btnScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = ExpressiveMotion.snappySpatialSpring(),
                    label = "work_manual_btn_scale"
                )
                val btnCorner by animateDpAsState(
                    targetValue = if (isPressed) 12.dp else 18.dp,
                    animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                    label = "work_manual_btn_corner"
                )

                Button(
                    onClick = {
                        stateManager.resetSessionCaches()
                        if (isWorkActive) {
                            stateManager.setProfile(FocusProfile.OFF)
                        } else {
                            stateManager.setProfile(FocusProfile.WORK, state.durationOption)
                        }
                    },
                    interactionSource = manualBtnInteraction,
                    shape = RoundedCornerShape(btnCorner),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWorkActive) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isWorkActive) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .graphicsLayer {
                            scaleX = btnScale
                            scaleY = btnScale
                        }
                        .testTag("btn_manual_work_focus")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Work,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isWorkActive) "End Work Focus" else "Start Work Focus Now",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Expandable Schedule Time & Day-of-Week Controls
            AnimatedVisibility(
                visible = isDetailsExpanded || state.workScheduleEnabled,
                enter = expandVertically(animationSpec = ExpressiveMotion.bouncySpatialSpring()) + fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()),
                exit = shrinkVertically(animationSpec = ExpressiveMotion.snappySpatialSpring()) + fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Start Time & End Time Selector Tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ScheduleTimeTile(
                            label = "Start Time",
                            timeValue = startLabel,
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                showStartTimePicker = true
                            },
                            testTag = "btn_work_start_time",
                            modifier = Modifier.weight(1f)
                        )
                        ScheduleTimeTile(
                            label = "End Time",
                            timeValue = endLabel,
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                showEndTimePicker = true
                            },
                            testTag = "btn_work_end_time",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Active Days of Week Selector Row
                    Text(
                        text = "Active Workdays",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WORK_DAY_OPTIONS.forEach { dayOption ->
                            val isSelected = state.workScheduleDays.contains(dayOption.calendarDay)
                            val dayInteraction = remember { MutableInteractionSource() }
                            val isDayPressed by dayInteraction.collectIsPressedAsState()

                            val pillCorner by animateDpAsState(
                                targetValue = when {
                                    isDayPressed -> 10.dp
                                    isSelected -> 20.dp
                                    else -> 12.dp
                                },
                                animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                                label = "work_day_corner_${dayOption.calendarDay}"
                            )
                            val pillScale by animateFloatAsState(
                                targetValue = if (isDayPressed) 0.9f else 1f,
                                animationSpec = ExpressiveMotion.snappySpatialSpring(),
                                label = "work_day_scale_${dayOption.calendarDay}"
                            )
                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                animationSpec = ExpressiveMotion.smoothColorSpring(),
                                label = "work_day_bg_${dayOption.calendarDay}"
                            )

                            Surface(
                                shape = RoundedCornerShape(pillCorner),
                                color = bgColor,
                                border = if (isSelected) {
                                    null
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer {
                                        scaleX = pillScale
                                        scaleY = pillScale
                                    }
                                    .clip(RoundedCornerShape(pillCorner))
                                    .clickable(
                                        interactionSource = dayInteraction,
                                        indication = null
                                    ) {
                                        ExpressiveHaptics.playSpringTap(context)
                                        stateManager.toggleWorkScheduleDay(dayOption.calendarDay)
                                    }
                                    .testTag("work_day_chip_${dayOption.calendarDay}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = dayOption.shortLabel,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = state.workScheduleStartHour,
            initialMinute = state.workScheduleStartMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = {
                Text(
                    text = "Work Focus Start Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = pickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setWorkScheduleTime(
                            startHour = pickerState.hour,
                            startMinute = pickerState.minute,
                            endHour = state.workScheduleEndHour,
                            endMinute = state.workScheduleEndMinute
                        )
                        showStartTimePicker = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEndTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = state.workScheduleEndHour,
            initialMinute = state.workScheduleEndMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = {
                Text(
                    text = "Work Focus End Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = pickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setWorkScheduleTime(
                            startHour = state.workScheduleStartHour,
                            startMinute = state.workScheduleStartMinute,
                            endHour = pickerState.hour,
                            endMinute = pickerState.minute
                        )
                        showEndTimePicker = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScheduleTimeTile(
    label: String,
    timeValue: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = timeValue,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
