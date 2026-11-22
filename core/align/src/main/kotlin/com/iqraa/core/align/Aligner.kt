package com.iqraa.core.align

import com.iqraa.core.align.model.AsrWord
import com.iqraa.core.align.model.Observation

/** Alignment move for one observed word (§9.1). */
sealed interface Move {
    /** observed word matched/substituted to expected index */
    data class Match(val expectedIndex: Int, val observed: AsrWord, val sim: Double) : Move

    /** 1↔2 merge: two observed words match one expected word (concatenation) */
    data class Merge(val expectedIndex: Int, val first: AsrWord, val second: AsrWord, val sim: Double) : Move

    /** 1→2 split is represented as Merge on the second pass; see Aligner. */
    data class Extra(val observed: AsrWord) : Move
}

/** Result of aligning an observation against the expected window. */
data class Alignment(
    val moves: List<Move>,
    val score: Double,
)

/** Similarity over characters in model space (§9.1). */
fun sim(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val d = levenshtein(a, b)
    return 1.0 - d.toDouble() / maxOf(a.length, b.length)
}

/** Levenshtein distance, small strings only (words). */
internal fun levenshtein(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    var prev = IntArray(n + 1) { it }
    var cur = IntArray(n + 1)
    for (i in 1..m) {
        cur[0] = i
        for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val t = prev; prev = cur; cur = t
    }
    return prev[n]
}

/**
 * Semi-global Needleman–Wunsch over the observed window (§9.1):
 * expected window [lo, hi]; leading/trailing observed speech is FREE.
 * Moves: match/substitute (cost 1−sim), skip-expected (gapMissed, MISSED candidate),
 * extra-observed (gapExtra, EXTRA), 1↔2 merge/split via observed-pair ↔ expected concat.
 */
object Aligner {

    fun align(
        observed: List<AsrWord>,
        expected: List<ExpectedWord>,
        lo: Int,
        hi: Int,
        config: TrackerConfig,
    ): Alignment {
        if (observed.isEmpty() || lo > hi) return Alignment(emptyList(), 0.0)
        val exp = expected.subList(lo, hi + 1).map { it.text }
        // DP over observed (i) × expected-in-window (j); free skip of leading/trailing OBSERVED.
        // states: (i, j) -> best score with last op ending at j
        val n = observed.size
        val m = exp.size
        // dp[i][j] = best score aligning observed[0,i) to expected[0..j] with expected j consumed as match/merge/skip
        val NEG = Double.NEGATIVE_INFINITY
        val dp = Array(n + 1) { DoubleArray(m + 1) { NEG } }
        val back = Array(n + 1) { arrayOfNulls<Op>(m + 1) }
        dp[0][0] = 0.0
        // free LEADING observed speech: skipping observed from the start costs nothing
        for (i in 0..n) dp[i][0] = 0.0
        // free LEADING expected skips (recitation begins mid-window / after the pointer back-window)
        for (j in 0..m) dp[0][j] = 0.0
        for (i in 1..n) {
            for (j in 1..m) {
                // 1:1 match/substitute
                val s = sim(observed[i - 1].text, exp[j - 1])
                val vMatch = (if (dp[i - 1][j - 1] != NEG) dp[i - 1][j - 1] + (s - (1.0 - s)) else NEG)
                // skip expected (MISSED candidate) — costs gapMissed
                val vSkip = dp[i][j - 1] - config.gapMissed
                // extra observed (EXTRA) — costs gapExtra (but leading/trailing free via dp[i][0]=0)
                val vExtra = if (dp[i - 1][j] != NEG) dp[i - 1][j] - config.gapExtra else NEG
                // 1↔2 merge: observed[i-2..i-1] concat ↔ expected j-1
                val vMerge = if (i >= 2 && dp[i - 2][j - 1] != NEG) {
                    val concat = observed[i - 2].text + observed[i - 1].text
                    val sm = sim(concat, exp[j - 1])
                    dp[i - 2][j - 1] + (sm - (1.0 - sm))
                } else NEG
                val best = maxOf(vMatch, vSkip, vExtra, vMerge)
                dp[i][j] = best
                back[i][j] = when (best) {
                    vMatch -> Op.MATCH
                    vSkip -> Op.SKIP_EXPECTED
                    vExtra -> Op.EXTRA
                    vMerge -> Op.MERGE
                    else -> null
                }
            }
        }
        // semi-global: best end = max over dp[n][*] and dp[*][m] (trailing observed free)
        var bestScore = NEG
        var bi = n
        var bj = m
        for (j in 0..m) {
            if (dp[n][j] > bestScore) { bestScore = dp[n][j]; bi = n; bj = j }
        }
        for (i in 0..n) {
            if (dp[i][m] > bestScore) { bestScore = dp[i][m]; bi = i; bj = m }
        }
        // backtrack
        val moves = ArrayList<Move>()
        // trailing observed words (after the best end row) are FREE extras (semi-global)
        for (t in bi until n) moves.add(Move.Extra(observed[t]))
        var i = bi
        var j = bj
        while (i > 0 || j > 0) {
            val op = back[i][j]
            when (op) {
                Op.MATCH -> {
                    val oi = i - 1
                    moves.add(Move.Match(lo + j - 1, observed[oi], sim(observed[oi].text, exp[j - 1])))
                    i--; j--
                }
                Op.MERGE -> {
                    moves.add(
                        Move.Merge(
                            lo + j - 1,
                            observed[i - 2],
                            observed[i - 1],
                            sim(observed[i - 2].text + observed[i - 1].text, exp[j - 1]),
                        ),
                    )
                    i -= 2; j--
                }
                Op.SKIP_EXPECTED -> {
                    j-- // MISSED candidate; recorded by tracker via expected-not-matched bookkeeping
                }
                Op.EXTRA -> {
                    moves.add(Move.Extra(observed[i - 1]))
                    i--
                }
                else -> {
                    if (j == 0) { // leading observed free-skip
                        moves.add(Move.Extra(observed[i - 1]))
                        i--
                    } else if (i == 0) {
                        j-- // expected consumed before any observed; treat as skip (MISSED candidate)
                    } else break
                }
            }
        }
        moves.reverse()
        return Alignment(moves, if (bestScore == NEG) 0.0 else bestScore)
    }

    private enum class Op { MATCH, SKIP_EXPECTED, EXTRA, MERGE }
}
