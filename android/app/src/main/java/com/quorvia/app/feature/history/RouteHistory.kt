package com.quorvia.app.feature.history

import android.content.Context
import com.quorvia.app.feature.explore.MapVisualMode
import com.quorvia.app.feature.explore.RouteMode
import com.quorvia.app.settings.RandomProvider
import org.json.JSONArray
import org.json.JSONObject

data class RouteHistoryRecord(
    val id: String,
    val createdAtMillis: Long,
    val randomProvider: RandomProvider,
    val randomSource: String,
    val radiusMeters: Int,
    val routeMode: RouteMode,
    val mapVisualMode: MapVisualMode,
    val originLatitude: Double,
    val originLongitude: Double,
    val targetLatitude: Double,
    val targetLongitude: Double,
    val routePointCount: Int,
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
        records.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private companion object {
        const val KEY_RECORDS = "records"
        const val MAX_RECORDS = 50
    }
}

private fun RouteHistoryRecord.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("createdAtMillis", createdAtMillis)
        .put("randomProvider", randomProvider.name)
        .put("randomSource", randomSource)
        .put("radiusMeters", radiusMeters)
        .put("routeMode", routeMode.name)
        .put("mapVisualMode", mapVisualMode.name)
        .put("originLatitude", originLatitude)
        .put("originLongitude", originLongitude)
        .put("targetLatitude", targetLatitude)
        .put("targetLongitude", targetLongitude)
        .put("routePointCount", routePointCount)

private fun JSONObject.toRouteHistoryRecord(): RouteHistoryRecord =
    RouteHistoryRecord(
        id = getString("id"),
        createdAtMillis = getLong("createdAtMillis"),
        randomProvider = enumValueOrDefault(
            value = optString("randomProvider"),
            default = RandomProvider.Quantum,
        ),
        randomSource = optString("randomSource", "ANU Quantum Numbers"),
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
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
