package com.iqraa.feature.recite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iqraa.core.align.WordState
import com.iqraa.core.data.SessionRepository
import com.iqraa.core.data.db.WordFlagEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The persisted §9.6 summary as stored (§15) — counts only, no audio/transcripts. */
data class SummaryData(
    val correct: Int,
    val y: Int,
    val close: Int,
    val wrong: Int,
    val missed: Int,
    val hinted: Int,
    val extra: Int,
)

sealed interface SummaryUiState {
    data object Loading : SummaryUiState

    data class Loaded(
        val data: SummaryData,
        val flagged: List<FlaggedWord>,
    ) : SummaryUiState
}

/**
 * V-048 (issue #11): the summary screen's state — loads the persisted session row + word
 * flags from Room (which is also the proof the history write happened). The §9.6 flagged
 * rows (WRONG+MISSED) are tappable in the screen — the tap reveals the word's ayah and
 * state (minimal functioning "jumps to word"; the full text-jump stays Phase-4).
 */
@HiltViewModel
class SummaryViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

        private val _state = MutableStateFlow<SummaryUiState>(SummaryUiState.Loading)
        val state: StateFlow<SummaryUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val row = sessions.sessionById(sessionId)
                if (row == null) {
                    _state.value = SummaryUiState.Loaded(SummaryData(0, 0, 0, 0, 0, 0, 0), emptyList())
                } else {
                    val flags = sessions.wordFlagsFor(sessionId)
                    val y = row.countsCorrect + row.countsClose + row.countsWrong + row.countsMissed
                    _state.value =
                        SummaryUiState.Loaded(
                            SummaryData(
                                correct = row.countsCorrect,
                                y = y,
                                close = row.countsClose,
                                wrong = row.countsWrong,
                                missed = row.countsMissed,
                                hinted = row.countsHinted,
                                extra = row.countsExtra,
                            ),
                            flags.map { it.toFlaggedWord() },
                        )
                }
            }
        }

        private fun WordFlagEntity.toFlaggedWord(): FlaggedWord =
            FlaggedWord(
                surah = surah,
                ayah = ayah,
                text = "",
                state = WordState.valueOf(state),
            )
    }
