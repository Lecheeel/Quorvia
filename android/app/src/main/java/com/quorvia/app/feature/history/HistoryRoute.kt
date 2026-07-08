package com.quorvia.app.feature.history

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.quorvia.app.feature.explore.ExplorePoint
import com.quorvia.app.feature.explore.MapVisualMode
import com.quorvia.app.feature.explore.RouteMode
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(
    records: List<RouteHistoryRecord>,
    selectedRecord: RouteHistoryRecord?,
    onOpenRecord: (RouteHistoryRecord) -> Unit,
    onCloseRecord: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    if (selectedRecord != null) {
        HistoryDetailRoute(
            record = selectedRecord,
            onBack = onCloseRecord,
        )
        return
    }

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
                    HistoryRecordCard(
                        record = record,
                        onClick = { onOpenRecord(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: RouteHistoryRecord,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                "${record.targetType.label} · ${formatRadius(record.radiusMeters)} · ${record.randomProvider.name}",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetailRoute(
    record: RouteHistoryRecord,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mapView = rememberHistoryMapView()
    var originAddress by remember(record.id) { mutableStateOf("Loading address...") }
    var targetAddress by remember(record.id) { mutableStateOf("Loading address...") }

    LaunchedEffect(record.id) {
        originAddress = reverseGeocode(
            context = context,
            point = ExplorePoint(record.originLatitude, record.originLongitude),
        )
        targetAddress = reverseGeocode(
            context = context,
            point = ExplorePoint(record.targetLatitude, record.targetLongitude),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route details", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    factory = { mapView },
                    update = { it.renderHistoryRoute(record) },
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { openAmapNavigation(context, record) },
            ) {
                Text("Open AMap Navigation")
            }

            DetailSection("Overview") {
                DetailRow("Generated", formatTime(record.createdAtMillis))
                DetailRow("Route", record.routeMode.label)
                DetailRow("Target type", record.targetType.label)
                DetailRow("Generation", record.generationMode.label)
                DetailRow("Radius", formatRadius(record.radiusMeters))
                DetailRow("Map", record.mapVisualMode.label)
                record.intent?.let { DetailRow("Intent", it) }
                DetailRow("Straight distance", formatDistance(record.straightDistanceMeters))
                DetailRow("Route points", record.routePointCount.toString())
            }

            DetailSection("Random") {
                DetailRow("Provider", record.randomProvider.name)
                DetailRow("Source", record.randomSource)
                DetailRow("Type", record.randomType)
                DetailRow("Random values", record.randomLength.toString())
                DetailRow("Sample points", record.samplePointCount.toString())
                record.densityScore?.let { DetailRow("Density score", "%.2f".format(it)) }
                DetailRow(
                    "Values",
                    if (record.randomValues.isEmpty()) "Not stored" else record.randomValues.joinToString(),
                )
            }

            DetailSection("Origin") {
                DetailRow("Address", originAddress)
                DetailRow("Latitude", formatCoordinate(record.originLatitude))
                DetailRow("Longitude", formatCoordinate(record.originLongitude))
            }

            DetailSection("Target") {
                DetailRow("Address", targetAddress)
                DetailRow("Latitude", formatCoordinate(record.targetLatitude))
                DetailRow("Longitude", formatCoordinate(record.targetLongitude))
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun rememberHistoryMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        object : MapView(context) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            onCreate(Bundle())
            map.uiSettings.isMyLocationButtonEnabled = false
            map.uiSettings.isZoomControlsEnabled = false
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    return mapView
}

private class HistoryMapRenderState {
    var originMarker: Marker? = null
    var targetMarker: Marker? = null
    var routePolyline: Polyline? = null
    var lastKey: String? = null
}

private val historyMapRenderStates = java.util.WeakHashMap<MapView, HistoryMapRenderState>()

private fun MapView.renderHistoryRoute(record: RouteHistoryRecord) {
    map.mapType = record.mapVisualMode.toAmapMapType()
    val renderState = historyMapRenderStates.getOrPut(this) { HistoryMapRenderState() }
    val key = record.id
    if (renderState.lastKey == key) return
    renderState.lastKey = key

    renderState.originMarker?.remove()
    renderState.targetMarker?.remove()
    renderState.routePolyline?.remove()

    val origin = LatLng(record.originLatitude, record.originLongitude)
    val target = LatLng(record.targetLatitude, record.targetLongitude)
    val points = record.routePoints.map { it.toLatLng() }.ifEmpty { listOf(origin, target) }

    renderState.originMarker = map.addMarker(
        MarkerOptions()
            .position(origin)
            .title("Origin")
            .icon(com.quorvia.app.feature.explore.MapMarkerHelper.getOriginMarker(context))
            .anchor(0.5f, 0.5f),
    )
    renderState.targetMarker = map.addMarker(
        MarkerOptions()
            .position(target)
            .title("Target")
            .icon(com.quorvia.app.feature.explore.MapMarkerHelper.getQuantumTargetMarker(context))
            .anchor(0.5f, 0.5f),
    )
    if (points.size >= 2) {
        renderState.routePolyline = map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(0xFF00C853.toInt()) // 与探索界面一致的亮绿色导航路线
                .width(8f),
        )
    }

    val bounds = LatLngBounds.builder().apply {
        points.forEach { include(it) }
        include(origin)
        include(target)
    }.build()
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
}

private suspend fun reverseGeocode(context: Context, point: ExplorePoint): String =
    suspendCancellableCoroutine { continuation ->
        val search = GeocodeSearch(context.applicationContext)
        search.setOnGeocodeSearchListener(
            object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    if (!continuation.isActive) return
                    val address = result?.regeocodeAddress?.formatAddress
                    continuation.resume(
                        if (rCode == AMAP_SUCCESS_CODE && !address.isNullOrBlank()) {
                            address
                        } else {
                            "Address unavailable"
                        },
                    )
                }

                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) = Unit
            },
        )
        search.getFromLocationAsyn(
            RegeocodeQuery(
                LatLonPoint(point.latitude, point.longitude),
                REVERSE_GEOCODE_RADIUS_METERS,
                GeocodeSearch.AMAP,
            ),
        )
    }

private val RouteHistoryRecord.straightDistanceMeters: Double
    get() = haversineMeters(
        originLatitude,
        originLongitude,
        targetLatitude,
        targetLongitude,
    )

private fun haversineMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val radiusMeters = 6_371_000.0
    val startLat = Math.toRadians(startLatitude)
    val endLat = Math.toRadians(endLatitude)
    val latDelta = Math.toRadians(endLatitude - startLatitude)
    val lonDelta = Math.toRadians(endLongitude - startLongitude)
    val a = sin(latDelta / 2) * sin(latDelta / 2) +
        cos(startLat) * cos(endLat) * sin(lonDelta / 2) * sin(lonDelta / 2)
    return radiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun openAmapNavigation(context: Context, record: RouteHistoryRecord): Boolean {
    val mode = when (record.routeMode) {
        RouteMode.Walk -> "2"
        RouteMode.Ride -> "3"
        RouteMode.Drive -> "0"
    }
    val uri = Uri.Builder()
        .scheme("amapuri")
        .authority("route")
        .path("plan")
        .appendQueryParameter("sourceApplication", "Quorvia")
        .appendQueryParameter("slat", record.originLatitude.toString())
        .appendQueryParameter("slon", record.originLongitude.toString())
        .appendQueryParameter("sname", "History origin")
        .appendQueryParameter("dlat", record.targetLatitude.toString())
        .appendQueryParameter("dlon", record.targetLongitude.toString())
        .appendQueryParameter("dname", "History target")
        .appendQueryParameter("dev", "0")
        .appendQueryParameter("t", mode)
        .build()
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
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

private fun MapVisualMode.toAmapMapType(): Int =
    when (this) {
        MapVisualMode.Normal -> com.amap.api.maps.AMap.MAP_TYPE_NORMAL
        MapVisualMode.Satellite -> com.amap.api.maps.AMap.MAP_TYPE_SATELLITE
    }

private fun ExplorePoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun formatRadius(radiusMeters: Int): String =
    if (radiusMeters < 1_000) {
        "${radiusMeters} m"
    } else {
        "${radiusMeters / 1_000} km"
    }

private fun formatDistance(distanceMeters: Double): String =
    if (distanceMeters < 1_000) {
        "${distanceMeters.roundToInt()} m"
    } else {
        "%.2f km".format(distanceMeters / 1_000)
    }

private fun formatCoordinate(value: Double): String =
    "%.5f".format(value)

private fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

private const val AMAP_SUCCESS_CODE = 1000
private const val REVERSE_GEOCODE_RADIUS_METERS = 100f
