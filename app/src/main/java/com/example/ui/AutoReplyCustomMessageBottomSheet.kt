package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FocusProfile
import com.example.core.FocusStateManager

private enum class AutoReplyTab(val title: String, val profile: FocusProfile?) {
    UNIVERSAL("Universal", null),
    DND("DND", FocusProfile.DO_NOT_DISTURB),
    GAMING("Gaming", FocusProfile.GAMING),
    WORK("Work", FocusProfile.WORK),
    SLEEP("Sleep", FocusProfile.SLEEP)
}

private data class MessagePreset(
    val title: String,
    val iconEmoji: String,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoReplyCustomMessageBottomSheet(
    stateManager: FocusStateManager,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val state by stateManager.state.collectAsState()
    var selectedTab by remember { mutableStateOf(AutoReplyTab.UNIVERSAL) }

    // Universal message state
    var universalText by remember(state.autoReplyMessageTemplate) {
        mutableStateOf(state.autoReplyMessageTemplate)
    }

    // Per-profile message states
    var dndText by remember(state.perProfileAutoReplyMessages[FocusProfile.DO_NOT_DISTURB]) {
        mutableStateOf(state.perProfileAutoReplyMessages[FocusProfile.DO_NOT_DISTURB] ?: "")
    }
    var gamingText by remember(state.perProfileAutoReplyMessages[FocusProfile.GAMING]) {
        mutableStateOf(state.perProfileAutoReplyMessages[FocusProfile.GAMING] ?: "")
    }
    var workText by remember(state.perProfileAutoReplyMessages[FocusProfile.WORK]) {
        mutableStateOf(state.perProfileAutoReplyMessages[FocusProfile.WORK] ?: "")
    }
    var sleepText by remember(state.perProfileAutoReplyMessages[FocusProfile.SLEEP]) {
        mutableStateOf(state.perProfileAutoReplyMessages[FocusProfile.SLEEP] ?: "")
    }

    val presets = remember {
        listOf(
            MessagePreset(
                title = "Standard Shield",
                iconEmoji = "🛡️",
                text = FocusStateManager.DEFAULT_AUTO_REPLY_TEMPLATE
            ),
            MessagePreset(
                title = "Gaming Mode",
                iconEmoji = "🎮",
                text = "🎮 In a competitive gaming match! Notifications are paused. Reply 'URGENT' if it cannot wait."
            ),
            MessagePreset(
                title = "Deep Work",
                iconEmoji = "💼",
                text = "💼 Deep focus session in progress (%s). Notifications are muted. Reply 'URGENT' for emergencies."
            ),
            MessagePreset(
                title = "Sleeping / Night",
                iconEmoji = "🌙",
                text = "🌙 Sleeping / Offline. Do not disturb unless urgent. Reply 'URGENT' to breakthrough."
            ),
            MessagePreset(
                title = "Concise & Polite",
                iconEmoji = "⚡",
                text = "Busy in %s Focus mode right now. I will respond as soon as I am free!"
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.testTag("auto_reply_bottom_sheet"),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Chat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Auto-Reply Message",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sent when calls are declined during focus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.testTag("btn_close_auto_reply_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Tab row to choose Universal vs Per-Profile override
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                AutoReplyTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                when (tab) {
                                    AutoReplyTab.UNIVERSAL -> Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                    AutoReplyTab.DND -> Icon(Icons.Rounded.DoNotDisturbOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    AutoReplyTab.GAMING -> Icon(Icons.Rounded.SportsEsports, contentDescription = null, modifier = Modifier.size(16.dp))
                                    AutoReplyTab.WORK -> Icon(Icons.Rounded.Work, contentDescription = null, modifier = Modifier.size(16.dp))
                                    AutoReplyTab.SLEEP -> Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = tab.title,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                }
            }

            // Main Content Area with Scroll
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val currentProfile = selectedTab.profile

                if (currentProfile == null) {
                    // Universal Template
                    Text(
                        text = "Universal Template",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Used for all Focus modes unless an individual profile below has a custom override. Use '%s' or '{profile}' where the active mode name should appear.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = universalText,
                        onValueChange = {
                            universalText = it
                            stateManager.setAutoReplyMessageTemplate(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_reply_text_input"),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        placeholder = {
                            Text("Type your auto-reply text...")
                        }
                    )

                    // Quick Insert helper chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable {
                                val newText = if (universalText.contains("%s")) universalText else "$universalText %s"
                                universalText = newText
                                stateManager.setAutoReplyMessageTemplate(newText)
                            }
                        ) {
                            Text(
                                text = "+ Insert %s (Mode)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.clickable {
                                val newText = "$universalText Reply 'URGENT' to break through."
                                universalText = newText
                                stateManager.setAutoReplyMessageTemplate(newText)
                            }
                        ) {
                            Text(
                                text = "+ 'Reply URGENT'",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.clickable {
                                universalText = FocusStateManager.DEFAULT_AUTO_REPLY_TEMPLATE
                                stateManager.setAutoReplyMessageTemplate(FocusStateManager.DEFAULT_AUTO_REPLY_TEMPLATE)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Reset Default",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                } else {
                    // Profile-specific override
                    val isCustom = when (currentProfile) {
                        FocusProfile.DO_NOT_DISTURB -> dndText.isNotBlank()
                        FocusProfile.GAMING -> gamingText.isNotBlank()
                        FocusProfile.WORK -> workText.isNotBlank()
                        FocusProfile.SLEEP -> sleepText.isNotBlank()
                        FocusProfile.OFF -> false
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Custom message for ${currentProfile.displayName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isCustom) "Dedicated text overrides universal template" else "Currently inheriting universal template",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isCustom,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val defaultForProfile = stateManager.getAutoReplyMessage(currentProfile)
                                        when (currentProfile) {
                                            FocusProfile.DO_NOT_DISTURB -> { dndText = defaultForProfile; stateManager.setPerProfileAutoReplyMessage(currentProfile, defaultForProfile) }
                                            FocusProfile.GAMING -> { gamingText = defaultForProfile; stateManager.setPerProfileAutoReplyMessage(currentProfile, defaultForProfile) }
                                            FocusProfile.WORK -> { workText = defaultForProfile; stateManager.setPerProfileAutoReplyMessage(currentProfile, defaultForProfile) }
                                            FocusProfile.SLEEP -> { sleepText = defaultForProfile; stateManager.setPerProfileAutoReplyMessage(currentProfile, defaultForProfile) }
                                            FocusProfile.OFF -> {}
                                        }
                                    } else {
                                        when (currentProfile) {
                                            FocusProfile.DO_NOT_DISTURB -> { dndText = ""; stateManager.setPerProfileAutoReplyMessage(currentProfile, "") }
                                            FocusProfile.GAMING -> { gamingText = ""; stateManager.setPerProfileAutoReplyMessage(currentProfile, "") }
                                            FocusProfile.WORK -> { workText = ""; stateManager.setPerProfileAutoReplyMessage(currentProfile, "") }
                                            FocusProfile.SLEEP -> { sleepText = ""; stateManager.setPerProfileAutoReplyMessage(currentProfile, "") }
                                            FocusProfile.OFF -> {}
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("toggle_custom_${currentProfile.name.lowercase()}")
                            )
                        }
                    }

                    val currentText = when (currentProfile) {
                        FocusProfile.DO_NOT_DISTURB -> dndText
                        FocusProfile.GAMING -> gamingText
                        FocusProfile.WORK -> workText
                        FocusProfile.SLEEP -> sleepText
                        FocusProfile.OFF -> ""
                    }

                    if (isCustom) {
                        OutlinedTextField(
                            value = currentText,
                            onValueChange = { newText ->
                                when (currentProfile) {
                                    FocusProfile.DO_NOT_DISTURB -> { dndText = newText; stateManager.setPerProfileAutoReplyMessage(currentProfile, newText) }
                                    FocusProfile.GAMING -> { gamingText = newText; stateManager.setPerProfileAutoReplyMessage(currentProfile, newText) }
                                    FocusProfile.WORK -> { workText = newText; stateManager.setPerProfileAutoReplyMessage(currentProfile, newText) }
                                    FocusProfile.SLEEP -> { sleepText = newText; stateManager.setPerProfileAutoReplyMessage(currentProfile, newText) }
                                    FocusProfile.OFF -> {}
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auto_reply_profile_text_input"),
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Presets Section
                Text(
                    text = "Quick Presets (Tap to apply)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .clickable {
                                    if (currentProfile == null) {
                                        universalText = preset.text
                                        stateManager.setAutoReplyMessageTemplate(preset.text)
                                    } else {
                                        when (currentProfile) {
                                            FocusProfile.DO_NOT_DISTURB -> { dndText = preset.text; stateManager.setPerProfileAutoReplyMessage(currentProfile, preset.text) }
                                            FocusProfile.GAMING -> { gamingText = preset.text; stateManager.setPerProfileAutoReplyMessage(currentProfile, preset.text) }
                                            FocusProfile.WORK -> { workText = preset.text; stateManager.setPerProfileAutoReplyMessage(currentProfile, preset.text) }
                                            FocusProfile.SLEEP -> { sleepText = preset.text; stateManager.setPerProfileAutoReplyMessage(currentProfile, preset.text) }
                                            FocusProfile.OFF -> {}
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(preset.iconEmoji, fontSize = 14.sp)
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Live Preview Bubble
                Text(
                    text = "Live WhatsApp / Telegram Preview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val previewProfile = currentProfile ?: if (state.activeProfile != FocusProfile.OFF) state.activeProfile else FocusProfile.GAMING
                val previewMessage = stateManager.getAutoReplyMessage(previewProfile)

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF25D366), CircleShape)
                            )
                            Text(
                                text = "Recipient View (${previewProfile.displayName} Active)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // WhatsApp-styled chat bubble
                        Surface(
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .align(Alignment.End)
                                .fillMaxWidth(0.92f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = previewMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "12:45 PM",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.DoneAll,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        universalText = FocusStateManager.DEFAULT_AUTO_REPLY_TEMPLATE
                        stateManager.resetAutoReplyTemplateToDefault()
                        FocusProfile.values().forEach {
                            stateManager.setPerProfileAutoReplyMessage(it, "")
                        }
                        dndText = ""
                        gamingText = ""
                        workText = ""
                        sleepText = ""
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_reset_all_auto_reply"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Reset All")
                }

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("save_auto_reply_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Done")
                }
            }
        }
    }
}
