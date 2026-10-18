package com.example.core

import android.app.NotificationManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.DndSeedColor
import com.example.ui.theme.GamingSeedColor
import com.example.ui.theme.SleepSeedColor
import com.example.ui.theme.WorkSeedColor

enum class FocusProfile(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val defaultAllowStarredOnly: Boolean,
    val defaultBlockRepeatedCalls: Boolean,
    val defaultSilenceNotifications: Boolean,
    val defaultDimLockScreen: Boolean,
    val interruptionFilter: Int
) {
    OFF(
        id = "OFF",
        displayName = "Off",
        subtitle = "Standard notifications and calls",
        icon = Icons.Filled.PowerSettingsNew,
        accentColor = Color(0xFF78909C),
        defaultAllowStarredOnly = false,
        defaultBlockRepeatedCalls = false,
        defaultSilenceNotifications = false,
        defaultDimLockScreen = false,
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    ),

    DO_NOT_DISTURB(
        id = "DO_NOT_DISTURB",
        displayName = "Do Not Disturb",
        subtitle = "Block distractions, allow priority contacts",
        icon = Icons.Filled.DoNotDisturbOn,
        accentColor = DndSeedColor,
        defaultAllowStarredOnly = true,
        defaultBlockRepeatedCalls = false,
        defaultSilenceNotifications = true,
        defaultDimLockScreen = true,
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
    ),

    GAMING(
        id = "GAMING",
        displayName = "Gaming",
        subtitle = "Zero latency, shield incoming popups & spam calls",
        icon = Icons.Filled.SportsEsports,
        accentColor = GamingSeedColor,
        defaultAllowStarredOnly = true,
        defaultBlockRepeatedCalls = true,
        defaultSilenceNotifications = true,
        defaultDimLockScreen = false,
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
    ),

    WORK(
        id = "WORK",
        displayName = "Deep Work",
        subtitle = "Allow essential work alerts, suppress social noise",
        icon = Icons.Filled.Work,
        accentColor = WorkSeedColor,
        defaultAllowStarredOnly = false,
        defaultBlockRepeatedCalls = false,
        defaultSilenceNotifications = true,
        defaultDimLockScreen = false,
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
    ),

    SLEEP(
        id = "SLEEP",
        displayName = "Sleep",
        subtitle = "Silenced alerts, dim dark slate lock screen",
        icon = Icons.Filled.Bedtime,
        accentColor = SleepSeedColor,
        defaultAllowStarredOnly = true,
        defaultBlockRepeatedCalls = false,
        defaultSilenceNotifications = true,
        defaultDimLockScreen = true,
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
    );

    companion object {
        fun fromId(id: String?): FocusProfile {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OFF
        }
    }
}

enum class DurationOption(val label: String, val durationMinutes: Long?) {
    UNTIL_TURNED_OFF("Until Turned Off", null),
    FOR_45_MINUTES("For 45 min", 45L),
    FOR_ONE_HOUR("For 1 Hour", 60L),
    FOR_TWO_HOURS("For 2 Hours", 120L)
}
