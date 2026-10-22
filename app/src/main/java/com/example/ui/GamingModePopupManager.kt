package com.example.ui

import android.content.Context
import android.graphics.PixelFormat
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
    val title: String = "GAMING MODE ACTIVE",
    val subtitle: String = "Calls & Pop-ups Shielded • Zero Interruption",
    val accentColorHex: Long = 0xFF38EF7D,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages the non-interactive, touch-passthrough Gaming Mode activation pop-up:
 * 1. Emits in-app Compose top HUD pill banner state (3.0 seconds, zero touch interception).
 * 2. Renders a system WindowManager top pill with FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE when
 *    overlay permission is available, or falls back to a lightweight Android system Toast.
 */
object GamingModePopupManager {
    private const val TAG = "GamingModePopup"
    private const val DISPLAY_DURATION_MS = 3000L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dismissJob: Job? = null
    private var activeOverlayView: View? = null

    private val _popupState = MutableStateFlow<GamingPopupBannerState?>(null)
    val popupState: StateFlow<GamingPopupBannerState?> = _popupState.asStateFlow()

    fun showGamingModeActivatedPopup(
        context: Context,
        triggerSource: String = "Focus Shield"
    ) {
        val subtitle = if (triggerSource.isNotBlank() && triggerSource != "Focus Shield") {
            "Auto-engaged for $triggerSource • Calls & Alerts Shielded"
        } else {
            "Calls & Pop-ups Shielded • Zero Interruption"
        }

        val banner = GamingPopupBannerState(
            title = "GAMING MODE ACTIVE",
            subtitle = subtitle,
            accentColorHex = 0xFF38EF7D
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
 * Non-interactive, touch-passthrough Compose overlay pill rendered at the top of the screen
 * whenever Gaming Mode activates. Consumes zero touch events so the user's taps pass straight
 * through to the underlying UI.
 */
@Composable
fun GamingModeNonInteractiveOverlay(
    modifier: Modifier = Modifier
) {
    val banner by GamingModePopupManager.popupState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = banner != null,
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
            val accentColor = Color(activeBanner.accentColorHex)

            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xF20B111E),
                border = BorderStroke(1.5.dp, accentColor),
                shadowElevation = 10.dp,
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
                        color = accentColor.copy(alpha = 0.18f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Text(
                                text = activeBanner.title,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = accentColor,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Text(
                            text = activeBanner.subtitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }
}
