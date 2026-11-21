# tasmee — Quran Recitation & Tasmee' App (Android, offline)

Free, offline Android app for **following along** with Quran recitation
(live word highlighting) and **testing memorization (tasmee')** with
conservative, honest feedback. No accounts, no network — audio never leaves
the device. Engineering spec: `upload/QURAN_APP_SPEC.md` (v2.0).

**Status:** Phase 0 + Phase 0.5 spike complete (see `docs/STATUS.md`).
Hafs only. Tajweed/harakat judgment is explicitly out of scope (§2).

## Build

Requirements: JDK 17+ (toolchain 17; sandbox used Temurin 21), Android SDK
with `platforms;android-37.1` and `build-tools;36.0.0`.

```bash
# 1) fetch pinned model assets (pinned revisions + SHA-256; not in git)
python3 scripts/fetch_models.py

# 2) build
./gradlew assembleDebug

# 3) unit tests
./gradlew test
```

`local.properties` must point `sdk.dir` at your Android SDK (not committed).

### What gets bundled
- `app/src/main/assets/models/quran-asr-small/` — Abdou/quran-asr-small int8 (36 MB, MIT)
- `app/src/main/assets/models/quran-asr-tiny/` — fallback model (19 MB, MIT)
- `app/src/main/assets/models/silero_vad.onnx` — Silero VAD
- `app/src/main/jniLibs/arm64-v8a/` — sherpa-onnx v2.0.2 + ONNX Runtime (arm64 only)
- `app/src/main/assets/fonts/AmiriQuran.ttf` — Amiri Quran (SIL OFL 1.1)

Models/jniLibs are fetched or vendored by scripts; nothing large lives in git
(see `.gitignore`). Third-party notices: `THIRD_PARTY_NOTICES.md`.

## Tests

```bash
./gradlew :core:align:test          # contract tests (identity, independence §9.2)
./gradlew test                      # all modules
```

## Evaluation harness (Phase 1)

`tools:eval` (JVM CLI) + `eval/run_asr.py` (sherpa-onnx Python) per §13.
Scoring happens in the model-native raw-Uthmani space (§7.2). Author-reported
benchmark numbers are never presented as ours.

## Offline guarantee

The merged (release) manifest must contain **no INTERNET and no
ACCESS_NETWORK_STATE** permission. CI fails the build if either appears
(`.github/workflows/ci.yml`).

## Documentation

`docs/ARCHITECTURE.md` · `docs/DECISIONS.md` (ADR log) ·
`docs/VERIFICATION.md` (evidence for every [VERIFY]) · `docs/STATUS.md` ·
`docs/EVALUATION.md` · `docs/TESTING.md` · `docs/PRIVACY.md` ·
`docs/DEVICES.md` · `docs/AUDIT.md`

## License

Apache-2.0 (app code; §4 — unless Mo decides otherwise). Third-party assets
keep their own licenses (see THIRD_PARTY_NOTICES.md): models MIT, sherpa-onnx
Apache-2.0, Tanzil text CC-BY-3.0 (verbatim-only), Amiri Quran OFL-1.1.
