package com.example.service

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
        val currentState = stateManager.state.value

        // Quick toggle: switch between OFF and DO_NOT_DISTURB
        val targetProfile = if (currentState.activeProfile == FocusProfile.OFF) {
            FocusProfile.DO_NOT_DISTURB
        } else {
            FocusProfile.OFF
        }

        stateManager.setProfile(targetProfile, DurationOption.UNTIL_TURNED_OFF)
        updateTileState(targetProfile)
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
                tile.subtitle = "Shielded"
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
