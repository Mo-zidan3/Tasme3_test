package com.iqraa.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * F1 Reader — the surah list (§10.1 Home), state-driven (issue #11 / V-048): Loading /
 * Loaded (114 clickable rows) / Error + retry (§11 — an empty list is NEVER rendered as
 * success). Title localized (اقرأ / Read) — no hardcoded Arabic on screen.
 */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun ReaderScreen(
    state: ReaderState,
    onSurahSelected: (Surah) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(all = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        when (state) {
            ReaderState.Loading -> {
                CircularProgressIndicator(Modifier.padding(top = 24.dp))
            }

            is ReaderState.Error -> {
                Column(Modifier.padding(top = 24.dp)) {
                    Text(
                        text = stringResource(R.string.reader_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            is ReaderState.Loaded -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
                    items(state.surahs, key = { it.number }) { surah ->
                        Text(
                            text = "${surah.number}. ${surah.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSurahSelected(surah) }
                                    .padding(vertical = 10.dp)
                                    .semantics { contentDescription = "surah_${surah.number}" },
                        )
                    }
                }
            }
        }
    }
}
