package com.quorvia.app.feature.explore

import android.content.Context

data class ExplorePreferences(
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val mapVisualMode: MapVisualMode = DEFAULT_MAP_VISUAL_MODE,
    val generationMode: TargetGenerationMode = DEFAULT_TARGET_GENERATION_MODE,
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
        val generationMode = TargetGenerationMode.entries.firstOrNull {
            it.name == preferences.getString(KEY_GENERATION_MODE, DEFAULT_TARGET_GENERATION_MODE.name)
        } ?: DEFAULT_TARGET_GENERATION_MODE

        return ExplorePreferences(
            radiusMeters = SUPPORTED_RADIUS_METERS.minBy { kotlin.math.abs(it - radiusMeters) },
            mapVisualMode = mapVisualMode,
            generationMode = generationMode,
        )
    }

    fun save(preferences: ExplorePreferences) {
        this.preferences.edit()
            .putInt(KEY_RADIUS_METERS, preferences.radiusMeters)
            .putString(KEY_MAP_VISUAL_MODE, preferences.mapVisualMode.name)
            .putString(KEY_GENERATION_MODE, preferences.generationMode.name)
            .apply()
    }

    private companion object {
        const val KEY_RADIUS_METERS = "radius_meters"
        const val KEY_MAP_VISUAL_MODE = "map_visual_mode"
        const val KEY_GENERATION_MODE = "generation_mode"
    }
}
