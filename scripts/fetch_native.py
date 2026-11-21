#!/usr/bin/env python3
"""fetch_native.py — §8.2: fetch pinned sherpa-onnx Android jniLibs + VAD, verify, extract arm64-only.

Pinned: sherpa-onnx release tag v2.0.2.
  tarball   : sherpa-onnx-v2.0.2-android.tar.bz2 (47.9 MB)
  tarball sha256: 08d22f7eb5c49bd161d248633eeb66e84735725706e21202b684800e5081a029
  extracted : libonnxruntime.so (22,249,560 B) + libsherpa-onnx-jni.so (5,366,640 B) → arm64-v8a only
  also      : silero_vad.onnx (from tag `asr-models`) sha256 9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6

Fetch order (README documents it): fetch_native.py FIRST (jniLibs + VAD), then fetch_models.py
(ONNX models). Both verify SHA-256 before placing anything. Kotlin API sources are vendored in
core/asr/src/main/kotlin/com/k2fsa/sherpa/onnx/ from the SAME tag (git-tracked).
"""
import hashlib
import sys
import tarfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
JNI_DIR = REPO / "app" / "src" / "main" / "jniLibs" / "arm64-v8a"
ASSET_VAD = REPO / "app" / "src" / "main" / "assets" / "models" / "silero_vad.onnx"
CACHE = REPO / "third_party" / "sherpa-onnx-v2.0.2"

TARBALL = "sherpa-onnx-v2.0.2-android.tar.bz2"
TARBALL_URL = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.2/{TARBALL}"
TARBALL_SHA = "08d22f7eb5c49bd161d248633eeb66e84735725706e21202b684800e5081a029"
SO_SHA = {
    "libonnxruntime.so": None,       # sizes recorded; sha printed for pinning on first run
    "libsherpa-onnx-jni.so": None,
}
VAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
VAD_SHA = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
KNOWN_SO_SHA = {
    # pinned after first verified extraction (V-004/V-016 evidence chain)
    "libonnxruntime.so": "33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1",
    "libsherpa-onnx-jni.so": "089fd48ee0218cdb6be0a533e775c80e90c6ae935f5082630695276ad5898084",
}
SO_SIDECAR = REPO / "third_party" / "sherpa-onnx-v2.0.2" / "jniLibs.sha256"


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(url: str, target: Path, sha: str | None) -> bool:
    import time
    import urllib.error
    import urllib.request

    if target.exists() and (sha is None or sha256_of(target) == sha):
        print(f"[skip] {target.name} (present, sha ok)")
        return True
    target.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(1, 4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "tasmee-native/1.0"})
            with urllib.request.urlopen(req, timeout=600) as r, open(target, "wb") as out:
                while True:
                    chunk = r.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
        except urllib.error.HTTPError as e:
            print(f"      HTTP {e.code} (attempt {attempt})")
            time.sleep(20)
            continue
        if sha is None or sha256_of(target) == sha:
            return True
        print(f"[bad ] {target.name} sha mismatch; retrying")
        target.unlink(missing_ok=True)
    return False


def main() -> int:
    ok = True
    CACHE.mkdir(parents=True, exist_ok=True)
    tar_path = CACHE / TARBALL

    # 1) silero VAD
    if not fetch(VAD_URL, ASSET_VAD, VAD_SHA):
        ok = False

    # 2) jniLibs: already-extracted + sidecar match → skip
    sidecar: dict[str, str] = {}
    if SO_SIDECAR.exists():
        for line in SO_SIDECAR.read_text().strip().split("\n"):
            if line.strip():
                sha, name = line.split()
                sidecar[name] = sha

    all_present = all((JNI_DIR / n).exists() for n in SO_SHA)
    if all_present:
        verified = all(sha256_of(JNI_DIR / n) == KNOWN_SO_SHA.get(n) for n in SO_SHA)
        if verified:
            print("[skip] jniLibs arm64 already extracted and sha-pinned-verified")
            return 0 if ok else 1

    # 3) download + verify tarball, extract arm64-only pair
    if not fetch(TARBALL_URL, tar_path, TARBALL_SHA):
        return 1
    print(f"[ ok ] {TARBALL} sha256={sha256_of(tar_path)}")
    JNI_DIR.mkdir(parents=True, exist_ok=True)
    with tarfile.open(tar_path, "r:bz2") as tf:
        for member in tf.getmembers():
            name = member.name.rsplit("/", 1)[-1]
            if member.name.startswith("jniLibs/arm64-v8a/") and name in SO_SHA:
                f = tf.extractfile(member)
                (JNI_DIR / name).write_bytes(f.read())
                print(f"[extract] {name} ({member.size} B) → {JNI_DIR / name}")

    # 4) write/verify sidecar
    lines = []
    for n in sorted(SO_SHA):
        s = sha256_of(JNI_DIR / n)
        lines.append(f"{s}  {n}")
    SO_SIDECAR.parent.mkdir(parents=True, exist_ok=True)
    SO_SIDECAR.write_text("\n".join(lines) + "\n")
    print("[ ok ] jniLibs sidecar written:")
    print(SO_SIDECAR.read_text())
    print("Note: record these two sha256 values in this script's KNOWN_SO_SHA on next edit.")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
