package com.example

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusSessionState
import com.example.core.FocusStateManager
import com.example.services.GameDetectorService
import com.example.ui.AppWhitelistBottomSheet
import com.example.ui.AutoReplyCustomMessageBottomSheet
import com.example.ui.GameDetectionCard
import com.example.ui.theme.ZenShieldTheme
import kotlinx.coroutines.delay

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

        // Automatic Permission Audit on Launch (Android 13+ POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val activeProfile by stateManager.activeProfileFlow.collectAsState()
            val state by stateManager.state.collectAsState()

            ZenShieldTheme(
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

    override fun onResume() {
        super.onResume()
        // Re-sync on resume
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
    val context = LocalContext.current
    var showWhitelistSheet by remember { mutableStateOf(false) }
    var showGamePickerSheet by remember { mutableStateOf(false) }
    var showAutoReplySheet by remember { mutableStateOf(false) }

    // System Permissions & Access Trackers
    var hasNotificationListener by remember { mutableStateOf(checkNotificationListenerAccess(context)) }
    var hasDndAccess by remember { mutableStateOf(stateManager.isNotificationPolicyAccessGranted()) }
    var hasRuntimePermissions by remember { mutableStateOf(checkRuntimePermissions(context)) }
    var hasBatteryExemption by remember { mutableStateOf(checkBatteryOptimizationExemption(context)) }
    var hasUsageStatsAccess by remember { mutableStateOf(GameDetectorService.hasUsageStatsPermission(context)) }

    // Re-check permissions and roles on lifecycle resume
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
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

    // Dynamic Header Background animated color
    val animatedHeaderColor by animateColorAsState(
        targetValue = when (activeProfile) {
            FocusProfile.OFF -> MaterialTheme.colorScheme.surfaceContainer
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
            LargeTopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Intelligent Material You Focus Guardian",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable { showWhitelistSheet = true }
                            .testTag("app_whitelist_quick_action")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Whitelist",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
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
            // 1. Hero Status Header Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HeroStatusHeaderCard(
                    state = state,
                    activeProfile = activeProfile,
                    headerBgColor = animatedHeaderColor,
                    onDurationSelected = { duration ->
                        stateManager.setDuration(duration)
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

            // 2. Horizontal Profile Carousel
            item {
                Text(
                    text = "Focus Profile Selector",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalProfileCarousel(
                    activeProfile = activeProfile,
                    onProfileSelected = { profile ->
                        stateManager.resetSessionCaches()
                        stateManager.setProfile(profile, state.durationOption)
                    }
                )
            }

            // 3. Tabbed / Expandable Configuration Cards
            item {
                Text(
                    text = "Shield Engine Rules & Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Card 1: Gaming Mode Lockdown
            item {
                ExpandableConfigCard(
                    title = "Gaming Mode Lockdown",
                    subtitle = "Zero latency, zero popups, uninterrupted gaming",
                    icon = Icons.Rounded.SportsEsports,
                    initiallyExpanded = state.activeProfile == FocusProfile.GAMING
                ) {
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

            // Real-Time Game Launch Detector & Zero-Airtime VoIP Reply
            item {
                GameDetectionCard(
                    state = state,
                    stateManager = stateManager,
                    onOpenAppPicker = { showGamePickerSheet = true }
                )
            }

            // Card 2: Active Call & VoIP Bouncer
            item {
                ExpandableConfigCard(
                    title = "Active Call & VoIP Bouncer",
                    subtitle = "Network-level rejection & 3-minute repeat breakthrough",
                    icon = Icons.Rounded.Phone,
                    initiallyExpanded = true
                ) {
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
                        subtitle = "Second call within 180s rings out loud for real emergencies",
                        checked = state.allowRepeatCallersEnabled,
                        onCheckedChange = { stateManager.setAllowRepeatCallersEnabled(it) },
                        testTag = "switch_repeat_breakthrough"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ConfigSwitchRow(
                        title = "Allow Starred Contacts",
                        subtitle = "Passes priority contacts through cellular & VoIP filters",
                        checked = state.allowStarredContactsOnly,
                        onCheckedChange = { checked ->
                            stateManager.updateProfileSettings(checked, state.blockRepeatedCallers, state.silenceNotifications)
                        },
                        testTag = "switch_allow_starred"
                    )
                }
            }

            // 3. VoIP Auto-Reply Customization Card (ElevatedCard)
            item {
                VoipAutoReplyCustomizationCard(
                    state = state,
                    stateManager = stateManager,
                    onOpenCustomizeSheet = { showAutoReplySheet = true }
                )
            }

            // Card 3: Smart Breakthrough & Emergency Scanner
            item {
                ExpandableConfigCard(
                    title = "Smart Breakthrough & Emergency Scanner",
                    subtitle = "Scans critical OTPs, ride drivers, and emergency text alerts",
                    icon = Icons.Rounded.Notifications,
                    initiallyExpanded = false
                ) {
                    ConfigSwitchRow(
                        title = "OTP, Ride & 'URGENT' Keyword Breakthrough",
                        subtitle = "Allows 2FA codes, delivery drivers, and emergency text alerts",
                        checked = state.smartUrgentBreakthroughEnabled,
                        onCheckedChange = { stateManager.setSmartUrgentBreakthroughEnabled(it) },
                        testTag = "switch_smart_urgent"
                    )
                }
            }

            // Card 4: Appearance & Material You Theme
            item {
                ExpandableConfigCard(
                    title = "Appearance & Material You Theme",
                    subtitle = "Dynamic color palette, profile tints, and pure black",
                    icon = Icons.Rounded.Palette,
                    initiallyExpanded = false
                ) {
                    ConfigSwitchRow(
                        title = "Use Material You Dynamic Wallpaper Colors",
                        subtitle = "Extracts colors directly from your Android wallpaper palette",
                        checked = state.themePreferences.followSystemWallpaper,
                        onCheckedChange = { checked ->
                            stateManager.updateThemePreferences(state.themePreferences.copy(followSystemWallpaper = checked))
                        },
                        testTag = "switch_theme_dynamic"
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
                }
            }

            // Card 5: System Permissions Checklist Card
            item {
                ExpandableConfigCard(
                    title = "System Permissions Checklist",
                    subtitle = "Interactive checklist for hardware and Android services",
                    icon = Icons.Rounded.Security,
                    initiallyExpanded = true
                ) {
                    PermissionStatusRow(
                        title = "Notification Listener Access",
                        description = "Required to intercept and actively decline cellular and VoIP calls and auto-reply.",
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
                        onAction = {
                            val perms = mutableListOf(Manifest.permission.READ_CONTACTS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionsLauncher.launch(perms.toTypedArray())
                        }
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

            item {
                Spacer(modifier = Modifier.height(24.dp))
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
fun HeroStatusHeaderCard(
    state: FocusSessionState,
    activeProfile: FocusProfile = state.activeProfile,
    headerBgColor: Color,
    onDurationSelected: (DurationOption) -> Unit,
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
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

                    Column {
                        Text(
                            text = if (isActive) activeProfile.displayName else "Shield Inactive",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isActive) activeProfile.subtitle else "Tap a profile below to activate focus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Glowing Status Badge
                Surface(
                    shape = CircleShape,
                    color = if (isActive) Color(0xFF38EF7D).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isActive) Color(0xFF38EF7D) else Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.padding(start = 4.dp)
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
                            text = if (isActive) "ACTIVE" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Countdown timer indicator
            if (isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                String.format("%02d:%02d:%02d remaining", hours, minutes, seconds)
                            } else {
                                String.format("%02d:%02d remaining", minutes, seconds)
                            }
                        } else {
                            "Active until turned off"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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

            // Big Toggle Button
            Button(
                onClick = onToggleFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("hero_toggle_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isActive) "Deactivate ZenShield" else "Activate Focus Shield",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun HorizontalProfileCarousel(
    activeProfile: FocusProfile,
    onProfileSelected: (FocusProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(
            FocusProfile.OFF to Icons.Rounded.Shield,
            FocusProfile.DO_NOT_DISTURB to Icons.Rounded.DoNotDisturbOn,
            FocusProfile.GAMING to Icons.Rounded.SportsEsports,
            FocusProfile.WORK to Icons.Rounded.Work,
            FocusProfile.SLEEP to Icons.Rounded.Bedtime
        ).forEach { (profile, icon) ->
            val isSelected = activeProfile == profile
            val testTag = "profile_chip_${profile.id.lowercase()}"

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .width(130.dp)
                    .clickable { onProfileSelected(profile) }
                    .testTag(testTag)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
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

                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
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
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "VoIP Auto-Reply Customization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Auto-replies via in-app RemoteInput when VoIP calls are bounced",
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
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
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
