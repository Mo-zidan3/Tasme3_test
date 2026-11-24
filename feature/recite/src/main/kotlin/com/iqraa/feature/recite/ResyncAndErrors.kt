package com.iqraa.feature.recite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iqraa.core.session.DegradedLevel

/**
 * T8: §10.6 resync picker — the lost-tracking banner opens an AYAH PICKER WITHIN THE
 * SELECTED RANGE; picking re-anchors via `tracker.reanchor(ayah)` (previous states
 * preserved per §10.6 — reanchor only moves the pointer).
 */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun ResyncPickerDialog(
    surah: Int,
    fromAyah: Int,
    toAyah: Int,
    onAyahPicked: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.resync_title)) },
        text = {
            Column {
                (fromAyah..toAyah).forEach { ayah ->
                    TextButton(
                        onClick = { onAyahPicked(ayah) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("$surah:$ayah")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.resync_cancel)) }
        },
    )
}

/**
 * T8: §11 error surfaces — model-load failure (retry once → other bundled model → honest
 * error), the lost-tracking banner (opens the resync picker), and the §10.5 degraded
 * ladder badge («الوضع الخفيف» when tiny or L1/L2 active — never silently degrade).
 */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun ModelLoadErrorCard(
    onRetry: () -> Unit,
    onTryOtherModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.model_load_error),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = onTryOtherModel) { Text(stringResource(R.string.try_other_model)) }
        }
    }
}

/** §10.5: «الوضع الخفيف» badge — shown when the tiny model is active or the ladder's
 *  badge flag is set (DegradedLevel.badge: L2/L3; L1 is a delay notice without the badge —
 *  §8.6 ladder semantics, never silently degrade). */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun DegradedBadge(
    level: DegradedLevel,
    tinyModel: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!level.badge && !tinyModel) return
    Text(
        text = stringResource(R.string.light_mode_badge),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
