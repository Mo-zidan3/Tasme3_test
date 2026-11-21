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
