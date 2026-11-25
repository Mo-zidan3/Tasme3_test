package com.iqraa.feature.reader

import com.iqraa.core.quran.SurahNames

/** One surah row for the reader list (§10.1 Home). */
data class Surah(
    val number: Int,
    val name: String,
    val ayahCount: Int,
)

/**
 * The reader's data seam (issue #11 / V-048): Hilt-injectable so the §11 error-state
 * instrumented tests can substitute a failing or empty source via @BindValue; the real
 * implementation reads the committed Tanzil-derived table.
 *
 * Contract: a failed load THROWS; a load that would return an EMPTY list also throws —
 * an empty success is exactly the Phase-3 defect class and must never happen again.
 */
fun interface SurahListSource {
    suspend fun load(): List<Surah>
}

/** Real source: the committed surah-names table (Tanzil; integrity unit-tested in core:quran). */
class TanzilSurahListSource : SurahListSource {
    override suspend fun load(): List<Surah> = SurahNames.load().map { Surah(it.number, it.name, it.ayahCount) }
}
