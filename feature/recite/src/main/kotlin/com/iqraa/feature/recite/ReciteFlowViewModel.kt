package com.iqraa.feature.recite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iqraa.core.align.ExpectedWord
import com.iqraa.core.align.TasmeeSummary
import com.iqraa.core.align.TrackerConfig
import com.iqraa.core.align.TrackerUpdate
import com.iqraa.core.align.WordState
import com.iqraa.core.data.SessionRepository
import com.iqraa.core.data.db.SessionEntity
import com.iqraa.core.data.db.WordFlagEntity
import com.iqraa.core.data.settings.SettingsRepository
import com.iqraa.core.quran.ExpectedWordEntry
import com.iqraa.core.quran.ExpectedWords
import com.iqraa.core.quran.QuranText
import com.iqraa.core.session.LifecycleState
import com.iqraa.core.session.PauseReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * V-048 (issue #11): the recite flow's owner — the wiring Phase 3 never shipped (the
 * NavHost rendered `Text("recite")`). Prepares the REAL pipeline (expected words from the
 * committed Tanzil table → model load via [RecognizerProvider] → [RecitePipelineFactory]),
 * maps the §10.4 lifecycle + tracker snapshots to UI state, persists §11 checkpoints per
 * FINAL and the session row at END (§15), and exposes §11 model-load error semantics
 * (retry once → other bundled model).
 *
 * The §10.4 machine itself stays in [ReciteViewModel] (unmodified); this class is the
 * Hilt-injectable shell the NavHost route binds to via SavedStateHandle nav args.
 */
sealed interface ReciteUiState {
    data object Preparing : ReciteUiState

    /** §11: model load failed — retry once, then try the other bundled model. */
    data class ModelError(
        val cause: Throwable,
        val triedOtherModel: Boolean,
    ) : ReciteUiState

    data class Session(
        val tasmeeMode: Boolean,
        val lifecycle: LifecycleState,
        val words: List<String>,
        val states: List<WordState>,
        val micError: Boolean,
        val tinyModel: Boolean,
    ) : ReciteUiState

    data class Ended(
        val summary: TasmeeSummary,
        val sessionId: Long,
    ) : ReciteUiState
}

/** Supplies the capture source for a session — real mic in the app, fixture PCM in tests. */
interface ChunkSourceProvider {
    fun newSource(): ChunkSource
}

/** The app's mic-backed provider (tests construct the VM directly with a fixture source). */
class MicChunkSourceProvider : ChunkSourceProvider {
    override fun newSource(): ChunkSource = MicChunkSource()
}

@HiltViewModel
class ReciteFlowViewModel
    @Inject
    constructor(
        private val recognizerProvider: RecognizerProvider,
        private val pipelineFactory: RecitePipelineFactory,
        private val settings: SettingsRepository,
        private val sessions: SessionRepository,
        private val chunkSources: ChunkSourceProvider,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val surah: Int = savedStateHandle.get<Int>("surah") ?: DEFAULT_SURAH
        val fromAyah: Int = savedStateHandle.get<Int>("from") ?: DEFAULT_AYAH
        val toAyah: Int = savedStateHandle.get<Int>("to") ?: DEFAULT_AYAH
        val tasmeeMode: Boolean = savedStateHandle.get<String>("mode") == MODE_TASMEE

        private val _state = MutableStateFlow<ReciteUiState>(ReciteUiState.Preparing)
        val state: StateFlow<ReciteUiState> = _state.asStateFlow()

        private var pipeline: RecitePipelineFactory.RecitePipeline? = null
        private var recite: ReciteViewModel? = null
        private var expected: List<ExpectedWord> = emptyList()

        /** §7.4 Uthmani display source — the SAME entries list [expected] is built from
         *  (single source of truth: `expectedEntries[i].display` is the display of
         *  `expected[i]`; asserted in [deriveDisplayWords] on every read). */
        private var expectedEntries: List<ExpectedWordEntry> = emptyList()
        private var modelIdInUse = RecognizerProvider.SMALL_ID
        private var modelRevInUse = ""
        private var presetInUse = SettingsRepository.PRESET_CONSERVATIVE
        private var configHashInUse = ""
        private var triedOtherModel = false
        private var sessionId = 0L
        private var startedAt = 0L
        private var checkpointJob: Job? = null

        init {
            prepare()
        }

        /** Builds the real pipeline; a model-load failure maps to the §11 error card. */
        private fun prepare(modelOverride: String? = null) {
            _state.value = ReciteUiState.Preparing
            viewModelScope.launch {
                try {
                    presetInUse = settings.presetNow()
                    modelIdInUse =
                        modelOverride
                            ?: settings.modelOverrideNow().ifEmpty { RecognizerProvider.SMALL_ID }
                    val recognizer = recognizerProvider.get(modelIdInUse)
                    modelRevInUse = recognizer.info.revision
                    expected = expectedWords()
                    val config =
                        TrackerConfig(
                            preset =
                                if (presetInUse == SettingsRepository.PRESET_BALANCED) {
                                    TrackerConfig.Preset.BALANCED
                                } else {
                                    TrackerConfig.Preset.CONSERVATIVE
                                },
                        )
                    configHashInUse = config.hashCode().toString()
                    val (p, vm) =
                        pipelineFactory.build(
                            recognizer = recognizer,
                            expected = expected,
                            config = config,
                            chunkSource = chunkSources.newSource(),
                        )
                    pipeline = p
                    recite = vm
                    startedAt = System.currentTimeMillis()
                    observeSession(p, vm)
                    _state.value =
                        ReciteUiState.Session(
                            tasmeeMode = tasmeeMode,
                            lifecycle = vm.state.value.lifecycle,
                            words = displayWords(),
                            states = List(expected.size) { WordState.PENDING },
                            micError = false,
                            tinyModel = modelIdInUse == RecognizerProvider.TINY_ID,
                        )
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    _state.value = ReciteUiState.ModelError(t, triedOtherModel)
                }
            }
        }

        /** §10.4: start (or resume after pause) the capture loop. */
        fun start() {
            recite?.start()
        }

        fun pause(reason: PauseReason) {
            recite?.pause(reason)
        }

        /** §10.3: the tasmee' hint (HINTED terminal). */
        fun hint() {
            pipeline?.session?.hint()
        }

        /** §10.6 resync: re-anchor the tracker to the picked ayah. */
        fun resync(ayahIndex: Int) {
            recite?.resync(ayahIndex)
        }

        /** §11: retry the model load. */
        fun retryModel() {
            prepare()
        }

        /** §11: switch to the other bundled model. */
        fun tryOtherModel() {
            triedOtherModel = true
            val other =
                if (modelIdInUse == RecognizerProvider.SMALL_ID) {
                    RecognizerProvider.TINY_ID
                } else {
                    RecognizerProvider.SMALL_ID
                }
            prepare(other)
        }

        /** Ends the session: stop capture, write the §15 history row, emit the §9.6 summary. */
        fun endSession() {
            val p = pipeline ?: return
            recite?.stop()
            checkpointJob?.cancel()
            viewModelScope.launch {
                val summary = p.session.summary()
                sessionId = sessions.checkpoint(sessionEntity(ended = true, summary), flagsFor(p.session.updates.value))
                _state.value = ReciteUiState.Ended(summary, sessionId)
            }
        }

        private fun observeSession(
            p: RecitePipelineFactory.RecitePipeline,
            vm: ReciteViewModel,
        ) {
            viewModelScope.launch {
                vm.state.collect { s ->
                    val current = _state.value
                    if (current is ReciteUiState.Session) {
                        _state.value = current.copy(lifecycle = s.lifecycle, micError = s.micError)
                    }
                }
            }
            viewModelScope.launch {
                p.session.updates.collect { u ->
                    val current = _state.value
                    if (current is ReciteUiState.Session) {
                        // tasmee' placeholders reveal as words reach CORRECT/CLOSE (§9.6)
                        _state.value =
                            current.copy(
                                states = renderStates(expected.size, u),
                                words = displayWords(),
                            )
                    }
                }
            }
            // §11: checkpoint after each FINAL observation (cheap, transactional, no audio/text)
            checkpointJob =
                viewModelScope.launch {
                    p.session.checkpointEvents.collect {
                        val id =
                            sessions.checkpoint(
                                sessionEntity(ended = false, summary = p.session.summary()),
                                flagsFor(p.session.updates.value),
                            )
                        if (sessionId == 0L) sessionId = id
                    }
                }
        }

        /**
         * Display words for the range (Uthmani). In tasmee' mode unrevealed words (PENDING)
         * render as uniform neutral placeholders — no letter/length hints (§10.3).
         */
        private fun displayWords(): List<String> =
            deriveDisplayWords(
                entries = expectedEntries,
                expected = expected,
                tasmeeMode = tasmeeMode,
                revealed = ::revealed,
                placeholder = PLACEHOLDER,
            )

        private fun revealed(e: ExpectedWord): Boolean {
            val update = pipeline?.session?.updates?.value ?: return false
            val s = update.states[e.index] ?: WordState.PENDING
            // §9.6: hidden words revealed on CORRECT or CLOSE (lenient reveal, honest scoring)
            return s == WordState.CORRECT || s == WordState.CLOSE
        }

        private fun renderStates(
            expectedCount: Int,
            update: TrackerUpdate?,
        ): List<WordState> {
            if (update == null) return List(expectedCount) { WordState.PENDING }
            return List(expectedCount) { i -> update.states[i] ?: WordState.PENDING }
        }

        private suspend fun expectedWords(): List<ExpectedWord> =
            withContext(Dispatchers.Default) {
                val ayahs = QuranText.load().filter { it.surah == surah && it.ayah in fromAyah..toAyah }
                val entries = ExpectedWords.forRange(ayahs, surah, fromAyah, toAyah)
                expectedEntries = entries
                toExpectedWords(entries)
            }

        private fun sessionEntity(
            ended: Boolean,
            summary: TasmeeSummary,
        ): SessionEntity =
            SessionEntity(
                id = if (sessionId == 0L) 0L else sessionId,
                startedAt = startedAt,
                endedAt = if (ended) System.currentTimeMillis() else null,
                mode = if (tasmeeMode) MODE_TASMEE else MODE_FOLLOW,
                surah = surah,
                fromAyah = fromAyah,
                toAyah = toAyah,
                modelId = modelIdInUse,
                modelRev = modelRevInUse,
                preset = presetInUse,
                configHash = configHashInUse,
                degradedLevel = 0,
                countsCorrect = summary.correct,
                countsClose = summary.close,
                countsWrong = summary.wrong,
                countsMissed = summary.missed,
                countsHinted = summary.hinted,
                countsExtra = summary.extra,
                durationMs = System.currentTimeMillis() - startedAt,
                deviceNote = null,
            )

        /** §9.6 flagged list = WRONG + MISSED words. */
        private fun flagsFor(update: TrackerUpdate?): List<WordFlagEntity> {
            if (update == null) return emptyList()
            return update.states
                .filterValues { it == WordState.WRONG || it == WordState.MISSED }
                .keys
                .map { idx ->
                    WordFlagEntity(
                        sessionId = sessionId,
                        globalWordIndex = idx,
                        surah = surah,
                        ayah = expected.getOrNull(idx)?.ayah ?: fromAyah,
                        state = update.states[idx]?.name ?: WordState.PENDING.name,
                    )
                }
        }

        private companion object {
            const val DEFAULT_SURAH = 1
            const val DEFAULT_AYAH = 1
            const val MODE_TASMEE = "tasmee"
            const val MODE_FOLLOW = "follow"

            /** §10.3: uniform neutral placeholder — no letter/length hints. */
            const val PLACEHOLDER = "•••"
        }
    }

/**
 * The tracker's §6.2 word list, built from the SAME [ExpectedWords.forRange] entries list
 * the display text comes from (single source of truth): `word.index` is the 0-based
 * position in that list, so `entries[i]` is everything `expected[i]` knows — including
 * the Uthmani `display` form that core:align's [ExpectedWord] deliberately does not carry.
 */
internal fun toExpectedWords(entries: List<ExpectedWordEntry>): List<ExpectedWord> =
    entries.mapIndexed { i, e -> ExpectedWord(i, e.surah, e.ayah, e.text, e.optionalPrefix) }

/**
 * §10.3 display words (Uthmani) derived from the same entries list [expected] was built
 * from. Tasmee' mode reveals only words the caller's [revealed] predicate accepts (§9.6:
 * CORRECT/CLOSE); everything else renders as [placeholder] — no letter/length hints.
 *
 * Size and index agreement between the two lists is asserted on EVERY call: a mismatch is
 * a wiring bug and fails LOUDLY — never a silent fallback, because rendering a wrong
 * Quranic word (§7.1 verbatim text) is the one error this screen must not hide.
 */
internal fun deriveDisplayWords(
    entries: List<ExpectedWordEntry>,
    expected: List<ExpectedWord>,
    tasmeeMode: Boolean,
    revealed: (ExpectedWord) -> Boolean,
    placeholder: String,
): List<String> {
    check(entries.size == expected.size) {
        "display source desynchronized: ${expected.size} tracker words vs ${entries.size} Quran entries"
    }
    expected.forEachIndexed { i, w ->
        check(w.index == i) { "expected[$i].index=${w.index} — display source is no longer positional" }
    }
    return if (!tasmeeMode) {
        entries.map { it.display }
    } else {
        expected.map { w -> if (revealed(w)) entries[w.index].display else placeholder }
    }
}
