License and attribution texts shipped with this app (rendered in About).
  MIT-models.txt          — Abdou/quran-asr-small & quran-asr-tiny (MIT)
  Apache-2.0.txt          — sherpa-onnx, ONNX Runtime, AndroidX, Dagger/Hilt, Kotlin
  OFL-1.1-AmiriQuran.txt  — Amiri Quran font (SIL Open Font License 1.1)
  CC-BY-3.0-Tanzil.txt    — Tanzil Quran Text notice (verbatim block)
  CC-BY-4.0-tlog.txt      — CC-BY-4.0 (tarteel-ai/tlog training-data credit)

Model citation (official, per model card):
  @misc{fadheli2026quranasr,
    author    = {Fadheli, Abdeladim},
    title     = {Small Conformer-CTC Models for On-Device Quranic Recitation Recognition},
    year      = {2026},
    publisher = {Zenodo},
    doi       = {10.5281/zenodo.23761691},
    url       = {https://zenodo.org/records/23761691}
  }

Model LICENSE copyright line (verbatim, per LICENSE at pinned revision):
  Copyright (c) 2026 Abdou

Training-data credits: tarteel-ai/tlog (CC-BY-4.0, gated), EveryAyah Hafs recitations
(compilation CC-BY-4.0; individual recording licenses undocumented), IqraEval (21 h).
Tanzil Project: https://tanzil.net

Limitations disclosure (user-facing; ar/en):
  AR: يعمل التطبيق في فضاء الحروف فقط (بدون حركات)، فبعض الكلمات المتشابهة بالحروف لا يمكن
      التمييز بينها عند التتبّع — مثلاً اختلافات الحركات في «من/مَن» أو «إنّ/أن». هذه خاصية
      في دقة النموذج نفسه، لا خطأ في النص.
  EN: This app tracks recitation at the letter level (without diacritics). Some words that differ
      only in diacritics (min/man, inna/anna) are indistinguishable to the tracker — a property of
      the recognizer's output, not of the Quran text.
