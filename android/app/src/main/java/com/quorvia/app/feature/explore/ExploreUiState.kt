package com.quorvia.app.feature.explore

data class ExploreUiState(
    val radiusKm: Int = 3,
    val routeMode: RouteMode = RouteMode.Walk,
    val currentPoint: ExplorePoint? = null,
    val targetPoint: ExplorePoint? = null,
    val status: ExploreStatus = ExploreStatus.Idle,
) {
    val canGenerate: Boolean =
        radiusKm in MIN_RADIUS_KM..MAX_RADIUS_KM &&
            currentPoint != null &&
            status !is ExploreStatus.Loading
}

enum class RouteMode {
    Walk,
    Drive,
}

const val MIN_RADIUS_KM = 1
const val MAX_RADIUS_KM = 10

data class ExplorePoint(
    val latitude: Double,
    val longitude: Double,
)

sealed interface ExploreStatus {
    data object Idle : ExploreStatus
    data object Loading : ExploreStatus
    data class Message(val text: String) : ExploreStatus
    data class Error(val text: String) : ExploreStatus
}

fun ExploreUiState.withRadius(radiusKm: Int): ExploreUiState =
    copy(radiusKm = radiusKm.coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM))

fun ExploreUiState.withRouteMode(routeMode: RouteMode): ExploreUiState =
    copy(routeMode = routeMode)

fun ExploreUiState.withCurrentPoint(point: ExplorePoint): ExploreUiState =
    copy(currentPoint = point)

fun ExploreUiState.withTargetPoint(point: ExplorePoint): ExploreUiState =
    copy(targetPoint = point, status = ExploreStatus.Message("Quantum target generated."))

fun ExploreUiState.withStatus(status: ExploreStatus): ExploreUiState =
    copy(status = status)
