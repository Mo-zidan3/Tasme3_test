package com.iqraa.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The reader's state holder — the ViewModel Phase 3 lacked when MainActivity rendered the
 * screen with `surahNames = emptyList()` (issue #11 / V-048).
 *
 * Sealed state (§11): Loading → Loaded | Error. A source that returns an EMPTY list is an
 * Error — never a Loaded(empty) success (the shipped defect class).
 */
sealed interface ReaderState {
    data object Loading : ReaderState

    data class Loaded(
        val surahs: List<Surah>,
    ) : ReaderState

    data class Error(
        val cause: Throwable,
    ) : ReaderState
}

@HiltViewModel
class ReaderViewModel
    @Inject
    constructor(
        private val source: SurahListSource,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
        val state: StateFlow<ReaderState> = _state.asStateFlow()

        init {
            load()
        }

        /** §11 retry: re-run the load from the error card. */
        fun retry() {
            load()
        }

        private fun load() {
            _state.value = ReaderState.Loading
            viewModelScope.launch {
                _state.value =
                    try {
                        val surahs = source.load()
                        if (surahs.isEmpty()) {
                            ReaderState.Error(IllegalStateException("surah source returned an empty list"))
                        } else {
                            ReaderState.Loaded(surahs)
                        }
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        // corrupt/missing asset — honest error card, the app never crashes
                        ReaderState.Error(t)
                    }
            }
        }
    }
