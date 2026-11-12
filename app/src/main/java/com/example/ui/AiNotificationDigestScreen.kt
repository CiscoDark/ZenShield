package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AiDigestScope
import com.example.core.AiNotificationDigest
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import com.example.core.GeminiModelPreset
import com.example.core.GlobalFocusRuleAction
import com.example.core.MissedNotificationEntity
import com.example.core.NotificationPriorityLevel
import com.example.core.SenderDigestGroup
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standalone & Embeddable BYOK (Bring Your Own Key) Gemini API Key Vault Card.
 *
 * Used in both the System tab and inside [AiNotificationDigestDashboardCard] so users can:
 * - Enter their personal Gemini API key with masked input & visibility toggle
 * - Switch between Gemini 2.5 Flash, Gemini 2.5 Pro, and Gemini 2.5 Flash-Lite presets
 * - Save their key locally on-device, run a live "Verify Key" connection check, or "Clear Key"
 */
@Composable
fun ByokGeminiKeyVaultCard(
    stateManager: FocusStateManager,
    missedLogViewModel: MissedNotificationLogViewModel,
    modifier: Modifier = Modifier,
    isEmbeddedInDigestCard: Boolean = false
) {
    val context = LocalContext.current
    val sessionState by stateManager.state.collectAsState()
    val verificationState by missedLogViewModel.verificationState.collectAsStateWithLifecycle()

    var keyDraft by rememberSaveable(sessionState.userGeminiApiKey) {
        mutableStateOf(sessionState.userGeminiApiKey)
    }
    var showRawKey by rememberSaveable { mutableStateOf(false) }

    val hasSavedKey = sessionState.hasUserGeminiApiKey
    val selectedPreset = sessionState.selectedGeminiModelPreset

    Surface(
        shape = RoundedCornerShape(if (isEmbeddedInDigestCard) 20.dp else 28.dp),
        color = if (isEmbeddedInDigestCard) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (hasSavedKey) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (isEmbeddedInDigestCard) "byok_embedded_vault_panel" else "byok_system_vault_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasSavedKey) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = "BYOK Gemini Key Vault",
                                tint = if (hasSavedKey) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "BYOK Gemini API Key Vault",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasSavedKey) {
                                "Active Key: ${sessionState.maskedUserGeminiApiKey} • Stored locally on device"
                            } else {
                                "Strictly BYOK • Requires your personal Google Gemini API key"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (hasSavedKey) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = if (hasSavedKey) "BYOK READY" else "KEY REQUIRED",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (hasSavedKey) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Model Preset Selector Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Gemini Model Preset",
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
                    GeminiModelPreset.entries.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                missedLogViewModel.selectGeminiModelPreset(preset)
                            },
                            label = {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("byok_model_chip_${preset.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = "${selectedPreset.modelId} — ${selectedPreset.description}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Masked API Key Input Field
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text("Your Personal Gemini API Key (BYOK)") },
                placeholder = { Text("Paste AIza... key from Google AI Studio") },
                singleLine = true,
                visualTransformation = if (showRawKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showRawKey = !showRawKey },
                            modifier = Modifier.testTag("byok_toggle_key_visibility_button")
                        ) {
                            Icon(
                                imageVector = if (showRawKey) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (showRawKey) "Hide API Key" else "Show API Key"
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("byok_api_key_input")
            )

            // Save Key, Verify Key, and Clear Key Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        missedLogViewModel.saveUserApiKey(keyDraft)
                    },
                    enabled = keyDraft.trim().isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("byok_save_key_button")
                ) {
                    Text(
                        text = "Save Key",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        missedLogViewModel.verifyUserApiKey(keyDraft)
                    },
                    enabled = verificationState !is ByokKeyVerificationState.Verifying &&
                        (keyDraft.trim().isNotEmpty() || hasSavedKey),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("byok_verify_key_button")
                ) {
                    if (verificationState is ByokKeyVerificationState.Verifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Testing...",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Verify Key",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (hasSavedKey || keyDraft.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            keyDraft = ""
                            missedLogViewModel.clearUserApiKey()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("byok_clear_key_button")
                    ) {
                        Text(
                            text = "Clear Key",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Verification Status Banner
            AnimatedVisibility(
                visible = verificationState is ByokKeyVerificationState.Verified ||
                    verificationState is ByokKeyVerificationState.Invalid,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val isVerified = verificationState is ByokKeyVerificationState.Verified
                val statusMessage = when (val st = verificationState) {
                    is ByokKeyVerificationState.Verified -> st.message
                    is ByokKeyVerificationState.Invalid -> st.errorMessage
                    else -> ""
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isVerified) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("byok_verification_status_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isVerified) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.ErrorOutline
                            },
                            contentDescription = null,
                            tint = if (isVerified) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isVerified) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * AI Notification Executive Digest & Missed Notification Log Summary Card
 *
 * Strictly BYOK (Bring Your Own Key):
 * - Uses the user's personal Gemini API key and selected [GeminiModelPreset]
 * - Embeds a collapsible/expandable [ByokGeminiKeyVaultCard] right inside the card
 * - Supports Auto-Summarize on Session End and on-demand "Generate AI Summary"
 */
@Composable
fun AiNotificationDigestDashboardCard(
    stateManager: FocusStateManager,
    missedLogViewModel: MissedNotificationLogViewModel,
    onOpenMissedLogScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionState by stateManager.state.collectAsState()
    val digestUiState by missedLogViewModel.digestUiState.collectAsStateWithLifecycle()
    val latestDigest by missedLogViewModel.latestDigest.collectAsStateWithLifecycle()
    val allMissed by missedLogViewModel.allMissedNotifications.collectAsStateWithLifecycle()
    val unreadMissed by missedLogViewModel.unreadMissedNotifications.collectAsStateWithLifecycle()
    val selectedScope by missedLogViewModel.selectedDigestScope.collectAsStateWithLifecycle()

    var isByokVaultExpanded by rememberSaveable {
        mutableStateOf(!sessionState.hasUserGeminiApiKey)
    }

    // Auto-expand the BYOK vault if user clears their key
    LaunchedEffect(sessionState.hasUserGeminiApiKey) {
        if (!sessionState.hasUserGeminiApiKey) {
            isByokVaultExpanded = true
        }
    }

    val activeDigest = (digestUiState as? AiDigestUiState.Success)?.digest ?: latestDigest
    val bouncedCallsCount = allMissed.count { it.isCallBounce }
    val silencedAlertsCount = allMissed.count { !it.isCallBounce }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_notification_digest_dashboard_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row with BYOK model badge & Unread count pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "AI Notification Digest",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "AI Notification Digest",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "BYOK • ${sessionState.selectedGeminiModelPreset.badgeLabel}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Strictly BYOK • Uses your personal Gemini API key",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (unreadMissed.isNotEmpty()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = "${unreadMissed.size} UNREAD",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (unreadMissed.isNotEmpty()) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Collapsible BYOK Key Status Bar + Expandable BYOK Vault
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (sessionState.hasUserGeminiApiKey) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        ExpressiveHaptics.playSpringTap(context)
                        isByokVaultExpanded = !isByokVaultExpanded
                    }
                    .testTag("byok_vault_expand_toggle")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = if (sessionState.hasUserGeminiApiKey) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (sessionState.hasUserGeminiApiKey) {
                                "BYOK Key Active (${sessionState.maskedUserGeminiApiKey}) • ${sessionState.selectedGeminiModelPreset.displayName}"
                            } else {
                                "Personal Gemini API Key Required (BYOK) • Tap to configure"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (isByokVaultExpanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = if (isByokVaultExpanded) "Collapse BYOK Vault" else "Expand BYOK Vault",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isByokVaultExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ByokGeminiKeyVaultCard(
                    stateManager = stateManager,
                    missedLogViewModel = missedLogViewModel,
                    isEmbeddedInDigestCard = true
                )
            }

            // Telemetry KPI strip (Total Missed, Unread, Bounced Calls)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DigestTelemetryPill(
                    value = silencedAlertsCount.toString(),
                    label = "Missed Alerts",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                DigestTelemetryPill(
                    value = bouncedCallsCount.toString(),
                    label = "Bounced Calls",
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                DigestTelemetryPill(
                    value = unreadMissed.size.toString(),
                    label = "Pending Review",
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Auto-summarize on Session End Toggle
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Summarize on Session End",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically generate an AI Executive Digest with your BYOK key when a Focus session ends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = sessionState.autoSummarizeMissedOnSessionEnd,
                        onCheckedChange = { enabled ->
                            ExpressiveHaptics.playSpringTap(context)
                            missedLogViewModel.setAutoSummarizeOnSessionEnd(enabled)
                        },
                        modifier = Modifier.testTag("ai_digest_auto_summarize_toggle")
                    )
                }
            }

            // Digest Scope Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AiDigestScope.entries.forEach { scope ->
                    val isSelected = selectedScope == scope
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            missedLogViewModel.selectDigestScope(scope)
                        },
                        label = {
                            Text(
                                text = scope.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("ai_digest_scope_chip_${scope.name.lowercase()}")
                    )
                }
            }

            // Action Buttons: Generate AI Summary + Log Sample Alerts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        if (!sessionState.hasUserGeminiApiKey) {
                            isByokVaultExpanded = true
                        }
                        missedLogViewModel.generateAiDigest(selectedScope)
                    },
                    enabled = digestUiState !is AiDigestUiState.Loading,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("ai_generate_digest_button")
                ) {
                    if (digestUiState is AiDigestUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Summarizing...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate AI Summary",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        missedLogViewModel.logSampleMissedNotificationsBatch()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("log_sample_missed_alerts_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = "Simulate Missed Batch",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+5 Alerts",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Error State Banner
            AnimatedVisibility(
                visible = digestUiState is AiDigestUiState.Error,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val errorMessage = (digestUiState as? AiDigestUiState.Error)?.message.orEmpty()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_digest_error_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                if (!sessionState.hasUserGeminiApiKey) {
                                    isByokVaultExpanded = true
                                } else {
                                    missedLogViewModel.generateAiDigest(selectedScope)
                                }
                            }
                        ) {
                            Text(
                                text = if (!sessionState.hasUserGeminiApiKey) "Set Key" else "Retry",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Executive Digest Result Content
            if (activeDigest != null) {
                ExecutiveDigestStructuredPanel(digest = activeDigest)
            } else if (digestUiState !is AiDigestUiState.Loading) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (allMissed.isEmpty()) {
                                "No missed notifications logged yet"
                            } else {
                                "Ready to summarize ${allMissed.size} missed notifications & calls"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (allMissed.isEmpty()) {
                                "When Sentinal silences distractions or bounces calls during a Focus session, they are persisted in your local Room database. Tap '+5 Alerts' above to test with realistic alerts, then tap 'Generate AI Summary' with your personal Gemini API key."
                            } else {
                                "Tap 'Generate AI Summary' above or finish your active Focus session to let ${sessionState.selectedGeminiModelPreset.displayName} build an Executive Digest using your personal BYOK Gemini API key."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recent Missed Alerts Preview (Top 3)
            if (allMissed.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recent Missed Log (${allMissed.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Room v2 Persistent",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allMissed.take(3).forEach { entry ->
                        CompactMissedNotificationRow(entry = entry)
                    }
                }
            }

            // Open Full Missed Notification Log Explorer Button
            OutlinedButton(
                onClick = {
                    ExpressiveHaptics.playSpringTap(context)
                    onOpenMissedLogScreen()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_missed_notification_log_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Missed Notification Log (${allMissed.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DigestTelemetryPill(
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Renders the structured Executive Digest (`headline`, `urgentActionItems`, `senderGroups`, `focusTip`).
 */
@Composable
fun ExecutiveDigestStructuredPanel(
    digest: AiNotificationDigest,
    modifier: Modifier = Modifier
) {
    val totalAnalyzed = digest.totalNotificationsAnalyzed + digest.totalCallsAnalyzed

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_executive_digest_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Executive Headline + Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "EXECUTIVE DIGEST • ${digest.focusProfileDisplayName.uppercase()}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "$totalAnalyzed items • ${digest.formattedGeneratedTime}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = digest.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Urgent Action Items Checklist
            if (digest.urgentActionItems.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlaylistAddCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Urgent Action Items (${digest.urgentActionItems.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        digest.urgentActionItems.forEach { actionItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(15.dp)
                                )
                                Text(
                                    text = actionItem,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Sender / App Digest Groups
            if (digest.senderGroups.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Grouped by Sender & App",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    digest.senderGroups.forEach { group ->
                        SenderDigestGroupRow(group = group)
                    }
                }
            }

            // Focus Tip Footer
            if (digest.focusTip.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = digest.focusTip,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SenderDigestGroupRow(
    group: SenderDigestGroup,
    modifier: Modifier = Modifier
) {
    val priorityColor = when {
        group.categoryLabel.contains("Call", ignoreCase = true) ||
            group.categoryLabel.contains("Urgent", ignoreCase = true) -> MaterialTheme.colorScheme.error
        group.categoryLabel.contains("Work", ignoreCase = true) ||
            group.categoryLabel.contains("Direct", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = group.senderOrApp,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = priorityColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = group.categoryLabel.uppercase(),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        val itemCount = group.count
                        Text(
                            text = "${itemCount}x",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = group.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactMissedNotificationRow(
    entry: MissedNotificationEntity,
    modifier: Modifier = Modifier
) {
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (entry.isCallBounce) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (entry.isCallBounce) {
                            Icons.Rounded.PhoneCallback
                        } else {
                            Icons.Rounded.NotificationsPaused
                        },
                        contentDescription = null,
                        tint = if (entry.isCallBounce) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.appName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• ${entry.senderOrTitle}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = entry.messagePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = timeFormatter.format(Date(entry.timestampMillis)),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Full-Screen Missed Notification & Bounced Call Log Explorer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedNotificationLogScreen(
    stateManager: FocusStateManager,
    missedLogViewModel: MissedNotificationLogViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val filterState by missedLogViewModel.filterState.collectAsStateWithLifecycle()
    val filteredItems by missedLogViewModel.filteredMissedNotifications.collectAsStateWithLifecycle()
    val allItems by missedLogViewModel.allMissedNotifications.collectAsStateWithLifecycle()
    val availableApps by missedLogViewModel.availableApps.collectAsStateWithLifecycle()

    BackHandler {
        ExpressiveHaptics.playSpringTap(context)
        onBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("missed_notification_log_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Missed Notification Log",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${filteredItems.size} of ${allItems.size} intercepted alerts & bounced calls",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            ExpressiveHaptics.playSpringTap(context)
                            onBack()
                        },
                        modifier = Modifier.testTag("missed_log_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (allItems.any { !it.isReviewed }) {
                        IconButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                missedLogViewModel.markAllReviewed()
                            },
                            modifier = Modifier.testTag("missed_log_mark_all_reviewed_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = "Mark All Reviewed",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (allItems.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                ExpressiveHaptics.playSpringTap(context)
                                missedLogViewModel.clearAllMissedItems()
                            },
                            modifier = Modifier.testTag("missed_log_clear_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear Missed Notification Log",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = filterState.searchQuery,
                    onValueChange = { missedLogViewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text("Search sender, message, app, or Focus mode...")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search Missed Log"
                        )
                    },
                    trailingIcon = {
                        if (filterState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { missedLogViewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("missed_log_search_input")
                )
            }

            // Filter Section: Focus Mode
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Filter by Focus Mode",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (filterState.selectedFocusProfile != null ||
                            filterState.selectedPriorityLevel != null ||
                            filterState.selectedAppFilter != null ||
                            filterState.showUnreadOnly ||
                            filterState.searchQuery.isNotEmpty()
                        ) {
                            Text(
                                text = "Reset Filters",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        ExpressiveHaptics.playSpringTap(context)
                                        missedLogViewModel.clearAllFilters()
                                    }
                                    .testTag("missed_log_reset_filters_button")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterState.selectedFocusProfile == null,
                            onClick = { missedLogViewModel.selectFocusProfileFilter(null) },
                            label = { Text("All Modes") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("filter_mode_all")
                        )
                        FocusProfile.entries.filter { it != FocusProfile.OFF }.forEach { profile ->
                            val isSelected = filterState.selectedFocusProfile == profile
                            FilterChip(
                                selected = isSelected,
                                onClick = { missedLogViewModel.selectFocusProfileFilter(profile) },
                                label = { Text(profile.displayName) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("filter_mode_${profile.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Filter Section: Priority Tier
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Filter by Priority Tier",
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
                        FilterChip(
                            selected = filterState.selectedPriorityLevel == null,
                            onClick = { missedLogViewModel.selectPriorityLevelFilter(null) },
                            label = { Text("All Priorities") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("filter_priority_all")
                        )
                        NotificationPriorityLevel.entries.forEach { level ->
                            val isSelected = filterState.selectedPriorityLevel == level
                            FilterChip(
                                selected = isSelected,
                                onClick = { missedLogViewModel.selectPriorityLevelFilter(level) },
                                label = { Text(level.badgeLabel) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("filter_priority_${level.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Filter Section: Source App & Calls Only
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Filter by Source App / Type",
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
                        FilterChip(
                            selected = filterState.selectedAppFilter == null && !filterState.showUnreadOnly,
                            onClick = {
                                missedLogViewModel.selectAppFilter(null)
                                missedLogViewModel.setShowUnreadOnly(false)
                            },
                            label = { Text("All Sources") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("filter_app_all")
                        )
                        FilterChip(
                            selected = filterState.showUnreadOnly,
                            onClick = { missedLogViewModel.setShowUnreadOnly(!filterState.showUnreadOnly) },
                            label = { Text("Unread Only") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("filter_unread_only")
                        )
                        FilterChip(
                            selected = filterState.selectedAppFilter == MissedNotificationLogViewModel.APP_FILTER_CALLS_ONLY,
                            onClick = {
                                missedLogViewModel.selectAppFilter(
                                    MissedNotificationLogViewModel.APP_FILTER_CALLS_ONLY
                                )
                            },
                            label = { Text("Bounced Calls Only") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("filter_calls_only")
                        )
                        availableApps.forEach { (pkg, appLabel) ->
                            val isSelected = filterState.selectedAppFilter == pkg
                            FilterChip(
                                selected = isSelected,
                                onClick = { missedLogViewModel.selectAppFilter(pkg) },
                                label = { Text(appLabel) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("filter_app_${pkg.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Embedded AI Executive Digest Card inside the full-screen log
            item {
                AiNotificationDigestDashboardCard(
                    stateManager = stateManager,
                    missedLogViewModel = missedLogViewModel,
                    onOpenMissedLogScreen = {}
                )
            }

            // Chronological Missed Notification Log Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Intercepted Notifications & Calls (${filteredItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (allItems.isEmpty()) {
                        TextButton(
                            onClick = { missedLogViewModel.logSampleMissedNotificationsBatch() }
                        ) {
                            Text("Load Sample Batch")
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("missed_log_empty_state"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsPaused,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (allItems.isEmpty()) {
                                    "Your Missed Notification Log is clean"
                                } else {
                                    "No missed notifications match your current filters"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (allItems.isEmpty()) {
                                    "Alerts silenced during DND, Gaming, Work, Sleep, or Cinema mode—and calls bounced by Call Bouncer—are automatically logged here."
                                } else {
                                    "Try clearing your search query or switching the Focus mode / priority filter chips above."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredItems,
                    key = { it.id }
                ) { item ->
                    DetailedMissedNotificationCard(
                        item = item,
                        onMarkReviewed = { missedLogViewModel.markReviewed(item.id) },
                        onDelete = { missedLogViewModel.deleteMissedItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailedMissedNotificationCard(
    item: MissedNotificationEntity,
    onMarkReviewed: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateTimeFormatter = SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
    val priorityLevel = runCatching { NotificationPriorityLevel.valueOf(item.priorityLevelName) }
        .getOrDefault(NotificationPriorityLevel.MEDIUM)
    val appliedAction = runCatching { GlobalFocusRuleAction.valueOf(item.appliedActionName) }
        .getOrDefault(GlobalFocusRuleAction.MUTE_SILENT)

    val badgeColor = when (priorityLevel) {
        NotificationPriorityLevel.CRITICAL -> MaterialTheme.colorScheme.error
        NotificationPriorityLevel.HIGH -> MaterialTheme.colorScheme.tertiary
        NotificationPriorityLevel.MEDIUM -> MaterialTheme.colorScheme.primary
        NotificationPriorityLevel.LOW -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("missed_log_item_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isReviewed) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isReviewed) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            } else {
                badgeColor.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (item.isCallBounce) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (item.isCallBounce) {
                                    Icons.Rounded.CallMissed
                                } else {
                                    Icons.Rounded.NotificationsPaused
                                },
                                contentDescription = null,
                                tint = if (item.isCallBounce) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.appName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = item.focusProfileDisplayName,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = badgeColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = priorityLevel.badgeLabel,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = item.senderOrTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!item.isReviewed) {
                        IconButton(
                            onClick = onMarkReviewed,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Mark Reviewed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete Entry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = item.messagePreview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (item.isCallBounce) {
                        "Call Bouncer • ${item.focusProfileDisplayName}"
                    } else {
                        "${appliedAction.badgeText} • ${item.focusProfileDisplayName}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateTimeFormatter.format(Date(item.timestampMillis)),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
