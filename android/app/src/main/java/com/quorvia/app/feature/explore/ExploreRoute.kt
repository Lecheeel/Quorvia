package com.quorvia.app.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quorvia.app.ui.theme.QuorviaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreRoute() {
    var uiState by remember { mutableStateOf(ExploreUiState()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quorvia", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        ExploreScreen(
            uiState = uiState,
            onRadiusChange = { uiState = uiState.withRadius(it) },
            onRouteModeChange = { uiState = uiState.withRouteMode(it) },
            onGenerateRoute = {},
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun ExploreScreen(
    uiState: ExploreUiState,
    onRadiusChange: (Int) -> Unit,
    onRouteModeChange: (RouteMode) -> Unit,
    onGenerateRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp)),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Map canvas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "AMap SDK integration will render the exploration area here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Exploration radius", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = uiState.radiusKm.toFloat(),
                    onValueChange = { onRadiusChange(it.toInt()) },
                    valueRange = MIN_RADIUS_KM.toFloat()..MAX_RADIUS_KM.toFloat(),
                    steps = 8,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = uiState.routeMode == RouteMode.Walk,
                        onClick = { onRouteModeChange(RouteMode.Walk) },
                        label = { Text("Walk") },
                    )
                    FilterChip(
                        selected = uiState.routeMode == RouteMode.Drive,
                        onClick = { onRouteModeChange(RouteMode.Drive) },
                        label = { Text("Drive") },
                    )
                    Spacer(Modifier.size(1.dp).weight(1f))
                    Text("${uiState.radiusKm} km")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    enabled = uiState.canGenerate,
                    onClick = onGenerateRoute,
                ) {
                    Text("Generate quantum route")
                }
            }
        }
    }
}

@Preview
@Composable
private fun ExploreRoutePreview() {
    QuorviaTheme {
        ExploreRoute()
    }
}
