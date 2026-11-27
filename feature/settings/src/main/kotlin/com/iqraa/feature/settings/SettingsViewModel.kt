package com.iqraa.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iqraa.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The settings state the screen renders (structured — the screen localizes). */
data class SettingsUiModel(
    val preset: String,
    val modelOverride: String,
)

/**
 * V-048 (issue #11): minimal FUNCTIONING settings — the §9.5 preset choice persists to
 * DataStore and is honored when the next session builds its TrackerConfig (see
 * ReciteFlowViewModel). Language/theme/fontScale controls stay Phase-4 (Mo's language
 * decision is recorded in issue #11 — open).
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                SettingsUiModel(preset = SettingsRepository.PRESET_CONSERVATIVE, modelOverride = ""),
            )
        val state: StateFlow<SettingsUiModel> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                settings.preset.collect { preset ->
                    _state.value = _state.value.copy(preset = preset)
                }
            }
            viewModelScope.launch {
                settings.modelOverride.collect { model ->
                    _state.value = _state.value.copy(modelOverride = model)
                }
            }
        }

        fun setPreset(preset: String) {
            viewModelScope.launch { settings.setPreset(preset) }
        }

        fun setModelOverride(modelId: String) {
            viewModelScope.launch { settings.setModelOverride(modelId) }
        }
    }
