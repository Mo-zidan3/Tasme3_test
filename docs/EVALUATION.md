# EVALUATION

Protocol fixed in spec §13. Phase 0 status: harness scaffolded (tools:eval
stub); no runs, no numbers. All published WER numbers are author-reported;
ours will be measured in the model-native raw-Uthmani space (§7.2) and label
split + environment. Gate: clip-level FA ≤5% on ≥60 held-out correct clips
(rule of three) per model and per preset; otherwise WRONG ships as CLOSE.

## N() collision analysis (V-024 — measured, host)
The model's comparison space is letters-only (29 letters; V-020). N() therefore makes some
distinct Quranic words indistinguishable:
- 2,771 of 14,636 normalized forms (18.9%) map to more than one distinct original word.
- 68.9% of all word instances (53,629/77,881) are part of such collisions — dominated by
  harakat-only variants (مِن/مَن, ٱللَّهِ/ٱللَّهُ/ٱللَّهَ, إِنَّ/أَن) which the model cannot
  express by design, and a few true mergers (قُلِ/قَٰلَ → قل; كَانَ/كَأَن → كان).
- 275 ayahs (96 groups) are letter-identical to another ayah; 94 identical pairs exist inside
  single surahs (e.g. the six basmala+الم openers; 2:5=31:5; 2:47=2:122).

Consequences for §13 metrics: substitutions between colliding pairs are invisible in model
space (sim=1.0 → CORRECT). This is a property of the model's output alphabet, NOT a defect of
N(); no normalization change can recover information the model never emits. Metrics that need
those distinctions (e.g. harakat-level scoring) must be run in standard-space reference scoring
via `eval/run_asr.py --standard` (labeled, eval-only).


## T11 reproducibility (V-043/V-044)
- **REPLAY (CI, reproducible)**: `scripts/t11_replay.sh` loops the committed derived text
  (`eval/hypotheses/t11/*-whole.json` / `*-app.json` + `eval/expected/t11/*.json`) through
  the REAL `RecitationTracker` via `tools:eval`. No audio needed — this is what CI runs.
- **REGENERATION (needs Mo's audio)**: `scripts/t11_app_shaped_eval.py` re-decodes Mo's
  licensed clips through the app-shaped path (silero VAD → Segmenter law → per-window
  decode). Input layout + template: `eval/data/clips-manifest.template.json`; clips are
  16 kHz mono WAVs converted with
  `ffmpeg -v error -y -i in.mp3 -ac 1 -ar 16000 out.wav`.
  Authorization of record: `eval/data/PROVENANCE.md`. Audio is NEVER committed beyond the
  one authorized 54:10 fixture.
- The 309% "app-shaped WER" word-union number from the first local attempt is RETIRED —
  it was a scoring bug (overlapping partials re-cover audio by design; per-word tracker
  states are the metric, V-043). Do not quote it.
