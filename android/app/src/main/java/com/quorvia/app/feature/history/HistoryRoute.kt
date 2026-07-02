package com.quorvia.app.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quorvia.app.feature.explore.MapVisualMode
import com.quorvia.app.feature.explore.RouteMode
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(
    records: List<RouteHistoryRecord>,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = records.isNotEmpty(),
                        onClick = onClear,
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Clear history")
                    }
                },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No routes yet.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Generated routes will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryRecordCard(record = record)
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(record: RouteHistoryRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(record.routeMode.label, style = MaterialTheme.typography.titleSmall)
                Text(formatTime(record.createdAtMillis), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${formatRadius(record.radiusMeters)} · ${record.mapVisualMode.label} · ${record.randomProvider.name}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Target ${formatCoordinate(record.targetLatitude)}, ${formatCoordinate(record.targetLongitude)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Route points ${record.routePointCount} · Source ${record.randomSource}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val RouteMode.label: String
    get() = when (this) {
        RouteMode.Walk -> "Walk"
        RouteMode.Ride -> "Ride"
        RouteMode.Drive -> "Drive"
    }

private val MapVisualMode.label: String
    get() = when (this) {
        MapVisualMode.Normal -> "2D"
        MapVisualMode.Satellite -> "Satellite"
    }

private fun formatRadius(radiusMeters: Int): String =
    if (radiusMeters < 1_000) {
        "${radiusMeters} m"
    } else {
        "${radiusMeters / 1_000} km"
    }

private fun formatCoordinate(value: Double): String =
    "%.5f".format(value)

private fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
