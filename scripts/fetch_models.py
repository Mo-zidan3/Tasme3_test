#!/usr/bin/env python3
"""fetch_models.py — §8.2: download pinned sherpa-onnx ASR models + VAD into app assets.

Pinned revisions (HuggingFace, verified 2026-11-21):
  Abdou/quran-asr-small @ 0c2744b9d70aaf45ba574c30c5d318c575258e66
  Abdou/quran-asr-tiny  @ a291b33fd5a31cc4f212363ee1ba2d56d9710b49

SHA-256 (measured by sha256sum on 2026-11-21, recorded in docs/VERIFICATION.md):
  small model.int8.onnx : 88f9df290d57b8890275d79187b77515aeeb12626a42bc76cbd0f8bfcaff00f0
  small tokens.txt      : 75b53814499cee84a07c7b7a318524d30bcaf84273c0f8b28a04176023e8c74f
  small spelling.json   : 7adff00aa1d4b0413a3a89f898117682a22639624b3666121f6de785e0fb346a
  tiny  model.int8.onnx : 58de6db034bb4f6516b3bc1c68e7a0ac4e77abffe603a550ee87b39dcd0c7ac6
  tiny  tokens.txt      : 75b53814499cee84a07c7b7a318524d30bcaf84273c0f8b28a04176023e8c74f (same file)
  silero_vad.onnx      : 9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6

Models are NOT committed to git. This script is the only sanctioned way to
place them into app/src/main/assets/models/.

Usage: python3 scripts/fetch_models.py [--skip-vad]
"""
import hashlib
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets" / "models"

# (url, relative target, sha256)
FILES = [
    (
        "https://huggingface.co/Abdou/quran-asr-small/resolve/main/model.int8.onnx",
        "quran-asr-small/model.int8.onnx",
        "88f9df290d57b8890275d79187b77515aeeb12626a42bc76cbd0f8bfcaff00f0",
    ),
    (
        "https://huggingface.co/Abdou/quran-asr-small/resolve/main/tokens.txt",
        "quran-asr-small/tokens.txt",
        "75b53814499cee84a07c7b7a318524d30bcaf84273c0f8b28a04176023e8c74f",
    ),
    (
        "https://huggingface.co/Abdou/quran-asr-tiny/resolve/main/model.int8.onnx",
        "quran-asr-tiny/model.int8.onnx",
        "58de6db034bb4f6516b3bc1c68e7a0ac4e77abffe603a550ee87b39dcd0c7ac6",
    ),
    (
        "https://huggingface.co/Abdou/quran-asr-tiny/resolve/main/tokens.txt",
        "quran-asr-tiny/tokens.txt",
        "75b53814499cee84a07c7b7a318524d30bcaf84273c0f8b28a04176023e8c74f",
    ),
    (
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        "silero_vad.onnx",
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    ),
]

HF_RETRY_WAIT_S = 45  # HF rate-limits (429) observed during the 0.5 spike
HF_MAX_TRIES = 6


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(url: str, target: Path, expected_sha: str) -> bool:
    import urllib.request
    import urllib.error

    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and sha256_of(target) == expected_sha:
        print(f"[skip] {target.name} already present with matching sha256")
        return True

    is_hf = "huggingface.co" in url
    tries = HF_MAX_TRIES if is_hf else 3
    wait = HF_RETRY_WAIT_S if is_hf else 5
    for attempt in range(1, tries + 1):
        try:
            print(f"[get ] {url} (attempt {attempt})")
            req = urllib.request.Request(url, headers={"User-Agent": "tasmee-fetch/1.0"})
            with urllib.request.urlopen(req, timeout=600) as r, open(target, "wb") as out:
                while True:
                    chunk = r.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
        except urllib.error.HTTPError as e:
            print(f"      HTTP {e.code}; waiting {wait}s before retry")
            time.sleep(wait)
            continue
        got = sha256_of(target)
        if got == expected_sha:
            print(f"[ ok ] {target} sha256={got}")
            return True
        print(f"[bad ] sha256 mismatch: got {got} want {expected_sha}; retrying")
        target.unlink(missing_ok=True)
        time.sleep(wait)
    return False


def main() -> int:
    skip_vad = "--skip-vad" in sys.argv
    failures = []
    for url, rel, sha in FILES:
        if skip_vad and rel == "silero_vad.onnx":
            continue
        if not fetch(url, ASSETS / rel, sha):
            failures.append(rel)
    if failures:
        print(f"FAILED: {failures}")
        return 1
    print("All model assets verified against pinned SHA-256.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
