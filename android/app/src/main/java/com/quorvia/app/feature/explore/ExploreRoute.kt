package com.quorvia.app.feature.explore

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
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
import com.amap.api.maps.model.GroundOverlay
import com.amap.api.maps.model.GroundOverlayOptions
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
import com.quorvia.app.settings.HeatmapDeveloperSettings
import com.quorvia.app.ui.theme.QuorviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
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
                    generationMode = preferences.generationMode,
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
                    generationMode = updated.generationMode,
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
    var showGenerateDialog by remember { mutableStateOf(false) }

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
            developerSettings = developerSettings,
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
                onClick = {
                    onStateChange(
                        uiState.withMapVisualMode(
                            when (uiState.mapVisualMode) {
                                MapVisualMode.Normal -> MapVisualMode.Satellite
                                MapVisualMode.Satellite -> MapVisualMode.Normal
                            },
                        ),
                    )
                },
            ) {
                Icon(Icons.Outlined.Layers, contentDescription = "Switch map layer")
            }
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
            onGenerateRoute = {
                showGenerateDialog = true
            },
            onOpenNavigation = {
                openGeneratedNavigation(context, uiState, onStateChange)
            },
            onCancelTarget = {
                onStateChange(uiState.withoutTargetRoute())
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )

        if (showGenerateDialog) {
            GenerateTargetDialog(
                generationMode = uiState.generationMode,
                onDismiss = { showGenerateDialog = false },
                onGenerate = { targetType, intent ->
                    showGenerateDialog = false
                    generateRoute(
                        context = context,
                        scope = scope,
                        qrngClient = qrngClient,
                        developerSettings = developerSettings,
                        uiState = uiState,
                        targetType = targetType,
                        intent = intent,
                        onStateChange = onStateChange,
                        onRouteGenerated = onRouteGenerated,
                    )
                },
            )
        }
    }
}

@Composable
private fun AMapPanel(
    mapView: MapView,
    renderState: MapRenderState,
    developerSettings: DeveloperSettings,
    uiState: ExploreUiState,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = {
            it.renderExploreOverlays(renderState, uiState, developerSettings.heatmap)
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
private fun GenerateTargetDialog(
    generationMode: TargetGenerationMode,
    onDismiss: () -> Unit,
    onGenerate: (ExplorationTargetType, String?) -> Unit,
) {
    var selectedType by remember { mutableStateOf(ExplorationTargetType.Attractor) }
    var intentText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${generationMode.label}: ${generationMode.randomValueCount} random values / ${generationMode.samplePointCount} sample points",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplorationTargetType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = intentText,
                    onValueChange = { intentText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    label = { Text("Intent (optional)") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onGenerate(selectedType, intentText.trim().takeIf { it.isNotBlank() })
                },
            ) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun FloatingControlPanel(
    developerSettings: DeveloperSettings,
    uiState: ExploreUiState,
    onRadiusChange: (Int) -> Unit,
    onGenerateRoute: () -> Unit,
    onOpenNavigation: () -> Unit,
    onCancelTarget: () -> Unit,
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
                        text = "${uiState.routeMode.label} · ${formatRadius(uiState.radiusMeters)} · ${uiState.generationMode.label}",
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
                if (uiState.targetPoint == null) {
                    Button(
                        enabled = uiState.canGenerate,
                        onClick = onGenerateRoute,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Generate")
                            Icon(Icons.Outlined.AddLocationAlt, contentDescription = null)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenNavigation,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = 0xFF1E88E5.toInt().toColor(),
                                contentColor = 0xFFFFFFFF.toInt().toColor(),
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Navigate")
                                Icon(Icons.Outlined.Navigation, contentDescription = null)
                            }
                        }
                        FilledTonalIconButton(
                            onClick = onCancelTarget,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel target")
                        }
                    }
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
                        Text("Generation", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${uiState.generationMode.label} · ${uiState.generationMode.samplePointCount} points",
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
    targetType: ExplorationTargetType,
    intent: String?,
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
            val generation = withContext(Dispatchers.IO) {
                qrngClient.generateTarget(
                    TargetGenerationRequest(
                        origin = current,
                        radiusMeters = uiState.radiusMeters,
                        mode = uiState.generationMode,
                        targetType = targetType,
                        provider = developerSettings.randomProvider,
                    ),
                )
            }
            val target = generation.target.point
            val routePoints = fetchRoutePoints(context, uiState.routeMode, current, target)
            Triple(generation, target, routePoints)
        }

        result
            .onSuccess { (generation, target, routePoints) ->
                onRouteGenerated(
                    RouteHistoryRecord(
                        id = "${System.currentTimeMillis()}-${target.latitude}-${target.longitude}",
                        createdAtMillis = System.currentTimeMillis(),
                        randomProvider = developerSettings.randomProvider,
                        randomSource = generation.source,
                        randomType = generation.randomType,
                        randomLength = generation.randomValueCount,
                        randomValues = emptyList(),
                        generationMode = generation.mode,
                        targetType = generation.targetType,
                        intent = intent,
                        samplePointCount = generation.samplePointCount,
                        densityScore = generation.target.score,
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
                onStateChange(uiState.withTargetRoute(generation, intent, routePoints))
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

private val TargetGenerationMode.randomValueCount: Int
    get() = when (this) {
        TargetGenerationMode.Standard -> 2048
        TargetGenerationMode.Fine -> 4096
    }

private val TargetGenerationMode.samplePointCount: Int
    get() = randomValueCount / 2

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

private fun Int.toColor(): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(this)

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
    var targetRangeCircle: Circle? = null
    var targetMarker: Marker? = null
    var densityMarker: Marker? = null
    var routePolyline: Polyline? = null
    var heatGroundOverlay: GroundOverlay? = null
    var samplePointCircles: List<Circle> = emptyList()
    var targetConnectorPolylines: List<Polyline> = emptyList()
    var lastViewportKey: String? = null
}

private fun MapView.renderExploreOverlays(
    renderState: MapRenderState,
    state: ExploreUiState,
    heatmapSettings: HeatmapDeveloperSettings,
) {
    map.mapType = state.mapVisualMode.toAmapMapType()
    renderState.radiusCircle?.remove()
    renderState.targetRangeCircle?.remove()
    renderState.targetMarker?.remove()
    renderState.densityMarker?.remove()
    renderState.routePolyline?.remove()
    renderState.heatGroundOverlay?.remove()
    renderState.samplePointCircles.forEach { it.remove() }
    renderState.targetConnectorPolylines.forEach { it.remove() }
    renderState.heatGroundOverlay = null
    renderState.samplePointCircles = emptyList()
    renderState.targetConnectorPolylines = emptyList()

    state.currentPoint?.let { origin ->
        renderState.radiusCircle = map.addCircle(
            CircleOptions()
                .center(origin.toLatLng())
                .radius(state.radiusMeters.toDouble())
                .strokeColor(0xFF2F4B66.toInt())
                .fillColor(0x00000000)
                .strokeWidth(20f),
        )
    }

    state.targetGeneration?.let { generation ->
        buildHeatmapBitmap(generation, heatmapSettings)?.let { bitmap ->
            renderState.heatGroundOverlay = map.addGroundOverlay(
                GroundOverlayOptions()
                    .image(BitmapDescriptorFactory.fromBitmap(bitmap))
                    .positionFromBounds(generation.heatGrid.toLatLngBounds())
                    .transparency(heatmapSettings.overlayTransparency.coerceIn(0f, 1f))
                    .zIndex(HEATMAP_Z_INDEX),
            )
        }
        renderState.densityMarker = map.addMarker(
            MarkerOptions()
                .position(generation.attractor.point.toLatLng())
                .title("Density peak")
                .snippet("Attractor score %.2f".format(generation.attractor.score))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                .anchor(0.5f, 1.0f),
        )
    }

    state.targetPoint?.let { target ->
        renderState.targetRangeCircle = map.addCircle(
            CircleOptions()
                .center(target.toLatLng())
                .radius(randomTargetRangeMeters(target, state.radiusMeters))
                .strokeColor(0xFFD32F2F.toInt())
                .fillColor(0x00000000)
                .strokeWidth(8f),
        )
        renderState.targetMarker = map.addMarker(
            MarkerOptions()
                .position(target.toLatLng())
                .title(state.targetGeneration?.targetType?.label ?: "Quantum target")
                .snippet(state.targetGeneration?.let { "${it.mode.label} · ${it.samplePointCount} sample points" } ?: "Generated from ANU/AQN entropy")
                .icon(MapMarkerHelper.getQuantumTargetMarker(context))
                .anchor(0.5f, 1.0f),
        )
    }

    if (state.routePoints.size >= 2) {
        renderState.routePolyline = map.addPolyline(
            PolylineOptions()
                .addAll(state.routePoints.map { it.toLatLng() })
                .color(0xFF1E88E5.toInt())
                .width(8f),
        )

        state.targetPoint?.let { target ->
            val routeEnd = state.routePoints.last()
            if (routeEnd.distanceToMeters(target) > TARGET_CONNECTOR_MIN_DISTANCE_METERS) {
                renderState.targetConnectorPolylines = routeEnd
                    .dashedSegmentsTo(target)
                    .map { (start, end) ->
                        map.addPolyline(
                            PolylineOptions()
                                .add(start.toLatLng(), end.toLatLng())
                                .color(0xCC1E88E5.toInt())
                                .width(7f),
                        )
                    }
            }
        }
    }

    fitViewportIfNeeded(renderState, state)
}

private fun ExplorePoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun ExplorePoint.distanceToMeters(other: ExplorePoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val deltaLat = Math.toRadians(other.latitude - latitude)
    val deltaLng = Math.toRadians(other.longitude - longitude)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1) * cos(lat2) * sin(deltaLng / 2) * sin(deltaLng / 2)
    return earthRadiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun ExplorePoint.dashedSegmentsTo(other: ExplorePoint): List<Pair<ExplorePoint, ExplorePoint>> {
    val distanceMeters = distanceToMeters(other)
    if (distanceMeters <= TARGET_CONNECTOR_MIN_DISTANCE_METERS) {
        return emptyList()
    }

    val segments = mutableListOf<Pair<ExplorePoint, ExplorePoint>>()
    var startMeters = 0.0
    while (startMeters < distanceMeters) {
        val endMeters = (startMeters + TARGET_CONNECTOR_DASH_METERS).coerceAtMost(distanceMeters)
        segments += interpolateTo(other, startMeters / distanceMeters) to interpolateTo(other, endMeters / distanceMeters)
        startMeters += TARGET_CONNECTOR_DASH_METERS + TARGET_CONNECTOR_GAP_METERS
    }
    return segments
}

private fun ExplorePoint.interpolateTo(other: ExplorePoint, fraction: Double): ExplorePoint =
    ExplorePoint(
        latitude = latitude + (other.latitude - latitude) * fraction,
        longitude = longitude + (other.longitude - longitude) * fraction,
    )

private fun randomTargetRangeMeters(target: ExplorePoint, radiusMeters: Int): Double {
    val seed = target.latitude.toBits() xor target.longitude.toBits()
    val fraction = ((seed ushr 1) % 1_000L).toDouble() / 999.0
    val radiusFraction = 0.02 + fraction * 0.03
    return (radiusMeters * radiusFraction)
        .coerceAtLeast(10.0)
        .coerceAtMost(radiusMeters * 0.05)
}

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
        append('|').append(state.targetGeneration?.targetType)
        append('|').append(state.targetGeneration?.mode)
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

private fun buildHeatmapBitmap(
    generation: TargetGenerationResult,
    settings: HeatmapDeveloperSettings,
): Bitmap? {
    val grid = generation.heatGrid
    if (grid.cells.isEmpty() || grid.size <= 1) {
        return null
    }

    val gridValues = DoubleArray(grid.size * grid.size)
    grid.cells.take(gridValues.size).forEachIndexed { index, cell ->
        gridValues[index] = cell.value.coerceIn(0.0, 1.0)
    }

    val bitmap = Bitmap.createBitmap(HEATMAP_BITMAP_SIZE, HEATMAP_BITMAP_SIZE, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(HEATMAP_BITMAP_SIZE * HEATMAP_BITMAP_SIZE)
    for (y in 0 until HEATMAP_BITMAP_SIZE) {
        val normalizedY = y.toDouble() / (HEATMAP_BITMAP_SIZE - 1).coerceAtLeast(1)
        val gridY = (1.0 - normalizedY) * (grid.size - 1)
        val centeredY = normalizedY * 2.0 - 1.0
        for (x in 0 until HEATMAP_BITMAP_SIZE) {
            val normalizedX = x.toDouble() / (HEATMAP_BITMAP_SIZE - 1).coerceAtLeast(1)
            val centeredX = normalizedX * 2.0 - 1.0
            val radialDistance = sqrt(centeredX * centeredX + centeredY * centeredY)
            if (radialDistance > 1.0) {
                continue
            }

            val gridX = normalizedX * (grid.size - 1)
            val value = sampleGridValue(gridValues, grid.size, gridX, gridY)
            val intensity = heatmapDisplayIntensity(value, generation, radialDistance, settings)
            pixels[y * HEATMAP_BITMAP_SIZE + x] = heatmapColor(intensity, generation, settings)
        }
    }
    bitmap.setPixels(pixels, 0, HEATMAP_BITMAP_SIZE, 0, 0, HEATMAP_BITMAP_SIZE, HEATMAP_BITMAP_SIZE)
    return bitmap
}

private fun HeatGrid.toLatLngBounds(): LatLngBounds =
    LatLngBounds(
        LatLng(minLatitude, minLongitude),
        LatLng(maxLatitude, maxLongitude),
    )

private fun sampleGridValue(values: DoubleArray, size: Int, x: Double, y: Double): Double {
    val x0 = floor(x).toInt().coerceIn(0, size - 1)
    val y0 = floor(y).toInt().coerceIn(0, size - 1)
    val x1 = (x0 + 1).coerceAtMost(size - 1)
    val y1 = (y0 + 1).coerceAtMost(size - 1)
    val tx = (x - x0).coerceIn(0.0, 1.0)
    val ty = (y - y0).coerceIn(0.0, 1.0)
    val top = lerp(values[y0 * size + x0], values[y0 * size + x1], tx)
    val bottom = lerp(values[y1 * size + x0], values[y1 * size + x1], tx)
    return lerp(top, bottom, ty)
}

private fun heatmapDisplayIntensity(
    value: Double,
    generation: TargetGenerationResult,
    radialDistance: Double,
    settings: HeatmapDeveloperSettings,
): Double {
    val normalized = value.coerceIn(0.0, 1.0)
    return if (generation.targetType == ExplorationTargetType.Void || generation.target.role == "void") {
        val voidValue = 1.0 - normalized
        val edgeFade = 1.0 - smoothstep(settings.voidEdgeFadeStart.toDouble(), 1.0, radialDistance)
        smoothstep(HEATMAP_VOID_CONTRAST_LOW, HEATMAP_VOID_CONTRAST_HIGH, voidValue)
            .pow(settings.voidGamma.toDouble()) * edgeFade
    } else {
        normalized.pow(settings.hotGamma.toDouble())
    }
}

private fun heatmapColor(
    intensity: Double,
    generation: TargetGenerationResult,
    settings: HeatmapDeveloperSettings,
): Int {
    val normalized = intensity.coerceIn(0.0, 1.0)
    val isVoid = generation.targetType == ExplorationTargetType.Void || generation.target.role == "void"
    val alphaCutoff = if (isVoid) HEATMAP_VOID_ALPHA_CUTOFF else settings.hotAlphaCutoff.toDouble()
    if (normalized <= alphaCutoff) {
        return Color.TRANSPARENT
    }
    val colors = if (isVoid) {
        VOID_HEATMAP_COLORS
    } else {
        HOT_HEATMAP_COLORS
    }
    val stops = if (isVoid) {
        VOID_HEATMAP_STOPS
    } else {
        HOT_HEATMAP_STOPS
    }
    val color = interpolateColor(colors, stops, normalized)
    val alphaGamma = if (isVoid) HEATMAP_VOID_ALPHA_GAMMA else settings.hotAlphaGamma.toDouble()
    val alphaIntensity = normalized.pow(alphaGamma)
    val alpha = (HEATMAP_MIN_ALPHA + alphaIntensity * (HEATMAP_MAX_ALPHA - HEATMAP_MIN_ALPHA))
        .roundToInt()
        .coerceIn(0, 255)
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

private fun interpolateColor(colors: IntArray, stops: FloatArray, value: Double): Int {
    val clamped = value.coerceIn(0.0, 1.0).toFloat()
    val upperIndex = stops.indexOfFirst { clamped <= it }.takeIf { it >= 0 } ?: stops.lastIndex
    if (upperIndex == 0) {
        return colors.first()
    }

    val lowerIndex = upperIndex - 1
    val lowerStop = stops[lowerIndex]
    val upperStop = stops[upperIndex]
    val fraction = if (upperStop == lowerStop) 0.0 else ((clamped - lowerStop) / (upperStop - lowerStop)).toDouble()
    return Color.rgb(
        lerp(Color.red(colors[lowerIndex]).toDouble(), Color.red(colors[upperIndex]).toDouble(), fraction).roundToInt(),
        lerp(Color.green(colors[lowerIndex]).toDouble(), Color.green(colors[upperIndex]).toDouble(), fraction).roundToInt(),
        lerp(Color.blue(colors[lowerIndex]).toDouble(), Color.blue(colors[upperIndex]).toDouble(), fraction).roundToInt(),
    )
}

private fun lerp(start: Double, end: Double, fraction: Double): Double =
    start + (end - start) * fraction.coerceIn(0.0, 1.0)

private fun smoothstep(edge0: Double, edge1: Double, value: Double): Double {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
    return x * x * (3 - 2 * x)
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
private const val TARGET_CONNECTOR_MIN_DISTANCE_METERS = 3.0
private const val TARGET_CONNECTOR_DASH_METERS = 10.0
private const val TARGET_CONNECTOR_GAP_METERS = 16.0
private const val HEATMAP_Z_INDEX = 1.0f
private const val HEATMAP_BITMAP_SIZE = 512
private const val HEATMAP_VOID_CONTRAST_LOW = 0.04
private const val HEATMAP_VOID_CONTRAST_HIGH = 0.78
private const val HEATMAP_VOID_ALPHA_GAMMA = 0.85
private const val HEATMAP_VOID_ALPHA_CUTOFF = 0.005
private const val HEATMAP_MIN_ALPHA = 10
private const val HEATMAP_MAX_ALPHA = 190
private val HOT_HEATMAP_COLORS = intArrayOf(
    Color.rgb(0, 122, 255),
    Color.rgb(0, 210, 180),
    Color.rgb(255, 214, 10),
    Color.rgb(255, 133, 27),
    Color.rgb(255, 59, 48),
)
private val HOT_HEATMAP_STOPS = floatArrayOf(0.08f, 0.50f, 0.78f, 0.94f, 1.0f)
private val VOID_HEATMAP_COLORS = intArrayOf(
    Color.rgb(35, 78, 255),
    Color.rgb(116, 77, 255),
    Color.rgb(0, 229, 255),
    Color.rgb(160, 245, 255),
    Color.rgb(245, 250, 255),
)
private val VOID_HEATMAP_STOPS = floatArrayOf(0.02f, 0.36f, 0.66f, 0.90f, 1.0f)

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
            onGenerateRoute = {},
            onOpenNavigation = {},
            onCancelTarget = {},
        )
    }
}
