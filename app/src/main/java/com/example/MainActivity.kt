package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.services.GameDetectorService
import com.example.ui.AppWhitelistBottomSheet
import com.example.ui.AutoReplyCustomMessageBottomSheet
import com.example.ui.GameDetectionCard
import com.example.ui.WeeklyShieldStatsCard
import com.example.ui.theme.FocusThemePreferences
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.Typography
import kotlinx.coroutines.delay

enum class SentinalWorkspaceTab(
    val label: String,
    val icon: ImageVector,
    val tag: String
) {
    SHIELD("Shield", Icons.Rounded.Shield, "nav_tab_shield"),
    ANALYTICS("Analytics", Icons.Rounded.BarChart, "nav_tab_analytics"),
    RULES("Rules", Icons.Rounded.Tune, "nav_tab_rules"),
    SYSTEM("System", Icons.Rounded.Palette, "nav_tab_system")
}

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission updated
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val stateManager = FocusStateManager.getInstance(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val activeProfile by stateManager.activeProfileFlow.collectAsState()
            val state by stateManager.state.collectAsState()

            DynamicTheme(
                darkTheme = isSystemInDarkTheme(),
                dynamicColor = state.themePreferences.followSystemWallpaper,
                activeProfile = activeProfile,
                preferences = state.themePreferences
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZenShieldDashboardScreen(stateManager = stateManager)
                }
            }
        }
    }
}

/**
 * DynamicTheme: Composable wrapper in MainActivity utilizing the system's current color scheme
 * to support Material You design standards (Android 12+ dynamicLightColorScheme / dynamicDarkColorScheme).
 */
@Composable
fun DynamicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    activeProfile: FocusProfile = FocusProfile.OFF,
    preferences: FocusThemePreferences = FocusThemePreferences(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val dynamicColorScheme: ColorScheme? = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (!preferences.profileTintedAccent || activeProfile == FocusProfile.OFF)) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

    if (dynamicColorScheme != null) {
        val finalColorScheme = if (darkTheme && preferences.amoledPureBlack) {
            dynamicColorScheme.copy(
                surface = Color.Black,
                background = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF070A0F),
                surfaceContainer = Color(0xFF0F141E),
                surfaceContainerHigh = Color(0xFF171E2B),
                surfaceContainerHighest = Color(0xFF1F2838),
                surfaceDim = Color.Black
            )
        } else {
            dynamicColorScheme
        }

        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            content = content
        )
    } else {
        com.example.ui.theme.DynamicTheme(
            darkTheme = darkTheme,
            dynamicColor = dynamicColor,
            activeProfile = activeProfile,
            preferences = preferences,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZenShieldDashboardScreen(
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val state by stateManager.state.collectAsState()
    val activeProfile by stateManager.activeProfileFlow.collectAsState()
    val weeklySummary by stateManager.weeklyStatsFlow.collectAsState()
    val context = LocalContext.current

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = SentinalWorkspaceTab.entries[selectedTabIndex]

    var showWhitelistSheet by remember { mutableStateOf(false) }
    var showGamePickerSheet by remember { mutableStateOf(false) }
    var showAutoReplySheet by remember { mutableStateOf(false) }

    // Return to Shield Command Center when pressing Back from secondary tabs
    BackHandler(enabled = selectedTabIndex != 0) {
        selectedTabIndex = 0
    }

    // System Permissions & Access Trackers
    var hasNotificationListener by remember { mutableStateOf(checkNotificationListenerAccess(context)) }
    var hasDndAccess by remember { mutableStateOf(stateManager.isNotificationPolicyAccessGranted()) }
    var hasRuntimePermissions by remember { mutableStateOf(checkRuntimePermissions(context)) }
    var hasBatteryExemption by remember { mutableStateOf(checkBatteryOptimizationExemption(context)) }
    var hasUsageStatsAccess by remember { mutableStateOf(GameDetectorService.hasUsageStatsPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationListener = checkNotificationListenerAccess(context)
                hasDndAccess = stateManager.isNotificationPolicyAccessGranted()
                hasRuntimePermissions = checkRuntimePermissions(context)
                hasBatteryExemption = checkBatteryOptimizationExemption(context)
                hasUsageStatsAccess = GameDetectorService.hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRuntimePermissions = checkRuntimePermissions(context)
    }

    val grantedPermissionsCount = listOf(
        hasNotificationListener,
        hasDndAccess,
        hasRuntimePermissions,
        hasBatteryExemption,
        hasUsageStatsAccess
    ).count { it }

    val animatedHeaderColor by animateColorAsState(
        targetValue = when (activeProfile) {
            FocusProfile.OFF -> MaterialTheme.colorScheme.surfaceContainerHigh
            FocusProfile.DO_NOT_DISTURB -> MaterialTheme.colorScheme.primaryContainer
            FocusProfile.GAMING -> MaterialTheme.colorScheme.secondaryContainer
            FocusProfile.WORK -> MaterialTheme.colorScheme.tertiaryContainer
            FocusProfile.SLEEP -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 400),
        label = "header_color"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = when (selectedTab) {
                                    SentinalWorkspaceTab.SHIELD -> "Command Center & Focus Profiles"
                                    SentinalWorkspaceTab.ANALYTICS -> "7-Day Call & Alert Telemetry"
                                    SentinalWorkspaceTab.RULES -> "Call Bouncer & Auto-Reply Engine"
                                    SentinalWorkspaceTab.SYSTEM -> "Material You Studio & Permissions"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { showWhitelistSheet = true }
                            .testTag("app_whitelist_quick_action")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = "Open App Whitelist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Whitelist",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                SentinalWorkspaceTab.entries.forEachIndexed { index, tab ->
                    val isSelected = selectedTabIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "workspace_tab_transition"
        ) { currentTab ->
            when (currentTab) {
                SentinalWorkspaceTab.SHIELD -> {
                    ShieldCommandCenterTab(
                        state = state,
                        activeProfile = activeProfile,
                        headerBgColor = animatedHeaderColor,
                        weeklySilencedTotal = weeklySummary.totalSilencedNotifications,
                        weeklyCallsTotal = weeklySummary.totalDeclinedCalls,
                        grantedPermissionsCount = grantedPermissionsCount,
                        stateManager = stateManager,
                        onOpenAnalyticsTab = { selectedTabIndex = SentinalWorkspaceTab.ANALYTICS.ordinal },
                        onOpenRulesTab = { selectedTabIndex = SentinalWorkspaceTab.RULES.ordinal },
                        onOpenSystemTab = { selectedTabIndex = SentinalWorkspaceTab.SYSTEM.ordinal }
                    )
                }

                SentinalWorkspaceTab.ANALYTICS -> {
                    AnalyticsTelemetryTab(
                        state = state,
                        stateManager = stateManager
                    )
                }

                SentinalWorkspaceTab.RULES -> {
                    RulesAndBouncerTab(
                        state = state,
                        stateManager = stateManager,
                        onOpenAutoReplySheet = { showAutoReplySheet = true },
                        onOpenGamePickerSheet = { showGamePickerSheet = true }
                    )
                }

                SentinalWorkspaceTab.SYSTEM -> {
                    SystemAndThemeTab(
                        state = state,
                        stateManager = stateManager,
                        hasNotificationListener = hasNotificationListener,
                        hasDndAccess = hasDndAccess,
                        hasRuntimePermissions = hasRuntimePermissions,
                        hasBatteryExemption = hasBatteryExemption,
                        hasUsageStatsAccess = hasUsageStatsAccess,
                        grantedPermissionsCount = grantedPermissionsCount,
                        onRequestRuntimePermissions = {
                            val perms = mutableListOf(Manifest.permission.READ_CONTACTS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionsLauncher.launch(perms.toTypedArray())
                        }
                    )
                }
            }
        }
    }

    if (showWhitelistSheet) {
        AppWhitelistBottomSheet(
            stateManager = stateManager,
            onDismissRequest = { showWhitelistSheet = false }
        )
    }

    if (showGamePickerSheet) {
        AppWhitelistBottomSheet(
            stateManager = stateManager,
            isGamePicker = true,
            onDismissRequest = { showGamePickerSheet = false }
        )
    }

    if (showAutoReplySheet) {
        AutoReplyCustomMessageBottomSheet(
            stateManager = stateManager,
            onDismissRequest = { showAutoReplySheet = false }
        )
    }
}

@Composable
private fun ShieldCommandCenterTab(
    state: FocusSessionState,
    activeProfile: FocusProfile,
    headerBgColor: Color,
    weeklySilencedTotal: Int,
    weeklyCallsTotal: Int,
    grantedPermissionsCount: Int,
    stateManager: FocusStateManager,
    onOpenAnalyticsTab: () -> Unit,
    onOpenRulesTab: () -> Unit,
    onOpenSystemTab: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Status Header Card
        item {
            HeroStatusHeaderCard(
                state = state,
                activeProfile = activeProfile,
                headerBgColor = headerBgColor,
                onDurationSelected = { duration ->
                    stateManager.setDuration(duration)
                },
                onAdjustMinutes = { deltaMinutes ->
                    stateManager.adjustRemainingTime(context, deltaMinutes.toLong())
                },
                onToggleFocus = {
                    stateManager.resetSessionCaches()
                    if (activeProfile == FocusProfile.OFF) {
                        stateManager.setProfile(FocusProfile.DO_NOT_DISTURB, state.durationOption)
                    } else {
                        stateManager.setProfile(FocusProfile.OFF)
                    }
                }
            )
        }

        // 2. Permission Readiness Banner (if any system permission is not yet granted)
        if (grantedPermissionsCount < 5) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSystemTab() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "System Guard Readiness: $grantedPermissionsCount/5 Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Tap to review Notification Listener, DND & Call Screening permissions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 2x2 Bento Focus Profile Selector Grid + Off Status Chip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Focus Mode Matrix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (activeProfile != FocusProfile.OFF) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .clickable {
                                stateManager.resetSessionCaches()
                                stateManager.setProfile(FocusProfile.OFF)
                            }
                            .testTag("profile_chip_off")
                    ) {
                        Text(
                            text = "Turn Off Shield",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FocusProfileBentoGrid(
                activeProfile = activeProfile,
                onProfileSelected = { profile ->
                    stateManager.resetSessionCaches()
                    stateManager.setProfile(profile, state.durationOption)
                }
            )
        }

        // 4. Compact Telemetry Snapshot Banner -> Jumps to Analytics Tab
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAnalyticsTab() },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.BarChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Weekly Telemetry Pulse",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Auto-replies restricted strictly to declined calls",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "Open Chart →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickStatPill(
                            label = "7-Day Alerts Silenced",
                            value = weeklySilencedTotal.toString(),
                            accentColor = Color(0xFF38EF7D),
                            icon = Icons.Rounded.NotificationsOff,
                            modifier = Modifier.weight(1f)
                        )
                        QuickStatPill(
                            label = "7-Day Calls Declined",
                            value = weeklyCallsTotal.toString(),
                            accentColor = Color(0xFF00D2FF),
                            icon = Icons.Rounded.PhoneCallback,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. Active Protection Summary Card -> Jumps to Rules Tab
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRulesTab() },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Call Bouncer & Game Auto-Lockdown",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append(if (state.autoReplyEnabled) "Call Auto-Reply ON" else "Call Auto-Reply OFF")
                                    append(" • ")
                                    append(if (state.autoGameDetectionEnabled) "Game Detector ON" else "Game Detector OFF")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Configure",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStatPill(
    label: String,
    value: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = value,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
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
}

@Composable
private fun AnalyticsTelemetryTab(
    state: FocusSessionState,
    stateManager: FocusStateManager
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Primary Recharts-Style Weekly Telemetry Card
        item {
            WeeklyShieldStatsCard(stateManager = stateManager)
        }

        // 2. Current Session Guard Policy Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Dispatch & Suppression Telemetry Policy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Declined Incoming Calls: Automatically dispatches your profile auto-reply message (30-min per-caller cooldown).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Outgoing Calls Placed by You: Tracked and exempted so recipients you dial never receive an automated focus reply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Incoming Chat & SMS Messages: Quietly silenced and dismissed during active Focus without sending automated replies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All-Time Blocked: ${state.totalShieldedCalls} Calls • ${state.totalShieldedNotifications} Alerts",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { stateManager.resetSessionCaches() }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Cooldowns")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesAndBouncerTab(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    onOpenAutoReplySheet: () -> Unit,
    onOpenGamePickerSheet: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Call Auto-Reply Customization Card
        item {
            VoipAutoReplyCustomizationCard(
                state = state,
                stateManager = stateManager,
                onOpenCustomizeSheet = onOpenAutoReplySheet
            )
        }

        // 2. Real-Time Game Launch Detector & 30s Exit Grace Countdown
        item {
            GameDetectionCard(
                state = state,
                stateManager = stateManager,
                onOpenAppPicker = onOpenGamePickerSheet
            )
        }

        // 3. Active Call & VoIP Bouncer
        item {
            ExpandableConfigCard(
                title = "Active Call & VoIP Bouncer",
                subtitle = "Network-level rejection & 3-minute repeat breakthrough",
                icon = Icons.Rounded.Phone,
                initiallyExpanded = true
            ) {
                ConfigSwitchRow(
                    title = "Decline All Calls Regardless of Condition",
                    subtitle = "Zero-tolerance lockdown: automatically drops all incoming cellular and VoIP calls, ignoring starred contacts and repeat callers",
                    checked = state.declineAllCallsRegardless,
                    onCheckedChange = { stateManager.setDeclineAllCallsRegardless(it) },
                    testTag = "switch_decline_all_calls_regardless"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Network-Level Call Rejection (Instant User Busy)",
                    subtitle = "Sends immediate busy signal to tower without ringing",
                    checked = state.networkLevelCallRejection,
                    onCheckedChange = { stateManager.setNetworkLevelCallRejectionEnabled(it) },
                    testTag = "switch_network_rejection"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Auto-Decline WhatsApp/Telegram Calls",
                    subtitle = "Hangs up third-party VoIP calls via programmatic action intent",
                    checked = state.autoDeclineVoipCalls,
                    onCheckedChange = { stateManager.setAutoDeclineVoipCallsEnabled(it) },
                    testTag = "switch_voip_bouncer"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "3-Minute Repeat Caller Breakthrough",
                    subtitle = if (state.declineAllCallsRegardless) "Overridden: Decline All Calls is active" else "Second call within 180s rings out loud for real emergencies",
                    checked = state.allowRepeatCallersEnabled && !state.declineAllCallsRegardless,
                    onCheckedChange = { stateManager.setAllowRepeatCallersEnabled(it) },
                    testTag = "switch_repeat_breakthrough"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Allow Starred Contacts",
                    subtitle = if (state.declineAllCallsRegardless) "Overridden: Decline All Calls is active" else "Passes priority contacts through cellular & VoIP filters",
                    checked = state.allowStarredContactsOnly && !state.declineAllCallsRegardless,
                    onCheckedChange = { checked ->
                        stateManager.updateProfileSettings(checked, state.blockRepeatedCallers, state.silenceNotifications)
                    },
                    testTag = "switch_allow_starred"
                )
            }
        }

        // 4. Gaming Mode Lockdown
        item {
            ExpandableConfigCard(
                title = "Gaming Mode Lockdown",
                subtitle = "Zero latency, zero popups, uninterrupted gaming",
                icon = Icons.Rounded.SportsEsports,
                initiallyExpanded = true
            ) {
                ConfigSwitchRow(
                    title = "Auto-Activate Game Mode DND on Game Launch",
                    subtitle = "Automatically transitions to Gaming DND whenever any game launches, and restores previous profile 30s after exit",
                    checked = state.autoGameDetectionEnabled,
                    onCheckedChange = { stateManager.setAutoGameDetectionEnabled(it) },
                    testTag = "switch_auto_activate_game_dnd"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Auto-Decline WhatsApp & VoIP Calls in Game",
                    subtitle = "Immediately drops VoIP calls in background during gaming",
                    checked = state.autoDeclineVoipInGame,
                    onCheckedChange = { stateManager.setAutoDeclineVoipInGameEnabled(it) },
                    testTag = "switch_gaming_voip"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Block Cellular Calls (Including Starred)",
                    subtitle = "Overrides priority contacts so you are never kicked out of match",
                    checked = state.strictGamingLockdownEnabled,
                    onCheckedChange = { stateManager.setStrictGamingLockdownEnabled(it) },
                    testTag = "switch_gaming_lockdown"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Suppress All Heads-Up Banners",
                    subtitle = "Blocks intrusive overlay banners while gaming",
                    checked = state.suppressHeadsUpBanners,
                    onCheckedChange = { stateManager.setSuppressHeadsUpBannersEnabled(it) },
                    testTag = "switch_gaming_heads_up"
                )
            }
        }

        // 5. Smart Breakthrough & Emergency Scanner
        item {
            ExpandableConfigCard(
                title = "Smart Breakthrough & Emergency Scanner",
                subtitle = "Scans critical OTPs, ride drivers, and emergency text alerts",
                icon = Icons.Rounded.Notifications,
                initiallyExpanded = true
            ) {
                ConfigSwitchRow(
                    title = "OTP, Ride & 'URGENT' Keyword Breakthrough",
                    subtitle = "Allows 2FA codes, delivery drivers, and emergency text alerts while keeping regular messages silent",
                    checked = state.smartUrgentBreakthroughEnabled,
                    onCheckedChange = { stateManager.setSmartUrgentBreakthroughEnabled(it) },
                    testTag = "switch_smart_urgent"
                )
            }
        }
    }
}

@Composable
private fun SystemAndThemeTab(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    hasNotificationListener: Boolean,
    hasDndAccess: Boolean,
    hasRuntimePermissions: Boolean,
    hasBatteryExemption: Boolean,
    hasUsageStatsAccess: Boolean,
    grantedPermissionsCount: Int,
    onRequestRuntimePermissions: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Material You Theme & Adaptive Icon Studio
        item {
            ExpandableConfigCard(
                title = "Appearance & Material You Theme",
                subtitle = "Dynamic color palette, profile tints, and pure black",
                icon = Icons.Rounded.Palette,
                initiallyExpanded = true
            ) {
                ConfigSwitchRow(
                    title = "Use Material You Dynamic Wallpaper Colors",
                    subtitle = "Extracts colors directly from your Android 12+ wallpaper palette",
                    checked = state.themePreferences.followSystemWallpaper,
                    onCheckedChange = { checked ->
                        stateManager.updateThemePreferences(state.themePreferences.copy(followSystemWallpaper = checked))
                    },
                    testTag = "switch_theme_dynamic"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Profile-Tinted Dynamic Accents",
                    subtitle = "Shifts primary accent color to match active Focus mode",
                    checked = state.themePreferences.profileTintedAccent,
                    onCheckedChange = { checked ->
                        stateManager.updateThemePreferences(state.themePreferences.copy(profileTintedAccent = checked))
                    },
                    testTag = "switch_theme_profile_tint"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "AMOLED Pure Black Mode",
                    subtitle = "Pure black backgrounds for maximum battery savings on OLED",
                    checked = state.themePreferences.amoledPureBlack,
                    onCheckedChange = { checked ->
                        stateManager.updateThemePreferences(state.themePreferences.copy(amoledPureBlack = checked))
                    },
                    testTag = "switch_theme_amoled"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Dim Lock Screen During Sleep/DND",
                    subtitle = "Generates minimalist dark slate status wallpaper on lock screen",
                    checked = state.dimLockScreenEnabled,
                    onCheckedChange = { stateManager.setDimLockScreenEnabled(it) },
                    testTag = "switch_dim_lock_screen"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                InteractiveAppIconPreviewRow()
            }
        }

        // 2. System Permissions Checklist Card
        item {
            ExpandableConfigCard(
                title = "System Permissions Checklist ($grantedPermissionsCount/5)",
                subtitle = "Interactive checklist for hardware and Android services",
                icon = Icons.Rounded.Security,
                initiallyExpanded = true
            ) {
                PermissionStatusRow(
                    title = "Notification Listener Access",
                    description = "Required to silence alerts, detect declined calls, and send call auto-replies.",
                    isGranted = hasNotificationListener,
                    actionLabel = "Enable",
                    onAction = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PermissionStatusRow(
                    title = "DND Interruption Policy",
                    description = "Allows syncing Android interruption filter with focus profiles",
                    isGranted = hasDndAccess,
                    actionLabel = "Grant",
                    onAction = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PermissionStatusRow(
                    title = "Runtime Permissions",
                    description = "Starred contacts lookup and push notifications",
                    isGranted = hasRuntimePermissions,
                    actionLabel = "Grant",
                    onAction = onRequestRuntimePermissions
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PermissionStatusRow(
                    title = "Battery Optimization Exemption",
                    description = "Prevents OEM battery killers from terminating call and notification guards",
                    isGranted = hasBatteryExemption,
                    actionLabel = "Exempt",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            runCatching {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }.onFailure {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PermissionStatusRow(
                    title = "Usage Stats Access",
                    description = "Required to detect real-time game launches and auto-engage Gaming profile",
                    isGranted = hasUsageStatsAccess,
                    actionLabel = "Grant",
                    onAction = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HeroStatusHeaderCard(
    state: FocusSessionState,
    activeProfile: FocusProfile = state.activeProfile,
    headerBgColor: Color,
    onDurationSelected: (DurationOption) -> Unit,
    onAdjustMinutes: (Int) -> Unit = {},
    onToggleFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = activeProfile != FocusProfile.OFF

    var remainingSeconds by remember(state.expiresAtMillis, isActive) {
        mutableLongStateOf(
            state.expiresAtMillis?.let {
                maxOf(0L, (it - System.currentTimeMillis()) / 1000L)
            } ?: 0L
        )
    }

    LaunchedEffect(state.expiresAtMillis, isActive) {
        if (isActive && state.expiresAtMillis != null) {
            while (true) {
                val rem = maxOf(0L, (state.expiresAtMillis - System.currentTimeMillis()) / 1000L)
                remainingSeconds = rem
                if (rem <= 0L) break
                delay(1000L)
            }
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_status_header_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = headerBgColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.16f else 0.06f),
                            Color.Transparent
                        ),
                        radius = 650f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile & Glowing Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (activeProfile == FocusProfile.OFF) {
                                    Image(
                                        painter = painterResource(
                                            if (isSystemInDarkTheme()) R.drawable.sentinal_icon_dark else R.drawable.sentinal_icon_light
                                        ),
                                        contentDescription = "Sentinal App Icon",
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = when (activeProfile) {
                                                    FocusProfile.OFF -> Icons.Rounded.Shield
                                                    FocusProfile.DO_NOT_DISTURB -> Icons.Rounded.DoNotDisturbOn
                                                    FocusProfile.GAMING -> Icons.Rounded.SportsEsports
                                                    FocusProfile.WORK -> Icons.Rounded.Work
                                                    FocusProfile.SLEEP -> Icons.Rounded.Bedtime
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Column {
                            Text(
                                text = if (isActive) activeProfile.displayName else "Shield Standby",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isActive) activeProfile.subtitle else "Select a profile below or tap Activate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Glowing Status Badge
                    Surface(
                        shape = CircleShape,
                        color = if (isActive) Color(0xFF38EF7D).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.5.dp,
                            if (isActive) Color(0xFF38EF7D) else Color.Gray.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) Color(0xFF38EF7D) else Color.Gray)
                            )
                            Text(
                                text = if (isActive) "ACTIVE" else "STANDBY",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Live Countdown + Quick -15m / +15m Adjusters when Active
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (state.expiresAtMillis != null) {
                                        val hours = remainingSeconds / 3600
                                        val minutes = (remainingSeconds % 3600) / 60
                                        val seconds = remainingSeconds % 60
                                        if (hours > 0) {
                                            String.format("%02d:%02d:%02d", hours, minutes, seconds)
                                        } else {
                                            String.format("%02d:%02d", minutes, seconds)
                                        }
                                    } else {
                                        "UNTIL TURNED OFF"
                                    },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .clickable { onAdjustMinutes(-15) }
                                        .testTag("btn_timer_minus_15")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Remove,
                                            contentDescription = "Subtract 15 minutes",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "15m",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .clickable { onAdjustMinutes(15) }
                                        .testTag("btn_timer_plus_15")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Add 15 minutes",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "15m",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Duration Pills: 1h, 2h, Until Turned Off
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        DurationOption.FOR_ONE_HOUR to "1h",
                        DurationOption.FOR_TWO_HOURS to "2h",
                        DurationOption.UNTIL_TURNED_OFF to "Until Turned Off"
                    ).forEach { (opt, label) ->
                        val isSelected = state.durationOption == opt
                        FilterChip(
                            selected = isSelected,
                            onClick = { onDurationSelected(opt) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(if (label == "Until Turned Off") 1.4f else 0.8f)
                        )
                    }
                }

                // Primary Action Button
                Button(
                    onClick = onToggleFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("hero_toggle_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (isActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isActive) "Deactivate Sentinal Shield" else "Activate Focus Shield",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FocusProfileBentoGrid(
    activeProfile: FocusProfile,
    onProfileSelected: (FocusProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles = listOf(
        Triple(FocusProfile.DO_NOT_DISTURB, Icons.Rounded.DoNotDisturbOn, "Silences notifications & declines calls"),
        Triple(FocusProfile.GAMING, Icons.Rounded.SportsEsports, "Zero popups, VoIP bouncer & 30s exit grace"),
        Triple(FocusProfile.WORK, Icons.Rounded.Work, "Deep focus with starred contact priority"),
        Triple(FocusProfile.SLEEP, Icons.Rounded.Bedtime, "Restful night shield with lock screen dim")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        profiles.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { (profile, icon, summary) ->
                    val isSelected = activeProfile == profile
                    val testTag = "profile_chip_${profile.id.lowercase()}"

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onProfileSelected(profile) }
                            .testTag(testTag)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF38EF7D).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "ON",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38EF7D),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoipAutoReplyCustomizationCard(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    onOpenCustomizeSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voip_auto_reply_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneCallback,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Declined Call Auto-Reply",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Replies strictly to declined incoming calls (never to outgoing calls or regular messages)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = state.autoReplyEnabled,
                    onCheckedChange = { stateManager.setAutoReplyEnabled(it) },
                    modifier = Modifier.testTag("voip_auto_reply_switch")
                )
            }

            AnimatedVisibility(visible = state.autoReplyEnabled) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Custom Auto-Reply Message",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FilledTonalButton(
                            onClick = onOpenCustomizeSheet,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_customize_auto_reply_sheet")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Per-Profile",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.autoReplyMessageTemplate,
                        onValueChange = { stateManager.setAutoReplyMessageTemplate(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voip_auto_reply_textfield"),
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        placeholder = { Text("Enter auto-reply text (%s = active focus name)...") }
                    )

                    val previewProfile = if (state.activeProfile != FocusProfile.OFF) state.activeProfile else FocusProfile.GAMING
                    val currentMessage = stateManager.getAutoReplyMessage(previewProfile)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Dispatch Preview: $currentMessage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )

                        TextButton(
                            onClick = { stateManager.resetSessionCaches() },
                            modifier = Modifier.testTag("btn_reset_auto_reply_cooldowns")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reset Cache",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveAppIconPreviewRow(
    modifier: Modifier = Modifier
) {
    val isSystemDark = isSystemInDarkTheme()
    var previewModeIsDark by remember { mutableStateOf(isSystemDark) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interactive App Icon",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (previewModeIsDark) "Dark Mode • Cyan Crescent Moon" else "Light Mode • Amber Sun-Moon",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .testTag("toggle_icon_preview_btn")
                    .clickable { previewModeIsDark = !previewModeIsDark }
            ) {
                Text(
                    text = if (previewModeIsDark) "Switch to Light" else "Switch to Dark",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { previewModeIsDark = false }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            width = if (!previewModeIsDark) 2.5.dp else 1.dp,
                            color = if (!previewModeIsDark) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    Image(
                        painter = painterResource(R.drawable.sentinal_icon_light),
                        contentDescription = "Sentinal Light Mode Icon (Gold Sun-Moon)",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = "Light Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (!previewModeIsDark) FontWeight.Bold else FontWeight.Normal,
                    color = if (!previewModeIsDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { previewModeIsDark = true }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            width = if (previewModeIsDark) 2.5.dp else 1.dp,
                            color = if (previewModeIsDark) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    Image(
                        painter = painterResource(R.drawable.sentinal_icon_dark),
                        contentDescription = "Sentinal Dark Mode Icon (Cyan Crescent Moon)",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = "Dark Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (previewModeIsDark) FontWeight.Bold else FontWeight.Normal,
                    color = if (previewModeIsDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ExpandableConfigCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    initiallyExpanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    content()
                }
            }
        }
    }
}

@Composable
fun ConfigSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF38EF7D) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isGranted) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF38EF7D).copy(alpha = 0.15f),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun checkNotificationListenerAccess(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

private fun checkRuntimePermissions(context: Context): Boolean {
    val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val notifsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    return contactsGranted && notifsGranted
}

private fun checkBatteryOptimizationExemption(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
        true
    }
}
