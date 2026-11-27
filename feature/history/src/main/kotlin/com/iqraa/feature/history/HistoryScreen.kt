package com.iqraa.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * F5 History — local list with per-state counts; delete one/all; no audio stored (§10.1).
 * V-048: real data + localized strings (was the hardcoded «السجل» + `Text("history")`
 * placeholder route).
 */
@Suppress("FunctionName") // Compose convention
@Composable
fun HistoryScreen(
    entries: List<HistoryUiModel>,
    onEntryTapped: (Long) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(all = 16.dp)) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            Button(onClick = onDeleteAll, modifier = Modifier.padding(vertical = 8.dp)) {
                Text(stringResource(R.string.delete_all_history))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                items(entries, key = { it.id }) { e ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onEntryTapped(e.id) },
                    ) {
                        Text(
                            text = "${e.surah}:${e.fromAyah}–${e.toAyah}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.history_counts_fmt, e.correct, e.y, e.close),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
