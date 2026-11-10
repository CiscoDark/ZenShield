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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Movie
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.example.core.InterceptEvent
import com.example.core.InterceptType
import com.example.services.GameDetectorService
import com.example.ui.AppWhitelistBottomSheet
import com.example.ui.AutoReplyCustomMessageBottomSheet
import com.example.ui.CinemaModeDashboardCard
import com.example.ui.CinemaModeScreen
import com.example.ui.FocusSessionHistoryDashboardCard
import com.example.ui.FocusSessionHistoryScreen
import com.example.ui.GameDetectionCard
import com.example.ui.GamingModeNonInteractiveOverlay
import com.example.ui.GamingModePopupManager
import com.example.ui.GamingModeWeeklyComparisonCard
import com.example.ui.HomeScreenWidgetCard
import com.example.ui.NotificationPriorityRulesCard
import com.example.ui.QuickSettingsTilesCard
import com.example.ui.RustCoreInspectorCard
import com.example.ui.SleepScheduleCard
import com.example.ui.WeeklyShieldStatsCard
import com.example.ui.WorkScheduleCard
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveShapes
import com.example.ui.theme.FocusThemePreferences
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.Typography
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
 * DynamicTheme: Composable wrapper in MainActivity utilizing Material 3 Expressive shapes,
 * typography, and dynamic wallpaper / profile-reactive color schemes.
 */
@Composable
fun DynamicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    activeProfile: FocusProfile = FocusProfile.OFF,
    preferences: FocusThemePreferences = FocusThemePreferences(),
    content: @Composable () -> Unit
) {
    com.example.ui.theme.DynamicTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        activeProfile = activeProfile,
        preferences = preferences,
        content = content
    )
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
    var showCinemaFullScreen by rememberSaveable { mutableStateOf(false) }
    var showSessionHistoryFullScreen by rememberSaveable { mutableStateOf(false) }

    // Return to Shield Command Center when pressing Back from secondary tabs
    BackHandler(enabled = selectedTabIndex != 0 && !showCinemaFullScreen && !showSessionHistoryFullScreen) {
        ExpressiveHaptics.playSpringTap(context)
        selectedTabIndex = 0
    }

    // System Permissions & Access Trackers (All 6 Centralized in Settings)
    var hasNotificationListener by remember { mutableStateOf(checkNotificationListenerAccess(context)) }
    var hasDndAccess by remember { mutableStateOf(stateManager.isNotificationPolicyAccessGranted()) }
    var hasRuntimePermissions by remember { mutableStateOf(checkRuntimePermissions(context)) }
    var hasBatteryExemption by remember { mutableStateOf(checkBatteryOptimizationExemption(context)) }
    var hasUsageStatsAccess by remember { mutableStateOf(GameDetectorService.hasUsageStatsPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(com.example.ui.GamingModePopupManager.hasOverlayPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                stateManager.checkAndExpireIfNeeded()
                stateManager.evaluateWorkSchedule()
                stateManager.evaluateSleepSchedule()
                hasNotificationListener = checkNotificationListenerAccess(context)
                hasDndAccess = stateManager.isNotificationPolicyAccessGranted()
                hasRuntimePermissions = checkRuntimePermissions(context)
                hasBatteryExemption = checkBatteryOptimizationExemption(context)
                hasUsageStatsAccess = GameDetectorService.hasUsageStatsPermission(context)
                hasOverlayPermission = com.example.ui.GamingModePopupManager.hasOverlayPermission(context)
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
        hasUsageStatsAccess,
        hasOverlayPermission
    ).count { it }

    val animatedHeaderColor by animateColorAsState(
        targetValue = when (activeProfile) {
            FocusProfile.OFF -> MaterialTheme.colorScheme.surfaceContainerHigh
            FocusProfile.DO_NOT_DISTURB -> MaterialTheme.colorScheme.primaryContainer
            FocusProfile.GAMING -> MaterialTheme.colorScheme.secondaryContainer
            FocusProfile.WORK -> MaterialTheme.colorScheme.tertiaryContainer
            FocusProfile.SLEEP -> MaterialTheme.colorScheme.primaryContainer
            FocusProfile.CINEMA -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "header_color"
    )

    val topBadgeCorner by animateDpAsState(
        targetValue = if (activeProfile != FocusProfile.OFF) 19.dp else 12.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "top_badge_corner"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(topBadgeCorner),
                                color = if (activeProfile != FocusProfile.OFF) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = if (activeProfile != FocusProfile.OFF) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                                    ) {
                                        Text(
                                            text = "M3 EXPRESSIVE",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
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
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable {
                                    ExpressiveHaptics.playSpringTap(context)
                                    showSessionHistoryFullScreen = true
                                }
                                .testTag("open_session_history_quick_action")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BarChart,
                                    contentDescription = "Open Focus Session History",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "History",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        val whitelistInteraction = remember { MutableInteractionSource() }
                        val isWhitelistPressed by whitelistInteraction.collectIsPressedAsState()
                        val whitelistCorner by animateDpAsState(
                            targetValue = if (isWhitelistPressed) 11.dp else 22.dp,
                            animationSpec = ExpressiveMotion.snappySpatialSpring(),
                            label = "whitelist_corner"
                        )
                        val whitelistScale by animateFloatAsState(
                            targetValue = if (isWhitelistPressed) 0.94f else 1f,
                            animationSpec = ExpressiveMotion.snappySpatialSpring(),
                            label = "whitelist_scale"
                        )
                        Surface(
                            shape = RoundedCornerShape(whitelistCorner),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.2.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .graphicsLayer {
                                    scaleX = whitelistScale
                                    scaleY = whitelistScale
                                }
                                .clickable(
                                    interactionSource = whitelistInteraction,
                                    indication = null
                                ) {
                                    ExpressiveHaptics.playSpringTap(context)
                                    showWhitelistSheet = true
                                }
                                .testTag("app_whitelist_quick_action")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
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
                FloatingExpressivePillToolbar(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { newIndex ->
                        if (newIndex != selectedTabIndex) {
                            ExpressiveHaptics.playSpringTap(context)
                            selectedTabIndex = newIndex
                        }
                    }
                )
            }
        ) { innerPadding ->
            // Directional spatial slide + spring-scale workspace transition
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (slideInHorizontally(
                        animationSpec = ExpressiveMotion.bouncySpatialSpring()
                    ) { fullWidth -> direction * (fullWidth / 5) } +
                        fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()) +
                        scaleIn(initialScale = 0.95f, animationSpec = ExpressiveMotion.bouncySpatialSpring()))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = ExpressiveMotion.snappySpatialSpring()
                            ) { fullWidth -> -direction * (fullWidth / 6) } +
                                fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring()) +
                                scaleOut(targetScale = 0.95f, animationSpec = ExpressiveMotion.snappySpatialSpring())
                        )
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
                            onOpenAnalyticsTab = {
                                ExpressiveHaptics.playSpringTap(context)
                                selectedTabIndex = SentinalWorkspaceTab.ANALYTICS.ordinal
                            },
                            onOpenRulesTab = {
                                ExpressiveHaptics.playSpringTap(context)
                                selectedTabIndex = SentinalWorkspaceTab.RULES.ordinal
                            },
                            onOpenSystemTab = {
                                ExpressiveHaptics.playSpringTap(context)
                                selectedTabIndex = SentinalWorkspaceTab.SYSTEM.ordinal
                            },
                            onOpenCinemaScreen = {
                                ExpressiveHaptics.playSpringTap(context)
                                showCinemaFullScreen = true
                            },
                            onOpenAutoReplySheet = {
                                ExpressiveHaptics.playSpringTap(context)
                                showAutoReplySheet = true
                            }
                        )
                    }

                    SentinalWorkspaceTab.ANALYTICS -> {
                        AnalyticsTelemetryTab(
                            state = state,
                            stateManager = stateManager,
                            onOpenFullSessionHistory = {
                                ExpressiveHaptics.playSpringTap(context)
                                showSessionHistoryFullScreen = true
                            }
                        )
                    }

                    SentinalWorkspaceTab.RULES -> {
                        RulesAndBouncerTab(
                            state = state,
                            stateManager = stateManager,
                            onOpenAutoReplySheet = {
                                ExpressiveHaptics.playSpringTap(context)
                                showAutoReplySheet = true
                            },
                            onOpenGamePickerSheet = {
                                ExpressiveHaptics.playSpringTap(context)
                                showGamePickerSheet = true
                            },
                            onOpenCinemaScreen = {
                                ExpressiveHaptics.playSpringTap(context)
                                showCinemaFullScreen = true
                            }
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
                            hasOverlayPermission = hasOverlayPermission,
                            grantedPermissionsCount = grantedPermissionsCount,
                            onRequestRuntimePermissions = {
                                ExpressiveHaptics.playSpringTap(context)
                                val perms = mutableListOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.READ_PHONE_STATE
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    perms.add(Manifest.permission.ANSWER_PHONE_CALLS)
                                }
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

        // Non-interactive touch-passthrough Gaming Mode pop-up pill at top of screen
        GamingModeNonInteractiveOverlay(
            modifier = Modifier.align(Alignment.TopCenter)
        )
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

    if (showCinemaFullScreen) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CinemaModeScreen(
                stateManager = stateManager,
                onBack = { showCinemaFullScreen = false },
                onCustomizeAutoReply = { showAutoReplySheet = true }
            )
        }
    }

    if (showSessionHistoryFullScreen) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            FocusSessionHistoryScreen(
                stateManager = stateManager,
                onBack = { showSessionHistoryFullScreen = false }
            )
        }
    }
}

/**
 * RollingTelemetryCounter
 *
 * Smooth vertical odometer-style roll animation for live countdown timers and blocked counters.
 */
@Composable
fun RollingTelemetryCounter(
    valueText: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = valueText,
        transitionSpec = {
            (slideInVertically(animationSpec = ExpressiveMotion.bouncySpatialSpring()) { h -> h / 2 } +
                fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()))
                .togetherWith(
                    slideOutVertically(animationSpec = ExpressiveMotion.snappySpatialSpring()) { h -> -h / 2 } +
                        fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring())
                )
        },
        modifier = modifier,
        label = "rolling_telemetry_counter"
    ) { target ->
        Text(
            text = target,
            style = style,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1
        )
    }
}

/**
 * LiveAnimatedShieldEmblem
 *
 * Replaces the static raster app icon in the Hero header with a live animated vector shield emblem
 * featuring a breathing sonar pulse ring and spring-morphing profile icon.
 */
@Composable
fun LiveAnimatedShieldEmblem(
    activeProfile: FocusProfile,
    modifier: Modifier = Modifier
) {
    val isActive = activeProfile != FocusProfile.OFF
    val infiniteTransition = rememberInfiniteTransition(label = "shield_sonar_transition")
    val sonarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonar_ring_progress"
    )
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_emblem_scale"
    )

    val emblemCorner by animateDpAsState(
        targetValue = if (isActive) 28.dp else 18.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "emblem_corner_morph"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "emblem_bg_color"
    )

    Box(
        modifier = modifier
            .size(62.dp)
            .testTag("live_animated_shield_emblem"),
        contentAlignment = Alignment.Center
    ) {
        // Breathing Sonar Pulse Rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f
            val baseAlpha = if (isActive) 0.55f else 0.22f
            val ringRadius = maxRadius * (0.65f + 0.35f * sonarProgress)
            val ringAlpha = baseAlpha * (1f - sonarProgress)

            drawCircle(
                color = primaryColor.copy(alpha = ringAlpha),
                radius = ringRadius,
                style = Stroke(width = if (isActive) 2.5.dp.toPx() else 1.5.dp.toPx())
            )
            if (isActive) {
                val innerProgress = (sonarProgress + 0.5f) % 1f
                drawCircle(
                    color = Color(0xFF38EF7D).copy(alpha = 0.4f * (1f - innerProgress)),
                    radius = maxRadius * (0.62f + 0.38f * innerProgress),
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(emblemCorner),
            color = containerColor,
            border = BorderStroke(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) Color(0xFF38EF7D).copy(alpha = 0.7f) else primaryColor.copy(alpha = 0.35f)
            ),
            shadowElevation = if (isActive) 8.dp else 2.dp,
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer {
                    val scale = if (isActive) breathingScale else 1f
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = activeProfile,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.6f, animationSpec = ExpressiveMotion.bouncySpatialSpring()) +
                            fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()))
                            .togetherWith(
                                scaleOut(targetScale = 0.6f, animationSpec = ExpressiveMotion.snappySpatialSpring()) +
                                    fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring())
                            )
                    },
                    label = "emblem_icon_morph"
                ) { profile ->
                    Icon(
                        imageVector = when (profile) {
                            FocusProfile.OFF -> Icons.Rounded.Shield
                            FocusProfile.DO_NOT_DISTURB -> Icons.Rounded.DoNotDisturbOn
                            FocusProfile.GAMING -> Icons.Rounded.SportsEsports
                            FocusProfile.WORK -> Icons.Rounded.Work
                            FocusProfile.SLEEP -> Icons.Rounded.Bedtime
                            FocusProfile.CINEMA -> Icons.Rounded.Movie
                        },
                        contentDescription = profile.displayName,
                        tint = if (profile != FocusProfile.OFF) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/**
 * FloatingExpressivePillToolbar
 *
 * Material 3 Expressive floating capsule navigation bar anchored above WindowInsets.navigationBars.
 * Active workspace tabs morph their corner radius, scale, and pill width using bouncy spring physics.
 */
@Composable
fun FloatingExpressivePillToolbar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("expressive_floating_pill_toolbar"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(
                1.2.dp,
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                    )
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SentinalWorkspaceTab.entries.forEachIndexed { index, tab ->
                    val isSelected = selectedTabIndex == index
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val pillCorner by animateDpAsState(
                        targetValue = when {
                            isPressed -> 12.dp
                            isSelected -> 26.dp
                            else -> 16.dp
                        },
                        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                        label = "nav_pill_corner_${tab.name}"
                    )
                    val itemScale by animateFloatAsState(
                        targetValue = when {
                            isPressed -> 0.91f
                            isSelected -> 1.03f
                            else -> 0.96f
                        },
                        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                        label = "nav_pill_scale_${tab.name}"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        animationSpec = ExpressiveMotion.smoothColorSpring(),
                        label = "nav_pill_bg_${tab.name}"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = ExpressiveMotion.smoothColorSpring(),
                        label = "nav_pill_fg_${tab.name}"
                    )

                    Surface(
                        shape = RoundedCornerShape(pillCorner),
                        color = containerColor,
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        } else {
                            null
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = itemScale
                                scaleY = itemScale
                            }
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(pillCorner))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onTabSelected(index) }
                            .testTag(tab.tag)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = if (isSelected) 16.dp else 14.dp,
                                vertical = 10.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else contentColor,
                                modifier = Modifier.size(21.dp)
                            )

                            AnimatedVisibility(
                                visible = isSelected,
                                enter = expandHorizontally(
                                    animationSpec = ExpressiveMotion.bouncySpatialSpring()
                                ) + fadeIn(animationSpec = ExpressiveMotion.smoothColorSpring()),
                                exit = shrinkHorizontally(
                                    animationSpec = ExpressiveMotion.snappySpatialSpring()
                                ) + fadeOut(animationSpec = ExpressiveMotion.smoothColorSpring())
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = contentColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
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
    onOpenSystemTab: () -> Unit,
    onOpenCinemaScreen: () -> Unit = {},
    onOpenAutoReplySheet: () -> Unit = {}
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Status Header Card (with Live Animated Shield Emblem + Custom Duration Slider + Auto-Reply Toggle)
        item {
            HeroStatusHeaderCard(
                state = state,
                activeProfile = activeProfile,
                headerBgColor = headerBgColor,
                onDurationSelected = { duration ->
                    ExpressiveHaptics.playSpringTap(context)
                    stateManager.setDuration(duration)
                },
                onCustomDurationMinutesChanged = { minutes ->
                    stateManager.setCustomDurationMinutes(minutes)
                },
                onAdjustMinutes = { deltaMinutes ->
                    ExpressiveHaptics.playSpringTap(context)
                    stateManager.adjustRemainingTime(context, deltaMinutes.toLong())
                },
                onToggleAutoReply = {
                    stateManager.toggleAutoReply()
                },
                onTimerExpired = {
                    stateManager.checkAndExpireIfNeeded()
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

        // 2. Compact Permission Shortcut Banner (links directly to unified Settings Permissions Hub when any of 6 is missing)
        if (grantedPermissionsCount < 6) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSystemTab() }
                        .testTag("home_permissions_shortcut_banner")
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
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "System Guard Readiness: $grantedPermissionsCount/6 Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Tap to open Settings and manage all permissions in one place",
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
        item(key = "focus_profile_bento_section") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Focus Mode Matrix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (activeProfile != FocusProfile.OFF) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
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
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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

        // 3-cinema. Cinema Mode Dashboard Card (2h / 2.5h presets + theater notification blocking toggle)
        item(key = "cinema_mode_dashboard_section") {
            CinemaModeDashboardCard(
                stateManager = stateManager,
                onOpenFullScreen = onOpenCinemaScreen,
                onCustomizeAutoReply = onOpenAutoReplySheet
            )
        }

        // 3-gaming-comparison. Gaming Mode Time Comparison (This Week vs Last Week)
        item(key = "gaming_weekly_comparison_section") {
            GamingModeWeeklyComparisonCard(
                stateManager = stateManager
            )
        }

        // 3a. 2x2 Bento Quick-Toggle Grid for Instant Rule Control
        item(key = "quick_rules_bento_section") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Guard Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable { onOpenRulesTab() }
                ) {
                    Text(
                        text = "All Rules →",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            QuickRulesBentoGrid(
                state = state,
                stateManager = stateManager
            )
        }

        // 3a-priority. Notification Priority Categorizer & Global Focus Rules Engine
        item(key = "notification_priority_rules_section") {
            NotificationPriorityRulesCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 3b. Work Focus Mode & Automatic Work Hours Schedule Card
        item(key = "work_schedule_card_section") {
            WorkScheduleCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 3b-sleep. Scheduled Sleep Shield & Overnight Bedtime Schedule Card
        item(key = "sleep_schedule_card_section") {
            SleepScheduleCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 3b-qs-tiles. Custom Notification Shade Quick Settings Tiles (Gaming Mode & Sleep Mode)
        item(key = "quick_settings_tiles_section") {
            QuickSettingsTilesCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 3b-widget. Resizable 2x1 Material You Home Screen Widget Live Preview & Pin Action
        item(key = "home_screen_widget_section") {
            HomeScreenWidgetCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 3c. Live Notification Shade Controller & Gaming Pop-Up Preview Card
        item(key = "live_hud_preview_section") {
            LiveNotificationHudPreviewCard(
                state = state,
                activeProfile = activeProfile,
                liveCallsDeclined = weeklyCallsTotal,
                liveAlertsSilenced = weeklySilencedTotal,
                stateManager = stateManager
            )
        }

        // 3d. Live Blocked Activity Feed & One-Tap Shield Intercept Simulator
        item(key = "live_intercepts_feed_section") {
            LiveInterceptsActivityFeedCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 4. Compact Telemetry Snapshot Banner -> Jumps to Analytics Tab
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAnalyticsTab() },
                shape = RoundedCornerShape(28.dp),
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
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
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
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Open Chart →",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
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
                shape = RoundedCornerShape(26.dp),
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
                            modifier = Modifier.size(42.dp)
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "Configure",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * LiveInterceptsActivityFeedCard
 *
 * Real-time Live Blocked Activity Feed (Recent Intercepts Log) + One-Tap Shield Intercept Simulator
 * with spring-correlated haptics on simulation, individual item dismissal, and clear-all.
 */
@Composable
fun LiveInterceptsActivityFeedCard(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val intercepts = state.recentIntercepts

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_intercepts_feed_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Live Intercepts Activity Feed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Real-time log of bounced calls & silenced alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (intercepts.isNotEmpty()) {
                    TextButton(
                        onClick = { stateManager.clearInterceptHistory() },
                        modifier = Modifier.testTag("clear_intercepts_btn")
                    ) {
                        Text(
                            text = "Clear (${intercepts.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // One-Tap Shield Intercept Simulator Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        stateManager.incrementShieldedCall(
                            callerLabel = "WhatsApp Call • Alex R.",
                            isVoip = true
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulate_call_intercept_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhoneCallback,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Test Call Bounce",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                FilledTonalButton(
                    onClick = {
                        stateManager.incrementShieldedNotification(
                            sourceApp = "Chat Ping • Squad",
                            detail = "Silenced & dismissed without auto-reply"
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulate_alert_intercept_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Test Alert Silence",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            if (intercepts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No recent intercepts in feed. Tap 'Test Call Bounce' or 'Test Alert Silence' above to preview real-time shield interception with spring haptics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intercepts.take(4).forEach { event ->
                        InterceptEventRow(
                            event = event,
                            onDismiss = { stateManager.dismissInterceptEvent(event.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InterceptEventRow(
    event: InterceptEvent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCall = event.type == InterceptType.CALL_DECLINED || event.type == InterceptType.VOIP_CALL_BOUNCED
    val badgeColor = if (isCall) Color(0xFF00D2FF) else Color(0xFF38EF7D)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    color = badgeColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCall) Icons.Rounded.PhoneCallback else Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = event.sourceLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = event.type.badgeLabel,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "${event.timeLabel} • ${event.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("dismiss_intercept_${event.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss intercept event",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun LiveNotificationHudPreviewCard(
    state: FocusSessionState,
    activeProfile: FocusProfile,
    liveCallsDeclined: Int,
    liveAlertsSilenced: Int,
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isActive = activeProfile != FocusProfile.OFF
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isActive, state.expiresAtMillis, state.sessionStartTimeMillis, state.gameExitCountdownSeconds) {
        while (true) {
            stateManager.checkAndExpireIfNeeded()
            nowMillis = System.currentTimeMillis()
            delay(500L)
        }
    }

    val isTimed = isActive && state.expiresAtMillis != null && state.expiresAtMillis > nowMillis
    val remainingSec = if (isTimed && state.expiresAtMillis != null) {
        maxOf(0L, (state.expiresAtMillis - nowMillis) / 1000L)
    } else 0L
    val elapsedSec = if (isActive) {
        maxOf(0L, (nowMillis - (state.sessionStartTimeMillis ?: nowMillis)) / 1000L)
    } else 0L

    val timerLabel = when {
        !isActive -> "STANDBY"
        isTimed -> {
            val h = remainingSec / 3600
            val m = (remainingSec % 3600) / 60
            val s = remainingSec % 60
            if (h > 0) String.format("%02d:%02d:%02d left", h, m, s)
            else String.format("%02d:%02d left", m, s)
        }
        else -> {
            val h = elapsedSec / 3600
            val m = (elapsedSec % 3600) / 60
            val s = elapsedSec % 60
            if (h > 0) String.format("Elapsed %02d:%02d:%02d", h, m, s)
            else String.format("Elapsed %02d:%02d", m, s)
        }
    }

    val progressFraction = when {
        !isActive -> 0f
        state.gameExitCountdownSeconds != null -> (state.gameExitCountdownSeconds / 15f).coerceIn(0.05f, 1f)
        isTimed && state.expiresAtMillis != null -> {
            val startMs = state.sessionStartTimeMillis ?: (state.expiresAtMillis - 3600_000L)
            val totalSpan = maxOf(60_000L, state.expiresAtMillis - startMs).toFloat()
            val remMs = maxOf(0L, state.expiresAtMillis - nowMillis).toFloat()
            (remMs / totalSpan).coerceIn(0.0f, 1f)
        }
        else -> 1f
    }

    val modeTitle = when (activeProfile) {
        FocusProfile.GAMING -> "GAMING FOCUS"
        FocusProfile.DO_NOT_DISTURB -> "DO NOT DISTURB"
        FocusProfile.WORK -> "WORK FOCUS"
        FocusProfile.SLEEP -> "SLEEP SHIELD"
        FocusProfile.CINEMA -> "CINEMA SHIELD"
        FocusProfile.OFF -> "LIVE NOTIFICATION STANDBY"
    }

    val hudCornerRadius by animateDpAsState(
        targetValue = if (isActive) 30.dp else 22.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "hud_card_corner"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_notification_hud_card"),
        shape = RoundedCornerShape(hudCornerRadius),
        color = Color(0xFF0D131F),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) Color(0xFF38EF7D).copy(alpha = 0.55f) else Color(0xFF26344B)
        ),
        shadowElevation = if (isActive) 8.dp else 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Mode Title, Live Timer, and Status Badge / Pop-up Tester
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFF38EF7D) else Color(0xFF00D2FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = modeTitle,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RollingTelemetryCounter(
                        valueText = timerLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color(0xFF00D2FF)
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (state.gameExitCountdownSeconds != null) {
                            Color(0x33FFB300)
                        } else if (isActive) {
                            Color(0x2638EF7D)
                        } else {
                            Color(0xFF1E293B)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (state.gameExitCountdownSeconds != null) Color(0xFFFFB300)
                            else if (isActive) Color(0xFF38EF7D)
                            else Color(0xFF475569)
                        )
                    ) {
                        Text(
                            text = when {
                                state.gameExitCountdownSeconds != null -> "EXIT ${state.gameExitCountdownSeconds}s"
                                isActive -> "LIVE • SHADE"
                                else -> "PREVIEW"
                            },
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = when {
                                state.gameExitCountdownSeconds != null -> Color(0xFFFFB300)
                                isActive -> Color(0xFF38EF7D)
                                else -> Color(0xFF94A3B8)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Subtitle, Interactive Live Shade Auto-Reply Toggle & Gaming Pop-Up Trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.autoReplyEnabled) {
                        "Call Auto-Reply ON (3m Cooldown)"
                    } else {
                        "Silent Call Bouncer • Auto-Reply OFF"
                    },
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (state.autoReplyEnabled) Color(0x2638EF7D) else Color(0xFF1E293B),
                        border = BorderStroke(
                            1.dp,
                            if (state.autoReplyEnabled) Color(0xFF38EF7D) else Color(0xFF475569)
                        ),
                        modifier = Modifier
                            .clickable { stateManager.toggleAutoReply() }
                            .testTag("btn_hud_toggle_auto_reply")
                    ) {
                        Text(
                            text = if (state.autoReplyEnabled) "Reply: ON" else "Reply: OFF",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (state.autoReplyEnabled) Color(0xFF38EF7D) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF162235),
                        border = BorderStroke(1.dp, Color(0xFF38EF7D).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable {
                                ExpressiveHaptics.playFocusActivated(context)
                                GamingModePopupManager.showGamingModeActivatedPopup(
                                    context,
                                    "Gaming HUD Test"
                                )
                            }
                            .testTag("btn_test_gaming_popup")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = Color(0xFF38EF7D),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Test Pop-Up",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFF38EF7D)
                            )
                        }
                    }
                }
            }

            // Live Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C283B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF00D2FF), Color(0xFF38EF7D))
                            )
                        )
                )
            }

            // 2-Column Live Telemetry Strip with Rolling Odometer Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF151F30),
                    border = BorderStroke(1.dp, Color(0xFF26364F)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RollingTelemetryCounter(
                            valueText = liveCallsDeclined.toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            color = Color(0xFF00D2FF)
                        )
                        Text(
                            text = "Calls Declined",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF151F30),
                    border = BorderStroke(1.dp, Color(0xFF26364F)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RollingTelemetryCounter(
                            valueText = liveAlertsSilenced.toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            color = Color(0xFF38EF7D)
                        )
                        Text(
                            text = "Alerts Silenced",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Clean Single-Row Pill Controls (-15m, +15m, +30m, Mode, Turn Off / Activate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LiveHudPillButton(
                    label = "-15m",
                    containerColor = Color(0xFF1A2538),
                    contentColor = Color(0xFFCBD5E1),
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        if (!isActive) stateManager.setProfile(FocusProfile.DO_NOT_DISTURB)
                        stateManager.adjustRemainingTime(context, -15L)
                    },
                    modifier = Modifier.weight(0.85f)
                )
                LiveHudPillButton(
                    label = "+15m",
                    containerColor = Color(0xFF0284C7),
                    contentColor = Color.White,
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        if (!isActive) stateManager.setProfile(FocusProfile.DO_NOT_DISTURB)
                        stateManager.adjustRemainingTime(context, 15L)
                    },
                    modifier = Modifier.weight(0.85f)
                )
                LiveHudPillButton(
                    label = "+30m",
                    containerColor = Color(0xFF0284C7),
                    contentColor = Color.White,
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        if (!isActive) stateManager.setProfile(FocusProfile.DO_NOT_DISTURB)
                        stateManager.adjustRemainingTime(context, 30L)
                    },
                    modifier = Modifier.weight(0.85f)
                )
                LiveHudPillButton(
                    label = "Mode",
                    containerColor = Color(0xFF1A2538),
                    contentColor = Color(0xFFE2E8F0),
                    onClick = { stateManager.cycleNextActiveProfile(context) },
                    modifier = Modifier.weight(1.1f)
                )
                LiveHudPillButton(
                    label = if (isActive) "Turn Off" else "Start",
                    containerColor = if (isActive) Color(0xFF7F1D1D) else Color(0xFF059669),
                    contentColor = if (isActive) Color(0xFFFFCDD2) else Color.White,
                    onClick = {
                        if (isActive) {
                            stateManager.setProfile(FocusProfile.OFF)
                        } else {
                            stateManager.setProfile(FocusProfile.GAMING, state.durationOption)
                        }
                    },
                    modifier = Modifier.weight(1.15f)
                )
            }
        }
    }
}

@Composable
private fun LiveHudPillButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 18.dp else 11.dp,
        animationSpec = ExpressiveMotion.snappySpatialSpring(),
        label = "hud_pill_corner"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.snappySpatialSpring(),
        label = "hud_pill_scale"
    )

    Surface(
        modifier = modifier
            .height(34.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = contentColor,
                maxLines = 1
            )
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
        shape = RoundedCornerShape(18.dp),
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
                RollingTelemetryCounter(
                    valueText = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
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
    stateManager: FocusStateManager,
    onOpenFullSessionHistory: () -> Unit = {}
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0a. Focus Session History (Room Database + Recharts-inspired Bar Chart)
        item(key = "analytics_focus_session_history") {
            FocusSessionHistoryDashboardCard(
                stateManager = stateManager,
                onOpenFullScreen = onOpenFullSessionHistory
            )
        }

        // 0b. Gaming Mode Time Comparison Dashboard (This Week vs Last Week)
        item(key = "analytics_gaming_weekly_comparison") {
            GamingModeWeeklyComparisonCard(
                stateManager = stateManager
            )
        }

        // 1. Primary Recharts-Style Weekly Telemetry Card
        item {
            WeeklyShieldStatsCard(stateManager = stateManager)
        }

        // 2. Live Intercepts Activity Feed
        item {
            LiveInterceptsActivityFeedCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 3. Current Session Guard Policy Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
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
                        text = "• Declined Incoming Calls: Automatically dispatches your profile auto-reply message when enabled (3-min per-caller cooldown).",
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
                            onClick = {
                                ExpressiveHaptics.playNotificationDismissed(context)
                                stateManager.resetSessionCaches()
                            }
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
    onOpenGamePickerSheet: () -> Unit,
    onOpenCinemaScreen: () -> Unit = {}
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Cinema Mode Dashboard Card (2h, 2.5h presets & theater notification blocking toggle)
        item(key = "rules_cinema_mode_card") {
            CinemaModeDashboardCard(
                stateManager = stateManager,
                onOpenFullScreen = onOpenCinemaScreen,
                onCustomizeAutoReply = onOpenAutoReplySheet
            )
        }

        // 0b. Notification Categorizer & Global Priority Focus Rules Engine
        item(key = "rules_notification_priority_card") {
            NotificationPriorityRulesCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 1. Call Auto-Reply Customization Card
        item {
            VoipAutoReplyCustomizationCard(
                state = state,
                stateManager = stateManager,
                onOpenCustomizeSheet = onOpenAutoReplySheet
            )
        }

        // 1b. Work Focus Mode (Manual & Automatic Schedule)
        item {
            WorkScheduleCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 1c. Scheduled Sleep Shield (Manual & Automatic Overnight Bedtime Schedule)
        item {
            SleepScheduleCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 2. Real-Time Game Launch Detector & 15s Exit Grace Countdown
        item {
            GameDetectionCard(
                state = state,
                stateManager = stateManager,
                onOpenAppPicker = onOpenGamePickerSheet
            )
        }

        // 3. Active Call & VoIP Bouncer
        item(key = "rules_call_bouncer") {
            val activeCallGuards = listOf(
                state.networkLevelCallRejection,
                state.autoDeclineVoipCalls,
                state.allowRepeatCallersEnabled && !state.declineAllCallsRegardless,
                state.allowStarredContactsOnly && !state.declineAllCallsRegardless,
                state.declineAllCallsRegardless
            ).count { it }
            ExpandableConfigCard(
                title = "Active Call & VoIP Bouncer",
                subtitle = "Network-level rejection & 3-minute repeat breakthrough",
                icon = Icons.Rounded.Phone,
                initiallyExpanded = true,
                badgeText = if (state.declineAllCallsRegardless) "ALL BLOCKED" else "$activeCallGuards ACTIVE"
            ) {
                ConfigSwitchRow(
                    title = "Decline All Calls Regardless of Condition",
                    subtitle = "Zero-tolerance lockdown: automatically drops all incoming cellular and VoIP calls, ignoring starred contacts and repeat callers",
                    checked = state.declineAllCallsRegardless,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setDeclineAllCallsRegardless(it)
                    },
                    testTag = "switch_decline_all_calls_regardless"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Network-Level Call Rejection (Instant User Busy)",
                    subtitle = "Sends immediate busy signal to tower without ringing",
                    checked = state.networkLevelCallRejection,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setNetworkLevelCallRejectionEnabled(it)
                    },
                    testTag = "switch_network_rejection"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Auto-Decline WhatsApp/Telegram Calls",
                    subtitle = "Hangs up third-party VoIP calls via programmatic action intent",
                    checked = state.autoDeclineVoipCalls,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setAutoDeclineVoipCallsEnabled(it)
                    },
                    testTag = "switch_voip_bouncer"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "3-Minute Repeat Caller Breakthrough",
                    subtitle = if (state.declineAllCallsRegardless) "Overridden: Decline All Calls is active" else "Second call within 180s rings out loud for real emergencies",
                    checked = state.allowRepeatCallersEnabled && !state.declineAllCallsRegardless,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setAllowRepeatCallersEnabled(it)
                    },
                    testTag = "switch_repeat_breakthrough"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Allow Starred Contacts",
                    subtitle = if (state.declineAllCallsRegardless) "Overridden: Decline All Calls is active" else "Passes priority contacts through cellular & VoIP filters",
                    checked = state.allowStarredContactsOnly && !state.declineAllCallsRegardless,
                    onCheckedChange = { checked ->
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.updateProfileSettings(checked, state.blockRepeatedCallers, state.silenceNotifications)
                    },
                    testTag = "switch_allow_starred"
                )
            }
        }

        // 4. Gaming Mode Lockdown (Collapsible Detail Card)
        item(key = "rules_gaming_lockdown") {
            val activeGameRules = listOf(
                state.autoGameDetectionEnabled,
                state.autoDeclineVoipInGame,
                state.strictGamingLockdownEnabled,
                state.suppressHeadsUpBanners
            ).count { it }
            ExpandableConfigCard(
                title = "Gaming Mode Lockdown",
                subtitle = "Zero latency, zero popups, uninterrupted gaming",
                icon = Icons.Rounded.SportsEsports,
                initiallyExpanded = false,
                badgeText = "$activeGameRules/4 ON"
            ) {
                ConfigSwitchRow(
                    title = "Auto-Activate Game Mode DND on Game Launch",
                    subtitle = "Automatically transitions to Gaming DND whenever any game launches, and exits Gaming Mode 15s after leaving the game",
                    checked = state.autoGameDetectionEnabled,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setAutoGameDetectionEnabled(it)
                    },
                    testTag = "switch_auto_activate_game_dnd"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Auto-Decline WhatsApp & VoIP Calls in Game",
                    subtitle = "Immediately drops VoIP calls in background during gaming",
                    checked = state.autoDeclineVoipInGame,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setAutoDeclineVoipInGameEnabled(it)
                    },
                    testTag = "switch_gaming_voip"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Block Cellular Calls (Including Starred)",
                    subtitle = "Overrides priority contacts so you are never kicked out of match",
                    checked = state.strictGamingLockdownEnabled,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setStrictGamingLockdownEnabled(it)
                    },
                    testTag = "switch_gaming_lockdown"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Suppress All Heads-Up Banners",
                    subtitle = "Blocks intrusive overlay banners while gaming",
                    checked = state.suppressHeadsUpBanners,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setSuppressHeadsUpBannersEnabled(it)
                    },
                    testTag = "switch_gaming_heads_up"
                )
            }
        }

        // 5. Smart Breakthrough & Emergency Scanner (Collapsible Detail Card)
        item(key = "rules_smart_breakthrough") {
            val activeAlertRules = listOf(
                state.smartUrgentBreakthroughEnabled,
                state.silenceNotifications
            ).count { it }
            ExpandableConfigCard(
                title = "Smart Breakthrough & Emergency Scanner",
                subtitle = "Scans critical OTPs, ride drivers, and emergency text alerts",
                icon = Icons.Rounded.Notifications,
                initiallyExpanded = false,
                badgeText = "$activeAlertRules/2 ON"
            ) {
                ConfigSwitchRow(
                    title = "OTP, Ride & 'URGENT' Keyword Breakthrough",
                    subtitle = "Allows 2FA codes, delivery drivers, and emergency text alerts while keeping regular messages silent",
                    checked = state.smartUrgentBreakthroughEnabled,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setSmartUrgentBreakthroughEnabled(it)
                    },
                    testTag = "switch_smart_urgent"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Silence Regular Non-Urgent Notifications",
                    subtitle = "Dismisses standard app banners and chat pings during active Focus while preserving your custom rule toggles across profile switches",
                    checked = state.silenceNotifications,
                    onCheckedChange = { checked ->
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.updateProfileSettings(
                            state.allowStarredContactsOnly,
                            state.blockRepeatedCallers,
                            checked
                        )
                    },
                    testTag = "switch_silence_regular_notifications"
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
    hasOverlayPermission: Boolean,
    grantedPermissionsCount: Int,
    onRequestRuntimePermissions: () -> Unit
) {
    val context = LocalContext.current
    val allPermissionsVerified = grantedPermissionsCount >= 6

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Unified System Permissions Hub Card (All 6 Permissions Piled in One Place in Settings)
        item {
            ExpandableConfigCard(
                title = "System Permissions Hub ($grantedPermissionsCount/6)",
                subtitle = if (allPermissionsVerified) {
                    "All 6 system permissions verified • Tap to review"
                } else {
                    "All required Android permissions consolidated in one place"
                },
                icon = Icons.Rounded.Security,
                initiallyExpanded = true,
                badgeText = if (allPermissionsVerified) "6/6 VERIFIED" else "$grantedPermissionsCount/6 ACTIVE"
            ) {
                PermissionStatusRow(
                    title = "Display Over Other Apps",
                    description = "Required to display the floating 5-second Gaming Mode confirmation pop-up over games",
                    isGranted = hasOverlayPermission,
                    actionLabel = "Grant",
                    onAction = {
                        ExpressiveHaptics.playSpringTap(context)
                        com.example.ui.GamingModePopupManager.requestOverlayPermission(context)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PermissionStatusRow(
                    title = "Usage Stats Access",
                    description = "Required to detect real-time game launches and trigger Gaming Mode",
                    isGranted = hasUsageStatsAccess,
                    actionLabel = "Grant",
                    onAction = {
                        ExpressiveHaptics.playSpringTap(context)
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PermissionStatusRow(
                    title = "Notification Listener Access",
                    description = "Required to silence alerts, detect declined calls, and send call auto-replies",
                    isGranted = hasNotificationListener,
                    actionLabel = "Enable",
                    onAction = {
                        ExpressiveHaptics.playSpringTap(context)
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
                        ExpressiveHaptics.playSpringTap(context)
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PermissionStatusRow(
                    title = "Runtime Permissions",
                    description = "Starred contacts lookup, cellular call termination, and push notifications",
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
                        ExpressiveHaptics.playSpringTap(context)
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
            }
        }

        // 2. Material You Theme Studio
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
                        ExpressiveHaptics.playSpringTap(context)
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
                        ExpressiveHaptics.playSpringTap(context)
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
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.updateThemePreferences(state.themePreferences.copy(amoledPureBlack = checked))
                    },
                    testTag = "switch_theme_amoled"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConfigSwitchRow(
                    title = "Minimalist OLED Focus Lock Screen",
                    subtitle = "Applies custom pitch-black vector artwork per active profile (DND, GAME, WORK, SLEEP) & restores default wallpaper when OFF",
                    checked = state.dimLockScreenEnabled,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setDimLockScreenEnabled(it)
                    },
                    testTag = "switch_dim_lock_screen"
                )
            }
        }

        // 3. Notification Shade Quick Settings Tiles Manager
        item {
            QuickSettingsTilesCard(
                state = state,
                stateManager = stateManager
            )
        }

        // 4. Rust Core Engine & Deterministic State Machine Inspector
        item(key = "system_rust_core_inspector") {
            RustCoreInspectorCard(
                state = state,
                stateManager = stateManager
            )
        }
    }
}

@Composable
fun HeroStatusHeaderCard(
    state: FocusSessionState,
    activeProfile: FocusProfile = state.activeProfile,
    headerBgColor: Color,
    onDurationSelected: (DurationOption) -> Unit,
    onCustomDurationMinutesChanged: (Int) -> Unit = {},
    onAdjustMinutes: (Int) -> Unit = {},
    onToggleAutoReply: () -> Unit = {},
    onTimerExpired: () -> Unit = {},
    onToggleFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isActive = activeProfile != FocusProfile.OFF

    var remainingSeconds by remember(state.expiresAtMillis, isActive) {
        mutableLongStateOf(
            state.expiresAtMillis?.let {
                maxOf(0L, (it - System.currentTimeMillis()) / 1000L)
            } ?: 0L
        )
    }

    LaunchedEffect(state.expiresAtMillis, isActive) {
        val exp = state.expiresAtMillis
        if (isActive && exp != null) {
            while (true) {
                val now = System.currentTimeMillis()
                if (now >= exp) {
                    remainingSeconds = 0L
                    onTimerExpired()
                    break
                }
                remainingSeconds = maxOf(0L, (exp - now) / 1000L)
                delay(250L)
            }
        } else {
            remainingSeconds = 0L
        }
    }

    val toggleInteraction = remember { MutableInteractionSource() }
    val isTogglePressed by toggleInteraction.collectIsPressedAsState()

    // Material 3 Expressive Shape Morphing for the Hero Card and Action Button
    val heroCardCorner by animateDpAsState(
        targetValue = if (isActive) 38.dp else 28.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "hero_card_corner"
    )
    val actionBtnCorner by animateDpAsState(
        targetValue = when {
            isTogglePressed -> 14.dp
            isActive -> 28.dp
            else -> 18.dp
        },
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "hero_action_btn_corner"
    )
    val actionBtnScale by animateFloatAsState(
        targetValue = if (isTogglePressed) 0.95f else 1f,
        animationSpec = ExpressiveMotion.snappySpatialSpring(),
        label = "hero_action_btn_scale"
    )
    val borderGlowColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        animationSpec = ExpressiveMotion.smoothColorSpring(),
        label = "hero_border_glow"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 1.8.dp else 1.dp,
                color = borderGlowColor,
                shape = RoundedCornerShape(heroCardCorner)
            )
            .testTag("hero_status_header_card"),
        shape = RoundedCornerShape(heroCardCorner),
        colors = CardDefaults.elevatedCardColors(containerColor = headerBgColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isActive) 8.dp else 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.22f else 0.08f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = if (isActive) 0.10f else 0.02f),
                            Color.Transparent
                        ),
                        radius = 720f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile & Live Animated Shield Emblem + Glowing Status Badge
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
                        LiveAnimatedShieldEmblem(
                            activeProfile = activeProfile
                        )

                        Column {
                            Text(
                                text = if (isActive) activeProfile.displayName else "Shield Standby",
                                style = MaterialTheme.typography.headlineMedium,
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
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
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
                    val countdownText = if (state.expiresAtMillis != null) {
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
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                RollingTelemetryCounter(
                                    valueText = countdownText,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .clickable { onAdjustMinutes(-15) }
                                        .testTag("btn_timer_minus_15")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .clickable { onAdjustMinutes(15) }
                                        .testTag("btn_timer_plus_15")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Add 15 minutes",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "15m",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive Expressive Custom Duration Slider (5m to 8h)
                var lastDetentBucket by remember { mutableIntStateOf(state.customDurationMinutes / 15) }
                val customHours = state.customDurationMinutes / 60
                val customRemMins = state.customDurationMinutes % 60
                val customDurationLabel = if (customHours > 0) {
                    String.format("%dh %02dm", customHours, customRemMins)
                } else {
                    "${customRemMins}m"
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Shield Timer (5m – 8h)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = customDurationLabel,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Slider(
                            value = state.customDurationMinutes.toFloat(),
                            onValueChange = { raw ->
                                val stepped = ((raw / 5f).roundToInt() * 5).coerceIn(5, 480)
                                val bucket = stepped / 15
                                if (bucket != lastDetentBucket) {
                                    lastDetentBucket = bucket
                                    ExpressiveHaptics.playSpringTap(context)
                                }
                                onCustomDurationMinutesChanged(stepped)
                            },
                            valueRange = 5f..480f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_duration_slider")
                        )
                    }
                }

                // Quick Duration Pills: 1h, 2h, Until Turned Off (with M3 Expressive shape morphing)
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
                        val chipCorner by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 12.dp,
                            animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                            label = "duration_chip_corner_$label"
                        )
                        FilterChip(
                            selected = isSelected,
                            onClick = { onDurationSelected(opt) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(chipCorner),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(if (label == "Until Turned Off") 1.4f else 0.8f)
                        )
                    }
                }

                // Quick Declined Call Auto-Reply Toggle Row (Accessible directly on Command Center)
                val autoReplyRowCorner by animateDpAsState(
                    targetValue = if (state.autoReplyEnabled) 22.dp else 14.dp,
                    animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                    label = "hero_autoreply_row_corner"
                )
                Surface(
                    shape = RoundedCornerShape(autoReplyRowCorner),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    border = BorderStroke(
                        1.dp,
                        if (state.autoReplyEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleAutoReply() }
                        .testTag("hero_auto_reply_toggle_row")
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneCallback,
                                contentDescription = null,
                                tint = if (state.autoReplyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                            Column {
                                Text(
                                    text = "Declined Call Auto-Reply",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.autoReplyEnabled) {
                                        "ON • 3-min per-caller cooldown"
                                    } else {
                                        "OFF • Calls declined silently without reply"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = state.autoReplyEnabled,
                            onCheckedChange = { onToggleAutoReply() },
                            modifier = Modifier.testTag("switch_command_center_auto_reply")
                        )
                    }
                }

                // Primary Action Button (Morphs corner radius & compresses scale on press)
                Button(
                    onClick = onToggleFocus,
                    interactionSource = toggleInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .graphicsLayer {
                            scaleX = actionBtnScale
                            scaleY = actionBtnScale
                        }
                        .testTag("hero_toggle_button"),
                    shape = RoundedCornerShape(actionBtnCorner),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (isActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isActive) "Deactivate Sentinal Shield" else "Activate Focus Shield",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * FocusProfileBentoGrid
 *
 * Material 3 Expressive 2x2 Bento Grid where pressing and selecting a Focus Profile dynamically
 * morphs the tile's corner radius, compresses/rebounds scale with bouncy springs, and fires
 * spring-correlated haptics.
 */
@Composable
fun FocusProfileBentoGrid(
    activeProfile: FocusProfile,
    onProfileSelected: (FocusProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles = listOf(
        Triple(FocusProfile.DO_NOT_DISTURB, Icons.Rounded.DoNotDisturbOn, "Silences notifications & declines calls"),
        Triple(FocusProfile.GAMING, Icons.Rounded.SportsEsports, "Zero popups, VoIP bouncer & 15s exit grace"),
        Triple(FocusProfile.WORK, Icons.Rounded.Work, "Manual or scheduled focus with custom rules"),
        Triple(FocusProfile.SLEEP, Icons.Rounded.Bedtime, "Restful night shield with lock screen dim"),
        Triple(FocusProfile.CINEMA, Icons.Rounded.Movie, "Zero vibration, 2h/2.5h presets & theater shield")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        profiles.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { (profile, icon, summary) ->
                    val isSelected = activeProfile == profile
                    val testTag = "profile_chip_${profile.id.lowercase()}"
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val tileCorner by animateDpAsState(
                        targetValue = when {
                            isPressed -> 14.dp
                            isSelected -> 34.dp
                            else -> 22.dp
                        },
                        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                        label = "bento_corner_${profile.id}"
                    )
                    val iconCorner by animateDpAsState(
                        targetValue = when {
                            isPressed -> 10.dp
                            isSelected -> 20.dp
                            else -> 13.dp
                        },
                        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                        label = "bento_icon_corner_${profile.id}"
                    )
                    val tileScale by animateFloatAsState(
                        targetValue = when {
                            isPressed -> 0.94f
                            isSelected -> 1.02f
                            else -> 1.0f
                        },
                        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                        label = "bento_scale_${profile.id}"
                    )
                    val tileBgColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = ExpressiveMotion.smoothColorSpring(),
                        label = "bento_bg_${profile.id}"
                    )

                    Surface(
                        shape = RoundedCornerShape(tileCorner),
                        color = tileBgColor,
                        border = if (isSelected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        },
                        shadowElevation = if (isSelected) 6.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = tileScale
                                scaleY = tileScale
                            }
                            .clip(RoundedCornerShape(tileCorner))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onProfileSelected(profile) }
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
                                    shape = RoundedCornerShape(iconCorner),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF38EF7D).copy(alpha = 0.22f),
                                        border = BorderStroke(1.dp, Color(0xFF38EF7D).copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "ON",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38EF7D),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
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

private data class QuickRuleTileData(
    val title: String,
    val statusText: String,
    val icon: ImageVector,
    val isEnabled: Boolean,
    val testTag: String,
    val onToggle: () -> Unit
)

/**
 * QuickRulesBentoGrid
 *
 * Compact 2x2 Material 3 Expressive Bento Grid on the Command Center providing 1-tap toggle access
 * to the 4 most-used protection rules: Decline All Calls, Call Auto-Reply, Auto-Game DND, and Starred VIP Bypass.
 */
@Composable
fun QuickRulesBentoGrid(
    state: FocusSessionState,
    stateManager: FocusStateManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vipEffective = state.allowStarredContactsOnly && !state.declineAllCallsRegardless

    val tiles = listOf(
        QuickRuleTileData(
            title = "Decline All Calls",
            statusText = if (state.declineAllCallsRegardless) "ON • Zero-Tolerance" else "OFF • Filtered",
            icon = Icons.Rounded.Phone,
            isEnabled = state.declineAllCallsRegardless,
            testTag = "bento_quick_decline_all",
            onToggle = {
                ExpressiveHaptics.playSpringTap(context)
                stateManager.setDeclineAllCallsRegardless(!state.declineAllCallsRegardless)
            }
        ),
        QuickRuleTileData(
            title = "Call Auto-Reply",
            statusText = if (state.autoReplyEnabled) "ON • 3m Cooldown" else "OFF • Silent",
            icon = Icons.Rounded.PhoneCallback,
            isEnabled = state.autoReplyEnabled,
            testTag = "bento_quick_auto_reply",
            onToggle = {
                ExpressiveHaptics.playSpringTap(context)
                stateManager.toggleAutoReply()
            }
        ),
        QuickRuleTileData(
            title = "Auto-Game DND",
            statusText = if (state.autoGameDetectionEnabled) "ON • 15s Exit Grace" else "OFF • Manual Only",
            icon = Icons.Rounded.SportsEsports,
            isEnabled = state.autoGameDetectionEnabled,
            testTag = "bento_quick_auto_game",
            onToggle = {
                ExpressiveHaptics.playSpringTap(context)
                stateManager.setAutoGameDetectionEnabled(!state.autoGameDetectionEnabled)
            }
        ),
        QuickRuleTileData(
            title = "Starred VIP Bypass",
            statusText = when {
                state.declineAllCallsRegardless -> "Overridden by All-Block"
                vipEffective -> "ON • VIPs Ring"
                else -> "OFF • Blocked"
            },
            icon = Icons.Rounded.Security,
            isEnabled = vipEffective,
            testTag = "bento_quick_starred_vip",
            onToggle = {
                ExpressiveHaptics.playSpringTap(context)
                if (state.declineAllCallsRegardless) {
                    stateManager.setDeclineAllCallsRegardless(false)
                    stateManager.updateProfileSettings(true, state.blockRepeatedCallers, state.silenceNotifications)
                } else {
                    stateManager.updateProfileSettings(
                        !state.allowStarredContactsOnly,
                        state.blockRepeatedCallers,
                        state.silenceNotifications
                    )
                }
            }
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_rules_bento_grid"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowTiles.forEach { tile ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val corner by animateDpAsState(
                        targetValue = when {
                            isPressed -> 12.dp
                            tile.isEnabled -> 26.dp
                            else -> 18.dp
                        },
                        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
                        label = "quick_rule_corner_${tile.testTag}"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.94f else 1f,
                        animationSpec = ExpressiveMotion.snappySpatialSpring(),
                        label = "quick_rule_scale_${tile.testTag}"
                    )
                    val bgColor by animateColorAsState(
                        targetValue = if (tile.isEnabled) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        animationSpec = ExpressiveMotion.smoothColorSpring(),
                        label = "quick_rule_bg_${tile.testTag}"
                    )

                    Surface(
                        shape = RoundedCornerShape(corner),
                        color = bgColor,
                        border = BorderStroke(
                            width = if (tile.isEnabled) 1.5.dp else 1.dp,
                            color = if (tile.isEnabled) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(corner))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { tile.onToggle() }
                            .testTag(tile.testTag)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (tile.isEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = tile.icon,
                                        contentDescription = null,
                                        tint = if (tile.isEnabled) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tile.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (tile.isEnabled) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tile.statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (tile.isEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
    val context = LocalContext.current
    val cardCorner by animateDpAsState(
        targetValue = if (state.autoReplyEnabled) 30.dp else 22.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "voip_card_corner"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voip_auto_reply_card"),
        shape = RoundedCornerShape(cardCorner),
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
                        shape = RoundedCornerShape(if (state.autoReplyEnabled) 21.dp else 14.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Declined Call Auto-Reply",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "3M COOLDOWN",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Replies strictly to declined incoming calls with a 3-minute per-caller cooldown (never to outgoing calls or regular messages)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = state.autoReplyEnabled,
                    onCheckedChange = {
                        ExpressiveHaptics.playSpringTap(context)
                        stateManager.setAutoReplyEnabled(it)
                    },
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
                            shape = CircleShape,
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
                        shape = RoundedCornerShape(18.dp),
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
                            onClick = {
                                ExpressiveHaptics.playNotificationDismissed(context)
                                stateManager.resetSessionCaches()
                            },
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
fun ExpandableConfigCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    initiallyExpanded: Boolean,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val cardCorner by animateDpAsState(
        targetValue = if (expanded) 28.dp else 20.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "expandable_card_corner"
    )
    val iconCorner by animateDpAsState(
        targetValue = if (expanded) 21.dp else 13.dp,
        animationSpec = ExpressiveMotion.bouncySpatialSpring(),
        label = "expandable_icon_corner"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        ExpressiveHaptics.playSpringTap(context)
                        expanded = !expanded
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(iconCorner),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (badgeText != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF38EF7D).copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, Color(0xFF38EF7D).copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38EF7D),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        ExpressiveHaptics.playSpringTap(context)
                        expanded = !expanded
                    }
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = ExpressiveMotion.bouncySpatialSpring()) + fadeIn(),
                exit = shrinkVertically(animationSpec = ExpressiveMotion.snappySpatialSpring()) + fadeOut()
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
                shape = CircleShape,
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
