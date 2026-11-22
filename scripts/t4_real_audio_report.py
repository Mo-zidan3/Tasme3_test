#!/usr/bin/env python3
"""T4 §7.5 + §13 — real-audio harness on Mo's 6 clips (INDICATIVE).

N() self-validation + clip-level metrics on the pinned quran-asr-small:
- decode each clip with sherpa-onnx 2.0.2 (same API as CI),
- N()-normalize both sides (letters-only, hamza-folded — the app's model space),
- report WER/CER per clip, false-alarm rate, detection rate, rule-of-three bound.

HONEST LABELS (see eval/data/PROVENANCE.md): these are 3 speakers × PROFESSIONAL
STUDIO recitations, NOT phone-mic recordings. The §13 phone gate (≥60 held-out
clips, ≥3 speakers, phone mic) is NOT met → this report is INDICATIVE.
"""
import json
import re
import wave
from pathlib import Path

import numpy as np
import sherpa_onnx

REPO = Path(__file__).resolve().parent.parent
MODEL = REPO / "app/src/main/assets/models/quran-asr-small/model.int8.onnx"
TOKENS = REPO / "app/src/main/assets/models/quran-asr-small/tokens.txt"
MANIFEST = Path("/tmp/real_clips_manifest.json")

# letters-only model space: keep Arabic letters, fold hamza carriers + alef-wasla
# onto their base letters (the ASR alphabet: plain letters, no diacritics)
LETTER_RE = re.compile(r"[^\u0621-\u064A\u0671]")
FOLD = {"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي", "ؤ": "و", "ئ": "ي", "ه": "ه"}


def N(s: str) -> str:
    # keep whitespace so WER word-splitting works; CER strips it at call sites
    s = re.sub(r"[^\s\u0621-\u064A\u0671]", "", s)
    for k, v in FOLD.items():
        s = s.replace(k, v)
    return " ".join(s.split())


def wer(ref_words, hyp_words):
    dp = [[0] * (len(hyp_words) + 1) for _ in range(len(ref_words) + 1)]
    for i in range(len(ref_words) + 1):
        dp[i][0] = i
    for j in range(len(hyp_words) + 1):
        dp[0][j] = j
    for i in range(1, len(ref_words) + 1):
        for j in range(1, len(hyp_words) + 1):
            dp[i][j] = min(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + (ref_words[i - 1] != hyp_words[j - 1]),
            )
    return dp[-1][-1] / max(1, len(ref_words))


def cer(ref, hyp):
    dp = list(range(len(ref) + 1))
    for j in range(1, len(hyp) + 1):
        prev, dp[0] = dp[0], j
        for i in range(1, len(ref) + 1):
            cur = dp[i]
            dp[i] = min(dp[i] + 1, dp[i - 1] + 1, prev + (ref[i - 1] != hyp[j - 1]))
            prev = cur
    return dp[len(ref)] / max(1, len(ref))


def main() -> int:
    clips = json.load(open(MANIFEST, encoding="utf-8"))["clips"]
    rec = sherpa_onnx.OfflineRecognizer.from_zipformer_ctc(
        model=str(MODEL), tokens=str(TOKENS), num_threads=2)

    print("### T4 real-audio harness — Mo's 6 clips, quran-asr-small (model space N())")
    print("### LABEL: INDICATIVE — 3 speakers × STUDIO recitations; §13 phone gate NOT met\n")
    rows = []
    for c in clips:
        with wave.open(c["file"]) as w:
            pcm = np.frombuffer(
                w.readframes(w.getnframes()), dtype=np.int16
            ).astype(np.float32) / 32768.0
        s = rec.create_stream()
        s.accept_waveform(16000, pcm)
        rec.decode_stream(s)
        hyp_raw = "".join(s.result.tokens).strip()
        hyp_n = N(hyp_raw)
        ref_n = N(c["expectedUthmani"])
        w = wer(ref_n.split(), hyp_n.split())
        cr = cer(ref_n.replace(" ", ""), hyp_n.replace(" ", ""))
        # clip-level stats: reciter repeats = re-covered audio (expected words re-observed)
        rows.append({"clipId": c["clipId"], "speaker": c["speaker"],
                     "audio_s": round(len(pcm) / 16000, 1),
                     "refWords": len(ref_n.split()), "hypWords": len(hyp_n.split()),
                     "WER": round(w, 4), "CER": round(cr, 4)})
        print(f"{c['clipId']:24} audio={len(pcm)/16000:5.1f}s "
              f"WER={w:6.1%} CER={cr:6.1%} refWords={len(ref_n.split())} hypWords={len(hyp_n.split())}")

    # aggregate: macro over clips + micro over words
    tot_ref = sum(r["refWords"] for r in rows)
    tot_hyp = sum(r["hypWords"] for r in rows)
    macro_wer = sum(r["WER"] for r in rows) / len(rows)
    macro_cer = sum(r["CER"] for r in rows) / len(rows)
    micro_wer = sum(r["WER"] * r["refWords"] for r in rows) / max(1, tot_ref)

    # clip-level false-alarm rate (multiset difference: hyp words NOT matched by a ref
    # word occurrence — bag approximation; honest limitation: no alignment, repetitions in
    # recitations inflate the denominator, NOT hidden).
    # detection rate: ref words matched by ≥1 hyp word occurrence.
    fa_hits, det_hits = 0, 0
    for c, r in zip(clips, rows):
        with wave.open(c["file"]) as w:
            pcm = np.frombuffer(
                w.readframes(w.getnframes()), dtype=np.int16
            ).astype(np.float32) / 32768.0
        s = rec.create_stream()
        s.accept_waveform(16000, pcm)
        rec.decode_stream(s)
        hyp_words = N("".join(s.result.tokens)).split()
        ref_words = N(c["expectedUthmani"]).split()
        from collections import Counter
        h = Counter(hyp_words)
        rr = Counter(ref_words)
        fa_hits += sum((h - rr).values())          # hyp occurrences with no ref counterpart
        det_hits += sum((rr & h).values())         # ref occurrences present in hyp
    fa_rate = fa_hits / max(1, tot_hyp)
    det_rate = det_hits / max(1, tot_ref)

    # rule-of-three upper bound (95% one-sided) on the per-clip CORRECT rate with n=6:
    # 3/n heuristic is for large n; for 6 clips the Clopper-Pearson-style bound for
    # k successes in n trials: p_upper ≈ (k + 2.996) / (n + 2.996 + 1 - k)… use the exact
    # beta: p_upper = BetaInv(0.95, k+1, n-k+1). For k=n=6: 1 - 0.05^(1/6) = 0.3912? No —
    # the no-failure upper bound is 1 - 0.05**(1/n) for "all successes" one-sided 95%.
    n = len(rows)
    perfect = sum(1 for r in rows if r["CER"] == 0.0)
    bound = 1 - 0.05 ** (1 / n) if perfect == n else None

    print()
    print(f"macro WER {macro_wer:.1%} / macro CER {macro_cer:.1%} over {n} clips (INDICATIVE)")
    print(f"micro WER (word-weighted) {micro_wer:.1%}")
    print(f"clip-level false-alarm rate {fa_rate:.1%} ({fa_hits}/{tot_hyp} hyp word occurrences)")
    print(f"clip-level detection rate {det_rate:.1%} ({det_hits}/{tot_ref} ref word occurrences)")
    # rule-of-three (95% one-sided) on the per-clip defect rate, exact binomial:
    # with k defect clips in n, upper bound = Beta(k+1, n-k) at 0.95 → for k=0 it is 3/n.
    k = n - perfect
    # exact Clopper-Pearson upper bound via beta quantile (scipy-free approximation):
    # for small n use the standard "3/n" only when k==0; else report the observed rate with n.
    if k == 0:
        print(f"rule-of-three upper bound (95% one-sided): per-clip CER-defect rate ≤ {3/n:.1%}")
    else:
        print(f"defect clips (CER>0): {k}/{n} observed = {k/n:.1%} (rule-of-three not applicable — defects observed)")
    print("NOTE: no alignment — repeats/re-covers inflate hyp counts; FA is bag-level, "
          "NOT the app's session-level EXTRA rate.")
    json.dump(rows, open("/tmp/t4_rows.json", "w"), indent=1)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
