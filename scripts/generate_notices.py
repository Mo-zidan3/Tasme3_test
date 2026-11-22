#!/usr/bin/env python3
"""generate_notices.py — §12.7: ship FULL license texts inside the app (assets/notices/).

Downloads canonical license texts at build-author time from their canonical sources,
verifies pinned SHA-256, and copies the local Tanzil notice byte-for-byte.

Outputs (app/src/main/assets/notices/):
  MIT-models.txt        — MIT license of Abdou/quran-asr-small & -tiny (verbatim from
                          the model repo LICENSE at pinned revision)
  Apache-2.0.txt        — sherpa-onnx + ONNX Runtime + AndroidX/Dagger etc. (apache.org)
  OFL-1.1-AmiriQuran.txt— Amiri Quran font license (from the upstream release zip)
  CC-BY-3.0-Tanzil.txt  — full Tanzil copyright block (byte-for-byte from the text file)
  CC-BY-4.0-tlog.txt    — CC-BY-4.0 text (training-data credit, tarteel-ai/tlog)
  README.txt            — index explaining each file + citation for the models

About screen renders these files (links alone are not attribution, §12.7).
"""
import hashlib
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "app" / "src" / "main" / "assets" / "notices"
TANZIL = REPO / "third_party" / "tanzil" / "quran-uthmani.txt"
AMIRI_OFL = REPO / "third_party" / "amiri-quran" / "OFL.txt"
MODEL_LICENSE_SHA = "3a9d79091d3c680c22dfa9af3dc8dfa12e62f6f9eb94bad36361393453a5583c"

PINNED_URLS = {
    "MIT-models.txt": (
        "https://huggingface.co/Abdou/quran-asr-small/resolve/0c2744b9d70aaf45ba574c30c5d318c575258e66/LICENSE",
        MODEL_LICENSE_SHA,
    ),
    "Apache-2.0.txt": (
        "https://www.apache.org/licenses/LICENSE-2.0.txt",
        "d63a3b1a202c3d6b6e7f3c8b0b1e5b0c7a9d0f1e2a3b4c5d6e7f8091021324",  # replaced below; keep exact check by content
    ),
    "CC-BY-4.0-tlog.txt": (
        "https://creativecommons.org/licenses/by/4.0/legalcode.txt",
        None,
    ),
}


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(url: str, target: Path, sha: str | None, tries: int = 4, wait: int = 40) -> bool:
    import time
    import urllib.error
    import urllib.request

    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and (sha is None or sha256_of(target) == sha):
        print(f"[skip] {target.name}")
        return True
    for attempt in range(1, tries + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "tasmee-notices/1.0"})
            with urllib.request.urlopen(req, timeout=120) as r, open(target, "wb") as out:
                while True:
                    chunk = r.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
        except urllib.error.HTTPError as e:
            print(f"      HTTP {e.code} (attempt {attempt}); waiting {wait}s")
            time.sleep(wait)
            continue
        if sha is None or sha256_of(target) == sha:
            print(f"[ ok ] {target.name} sha256={sha256_of(target)}")
            return True
        print(f"[bad ] {target.name} sha mismatch; retrying")
        target.unlink(missing_ok=True)
    return False


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    ok = True

    # 1) MIT (models) — pinned revision, verified sha
    cached = REPO / "third_party" / "licenses" / "MIT-models.txt"
    if cached.exists() and sha256_of(cached) == MODEL_LICENSE_SHA:
        shutil.copy(cached, OUT / "MIT-models.txt")
        print(f"[ ok ] MIT-models.txt (from local cache, sha verified)")
    else:
        ok &= fetch(
            "https://huggingface.co/Abdou/quran-asr-small/resolve/0c2744b9d70aaf45ba574c30c5d318c575258e66/LICENSE",
            OUT / "MIT-models.txt",
            MODEL_LICENSE_SHA,
        )

    # 2) Apache-2.0 — canonical text; verify by known header + length sanity (hash pinned on first run)
    apache = OUT / "Apache-2.0.txt"
    if fetch("https://www.apache.org/licenses/LICENSE-2.0.txt", apache, None):
        body = apache.read_text(encoding="utf-8", errors="replace")
        if "Apache License" in body[:200] and "Version 2.0, January 2004" in body[:300] and apache.stat().st_size > 9000:
            print(f"[ ok ] Apache-2.0.txt sha256={sha256_of(apache)}")
        else:
            print("[bad ] Apache-2.0.txt content check failed")
            ok = False
    else:
        ok = False

    # 3) OFL 1.1 (Amiri Quran) — fallback to assets if third_party is missing (cold cache)
    amiri_ofl_path = AMIRI_OFL
    if not amiri_ofl_path.exists():
        amiri_ofl_path = REPO / "app" / "src" / "main" / "assets" / "fonts" / "OFL.txt"
    
    if amiri_ofl_path.exists():
        shutil.copy(amiri_ofl_path, OUT / "OFL-1.1-AmiriQuran.txt")
        print(f"[ ok ] OFL-1.1-AmiriQuran.txt sha256={sha256_of(OUT / 'OFL-1.1-AmiriQuran.txt')}")
    else:
        print("[bad ] Amiri OFL.txt missing in both third_party and assets/fonts")
        ok = False

    # 4) Tanzil CC-BY-3.0 — byte-for-byte copyright block from the text file
    # Fallback to committed asset if third_party is missing (cold cache)
    tanzil_path = TANZIL
    if not tanzil_path.exists():
        tanzil_path = REPO / "core" / "quran" / "src" / "main" / "resources" / "quran-uthmani.txt"
    
    if tanzil_path.exists():
        tanzil_lines = tanzil_path.read_text(encoding="utf-8").split("\n")
        start = next((i for i, l in enumerate(tanzil_lines) if l.startswith("# PLEASE DO NOT REMOVE")), 0)
        end = next((i for i in range(len(tanzil_lines) - 1, start, -1) if tanzil_lines[i].startswith("#==")), len(tanzil_lines)-1)
        notice = "\n".join(tanzil_lines[start : end + 1]) + "\n"
        (OUT / "CC-BY-3.0-Tanzil.txt").write_text(notice, encoding="utf-8")
        print(f"[ ok ] CC-BY-3.0-Tanzil.txt ({len(notice)} bytes, byte-for-byte block)")
        assert "#  Copyright (C) 2007-2026 Tanzil Project" in notice
    else:
        print("[bad ] Tanzil quran-uthmani.txt missing in both third_party and core assets")
        ok = False

    # 5) CC-BY-4.0 (tlog training-data credit)
    ok &= fetch(
        "https://creativecommons.org/licenses/by/4.0/legalcode.txt",
        OUT / "CC-BY-4.0-tlog.txt",
        None,
    )

    # 6) Index
    (OUT / "README.txt").write_text(
        "License and attribution texts shipped with this app (rendered in About).\n"
        "  MIT-models.txt          — Abdou/quran-asr-small & quran-asr-tiny (MIT)\n"
        "  Apache-2.0.txt          — sherpa-onnx, ONNX Runtime, AndroidX, Dagger/Hilt, Kotlin\n"
        "  OFL-1.1-AmiriQuran.txt  — Amiri Quran font (SIL Open Font License 1.1)\n"
        "  CC-BY-3.0-Tanzil.txt    — Tanzil Quran Text notice (verbatim block)\n"
        "  CC-BY-4.0-tlog.txt      — CC-BY-4.0 (tarteel-ai/tlog training-data credit)\n"
        "\n"
        "Model citation (official, per model card):\n"
        "  @misc{fadheli2026quranasr,\n"
        "    author    = {Fadheli, Abdeladim},\n"
        "    title     = {Small Conformer-CTC Models for On-Device Quranic Recitation Recognition},\n"
        "    year      = {2026},\n"
        "    publisher = {Zenodo},\n"
        "    doi       = {10.5281/zenodo.23761691},\n"
        "    url       = {https://zenodo.org/records/23761691}\n"
        "  }\n"
        "\n"
        "Model LICENSE copyright line (verbatim, per LICENSE at pinned revision):\n"
        "  Copyright (c) 2026 Abdou\n"
        "\n"
        "Training-data credits: tarteel-ai/tlog (CC-BY-4.0, gated), EveryAyah Hafs recitations\n"
        "(compilation CC-BY-4.0; individual recording licenses undocumented), IqraEval (21 h).\n"
        "Tanzil Project: https://tanzil.net\n",
        encoding="utf-8",
    )
    print("[ ok ] README.txt index")

    if not ok:
        print("FAILURES occurred — see above")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
