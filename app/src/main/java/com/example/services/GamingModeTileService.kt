package com.example.services

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.example.R
import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import com.example.ui.theme.ExpressiveHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Custom Quick Settings Tile for the system notification shade that allows the user
 * to toggle Gaming Mode directly without opening the Sentinal app.
 */
class GamingModeTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observerJob: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        val stateManager = FocusStateManager.getInstance(applicationContext)
        updateTileState(stateManager.getActiveProfile())
    }

    override fun onStartListening() {
        super.onStartListening()
        val stateManager = FocusStateManager.getInstance(applicationContext)
        updateTileState(stateManager.getActiveProfile())

        observerJob?.cancel()
        observerJob = scope.launch {
            stateManager.state.collect { state ->
                updateTileState(state.activeProfile)
            }
        }
    }

    override fun onStopListening() {
        observerJob?.cancel()
        observerJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val toggleAction = Runnable {
            val stateManager = FocusStateManager.getInstance(applicationContext)
            val nextProfile = computeToggledGamingProfile(stateManager.getActiveProfile())
            ExpressiveHaptics.playSpringTap(applicationContext)
            stateManager.setProfile(
                profile = nextProfile,
                duration = if (nextProfile == FocusProfile.GAMING) {
                    stateManager.state.value.durationOption
                } else {
                    DurationOption.UNTIL_TURNED_OFF
                }
            )
            updateTileState(nextProfile)
            QuickSettingsTileSync.requestSyncAllTiles(applicationContext)
        }

        if (isLocked) {
            unlockAndRun(toggleAction)
        } else {
            toggleAction.run()
        }
    }

    private fun updateTileState(activeProfile: FocusProfile) {
        val tile = qsTile ?: return
        val isGamingActive = activeProfile == FocusProfile.GAMING

        tile.state = if (isGamingActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_gaming_label)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isGamingActive) {
                getString(R.string.tile_gaming_active_sub)
            } else {
                getString(R.string.profile_off)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = if (isGamingActive) {
                getString(R.string.tile_gaming_active_sub)
            } else {
                getString(R.string.tile_tap_to_activate)
            }
        }

        try {
            tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_qs_gaming_mode)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set Gaming Mode tile icon: ${e.message}")
        }

        tile.updateTile()
    }

    companion object {
        private const val TAG = "GamingModeTileService"

        fun computeToggledGamingProfile(currentProfile: FocusProfile): FocusProfile {
            return if (currentProfile == FocusProfile.GAMING) {
                FocusProfile.OFF
            } else {
                FocusProfile.GAMING
            }
        }

        fun requestTileSync(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    requestListeningState(
                        context.applicationContext,
                        ComponentName(context.applicationContext, GamingModeTileService::class.java)
                    )
                }
            }
        }
    }
}
