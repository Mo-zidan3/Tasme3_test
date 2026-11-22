#!/usr/bin/env python3
"""Generate THIRD_PARTY_NOTICES.md and app/src/main/assets/notices/ files."""
import hashlib
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "app" / "src" / "main" / "assets" / "notices"
OUT.mkdir(parents=True, exist_ok=True)

TANZIL = REPO / "third_party" / "tanzil" / "quran-uthmani.txt"
AMIRI_OFL = REPO / "third_party" / "amiri-quran" / "OFL.txt"
QURAN_RESOURCE = REPO / "core" / "quran" / "src" / "main" / "resources" / "quran-uthmani.txt"

# V-036: pinned canonical SHA-256 of every shipped notice (the license gate asserts these).
# Source of truth = the GIT-TRACKED assets; extraction/reconstruction is only a first-boot
# bootstrap and is byte-asserted against the committed resource header. Hand-retyped or
# drift-edited license text (Mo's 3b1da0a: Tanzil 1004 B, OFL 4027 B) hard-fails here.
CANONICAL_SHA256 = {
    "CC-BY-3.0-Tanzil.txt": "72493509d507314f60c018fd33aa57d9064a0de42be8926745e4953f59925c62",
    "OFL-1.1-AmiriQuran.txt": "72de68e5954f4fdd24702292ef5a32f003ca960ec9330dc86e5eefb5dffb9b22",
    "MIT-models.txt": "3a9d79091d3c680c22dfa9af3dc8dfa12e62f6f9eb94bad36361393453a5583c",
    "Apache-2.0.txt": "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
    "CC-BY-4.0-tlog.txt": "9ba9550ad48438d0836ddab3da480b3b69ffa0aac7b7878b5a0039e7ab429411",
    "README.txt": "c67401e86f2802582a4cafdcaeac32d0785d1197bdab3463ea341ffe1089f38e",
}


def sha256_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def fetch(url: str, out: Path) -> bool:
    import urllib.request
    try:
        urllib.request.urlretrieve(url, out)
        return True
    except Exception as e:
        print(f"[bad ] fetch {url}: {e}")
        return False

def main() -> int:
    ok = True

    # 1) MIT (models) — fetch from pinned revision
    mit_url = "https://huggingface.co/Abdou/quran-asr-small/raw/0c2744b9d70aaf45ba574c30c5d318c575258e66/LICENSE"
    mit_path = OUT / "MIT-models.txt"
    if not mit_path.exists():
        if not fetch(mit_url, mit_path):
            ok = False
    if mit_path.exists():
        print(f"[ ok ] MIT-models.txt sha256={sha256_of(mit_path)}")
    else:
        print("[bad ] MIT-models.txt missing")
        ok = False

    # 2) Apache-2.0 (canonical)
    apache_url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
    apache_path = OUT / "Apache-2.0.txt"
    if not apache_path.exists():
        if not fetch(apache_url, apache_path):
            ok = False
    if apache_path.exists():
        print(f"[ ok ] Apache-2.0.txt sha256={sha256_of(apache_path)}")
    else:
        print("[bad ] Apache-2.0.txt missing")
        ok = False

    # 3) OFL 1.1 (Amiri Quran) — V-036: COPY the committed canonical asset (4389 B,
    # sha256 72de68e5…); when third_party is present it must be byte-identical to the
    # canonical, otherwise hard-fail. Never regenerate license text.
    amiri_path = OUT / "OFL-1.1-AmiriQuran.txt"
    canonical_amiri = REPO / "app" / "src" / "main" / "assets" / "notices" / "OFL-1.1-AmiriQuran.txt"
    if not canonical_amiri.exists():
        if not AMIRI_OFL.exists():
            print("[FAIL] canonical OFL asset missing and third_party/amiri-quran/OFL.txt absent")
            ok = False
        else:
            shutil.copy(AMIRI_OFL, canonical_amiri)
            print("[note] first boot: OFL canonical seeded from third_party/amiri-quran/OFL.txt")
    elif AMIRI_OFL.exists():
        if AMIRI_OFL.read_bytes() != canonical_amiri.read_bytes():
            print("[FAIL] third_party/amiri-quran/OFL.txt is NOT byte-identical to the canonical asset")
            ok = False
    if amiri_path.resolve() != canonical_amiri.resolve() and canonical_amiri.exists():
        shutil.copy(canonical_amiri, amiri_path)
    if canonical_amiri.exists() and sha256_of(canonical_amiri) != CANONICAL_SHA256["OFL-1.1-AmiriQuran.txt"]:
        print(f"[FAIL] OFL-1.1-AmiriQuran.txt sha {sha256_of(canonical_amiri)[:16]}… != pinned canonical 72de68e5…")
        ok = False
    if canonical_amiri.exists():
        print(f"[ ok ] OFL-1.1-AmiriQuran.txt sha256={sha256_of(canonical_amiri)}")

    # 4) Tanzil CC-BY-3.0 — V-036: COPY the committed canonical notice (1106 B, sha256
    # 72493509…). Bootstrap-only extraction from the committed quran-uthmani.txt resource;
    # when third_party is present its header block must match the canonical notice
    # byte-for-byte — any upstream drift hard-fails instead of being silently re-typed.
    tanzil_path = OUT / "CC-BY-3.0-Tanzil.txt"
    canonical_tanzil = REPO / "app" / "src" / "main" / "assets" / "notices" / "CC-BY-3.0-Tanzil.txt"
    if not canonical_tanzil.exists():
        src = TANZIL if TANZIL.exists() else QURAN_RESOURCE
        lines = src.read_text(encoding="utf-8").split("\n")
        start = next((i for i, l in enumerate(lines) if l.startswith("# PLEASE DO NOT REMOVE")), None)
        sep = next((i for i, l in enumerate(lines) if start is not None and i > start and l.startswith("# ------")), None)
        if start is None or sep is None:
            print(f"[FAIL] Tanzil copyright block not found in {src}")
            ok = False
        else:
            notice = "\n".join(lines[start:sep]) + "\n" + ("#" + "=" * 68) + "\n"
            canonical_tanzil.write_text(notice, encoding="utf-8")
            print("[note] first boot: Tanzil notice extracted from the committed resource")
    elif TANZIL.exists():
        lines = TANZIL.read_text(encoding="utf-8").split("\n")
        start = next((i for i, l in enumerate(lines) if l.startswith("# PLEASE DO NOT REMOVE")), None)
        sep = next((i for i, l in enumerate(lines) if start is not None and i > start and l.startswith("# ------")), None)
        if start is None or sep is None:
            print("[FAIL] Tanzil copyright block not found in third_party/tanzil/quran-uthmani.txt")
            ok = False
        else:
            fresh = "\n".join(lines[start:sep]) + "\n"
            if not canonical_tanzil.read_text(encoding="utf-8").startswith(fresh):
                print("[FAIL] third_party Tanzil header does not match the canonical notice — upstream changed?")
                ok = False
    if tanzil_path.resolve() != canonical_tanzil.resolve() and canonical_tanzil.exists():
        shutil.copy(canonical_tanzil, tanzil_path)
    if canonical_tanzil.exists():
        got = sha256_of(canonical_tanzil)
        if got != CANONICAL_SHA256["CC-BY-3.0-Tanzil.txt"]:
            print(f"[FAIL] CC-BY-3.0-Tanzil.txt sha {got[:16]}… != pinned canonical 72493509…")
            ok = False
        print(f"[ ok ] CC-BY-3.0-Tanzil.txt ({canonical_tanzil.stat().st_size} bytes, sha256={got})")

    # 5) CC-BY-4.0 (tlog)
    tlog_url = "https://creativecommons.org/licenses/by/4.0/legalcode.txt"
    tlog_path = OUT / "CC-BY-4.0-tlog.txt"
    if not tlog_path.exists():
        if not fetch(tlog_url, tlog_path):
            ok = False
    if tlog_path.exists():
        print(f"[ ok ] CC-BY-4.0-tlog.txt sha256={sha256_of(tlog_path)}")
    else:
        print("[bad ] CC-BY-4.0-tlog.txt missing")
        ok = False

    # 6) README — V-036: the rich index (citation + limitations disclosure) is the
    # TRACKED canonical; only generated when absent (first boot), never overwritten.
    readme = OUT / "README.txt"
    if readme.exists():
        print("[ ok ] README.txt index (tracked version kept)")
    else:
        readme.write_text(
        "Third-party notices for bundled assets.\n\n"
        "Files:\n"
        "  MIT-models.txt          — MIT License (Abdou/quran-asr-small + tiny)\n"
        "  Apache-2.0.txt          — Apache License 2.0 (sherpa-onnx)\n"
        "  OFL-1.1-AmiriQuran.txt  — SIL Open Font License 1.1 (Amiri Quran font)\n"
        "  CC-BY-3.0-Tanzil.txt    — Creative Commons Attribution 3.0 (Tanzil Quran text)\n"
        "  CC-BY-4.0-tlog.txt      — Creative Commons Attribution 4.0 (tarteel-ai/tlog)\n\n"
        "Citation:\n"
        "@software{fadheli2026quranasr,\n"
        "  author = {Fadheli, Abdeladim},\n"
        "  title = {quran-asr-small},\n"
        "  year = {2026},\n"
        "  doi = {10.5281/zenodo.23761691}\n"
        "}\n\n"
        "Limitations:\n"
        "The model's comparison space is letters-only (29 letters). This makes some\n"
        "distinct Quranic words indistinguishable (e.g. harakat-only variants).\n"
        "This is an inherent property of the model's output alphabet, not a defect.\n",
            encoding="utf-8",
        )
        print(f"[ ok ] README.txt written")

    # V-036 §3: FINAL LICENSE GATE — every shipped notice must match its pinned canonical
    # SHA-256. This is the ellipsis-grep's replacement: secrets-scan passed degraded
    # notices in run 45035800627 because it only greps for '…'.
    for name, want in CANONICAL_SHA256.items():
        p = OUT / name
        if not p.exists():
            print(f"[FAIL] notice missing: {name}")
            ok = False
            continue
        got = sha256_of(p)
        if got != want:
            print(f"[FAIL] {name} sha256={got} != pinned canonical {want[:16]}… — NON-CANONICAL LICENSE BYTES")
            ok = False
        else:
            print(f"[gate] {name} sha256={got[:16]}… OK")
    return 0 if ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
