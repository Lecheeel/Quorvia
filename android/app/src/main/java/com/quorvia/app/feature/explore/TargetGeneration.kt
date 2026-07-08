package com.quorvia.app.feature.explore

import org.json.JSONArray
import org.json.JSONObject

enum class TargetGenerationMode(val queryValue: String, val label: String) {
    Standard("standard", "Standard"),
    Fine("fine", "Fine"),
}

enum class ExplorationTargetType(val queryValue: String, val label: String) {
    Attractor("attractor", "Attractor"),
    Void("void", "Void"),
    Anomaly("anomaly", "Anomaly"),
}

data class TargetGenerationRequest(
    val origin: ExplorePoint,
    val radiusMeters: Int,
    val mode: TargetGenerationMode,
    val targetType: ExplorationTargetType,
    val provider: com.quorvia.app.settings.RandomProvider,
)

data class TargetGenerationResult(
    val source: String,
    val mode: TargetGenerationMode,
    val targetType: ExplorationTargetType,
    val randomType: String,
    val batchCount: Int,
    val randomValueCount: Int,
    val samplePointCount: Int,
    val radiusMeters: Int,
    val target: DensityTargetPoint,
    val attractor: DensityTargetPoint,
    val voidPoint: DensityTargetPoint,
    val heatGrid: HeatGrid,
    val samplePoints: List<ExplorePoint>,
)

data class DensityTargetPoint(
    val point: ExplorePoint,
    val role: String,
    val density: Double,
    val score: Double,
)

data class HeatGrid(
    val size: Int,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val cells: List<HeatGridCell>,
)

data class HeatGridCell(
    val point: ExplorePoint,
    val value: Double,
)

fun targetGenerationResultFromJson(json: JSONObject): TargetGenerationResult =
    TargetGenerationResult(
        source = json.getString("source"),
        mode = TargetGenerationMode.entries.first { it.queryValue == json.getString("mode") },
        targetType = ExplorationTargetType.entries.first { it.queryValue == json.getString("targetType") },
        randomType = json.getString("randomType"),
        batchCount = json.getInt("batchCount"),
        randomValueCount = json.getInt("randomValueCount"),
        samplePointCount = json.getInt("samplePointCount"),
        radiusMeters = json.getInt("radiusMeters"),
        target = json.getJSONObject("target").toDensityTargetPoint(),
        attractor = json.getJSONObject("attractor").toDensityTargetPoint(),
        voidPoint = json.getJSONObject("void").toDensityTargetPoint(),
        heatGrid = json.getJSONObject("heatGrid").toHeatGrid(),
        samplePoints = json.getJSONArray("samplePoints").toPointList(),
    )

fun TargetGenerationRequest.toJson(): JSONObject =
    JSONObject()
        .put("origin", origin.toJson())
        .put("radiusMeters", radiusMeters)
        .put("mode", mode.queryValue)
        .put("targetType", targetType.queryValue)
        .put("provider", provider.queryValue)

private fun JSONObject.toDensityTargetPoint(): DensityTargetPoint =
    DensityTargetPoint(
        point = ExplorePoint(
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
        ),
        role = getString("role"),
        density = getDouble("density"),
        score = getDouble("score"),
    )

private fun JSONObject.toHeatGrid(): HeatGrid =
    HeatGrid(
        size = getInt("size"),
        minLatitude = getDouble("minLatitude"),
        maxLatitude = getDouble("maxLatitude"),
        minLongitude = getDouble("minLongitude"),
        maxLongitude = getDouble("maxLongitude"),
        cells = getJSONArray("cells").toHeatGridCells(),
    )

private fun JSONArray.toHeatGridCells(): List<HeatGridCell> =
    List(length()) { index ->
        val json = getJSONObject(index)
        HeatGridCell(
            point = ExplorePoint(
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude"),
            ),
            value = json.getDouble("value"),
        )
    }

private fun JSONArray.toPointList(): List<ExplorePoint> =
    List(length()) { index ->
        val json = getJSONObject(index)
        ExplorePoint(
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
        )
    }

private fun ExplorePoint.toJson(): JSONObject =
    JSONObject()
        .put("latitude", latitude)
        .put("longitude", longitude)
