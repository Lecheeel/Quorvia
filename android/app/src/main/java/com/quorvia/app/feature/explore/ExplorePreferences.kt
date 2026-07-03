package com.quorvia.app.feature.explore

import android.content.Context

data class ExplorePreferences(
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val mapVisualMode: MapVisualMode = DEFAULT_MAP_VISUAL_MODE,
)

class ExplorePreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "explore_preferences",
        Context.MODE_PRIVATE,
    )

    fun load(): ExplorePreferences {
        val radiusMeters = preferences.getInt(KEY_RADIUS_METERS, DEFAULT_RADIUS_METERS)
        val mapVisualMode = MapVisualMode.entries.firstOrNull {
            it.name == preferences.getString(KEY_MAP_VISUAL_MODE, DEFAULT_MAP_VISUAL_MODE.name)
        } ?: DEFAULT_MAP_VISUAL_MODE

        return ExplorePreferences(
            radiusMeters = SUPPORTED_RADIUS_METERS.minBy { kotlin.math.abs(it - radiusMeters) },
            mapVisualMode = mapVisualMode,
        )
    }

    fun save(preferences: ExplorePreferences) {
        this.preferences.edit()
            .putInt(KEY_RADIUS_METERS, preferences.radiusMeters)
            .putString(KEY_MAP_VISUAL_MODE, preferences.mapVisualMode.name)
            .apply()
    }

    private companion object {
        const val KEY_RADIUS_METERS = "radius_meters"
        const val KEY_MAP_VISUAL_MODE = "map_visual_mode"
    }
}
