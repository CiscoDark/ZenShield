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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
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

private data class SleepDayOption(
    val calendarDay: Int,
    val shortLabel: String,
    val fullName: String
)

private val SLEEP_DAY_OPTIONS = listOf(
    SleepDayOption(Calendar.SUNDAY, "S", "Sunday"),
    SleepDayOption(Calendar.MONDAY, "M", "Monday"),
    SleepDayOption(Calendar.TUESDAY, "T", "Tuesday"),
    SleepDayOption(Calendar.WEDNESDAY, "W", "Wednesday"),
    SleepDayOption(Calendar.THURSDAY, "T", "Thursday"),
    SleepDayOption(Calendar.FRIDAY, "F", "Friday"),
    SleepDayOption(Calendar.SATURDAY, "S", "Saturday")
)

private val WEEKNIGHTS_SET = setOf(
    Calendar.SUNDAY,
    Calendar.MONDAY,
    Calendar.TUESDAY,
    Calendar.WEDNESDAY,
    Calendar.THURSDAY
)

private val WEEKENDS_SET = setOf(
    Calendar.FRIDAY,
    Calendar.SATURDAY
)

/**
 * SleepScheduleCard
 *
 * Material 3 Expressive card allowing Sleep Shield (FocusProfile.SLEEP) to be triggered
 * both manually with one tap and automatically on a recurring overnight or daily bedtime schedule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScheduleCard(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSleepActive = state.activeProfile == FocusProfile.SLEEP
    var isDetailsExpanded by remember(state.sleepScheduleEnabled) { mutableStateOf(state.sleepScheduleEnabled) }
    var showBedtimePicker by remember { mutableStateOf(false) }
    var showWakeUpPicker by remember { mutableStateOf(false) }

    val startLabel = String.format(Locale.getDefault(), "%02d:%02d", state.sleepScheduleStartHour, state.sleepScheduleStartMinute)
    val endLabel = String.format(Locale.getDefault(), "%02d:%02d", state.sleepScheduleEndHour, state.sleepScheduleEndMinute)

    // Compute total sleep window duration (e.g., 22:30 -> 07:00 = 8h 30m)
    val startTotalMins = state.sleepScheduleStartHour * 60 + state.sleepScheduleStartMinute
    val endTotalMins = state.sleepScheduleEndHour * 60 + state.sleepScheduleEndMinute
    val durationMins = when {
        endTotalMins > startTotalMins -> endTotalMins - startTotalMins
        endTotalMins < startTotalMins -> (24 * 60 - startTotalMins) + endTotalMins
        else -> 0
    }
    val durationHours = durationMins / 60
    val durationRemMins = durationMins % 60
    val sleepDurationLabel = when {
        durationHours > 0 && durationRemMins > 0 -> "${durationHours}h ${durationRemMins}m window"
        durationHours > 0 -> "${durationHours}h window"
        else -> "${durationRemMins}m window"
    }

    val cardCorner by animateDpAsState(
        targetValue = if (isSleepActive || state.sleepScheduleEnabled) 30.dp else 22.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "sleep_schedule_card_corner"
    )
    val emblemCorner by animateDpAsState(
        targetValue = if (isSleepActive || state.sleepScheduleEnabled) 20.dp else 14.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "sleep_schedule_emblem_corner"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sleep_schedule_card"),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = if (isSleepActive) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            width = if (isSleepActive || state.sleepScheduleEnabled) 1.5.dp else 1.dp,
            color = when {
                isSleepActive -> Color(0xFF9F7AEA).copy(alpha = 0.78f)
                state.sleepScheduleEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Row: Emblem + Title + Live/Scheduled Badge + Manual Trigger Button
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
                        shape = RoundedCornerShape(emblemCorner),
                        color = if (isSleepActive) {
                            Color(0xFF7C3AED)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = "Sleep Schedule Shield",
                                tint = if (isSleepActive) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
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
                                text = "Scheduled Sleep Shield",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isSleepActive && state.isAutoTriggeredBySleepSchedule -> Color(0xFF7C3AED).copy(alpha = 0.24f)
                                    isSleepActive -> Color(0xFF38EF7D).copy(alpha = 0.22f)
                                    state.sleepScheduleEnabled -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Text(
                                    text = when {
                                        isSleepActive && state.isAutoTriggeredBySleepSchedule -> "AUTO BEDTIME"
                                        isSleepActive -> "SLEEP ACTIVE"
                                        state.sleepScheduleEnabled -> "SCHEDULE ON"
                                        else -> "MANUAL"
                                    },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        isSleepActive && state.isAutoTriggeredBySleepSchedule -> Color(0xFFC4B5FD)
                                        isSleepActive -> Color(0xFF38EF7D)
                                        state.sleepScheduleEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (state.sleepScheduleEnabled) {
                                "Auto-shields $startLabel – $endLabel ($sleepDurationLabel)"
                            } else {
                                "Trigger Sleep DND manually or set an overnight bedtime schedule"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Instant 1-Tap Manual Sleep Focus Trigger Button
                val manualBtnInteraction = remember { MutableInteractionSource() }
                val isManualBtnPressed by manualBtnInteraction.collectIsPressedAsState()
                val manualBtnScale by animateFloatAsState(
                    targetValue = if (isManualBtnPressed) 0.93f else 1f,
                    animationSpec = ExpressiveMotion.snappySpatialSpring(),
                    label = "sleep_manual_btn_scale"
                )
                val manualBtnCorner by animateDpAsState(
                    targetValue = when {
                        isManualBtnPressed -> 10.dp
                        isSleepActive -> 22.dp
                        else -> 16.dp
                    },
                    animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                    label = "sleep_manual_btn_corner"
                )

                Button(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.resetSessionCaches()
                        if (isSleepActive) {
                            stateManager.setProfile(FocusProfile.OFF)
                        } else {
                            stateManager.setProfile(FocusProfile.SLEEP, state.durationOption)
                        }
                    },
                    interactionSource = manualBtnInteraction,
                    shape = RoundedCornerShape(manualBtnCorner),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSleepActive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isSleepActive) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    ),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = manualBtnScale
                            scaleY = manualBtnScale
                        }
                        .testTag("btn_manual_sleep_focus")
                ) {
                    Text(
                        text = if (isSleepActive) "Wake Up" else "Sleep Now",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 2. Automatic Bedtime Schedule Master Toggle Row + Expand/Collapse Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        ExpressiveHaptics.playSpringTap(context)
                        isDetailsExpanded = !isDetailsExpanded
                    }
                    .padding(vertical = 4.dp)
                    .testTag("btn_toggle_sleep_schedule_details"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = if (state.sleepScheduleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Automatic Overnight Sleep Schedule",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.sleepScheduleEnabled) {
                                "$startLabel Bedtime → $endLabel Wake-up • ${state.sleepScheduleDays.size} nights/wk"
                            } else {
                                "Tap to configure bedtime, wake-up time, and active nights"
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
                        checked = state.sleepScheduleEnabled,
                        onCheckedChange = { enabled ->
                            ExpressiveHaptics.playSpringTap(context)
                            stateManager.setSleepScheduleEnabled(enabled)
                            if (enabled) {
                                isDetailsExpanded = true
                            }
                        },
                        modifier = Modifier.testTag("switch_sleep_schedule_enabled")
                    )
                    Icon(
                        imageVector = if (isDetailsExpanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = if (isDetailsExpanded) "Collapse schedule" else "Expand schedule",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Expandable Bedtime, Wake-Up & Day-of-Week Selector
            AnimatedVisibility(
                visible = isDetailsExpanded,
                enter = expandVertically(animationSpec = ExpressiveMotion.bouncySpatialSpring()) +
                    fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()),
                exit = shrinkVertically(animationSpec = ExpressiveMotion.snappySpatialSpring()) +
                    fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bedtime & Wake-Up Time Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SleepTimeButtonPill(
                            label = "BEDTIME",
                            timeText = startLabel,
                            subtitle = "Silence & OLED Dim",
                            isBedtime = true,
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                showBedtimePicker = true
                            },
                            testTag = "btn_sleep_start_time",
                            modifier = Modifier.weight(1f)
                        )
                        SleepTimeButtonPill(
                            label = "WAKE-UP TIME",
                            timeText = endLabel,
                            subtitle = "Restore Normal Ring",
                            isBedtime = false,
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                showWakeUpPicker = true
                            },
                            testTag = "btn_sleep_end_time",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Quick Preset Night Chips (Every Night, Weeknights, Weekends)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Nights",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SleepPresetChip(
                                label = "Every Night",
                                isSelected = state.sleepScheduleDays.size == 7,
                                onClick = {
                                    ExpressiveHaptics.playSpringTap(context)
                                    stateManager.setSleepScheduleDays(FocusSessionState.DEFAULT_SLEEP_DAYS)
                                },
                                testTag = "sleep_preset_every_night"
                            )
                            SleepPresetChip(
                                label = "Sun–Thu",
                                isSelected = state.sleepScheduleDays == WEEKNIGHTS_SET,
                                onClick = {
                                    ExpressiveHaptics.playSpringTap(context)
                                    stateManager.setSleepScheduleDays(WEEKNIGHTS_SET)
                                },
                                testTag = "sleep_preset_weeknights"
                            )
                            SleepPresetChip(
                                label = "Fri–Sat",
                                isSelected = state.sleepScheduleDays == WEEKENDS_SET,
                                onClick = {
                                    ExpressiveHaptics.playSpringTap(context)
                                    stateManager.setSleepScheduleDays(WEEKENDS_SET)
                                },
                                testTag = "sleep_preset_weekends"
                            )
                        }
                    }

                    // 7-Day Selector Pills (Sun..Sat)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SLEEP_DAY_OPTIONS.forEach { dayOpt ->
                            val isSelected = state.sleepScheduleDays.contains(dayOpt.calendarDay)
                            val dayInteraction = remember { MutableInteractionSource() }
                            val isPressed by dayInteraction.collectIsPressedAsState()

                            val dayCorner by animateDpAsState(
                                targetValue = when {
                                    isPressed -> 8.dp
                                    isSelected -> 18.dp
                                    else -> 12.dp
                                },
                                animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                                label = "sleep_day_corner_${dayOpt.calendarDay}"
                            )
                            val dayBg by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                animationSpec = ExpressiveMotion.smoothColorSpring(),
                                label = "sleep_day_bg_${dayOpt.calendarDay}"
                            )
                            val dayFg by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = ExpressiveMotion.smoothColorSpring(),
                                label = "sleep_day_fg_${dayOpt.calendarDay}"
                            )

                            Surface(
                                shape = RoundedCornerShape(dayCorner),
                                color = dayBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clickable(
                                        interactionSource = dayInteraction,
                                        indication = null
                                    ) {
                                        ExpressiveHaptics.playSpringTap(context)
                                        stateManager.toggleSleepScheduleDay(dayOpt.calendarDay)
                                    }
                                    .testTag("sleep_day_chip_${dayOpt.calendarDay}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = dayOpt.shortLabel,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = dayFg
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBedtimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.sleepScheduleStartHour,
            initialMinute = state.sleepScheduleStartMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showBedtimePicker = false },
            title = {
                Text(
                    text = "Sleep Schedule Bedtime",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setSleepScheduleTime(
                            startHour = timePickerState.hour,
                            startMinute = timePickerState.minute,
                            endHour = state.sleepScheduleEndHour,
                            endMinute = state.sleepScheduleEndMinute
                        )
                        showBedtimePicker = false
                    },
                    modifier = Modifier.testTag("confirm_sleep_start_time")
                ) {
                    Text("Save Bedtime", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBedtimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showWakeUpPicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.sleepScheduleEndHour,
            initialMinute = state.sleepScheduleEndMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showWakeUpPicker = false },
            title = {
                Text(
                    text = "Sleep Schedule Wake-Up Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setSleepScheduleTime(
                            startHour = state.sleepScheduleStartHour,
                            startMinute = state.sleepScheduleStartMinute,
                            endHour = timePickerState.hour,
                            endMinute = timePickerState.minute
                        )
                        showWakeUpPicker = false
                    },
                    modifier = Modifier.testTag("confirm_sleep_end_time")
                ) {
                    Text("Save Wake-Up", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWakeUpPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SleepPresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SleepTimeButtonPill(
    label: String,
    timeText: String,
    subtitle: String,
    isBedtime: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 18.dp,
        animationSpec = ExpressiveMotion.snappySpatialSpring(),
        label = "sleep_time_pill_corner_$label"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = ExpressiveMotion.snappySpatialSpring(),
        label = "sleep_time_pill_scale_$label"
    )

    Surface(
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isBedtime) Icons.Rounded.Bedtime else Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = timeText,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
