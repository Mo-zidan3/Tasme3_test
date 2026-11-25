package com.iqraa.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * F6 Settings — §10.1. V-048: minimal FUNCTIONING controls (was the stub with the
 * hardcoded «الإعدادات» title): the §9.5 preset (persisted to DataStore, honored by the
 * next session's TrackerConfig) and the bundled-model choice. Language/theme/fontScale
 * stay Phase-4 (Mo's locale-default decision recorded in issue #11).
 */
@Suppress("FunctionName") // Compose convention
@Composable
fun SettingsScreen(
    state: SettingsUiModel,
    onPresetSelected: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.preset_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        )
        Row {
            FilterChip(
                selected = state.preset == PRESET_CONSERVATIVE,
                onClick = { onPresetSelected(PRESET_CONSERVATIVE) },
                label = { Text(stringResource(R.string.preset_conservative)) },
                modifier = Modifier.padding(end = 8.dp),
            )
            FilterChip(
                selected = state.preset == PRESET_BALANCED,
                onClick = { onPresetSelected(PRESET_BALANCED) },
                label = { Text(stringResource(R.string.preset_balanced)) },
            )
        }
        Text(
            stringResource(R.string.model_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        )
        Row {
            FilterChip(
                selected = state.modelOverride.isEmpty() || state.modelOverride == MODEL_SMALL,
                onClick = { onModelSelected(MODEL_SMALL) },
                label = { Text(stringResource(R.string.model_small)) },
                modifier = Modifier.padding(end = 8.dp),
            )
            FilterChip(
                selected = state.modelOverride == MODEL_TINY,
                onClick = { onModelSelected(MODEL_TINY) },
                label = { Text(stringResource(R.string.model_tiny)) },
            )
        }
        Text(
            stringResource(R.string.settings_language_note),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

private const val PRESET_CONSERVATIVE = "CONSERVATIVE"
private const val PRESET_BALANCED = "BALANCED"
private const val MODEL_SMALL = "quran-asr-small"
private const val MODEL_TINY = "quran-asr-tiny"
