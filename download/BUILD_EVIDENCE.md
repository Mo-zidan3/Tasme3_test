# Phase 0.6 + Phase 1 — build & test evidence (2026-11-21)

```
$ ./gradlew assembleDebug test
BUILD SUCCESSFUL in 38s

$ ./gradlew :core:quran:test :core:align:test
52 tests passed (21 quran + 31 align), 0 failed

$ ./gradlew :core:quran:jacocoTestReport :core:align:jacocoTestReport
core:quran 93.6% lines | core:align 91.7% lines | combined 91.9% (target ≥85%)
```
