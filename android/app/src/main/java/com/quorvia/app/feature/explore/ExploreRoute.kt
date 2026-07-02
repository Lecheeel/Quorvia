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
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.quorvia.app.ui.theme.QuorviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onStateChange(uiState.withStatus(ExploreStatus.Message("Location permission granted.")))
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
                        generateTargetPoint(current, uiState.radiusKm, randomValues)
                    }

                    result
                        .onSuccess { target ->
                            mapView.addTargetMarker(current, target)
                            onStateChange(uiState.withTargetPoint(target))
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
                uiState.currentPoint?.let { point ->
                    it.map.moveCamera(CameraUpdateFactory.newLatLngZoom(point.toLatLng(), 15f))
                }
                uiState.targetPoint?.let { target ->
                    it.addTargetMarker(uiState.currentPoint, target)
                }
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
            mapView.map.moveCamera(CameraUpdateFactory.newLatLngZoom(point.toLatLng(), 15f))
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

private fun MapView.addTargetMarker(origin: ExplorePoint?, target: ExplorePoint) {
    map.clear()
    origin?.let {
        map.addMarker(
            MarkerOptions()
                .position(it.toLatLng())
                .title("Current location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)),
        )
    }
    map.addMarker(
        MarkerOptions()
            .position(target.toLatLng())
            .title("Quantum target")
            .snippet("Generated from ANU/AQN entropy")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
    )
    origin?.let {
        map.addPolyline(
            PolylineOptions()
                .add(it.toLatLng(), target.toLatLng())
                .color(0xFF2367F4.toInt())
                .width(8f),
        )
    }
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), 15f))
}

private fun ExplorePoint.toLatLng(): LatLng = LatLng(latitude, longitude)

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
