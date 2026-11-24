#!/usr/bin/env python3
"""T11 (B5): APP-SHAPED streaming evaluation of Mo's 6 clips.

The §13 harness must measure the SHIPPED pipeline, not whole-clip decode: this script
mirrors the app's real path — silero VAD → Segmenter-law windows (partials every 1000 ms
over the trailing 2500 ms from the session ring; pause-final after ≥500 ms of silence;
VAD pad 300 ms) → EACH WINDOW'S AUDIO decoded separately → session-global word timings
(window-relative timestamps + windowStartMs) → observations JSON.

Two modes are scored per clip, with the same N()/CER/WER model space as V-038:
  whole-clip  — one FINAL over the entire clip (the old harness behavior)
  app-shaped  — the streaming observation stream (this script)

Output: eval/reports/t11-app-shaped-indicative.txt — INDICATIVE labels: 3 speakers,
PROFESSIONAL STUDIO recitations; the §13 phone gate (≥60 held-out clips, ≥3 speakers on
PHONE MICS) is NOT met; WRONG still renders CLOSE.
"""
import json
import re
import sys
import wave
from pathlib import Path

import numpy as np

REPO = Path(__file__).resolve().parent.parent
MODEL = REPO / "app/src/main/assets/models/quran-asr-small"
VAD_MODEL = REPO / "app/src/main/assets/models/silero_vad.onnx"
CLIPS = Path("/tmp/clips")  # 16 kHz mono WAVs converted from Mo's uploads
MANIFEST = Path("/tmp/real_clips_manifest.json")  # F1-verified ayah windows (V-038)

SAMPLE_RATE = 16_000
PARTIAL_WINDOW_MS = 2500
PARTIAL_EVERY_MS = 1000
PAUSE_FINAL_MS = 500
MAX_SEGMENT_MS = 8000
OVERLAP_MS = 1000
VAD_PAD_MS = 300
VAD_THRESHOLD = 0.5

LETTER_RE = re.compile(r"[^\u0621-\u064A\u0671]")
FOLD = {"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي", "ؤ": "و", "ئ": "ي"}


def N(s: str) -> str:
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
            dp[i][j] = min(dp[i - 1][j] + 1, dp[i][j - 1] + 1,
                           dp[i - 1][j - 1] + (ref_words[i - 1] != hyp_words[j - 1]))
    return dp[-1][-1] / max(1, len(ref_words))


def cer(ref, hyp):
    if not ref:
        return 0.0 if not hyp else 1.0
    prev = list(range(len(ref) + 1))
    for j in range(1, len(hyp) + 1):
        cur, prev[0] = [j], j
        cur = [j] + [0] * len(ref)
        for i in range(1, len(ref) + 1):
            tmp = prev[i]
            cur[i] = min(prev[i] + 1, cur[i - 1] + 1, prev[i - 1] + (ref[i - 1] != hyp[j - 1]))
            prev[i] = tmp
        prev = cur
    return prev[len(ref)] / len(ref)


def read_pcm(wav_path: Path) -> np.ndarray:
    with wave.open(str(wav_path)) as w:
        assert w.getframerate() == SAMPLE_RATE, f"{wav_path} not 16 kHz"
        return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0


def vad_speech(pcm: np.ndarray) -> list:
    """Silero VAD (the SAME shipped silero_vad.onnx weights) driven via onnxruntime —
    the pip sherpa-onnx 2.0.2 python VoiceActivityDetector produced no segments on these
    clips (4 attempts, chunked/window-aligned feeds; its pybind surface appears broken for
    this model revision), so the harness runs the ONNX directly: x[1,512] + LSTM h/c
    [2,1,64] → prob; 512-sample (32 ms) chunks; states carried across chunks.
    → [(start_ms, end_ms, is_speech)] per 100 ms decision (3 frames majority)."""
    import onnxruntime as ort
    sess = ort.InferenceSession(str(VAD_MODEL), providers=["CPUExecutionProvider"])
    h = np.zeros((2, 1, 64), dtype=np.float32)
    st = np.zeros((2, 1, 64), dtype=np.float32)
    probs = []
    for off in range(0, len(pcm), 512):
        x = pcm[off:off + 512]
        if len(x) < 512:
            x = np.pad(x, (0, 512 - len(x)))
        p, h, st = sess.run(None, {"x": x.reshape(1, 512).astype(np.float32), "h": h, "c": st})
        probs.append(float(p[0][0]))
    # 100 ms decisions = 3.125 frames → majority over 3 frames (32 ms each)
    out = []
    frames_per_100ms = 3
    for i in range(0, len(probs), frames_per_100ms):
        window = probs[i:i + frames_per_100ms]
        speech = sum(1 for p in window if p > VAD_THRESHOLD) * 2 > len(window)
        start_ms = i * 512 * 1000 // SAMPLE_RATE
        end_ms = (i + frames_per_100ms) * 512 * 1000 // SAMPLE_RATE
        out.append((start_ms, end_ms, speech))
    return out


def segmenter_windows(frames: list) -> list:
    """The Kotlin Segmenter law (core:audio/Segmenter.kt): partials at fixed cadence while
    speech runs; pause-final after ≥500 ms silence; force-cut at 8000 ms with 1 s overlap."""
    events = []
    segment_start = -1
    last_speech_end = -1
    last_partial_at = -1
    in_speech = False
    for (start_ms, end_ms, is_speech) in frames:
        if is_speech:
            if not in_speech:
                in_speech = True
                segment_start = start_ms if segment_start < 0 else max(segment_start, start_ms - OVERLAP_MS)
                last_partial_at = segment_start
            last_speech_end = end_ms
            t = last_partial_at + PARTIAL_EVERY_MS
            while t <= end_ms:
                events.append(("partial", max(segment_start, t - PARTIAL_WINDOW_MS), t))
                last_partial_at = t
                t += PARTIAL_EVERY_MS
            if end_ms - segment_start >= MAX_SEGMENT_MS:
                events.append(("final", segment_start, end_ms, True))
                segment_start = end_ms - OVERLAP_MS
                last_partial_at = segment_start
        elif in_speech:
            if end_ms - last_speech_end >= PAUSE_FINAL_MS:
                events.append(("final", segment_start, last_speech_end, False))
                in_speech = False
                segment_start = -1
                last_partial_at = -1
    return events


def assemble(result, window_start_ms: int, spelling: dict) -> list:
    """Words between SEPARATOR tokens; session-global ms; endMs += frame shift (V-015).
    V-045: the bundled quran-asr vocab separates words with the SPACE token (no ▁ in
    tokens.txt) — split on BOTH; ▁-only produced one giant word per window (the first
    replay's correct=0-1 numbers were that harness artifact, retired in V-044)."""
    words = []
    letters, start, last_ts = [], None, None
    for tok, ts in zip(result.tokens, result.timestamps):
        if tok in ("▁", " "):
            if letters:
                text = "".join(letters)
                text = spelling.get(text, text)
                words.append([text, int(start * 1000) + window_start_ms,
                              int(last_ts * 1000) + window_start_ms + 40])
                letters, start, last_ts = [], None, None
        else:
            if start is None:
                start = ts
            last_ts = ts
            letters.append(tok)
    if letters:
        text = "".join(letters)
        text = spelling.get(text, text)
        words.append([text, int(start * 1000) + window_start_ms,
                      int(last_ts * 1000) + window_start_ms + 40])
    return words


def decode_window(recognizer, pcm: np.ndarray, ws: int, we: int, spelling: dict) -> list:
    audio = pcm[ws * SAMPLE_RATE // 1000: we * SAMPLE_RATE // 1000]
    if len(audio) == 0:
        return []
    stream = recognizer.create_stream()
    stream.accept_waveform(SAMPLE_RATE, audio)
    recognizer.decode_stream(stream)
    return assemble(stream.result, ws, spelling)


def expected_json(c: dict) -> dict:
    """EvalMain's expected-words.json per clip: model-space words via N(), with the range."""
    return {
        "surah": c["surah"],
        "fromAyah": c["ayahs"][0],
        "toAyah": c["ayahs"][-1],
        "words": N(c["expectedUthmani"]).split(),
    }


def main() -> int:
    import sherpa_onnx

    recognizer = sherpa_onnx.OfflineRecognizer.from_zipformer_ctc(
        model=str(MODEL / "model.int8.onnx"), tokens=str(MODEL / "tokens.txt"), num_threads=2)
    spelling = {}
    sp = MODEL / "spelling.json"
    if sp.exists():
        spelling = json.loads(sp.read_text(encoding="utf-8"))

    clips = json.load(open(MANIFEST, encoding="utf-8"))["clips"]
    outdir = REPO / "eval/hypotheses/t11"
    expdir = REPO / "eval/expected/t11"
    outdir.mkdir(parents=True, exist_ok=True)
    expdir.mkdir(parents=True, exist_ok=True)

    print("### T11: emitting APP-SHAPED vs WHOLE-CLIP hypotheses (per-window audio decode)")
    print("### replay through the REAL tracker happens in CI via tools:eval (Gradle is")
    print("### sandbox-blocked, §0). Scoring = the tracker's per-word states — the shipped")
    print("### pipeline, NOT raw word-union WER (overlapping partials re-cover audio by")
    print("### design; the tracker's per-word evidence + EXTRA dedup handles that).")
    print()
    summary = []
    for c in clips:
        pcm = read_pcm(Path(c["file"]))
        duration_ms = len(pcm) * 1000 // SAMPLE_RATE
        clip_id = c["clipId"]

        exp = expected_json(c)
        (expdir / f"{clip_id}.json").write_text(
            json.dumps(exp, ensure_ascii=False, indent=1), encoding="utf-8")

        # mode 1: whole clip — one FINAL over the entire audio (V-038 behavior)
        stream = recognizer.create_stream()
        stream.accept_waveform(SAMPLE_RATE, pcm)
        recognizer.decode_stream(stream)
        whole_words = assemble(stream.result, 0, spelling)
        (outdir / f"{clip_id}-whole.json").write_text(
            json.dumps({"clips": [{"clipId": clip_id, "observations": [{
                "words": whole_words, "windowStartMs": 0,
                "windowEndMs": duration_ms, "isFinal": True,
            }]}]}, ensure_ascii=False, indent=1), encoding="utf-8")

        # mode 2: app-shaped — silero VAD → Segmenter-law windows → per-window audio decode
        frames = vad_speech(pcm)
        events = segmenter_windows(frames)
        observations = []
        for ev in events:
            is_final = ev[0] == "final"
            ws = max(0, ev[1] - VAD_PAD_MS)
            we = min(duration_ms, ev[2] + VAD_PAD_MS)
            words = decode_window(recognizer, pcm, ws, we, spelling)
            if words or is_final:
                observations.append({
                    "words": words, "windowStartMs": ws,
                    "windowEndMs": we, "isFinal": is_final,
                })
        (outdir / f"{clip_id}-app.json").write_text(
            json.dumps({"clips": [{"clipId": clip_id, "observations": observations}]},
                       ensure_ascii=False, indent=1), encoding="utf-8")

        partials = sum(1 for e in events if e[0] == "partial")
        finals = sum(1 for e in events if e[0] == "final")
        summary.append((clip_id, len(exp["words"]), partials, finals, len(observations)))
        print(f"{clip_id:24} expected={len(exp['words']):3d} words  "
              f"partials={partials:3d} finals={finals:2d}  obs={len(observations):3d}  "
              f"(files: {clip_id}-whole.json / {clip_id}-app.json)")

    print()
    print(f"emitted {len(clips) * 2} hypothesis files → {outdir}")
    print(f"emitted {len(clips)} expected files → {expdir}")
    print("CI replay: scripts/t11_replay.sh loops tools:eval over both modes; the report")
    print("lands in the build job log + the reports artifact. INDICATIVE labels apply:")
    print("studio clips, §13 phone gate NOT met, WRONG still renders CLOSE.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
