package com.quorvia.app.feature.explore

data class ExploreUiState(
    val radiusKm: Int = 3,
    val routeMode: RouteMode = RouteMode.Walk,
) {
    val canGenerate: Boolean = radiusKm in MIN_RADIUS_KM..MAX_RADIUS_KM
}

enum class RouteMode {
    Walk,
    Drive,
}

const val MIN_RADIUS_KM = 1
const val MAX_RADIUS_KM = 10

fun ExploreUiState.withRadius(radiusKm: Int): ExploreUiState =
    copy(radiusKm = radiusKm.coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM))

fun ExploreUiState.withRouteMode(routeMode: RouteMode): ExploreUiState =
    copy(routeMode = routeMode)

