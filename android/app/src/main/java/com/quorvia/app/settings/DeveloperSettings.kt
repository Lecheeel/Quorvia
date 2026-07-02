package com.quorvia.app.settings

import android.content.Context

enum class RandomProvider(val queryValue: String) {
    Quantum("aqn"),
    Debug("debug"),
}

data class DeveloperSettings(
    val developerModeEnabled: Boolean = false,
    val randomProvider: RandomProvider = RandomProvider.Quantum,
) {
    val isDebugRandom: Boolean = developerModeEnabled && randomProvider == RandomProvider.Debug
}

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
        )
    }

    fun save(settings: DeveloperSettings) {
        preferences.edit()
            .putBoolean(KEY_DEVELOPER_MODE, settings.developerModeEnabled)
            .putString(KEY_RANDOM_PROVIDER, settings.randomProvider.name)
            .apply()
    }

    private companion object {
        const val KEY_DEVELOPER_MODE = "developer_mode_enabled"
        const val KEY_RANDOM_PROVIDER = "random_provider"
    }
}
