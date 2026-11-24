package com.iqraa.core.align

import com.iqraa.core.align.model.AsrWord
import com.iqraa.core.align.model.Observation

/** Per-expected-word evidence accumulated across observations (§9.3). */
internal data class WordEvidence(
    val expectedIndex: Int,
    val bestSim: Double = 0.0,
    val bestInteriorSim: Double = 0.0, // best sim among interior observations
    val interiorIndependentCount: Int = 0, // # INDEPENDENT observations with interior evidence
    val interiorFinalCount: Int = 0, // # FINAL interior observations (Balanced preset)
    val absenceIndependentCount: Int = 0, // # INDEPENDENT observations that SKIPPED the word (MISSED-only)
    val seen: Boolean = false,
)

/** Implementation of the normative RecitationTracker (§9). */
class RecitationTrackerImpl : RecitationTracker {
    private var expected: List<ExpectedWord> = emptyList()
    private var config: TrackerConfig = TrackerConfig()
    private val states = HashMap<Int, WordState>()
    private val evidence = HashMap<Int, WordEvidence>()
    private val seenObservationIds = HashSet<String>()
    private val observations = ArrayList<Observation>()
    private var pointer: Int = 0
    private val extras = LinkedHashMap<Pair<String, Long>, AsrWord>() // EXTRA dedup (text, startMs/40)

    /** (text, startMs/40) keys that were MATCHED (not extra) — re-observed matched audio is
     *  never EXTRA (V-031: partials legitimately re-cover audio the pointer passed). */
    private val matchedPositions = HashSet<Pair<String, Long>>()
    private var lostSpeechMs: Long = 0
    private var lastAlignedEndMs: Long = 0
    private var lastWasUsable: Boolean = false

    /**
     * A1 time-anchored window (V-041): expected index -> session ms of its FIRST matched
     * audio (Match/Merge startMs). A FINAL's alignment window starts at the last anchor at
     * or before the window start, minus backWords — segment-early words stay reachable (I1)
     * while refrain copies far behind the recitation are excluded from the candidate set.
     */
    private val anchors = HashMap<Int, Long>()

    /**
     * A2 per-word independence (V-041): for each expected word, the observation windows
     * already COUNTED as independent votes for that word. A new vote counts only if it is
     * independent of EVERY window in the word's list (the old observation-level
     * `any { independent(...) }` accepted a vote independent of ANY previous window, so
     * mutually-overlapping partials could each re-vote on the same word).
     */
    private val countedWindows = HashMap<Int, MutableList<Observation>>()

    override fun reset(
        expected: List<ExpectedWord>,
        config: TrackerConfig,
    ) {
        this.expected = expected
        this.config = config
        states.clear()
        evidence.clear()
        seenObservationIds.clear()
        observations.clear()
        pointer = 0
        extras.clear()
        matchedPositions.clear()
        lostSpeechMs = 0
        lastAlignedEndMs = 0
        lastWasUsable = false
        anchors.clear()
        countedWindows.clear()
        expected.forEachIndexed { i, _ -> states[i] = WordState.PENDING }
    }

    override fun onObservation(obs: Observation): TrackerUpdate {
        // §9.2 idempotency: identical (windowStartMs, windowEndMs, isFinal, modelRev) may only
        // improve evidence — dedup by identity, never re-vote.
        val id = obs.identity()
        if (id in seenObservationIds) return snapshot()
        seenObservationIds.add(id)

        observations.add(obs)

        val windowUsable = obs.words.isNotEmpty()
        if (windowUsable) {
            val (alignment, ok) = alignWindow(obs)
            if (ok && alignment != null) {
                lastWasUsable = true
                lastAlignedEndMs = obs.windowEndMs
                lostSpeechMs = 0
                recordEvidence(obs, alignment, absenceAllowed = true)
            } else {
                // speech with no matched expected word: record EXTRA stats (§9.4 repeats/ta'awwudh),
                // then feed the lost timer (no usable alignment).
                if (alignment != null) recordExtrasOnly(obs, alignment)
                advanceLostTimer(obs, hadSpeech = true)
            }
        } else {
            // empty decode during speech window: no usable alignment
            advanceLostTimer(obs, hadSpeech = obs.windowEndMs > lastAlignedEndMs)
        }
        recomputeStates()
        return snapshot()
    }

    /** Last expected index anchored (first-matched) at or before [ws]; −1 when none. */
    private fun lastAnchorIdxAtOrBefore(ws: Long): Int = anchors.entries.filter { it.value <= ws }.maxOfOrNull { it.key } ?: -1

    /**
     * Aligns obs to the expected window; returns null when unusable.
     * FINALS (V-025/I1) must reach segment-early words, so their window is TIME-ANCHORED
     * (A1/V-041): lo = last anchor at/before the window start − backWords (0 when no anchor
     * precedes the window — the whole-segment/session-start case). Partials keep the
     * pointer-based back-window (§9.4). Equal-score alignments tie-break FORWARD-nearest
     * to the pointer (A1b) — never the earliest occurrence (refrain false-alarm fix). */
    private fun alignWindow(obs: Observation): Pair<Alignment?, Boolean> {
        if (obs.words.isEmpty() || expected.isEmpty()) return null to false
        val lo =
            if (obs.isFinal) {
                (lastAnchorIdxAtOrBefore(obs.windowStartMs) - config.backWords).coerceAtLeast(0)
            } else {
                (pointer - config.backWords).coerceAtLeast(0)
            }
        val hi = (pointer + config.lookaheadWords).coerceAtMost(expected.size - 1)
        if (lo > hi) return null to false
        val alignment = Aligner.align(obs.words, expected, lo, hi, config, pointerHint = pointer)
        val matchedCount = alignment.moves.count { it is Move.Match || it is Move.Merge }
        // unusable if essentially nothing matched — but the alignment (with EXTRA moves) is still
        // returned so repeats/ta'awwudh can be recorded as EXTRA stats (§9.4).
        val ok = matchedCount > 0
        if (!ok) return alignment to false
        // advance pointer beyond the highest matched expected index (monotonic §9.4)
        val maxIdx =
            alignment.moves.maxOfOrNull {
                when (it) {
                    is Move.Match -> it.expectedIndex
                    is Move.Merge -> it.expectedIndex
                    is Move.Extra -> -1
                }
            } ?: pointer
        if (maxIdx >= pointer) pointer = (maxIdx + 1).coerceAtMost(expected.size)
        return alignment to true
    }

    /** §9.2 interiority (timed mode): word fully inside window minus boundary. */
    private fun isInterior(
        w: AsrWord,
        obs: Observation,
    ): Boolean {
        val hasTiming = w.startMs != null && w.endMs != null
        return if (hasTiming) {
            w.startMs!! >= obs.windowStartMs + config.boundaryMs &&
                w.endMs!! <= obs.windowEndMs - config.boundaryMs
        } else {
            true // no timing: interiority resolved later in token-index space
        }
    }

    /** §9.2 null-timing interiority in token-index space: exclude first/last kEdgeTokens of hypothesis. */
    private fun isInteriorByTokenIndex(
        observedIndex: Int,
        wordCount: Int,
    ): Boolean {
        if (config.boundaryMs > 0 && hasAnyTiming()) return true // timed mode preferred
        return observedIndex >= config.kEdgeTokens && observedIndex < wordCount - config.kEdgeTokens
    }

    private fun hasAnyTiming(): Boolean = observations.any { o -> o.words.any { it.startMs != null } }

    /**
     * A2 (V-041): a vote for [idx] counts as independent only if [obs] is independent of
     * EVERY window already counted for that word; counted windows are kept per word.
     */
    private fun countsAsIndependent(
        idx: Int,
        obs: Observation,
    ): Boolean {
        val list = countedWindows.getOrPut(idx) { mutableListOf() }
        val ok = list.all { independent(it, obs, config.strictOverlapIndependence) }
        if (ok) list.add(obs)
        return ok
    }

    private fun recordEvidence(
        obs: Observation,
        alignment: Alignment,
        absenceAllowed: Boolean,
    ) {
        val wordCount = obs.words.size
        val matchedIndices =
            alignment.moves.mapNotNull {
                when (it) {
                    is Move.Match -> it.expectedIndex
                    is Move.Merge -> it.expectedIndex
                    is Move.Extra -> null
                }
            }
        if (matchedIndices.isNotEmpty()) {
            val spanMin = matchedIndices.min()
            val spanMax = matchedIndices.max()
            // record A1 anchors FIRST (first-match only, timed moves) so later lookups see them
            for (move in alignment.moves) {
                when (move) {
                    is Move.Match -> {
                        if (move.expectedIndex !in anchors && move.observed.startMs != null) {
                            anchors[move.expectedIndex] = move.observed.startMs!!
                        }
                        if (move.observed.startMs != null) {
                            matchedPositions.add(move.observed.text to (move.observed.startMs!! / 40))
                        }
                    }

                    is Move.Merge -> {
                        if (move.expectedIndex !in anchors && move.first.startMs != null) {
                            anchors[move.expectedIndex] = move.first.startMs!!
                        }
                    }

                    else -> {}
                }
            }
            // §9.3 MISSED evidence — interior skips: expected words strictly between matched
            // span words are bracketed by matched audio (their slot is inside the window).
            for (k in spanMin..spanMax) {
                if (k in matchedIndices) continue
                if (!absenceAllowed) continue
                if (isSettled(k) || expected[k].optionalPrefix) continue
                addAbsence(k, obs)
            }
            // A1d leading skips (k < spanMin, unmatched). A skipped word has no audio of its
            // own — a vote is justified only when this window plausibly covered the word's
            // EXPECTED slot:
            //  - ANCHORED word (matched before, known audio position): vote only if that
            //    position lies inside this window — absence over re-covered audio is real
            //    evidence; absence over audio the window never contained is not.
            //  - NEVER-ANCHORED word: the recitation has provably advanced past it (spanMin
            //    matched NOW) and it was never heard anywhere — the session-progress channel
            //    (V-031 segment-initial skips). §9.3's pointer≥3 + ≥2-independent gates
            //    protect against boundary artifacts at state level.
            // Settled (CORRECT/HINTED) and optional-prefix words never receive absence.
            if (absenceAllowed) {
                for (k in 0 until spanMin) {
                    if (k in matchedIndices) continue
                    if (isSettled(k) || expected[k].optionalPrefix) continue
                    val anchoredSlot = anchors[k]
                    if (anchoredSlot != null) {
                        if (anchoredSlot >= obs.windowStartMs && anchoredSlot <= obs.windowEndMs) {
                            addAbsence(k, obs)
                        }
                    } else {
                        addAbsence(k, obs)
                    }
                }
            }
        }
        for (move in alignment.moves) {
            when (move) {
                is Move.Match -> {
                    val interior =
                        if (move.observed.startMs != null) {
                            isInterior(move.observed, obs)
                        } else {
                            val oi = obs.words.indexOf(move.observed)
                            isInteriorByTokenIndex(oi, wordCount)
                        }
                    addEvidence(
                        move.expectedIndex,
                        move.sim,
                        interior && (obs.isFinal || !isAtNaturalCutBoundary(move.observed, obs)),
                        obs.isFinal,
                        obs,
                    )
                    if (!interior && obs.isFinal && isAtNaturalCutBoundary(move.observed, obs)) {
                        // natural pause-final: boundary exclusion does NOT apply (§9.2)
                        addEvidence(move.expectedIndex, move.sim, true, obs.isFinal, obs)
                    }
                }

                is Move.Merge -> {
                    val interior =
                        move.first.startMs?.let {
                            move.second.endMs?.let { e ->
                                it >= obs.windowStartMs + config.boundaryMs && e <= obs.windowEndMs - config.boundaryMs
                            }
                        } ?: true
                    addEvidence(move.expectedIndex, move.sim, interior, obs.isFinal, obs)
                }

                is Move.Extra -> {
                    // §9.4 EXTRA dedup (V-031): key = (text, wordStartMs/40) when timing exists —
                    // the same spoken word has the SAME session-global startMs in every window
                    // that contains it (partials re-observe identical audio), so overlapping
                    // partials collapse to ONE entry. Repeats at a NEW audio position get a new
                    // key. Fallback without timing: (text, windowEndMs) — one per observation.
                    // A word whose audio position was EVER matched is re-covered matched audio,
                    // not an EXTRA — suppressed (V-031).
                    if (move.observed.startMs != null) {
                        val key = move.observed.text to (move.observed.startMs!! / 40)
                        if (key !in matchedPositions && key !in extras) extras[key] = move.observed
                    } else {
                        val key = move.observed.text to obs.windowEndMs
                        if (key !in extras) extras[key] = move.observed
                    }
                }
            }
        }
    }

    /** CORRECT and HINTED are settled states — absence votes against them are pointless. */
    private fun isSettled(k: Int): Boolean = (states[k] == WordState.CORRECT || states[k] == WordState.HINTED)

    private fun addAbsence(
        k: Int,
        obs: Observation,
    ) {
        addEvidence(k, 0.0, interior = false, isFinal = obs.isFinal, obs, absence = true)
    }

    /** §9.4: EXTRA stats are recorded even when nothing matched (repeats behind window, ta'awwudh). */
    private fun recordExtrasOnly(
        obs: Observation,
        alignment: Alignment,
    ) {
        for (move in alignment.moves) {
            if (move is Move.Extra) {
                if (move.observed.startMs != null) {
                    val key = move.observed.text to (move.observed.startMs!! / 40)
                    if (key !in extras) extras[key] = move.observed
                } else {
                    val key = move.observed.text to obs.windowEndMs
                    if (key !in extras) extras[key] = move.observed
                }
            }
        }
    }

    private fun isAtNaturalCutBoundary(
        w: AsrWord,
        obs: Observation,
    ): Boolean {
        // natural pause-final: window end is speech end (no forced cut marker in Observation; a
        // pause-final is the only final without timing cut characteristics — approximate: any final)
        return obs.isFinal
    }

    private fun addEvidence(
        idx: Int,
        s: Double,
        interior: Boolean,
        isFinal: Boolean,
        obs: Observation,
        absence: Boolean = false,
    ) {
        val e = evidence.getOrPut(idx) { WordEvidence(idx) }
        // A2: independence is PER WORD — a vote counts only when this window is independent
        // of every window ALREADY COUNTED for that word; only windows whose vote counted
        // enter the list (non-interior / non-independent additions never pollute it).
        val updated =
            if (absence) {
                // Absence evidence (§9.3 MISSED only): never feeds WRONG (a skipped word is not a misrecitation).
                val counted = countsAsIndependent(idx, obs)
                e.copy(
                    seen = true,
                    absenceIndependentCount = e.absenceIndependentCount + (if (counted) 1 else 0),
                )
            } else {
                val counted = if (interior) countsAsIndependent(idx, obs) else false
                e.copy(
                    seen = true,
                    bestSim = maxOf(e.bestSim, s),
                    bestInteriorSim = if (interior) maxOf(e.bestInteriorSim, s) else e.bestInteriorSim,
                    interiorIndependentCount = e.interiorIndependentCount + (if (interior && counted) 1 else 0),
                    interiorFinalCount = e.interiorFinalCount + (if (interior && isFinal) 1 else 0),
                )
            }
        evidence[idx] = updated
    }

    /** §9.3 precedence machine, applied over ALL evidence. */
    private fun recomputeStates() {
        for (i in expected.indices) {
            val current = states[i] ?: WordState.PENDING
            if (current == WordState.HINTED) continue // terminal
            val optional = expected[i].optionalPrefix
            val e = evidence[i]
            val next =
                when {
                    e != null && e.bestInteriorSim >= config.tauCorrect -> WordState.CORRECT

                    current == WordState.CORRECT -> WordState.CORRECT

                    // never downgraded
                    // §7.6 optional-prefix words (basmala): absorbing is the only "positive" path;
                    // omitting them is NEVER penalized → no MISSED/WRONG, only PENDING (or CORRECT).
                    optional -> WordState.PENDING

                    e != null && isMissedConfirmed(i, e) -> WordState.MISSED

                    e != null && isWrongConfirmed(i, e) -> WordState.WRONG

                    e != null && e.seen -> WordState.CLOSE

                    else -> WordState.PENDING
                }
            // self-correction: MISSED/WRONG/CLOSE → CORRECT handled by the CORRECT branch above
            if (next == WordState.PENDING && current != WordState.PENDING && e == null) {
                states[i] = current
            } else {
                states[i] = next
            }
        }
        // MISSED confirmation additionally requires pointer ≥3 beyond (§9.3 rule 3);
        // optional-prefix words are never MISSED (§7.6).
        for (i in expected.indices) {
            if (states[i] == WordState.MISSED && (pointer < i + 3 || expected[i].optionalPrefix)) {
                states[i] = if (expected[i].optionalPrefix) WordState.PENDING else WordState.CLOSE
            }
        }
    }

    private fun isMissedConfirmed(
        i: Int,
        e: WordEvidence,
    ): Boolean {
        // §9.3 rule 3: skipped by alignment AND pointer ≥3 beyond AND confirmed in ≥2 independent
        // observations (absence evidence), and no real match evidence above tauWrong.
        return pointer >= i + 3 &&
            e.absenceIndependentCount >= 2 &&
            e.bestInteriorSim < config.tauWrong
    }

    private fun isWrongConfirmed(
        i: Int,
        e: WordEvidence,
    ): Boolean =
        when (config.preset) {
            TrackerConfig.Preset.CONSERVATIVE -> {
                e.interiorIndependentCount >= 2 && e.bestInteriorSim < config.tauWrong
            }

            TrackerConfig.Preset.BALANCED -> {
                e.interiorFinalCount >= 1 && e.bestSim < config.tauWrong
            }
        }

    private fun advanceLostTimer(
        obs: Observation,
        hadSpeech: Boolean,
    ) {
        // §9.4: lost timer advances ONLY while VAD reports speech with no usable alignment.
        if (hadSpeech && !lastWasUsable) {
            lostSpeechMs += (obs.windowEndMs - maxOf(obs.windowStartMs, lastAlignedEndMs)).coerceAtLeast(0)
        }
        if (lostSpeechMs >= config.lostAfterMs) {
            // range-wide re-anchor search (§9.4): try to find minReanchorRun consecutive τ_correct matches
            tryReanchorRangeWide(obs)
        }
    }

    private fun tryReanchorRangeWide(obs: Observation) {
        if (obs.words.isEmpty() || expected.isEmpty()) return
        val alignment = Aligner.align(obs.words, expected, 0, expected.size - 1, config)
        val matches = alignment.moves.filterIsInstance<Move.Match>().sortedBy { it.expectedIndex }
        // V-044: a global re-anchor alignment may produce ZERO match moves (all
        // merges/extras — e.g. the E2E fixture stream, run 45336537914: crashes on
        // matches.last() below). No matches ⇒ no re-anchor possible.
        if (matches.isEmpty()) return
        var run = 1
        var bestRunStart = -1
        var bestRun = 0
        for (k in 1 until matches.size) {
            if (matches[k].expectedIndex == matches[k - 1].expectedIndex + 1 && matches[k].sim >= config.tauCorrect &&
                matches[k - 1].sim >= config.tauCorrect
            ) {
                run++
            } else {
                if (run > bestRun) {
                    bestRun = run
                    bestRunStart = matches[k - 1].expectedIndex - run + 1
                }
                run = 1
            }
        }
        if (run > bestRun) {
            bestRun = run
            bestRunStart = matches.last().expectedIndex - run + 1
        }
        if (bestRun >= config.minReanchorRun && bestRunStart >= 0) {
            // strong match: re-anchor pointer; words between old pointer and anchor stay MISSED candidates
            pointer = (bestRunStart + bestRun).coerceAtMost(expected.size)
            lostSpeechMs = 0
            lastWasUsable = true
            lastAlignedEndMs = obs.windowEndMs
            // A1c (V-041): relocalization emits POSITIVE evidence only — no absence votes
            // (the global lo=0 alignment over lost audio cannot justify MISSED/WRONG).
            recordEvidence(obs, alignment, absenceAllowed = false)
        }
    }

    override fun hint(): TrackerUpdate {
        if (expected.isEmpty()) return snapshot()
        val idx =
            (0 until expected.size).firstOrNull { (states[it] ?: WordState.PENDING) == WordState.PENDING }
                ?: return snapshot()
        states[idx] = WordState.HINTED // terminal (§9.3 priority 1)
        pointer = (idx + 1).coerceAtMost(expected.size)
        return snapshot()
    }

    override fun reanchor(ayahIndex: Int): TrackerUpdate {
        val idx = expected.indexOfFirst { it.ayah == ayahIndex }
        if (idx >= 0) {
            pointer = idx
            lostSpeechMs = 0
        }
        return snapshot()
    }

    /** Test introspection: raw evidence map (debug string form). */
    fun dumpEvidence(): String =
        evidence.entries.sortedBy { it.key }.joinToString { (k, v) ->
            "$k(best=${v.bestSim}, interiorBest=${v.bestInteriorSim}, indep=${v.interiorIndependentCount}, finals=${v.interiorFinalCount}, seen=${v.seen})"
        }

    /** Test hooks (V-041/A2): per-word independent counts for stream-level assertions. */
    fun interiorIndependentCount(idx: Int): Int = evidence[idx]?.interiorIndependentCount ?: 0

    fun absenceIndependentCount(idx: Int): Int = evidence[idx]?.absenceIndependentCount ?: 0

    /** Test hook (V-041/A1): anchor map size / anchored expected index at-or-before [ws]. */
    fun anchorCount(): Int = anchors.size

    fun anchoredIdxAtOrBefore(ws: Long): Int = lastAnchorIdxAtOrBefore(ws)

    /** Test hook: current pointer (§9.4 monotonic). */
    fun pointerPublic(): Int = pointer

    fun snapshot(): TrackerUpdate = TrackerUpdate(states = states.toMap(), pointer = pointer, extras = extras.values.toList())

    /** Tasmee' summary (§9.6): X = CORRECT only; Y = expected − HINTED − optional-prefix;
     *  CLOSE separate. Optional-prefix words (§7.6) never count in Y. */
    fun summary(): TasmeeSummary {
        var correct = 0
        var close = 0
        var wrong = 0
        var missed = 0
        var hinted = 0
        var optional = 0
        for ((i, s) in states) {
            when (s) {
                WordState.CORRECT -> {
                    correct++
                }

                WordState.CLOSE -> {
                    close++
                }

                WordState.WRONG -> {
                    wrong++
                }

                WordState.MISSED -> {
                    missed++
                }

                WordState.HINTED -> {
                    hinted++
                }

                WordState.PENDING -> {
                    if (expected.getOrNull(i)?.optionalPrefix == true) optional++
                }

                WordState.EXTRA -> {}
            }
        }
        val y = states.size - hinted - optional
        return TasmeeSummary(correct = correct, y = y, close = close, wrong = wrong, missed = missed, hinted = hinted, extra = extras.size)
    }
}

/** §9.6 summary semantics. */
data class TasmeeSummary(
    val correct: Int,
    val y: Int,
    val close: Int,
    val wrong: Int,
    val missed: Int,
    val hinted: Int,
    val extra: Int,
) {
    /** «صحيحة: X من Y» */
    val headline: String get() = "صحيحة: $correct من $y"
}
