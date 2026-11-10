package com.example.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example. MainActivity
import com.example.R
import com.example.core.FocusStateManager
import com.example.services.ZenShieldForegroundService
import com.example.ui.theme.ExpressiveHaptics
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GamingPopupBannerState(
    val title: String = "Gaming Mode Active",
    val subtitle: String = "Calls & Pop-ups Shielded • Zero Interruption",
    val accentColorHex: Long = 0xFFA8C7FA,
    val timestamp: Long = System.currentTimeMillis()
)

data class GamingActivationPromptState(
    val packageName: String,
    val gameLabel: String,
    val remainingSeconds: Int = FocusStateManager.GAME_CONFIRMATION_COUNTDOWN_SECONDS,
    val totalSeconds: Int = FocusStateManager.GAME_CONFIRMATION_COUNTDOWN_SECONDS
)

/**
 * Manages both:
 * 1. The interactive Gaming Mode Auto-Activation Confirmation Pop-up ("Activate (5s)" / "Not Now")
 *    when a game is detected launching.
 * 2. The non-interactive, touch-passthrough Gaming Mode activated status HUD banner once engaged.
 */
object GamingModePopupManager {
    private const val TAG = "GamingModePopup"
    private const val DISPLAY_DURATION_MS = 3000L
    const val GAME_PROMPT_CHANNEL_ID = "zenshield_game_prompt_high_v1"
    const val PROMPT_NOTIFICATION_ID = 9002

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dismissJob: Job? = null
    private var activeOverlayView: View? = null
    private var activePromptOverlayView: View? = null

    fun hasOverlayPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure {
                runCatching {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                }
            }
        }
    }

    private val _popupState = MutableStateFlow<GamingPopupBannerState?>(null)
    val popupState: StateFlow<GamingPopupBannerState?> = _popupState.asStateFlow()

    private val _promptState = MutableStateFlow<GamingActivationPromptState?>(null)
    val promptState: StateFlow<GamingActivationPromptState?> = _promptState.asStateFlow()

    /**
     * Displays the interactive 5-second confirmation prompt asking the user if they want
     * Gaming Mode to activate for the detected game.
     * Uses dual delivery:
     * 1. High-Priority Heads-Up Notification with "Activate (5s)" and "Not Now" action buttons
     * 2. Floating WindowManager overlay (when Display over other apps is granted) + in-app Compose banner
     */
    fun showGamingActivationPrompt(
        context: Context,
        packageName: String,
        gameLabel: String,
        remainingSeconds: Int = FocusStateManager.GAME_CONFIRMATION_COUNTDOWN_SECONDS
    ) {
        val prompt = GamingActivationPromptState(
            packageName = packageName,
            gameLabel = gameLabel,
            remainingSeconds = remainingSeconds,
            totalSeconds = FocusStateManager.GAME_CONFIRMATION_COUNTDOWN_SECONDS
        )
        _promptState.value = prompt

        runCatching {
            postOrUpdateGamePromptHeadsUpNotification(
                appContext = context.applicationContext,
                prompt = prompt,
                isInitialAlert = true
            )
        }.onFailure {
            Log.w(TAG, "Unable to post Gaming Mode heads-up notification: ${it.message}")
        }

        runCatching {
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                tryShowSystemInteractivePromptOverlay(context.applicationContext, prompt)
            }
        }.onFailure {
            Log.w(TAG, "Unable to show Gaming Mode confirmation prompt on main looper: ${it.message}")
        }
    }

    /**
     * Updates the live countdown seconds (5s -> 1s) on the active confirmation prompt.
     */
    fun updateGamingActivationPromptCountdown(
        context: Context,
        packageName: String,
        gameLabel: String,
        remainingSeconds: Int
    ) {
        val current = _promptState.value
        val total = current?.totalSeconds ?: FocusStateManager.GAME_CONFIRMATION_COUNTDOWN_SECONDS
        val updated = GamingActivationPromptState(
            packageName = packageName,
            gameLabel = gameLabel,
            remainingSeconds = remainingSeconds,
            totalSeconds = total
        )
        _promptState.value = updated

        runCatching {
            postOrUpdateGamePromptHeadsUpNotification(
                appContext = context.applicationContext,
                prompt = updated,
                isInitialAlert = false
            )
        }

        runCatching {
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                val view = activePromptOverlayView ?: return@post
                view.findViewById<TextView>(R.id.tv_prompt_subtitle)?.text =
                    "$gameLabel • Auto-activating in ${remainingSeconds}s…"
                view.findViewById<TextView>(R.id.btn_popup_activate)?.text =
                    "Activate (${remainingSeconds}s)"
            }
        }
    }

    /**
     * Dismisses the interactive confirmation prompt overlay and cancels the heads-up notification.
     */
    fun dismissGamingActivationPrompt(context: Context? = null) {
        _promptState.value = null
        if (context != null) {
            val appContext = context.applicationContext
            runCatching {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(PROMPT_NOTIFICATION_ID)
            }
            runCatching {
                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post {
                    val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    if (wm != null) {
                        removeActivePromptOverlay(wm)
                    }
                }
            }
        }
    }

    private fun ensureGamePromptChannel(appContext: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(GAME_PROMPT_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    GAME_PROMPT_CHANNEL_ID,
                    "Gaming Mode Launch Confirmation",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "5-second heads-up confirmation banner when a game launches"
                    enableVibration(true)
                    setShowBadge(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun postOrUpdateGamePromptHeadsUpNotification(
        appContext: Context,
        prompt: GamingActivationPromptState,
        isInitialAlert: Boolean
    ) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureGamePromptChannel(appContext, nm)

        val contentIntent = PendingIntent.getActivity(
            appContext,
            9100,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmIntent = PendingIntent.getService(
            appContext,
            9101,
            Intent(appContext, ZenShieldForegroundService::class.java).apply {
                action = ZenShieldForegroundService.ACTION_CONFIRM_GAME_PROMPT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = PendingIntent.getService(
            appContext,
            9102,
            Intent(appContext, ZenShieldForegroundService::class.java).apply {
                action = ZenShieldForegroundService.ACTION_DECLINE_GAME_PROMPT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, GAME_PROMPT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("🎮 Game Detected: ${prompt.gameLabel}")
            .setContentText("Auto-activating Gaming Mode in ${prompt.remainingSeconds}s… Tap Not Now to stay off")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(!isInitialAlert)
            .setAutoCancel(false)
            .setOngoing(true)
            .setProgress(prompt.totalSeconds, prompt.remainingSeconds, false)
            .setContentIntent(contentIntent)
            .setDeleteIntent(declineIntent)
            .addAction(
                R.drawable.ic_launcher_monochrome,
                "Activate (${prompt.remainingSeconds}s)",
                confirmIntent
            )
            .addAction(
                R.drawable.ic_launcher_monochrome,
                "Not Now",
                declineIntent
            )
            .build()

        nm.notify(PROMPT_NOTIFICATION_ID, notification)
    }

    fun showGamingModeActivatedPopup(
        context: Context,
        triggerSource: String = "Focus Shield"
    ) {
        dismissGamingActivationPrompt(context)

        val subtitle = if (triggerSource.isNotBlank() && triggerSource != "Focus Shield") {
            "Auto-engaged for $triggerSource • Calls & Alerts Shielded"
        } else {
            "Calls & Pop-ups Shielded • Zero Interruption"
        }

        val banner = GamingPopupBannerState(
            title = "Gaming Mode Active",
            subtitle = subtitle,
            accentColorHex = 0xFFA8C7FA
        )

        // 1. Update in-app non-interactive HUD banner flow
        _popupState.value = banner
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(DISPLAY_DURATION_MS)
            _popupState.value = null
        }

        // 2. Display system-level non-interactive WindowManager pill or Toast fallback on Main thread
        runCatching {
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                val shownOverlay = tryShowSystemPassthroughOverlay(context.applicationContext, banner)
                if (!shownOverlay) {
                    tryShowSystemToast(context.applicationContext, banner)
                }
            }
        }.onFailure {
            Log.w(TAG, "Unable to dispatch Gaming Mode pop-up on main looper: ${it.message}")
        }
    }

    private fun tryShowSystemInteractivePromptOverlay(
        appContext: Context,
        prompt: GamingActivationPromptState
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            return false
        }

        return try {
            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return false

            removeActivePromptOverlay(windowManager)

            val view = LayoutInflater.from(appContext).inflate(R.layout.popup_gaming_mode_prompt, null)
            view.findViewById<TextView>(R.id.tv_prompt_title)?.text = "Game Detected • Activate Gaming Mode?"
            view.findViewById<TextView>(R.id.tv_prompt_subtitle)?.text =
                "${prompt.gameLabel} • Auto-activating in ${prompt.remainingSeconds}s…"

            val notNowBtn = view.findViewById<TextView>(R.id.btn_popup_not_now)
            val activateBtn = view.findViewById<TextView>(R.id.btn_popup_activate)
            activateBtn?.text = "Activate (${prompt.remainingSeconds}s)"

            notNowBtn?.setOnClickListener {
                ExpressiveHaptics.playSpringTap(appContext)
                FocusStateManager.getInstance(appContext).declinePendingGameActivation()
            }

            activateBtn?.setOnClickListener {
                ExpressiveHaptics.playFocusActivated(appContext)
                FocusStateManager.getInstance(appContext).confirmPendingGameActivation()
            }

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }

            // FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCH_MODAL allows buttons inside the prompt pill to
            // receive taps while outside touches pass straight through to the running game.
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 96
            }

            windowManager.addView(view, params)
            activePromptOverlayView = view
            true
        } catch (e: Exception) {
            Log.d(TAG, "Interactive system prompt overlay unavailable (${e.message}).")
            false
        }
    }

    private fun tryShowSystemPassthroughOverlay(
        appContext: Context,
        banner: GamingPopupBannerState
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            return false
        }

        return try {
            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return false

            removeActiveOverlay(windowManager)

            val view = LayoutInflater.from(appContext).inflate(R.layout.popup_gaming_mode_hud, null)
            view.findViewById<TextView>(R.id.tv_popup_title)?.text = banner.title
            view.findViewById<TextView>(R.id.tv_popup_subtitle)?.text = banner.subtitle

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 96
            }

            windowManager.addView(view, params)
            activeOverlayView = view

            Handler(Looper.getMainLooper()).postDelayed({
                removeActiveOverlay(windowManager)
            }, DISPLAY_DURATION_MS)

            true
        } catch (e: Exception) {
            Log.d(TAG, "System overlay unavailable (${e.message}), using Toast fallback.")
            false
        }
    }

    private fun removeActivePromptOverlay(windowManager: WindowManager) {
        val current = activePromptOverlayView ?: return
        activePromptOverlayView = null
        runCatching {
            windowManager.removeViewImmediate(current)
        }
    }

    private fun removeActiveOverlay(windowManager: WindowManager) {
        val current = activeOverlayView ?: return
        activeOverlayView = null
        runCatching {
            windowManager.removeViewImmediate(current)
        }
    }

    private fun tryShowSystemToast(
        appContext: Context,
        banner: GamingPopupBannerState
    ) {
        runCatching {
            Toast.makeText(
                appContext,
                "🎮 ${banner.title} • ${banner.subtitle}",
                Toast.LENGTH_SHORT
            ).apply {
                setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 120)
            }.show()
        }
    }
}

/**
 * Top overlay rendered at the top of the screen:
 * - Renders the interactive Gaming Mode Auto-Activation Confirmation Pop-up ("Activate (5s)" / "Not Now")
 *   when `promptState` is active.
 * - Renders the non-interactive touch-passthrough status pill when `popupState` is active.
 */
@Composable
fun GamingModeNonInteractiveOverlay(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prompt by GamingModePopupManager.promptState.collectAsState()
    val banner by GamingModePopupManager.popupState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // 1. Dedicated Interactive Confirmation Pop-up ("Activate (5s)" / "Not Now")
        AnimatedVisibility(
            visible = prompt != null,
            enter = slideInVertically(
                initialOffsetY = { -it - 40 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it - 40 }
            ) + fadeOut()
        ) {
            val activePrompt = prompt ?: GamingActivationPromptState(
                packageName = "com.game.launch",
                gameLabel = "Game"
            )
            val progress by animateFloatAsState(
                targetValue = (activePrompt.remainingSeconds.toFloat() / activePrompt.totalSeconds.coerceAtLeast(1).toFloat())
                    .coerceIn(0f, 1f),
                label = "gaming_prompt_countdown_progress"
            )

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .testTag("gaming_mode_confirmation_popup")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Game Detected • Activate Gaming Mode?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${activePrompt.gameLabel} • Auto-activating Gaming Mode in ${activePrompt.remainingSeconds}s…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Not Now Button (Material 3 Secondary Container Tonal Pill)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    ExpressiveHaptics.playSpringTap(context)
                                    FocusStateManager.getInstance(context).declinePendingGameActivation()
                                }
                                .testTag("gaming_prompt_not_now_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Not Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        // Activate Button (Material 3 Primary Container Tonal Pill)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    ExpressiveHaptics.playFocusActivated(context)
                                    FocusStateManager.getInstance(context).confirmPendingGameActivation()
                                }
                                .testTag("gaming_prompt_activate_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Activate (${activePrompt.remainingSeconds}s)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Non-Interactive Activated Status Pill (Material 3 Tonal Card)
        AnimatedVisibility(
            visible = prompt == null && banner != null,
            enter = slideInVertically(
                initialOffsetY = { -it - 40 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it - 40 }
            ) + fadeOut()
        ) {
            val activeBanner = banner ?: GamingPopupBannerState()

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .testTag("gaming_mode_non_interactive_popup")
                    .clearAndSetSemantics {
                        contentDescription = "${activeBanner.title}: ${activeBanner.subtitle}"
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = activeBanner.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = activeBanner.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
