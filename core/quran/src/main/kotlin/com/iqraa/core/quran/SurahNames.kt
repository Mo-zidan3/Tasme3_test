package com.iqraa.core.quran

import java.io.InputStream

/**
 * The canonical 114 surah names (Arabic, plain — no «سورة» prefix, no harakat) exactly as
 * Tanzil's own surah index lists them, plus per-surah ayah counts (V-048 data foundation —
 * issue #11: the reader shipped an empty list because no surah table existed).
 *
 * Same source and license as the committed quran-uthmani.txt (Tanzil, CC-BY 3.0 — the
 * verbatim notice rides in the file header and docs/tanzil-notice.txt). The per-surah
 * counts are cross-validated against the ayah asset by [SurahNamesTest] (both are Tanzil:
 * they must agree; totals 6,236).
 */
object SurahNames {
    const val ASSET_PATH = "/surah-names.txt"

    /** One surah row: number (1..114), plain Arabic name, ayah count. */
    data class Surah(
        val number: Int,
        val name: String,
        val ayahCount: Int,
    )

    /** Parse from a stream; validates 114 surahs with contiguous numbering (integrity §7.3). */
    fun parse(input: InputStream): List<Surah> {
        val surahs = ArrayList<Surah>(114)
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split('|')
                require(parts.size == 3) { "malformed surah line: ${line.take(40)}" }
                val number = parts[0].toInt()
                require(number in 1..114) { "surah number out of range: $number" }
                require(parts[1].isNotBlank()) { "blank surah name at $number" }
                val count = parts[2].toInt()
                require(count > 0) { "non-positive ayah count at $number" }
                surahs.add(Surah(number, parts[1], count))
            }
        }
        require(surahs.size == 114) { "expected 114 surahs, got ${surahs.size}" }
        require(surahs.map { it.number } == (1..114).toList()) {
            "surah numbering is not contiguous 1..114"
        }
        return surahs
    }

    fun load(): List<Surah> = parse(SurahNames::class.java.getResourceAsStream(ASSET_PATH)!!)
}
