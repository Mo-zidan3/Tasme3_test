package com.iqraa.core.data.settings

/**
 * §15 DataStore keys — single source of truth for preference names.
 * Storage keys are package-independent literal names (D-019: no migration concern).
 */
object DataStoreKeys {
    const val FILE = "iqraa_settings"

    const val LANGUAGE = "language"
    const val THEME = "theme"
    const val FONT_SCALE = "font_scale"
    const val PRESET = "preset"
    const val MODEL_OVERRIDE = "model_override"

    // calibration block: {model, rtf, timestamp} — §8.6 calibration cache
    const val CALIBRATION_MODEL = "calibration_model"
    const val CALIBRATION_RTF = "calibration_rtf"
    const val CALIBRATION_TIMESTAMP = "calibration_timestamp"

    const val RIWAYA_STATEMENT_SHOWN = "riwaya_statement_shown"

    /** §10.2/F2: manual scroll pauses auto-follow for this long before resuming. */
    const val AUTO_SCROLL_PAUSE_MS = "auto_scroll_pause_ms"
}
