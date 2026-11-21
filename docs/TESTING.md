# TESTING

Current (Phase 0): core:align contract tests — observation identity (§9.2
4-tuple), independence rules (<50% overlap of shorter window; final +
window-bounds differ), TrackerConfig defaults per §9.1, AsrWord confidence
null. JUnit Jupiter 6.1.3.

Current after Phase 1 (all green):
- core:quran (21 tests): integrity (114/6,236/sha256/notice), basmala placement (1:1 IS basmala,
  9:1 none), N() goldens (dagger alif, 06DF/06E5/06E6 drops, alef-wasla, hamza seats, ة/ى, tatweel,
  basmala, ta'awwudh, full-77,881-word sweep).
- core:align (31 tests): sim/levenshtein; aligner table tests (semi-global free edges, skip, merge,
  substitution); tracker property tests (idempotency, independence-gated WRONG for both presets,
  nothing-downgrades-CORRECT, HINTED terminal, MISSED via realistic partial sequence, boundary
  exclusion vs pause-final relaxation, null-timing token-index interiority, monotonic pointer,
  repeats→EXTRA, EXTRA dedup, lost timer, reanchor, §9.6 summary semantics).
- JaCoCo line coverage: core:quran 93.6%, core:align 91.7% (combined 91.9% ≥ 85% target).
- tools:eval + eval/run_asr.py: synthetic end-to-end pipeline exercised (report with honest labels).

Planned per §14: golden/table tests (N(), alignment planted-error matrix),
property-based tracker tests (idempotency, independence, monotonicity),
instrumented fixture decode, Room migrations, Compose UI tests, coverage
≥85% lines on core:quran + core:align (Phase 1 target).

Run: `./gradlew test`
