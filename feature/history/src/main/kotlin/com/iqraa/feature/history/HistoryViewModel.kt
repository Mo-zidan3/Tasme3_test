package com.iqraa.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iqraa.core.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One history row for the screen (structured — the screen localizes via resources). */
data class HistoryUiModel(
    val id: Long,
    val surah: Int,
    val fromAyah: Int,
    val toAyah: Int,
    val correct: Int,
    val y: Int,
    val close: Int,
    val endedAt: Long?,
)

/**
 * V-048 (issue #11): the history's state holder — the §15 Room flow the NavHost never
 * wired (`Text("history")` placeholder before).
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
    ) : ViewModel() {
        val entries: StateFlow<List<HistoryUiModel>> =
            sessions
                .history()
                .map { list ->
                    list.map {
                        HistoryUiModel(
                            id = it.id,
                            surah = it.surah,
                            fromAyah = it.fromAyah,
                            toAyah = it.toAyah,
                            correct = it.countsCorrect,
                            y = it.countsCorrect + it.countsClose + it.countsWrong + it.countsMissed,
                            close = it.countsClose,
                            endedAt = it.endedAt,
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun deleteAll() {
            viewModelScope.launch { sessions.deleteAll() }
        }
    }
