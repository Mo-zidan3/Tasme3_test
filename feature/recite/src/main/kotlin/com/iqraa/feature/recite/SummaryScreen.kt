package com.iqraa.feature.recite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iqraa.core.align.WordState
import com.iqraa.core.designsystem.FeedbackEncoding

/**
 * T8: the §9.6 Summary — «صحيحة: X من Y» headline (X = CORRECT only; Y = expected −
 * HINTED − optional basmala, computed by the caller), CLOSE reported separately,
 * per-state counts, and the TAPPABLE flagged-words list (§3 F5, §15 WordFlagEntity rows).
 * No audio, no transcripts — counts and flags only.
 */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun SummaryScreen(
    correct: Int,
    y: Int,
    close: Int,
    wrong: Int,
    missed: Int,
    hinted: Int,
    extra: Int,
    flaggedWords: List<FlaggedWord>,
    onWordTapped: (FlaggedWord) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "صحيحة: $correct من $y",
            style = MaterialTheme.typography.headlineMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.summary_close_fmt, close), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.summary_missed_fmt, missed), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.summary_wrong_fmt, wrong), style = MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.summary_hinted_fmt, hinted), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.summary_extra_fmt, extra), style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider()
        Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(flaggedWords) { w ->
                val enc = w.encoding()
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onWordTapped(w) },
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${w.surah}:${w.ayah} — ${w.text}")
                        Text(
                            text = "${enc.glyph} ${enc.labelAr}",
                            color = enc.color,
                        )
                    }
                }
            }
        }
    }
}

/** One tappable flagged word (mirrors §15 WordFlagEntity). */
data class FlaggedWord(
    val surah: Int,
    val ayah: Int,
    val text: String,
    val state: WordState,
)

/** §10.2 encoding for a flagged word — color + glyph + label, never color-only. */
internal fun FlaggedWord.encoding(): FeedbackEncoding =
    when (state) {
        WordState.CORRECT -> FeedbackEncoding.CORRECT
        WordState.CLOSE -> FeedbackEncoding.CLOSE
        WordState.WRONG -> FeedbackEncoding.WRONG
        WordState.MISSED -> FeedbackEncoding.MISSED
        WordState.HINTED -> FeedbackEncoding.HINTED
        WordState.EXTRA -> FeedbackEncoding.CLOSE
        WordState.PENDING -> FeedbackEncoding.MISSED
    }
