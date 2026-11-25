package com.iqraa.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * F1 Reader — surah list (§10.1 Home → Range picker → Reader).
 * Phase-3 skeleton: data wiring lands with the reader ViewModel (core:quran table);
 * uniform placeholders only (no letter/length hints per the hard rules).
 */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun ReaderScreen(
    surahNames: List<Pair<Int, String>>,
    onSurahSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "اقرأ",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(surahNames) { (num, name) ->
                Text(
                    text = "$num. $name",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}
