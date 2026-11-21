# ARCHITECTURE

Phase 0 scaffold implementing spec §6. Multi-module Gradle, UDF (Compose +
StateFlow), Coroutines/Flow, Hilt (KSP), Room, DataStore.

```
app/                     shell: MainActivity, DI wiring, manifest (no INTERNET)
core/
  quran/                 read-only Quran data + N() (Phase 1)     [Android lib, JVM-pure logic]
  align/                 RecitationTracker + feedback policy      [pure JVM]
  asr/                   RecitationRecognizer + sherpa-onnx impl  [Android lib; vendored kotlin-api]
  audio/                 mic capture, VAD, segmenter (Phase 2)
  session/               audio → asr → align orchestration (Phase 2)
  data/                  Room (history) + DataStore (settings) (Phase 2)
  designsystem/           Compose theme, RTL, Quran text composables
feature/{reader,recite,history,settings}/
tools/eval/              JVM CLI scorer (same core:align as app)  [pure JVM]
scripts/fetch_models.py  pinned model fetch + SHA-256
```

Data flow (§6.3): Mic 16 kHz → ring buffer → VAD/segmenter →
Recognizer.decode → Observation → Tracker.onObservation → TrackerUpdate →
SessionViewModel (StateFlow) → Compose (highlight/reveal/scroll) → Room
checkpoint after each FINAL observation.

Normative contracts live in code now:
- `core/align/…/Tracker.kt` — WordState, TrackerConfig (all §9.1 tunables),
  Observation identity + independence (§9.2, unit-tested).
- `core/asr/…/RecitationRecognizer.kt` — §6.2 interface + AsrResult/AsrWord
  (confidence always null, Q1-CLOSED) + ModelLoadException.

Threading (§6.2): inference on a dedicated single-thread dispatcher; queue
depth 1; drop stale results by windowEndMs; capture thread never waits on
inference. Implemented in Phase 2.
