# CI — what the FIRST remote run closes

`gh` CLI and a git remote are unavailable in the implementation sandbox, so CI has never
executed (E1). The workflow is engineered so **one push by Mo produces ALL missing evidence**,
not just a green tick. Jobs (`.github/workflows/ci.yml`):

| Job | Steps | Closes |
|---|---|---|
| **build** | assembleDebug · `test` + JaCoCo · detekt/ktlint (informational until fully wired) · `:app:lintDebug` · merged-manifest offline check (INTERNET / ACCESS_NETWORK_STATE fail) · **classpath hygiene** (`:app` releaseRuntimeClasspath must not contain tools:eval, JUnit, Truth) · license report | unit-test evidence on a real machine |
| **release** | `assembleRelease` + `bundleRelease` with R8 ON · **minify guard**: fails without `app/build/outputs/mapping/release/mapping.txt`, fails if `sandboxNoMinify` is committed in gradle.properties · uploads APK+AAB+mapping.txt | **E1** (first minified release + R8 keep-rule evidence anywhere) |
| **size** | bundletool `build-apks` (debug keystore) + `get-size total --dimensions=SDK,ABI,SCREEN_DENSITY` · arm64/API-27+ surfaced · fails above 85 MB hard ceiling, warns above the 80 MB target (§14) · appends to job summary | **real AAB download size** (replaces the 77–80 MB estimate) |
| **instrumented** | `reactivecircus/android-emulator-runner`, matrix **API 27** (the real native floor, V-016) + **API 35** · `:app:connectedDebugAndroidTest` (native smoke tests) | **S2 decode fixture path, Q8-on-device, minSdk question** — without Mo's phones |

## Instrumented test contents (app/src/androidTest)
- `loadLibrarySmoke_bothNativeLibsLoad` — `System.loadLibrary("onnxruntime")` +
  `System.loadLibrary("sherpa-onnx-jni")` on API 27 and 35. **This is the definitive minSdk
  answer**: if it passes on 27, the prebuits load on the floor; if it fails, minSdk must be 27+.
- `sherpaFixtureDecode_timestampsUnitAndFrameShift` — **SKIPS with a loud message when
  `eval/fixtures/fixture.wav` is absent** (gated/third-party audio is never committed). With
  Mo's own recorded clip (see Needs-from-Mo), it asserts seconds-scale timestamps + 40 ms
  frame-shift multiples on-device.
- `amiriQuranFont_rendersAllTanzilCodepoints_nonEmpty` — font asset loads and is non-trivial
  (full shaping assertions + screenshots land with the Phase 2 UI tests).

## For Mo
> Push the repo to GitHub (or provide a remote). The first run closes E1, S2 (emulator decode
> path), the AAB size measurement, and the R8 keep-rules gate. `git remote add origin … && git
> push -u origin main` is all that is needed; every asset is fetched and SHA-256-verified by
> the workflow itself (fetch_native → generate_notices → fetch_models).
