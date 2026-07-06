package com.quorvia.app.feature.explore

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.BusRouteResult
import com.amap.api.services.route.DrivePath
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.RideRouteResult
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.WalkPath
import com.amap.api.services.route.WalkRouteResult
import com.quorvia.app.feature.history.RouteHistoryRecord
import com.quorvia.app.settings.DeveloperSettings
import com.quorvia.app.ui.theme.QuorviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

@Composable
fun ExploreRoute(
    developerSettings: DeveloperSettings,
    preferences: ExplorePreferences,
    onPreferencesChange: (ExplorePreferences) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onRouteGenerated: (RouteHistoryRecord) -> Unit,
) {
    var uiState by remember {
        mutableStateOf(
            ExploreUiState(
                radiusMeters = preferences.radiusMeters,
                mapVisualMode = preferences.mapVisualMode,
            ),
        )
    }

    ExploreScreen(
        developerSettings = developerSettings,
        uiState = uiState,
        onStateChange = { updated ->
            uiState = updated
            onPreferencesChange(
                ExplorePreferences(
                    radiusMeters = updated.radiusMeters,
                    mapVisualMode = updated.mapVisualMode,
                ),
            )
        },
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
        onRouteGenerated = onRouteGenerated,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ExploreScreen(
    developerSettings: DeveloperSettings,
    uiState: ExploreUiState,
    onStateChange: (ExploreUiState) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onRouteGenerated: (RouteHistoryRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qrngClient = remember { QrngClient() }
    val mapView = rememberMapViewWithLifecycle()
    val mapRenderState = remember { MapRenderState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onStateChange(uiState.withStatus(ExploreStatus.Message("Location permission granted.")))
            mapView.enableCurrentLocationCursor()
            startSingleLocation(context, onStateChange, uiState, mapView)
        } else {
            onStateChange(uiState.withStatus(ExploreStatus.Error("Location permission is required.")))
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            mapView.enableCurrentLocationCursor()
            startSingleLocation(context, onStateChange, uiState, mapView)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    Box(modifier = modifier) {
        AMapPanel(
            mapView = mapView,
            renderState = mapRenderState,
            uiState = uiState,
            modifier = Modifier.fillMaxSize(),
        )

        TopOverlay(
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalIconButton(
                onClick = { startSingleLocation(context, onStateChange, uiState, mapView) },
            ) {
                Icon(Icons.Outlined.MyLocation, contentDescription = "Locate")
            }
            FilledTonalIconButton(onClick = onOpenHistory) {
                Icon(Icons.Outlined.History, contentDescription = "History")
            }
        }

        FloatingControlPanel(
            developerSettings = developerSettings,
            uiState = uiState,
            onRadiusChange = { onStateChange(uiState.withRadius(it)) },
            onMapVisualModeChange = { onStateChange(uiState.withMapVisualMode(it)) },
            onGenerateRoute = {
                generateRoute(
                    context = context,
                    scope = scope,
                    qrngClient = qrngClient,
                    developerSettings = developerSettings,
                    uiState = uiState,
                    onStateChange = onStateChange,
                    onRouteGenerated = onRouteGenerated,
                )
            },
            onOpenNavigation = {
                openGeneratedNavigation(context, uiState, onStateChange)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun AMapPanel(
    mapView: MapView,
    renderState: MapRenderState,
    uiState: ExploreUiState,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = {
            it.renderExploreOverlays(renderState, uiState)
        },
    )
}

@Composable
private fun TopOverlay(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Quorvia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
    }
}

@Composable
private fun FloatingControlPanel(
    developerSettings: DeveloperSettings,
    uiState: ExploreUiState,
    onRadiusChange: (Int) -> Unit,
    onMapVisualModeChange: (MapVisualMode) -> Unit,
    onGenerateRoute: () -> Unit,
    onOpenNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (developerSettings.isDebugRandom) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        text = "DEBUG RANDOM SOURCE",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${uiState.routeMode.label} · ${formatRadius(uiState.radiusMeters)} · ${uiState.mapVisualMode.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatusText(uiState.status)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        contentDescription = if (expanded) "Collapse controls" else "Expand controls",
                    )
                }
                Button(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    enabled = uiState.canGenerate,
                    onClick = onGenerateRoute,
                ) {
                    Text("Generate")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Exploration radius", style = MaterialTheme.typography.titleSmall)
                    val radiusIndex = SUPPORTED_RADIUS_METERS.indexOf(uiState.radiusMeters)
                        .coerceAtLeast(0)
                    Slider(
                        value = radiusIndex.toFloat(),
                        onValueChange = { index ->
                            val selectedIndex = index.roundToInt().coerceIn(SUPPORTED_RADIUS_METERS.indices)
                            onRadiusChange(SUPPORTED_RADIUS_METERS[selectedIndex])
                        },
                        valueRange = 0f..SUPPORTED_RADIUS_METERS.lastIndex.toFloat(),
                        steps = SUPPORTED_RADIUS_METERS.size - 2,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Route", style = MaterialTheme.typography.titleSmall)
                        Text(uiState.routeMode.label, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(1.dp).weight(1f))
                        Text(formatRadius(uiState.radiusMeters))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Map", style = MaterialTheme.typography.titleSmall)
                        FilterChip(
                            selected = uiState.mapVisualMode == MapVisualMode.Normal,
                            onClick = { onMapVisualModeChange(MapVisualMode.Normal) },
                            label = { Text("2D") },
                        )
                        FilterChip(
                            selected = uiState.mapVisualMode == MapVisualMode.Satellite,
                            onClick = { onMapVisualModeChange(MapVisualMode.Satellite) },
                            label = { Text("Satellite") },
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        enabled = uiState.targetPoint != null,
                        onClick = onOpenNavigation,
                    ) {
                        Text("Open AMap Navigation")
                    }
                }
            }
        }
    }
}

private fun generateRoute(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    qrngClient: QrngClient,
    developerSettings: DeveloperSettings,
    uiState: ExploreUiState,
    onStateChange: (ExploreUiState) -> Unit,
    onRouteGenerated: (RouteHistoryRecord) -> Unit,
) {
    val current = uiState.currentPoint
    if (current == null) {
        onStateChange(uiState.withStatus(ExploreStatus.Error("Waiting for current location.")))
        return
    }

    onStateChange(uiState.withStatus(ExploreStatus.Loading))
    scope.launch {
        val result = runCatching {
            val qrngResponse = withContext(Dispatchers.IO) {
                qrngClient.fetchUInt16(
                    length = 2,
                    provider = developerSettings.randomProvider,
                )
            }
            val target = generateTargetPoint(current, uiState.radiusMeters, qrngResponse.values)
            val routePoints = fetchRoutePoints(context, uiState.routeMode, current, target)
            Triple(qrngResponse, target, routePoints)
        }

        result
            .onSuccess { (qrngResponse, target, routePoints) ->
                onRouteGenerated(
                    RouteHistoryRecord(
                        id = "${System.currentTimeMillis()}-${target.latitude}-${target.longitude}",
                        createdAtMillis = System.currentTimeMillis(),
                        randomProvider = developerSettings.randomProvider,
                        randomSource = qrngResponse.source,
                        randomType = "uint16",
                        randomLength = 2,
                        randomValues = qrngResponse.values,
                        radiusMeters = uiState.radiusMeters,
                        routeMode = uiState.routeMode,
                        mapVisualMode = uiState.mapVisualMode,
                        originLatitude = current.latitude,
                        originLongitude = current.longitude,
                        targetLatitude = target.latitude,
                        targetLongitude = target.longitude,
                        routePointCount = routePoints.size,
                        routePoints = routePoints,
                    ),
                )
                onStateChange(uiState.withTargetRoute(target, routePoints))
            }
            .onFailure { error ->
                onStateChange(
                    uiState.withStatus(
                        ExploreStatus.Error(error.message ?: "Quantum random source unavailable."),
                    ),
                )
            }
    }
}

private fun openGeneratedNavigation(
    context: Context,
    uiState: ExploreUiState,
    onStateChange: (ExploreUiState) -> Unit,
) {
    val current = uiState.currentPoint
    val target = uiState.targetPoint
    if (current == null || target == null) {
        onStateChange(uiState.withStatus(ExploreStatus.Error("Generate a route first.")))
        return
    }
    val opened = openAmapNavigation(context, uiState.routeMode, current, target)
    if (!opened) {
        onStateChange(uiState.withStatus(ExploreStatus.Error("AMap app is not installed.")))
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

@Composable
private fun StatusText(status: ExploreStatus) {
    val text = when (status) {
        ExploreStatus.Idle -> "Waiting for location."
        ExploreStatus.Loading -> "Requesting quantum entropy."
        is ExploreStatus.Message -> status.text
        is ExploreStatus.Error -> status.text
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

private fun formatRadius(radiusMeters: Int): String =
    if (radiusMeters < 1_000) {
        "${radiusMeters} m"
    } else {
        "${radiusMeters / 1_000} km"
    }

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
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

private fun startSingleLocation(
    context: android.content.Context,
    onStateChange: (ExploreUiState) -> Unit,
    state: ExploreUiState,
    mapView: MapView,
) {
    val client = AMapLocationClient(context.applicationContext)
    val option = AMapLocationClientOption().apply {
        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        isOnceLocation = true
        isNeedAddress = false
    }
    client.setLocationOption(option)
    client.setLocationListener { location ->
        if (location != null && location.errorCode == 0) {
            val point = ExplorePoint(location.latitude, location.longitude)
            onStateChange(state.withCurrentPoint(point).withStatus(ExploreStatus.Message("Location ready.")))
        } else {
            val message = location?.errorInfo ?: "Location failed."
            onStateChange(state.withStatus(ExploreStatus.Error(message)))
        }
        client.stopLocation()
        client.onDestroy()
    }
    client.startLocation()
}

private fun MapView.enableCurrentLocationCursor() {
    map.myLocationStyle = MyLocationStyle()
        .myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
        .myLocationIcon(MapMarkerHelper.getUserLocationMarker(context))
        .showMyLocation(true)
    map.isMyLocationEnabled = true
}

private class MapRenderState {
    var radiusCircle: Circle? = null
    var targetMarker: Marker? = null
    var routePolyline: Polyline? = null
    var lastViewportKey: String? = null
}

private fun MapView.renderExploreOverlays(renderState: MapRenderState, state: ExploreUiState) {
    map.mapType = state.mapVisualMode.toAmapMapType()
    renderState.radiusCircle?.remove()
    renderState.targetMarker?.remove()
    renderState.routePolyline?.remove()

    state.currentPoint?.let { origin ->
        renderState.radiusCircle = map.addCircle(
            CircleOptions()
                .center(origin.toLatLng())
                .radius(state.radiusMeters.toDouble())
                .strokeColor(0xFF2367F4.toInt())
                .fillColor(0x222367F4)
                .strokeWidth(4f),
        )
    }

    state.targetPoint?.let { target ->
        renderState.targetMarker = map.addMarker(
            MarkerOptions()
                .position(target.toLatLng())
                .title("Quantum target")
                .snippet("Generated from ANU/AQN entropy")
                .icon(MapMarkerHelper.getQuantumTargetMarker(context))
                .anchor(0.5f, 0.5f),
        )
    }

    if (state.routePoints.size >= 2) {
        renderState.routePolyline = map.addPolyline(
            PolylineOptions()
                .addAll(state.routePoints.map { it.toLatLng() })
                .color(0xFF2367F4.toInt())
                .width(8f),
        )
    }

    fitViewportIfNeeded(renderState, state)
}

private fun ExplorePoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun MapVisualMode.toAmapMapType(): Int =
    when (this) {
        MapVisualMode.Normal -> com.amap.api.maps.AMap.MAP_TYPE_NORMAL
        MapVisualMode.Satellite -> com.amap.api.maps.AMap.MAP_TYPE_SATELLITE
    }

private fun ExplorePoint.toLatLonPoint(): LatLonPoint = LatLonPoint(latitude, longitude)

private fun LatLonPoint.toExplorePoint(): ExplorePoint = ExplorePoint(latitude, longitude)

private fun MapView.fitViewportIfNeeded(renderState: MapRenderState, state: ExploreUiState) {
    val current = state.currentPoint ?: return
    val key = buildString {
        append(current.latitude).append(':').append(current.longitude)
        append('|').append(state.radiusMeters)
        append('|').append(state.targetPoint?.latitude).append(':').append(state.targetPoint?.longitude)
        append('|').append(state.routePoints.size)
    }
    if (renderState.lastViewportKey == key) {
        return
    }
    renderState.lastViewportKey = key

    val points = buildList {
        addAll(radiusBoundsPoints(current, state.radiusMeters))
        state.targetPoint?.let { add(it) }
        addAll(state.routePoints)
    }
    val bounds = LatLngBounds.builder().apply {
        points.forEach { include(it.toLatLng()) }
    }.build()
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
}

private fun radiusBoundsPoints(center: ExplorePoint, radiusMeters: Int): List<ExplorePoint> =
    listOf(
        generateTargetPoint(center, radiusMeters, listOf(0, 65_535)),
        generateTargetPoint(center, radiusMeters, listOf(16_384, 65_535)),
        generateTargetPoint(center, radiusMeters, listOf(32_768, 65_535)),
        generateTargetPoint(center, radiusMeters, listOf(49_152, 65_535)),
    )

private suspend fun fetchRoutePoints(
    context: android.content.Context,
    routeMode: RouteMode,
    origin: ExplorePoint,
    target: ExplorePoint,
): List<ExplorePoint> = suspendCancellableCoroutine { continuation ->
    val routeSearch = RouteSearch(context.applicationContext)
    val fromAndTo = RouteSearch.FromAndTo(origin.toLatLonPoint(), target.toLatLonPoint())

    routeSearch.setRouteSearchListener(
        object : RouteSearch.OnRouteSearchListener {
            override fun onDriveRouteSearched(result: DriveRouteResult?, errorCode: Int) {
                if (routeMode != RouteMode.Drive || !continuation.isActive) return
                val routePoints = result?.paths?.firstOrNull()?.toRoutePoints()
                if (errorCode == AMAP_SUCCESS_CODE && !routePoints.isNullOrEmpty()) {
                    continuation.resume(routePoints)
                } else {
                    continuation.resumeWithException(IllegalStateException("Drive route unavailable."))
                }
            }

            override fun onWalkRouteSearched(result: WalkRouteResult?, errorCode: Int) {
                if (routeMode != RouteMode.Walk || !continuation.isActive) return
                val routePoints = result?.paths?.firstOrNull()?.toRoutePoints()
                if (errorCode == AMAP_SUCCESS_CODE && !routePoints.isNullOrEmpty()) {
                    continuation.resume(routePoints)
                } else {
                    continuation.resumeWithException(IllegalStateException("Walk route unavailable."))
                }
            }

            override fun onBusRouteSearched(result: BusRouteResult?, errorCode: Int) = Unit

            override fun onRideRouteSearched(result: RideRouteResult?, errorCode: Int) {
                if (routeMode != RouteMode.Ride || !continuation.isActive) return
                val routePoints = result?.paths?.firstOrNull()?.toRoutePoints()
                if (errorCode == AMAP_SUCCESS_CODE && !routePoints.isNullOrEmpty()) {
                    continuation.resume(routePoints)
                } else {
                    continuation.resumeWithException(IllegalStateException("Ride route unavailable."))
                }
            }
        },
    )

    when (routeMode) {
        RouteMode.Walk -> {
            val query = RouteSearch.WalkRouteQuery(fromAndTo, RouteSearch.WalkDefault)
            routeSearch.calculateWalkRouteAsyn(query)
        }

        RouteMode.Ride -> {
            val query = RouteSearch.RideRouteQuery(fromAndTo)
            routeSearch.calculateRideRouteAsyn(query)
        }

        RouteMode.Drive -> {
            val query = RouteSearch.DriveRouteQuery(fromAndTo, RouteSearch.DrivingDefault, null, null, "")
            routeSearch.calculateDriveRouteAsyn(query)
        }
    }
}

private fun WalkPath.toRoutePoints(): List<ExplorePoint> =
    steps.flatMap { step -> step.polyline.map { it.toExplorePoint() } }

private fun DrivePath.toRoutePoints(): List<ExplorePoint> =
    steps.flatMap { step -> step.polyline.map { it.toExplorePoint() } }

private fun com.amap.api.services.route.RidePath.toRoutePoints(): List<ExplorePoint> =
    steps.flatMap { step -> step.polyline.map { it.toExplorePoint() } }

private const val AMAP_SUCCESS_CODE = 1000

private fun openAmapNavigation(
    context: Context,
    routeMode: RouteMode,
    origin: ExplorePoint,
    target: ExplorePoint,
): Boolean {
    val mode = when (routeMode) {
        RouteMode.Walk -> "2"
        RouteMode.Ride -> "3"
        RouteMode.Drive -> "0"
    }
    val uri = Uri.Builder()
        .scheme("amapuri")
        .authority("route")
        .path("plan")
        .appendQueryParameter("sourceApplication", "Quorvia")
        .appendQueryParameter("slat", origin.latitude.toString())
        .appendQueryParameter("slon", origin.longitude.toString())
        .appendQueryParameter("sname", "Current location")
        .appendQueryParameter("dlat", target.latitude.toString())
        .appendQueryParameter("dlon", target.longitude.toString())
        .appendQueryParameter("dname", "Quantum target")
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

@Preview
@Composable
private fun ExploreRoutePreview() {
    QuorviaTheme {
        FloatingControlPanel(
            developerSettings = DeveloperSettings(),
            uiState = ExploreUiState(
                currentPoint = ExplorePoint(39.9087, 116.3975),
                status = ExploreStatus.Message("Location ready."),
            ),
            onRadiusChange = {},
            onMapVisualModeChange = {},
            onGenerateRoute = {},
            onOpenNavigation = {},
        )
    }
}
