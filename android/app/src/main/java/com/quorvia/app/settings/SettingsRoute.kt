package com.quorvia.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quorvia.app.BuildConfig
import com.quorvia.app.feature.explore.ExplorePreferences
import com.quorvia.app.feature.explore.TargetGenerationMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    settings: DeveloperSettings,
    explorePreferences: ExplorePreferences,
    onSettingsChange: (DeveloperSettings) -> Unit,
    onExplorePreferencesChange: (ExplorePreferences) -> Unit,
    onBack: () -> Unit,
) {
    var aboutTapCount by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            aboutTapCount += 1
                            if (aboutTapCount >= DEVELOPER_TAP_COUNT) {
                                onSettingsChange(settings.copy(developerModeEnabled = true))
                            }
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("About Quorvia", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (settings.developerModeEnabled) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Developer mode", style = MaterialTheme.typography.titleSmall)
                        Text("Random source", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.randomProvider == RandomProvider.Quantum,
                                onClick = {
                                    onSettingsChange(settings.copy(randomProvider = RandomProvider.Quantum))
                                },
                                label = { Text("Quantum") },
                            )
                            FilterChip(
                                selected = settings.randomProvider == RandomProvider.Debug,
                                onClick = {
                                    onSettingsChange(settings.copy(randomProvider = RandomProvider.Debug))
                                },
                                label = { Text("Debug") },
                            )
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Generation quality", style = MaterialTheme.typography.titleSmall)
                    Text("Standard uses 1024 sample points. Fine uses 2048 sample points.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = explorePreferences.generationMode == TargetGenerationMode.Standard,
                            onClick = {
                                onExplorePreferencesChange(
                                    explorePreferences.copy(generationMode = TargetGenerationMode.Standard),
                                )
                            },
                            label = { Text("Standard") },
                        )
                        FilterChip(
                            selected = explorePreferences.generationMode == TargetGenerationMode.Fine,
                            onClick = {
                                onExplorePreferencesChange(
                                    explorePreferences.copy(generationMode = TargetGenerationMode.Fine),
                                )
                            },
                            label = { Text("Fine") },
                        )
                    }
                }
            }

            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

private const val DEVELOPER_TAP_COUNT = 7
