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
 * to toggle Sleep Mode directly without opening the Sentinal app.
 */
class SleepModeTileService : TileService() {

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
            val nextProfile = computeToggledSleepProfile(stateManager.getActiveProfile())
            ExpressiveHaptics.playSpringTap(applicationContext)
            stateManager.setProfile(
                profile = nextProfile,
                duration = if (nextProfile == FocusProfile.SLEEP) {
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
        val isSleepActive = activeProfile == FocusProfile.SLEEP

        tile.state = if (isSleepActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_sleep_label)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isSleepActive) {
                getString(R.string.tile_sleep_active_sub)
            } else {
                getString(R.string.profile_off)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = if (isSleepActive) {
                getString(R.string.tile_sleep_active_sub)
            } else {
                getString(R.string.tile_tap_to_activate)
            }
        }

        try {
            tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_qs_sleep_mode)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set Sleep Mode tile icon: ${e.message}")
        }

        tile.updateTile()
    }

    companion object {
        private const val TAG = "SleepModeTileService"

        fun computeToggledSleepProfile(currentProfile: FocusProfile): FocusProfile {
            return if (currentProfile == FocusProfile.SLEEP) {
                FocusProfile.OFF
            } else {
                FocusProfile.SLEEP
            }
        }

        fun requestTileSync(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    requestListeningState(
                        context.applicationContext,
                        ComponentName(context.applicationContext, SleepModeTileService::class.java)
                    )
                }
            }
        }
    }
}
