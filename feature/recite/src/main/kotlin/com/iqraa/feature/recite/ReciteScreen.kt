package com.iqraa.feature.recite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iqraa.core.designsystem.FeedbackEncoding
import com.iqraa.core.designsystem.QuranText

/**
 * F2/F3 Recite — follow-along + tasmee' (§10.3): auto-scroll follows the pointer word;
 * manual scroll pauses it 5 s with a «العودة للمتابعة» chip; the hint button exists ONLY
 * in tasmee' mode. §10.2 rendering via QuranText (never color-only).
 */
@Suppress("FunctionName") // Compose convention
@Composable
fun ReciteScreen(
    words: List<String>,
    states: List<FeedbackEncoding?>,
    isTasmeeMode: Boolean,
    pausedBanner: String?,
    onResume: () -> Unit,
    onHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var autoScrollPaused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        pausedBanner?.let { banner ->
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(banner, style = MaterialTheme.typography.titleMedium)
            }
        }
        // §10.3: manual-scroll pause → «العودة للمتابعة» chip for 5 s, tap resumes immediately.
        if (autoScrollPaused) {
            AssistChip(
                onClick = { autoScrollPaused = false },
                label = { Text("العودة للمتابعة") },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        QuranText(
            words = words,
            states = states,
            modifier =
                Modifier
                    .fillMaxSize()
                    .weight(1f),
        )
        if (isTasmeeMode) {
            // §10.2: hint button ONLY in tasmee' mode
            AssistChip(
                onClick = onHint,
                label = { Text("تلميح") },
            )
        }
    }
    // 5 s auto-resume of auto-follow (§10.3); no-op while user interacts
    androidx.compose.runtime.LaunchedEffect(autoScrollPaused) {
        if (autoScrollPaused) {
            delay(5000)
            autoScrollPaused = false
        }
    }
}
