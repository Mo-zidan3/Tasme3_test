package com.example.core.align

import com.example.core.align.model.AsrWord
import com.example.core.align.model.Observation

/** Per-expected-word evidence accumulated across observations (§9.3). */
internal data class WordEvidence(
    val expectedIndex: Int,
    val bestSim: Double = 0.0,
    val bestInteriorSim: Double = 0.0,       // best sim among interior observations
    val interiorIndependentCount: Int = 0,   // # INDEPENDENT observations with interior evidence
    val interiorFinalCount: Int = 0,          // # FINAL interior observations (Balanced preset)
    val absenceIndependentCount: Int = 0,    // # INDEPENDENT observations that SKIPPED the word (MISSED-only)
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
    private val extras = LinkedHashMap<Pair<String, Long>, AsrWord>() // EXTRA dedup key (text, 250ms bucket)
    private var lostSpeechMs: Long = 0
    private var lastAlignedEndMs: Long = 0
    private var lastWasUsable: Boolean = false

    override fun reset(expected: List<ExpectedWord>, config: TrackerConfig) {
        this.expected = expected
        this.config = config
        states.clear()
        evidence.clear()
        seenObservationIds.clear()
        observations.clear()
        pointer = 0
        extras.clear()
        lostSpeechMs = 0
        lastAlignedEndMs = 0
        lastWasUsable = false
        expected.forEachIndexed { i, _ -> states[i] = WordState.PENDING }
    }

    override fun onObservation(obs: Observation): TrackerUpdate {
        // §9.2 idempotency: identical (windowStartMs, windowEndMs, isFinal, modelRev) may only
        // improve evidence — dedup by identity, never re-vote.
        val id = obs.identity()
        if (id in seenObservationIds) return snapshot()
        seenObservationIds.add(id)

        val isIndependent = observations.any { independent(it, obs) } || observations.isEmpty()
        // keep only observations that can contribute independent votes
        observations.add(obs)

        val windowUsable = obs.words.isNotEmpty()
        if (windowUsable) {
            val (alignment, ok) = alignWindow(obs)
            if (ok && alignment != null) {
                lastWasUsable = true
                lastAlignedEndMs = obs.windowEndMs
                lostSpeechMs = 0
                recordEvidence(obs, alignment, isIndependent)
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

    /** Aligns obs to the pointer window; returns null when unusable.
     *  FINALS cover a whole segment: their audio window extends far behind the pointer, so they
     *  align with lo=0 (leading expected skips are free since V-021) — otherwise the segment's
     *  early words become "free leading OBSERVED" and never earn evidence from the final
     *  (root cause of the trailing-EXTRA / never-WRONG artifacts, V-025). */
    private fun alignWindow(obs: Observation): Pair<Alignment?, Boolean> {
        if (obs.words.isEmpty() || expected.isEmpty()) return null to false
        val lo = if (obs.isFinal) 0 else (pointer - config.backWords).coerceAtLeast(0)
        val hi = (pointer + config.lookaheadWords).coerceAtMost(expected.size - 1)
        val alignment = Aligner.align(obs.words, expected, lo, hi, config)
        val matchedCount = alignment.moves.count { it is Move.Match || it is Move.Merge }
        // unusable if essentially nothing matched — but the alignment (with EXTRA moves) is still
        // returned so repeats/ta'awwudh can be recorded as EXTRA stats (§9.4).
        val ok = matchedCount > 0
        if (!ok) return alignment to false
        // advance pointer beyond the highest matched expected index (monotonic §9.4)
        val maxIdx = alignment.moves.maxOfOrNull {
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
    private fun isInterior(w: AsrWord, obs: Observation): Boolean {
        val hasTiming = w.startMs != null && w.endMs != null
        return if (hasTiming) {
            w.startMs!! >= obs.windowStartMs + config.boundaryMs &&
                w.endMs!! <= obs.windowEndMs - config.boundaryMs
        } else {
            true // no timing: interiority resolved later in token-index space
        }
    }

    /** §9.2 null-timing interiority in token-index space: exclude first/last kEdgeTokens of hypothesis. */
    private fun isInteriorByTokenIndex(observedIndex: Int, wordCount: Int): Boolean {
        if (config.boundaryMs > 0 && hasAnyTiming()) return true // timed mode preferred
        return observedIndex >= config.kEdgeTokens && observedIndex < wordCount - config.kEdgeTokens
    }

    private fun hasAnyTiming(): Boolean =
        observations.any { o -> o.words.any { it.startMs != null } }

    private fun recordEvidence(obs: Observation, alignment: Alignment, isIndependent: Boolean) {
        val wordCount = obs.words.size
        // §9.3 MISSED evidence: expected words INSIDE the aligned span that were skipped get
        // absence-evidence (sim 0), interior by the same rule as matched words, so ≥2
        // independent observations can confirm MISSED.
        val matchedIndices = alignment.moves.mapNotNull {
            when (it) {
                is Move.Match -> it.expectedIndex
                is Move.Merge -> it.expectedIndex
                is Move.Extra -> null
            }
        }
        if (matchedIndices.isNotEmpty()) {
            val spanMin = matchedIndices.min()
            val spanMax = matchedIndices.max()
            for (k in spanMin..spanMax) {
                if (k in matchedIndices) continue
                addEvidence(k, 0.0, interior = isSpanInterior(k, spanMin, spanMax, wordCount, obs), obs.isFinal, isIndependent, absence = true)
            }
        }
        for (move in alignment.moves) {
            when (move) {
                is Move.Match -> {
                    val interior = if (move.observed.startMs != null) {
                        isInterior(move.observed, obs)
                    } else {
                        val oi = obs.words.indexOf(move.observed)
                        isInteriorByTokenIndex(oi, wordCount)
                    }
                    addEvidence(move.expectedIndex, move.sim, interior && (obs.isFinal || !isAtNaturalCutBoundary(move.observed, obs)), obs.isFinal, isIndependent)
                    if (!interior && obs.isFinal && isAtNaturalCutBoundary(move.observed, obs)) {
                        // natural pause-final: boundary exclusion does NOT apply (§9.2)
                        addEvidence(move.expectedIndex, move.sim, true, obs.isFinal, isIndependent)
                    }
                }
                is Move.Merge -> {
                    val interior = move.first.startMs?.let {
                        move.second.endMs?.let { e ->
                            it >= obs.windowStartMs + config.boundaryMs && e <= obs.windowEndMs - config.boundaryMs
                        }
                    } ?: true
                    addEvidence(move.expectedIndex, move.sim, interior, obs.isFinal, isIndependent)
                }
                is Move.Extra -> {
                    // §9.4 EXTRA dedup key (normalizedText, 250 ms bucket)
                    if (move.observed.startMs != null) {
                        val bucket = move.observed.startMs!! / 250
                        val key = move.observed.text to bucket
                        if (key !in extras) extras[key] = move.observed
                    } else {
                        extras[move.observed.text to (obs.windowEndMs / 250)] = move.observed
                    }
                }
            }
        }
    }

    /** §9.4: EXTRA stats are recorded even when nothing matched (repeats behind window, ta'awwudh). */
    private fun recordExtrasOnly(obs: Observation, alignment: Alignment) {
        for (move in alignment.moves) {
            if (move is Move.Extra) {
                if (move.observed.startMs != null) {
                    val key = move.observed.text to (move.observed.startMs!! / 250)
                    if (key !in extras) extras[key] = move.observed
                } else {
                    val key = move.observed.text to (obs.windowEndMs / 250)
                    if (key !in extras) extras[key] = move.observed
                }
            }
        }
    }

    /** A skipped expected word between two matched words is interior to the observation. */
    private fun isSpanInterior(k: Int, spanMin: Int, spanMax: Int, wordCount: Int, obs: Observation): Boolean =
        k > spanMin && k < spanMax

    private fun isAtNaturalCutBoundary(w: AsrWord, obs: Observation): Boolean {
        // natural pause-final: window end is speech end (no forced cut marker in Observation; a
        // pause-final is the only final without timing cut characteristics — approximate: any final)
        return obs.isFinal
    }

    private fun addEvidence(idx: Int, s: Double, interior: Boolean, isFinal: Boolean, independent: Boolean, absence: Boolean = false) {
        val e = evidence.getOrPut(idx) { WordEvidence(idx) }
        val updated = if (absence) {
            // Absence evidence (§9.3 MISSED only): never feeds WRONG (a skipped word is not a misrecitation).
            e.copy(
                seen = true,
                absenceIndependentCount = e.absenceIndependentCount + (if (independent) 1 else 0),
            )
        } else {
            e.copy(
                seen = true,
                bestSim = maxOf(e.bestSim, s),
                bestInteriorSim = if (interior) maxOf(e.bestInteriorSim, s) else e.bestInteriorSim,
                interiorIndependentCount = e.interiorIndependentCount + (if (interior && independent) 1 else 0),
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
            val next = when {
                e != null && e.bestInteriorSim >= config.tauCorrect -> WordState.CORRECT
                current == WordState.CORRECT -> WordState.CORRECT // never downgraded
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

    private fun isMissedConfirmed(i: Int, e: WordEvidence): Boolean {
        // §9.3 rule 3: skipped by alignment AND pointer ≥3 beyond AND confirmed in ≥2 independent
        // observations (absence evidence), and no real match evidence above tauWrong.
        return pointer >= i + 3 &&
            e.absenceIndependentCount >= 2 &&
            e.bestInteriorSim < config.tauWrong
    }

    private fun isWrongConfirmed(i: Int, e: WordEvidence): Boolean = when (config.preset) {
        TrackerConfig.Preset.CONSERVATIVE -> {
            e.interiorIndependentCount >= 2 && e.bestInteriorSim < config.tauWrong
        }
        TrackerConfig.Preset.BALANCED -> {
            e.interiorFinalCount >= 1 && e.bestSim < config.tauWrong
        }
    }

    private fun advanceLostTimer(obs: Observation, hadSpeech: Boolean) {
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
        var run = 1
        var bestRunStart = -1
        var bestRun = 0
        for (k in 1 until matches.size) {
            if (matches[k].expectedIndex == matches[k - 1].expectedIndex + 1 && matches[k].sim >= config.tauCorrect &&
                matches[k - 1].sim >= config.tauCorrect
            ) {
                run++
            } else {
                if (run > bestRun) { bestRun = run; bestRunStart = matches[k - 1].expectedIndex - run + 1 }
                run = 1
            }
        }
        if (run > bestRun) { bestRun = run; bestRunStart = matches.last().expectedIndex - run + 1 }
        if (bestRun >= config.minReanchorRun && bestRunStart >= 0) {
            // strong match: re-anchor pointer; words between old pointer and anchor stay MISSED candidates
            pointer = (bestRunStart + bestRun).coerceAtMost(expected.size)
            lostSpeechMs = 0
            lastWasUsable = true
            lastAlignedEndMs = obs.windowEndMs
            recordEvidence(obs, alignment, isIndependent = true)
        }
    }

    override fun hint(): TrackerUpdate {
        if (expected.isEmpty()) return snapshot()
        val idx = (0 until expected.size).firstOrNull { (states[it] ?: WordState.PENDING) == WordState.PENDING }
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
    fun dumpEvidence(): String = evidence.entries.sortedBy { it.key }.joinToString { (k, v) ->
        "$k(best=${v.bestSim}, interiorBest=${v.bestInteriorSim}, indep=${v.interiorIndependentCount}, finals=${v.interiorFinalCount}, seen=${v.seen})"
    }

    fun snapshot(): TrackerUpdate =
        TrackerUpdate(states = states.toMap(), pointer = pointer, extras = extras.values.toList())

    /** Tasmee' summary (§9.6): X = CORRECT only; Y = expected − HINTED − optional-prefix;
     *  CLOSE separate. Optional-prefix words (§7.6) never count in Y. */
    fun summary(): TasmeeSummary {
        var correct = 0; var close = 0; var wrong = 0; var missed = 0; var hinted = 0; var optional = 0
        for ((i, s) in states) {
            when (s) {
                WordState.CORRECT -> correct++
                WordState.CLOSE -> close++
                WordState.WRONG -> wrong++
                WordState.MISSED -> missed++
                WordState.HINTED -> hinted++
                WordState.PENDING -> if (expected.getOrNull(i)?.optionalPrefix == true) optional++
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
