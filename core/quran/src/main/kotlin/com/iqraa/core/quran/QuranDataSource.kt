package com.iqraa.core.quran

/** Phase 1: Tanzil Uthmani asset + N() + derived tables (§7). This is the Phase 0 stub. */
interface QuranDataSource {
    fun surahCount(): Int
    fun ayahCount(): Int
}

object StubQuranDataSource : QuranDataSource {
    override fun surahCount(): Int = 114
    override fun ayahCount(): Int = 6236
}
