package com.iqraa.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * §10.1 Range picker — the screen Phase 3 never shipped (reader's onSurahSelected was a
 * no-op): pick the ayah range within the surah + the mode (follow-along | tasmee'), then
 * start the recitation. Minimal functioning version (issue #11 / V-048): steppers clamp
 * from/to inside 1..ayahCount with from ≤ to.
 */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun RangePickerScreen(
    surah: Surah,
    onStart: (fromAyah: Int, toAyah: Int, tasmeeMode: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var from by remember(surah.number) { mutableStateOf(1) }
    var to by remember(surah.number) { mutableStateOf(surah.ayahCount) }
    var tasmee by remember(surah.number) { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.range_picker_title), style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(surah.name, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.range_ayahs_fmt, surah.ayahCount), style = MaterialTheme.typography.bodyMedium)
        }
        AyahStepper(
            label = stringResource(R.string.range_from),
            value = from,
            onDec = { from = (from - 1).coerceAtLeast(1).also { if (to < it) to = it } },
            onInc = { from = (from + 1).coerceAtMost(surah.ayahCount).also { if (to < it) to = it } },
        )
        AyahStepper(
            label = stringResource(R.string.range_to),
            value = to,
            onDec = { to = (to - 1).coerceAtLeast(from) },
            onInc = { to = (to + 1).coerceAtMost(surah.ayahCount) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = !tasmee,
                onClick = { tasmee = false },
                label = { Text(stringResource(R.string.mode_follow)) },
            )
            FilterChip(
                selected = tasmee,
                onClick = { tasmee = true },
                label = { Text(stringResource(R.string.mode_tasmee)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onStart(from, to, tasmee) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.start_recitation))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

/** One labeled ayah stepper row: [-] value [+] — clamped by the caller's callbacks. */
@Suppress("FunctionName") // Compose convention
@Composable
private fun AyahStepper(
    label: String,
    value: Int,
    onDec: () -> Unit,
    onInc: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onDec, enabled = true) { Text("-") }
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        OutlinedButton(onClick = onInc, enabled = true) { Text("+") }
    }
}
