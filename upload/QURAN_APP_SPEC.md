# Quran Recitation & Tasmee' App — Engineering Specification
**Audience:** an AI coding agent that will build this project end to end.
**Owner / reviewer:** Mo (product owner, technical lead).
**Spec version:** 2.0 · **Date:** 2026-11-21 · **Supersedes:** v1.0 (2026-11-20)
**Status:** Phases 0–4 in scope (incl. Phase 0.5 spike). Phase 5 requires Mo's written approval.
**Changelog:** Appendix C maps every v1.0 gap / external-review point to its resolution here.

---

## 0. Operating rules (read first)

1. You are the implementing engineer; Mo reviews. Work phase by phase (§16). Do not start phase N+1 until phase N's "Done when" is satisfied **and demonstrated** (command output, test report, screenshot).
2. Items tagged `[VERIFY]` are genuinely open. Confirm against the primary source (official docs, model repo, library source) before relying on them. **Never guess an API name, flag, file name, version, or number.** Items in §5 marked CLOSED were verified on 2026-11-21 against the cited source; re-open if the source changes.
3. If this spec and reality disagree: **trust reality**, record it in `docs/DECISIONS.md`, log the evidence in `docs/VERIFICATION.md`, flag it to Mo in `docs/STATUS.md`.
4. Ask Mo instead of guessing when a decision is irreversible or touches Quran-text integrity, licensing, privacy, or product scope (§2–3). Decisions tagged `[MO]` in this spec are product-level defaults: implement as written, but list them for Mo's ratification in `docs/STATUS.md`. For everything else: choose a sensible default, record it, continue.
5. **Never state a number you did not measure.** Author-reported benchmark numbers must always be labeled as such, with the scoring space (§7.2) and subset named. If a test was not run, say so.
6. Never train/fine-tune a model, and never add network access to the app, unless Mo approves in writing (Phase 5 / ADR). Build-time scripts on the dev machine may use network; the app may not.
7. If the working directory already contains code: audit first (Phase 0), do not rewrite working code without an ADR.
8. The Phase 0.5 spike outputs are authoritative inputs to Phase 1–2 design. Do not design `core:align` against unconfirmed timing/confidence semantics.

---

## 1. Product summary

A free Android app that lets a user:
- **Recite and follow along** — live word highlighting against the Quran text.
- **Test memorization (tasmee')** — text hidden, revealed as the user recites.
- **Get conservative feedback** on skipped or misrecited words.

Everything runs on-device, offline, no accounts.

**Product principles (priority order):**
1. **Integrity of the Quran text.** Never alter, auto-correct, paraphrase, or "improve" it. Verifiable by test.
2. **Privacy by construction.** No network permission; audio never leaves the device.
3. **Honest feedback.** A false alarm is worse than a miss at launch. Uncertain detections are shown as uncertain. If the §13 gate is not met, the app ships **without red WRONG flags** and the store listing must not promise mistake detection (§12.6).
4. **Replaceable model.** The speech model is a swappable file behind `RecitationRecognizer`.

---

## 2. Scope and non-goals (MVP)

**Not in MVP — do not build, do not promise in UI or store text:**
- Tajweed judgment (madd, ghunnah, makharij, qalqalah…).
- Detection of vowel-mark (harakat) errors. The model outputs letters only (§5 V1).
- Any confidence/log-prob-based feature (not exposed by the chosen runtime, §5 Q1-CLOSED). Plan B is Phase 5.
- Training or fine-tuning any model.
- Accounts, cloud sync, social, ads, analytics, network crash reporting.
- Reciter audio playback/download (individual recording licenses undocumented, V9).
- Riwayat other than Hafs (model supports Warsh; keep data model extensible, ship Hafs only).
- iOS (keep `core:quran`, `core:align`, `tools:eval` pure JVM for later portability).
- Translation/tafsir. Session export. Foreground service / ongoing notification (MVP is foreground-only, screen-on; §10.4).
- Whole-Quran search while tracking (S1 voice search is the only global search, post-MVP).

---

## 3. MVP features and acceptance criteria

| ID | Feature | Acceptance criteria |
|---|---|---|
| F1 | Reader | Surah list; ayah view with Uthmani text; correct RTL shaping (§7.7 font tests); font scale 0.8–2.0×; light/dark. Text matches approved source byte-for-byte (§7 tests). |
| F2 | Follow-along | Pick surah + ayah range, tap mic; words highlighted as recited; states per §9–§10; **p95 word-highlight latency ≤ 1.5 s on the mid-range test device (§14)**; works in airplane mode. |
| F3 | Tasmee' | Text hidden (§10.3 placeholders); word revealed on CORRECT/CLOSE; hint reveals next word → HINTED (counted separately); summary semantics exactly per §9.6. |
| F4 | Feedback policy | Implements §9 exactly; every threshold in `TrackerConfig`; no magic numbers in logic. |
| F5 | Session history | Local list with counts per state; delete one/all; **no audio stored**; partial summary survives process death via checkpoints (§11, §15). |
| F6 | Settings | Language (ar/en), theme, font size, sensitivity preset (Conservative default / Balanced per §9.5), model override (Auto/Small/Tiny), degraded-mode badge visible when active (§8.6). |
| F7 | Onboarding & permissions | Mic rationale; privacy statement; **riwaya statement (Hafs only)**; denied / denied-permanently handling with deep link to system settings. |
| F8 | About & licenses | Credits/licenses per §12; study-aid disclaimer; Tanzil notice + link (§7.1). |
| S1 | Stretch: voice search | Post-MVP, Mo approval, Phase 5. |
| S2 | Stretch: save recitation locally | Off by default, local only, deletable. Phase 5. |

---

## 4. Locked decisions (do not revisit without Mo)

| Topic | Decision |
|---|---|
| Platform | Android native, Kotlin + Jetpack Compose (Material 3). **minSdk 26**; targetSdk = latest stable at implementation time (verify, record). **ABI: arm64-v8a only** for release (armeabi-v7a dropped to protect size budget, §14). |
| Execution | 100% on-device. **No `INTERNET` permission** in release manifest (CI-enforced, merged manifest). Also strip `ACCESS_NETWORK_STATE` if injected by any dependency. |
| Primary model | `Abdou/quran-asr-small` int8 ONNX via sherpa-onnx. Fallback: `Abdou/quran-asr-tiny` int8. **Each model must pass its own §13 gate before shipping** (§8.1). |
| Runtime consumption | **Pinned prebuilt `jniLibs` from official sherpa-onnx releases + vendored Kotlin API sources** (Q4-CLOSED). No official Maven artifact exists; do NOT use third-party wrappers (`com.litongjava:*`, `com.bihe0832.android:*`). |
| Comparison space | **Model-native raw Uthmani letters** everywhere in app and `tools:eval`. `spelling.json` is used ONLY in `eval/run_asr.py` when scoring against standard-spelling references (§7.2). |
| Presets | Conservative = ≥2 independent interior observations for WRONG. Balanced = single **final** interior observation allowed (no confidence gate). Both gated per §13; default Conservative. `[MO]` |
| Tasmee' summary | "صحيحة: X من Y" counts **CORRECT only**; CLOSE reported separately as uncertain; Y excludes HINTED (§9.6). `[MO]` |
| Font | **Amiri Quran (SIL OFL 1.1)** from upstream `aliftype/amiri` releases (not Google Fonts, §7.7). KFGQPC Uthmanic HAFS is an optional upgrade **only after Mo obtains its EULA** (primary source currently unavailable). `[MO]` |
| Training | None in MVP. |
| Riwaya | Hafs. |
| Architecture | Multi-module, UDF, Coroutines/Flow, Hilt (KSP), Room, DataStore. |
| Alignment | Own implementation in pure Kotlin/JVM `core:align`, unit-tested without a device. |
| Language | Code & docs English; UI Arabic (primary) + English. |
| App code license | Apache-2.0 unless Mo says otherwise. |
| applicationId | Dev placeholder `com.example.tasmee`. **Google Play rejects `com.example.*` — renaming is a Phase 4 release blocker.** Mo must supply the final id (§18). |
| Versions | Latest stable at implementation time; check compatibility; record in `docs/DECISIONS.md`. Never pin from memory. |

---

## 5. Verified facts, closed questions, and open items

### 5.1 Verification log (2026-11-21, primary sources; Appendix A)

All benchmark numbers are **author-reported**, not ours. Re-open a source before a decision depends on a number.

| # | Fact / closure | Source |
|---|---|---|
| V1 | `Abdou/quran-asr-small`: 33M params; **36 MB int8 ONNX**; Conformer-CTC (9 blocks, d=384, ×4 subsampling); **31 tokens = 29 Arabic letters + word separator `▁` + CTC blank**; greedy CTC, **no LM, no lexicon, no hints**; Hafs+Warsh; **MIT** (data keeps own terms; tlog CC-BY-4.0). Output = **bare Uthmani rasm**; folds: ة→ه, ى→ي, hamza seats folded. Training labels = Tanzil Hafs Uthmani. | A1, A2 |
| V2 | small, Quran-Lab 600-clip benchmark, **standard-spelling space**: 4.49% WER overall / 10.45% phone (CER 2.17 / 2.84). **Raw-Uthmani space (the app's space): phone 3.29% WER / 1.16% CER.** Author disclosed one duplicated phone clip (36:71). | A1 |
| V3 | small training data: 1,532 h = EveryAyah Hafs 1,132 h + Warsh 96 h + **tlog phone 283 h** + Iqra'Eval 21 h. | A1 |
| V4 | `Abdou/quran-asr-tiny`: 16.4M params; **19 MB int8** (fp32 66 MB); same interface (`x` fbank [N,T,80] + `x_lens` → `log_probs`, `log_probs_len`; metadata `vocab_size=31`, `subsampling_factor=4`); MIT. **As-shipped int8: 7.08% WER overall / 18.34% phone (standard space)**; fp32: 6.94 / 17.95. **Raw-Uthmani space: 12.90 overall / 11.51 phone WER (CER 4.25 / 4.12).** | A2 |
| V4b | **Leakage disclosure (tiny card):** benchmark is held out from the *benchmark authors'* training data, not the model's. **134/200 EveryAyah clips are reciters in tiny's training set** (without them: 8.87% WER); 13 phone clips were in training until step ~152k. EveryAyah numbers are optimistic; phone subset is the signal, with caveats. | A2 |
| V4c | tiny card limitations: **children and very noisy rooms underrepresented**; other speech is forced into Quranic-looking letters; riwayat beyond Hafs/Warsh not trained. | A2 |
| V5 | Both models load as sherpa-onnx **zipformer CTC** (`model.int8.onnx` + `tokens.txt`). Repos ship `transcribe.py`, `spelling.json` (2,111-word Uthmani→standard table), `retrieve.py`, training code. `transcribe.py` uses `OfflineRecognizer.from_zipformer_ctc(...)`; **sherpa-onnx computes the 80-dim Kaldi-compatible fbank internally (16 kHz, 25 ms/10 ms)**; words split on `▁`. | A1, A2, transcribe.py |
| V6 | Only open Tarteel models: two Whisper fine-tunes (Dec 2022, Apache-2.0, empty cards). **Harness-only baseline, never shipped.** Community GGML q5_1 of base verified: `koribeche/wird-asr-model`, 59.7 MB, SHA-256 `acc1f03c8b1468b674b4fab50e0b2312cff982ba0001cd16fe36ca23862b6536` (conversion note: `max_length` set to 448 before convert). | A3, A4 |
| V7 | Public EveryAyah test split overlaps most Quran-ASR training sets → WER on it is not a quality signal. Phone-mic held-out data is what matters. | A5 |
| V8 | `Quran-Lab/quranic-asr-benchmark`: 600 clips (200 each everyayah_heldout / qul_alnufais / tlog_holdout); gated, manual approval; research/eval only; **audio must not be redistributed**. | A5 |
| V9 | `tarteel-ai/tlog`: gated, CC-BY-4.0. `tarteel-ai/everyayah`: compilation CC-BY-4.0; individual recording licenses undocumented. | A6, A7 |
| **Q1-CLOSED** | Kotlin `OfflineRecognizerResult` = `text, tokens: Array<String>, timestamps: FloatArray, lang, emotion, event, durations (TDT only), words (HLG only)`. **Per-token timestamps YES; per-token log-probs/confidence NO.** `OfflineZipformerCtcModelConfig(model=...)` exists; `OfflineRecognizer(assetManager, config)` loads directly from assets; `createStream / acceptWaveform / decode / getResult`. Timestamps are seconds by sherpa convention `[VERIFY unit + frame shift with a fixture in Spike S2]`. | sherpa-onnx `kotlin-api/OfflineRecognizer.kt` (master, 2026-11-21) |
| **Q2-CLOSED (plan A)** | No custom front-end needed: sherpa-onnx computes features (V5). Confirm `FeatureConfig(sampleRate=16000, featureDim=80)` defaults in Spike S2. Exact fbank replication is only required if Plan B (Phase 5) is ever activated. | A1/A2 cards + transcribe.py |
| **Q3-CLOSED (inventory)** | 29 letters + `▁` separator + blank; folds per V1; output raw Uthmani. **Exact `tokens.txt` list must still be read and documented in `core:quran` at Phase 1** (do not reconstruct from memory). | A2 card |
| **Q4-CLOSED** | **No official Maven Central artifact** (`repo1.maven.org/.../com/k2fsa/sherpa/onnx/sherpa-onnx/maven-metadata.xml` → 404, checked twice; only third-party wrappers exist). Official path: **copy `kotlin-api` sources into the project + prebuilt `jniLibs`** (or build with NDK). sherpa-onnx = Apache-2.0; ONNX Runtime = MIT. `[VERIFY exact release asset name + minSdk of prebuilt .so in Spike S2; test on API 26 emulator]`. | sherpa-onnx docs/discussions; Maven 404 |
| **Q5-CLOSED (text)** | Tanzil Quran Text: **CC-BY-3.0**; verbatim copies only, **"CHANGING IT IS NOT ALLOWED"**; may be used in any website/app **provided source (Tanzil Project) is clearly indicated and a link to tanzil.net is made**; copyright notice must be included in all verbatim copies **and reproduced in files derived from / containing substantial portions** (→ our derived normalization table carries the notice too). No non-commercial clause. | tanzil.net/docs/text_license |
| **Q5-CLOSED (font default)** | **Amiri Quran: SIL OFL 1.1**, upstream `aliftype/amiri`. Use upstream release files, **not Google Fonts** (a served-file naming/color-font mixup is documented there). KFGQPC: official site redesigned, font pages currently unavailable ("قريباً"); third-party license descriptions conflict → **do not ship KFGQPC without its EULA in hand (Mo)**. | aliftype/amiri; fontsource; google/fonts issue #8513; fonts.qurancomplex.gov.sa |
| **VAD-CLOSED** | sherpa-onnx Kotlin `Vad`: `VadModelConfig(sileroVadModelConfig=SileroVadModelConfig(model, threshold=0.5, minSilenceDuration=0.25, minSpeechDuration=0.25, windowSize=512, maxSpeechDuration=5.0), sampleRate=16000, numThreads, provider="cpu")`; `acceptWaveform / front(): SpeechSegment(start [samples], samples) / pop / isSpeechDetected / flush / reset`. Asset: `silero_vad.onnx` from sherpa-onnx releases tag `asr-models`. Note `windowSize=512` samples = 32 ms; `maxSpeechDuration` must be raised to match `maxSegmentMs` (§8.4). | sherpa-onnx `kotlin-api/Vad.kt` |
| **A4-CLOSED** | Baseline GGML repo exists with published SHA-256 (V6). Baseline runner = whisper.cpp CLI (§13.2), harness-only. | A4 |

### 5.2 Remaining open questions

| # | Question | Phase |
|---|---|---|
| Q6 | Effect of `AudioSource` (VOICE_RECOGNITION vs MIC vs UNPROCESSED) and OS NS/AGC on accuracy. **Effect size unknown — measure, do not assume.** | 2 |
| Q7 | Real RTF, memory, battery, highlight latency on target devices (§18). | 0.5/2/4 |
| Q8 | `[VERIFY]` Timestamp unit (seconds) and output frame shift (expected 40 ms = 10 ms hop × 4 subsampling) via fixture; exact prebuilt jniLibs release asset name and minSdk; exact `tokens.txt` inventory; Tanzil ayah count = 6,236 under Hafs numbering; Amiri Quran shaping of all Tanzil Uthmani codepoints on API 26+. | 0.5/1 |

---

## 6. Architecture

### 6.1 Modules

```
repo/
 ├── app/                    # Shell: navigation, DI wiring, theme, manifest
 ├── core/
 │   ├── quran/              # Read-only Quran data, N(), derived tables   (pure JVM)
 │   ├── align/              # Tracker + feedback policy                   (pure JVM)
 │   ├── asr/                # RecitationRecognizer + sherpa-onnx impl
 │   ├── audio/              # Mic capture, VAD, segmenter, ring buffer
 │   ├── session/            # audio → asr → align orchestration; StateFlow
 │   ├── data/               # Room (history) + DataStore (settings)
 │   └── designsystem/       # Compose theme, RTL, Quran text composables
 ├── feature/{reader,recite,history,settings}/
 ├── tools/eval/             # JVM CLI scorer (uses core:align)             (pure JVM)
 ├── eval/                   # Python ASR runner; data/ labels/ git-ignored
 ├── scripts/                # fetch_models.py, fetch_native.py, license report
 ├── docs/  └── .github/workflows/
```

`core:quran`, `core:align`, `tools:eval` have **no Android dependencies** (JVM-testable, KMP-portable).

### 6.2 Interfaces and contracts (normative)

```kotlin
interface RecitationRecognizer : AutoCloseable {
    val info: ModelInfo                      // id, revision, sha256, space="uthmani-raw"
    suspend fun load()                       // throws ModelLoadException (§11)
    /** One speech segment: 16 kHz mono float PCM in [-1,1]. */
    suspend fun decode(pcm: FloatArray, segmentStartMs: Long): AsrResult
}

data class AsrResult(
    val text: String,                        // model-native raw Uthmani
    val words: List<AsrWord>,
    val audioMs: Long, val computeMs: Long   // RTF = computeMs / audioMs
)

data class AsrWord(
    val text: String,                        // normalized via N(), model space
    val startMs: Long?, val endMs: Long?,    // SESSION-GLOBAL ms (contract below)
    val confidence: Float?                   // always null with sherpa (Q1-CLOSED)
)

data class Observation(
    val words: List<AsrWord>,
    val windowStartMs: Long, val windowEndMs: Long,  // session-global
    val isFinal: Boolean,                    // true: pause-final or max-length cut
    val modelRev: String
)

enum class WordState { PENDING, CORRECT, CLOSE, WRONG, MISSED, HINTED, EXTRA }

interface RecitationTracker {
    fun reset(expected: List<ExpectedWord>, config: TrackerConfig)
    fun onObservation(obs: Observation): TrackerUpdate
    fun hint(): TrackerUpdate                // tasmee' only
    fun reanchor(ayahIndex: Int): TrackerUpdate
}
```

**Timing & word-assembly contract (normative):**
- sherpa-onnx returns **per-token** `tokens` + `timestamps` (seconds `[VERIFY Q8]`). Tokens are letters; `▁` is the word separator. Assemble words between separators; `word.startMs = segmentStartMs + firstTokenTs*1000`; `word.endMs = segmentStartMs + lastTokenTs*1000 + frameShiftMs` (expected 40 ms `[VERIFY Q8]`).
- All tracker math uses **session-global milliseconds**. The segmenter owns the mapping from segment-local to session time.
- **Null-timing fallback** (if timestamps are unavailable/invalid for a result): interiority is computed in **token-index space** — exclude the first/last `kEdgeTokens` (TrackerConfig, start 2) of the hypothesis instead of `boundaryMs`. The tracker must work correctly in both modes; unit-test both.
- `confidence` is null in MVP. **No code path may depend on it** (§9.5 presets are confidence-free).

**Threading (normative):** inference on a dedicated single-thread dispatcher; never block main. ONNX inference is **non-interruptible**: "cancel stale partials" = queue depth 1, drop stale results by `windowEndMs`, never start a queued partial if a newer one exists. Audio capture thread never waits on inference.

### 6.3 Data flow

```
Mic 16kHz mono → ring buffer (≤30s) → VAD/segmenter
  → Recognizer.decode(segment)  [partials: trailing partialWindowMs; finals: full segment]
  → Observation (session-global bounds)
  → Tracker.onObservation → TrackerUpdate (states, pointer, stats)
  → SessionViewModel (StateFlow) → Compose (highlight/reveal/scroll)
  → Room checkpoint after each FINAL observation (§11, §15)
```

---

## 7. Quran text, normalization, and font

### 7.1 Source and license (Q5-CLOSED)
- Source: **Tanzil Uthmani Hafs** (Mo approves before use, §18). Store as read-only asset; commit SHA-256.
- License obligations (CC-BY-3.0, verbatim terms): ship the text **unmodified**; include the full Tanzil copyright notice in `THIRD_PARTY_NOTICES.md`, the About screen, **and the header of every derived file containing substantial portions** (including the normalized table); indicate "Tanzil Project" and link to `tanzil.net` in About. Record this interpretation in `docs/DECISIONS.md`.

### 7.2 Comparison space (normative decision)
- The app compares in **model-native raw Uthmani letters**. Rationale: the same phone clips score ~3× better in this space (V2: 3.29 vs 10.45 WER) because the model writes Uthmani rasm and Tanzil is the same rasm.
- `spelling.json` is **never used at app runtime**. It is used only by `eval/run_asr.py --standard` when scoring against standard-spelling references (e.g., EveryAyah/QUL refs), for leaderboard comparability. Reports must state the space.

### 7.3 Integrity tests (CI, must pass)
114 surahs; **6,236 ayat `[VERIFY Q8 against source]`**; asset checksum matches; no code path writes to or transforms the stored asset; basmala present where expected (Al-Fatihah ayah 1; separate optional basmala for every surah except At-Tawbah — do not double-count).

### 7.4 Normalization `N()` (pure, golden-tested)
- Maps Tanzil Uthmani → model token space: strip tashkeel/Quranic annotation marks/tatweel; apply the model's folds (**ة→ه, ى→ي, hamza seats folded**, V1); map alef variants per the **actual `tokens.txt`** (read it in Phase 1; do not invent rules). Word boundaries = whitespace in the source, preserved before per-word normalization.
- Derive rules from `tokens.txt` + repo logic (`transcribe.py`, `spelling.json` keys). Store normalized forms in a **separate derived table** (with Tanzil notice header); never overwrite the original.
- Golden tests: dagger alef (U+0670), small marks (U+06D6–U+06ED), ayah-end (U+06DD), alef-wasla, hamza seats, ة/ى, tatweel, basmala, ta'awwudh.

### 7.5 `N()` self-validation (protocol fixed)
- Validate with the **primary model (small)** via `eval/run_asr.py` on a **clean fixture** (quiet close-mic or studio; Mo's phone clips acceptable if quiet), scoring **in model space**: target **CER < 2%** (justified: author-reported raw-space phone CER 1.16%, studio ~0.2–0.5%, V2). If it fails, `N()` is wrong — fix `N()`, not the threshold.
- **Do not validate `N()` with tiny** (its raw-space phone CER is 4.12%, V4 — model error, not normalization error). Tiny's accuracy is judged only by the §13 gate.
- Calibration clips stay out of git and out of the app.

### 7.6 Tracking constraints
- Basmala optional at surah start (except At-Tawbah); never penalize reciting/omitting it where optional. Out-of-text speech (ta'awwudh, "صدق الله العظيم", etc.) absorbed by semi-global alignment (§9).
- **Mutashabihat:** tracker searches only inside the user-selected range. Never whole-Quran while tracking.

### 7.7 Font (Q5-CLOSED default)
- **Amiri Quran (OFL 1.1)** from upstream `aliftype/amiri` releases; ship license text; do not fetch from Google Fonts (documented served-file mixup).
- Shaping tests on API 26+ devices: dagger alef, small high/low marks, stop signs, ayah-end numerals, shadda+madd combinations, RTL line breaking, font scale 2.0×. Record results with screenshots.
- KFGQPC Uthmanic HAFS = optional upgrade **only after Mo provides its EULA** (official font pages currently unavailable; third-party descriptions conflict). Known HarfBuzz shaping issues with that font must be re-tested if adopted.

---

## 8. ASR engine

### 8.1 Candidates and per-model gate
| Role | Model | Notes |
|---|---|---|
| Primary | `Abdou/quran-asr-small` int8 | 36 MB; best verified trade-off (V2). |
| Low-end fallback | `Abdou/quran-asr-tiny` int8 | 19 MB; **as-shipped int8 7.08/18.34 (standard space), raw-space phone 11.51 WER (V4)** — ~3.5× worse than small in app space. |
| Harness-only baseline | `tarteel-ai/whisper-base-ar-quran` | Via verified GGML (V6) + whisper.cpp; **never shipped**. |

- **The §13 gate applies per model AND per preset.** A model/preset that fails ships only with WRONG→CLOSE rendering (§13.4) or is disabled. If neither model passes on Mo's recordings: **stop and report to Mo — do not silently ship.**
- Tiny may not be auto-selected into a worse false-alarm profile silently: when tiny is active, show the **"الوضع الخفيف" badge** (§10.5) and disclose it in the session summary.

### 8.2 Integration (Q4-CLOSED path)
- Vendor the **exact `kotlin-api` files matching the pinned sherpa-onnx release** (`OfflineRecognizer.kt`, `Vad.kt`, `FeatureConfig.kt`, …) under `core/asr/src/main/kotlin/com/k2fsa/sherpa/onnx/` (Apache-2.0 notice). Download **prebuilt `jniLibs` arm64-v8a** from the same pinned release `[VERIFY asset name, Spike S2]`; verify SHA-256; extract only `libsherpa-onnx-jni.so` (+ deps) into `app/src/main/jniLibs/arm64-v8a/`. API sources and .so must come from the **same tag** (ABI match).
- Config: `OfflineModelConfig(zipformerCtc = OfflineZipformerCtcModelConfig(model="models/<name>/model.int8.onnx"), tokens="models/<name>/tokens.txt", numThreads=2 start, provider="cpu")`, `decodingMethod="greedy_search"`, `FeatureConfig(sampleRate=16000, featureDim=80)` `[confirm defaults, Spike]`. Load via `OfflineRecognizer(assetManager, config)` — models stay in assets, no copy to filesDir.
- Models are **not committed to git**. `scripts/fetch_models.py` downloads a **pinned revision**, verifies SHA-256, places `app/src/main/assets/models/<name>/{model.int8.onnx,tokens.txt}` (+ `silero_vad.onnx` from releases tag `asr-models`). Record revisions/hashes/licenses in `THIRD_PARTY_NOTICES.md`. **Archive the exact shipped files + license texts** (Mo's storage / private release asset — ask Mo, §18) per §19 single-author risk.
- R8: keep `com.k2fsa.sherpa.onnx.**` and native methods; **verify the release build actually decodes on device** (Phase 4).
- **Plan B (ONNX Runtime direct + custom Kaldi fbank + frame log-probs) is Phase 5**, behind the same interface, only if Mo approves confidence-based features. Not MVP.
- `eval/run_asr.py` (sherpa-onnx Python, `from_zipformer_ctc`) must produce **the same hypotheses JSON as the app** (same space, same greedy decoding) so desktop and on-device results are comparable.

### 8.3 Audio capture
- `AudioRecord`, 16 kHz mono PCM16 → float [-1,1]. Start `VOICE_RECOGNITION`; compare `MIC`/`UNPROCESSED` in harness (Q6, **no assumed effect size**).
- Foreground only, screen-on during session (§10.4). Handle: permission revoked mid-session, mic taken by another app, audio focus loss, phone call, headset connect/disconnect, rotation (ViewModel survives), app switch / screen-off (pause, §11), process death (checkpoints, §11/§15). Release mic on pause/stop.

### 8.4 Segmentation and decoding (model is non-streaming)
All values in `AsrConfig`; defaults are starting points, tuned by harness, never presented as results.

| Param | Default | Meaning |
|---|---|---|
| `pauseMs` | 500 | Silence after speech → **final** segment. |
| `maxSegmentMs` | 8000 | Force-cut → final segment; carry `overlapMs` into next. |
| `overlapMs` | 1000 | Overlap between consecutive segments. |
| `partialEveryMs` | 1000 | Cadence for partial decodes (live highlighting). |
| **`partialWindowMs`** | **2500** | **Trailing window length for partials — separate from segment length.** |
| `vadPadMs` | 250 | Pad VAD segment edges (protect long madd tails). |
| `ringBufferMs` | 30000 | Max retained audio. |

- VAD: Silero via sherpa-onnx (VAD-CLOSED). Set `maxSpeechDuration ≥ maxSegmentMs/1000`; segmenter applies `vadPadMs` and its own `pauseMs` timer. Energy-based fallback (RMS threshold + hangover) if VAD fails to load.
- **Scheduling law (normative):** decode time D = RTF × W. Effective partial cadence = `max(partialEveryMs, D)` on the single inference thread. With RTF 0.5 and W=8 s, D=4 s → highlighting would update every ~4 s; therefore **partials use `partialWindowMs` (2.5 s → D≈1.25 s)**, never the full segment. Inference is non-interruptible; queue depth 1; drop stale results (§6.2).
- Observation types: pause-final and max-length-cut → `isFinal=true`; trailing-window partials → `isFinal=false`. Window bounds are session-global ms of the decoded audio.
- Measure RTF at **both** W=8 s and W=2.5 s (fixed overhead raises RTF for short windows).

### 8.5 First-run calibration (protocol fixed)
After permission: for each bundled model, decode a **5 s silence/noise fixture 3×, discard the first (warmup), take median RTF** at W=5 s; also probe W=2.5 s. Pick **small if median RTF ≤ 0.5 else tiny** (starting threshold). Persist result; manual override in F6; re-run on model change. Show progress UI (two model loads + probes can take seconds).

### 8.6 Degraded-mode ladder (normative)
| Level | Trigger (measured) | Behavior | UI |
|---|---|---|---|
| L0 full | small, RTF≤0.5 | partials per config | normal |
| L1 reduced | sustained backlog or RTF 0.5–0.8 | `partialEveryMs`×2 | subtle badge |
| L2 final-only | tiny selected and RTF>1, or repeated backlog | **no partials**; highlight on finals only | "الوضع الخفيف" badge + explanation |
| L3 unsupported | even final decode RTF>1.5 sustained | stop session, honest message | error screen (§11) |

Level changes are logged in the session summary. Never silently degrade.

---

## 9. Alignment and feedback policy (`core:align`, pure Kotlin)

All numbers are initial guesses in `TrackerConfig`, tuned in Phase 1 harness. **Comparison space = model-native raw Uthmani (§7.2).**

### 9.1 Similarity and alignment
- `sim(a,b) = 1 − Levenshtein(a,b)/max(|a|,|b|)` over characters in model space.
- Semi-global Needleman–Wunsch variant aligning observed words to expected window `pointer − backWords .. pointer + lookaheadWords`; unrelated leading/trailing speech is free.
- Moves: match/substitute (cost `1−sim`), skip-expected (MISSED candidate, `gapMissed`), extra-observed (EXTRA, `gapExtra`), **1↔2 merge/split** (CTC can merge/split words; compare against concatenation).
- Initial: `backWords=3`, `lookaheadWords=15`, `gapMissed=0.9`, `gapExtra=0.7`, `kEdgeTokens=2`.

### 9.2 Observation identity and independence (normative — the anti-false-alarm core)
- **Identity** = `(windowStartMs, windowEndMs, isFinal, modelRev)`.
- **Two observations are INDEPENDENT iff** audio overlap < 50% of the shorter window, **OR** one is final and the windows differ. Re-observing an identical window may only **improve evidence**, never add an independent vote (idempotency).
- **Interiority:** a word is interior to a window if `startMs ≥ windowStart+boundaryMs` and `endMs ≤ windowEnd−boundaryMs` (`boundaryMs=300` default). Boundary exclusion applies to **partials and forced max-length cuts**, not to natural pause-finals. Null-timing mode: token-index interiority (§6.2).

### 9.3 State machine and precedence (normative)
Per expected word, derive state from **all** observations:

| Priority | State | Rule |
|---|---|---|
| 1 | HINTED | Set by hint (tasmee'). **Terminal** — never overwritten; excluded from Y (§9.6). |
| 2 | CORRECT | Any **interior** observation with `sim ≥ τ_correct (0.90)`. Overrides CLOSE/WRONG/MISSED. Never downgraded. |
| 3 | MISSED | Alignment skipped the word **AND** pointer advanced ≥3 words beyond it **AND** confirmed in **≥2 independent observations** (§9.2). |
| 4 | WRONG | Preset rule (§9.5) satisfied, no CORRECT evidence, not HINTED. |
| 5 | CLOSE | Any evidence exists but none of the above (includes best sim in [0.50, 0.90) and uncertain cases). Shown as uncertain, **never as an error**. |
| 6 | PENDING | No evidence yet. |
| — | EXTRA | Observed word with no expected counterpart. **Never an error in MVP**; stats only. |

**Transitions:** MISSED→CORRECT and WRONG→CORRECT and CLOSE→CORRECT are allowed when later interior evidence matches (self-correction). Nothing downgrades CORRECT. HINTED is terminal.

### 9.4 Pointer, repeats, jumps, recovery (normative)
- **Monotonic pointer with repeats:** a repeated word aligning **inside** the window produces normal evidence (may upgrade MISSED→CORRECT). A repeat aligning **before `pointer − backWords`** → EXTRA, never penalized, no state change.
- **Forward jumps / out-of-order within range:** if best local alignment score < `reanchorSim` for `lostAfterMs` **of speech** (see below), search the **whole selected range**; on a strong match (≥3 consecutive words with `sim ≥ τ_correct`) re-anchor the pointer. Words between old pointer and new anchor become MISSED candidates **only under the §9.3 confirmation rule**. Never search outside the range.
- **Lost timer:** `lostAfterMs (6000)` advances **only while VAD reports speech with no usable alignment**. Silence/thinking pauses (normal in tasmee') do not advance it. If still lost after range-wide search → "resync" prompt (§10.6).
- **Out-of-text speech** absorbed by semi-global alignment.
- **Idempotency & dedup:** re-observing identical audio cannot change states except to improve evidence. EXTRA stats dedup key = `(normalizedText, sessionTime bucket 250 ms)` so `overlapMs` does not double-count.
- **Never claim what the model cannot see:** no harakat/tajweed claims anywhere in code, UI, store, docs.

### 9.5 Presets (confidence-free, Q1-CLOSED)
| Preset | WRONG rule | Default |
|---|---|---|
| **Conservative** | word interior in **≥2 independent** observations AND `max sim < τ_wrong (0.50)` AND no CORRECT evidence | ✅ |
| **Balanced** | word interior in **a single FINAL** observation AND `sim < τ_wrong` AND no CORRECT evidence | off |

Rationale: max-over-observations can only reduce false alarms vs single-observation judging; the price is more misses — measure both (§13). **Each preset must pass its own §13 gate**; if Balanced fails, disable it in Settings with an honest note. `[MO]` ratifies.

### 9.6 Tasmee' specifics (decision fixed)
- Hidden words revealed on **CORRECT or CLOSE** (lenient reveal, honest scoring).
- Hint reveals next expected word → HINTED (terminal).
- Summary: counts per state; **«صحيحة: X من Y» where X = CORRECT only, Y = expected words in range − HINTED**; CLOSE shown separately as «قريبة (غير مؤكدة)»; flagged list = WRONG+MISSED, tappable → jumps to word. `[MO]` ratifies.

---

## 10. UI behavior specification

### 10.1 Screens
Home (continue + surah list) → Range picker → Recite (mode toggle Follow-along | Tasmee') → Summary → History → Settings → About.

### 10.2 Feedback encoding (never color-only)
| State | Visual | Arabic | English |
|---|---|---|---|
| CORRECT | green + check | صحيحة | Correct |
| CLOSE | amber + tilde | قريبة من النص | Close to the text |
| WRONG | red + dotted underline | راجع هذه الكلمة | Please review this word |
| MISSED | grey outline | لم تُقرأ | Not recited |
| HINTED | blue outline | تلميح | Hint |

Wording stays suggestive, never accusatory («خطأ» forbidden). If WRONG is disabled by the gate (§13.4), the WRONG row is never rendered; CLOSE styling is used.

### 10.3 Follow-along & tasmee' behavior (normative)
- **Auto-scroll:** follows the pointer word; keep the current ayah visible (smooth scroll). **Manual scroll pauses auto-scroll for 5 s** and shows a «العودة للمتابعة» chip; tapping resumes immediately.
- **Tasmee' placeholders:** hidden words render as uniform neutral placeholders (no letter/length hints by default). Revealed words render normally with their state.
- **Hint button:** visible **only in tasmee'**; hidden in follow-along (text already visible).
- **Mode toggle:** switching modes mid-session ends the current session (summary) and starts a new one — no state mixing.
- **Mic level meter** always visible during a session; clear idle/listening/paused/error states.

### 10.4 Session lifecycle UX (normative)
- **Screen-on** while session active (FLAG_KEEP_SCREEN_ON). **No foreground service, no notification in MVP.**
- **Screen off / app switch / focus loss / incoming call:** capture pauses immediately (mic released), session state preserved, banner «الجلسة متوقفة» with resume button; resume restarts capture with a 1 s countdown. Android 14+ background-mic restriction is avoided by design (foreground only).
- **Rotation:** ViewModel survives; no session interruption.
- **Process death:** session ends; Room checkpoints (§11) preserve partial counts; on relaunch Home shows «ملخص الجلسة السابقة» (read-only summary). No audio/transcripts persisted.
- **Permission revoked mid-session:** stop, show rationale screen with re-grant flow.

### 10.5 Badges and honesty surfaces
- «الوضع الخفيف» badge when tiny or L1/L2 active (§8.6), with a one-line explanation; repeated in summary.
- Calibration progress UI on first run.
- Riwaya statement in onboarding: «هذا التطبيق يدعم رواية حفص عن عاصم فقط.»

### 10.6 Resync flow
Lost-tracking banner: «تعذّر تتبّع التلاوة. اضغط للبدء من آية محددة.» → tapping opens an ayah picker **within the selected range** → `tracker.reanchor()`; previous states preserved.

### 10.7 Required strings (Mo reviews Arabic before release)
- Disclaimer (About + onboarding): «هذا التطبيق وسيلة مساعدة للمراجعة، ولا يغني عن التلقي من معلّم مجاز.»
- Mic rationale: «نحتاج الميكروفون لسماع تلاوتك. الصوت يُعالَج على جهازك فقط ولا يُرسَل إلى أي مكان.»
- Lost tracking: §10.6. Riwaya: §10.5.
- Full RTL; Arabic primary, English secondary (all strings in resources); dark mode; font scaling; TalkBack content descriptions; permission-denied-permanently deep link.

---

## 11. Error handling and recovery (normative)

| Failure | Detection | User-facing | Recovery |
|---|---|---|---|
| Model load fails (corrupt asset, ONNX init error) | `ModelLoadException` from `load()` | Error card, no crash | Retry once → try other bundled model → if both fail: honest error screen, reader still usable |
| Asset SHA-256 mismatch | fetch script (build time); load-time guard | build fails / error card | re-fetch; never ship mismatched assets |
| Native decode crash | process death | §10.4 process-death path | checkpoints preserve partial summary; input guards (length, sample rate, finite samples) + instrumented tests minimize risk |
| Sustained RTF > 1 | measured backlog/RTF | degraded badge / L3 message | §8.6 ladder; never silently degrade |
| Mic taken / focus loss / call | AudioRecord error / focus callback | pause banner | manual resume (§10.4) |
| VAD load fails | init error | none (transparent) | energy fallback (§8.4); log in session meta |
| Calibration fails/timeout | probe exception | skip calibration | default to small + L1; allow manual override |
| Room write fails (storage full) | IOException | warning toast | session continues in memory; summary retried at end |
| Permission denied permanently | permission API | rationale + deep link | F7 flow |

**Checkpointing:** after each FINAL observation, persist current counts + pointer + config hash to Room (cheap, no audio/text). This is what makes process-death recovery honest.

---

## 12. Privacy, security, legal

1. **No `INTERNET`** in release manifest; CI fails on merged-manifest appearance. Strip `ACCESS_NETWORK_STATE` if injected (`tools:node="remove"`). Models ship inside the app.
2. Only `RECORD_AUDIO` runtime permission. Audio in memory only (S2 excepted, Phase 5).
3. No transcripts/audio in release logs. `android:allowBackup="false"` + restrictive `dataExtractionRules`. No exported components except launcher.
4. R8 enabled with JNI keep rules (§8.2); **release build must decode on a real device** (Phase 4 gate).
5. Play **Data safety: no data collected** — keep it true.
6. **Store listing constraints:** no tajweed/harakat promises; no "AI teacher"; **if WRONG is disabled at ship, do not claim mistake detection** — position as «متابعة التلاوة ومراجعة الحفظ». Disclaimer prominent. `applicationId` must not be `com.example.*` (Play policy) — Phase 4 blocker.
7. `THIRD_PARTY_NOTICES.md` + About credits: model authors (Abdou/quran-asr-*, MIT) **and training-data credits** (Tarteel/tlog CC-BY-4.0, EveryAyah reciters, Iqra'Eval); **Tanzil notice verbatim + link** (§7.1, also in derived-table headers); **Amiri Quran OFL text**; sherpa-onnx Apache-2.0 (incl. vendored Kotlin API); ONNX Runtime MIT; every dependency from the license report. `[VERIFY each — do not assume]`.
8. Gated data (benchmark, tlog): never commit/redistribute; respect terms. Mo's recordings: `eval/data/` git-ignored, never uploaded.

---

## 13. Evaluation protocol (go/no-go gate)

**Why:** published WER does not predict real use (V7, V4b). Decisions are made on phone-mic recordings of real users, **in the app's comparison space (§7.2)**.

### 13.1 Data collection protocol (Mo; git-ignored)
- **First look:** ≥50 clips (≥25 correct, ≥25 planted errors), noisy/room-echo + fast (hadr) samples included.
- **Gate set:** **disjoint held-out ≥60 correct clips** (rule of three, §13.4), plus ≥25 planted-error clips for detection metrics. **Assign dev/held-out split at recording time** (id ranges), never after tuning.
- **Diversity requirement for a non-indicative gate:** ≥3 speakers (include a child if possible — V4c), ≥2 devices, quiet+noisy, tartil+hadr, short surahs + mutashabihat-prone passages + long ayahs. Single-speaker results are labeled **indicative**.
- Estimated Mo effort: ~2–3 hours of recording (planning estimate, not a measurement).
- Format: `eval/data/<id>.wav` (16 kHz mono) + `eval/labels/<id>.json`:

```json
{
  "id": "clip_001", "split": "dev|heldout",
  "expected": { "surah": 1, "fromAyah": 1, "toAyah": 7 },
  "speaker": "adult_male|adult_female|child", "device": "pixel-7",
  "environment": "quiet|noisy", "speed": "tartil|hadr",
  "planted_errors": [
    { "type": "SKIP", "ayah": 3, "wordIndex": 2 },
    { "type": "SUBSTITUTE", "ayah": 5, "wordIndex": 1, "said": "..." }
  ]
}
```
- If Mo obtains `Quran-Lab/quranic-asr-benchmark` access: local evaluation only (V8), never commit audio, treat as indicative (V4b, A9 conflict of interest).

### 13.2 Pipeline
`eval/run_asr.py` (per model; `--standard` only for standard-space reference scoring) → `eval/hypotheses/<model>/<clip>.json` → `tools:eval` (**same `core:align` code as the app**) → `eval/reports/<date>.md`. Baseline: `eval/run_baseline.py` via whisper.cpp + verified GGML (V6), harness-only.

### 13.3 Metrics
| Metric | Definition |
|---|---|
| WER / CER | vs `N(expected)` **in model space**, per model, per environment; standard-space numbers optionally reported for comparability, labeled |
| Clip-level false-alarm rate | % of **correct** clips with ≥1 WRONG flag |
| Word-level false-alarm rate | WRONG flags per 100 words on correct clips |
| Detection rate | % of planted errors ending WRONG or CLOSE (not CORRECT); also WRONG-only |
| Tracking loss rate | % of clips needing resync |
| **Word-highlight latency** | p50/p95 from audio end-of-word to UI state, on device |
| **Tracking hit rate** | % of correctly recited words reaching CORRECT/CLOSE |
| RTF (W=8s and W=2.5s), peak RAM, load time | per model, per device |

### 13.4 Gate (initial targets; Mo may relax)
- Red WRONG flags ship **only if clip-level FA ≤5% on the held-out correct set**, **per model and per preset**.
- **Rule of three:** with n correct clips and zero false alarms, 95% upper bound ≈ 3/n → **≥60 clips** to demonstrate ≤5%. Fewer → report point estimate + upper bound, label **indicative**, keep WRONG rendered as CLOSE.
- Gate not met (or indicative): ship WRONG→CLOSE (amber), keep logic, record in `docs/DECISIONS.md`, report to Mo, **adjust store copy (§12.6)**.
- **No tuning on final held-out clips.** Every report states git commit, model revisions, config values, data counts, comparison space, and split assignment.
- **Phase 2 may legitimately end "indicative"** if Mo's recordings are absent (§18 default). This is a known outcome, not a failure — but it means no final model verdict and no red WRONG at ship. Communicate to Mo early.

---

## 14. Quality gates and budgets

**Testing:** unit (`core:quran`, `core:align` incl. null-timing mode, `core:session` with fake recognizer, ViewModels); golden/table tests (N(), alignment: planted skip/substitution/insertion/repetition/merge/split/boundary/ta'awwudh/basmala/jumps); property-based tracker tests (idempotency, independence, monotonicity); instrumented (fixture decode, Room migrations, permission flows, R8 release decode); Compose UI tests (main flows). Coverage ≥85% lines on `core:quran` + `core:align`.

**CI (GitHub Actions):** ktlint, detekt, Android Lint (warnings-as-errors for new code), unit tests, `assembleDebug`, `assembleRelease`, **merged-manifest INTERNET/ACCESS_NETWORK_STATE check**, dependency license report, migration tests.

**Budgets (targets; revise only with measured data + ADR):**
| Item | Target |
|---|---|
| Size: models 36+19=55 MB; jniLibs arm64 ~10–20 MB; app+Compose ~8–12 MB; font+text ~2–4 MB | **AAB download ≤80 MB target; report install size.** If over: options (a) Play Asset Delivery install-time pack (app still needs no INTERNET), (b) drop tiny with Mo approval, (c) accept with ADR. arm64-only. |
| Cold start | ≤2 s mid-range |
| Model load | ≤1.5 s (small) |
| RTF mid-range | ≤0.5 (W=8s); also report W=2.5s |
| **p95 word-highlight latency** | **≤1.5 s mid-range** |
| Peak session RAM | report; investigate >400 MB |
| Battery 15-min session | measure and report (no fixed MVP target) |

---

## 15. Persistence

**Room (exportSchema=true, versioned migrations, NO destructive fallback in release, CI migration tests):**
- `SessionEntity(id, startedAt, endedAt, mode, surah, fromAyah, toAyah, modelId, modelRev, preset, configHash, degradedLevel, countsCorrect, countsClose, countsWrong, countsMissed, countsHinted, countsExtra, durationMs, deviceNote)`
- `WordFlagEntity(sessionId, globalWordIndex, surah, ayah, state)` — for the tappable flagged list.
- Checkpoint writes after each FINAL observation (§11). No audio, no transcripts (S2 excepted, Phase 5).

**DataStore keys:** `language, theme, fontScale, preset, modelOverride, calibration{model,rtf,timestamp}, riwayaStatementShown, autoScrollPauseMs`.

---

## 16. Phased plan

| Phase | Content | Done when | Needs from Mo |
|---|---|---|---|
| **0 — Discover/scaffold** | Audit existing code (`docs/AUDIT.md`) or scaffold §6.1; Gradle KTS + version catalog; Hilt; detekt/ktlint; CI skeleton; docs skeleton; .gitignore (models, eval/data, keystores) | CI green; merged-manifest check passes; versions + Q4 decision recorded | — |
| **0.5 — Spike (timebox 2–3 days)** | S1 fetch models (pinned rev + SHA-256) + archive location question; S2 vendor kotlin-api + jniLibs (same tag), R8 rules, **decode fixture on API 26 emulator**, confirm timestamp unit/frame shift/FeatureConfig/minSdk (Q8); S3 silero_vad.onnx + VAD smoke; S4 Tanzil asset + checksum + license file; S5 Amiri Quran fetch + shaping smoke; S6 baseline GGML fetch (V6 hash) | `docs/VERIFICATION.md` entries with evidence; spike report; **any failure → flag Mo before Phase 1** | archive location |
| **1 — Quran + align + harness** | `core:quran` (asset, integrity tests, N() + golden tests, exact tokens.txt documented); `core:align` (§9 complete incl. independence, precedence, null-timing mode); `tools:eval` + `eval/run_asr.py` (synthetic-capable) | ≥85% coverage; harness report exists; Q3/Q5 documented; N() self-validation (§7.5) recorded | text/font approval (default applies, flagged) |
| **2 — On-device ASR/audio** | `core:asr`, `core:audio` (VAD/segmenter/partialWindow), `core:session`, calibration + degraded ladder | instrumented fixture decode CER below target; RTF/RAM/battery/latency on ≥1 real device logged; Q6/Q7 answered; **may end "indicative" without Mo data (§13.4)** | recordings (§13.1), devices |
| **3 — Features/UI** | F1–F8 per §3/§10/§11 on real device | UI tests cover follow-along, tasmee', denied permission, rotation, interruption, process death; manual script executed; accessibility passes (TalkBack, contrast, 2.0×) | Arabic string review |
| **4 — Hardening/release** | perf/battery pass; error paths; R8 release decode on device; size budget measured; notices complete; limitations text; evaluation report; **applicationId renamed (blocker)**; signing via Mo keystore | release decodes on device; CI green; all §17 docs exist; tag `v0.1.0`; STATUS lists open items | final applicationId, keystore, store copy approval |
| **5 — Optional (written approval)** | S1, S2, model upgrades (must pass §13 gate), Plan B ONNX-direct + confidence features, fine-tuning, Warsh, iOS | per-feature ADR + gate | approval |

*Planning estimates (not measurements): P0 1–2d · P0.5 2–3d · P1 5–8d · P2 5–8d · P3 6–10d · P4 3–5d ≈ 4–6 weeks for one engineer + Mo reviews.*

---

## 17. Repository and documentation

Required (short, current): `README.md` (build, fetch models, tests, harness); `docs/ARCHITECTURE.md`; `docs/DECISIONS.md` (ADR log: versions, Q closures, deviations, gate outcomes, `[MO]` ratifications); `docs/VERIFICATION.md` (evidence log for every `[VERIFY]` closure: date, source, command/output); `docs/STATUS.md` (per phase: done/blocked/next/Needs from Mo, with outputs); `docs/EVALUATION.md`; `docs/TESTING.md`; `docs/PRIVACY.md`; `docs/DEVICES.md`; `THIRD_PARTY_NOTICES.md`; `LICENSE`.
Conventions: Conventional Commits; small commits; one logical change per PR; no large binaries in git.

---

## 18. Inputs needed from Mo (proceed with default if absent; list in STATUS)

| Input | Default if missing |
|---|---|
| Recordings per §13.1 (≥50 first look; ≥60 correct held-out + ≥25 error clips for gate; ≥3 speakers) | harness on synthetic hypotheses; **all accuracy claims "not measured on real audio"; Phase 2 ends indicative; WRONG ships as CLOSE** |
| Test devices (≥1 low-end 3–4 GB, ≥1 flagship) → `docs/DEVICES.md` | whatever is available; limitation reported |
| **Final applicationId (Play rejects com.example.*)** | dev placeholder; **Phase 4 blocked** |
| Quran text source + font approval | Tanzil + Amiri Quran defaults, flagged pending |
| Model archive location (§8.2) | ask once; keep local until answered |
| Code license | Apache-2.0 |
| Release keystore | debug signing; never committed |
| Arabic strings + disclaimer + store copy review | provided strings, flagged unreviewed |
| Ratification of `[MO]` decisions (presets §9.5, summary §9.6, font §7.7, tiny shipping policy §8.1) | implement as written, flagged |

---

## 19. Risks and mitigations

| Risk | Mitigation |
|---|---|
| ASR auto-corrects reciter toward correct text (closed-vocabulary effect) | CTC without LM (V1); measure detection rate (§13); honest UI/store text |
| Phone-mic domain shift | evaluate on real phone recordings; phone subset is the signal (V2/V7) |
| **Benchmark "held-out" is relative to benchmark authors, not model authors (V4b)** | treat published numbers as indicative; Mo's own held-out set is the gate |
| **Children / very noisy rooms underrepresented (V4c)** | include child + noisy clips in §13.1; conservative defaults; limitations text |
| Fast recitation / chunk boundaries | boundary rule, overlap, partialWindow, evidence aggregation; tune in harness |
| **Partial-decode latency (RTF×W arithmetic)** | §8.4 scheduling law + latency SLO + degraded ladder |
| False alarms destroy trust | Conservative default; independence rule; per-model/per-preset gate; suggestive wording |
| **Warsh/other-riwaya reader gets systematic false flags** | onboarding riwaya statement; lost-tracking message variant; Hafs-only scope |
| Self-reported numbers | never quote as ours; re-measure; label space/subset |
| sherpa-onnx API/ABI drift | pin tag; vendored API + .so from same release; fixture instrumented test |
| Low-end devices too slow | tiny fallback + calibration + ladder; **tiny gated separately, badge disclosed** |
| Quran text corruption | checksum + integrity tests + read-only asset |
| Mutashabihat | range-constrained tracking; re-anchor rule |
| License/attribution gaps (models, data, text, **font EULA uncertainty**) | notices + `[VERIFY]` every license; Amiri OFL default; KFGQPC only with EULA |
| Over-claiming (tajweed, "AI teacher", mistake detection when WRONG disabled) | §2 non-goals + §12.6 store constraints + disclaimer |
| Single-author community model repo may change/disappear | pin revision + SHA-256; **archive exact shipped files + licenses** (Mo's storage); interface isolation |
| **Play rejects com.example.* applicationId** | Phase 4 blocker; Mo input early |
| Native decode crash | input guards, fixture tests, checkpointing + graceful process-death path |

---

## Appendix A — Sources (verified 2026-11-21 unless noted)

- A1: https://huggingface.co/Abdou/quran-asr-small (card + transcribe.py read)
- A2: https://huggingface.co/Abdou/quran-asr-tiny (card read; int8 numbers, leakage disclosure, folds, limitations)
- A3: https://huggingface.co/tarteel-ai/whisper-base-ar-quran
- A4: https://huggingface.co/koribeche/wird-asr-model (GGML q5_1, 59.7 MB, SHA-256 published — verified)
- A5: https://huggingface.co/datasets/Quran-Lab/quranic-asr-benchmark (gated)
- A6: https://huggingface.co/datasets/tarteel-ai/everyayah
- A7: https://huggingface.co/datasets/tarteel-ai/tlog (gated)
- A8: https://huggingface.co/Muno459/fastconformer-quran (alternative; benchmark maintained by a party publishing competing models — indicative)
- A9: https://huggingface.co/spaces/Quran-Lab/quranic-asr-leaderboard (same caveat)
- S1: https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/OfflineRecognizer.kt (Q1)
- S2: https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/Vad.kt (VAD)
- S3: https://k2-fsa.github.io/sherpa/onnx/android/ + discussions #1950 (Q4: copy kotlin-api + jniLibs; no official Maven artifact; Maven metadata 404 checked twice)
- S4: https://tanzil.net/docs/text_license (Q5 text, verbatim terms)
- S5: https://github.com/aliftype/amiri + https://fontsource.org/fonts/amiri-quran/about (OFL 1.1); google/fonts issue #8513 (served-file mixup); fonts.qurancomplex.gov.sa (redesigned, font pages unavailable)

## Appendix B — Glossary
تسميع (tasmee'): reciting from memory to be checked · حفص/ورش: recitation traditions · الحركات (harakat): vowel marks — **not detectable** · التجويد (tajweed): **out of scope** · التعوّذ/البسملة: optional leading phrases · WER/CER: word/character error rate · RTF: compute time ÷ audio duration · CTC: frame-level decoding, no autoregressive decoder · **model space / raw Uthmani**: the model's bare-letter spelling (§7.2) · **independent observations**: §9.2.

## Appendix C — v1.0 → v2.0 resolution map
| v1.0 gap / review point | Resolved in |
|---|---|
| G1 independence undefined | §9.2 (normative identity + independence) |
| G2 partial window undefined / no latency SLO / non-interruptible inference | §8.4 `partialWindowMs` + scheduling law; §6.2 threading; §14 SLO |
| G3 null timing/confidence semantics; Q1 unknown | §5 Q1-CLOSED (source-verified); §6.2 contract + fallback; §9.5 confidence-free presets; Plan B → Phase 5 |
| G4 comparison space / §7.3 threshold mismatch | §7.2 space decision; §7.5 validation protocol (small only, model space, justified threshold); §13.3 |
| G5 per-model gate / tiny silent fallback / V4 fp32-vs-int8 / calibration warmup | §8.1, §8.5, §8.6; V4/V4b/V4c corrected |
| G6 state precedence / transitions / lost-timer-on-silence / jumps | §9.3, §9.4 |
| External review: Phase 0.5 spike | §16 Phase 0.5 (timeboxed, authoritative for Phase 1–2) |
| External review: UI specs (auto-scroll, hint per mode, process death, notification) | §10.3–10.6 (no FGS/notification in MVP) |
| External review: error handling | §11 (table + checkpointing) |
| External review: CLOSE counted as correct? | §9.6 `[MO]` decision: CORRECT only in X of Y |
| External review: Room migrations | §15 |
| External review: Phase 2 may end indicative | §13.4 + §16 + §18 (explicit Mo communication) |
| External review: applicationId / out-of-order / speeds / export / durations | §4+§12.6 (Play blocker); §9.4 re-anchor; §13.1 speed field; §2 export non-goal; §16 planning estimates |
| Medium gaps: size arithmetic, split-at-collection, ≥3 speakers+child, Warsh reader, screen-off, EXTRA dedup, baseline runtime, ACCESS_NETWORK_STATE, VAD padding | §14; §13.1; §19; §10.4; §9.4; §13.2 (A4 verified); §12.1; §8.4 |