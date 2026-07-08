package com.quorvia.app.feature.history

import android.content.Context
import com.quorvia.app.feature.explore.ExplorePoint
import com.quorvia.app.feature.explore.ExplorationTargetType
import com.quorvia.app.feature.explore.MapVisualMode
import com.quorvia.app.feature.explore.RouteMode
import com.quorvia.app.feature.explore.TargetGenerationMode
import com.quorvia.app.settings.RandomProvider
import org.json.JSONArray
import org.json.JSONObject

data class RouteHistoryRecord(
    val id: String,
    val createdAtMillis: Long,
    val randomProvider: RandomProvider,
    val randomSource: String,
    val randomType: String,
    val randomLength: Int,
    val randomValues: List<Int>,
    val generationMode: TargetGenerationMode = TargetGenerationMode.Standard,
    val targetType: ExplorationTargetType = ExplorationTargetType.Attractor,
    val intent: String? = null,
    val samplePointCount: Int = randomLength / 2,
    val densityScore: Double? = null,
    val radiusMeters: Int,
    val routeMode: RouteMode,
    val mapVisualMode: MapVisualMode,
    val originLatitude: Double,
    val originLongitude: Double,
    val targetLatitude: Double,
    val targetLongitude: Double,
    val routePointCount: Int,
    val routePoints: List<ExplorePoint>,
)

class RouteHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "route_history",
        Context.MODE_PRIVATE,
    )

    fun load(): List<RouteHistoryRecord> =
        runCatching {
            val raw = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
            val array = JSONArray(raw)
            List(array.length()) { index -> array.getJSONObject(index).toRouteHistoryRecord() }
        }.getOrDefault(emptyList())

    fun add(record: RouteHistoryRecord): List<RouteHistoryRecord> {
        val records = (listOf(record) + load()).take(MAX_RECORDS)
        save(records)
        return records
    }

    fun clear(): List<RouteHistoryRecord> {
        preferences.edit().remove(KEY_RECORDS).apply()
        return emptyList()
    }

    private fun save(records: List<RouteHistoryRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toRouteHistoryJson()) }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private companion object {
        const val KEY_RECORDS = "records"
        const val MAX_RECORDS = 50
    }
}

internal fun RouteHistoryRecord.toRouteHistoryJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("createdAtMillis", createdAtMillis)
        .put("randomProvider", randomProvider.name)
        .put("randomSource", randomSource)
        .put("randomType", randomType)
        .put("randomLength", randomLength)
        .put("randomValues", JSONArray().apply { randomValues.forEach { put(it) } })
        .put("generationMode", generationMode.name)
        .put("targetType", targetType.name)
        .put("intent", intent)
        .put("samplePointCount", samplePointCount)
        .put("densityScore", densityScore)
        .put("radiusMeters", radiusMeters)
        .put("routeMode", routeMode.name)
        .put("mapVisualMode", mapVisualMode.name)
        .put("originLatitude", originLatitude)
        .put("originLongitude", originLongitude)
        .put("targetLatitude", targetLatitude)
        .put("targetLongitude", targetLongitude)
        .put("routePointCount", routePointCount)
        .put("routePoints", JSONArray().apply { routePoints.forEach { put(it.toJson()) } })

internal fun JSONObject.toRouteHistoryRecord(): RouteHistoryRecord =
    RouteHistoryRecord(
        id = getString("id"),
        createdAtMillis = getLong("createdAtMillis"),
        randomProvider = enumValueOrDefault(
            value = optString("randomProvider"),
            default = RandomProvider.Quantum,
        ),
        randomSource = optString("randomSource", "ANU Quantum Numbers"),
        randomType = optString("randomType", "uint16"),
        randomLength = optInt("randomLength", 2),
        randomValues = optIntList("randomValues"),
        generationMode = enumValueOrDefault(
            value = optString("generationMode"),
            default = TargetGenerationMode.Standard,
        ),
        targetType = enumValueOrDefault(
            value = optString("targetType"),
            default = ExplorationTargetType.Attractor,
        ),
        intent = optString("intent").takeIf { it.isNotBlank() && it != "null" },
        samplePointCount = optInt("samplePointCount", optInt("randomLength", 2) / 2),
        densityScore = if (has("densityScore") && !isNull("densityScore")) optDouble("densityScore") else null,
        radiusMeters = getInt("radiusMeters"),
        routeMode = enumValueOrDefault(
            value = optString("routeMode"),
            default = RouteMode.Walk,
        ),
        mapVisualMode = enumValueOrDefault(
            value = optString("mapVisualMode"),
            default = MapVisualMode.Normal,
        ),
        originLatitude = getDouble("originLatitude"),
        originLongitude = getDouble("originLongitude"),
        targetLatitude = getDouble("targetLatitude"),
        targetLongitude = getDouble("targetLongitude"),
        routePointCount = getInt("routePointCount"),
        routePoints = optPointList("routePoints"),
    )

private fun ExplorePoint.toJson(): JSONObject =
    JSONObject()
        .put("latitude", latitude)
        .put("longitude", longitude)

private fun JSONObject.toExplorePoint(): ExplorePoint =
    ExplorePoint(
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
    )

private fun JSONObject.optIntList(name: String): List<Int> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index -> array.getInt(index) }
}

private fun JSONObject.optPointList(name: String): List<ExplorePoint> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index -> array.getJSONObject(index).toExplorePoint() }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
