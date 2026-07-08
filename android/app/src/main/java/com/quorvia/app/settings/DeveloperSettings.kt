package com.quorvia.app.settings

import android.content.Context

enum class RandomProvider(val queryValue: String) {
    Quantum("aqn"),
    Debug("debug"),
}

data class DeveloperSettings(
    val developerModeEnabled: Boolean = false,
    val randomProvider: RandomProvider = RandomProvider.Quantum,
    val heatmap: HeatmapDeveloperSettings = HeatmapDeveloperSettings(),
) {
    val isDebugRandom: Boolean = developerModeEnabled && randomProvider == RandomProvider.Debug
}

data class HeatmapDeveloperSettings(
    val overlayTransparency: Float = 0.10f,
    val hotGamma: Float = 1.80f,
    val hotAlphaCutoff: Float = 0.00f,
    val hotAlphaGamma: Float = 1.65f,
    val voidGamma: Float = 0.77f,
    val voidEdgeFadeStart: Float = 0.69f,
)

class DeveloperSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "developer_settings",
        Context.MODE_PRIVATE,
    )

    fun load(): DeveloperSettings {
        val developerModeEnabled = preferences.getBoolean(KEY_DEVELOPER_MODE, false)
        val randomProvider = RandomProvider.entries.firstOrNull {
            it.name == preferences.getString(KEY_RANDOM_PROVIDER, RandomProvider.Quantum.name)
        } ?: RandomProvider.Quantum

        return DeveloperSettings(
            developerModeEnabled = developerModeEnabled,
            randomProvider = if (developerModeEnabled) randomProvider else RandomProvider.Quantum,
            heatmap = HeatmapDeveloperSettings(
                overlayTransparency = preferences.getFloat(KEY_HEATMAP_OVERLAY_TRANSPARENCY, 0.10f),
                hotGamma = preferences.getFloat(KEY_HEATMAP_HOT_GAMMA, 1.80f),
                hotAlphaCutoff = preferences.getFloat(KEY_HEATMAP_HOT_ALPHA_CUTOFF, 0.00f),
                hotAlphaGamma = preferences.getFloat(KEY_HEATMAP_HOT_ALPHA_GAMMA, 1.65f),
                voidGamma = preferences.getFloat(KEY_HEATMAP_VOID_GAMMA, 0.77f),
                voidEdgeFadeStart = preferences.getFloat(KEY_HEATMAP_VOID_EDGE_FADE_START, 0.69f),
            ),
        )
    }

    fun save(settings: DeveloperSettings) {
        preferences.edit()
            .putBoolean(KEY_DEVELOPER_MODE, settings.developerModeEnabled)
            .putString(KEY_RANDOM_PROVIDER, settings.randomProvider.name)
            .putFloat(KEY_HEATMAP_OVERLAY_TRANSPARENCY, settings.heatmap.overlayTransparency)
            .putFloat(KEY_HEATMAP_HOT_GAMMA, settings.heatmap.hotGamma)
            .putFloat(KEY_HEATMAP_HOT_ALPHA_CUTOFF, settings.heatmap.hotAlphaCutoff)
            .putFloat(KEY_HEATMAP_HOT_ALPHA_GAMMA, settings.heatmap.hotAlphaGamma)
            .putFloat(KEY_HEATMAP_VOID_GAMMA, settings.heatmap.voidGamma)
            .putFloat(KEY_HEATMAP_VOID_EDGE_FADE_START, settings.heatmap.voidEdgeFadeStart)
            .apply()
    }

    private companion object {
        const val KEY_DEVELOPER_MODE = "developer_mode_enabled"
        const val KEY_RANDOM_PROVIDER = "random_provider"
        const val KEY_HEATMAP_OVERLAY_TRANSPARENCY = "heatmap_overlay_transparency"
        const val KEY_HEATMAP_HOT_GAMMA = "heatmap_hot_gamma"
        const val KEY_HEATMAP_HOT_ALPHA_CUTOFF = "heatmap_hot_alpha_cutoff"
        const val KEY_HEATMAP_HOT_ALPHA_GAMMA = "heatmap_hot_alpha_gamma"
        const val KEY_HEATMAP_VOID_GAMMA = "heatmap_void_gamma"
        const val KEY_HEATMAP_VOID_EDGE_FADE_START = "heatmap_void_edge_fade_start"
    }
}
