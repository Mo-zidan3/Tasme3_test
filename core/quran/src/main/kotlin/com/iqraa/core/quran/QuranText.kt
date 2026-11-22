package com.iqraa.core.quran

import java.io.InputStream

/**
 * Read-only loader for the committed Tanzil Uthmani asset (§7.1, §7.3).
 *
 * Asset format (see the file header):
 *  - '#' comment lines (provenance + VERBATIM Tanzil copyright notice — CC-BY-3.0)
 *  - ayah lines: `surah|ayah|uthmani-text`
 *
 * This class NEVER writes or transforms the stored asset (§7.3: no mutation path).
 * The normalized model-space forms are derived separately by [Normalizer] and stored
 * in a derived table (also carrying the Tanzil notice header).
 */
object QuranText {

    const val ASSET_PATH = "/quran-uthmani.txt"

    /** sha256 of the ayah payload (everything after the header), pinned at asset creation. */
    const val AYAH_PAYLOAD_SHA256 = "a0d9cd3755df1570c14a3a2c226b401ede4e4d12df44655f5873c0e1924bdd38"

    /** sha256 of the whole asset file. */
    const val ASSET_SHA256 = "8c3a25b0ab5eff2fbb8762b10262caf2d350972c6e704c00eac21af47fa06751"

    data class Ayah(val surah: Int, val ayah: Int, val text: String)

    /** Parse from a stream; validates 114 surahs and 6,236 ayahs (integrity §7.3). */
    fun parse(input: InputStream): List<Ayah> {
        val ayahs = ArrayList<Ayah>(6236)
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split('|', limit = 3)
                require(parts.size == 3) { "malformed line: ${line.take(40)}" }
                val s = parts[0].toInt()
                val a = parts[1].toInt()
                require(s in 1..114 && a >= 1) { "out-of-range refs $s:$a" }
                require(parts[2].isNotBlank()) { "blank ayah text at $s:$a" }
                ayahs.add(Ayah(s, a, parts[2]))
            }
        }
        val surahIds = ayahs.map { it.surah }.toSortedSet()
        require(surahIds.size == 114) { "expected 114 surahs, got ${surahIds.size}" }
        require(ayahs.size == 6236) { "expected 6236 ayahs, got ${ayahs.size}" }
        // ayah numbering per surah must be contiguous from 1
        val bySurah = ayahs.groupBy { it.surah }
        for ((s, list) in bySurah) {
            val sorted = list.sortedBy { it.ayah }
            require(sorted.first().ayah == 1) { "surah $s does not start at ayah 1" }
            require((1..sorted.size).zip(sorted).all { (i, ay) -> ay.ayah == i }) {
                "surah $s ayah numbering not contiguous"
            }
        }
        return ayahs
    }

    fun load(): List<Ayah> = parse(QuranText::class.java.getResourceAsStream(ASSET_PATH)!!)

    /** sha256 (hex) helper for integrity tests. */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
