#!/usr/bin/env python3
"""eval/run_baseline.py — §13.2 baseline via whisper.cpp + verified GGML (harness-only).

STATUS: baseline NOT YET MEASURED. The pinned GGML fetch (S6) is rate-limited by HF (429)
across multiple sessions with exponential backoff (V-012, V-019). This stub implements the
interface so the pipeline is complete; it REFUSES to produce numbers:

  python3 eval/run_baseline.py <clips-dir>   → error: baseline model not available

It never blocks Phase 2. When the model is fetchable, implement decode via whisper.cpp with the
pinned GGML (sha256 recorded at fetch time) and emit the same hypotheses JSON as run_asr.py.
"""
import argparse
import sys


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("clips", nargs="?", help="clips dir (16 kHz mono wav)")
    args = ap.parse_args()
    if not args.clips:
        ap.error("clips dir required")
    print("BASELINE NOT YET MEASURED: the pinned GGML model could not be fetched "
          "(HF 429 across sessions; V-012/V-019). No baseline numbers are produced. "
          "Retry: see scripts/fetch_models.py S6 section.", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
