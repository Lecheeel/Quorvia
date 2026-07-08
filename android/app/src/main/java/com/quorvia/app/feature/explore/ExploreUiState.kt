package com.quorvia.app.feature.explore

data class ExploreUiState(
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val mapVisualMode: MapVisualMode = DEFAULT_MAP_VISUAL_MODE,
    val generationMode: TargetGenerationMode = DEFAULT_TARGET_GENERATION_MODE,
    val currentPoint: ExplorePoint? = null,
    val targetPoint: ExplorePoint? = null,
    val targetGeneration: TargetGenerationResult? = null,
    val intent: String? = null,
    val routePoints: List<ExplorePoint> = emptyList(),
    val status: ExploreStatus = ExploreStatus.Idle,
) {
    val canGenerate: Boolean =
        radiusMeters in SUPPORTED_RADIUS_METERS &&
            currentPoint != null &&
            status !is ExploreStatus.Loading
}

enum class RouteMode {
    Walk,
    Ride,
    Drive,
}

enum class MapVisualMode {
    Normal,
    Satellite,
}

val SUPPORTED_RADIUS_METERS = listOf(300, 500, 1_000, 2_000, 3_000, 5_000, 10_000)
const val DEFAULT_RADIUS_METERS = 3_000
val DEFAULT_MAP_VISUAL_MODE = MapVisualMode.Satellite
val DEFAULT_TARGET_GENERATION_MODE = TargetGenerationMode.Standard

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

fun ExploreUiState.withRadius(radiusMeters: Int): ExploreUiState =
    copy(
        radiusMeters = SUPPORTED_RADIUS_METERS.minBy { kotlin.math.abs(it - radiusMeters) },
        targetPoint = null,
        targetGeneration = null,
        intent = null,
        routePoints = emptyList(),
    )

val ExploreUiState.routeMode: RouteMode
    get() = routeModeForRadius(radiusMeters)

fun routeModeForRadius(radiusMeters: Int): RouteMode =
    when {
        radiusMeters < 1_000 -> RouteMode.Walk
        radiusMeters <= 2_000 -> RouteMode.Ride
        else -> RouteMode.Drive
    }

fun ExploreUiState.withMapVisualMode(mapVisualMode: MapVisualMode): ExploreUiState =
    copy(mapVisualMode = mapVisualMode)

fun ExploreUiState.withGenerationMode(generationMode: TargetGenerationMode): ExploreUiState =
    copy(generationMode = generationMode)

fun ExploreUiState.withCurrentPoint(point: ExplorePoint): ExploreUiState =
    copy(currentPoint = point)

fun ExploreUiState.withTargetRoute(
    generation: TargetGenerationResult,
    intent: String?,
    routePoints: List<ExplorePoint>,
): ExploreUiState =
    copy(
        targetPoint = generation.target.point,
        targetGeneration = generation,
        intent = intent,
        routePoints = routePoints,
        status = ExploreStatus.Message("Route generated."),
    )

fun ExploreUiState.withoutTargetRoute(): ExploreUiState =
    copy(
        targetPoint = null,
        targetGeneration = null,
        intent = null,
        routePoints = emptyList(),
        status = ExploreStatus.Message("Ready to generate a new target."),
    )

fun ExploreUiState.withStatus(status: ExploreStatus): ExploreUiState =
    copy(status = status)
