#!/usr/bin/env python3
"""V-037 host-side verification: decode the generated timing fixture with the pinned
quran-asr-small model (same sherpa-onnx 2.0.2 API as V-015) and assert words > 0.
Run before pushing fixture changes; CI asserts the same on-device."""
import sys
import wave

import numpy as np
import sherpa_onnx

MODEL = "app/src/main/assets/models/quran-asr-small/model.int8.onnx"
TOKENS = "app/src/main/assets/models/quran-asr-small/tokens.txt"


def main() -> int:
    wav = sys.argv[1] if len(sys.argv) > 1 else "eval/fixtures/timing_fixture.wav"
    with wave.open(wav) as w:
        assert w.getframerate() == 16000 and w.getnchannels() == 1
        pcm = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0
    r = sherpa_onnx.OfflineRecognizer.from_zipformer_ctc(model=MODEL, tokens=TOKENS, num_threads=2)
    s = r.create_stream()
    s.accept_waveform(16000, pcm)
    r.decode_stream(s)
    res = s.result
    print(f"tokens: {len(res.tokens)}  text: {''.join(res.tokens)[:80]!r}")
    ts = [t for t in res.timestamps[:10]]
    print(f"timestamps[:10]: {ts}")
    if not res.tokens:
        print("FAIL: fixture decodes to ZERO tokens — the emulator test would fail")
        return 1
    print("OK: fixture produces tokens — on-device timing test can assert")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
