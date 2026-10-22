package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonePaused
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.FocusStateManager
import com.example.ui.theme.FocusThemePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    stateManager: FocusStateManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val state by stateManager.state.collectAsState()
    val context = LocalContext.current
    var customTemplateText by remember(state.autoReplyMessageTemplate) {
        mutableStateOf(state.autoReplyMessageTemplate)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader("Theme Engine & Aesthetics")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ThemeToggleRow(
                            icon = Icons.Filled.Wallpaper,
                            title = stringResource(R.string.theme_follow_system),
                            subtitle = "Uses Android 12+ wallpaper colors dynamically",
                            checked = state.themePreferences.followSystemWallpaper,
                            onCheckedChange = { checked ->
                                stateManager.updateThemePreferences(state.themePreferences.copy(followSystemWallpaper = checked))
                            },
                            testTag = "toggle_dynamic_theme"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ThemeToggleRow(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.theme_profile_tint),
                            subtitle = "Subtle tonal shifts when Gaming, Sleep, Work, or DND is active",
                            checked = state.themePreferences.profileTintedAccent,
                            onCheckedChange = { checked ->
                                stateManager.updateThemePreferences(state.themePreferences.copy(profileTintedAccent = checked))
                            },
                            testTag = "toggle_profile_tint"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ThemeToggleRow(
                            icon = Icons.Filled.DarkMode,
                            title = stringResource(R.string.theme_amoled_black),
                            subtitle = "Pure black background & surface preserving dynamic accents",
                            checked = state.themePreferences.amoledPureBlack,
                            onCheckedChange = { checked ->
                                stateManager.updateThemePreferences(state.themePreferences.copy(amoledPureBlack = checked))
                            },
                            testTag = "toggle_amoled_black"
                        )
                    }
                }
            }

            item {
                SectionHeader("Call Screening & Breakthrough Engine")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ThemeToggleRow(
                            icon = Icons.Filled.PhonePaused,
                            title = "Starred Contacts Bypass",
                            subtitle = "Allows priority callers through cellular and VoIP shields",
                            checked = state.allowStarredContactsOnly,
                            onCheckedChange = { checked ->
                                stateManager.updateProfileSettings(checked, state.blockRepeatedCallers, state.silenceNotifications)
                            },
                            testTag = "toggle_starred_only"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ThemeToggleRow(
                            icon = Icons.Filled.Timer,
                            title = "3-Minute Repeat Breakthrough",
                            subtitle = "Second call within 180 seconds rings out loud for emergency breakthrough",
                            checked = state.allowRepeatCallersEnabled,
                            onCheckedChange = { checked ->
                                stateManager.setAllowRepeatCallersEnabled(checked)
                            },
                            testTag = "toggle_repeat_breakthrough"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ThemeToggleRow(
                            icon = Icons.Filled.Shield,
                            title = "Strict Gaming Lockdown",
                            subtitle = "Block even starred contacts and VoIP calls during Gaming mode",
                            checked = state.strictGamingLockdownEnabled,
                            onCheckedChange = { checked ->
                                stateManager.setStrictGamingLockdownEnabled(checked)
                            },
                            testTag = "toggle_gaming_lockdown"
                        )
                    }
                }
            }

            item {
                SectionHeader("Call Rejection Auto-Reply (RemoteInput)")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ThemeToggleRow(
                            icon = Icons.Filled.Chat,
                            title = "Auto-Reply on Declined Calls",
                            subtitle = "Notifies callers via in-app message when their call is declined during focus",
                            checked = state.autoReplyEnabled,
                            onCheckedChange = { checked ->
                                stateManager.setAutoReplyEnabled(checked)
                            },
                            testTag = "toggle_auto_reply"
                        )

                        if (state.autoReplyEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Text(
                                text = "Auto-Reply Template (%s = Active Profile)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            OutlinedTextField(
                                value = customTemplateText,
                                onValueChange = { newText ->
                                    customTemplateText = newText
                                    stateManager.setAutoReplyMessageTemplate(newText)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auto_reply_template_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader("Lock Screen & System Services")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ThemeToggleRow(
                            icon = Icons.Filled.Wallpaper,
                            title = stringResource(R.string.dim_lock_screen),
                            subtitle = stringResource(R.string.lockscreen_wallpaper_desc),
                            checked = state.dimLockScreenEnabled,
                            onCheckedChange = { checked ->
                                stateManager.setDimLockScreenEnabled(checked)
                            },
                            testTag = "toggle_dim_lock_screen"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "System DND & Notification Access",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Required for automatic DND sync and VoIP call screening",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Manage")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification Listener Access",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Enables VoIP call hangup & smart breakthrough filtering",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Manage")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}
