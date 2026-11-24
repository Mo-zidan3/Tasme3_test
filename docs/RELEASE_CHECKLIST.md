# v0.1.0 release checklist (Phase 4) — DO NOT TAG until every box is ticked

- [ ] Phase 3 H1–H4 executed and recorded by Mo on hardware (docs/TESTING.md) — HARD GATE
- [ ] Arabic strings reviewed by Mo (currently UNREVIEWED)
- [ ] Store listing reviewed (docs/STORE_LISTING.md) incl. §12.6 constraints
- [ ] Release keystore created + IQRAA_* env vars set as CI secrets (never committed)
- [ ] Release APK/AAB: `./gradlew :app:bundleRelease` with env vars → signed artifact
- [ ] R8 keep rules proven on-device (V-045 releaseSmoke green in CI)
- [ ] Manual release smoke on Mo's phone: install signed release, decode a real recitation
- [ ] Notices/licenses render in-app (About screen) — canonical SHA gate green in CI
- [ ] Tag plan: annotate `v0.1.0` on the verified main tip with the run id in the message;
      never tag a red or unverified tip
