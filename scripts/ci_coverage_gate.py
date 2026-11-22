#!/usr/bin/env python3
"""V-035 P2: JaCoCo XML coverage gate — REAL numbers, hard failure below threshold.

Parses the JaCoCo XML reports produced by :core:quran and :core:align
jacocoTestReport tasks and FAILS (exit 1) if either module's LINE coverage is
below the floor. Prints the measured numbers (never re-uses doc numbers).

Usage: python3 scripts/ci_coverage_gate.py <floor_percent>
       [--xml module=path ...]
Defaults mirror the CI step: core:quran and core:align at 85% line floor.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

DEFAULTS = {
    "core:quran": REPO / "core/quran/build/reports/jacoco/test/jacocoTestReport.xml",
    "core:align": REPO / "core/align/build/reports/jacoco/test/jacocoTestReport.xml",
}


def line_coverage(xml_path: Path) -> tuple[int, int]:
    """Return (covered, total) line counts from a JaCoCo XML report.

    Report-level counters ONLY (direct children of <report>): the XML repeats LINE
    counters at method/class/sourcefile/package/report level, so a naive
    root.iter("counter") walk sums every level and inflates counts ~5×.
    """
    root = ET.parse(xml_path).getroot()
    covered = missed = 0
    for counter in root.findall("counter"):
        if counter.get("type") == "LINE":
            covered += int(counter.get("covered", "0"))
            missed += int(counter.get("missed", "0"))
    return covered, covered + missed


def main() -> int:
    floor = float(sys.argv[1]) if len(sys.argv) > 1 else 85.0
    paths = DEFAULTS
    print(f"[coverage gate] line-coverage floor: {floor:.1f}%")
    failed = False
    for module, path in paths.items():
        if not path.exists():
            print(f"[FAIL] {module}: report missing at {path}")
            failed = True
            continue
        covered, total = line_coverage(path)
        pct = 100.0 * covered / total if total else 0.0
        status = "OK" if pct >= floor else "FAIL"
        print(f"[{status}] {module}: {covered}/{total} lines = {pct:.1f}%")
        if pct < floor:
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
