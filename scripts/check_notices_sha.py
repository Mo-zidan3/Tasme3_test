#!/usr/bin/env python3
"""V-036 §3: canonical-SHA license-bytes gate.

Asserts the SHA-256 of every shipped notice file in app/src/main/assets/notices/
against the pinned canonical values (the same pins as generate_notices.py's final
gate). Fails (exit 1) on any missing file or byte drift.

Rationale (V-036): run 45035800627's GREEN release shipped degraded notices —
Tanzil 1004 B vs canonical 1106 B (sha256 72493509…), OFL 4027 B vs canonical
4389 B (sha256 72de68e5…) — and secrets-scan passed them because it only greps
for '…'. Ellipsis-grep is not a license gate.
"""
import hashlib
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
NOTICES = REPO / "app" / "src" / "main" / "assets" / "notices"

CANONICAL_SHA256 = {
    "CC-BY-3.0-Tanzil.txt": "72493509d507314f60c018fd33aa57d9064a0de42be8926745e4953f59925c62",
    "OFL-1.1-AmiriQuran.txt": "72de68e5954f4fdd24702292ef5a32f003ca960ec9330dc86e5eefb5dffb9b22",
    "MIT-models.txt": "3a9d79091d3c680c22dfa9af3dc8dfa12e62f6f9eb94bad36361393453a5583c",
    "Apache-2.0.txt": "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
    "CC-BY-4.0-tlog.txt": "9ba9550ad48438d0836ddab3da480b3b69ffa0aac7b7878b5a0039e7ab429411",
    "README.txt": "c67401e86f2802582a4cafdcaeac32d0785d1197bdab3463ea341ffe1089f38e",
}

# V-037/T2: the derived docs/tanzil-notice.txt must stay byte-identical to the canonical asset
# (was 1037 B — the truncated variant).
DOCS_TANZIL = REPO / "docs" / "tanzil-notice.txt"


def main() -> int:
    failed = False
    targets = {NOTICES / name: want for name, want in CANONICAL_SHA256.items()}
    targets[DOCS_TANZIL] = CANONICAL_SHA256["CC-BY-3.0-Tanzil.txt"]
    for p, want in sorted(targets.items()):
        name = p.relative_to(REPO).as_posix()
        if not p.exists():
            print(f"[FAIL] {name}: missing")
            failed = True
            continue
        got = hashlib.sha256(p.read_bytes()).hexdigest()
        if got != want:
            print(f"[FAIL] {name}: sha256={got} != canonical {want} — NON-CANONICAL LICENSE BYTES")
            failed = True
        else:
            print(f"[gate] {name} ({p.stat().st_size} B) sha256={got[:16]}… OK")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
