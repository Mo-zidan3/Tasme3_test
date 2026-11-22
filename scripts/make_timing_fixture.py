#!/usr/bin/env python3
"""make_timing_fixture.py — §7.5/S2 CI decode fixture (1.3).

Deterministic, seeded, pure-code generation of the SAME harmonic-complex signal class used in
V-015 (host timing verification): an 8.0 s, 16 kHz mono WAV built from a fixed harmonic stack.
Nothing third-party, nothing gated — the file is regenerated at test time, never committed.

The fixture's ONLY job: exercise the real sherpa-onnx JNI decode path on-device and verify the
TIMING CONTRACT (timestamps in seconds; token deltas on the 40 ms frame grid). It is NOT an
accuracy fixture (accuracy runs on Mo's own recorded clip — loud-skip when absent).

Usage: python3 scripts/make_timing_fixture.py [out.wav]   (default: eval/fixtures/timing_fixture.wav)
"""
import math
import struct
import sys
import wave
from pathlib import Path

SR = 16000
DUR_S = 8.0
F0 = 220.0          # base of the harmonic stack (voiced-formant-like energy)
N_HARMONICS = 12
SEED_PHASE = 0.0    # fixed phase — fully deterministic, no RNG needed
SYLLABLE_RATE = 8.0 # V-015's signal class: 8 syllables/s amplitude gating — WITHOUT the
                    # syllabic envelope the CTC emits nothing (run 45063204700 API-27:
                    # "decode must return words (got 0)"); with it, V-015 measured 26 tokens.
# V-037: deterministic pitch-step schedule (V-015's fixture class) — F0 changes per syllable
# period via a fixed 8-step table, giving the CTC formant-like variation to fire repeatedly.
PITCH_STEPS_HZ = [220.0, 180.0, 260.0, 200.0, 240.0, 160.0, 280.0, 190.0]


def render() -> bytes:
    n = int(SR * DUR_S)
    # build a fixed phase list (deterministic "seed"): phase_k = (k * 137.5)° per harmonic
    phases = [math.radians(k * 137.5) for k in range(1, N_HARMONICS + 1)]
    amps = [1.0 / k for k in range(1, N_HARMONICS + 1)]
    # slow amplitude envelope (attack 200 ms, release 200 ms) so VAD sees speech-like bursts
    env_attack = int(0.2 * SR)
    env_release = int(0.2 * SR)
    out = bytearray()
    phase_acc = 0.0  # cumulative carrier phase (phase-continuous under pitch steps)
    for i in range(n):
        t = i / SR
        f0 = PITCH_STEPS_HZ[int(t * SYLLABLE_RATE) % len(PITCH_STEPS_HZ)]
        phase_acc += 2 * math.pi * f0 / SR
        v = 0.0
        for k in range(N_HARMONICS):
            v += amps[k] * math.sin((k + 1) * phase_acc + phases[k])
        # normalize the stack to ±0.8
        v *= 0.8 / (sum(amps) / 2)
        # V-037: syllabic gating (8 syll/s, raised-cosine) — V-015's fixture class; the plain
        # continuous stack decoded to ZERO words on-device (45063204700 API-27 evidence).
        syll = 0.35 + 0.65 * (0.5 * (1 - math.cos(2 * math.pi * SYLLABLE_RATE * t)))
        # pitch-step: deterministic per-syllable F0 from the fixed table (phase-continuous via
        # cumulative phase so no clicks are introduced)
        v *= syll
        if i < env_attack:
            v *= i / env_attack
        elif i > n - env_release:
            v *= (n - i) / env_release
        s = max(-32768, min(32767, int(v * 32767)))
        out += struct.pack("<h", s)
    return bytes(out)


def main() -> int:
    target = Path(sys.argv[1] if len(sys.argv) > 1 else "eval/fixtures/timing_fixture.wav")
    target.parent.mkdir(parents=True, exist_ok=True)
    data = render()
    with wave.open(str(target), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)
    # determinism self-check: size fixed; sha depends only on this script
    import hashlib
    sha = hashlib.sha256(data).hexdigest()
    print(f"timing fixture: {target} ({len(data) // 2} samples, {DUR_S}s @ {SR} Hz)")
    print(f"payload sha256={sha}")
    print("deterministic: rerun and compare — same sha expected")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
