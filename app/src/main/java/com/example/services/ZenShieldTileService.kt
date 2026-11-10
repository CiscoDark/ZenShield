package com.example.services

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.R
import com.example.core.DurationOption
import com.example.core.FocusProfile
import com.example.core.FocusStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Synchronizes all Sentinal Quick Settings tiles (All-Mode Cycle, Gaming Mode, and Sleep Mode)
 * whenever the active Focus profile changes from anywhere in the app or background services.
 */
object QuickSettingsTileSync {
    fun requestSyncAllTiles(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val appCtx = context.applicationContext
            runCatching {
                TileService.requestListeningState(
                    appCtx,
                    ComponentName(appCtx, ZenShieldTileService::class.java)
                )
            }
            GamingModeTileService.requestTileSync(appCtx)
            SleepModeTileService.requestTileSync(appCtx)
        }
    }
}

class ZenShieldTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observerJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val stateManager = FocusStateManager.getInstance(applicationContext)

        observerJob?.cancel()
        observerJob = scope.launch {
            stateManager.state.collect { state ->
                updateTileState(state.activeProfile)
            }
        }
    }

    override fun onStopListening() {
        observerJob?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val stateManager = FocusStateManager.getInstance(applicationContext)
        val currentProfile = stateManager.getActiveProfile()

        // Cycle through: OFF -> DO_NOT_DISTURB -> GAMING -> WORK -> SLEEP -> OFF
        val nextProfile = when (currentProfile) {
            FocusProfile.OFF -> FocusProfile.DO_NOT_DISTURB
            FocusProfile.DO_NOT_DISTURB -> FocusProfile.GAMING
            FocusProfile.GAMING -> FocusProfile.WORK
            FocusProfile.WORK -> FocusProfile.SLEEP
            FocusProfile.SLEEP -> FocusProfile.OFF
            FocusProfile.CINEMA -> FocusProfile.OFF
        }

        stateManager.setProfile(nextProfile, DurationOption.UNTIL_TURNED_OFF)
        updateTileState(nextProfile)
        QuickSettingsTileSync.requestSyncAllTiles(applicationContext)
    }

    private fun updateTileState(profile: FocusProfile) {
        val tile = qsTile ?: return

        if (profile == FocusProfile.OFF) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.profile_off)
            }
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = profile.displayName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Shield Active"
            }
        }

        try {
            tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_launcher_monochrome)
        } catch (_: Exception) {
            // Keep default icon if loading fails
        }

        tile.updateTile()
    }
}
