package com.example.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.core.FocusProfile
import com.example.core.FocusStateManager

/**
 * SentinelWidgetProvider
 *
 * Resizable 2x1 Material You Home Screen Widget for Sentinel (com.zenshield.focus).
 * - Left Touch Zone (@id/widget_left_container): Launches MainActivity dashboard.
 * - Right Touch Zone (@id/widget_toggle_button): Instantly toggles Focus Mode
 *   (activates DO_NOT_DISTURB when OFF, or turns Sentinel OFF when active).
 */
class SentinelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_WIDGET) {
            runCatching {
                val stateManager = FocusStateManager.getInstance(context.applicationContext)
                val currentProfile = FocusStateManager.getActiveProfile(context.applicationContext)
                stateManager.resetSessionCaches()
                if (currentProfile != FocusProfile.OFF) {
                    stateManager.setProfile(FocusProfile.OFF)
                } else {
                    val duration = stateManager.state.value.durationOption
                    stateManager.setProfile(FocusProfile.DO_NOT_DISTURB, duration)
                }
                updateAllWidgets(context.applicationContext)
            }.onFailure { e ->
                Log.e(TAG, "Failed to toggle Sentinel widget state: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "SentinelWidgetProvider"
        const val ACTION_TOGGLE_WIDGET = "com.zenshield.focus.ACTION_TOGGLE_WIDGET"

        private const val REQUEST_CODE_OPEN_APP = 7101
        private const val REQUEST_CODE_TOGGLE_FOCUS = 7102

        fun updateAllWidgets(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                val componentName = ComponentName(appContext, SentinelWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    for (appWidgetId in appWidgetIds) {
                        updateWidget(appContext, appWidgetManager, appWidgetId)
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Unable to update Sentinel home screen widgets: ${e.message}")
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val stateManager = FocusStateManager.getInstance(context.applicationContext)
            val activeProfile = FocusStateManager.getActiveProfile(context.applicationContext)
            val sessionState = stateManager.state.value
            val isActive = activeProfile != FocusProfile.OFF

            val views = RemoteViews(context.packageName, R.layout.widget_sentinel)

            // 1. Update Card & Badge Background Drawables
            views.setInt(
                R.id.widget_root_container,
                "setBackgroundResource",
                if (isActive) R.drawable.bg_widget_sentinel_active else R.drawable.bg_widget_sentinel_inactive
            )
            views.setInt(
                R.id.widget_icon_badge,
                "setBackgroundResource",
                if (isActive) R.drawable.bg_widget_icon_circle_active else R.drawable.bg_widget_icon_circle_inactive
            )
            views.setImageViewResource(
                R.id.widget_status_icon,
                if (isActive) R.drawable.ic_widget_shield_active else R.drawable.ic_widget_shield_inactive
            )

            // 2. Update Status Title & Profile Subtitle
            views.setTextViewText(
                R.id.widget_title_text,
                if (isActive) {
                    context.getString(R.string.widget_sentinel_active)
                } else {
                    context.getString(R.string.widget_sentinel_inactive)
                }
            )

            val subtitleText = if (isActive) {
                val ruleTag = if (sessionState.autoReplyEnabled) "Auto-Reply On" else "Shield Engaged"
                "${activeProfile.displayName} • $ruleTag"
            } else {
                "Tap toggle to engage DND"
            }
            views.setTextViewText(R.id.widget_subtitle_text, subtitleText)
            views.setTextColor(
                R.id.widget_subtitle_text,
                if (isActive) Color.parseColor("#38EF7D") else Color.parseColor("#94A3B8")
            )

            // 3. Update Right-Side Pill Toggle Button
            views.setInt(
                R.id.widget_toggle_button,
                "setBackgroundResource",
                if (isActive) R.drawable.bg_widget_toggle_pill_on else R.drawable.bg_widget_toggle_pill_off
            )
            views.setTextViewText(
                R.id.widget_toggle_text,
                if (isActive) "ON" else "OFF"
            )
            views.setTextColor(
                R.id.widget_toggle_text,
                if (isActive) Color.parseColor("#062116") else Color.parseColor("#E2E8F0")
            )

            // 4. Wire Left Zone -> Launch MainActivity Dashboard
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_APP,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_left_container, openAppPendingIntent)

            // 5. Wire Right Toggle Button -> Broadcast Instant Focus Toggle
            val toggleIntent = Intent(context, SentinelWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_WIDGET
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_TOGGLE_FOCUS,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun isPinWidgetSupported(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return runCatching {
                AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
            }.getOrDefault(false)
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return runCatching {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
                val provider = ComponentName(context, SentinelWidgetProvider::class.java)
                appWidgetManager.requestPinAppWidget(provider, null, null)
            }.getOrDefault(false)
        }
    }
}
