package com.quorvia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quorvia.app.feature.explore.ExplorePreferences
import com.quorvia.app.feature.explore.ExplorePreferencesStore
import com.quorvia.app.feature.explore.ExploreRoute
import com.quorvia.app.feature.history.HistoryRoute
import com.quorvia.app.feature.history.RouteHistoryRecord
import com.quorvia.app.feature.history.RouteHistoryStore
import com.quorvia.app.settings.DeveloperSettingsStore
import com.quorvia.app.settings.SettingsRoute
import com.quorvia.app.ui.theme.QuorviaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { DeveloperSettingsStore(this) }
            val historyStore = remember { RouteHistoryStore(this) }
            val explorePreferencesStore = remember { ExplorePreferencesStore(this) }
            var settings by remember { mutableStateOf(settingsStore.load()) }
            var historyRecords by remember { mutableStateOf(historyStore.load()) }
            var explorePreferences by remember { mutableStateOf(explorePreferencesStore.load()) }
            var selectedHistoryRecord by remember { mutableStateOf<RouteHistoryRecord?>(null) }
            var screen by remember { mutableStateOf(AppScreen.Explore) }

            QuorviaTheme {
                BackHandler(enabled = screen != AppScreen.Explore) {
                    if (screen == AppScreen.History && selectedHistoryRecord != null) {
                        selectedHistoryRecord = null
                    } else {
                        selectedHistoryRecord = null
                        screen = AppScreen.Explore
                    }
                }

                when (screen) {
                    AppScreen.Explore -> ExploreRoute(
                        developerSettings = settings,
                        preferences = explorePreferences,
                        onPreferencesChange = { updated ->
                            explorePreferences = updated
                            explorePreferencesStore.save(updated)
                        },
                        onOpenSettings = { screen = AppScreen.Settings },
                        onOpenHistory = {
                            historyRecords = historyStore.load()
                            screen = AppScreen.History
                        },
                        onRouteGenerated = { record ->
                            historyRecords = historyStore.add(record)
                        },
                    )
                    AppScreen.History -> HistoryRoute(
                        records = historyRecords,
                        selectedRecord = selectedHistoryRecord,
                        onOpenRecord = { record ->
                            selectedHistoryRecord = record
                        },
                        onCloseRecord = {
                            selectedHistoryRecord = null
                        },
                        onClear = {
                            historyRecords = historyStore.clear()
                            selectedHistoryRecord = null
                        },
                        onBack = {
                            selectedHistoryRecord = null
                            screen = AppScreen.Explore
                        },
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
    History,
    Settings,
}
