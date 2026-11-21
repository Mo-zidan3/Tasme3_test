#!/usr/bin/env python3
"""eval/run_asr.py — §13.2 pipeline: decode clips with the pinned model via sherpa-onnx
and emit hypotheses JSON in the SAME format the app consumes (model space, greedy CTC).

Usage:
  python3 eval/run_asr.py <clips-dir-or-file> [--model small|tiny] [--out eval/hypotheses/<model>/]
  python3 eval/run_asr.py --standard ...   # rewrite words via spelling.json for standard-space
                                          # reference scoring ONLY (§7.2; never at app runtime)

Hypotheses JSON (consumed by tools:eval):
{
  "clips": [
    {"clipId": "clip_001",
     "observations": [
       {"words": [["بسم", 0, 300], ...],   // [text, startMs, endMs] session-global ms
        "windowStartMs": 0, "windowEndMs": 8000, "isFinal": true, "modelRev": "small@0c2744b"}]}]}

Timestamps are SECONDS from sherpa-onnx; multiplied by 1000 here; endMs = lastTs*1000 + 40
(frame shift 40 ms, V-015). Words are assembled between ▁ separators.

Synthetic mode (no real audio available in the sandbox):
  python3 eval/run_asr.py --synthetic expected-words.json --out eval/hypotheses/synthetic/
produces the same JSON from the expected words with planted errors — used to exercise
tools:eval end-to-end. Everything from synthetic mode is labeled "not measured on real audio".
"""
import argparse
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MODELS = {
    "small": (REPO / "app/src/main/assets/models/quran-asr-small", "small@0c2744b9d70aaf45ba574c30c5d318c575258e66"),
    "tiny": (REPO / "app/src/main/assets/models/quran-asr-tiny", "tiny@a291b33fd5a31cc4f212363ee1ba2d56d9710b49"),
}
FRAME_SHIFT_MS = 40  # V-015 (seconds, 10ms hop × subsampling 4)


def decode_clips(clips, model_dir, model_rev, standard: bool):
    import sherpa_onnx
    import soundfile as sf
    import numpy as np

    recognizer = sherpa_onnx.OfflineRecognizer.from_zipformer_ctc(
        model=str(model_dir / "model.int8.onnx"),
        tokens=str(model_dir / "tokens.txt"),
        num_threads=2,
    )
    table = {}
    if standard:
        sp = model_dir / "spelling.json"
        if sp.exists():
            table = json.loads(sp.read_text(encoding="utf-8"))
    out = []
    for clip in clips:
        pcm, sr = sf.read(str(clip), dtype="float32", always_2d=True)
        pcm = pcm.mean(axis=1)
        stream = recognizer.create_stream()
        stream.accept_waveform(sr, pcm)
        recognizer.decode_stream(stream)
        result = stream.result
        words = assemble_words(result, 0, table)
        out.append({
            "clipId": clip.stem,
            "observations": [{
                "words": words,
                "windowStartMs": 0,
                "windowEndMs": int(len(pcm) / sr * 1000),
                "isFinal": True,
                "modelRev": model_rev,
            }],
        })
    return out


def assemble_words(result, segment_start_ms, table):
    """Words between ▁ separators; session-global ms; endMs += frameShift (V-015)."""
    words = []
    cur_letters, cur_start = [], None
    for tok, ts in zip(result.tokens, result.timestamps):
        if tok == "▁":
            if cur_letters:
                text = "".join(cur_letters)
                text = table.get(text, text)
                words.append([text, int(cur_start * 1000) + segment_start_ms,
                              int(cur_last_ts * 1000) + segment_start_ms + FRAME_SHIFT_MS])
                cur_letters, cur_start = [], None
        else:
            if cur_start is None:
                cur_start = ts
            cur_last_ts = ts
            cur_letters.append(tok)
    if cur_letters:
        text = "".join(cur_letters)
        text = table.get(text, text)
        words.append([text, int(cur_start * 1000) + segment_start_ms,
                      int(cur_last_ts * 1000) + segment_start_ms + FRAME_SHIFT_MS])
    return words


def _stream(words_timed, model_rev="synthetic", partial_window=2500, partial_every=1000,
            pause_final=500, pad=300):
    """App-shaped observation stream (same law as core:align SegmenterSimulator):
    trailing partials every partial_every ms; pause-final covering the segment."""
    if not words_timed:
        return []
    obs = []
    seg_start = words_timed[0][1]
    seg_end = words_timed[-1][2]
    t = seg_start + partial_every
    while t < seg_end:
        w_start = max(seg_start, t - partial_window)
        in_win = [[w, s, e] for (w, s, e) in words_timed if e > w_start and s < t]
        obs.append({"words": in_win, "windowStartMs": w_start - pad, "windowEndMs": t + pad,
                    "isFinal": False, "modelRev": model_rev})
        t += partial_every
    obs.append({"words": words_timed, "windowStartMs": seg_start - pad,
                "windowEndMs": seg_end + pad, "isFinal": True, "modelRev": model_rev})
    return obs


def synthetic(expected_words_json: Path):
    """Fabricate hypotheses from expected words with planted errors (§13-style, no real audio).
    Observation streams mirror the app's segmenter law (partials + pause-final), matching the
    SegmenterSimulator used in core:align tests."""
    spec = json.loads(expected_words_json.read_text(encoding="utf-8"))
    words = spec["words"]
    clips = []
    # clip A: correct recitation → expect all CORRECT, zero WRONG flags
    a = [[w, i * 400, i * 400 + 350] for i, w in enumerate(words)]
    clips.append({"clipId": "syn_correct_001", "observations": _stream(a)})
    # clip B: skip word 11 (يلد) with its slot silent → MISSED
    b = [[w, i * 400, i * 400 + 350] for i, w in enumerate(words) if i != 11]
    clips.append({"clipId": "syn_skip_002", "observations": _stream(b)})
    # clip C: persistent substitution word 15 (يكن→كين, sim 0.333) → WRONG under both presets
    c = [[("كين" if i == 15 else w), i * 400, i * 400 + 350] for i, w in enumerate(words)]
    clips.append({"clipId": "syn_wrong_003", "observations": _stream(c)})
    return clips


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input", nargs="?", help="clips dir/file, or expected-words.json with --synthetic")
    ap.add_argument("--model", default="small", choices=list(MODELS))
    ap.add_argument("--standard", action="store_true", help="spelling.json rewrite (eval-only)")
    ap.add_argument("--synthetic", action="store_true")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()
    if not args.input:
        ap.error("input required")
    if args.synthetic:
        clips = synthetic(Path(args.input))
        model_dir, model_rev = MODELS["small"]
        root = {"clips": clips, "note": "SYNTHETIC hypotheses — NOT measured on real audio"}
    else:
        p = Path(args.input)
        clips = sorted(p.glob("*.wav")) if p.is_dir() else [p]
        model_dir, model_rev = MODELS[args.model]
        root = {"clips": decode_clips(clips, model_dir, model_rev, args.standard)}
    out = Path(args.out) if args.out else REPO / "eval/hypotheses" / ("synthetic" if args.synthetic else args.model)
    out.mkdir(parents=True, exist_ok=True)
    (out / "hypotheses.json").write_text(json.dumps(root, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"wrote {out / 'hypotheses.json'} ({len(root['clips'])} clips)")


if __name__ == "__main__":
    sys.exit(main())
