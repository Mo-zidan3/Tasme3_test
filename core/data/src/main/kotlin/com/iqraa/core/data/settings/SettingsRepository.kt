package com.iqraa.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * §15 + V-048: DataStore-backed settings reads/writes. Keys live in [DataStoreKeys]
 * (single source of truth, D-019). A corrupt file degrades to defaults (§11: never crash
 * the session over preferences) — writes still go through the same DataStore.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    private val onboardingKey = booleanPreferencesKey(DataStoreKeys.ONBOARDING_COMPLETED)
    private val presetKey = stringPreferencesKey(DataStoreKeys.PRESET)
    private val modelOverrideKey = stringPreferencesKey(DataStoreKeys.MODEL_OVERRIDE)

    /** V-048: true once the user completed onboarding (granted or continued without). */
    val onboardingCompleted: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[onboardingKey] ?: false }

    /** §9.5 preset name (CONSERVATIVE default; Mo ratifies). */
    val preset: Flow<String> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[presetKey] ?: PRESET_CONSERVATIVE }

    /** §8.5 model override — empty string = the default small model. */
    val modelOverride: Flow<String> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[modelOverrideKey] ?: "" }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[onboardingKey] = true }
    }

    suspend fun setPreset(preset: String) {
        dataStore.edit { it[presetKey] = preset }
    }

    suspend fun setModelOverride(modelId: String) {
        dataStore.edit { it[modelOverrideKey] = modelId }
    }

    suspend fun onboardingCompletedNow(): Boolean = onboardingCompleted.first()

    suspend fun presetNow(): String = preset.first()

    suspend fun modelOverrideNow(): String = modelOverride.first()

    companion object {
        const val PRESET_CONSERVATIVE = "CONSERVATIVE"
        const val PRESET_BALANCED = "BALANCED"
    }
}
