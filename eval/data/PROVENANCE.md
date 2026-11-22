# PROVENANCE — Mo's recitation clips (2026-11-22)

## Authorization of record
Mo uploaded these clips with the instruction (IQRAA session, 2026-11-22):

> "Mo's audio clips (he uploaded them with this prompt) — Mo states these recitations are
> permitted for use."

Per that statement, the clips are authorized for use in this project's evaluation and CI.
Mo is the product owner speaking for the permission ("Mo states … permitted for use").
No gated benchmark / tlog audio is committed — none of these are from a gated dataset.

## Clips (as uploaded, MP3/AAC 44.1 kHz stereo, WhatsApp-forwarded "Original audio")
| # | File (uploaded) | Duration | Identified content (host decode + ayah match) | Speaker |
|---|---|---|---|---|
| 1 | ezz_elawami_Original audio_29017540214573455.mp3 | 75.3 s | 39:42 (az-Zumar, repeated) | Ezz El-Awami (reciter) |
| 2 | riadmostaf_Original audio_29126780870296850.mp3 | 68.8 s | 19:4–5 (Maryam) | Riad Mostaf (reciter) |
| 3 | riadmostaf_Original audio_29710391055313722.mp3 | 41.0 s | 5:23 (al-Ma'idah) | Riad Mostaf |
| 4 | riadmostaf_Original audio_29734506272823931.mp3 | 38.0 s | 54:10–12 (al-Qamar) | Riad Mostaf |
| 5 | riadmostaf_Original audio_30208365058757351.mp3 | 70.6 s | 25:27–29 (al-Furqan) | Riad Mostaf |
| 6 | yasser.al.dossary_Original audio_40504772022471849.mp3 | 32.1 s | 2:200–201 (al-Baqarah) | Yasser Al-Dossary (reciter) |

## Honest classification (this is the §13 gate-relevance statement)
- **Source type: PROFESSIONAL STUDIO recitations by named reciters** (audio quality
  55–75 kbps AAC stereo, clean, high SNR, consistent mic technique). They are NOT
  phone-mic recordings of ordinary users.
- **Speakers: 3** (Ezz El-Awami, Riad Mostaf, Yasser Al-Dossary).
- **Recording style: tartil-leaning studio pace, single speaker per clip, quiet
  background (studio). No noisy clips in this set.**
- **§13 phone-gate criteria NOT met**: the gate requires ≥60 held-out correct clips,
  ≥3 speakers recorded ON PHONE MICS. These 6 studio clips (3 speakers) do NOT satisfy
  the phone gate and cannot be used to certify phone-mic behavior. **Professional studio
  recitations do not satisfy the §13 phone gate.** All numbers derived from this set are
  **INDICATIVE** (model-space, clean-audio subset), and WRONG stays rendered as CLOSE
  in the app until a real phone-mic gate set exists.
- tartil/hadr: the Al-Dossary clip (32 s, al-Baqarah 200–201) is the fastest/hadr-leaning;
  Ezz El-Awami's is the slowest (75 s on one ayah, with repetition).

## Committed into git
Exactly ONE clip is committed as the permanent CI accuracy fixture:
- `eval/data/fixture_54_10_riadmostaf.wav` — 10.05 s, 16 kHz mono PCM WAV, the first
  10 s of clip #4 = **al-Qamar 54:10** («فَدَعَا رَبَّهُ أَنِّى مَغْلُوبٌ فَانْتَصِرْ»),
  host decode: «فدعا ربه اني مغلوب فانتصر» (25 tokens, clean single ayah).
  Committed together with this permission record (the authorization of record quoted above).
- All OTHER audio stays OUT of git: `eval/data/*.wav` beyond the fixture and the original
  MP3s are git-ignored (see .gitignore: eval/data/** except the fixture + PROVENANCE.md).
- sha256 of the committed fixture is recorded in VERIFICATION (V-038) for integrity.
