package com.iqraa.feature.history

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

/** F5 History — local list with per-state counts; delete one/all; no audio stored. */
@Suppress("FunctionName") // Compose convention
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("السجل", style = MaterialTheme.typography.headlineMedium)
        LazyColumn {
            items(entries) { e ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge)
                    Text(e.counts, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

data class HistoryEntry(
    val title: String,
    val counts: String,
)
