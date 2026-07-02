package com.quorvia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quorvia.app.feature.explore.ExploreRoute
import com.quorvia.app.settings.DeveloperSettingsStore
import com.quorvia.app.settings.SettingsRoute
import com.quorvia.app.ui.theme.QuorviaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { DeveloperSettingsStore(this) }
            var settings by remember { mutableStateOf(settingsStore.load()) }
            var screen by remember { mutableStateOf(AppScreen.Explore) }

            QuorviaTheme {
                when (screen) {
                    AppScreen.Explore -> ExploreRoute(
                        developerSettings = settings,
                        onOpenSettings = { screen = AppScreen.Settings },
                    )
                    AppScreen.Settings -> SettingsRoute(
                        settings = settings,
                        onSettingsChange = { updated ->
                            settings = updated
                            settingsStore.save(updated)
                        },
                        onBack = { screen = AppScreen.Explore },
                    )
                }
            }
        }
    }
}

private enum class AppScreen {
    Explore,
    Settings,
}
