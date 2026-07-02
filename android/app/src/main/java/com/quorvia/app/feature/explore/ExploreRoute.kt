package com.quorvia.app.feature.explore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.quorvia.app.ui.theme.QuorviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

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
            onStateChange = { uiState = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun ExploreScreen(
    uiState: ExploreUiState,
    onStateChange: (ExploreUiState) -> Unit,
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

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AMapPanel(
            mapView = mapView,
            renderState = mapRenderState,
            uiState = uiState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        ControlPanel(
            uiState = uiState,
            onRadiusChange = { onStateChange(uiState.withRadius(it)) },
            onRouteModeChange = { onStateChange(uiState.withRouteMode(it)) },
            onLocate = {
                startSingleLocation(context, onStateChange, uiState, mapView)
            },
            onGenerateRoute = {
                val current = uiState.currentPoint
                if (current == null) {
                    onStateChange(uiState.withStatus(ExploreStatus.Error("Waiting for current location.")))
                    return@ControlPanel
                }

                onStateChange(uiState.withStatus(ExploreStatus.Loading))
                scope.launch {
                    val result = runCatching {
                        val randomValues = withContext(Dispatchers.IO) {
                            qrngClient.fetchUInt16(length = 2)
                        }
                        val target = generateTargetPoint(current, uiState.radiusMeters, randomValues)
                        val routePoints = fetchRoutePoints(context, uiState.routeMode, current, target)
                        target to routePoints
                    }

                    result
                        .onSuccess { (target, routePoints) ->
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
            },
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
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = {
                it.renderExploreOverlays(renderState, uiState)
            },
        )
    }
}

@Composable
private fun ControlPanel(
    uiState: ExploreUiState,
    onRadiusChange: (Int) -> Unit,
    onRouteModeChange: (RouteMode) -> Unit,
    onLocate: () -> Unit,
    onGenerateRoute: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                Text(formatRadius(uiState.radiusMeters))
            }
            StatusText(uiState.status)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    onClick = onLocate,
                ) {
                    Text("Locate")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    enabled = uiState.canGenerate,
                    onClick = onGenerateRoute,
                ) {
                    Text("Generate")
                }
            }
        }
    }
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
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
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

            override fun onRideRouteSearched(result: RideRouteResult?, errorCode: Int) = Unit
        },
    )

    when (routeMode) {
        RouteMode.Walk -> {
            val query = RouteSearch.WalkRouteQuery(fromAndTo, RouteSearch.WalkDefault)
            routeSearch.calculateWalkRouteAsyn(query)
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

private const val AMAP_SUCCESS_CODE = 1000

@Preview
@Composable
private fun ExploreRoutePreview() {
    QuorviaTheme {
        ControlPanel(
            uiState = ExploreUiState(
                currentPoint = ExplorePoint(39.9087, 116.3975),
                status = ExploreStatus.Message("Location ready."),
            ),
            onRadiusChange = {},
            onRouteModeChange = {},
            onLocate = {},
            onGenerateRoute = {},
        )
    }
}
