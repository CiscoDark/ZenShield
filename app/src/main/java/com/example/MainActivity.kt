package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.core.FocusStateManager
import com.example.ui.FocusDashboardScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.ZenShieldTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val stateManager = FocusStateManager.getInstance(applicationContext)

        setContent {
            val state by stateManager.state.collectAsState()
            var currentScreenIsSettings by remember { mutableStateOf(false) }

            ZenShieldTheme(
                activeProfile = state.activeProfile,
                preferences = state.themePreferences
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (currentScreenIsSettings) {
                        SettingsScreen(
                            stateManager = stateManager,
                            onNavigateBack = { currentScreenIsSettings = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        FocusDashboardScreen(
                            stateManager = stateManager,
                            onNavigateToSettings = { currentScreenIsSettings = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
