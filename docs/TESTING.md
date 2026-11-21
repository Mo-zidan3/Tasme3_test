# TESTING

Current (Phase 0): core:align contract tests — observation identity (§9.2
4-tuple), independence rules (<50% overlap of shorter window; final +
window-bounds differ), TrackerConfig defaults per §9.1, AsrWord confidence
null. JUnit Jupiter 6.1.3.

Planned per §14: golden/table tests (N(), alignment planted-error matrix),
property-based tracker tests (idempotency, independence, monotonicity),
instrumented fixture decode, Room migrations, Compose UI tests, coverage
≥85% lines on core:quran + core:align (Phase 1 target).

Run: `./gradlew test`
