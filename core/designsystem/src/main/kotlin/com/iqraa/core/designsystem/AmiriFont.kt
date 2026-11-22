package com.iqraa.core.designsystem

import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * §5: Amiri Quran 1.003 (OFL) — the default Quran font (Mo's ratified choice).
 * The TTF is a FETCHED asset (app/src/main/assets/fonts/AmiriQuran.ttf, populated by CI —
 * never committed), so the family is built from the file at runtime; null-safe fallback to
 * the platform default when the file is absent (host previews).
 */
fun amiriQuranFontFamilyOrNull(fontFile: File): FontFamily? {
    if (!fontFile.exists() || fontFile.length() <= 100_000) return null
    return FontFamily(
        androidx.compose.ui.text.font.Font(file = fontFile),
    )
}
