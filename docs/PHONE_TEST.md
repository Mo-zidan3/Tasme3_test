# PHONE_TEST.md — Mo's one-hour phone test kit (H1–H4, Phase 3)

This is the **non-technical** checklist for the hardware gates. Everything here runs on
YOUR phone with the `app-release-smoke.apk` CI builds for you — no Android Studio needed.
Phase 3 is DONE only when this page is filled in and committed (paste results into
`eval/reports/phone-<device>.md` for H4; the rest can be edits of this file).

**What you need (10 minutes of prep):**

1. An Android phone, Android 8.0 (Oreo) or newer, arm64 (any real phone).
2. The `app-release-smoke.apk` artifact: open the repo → **Actions** → the latest green
   `ci` run on `main` → scroll to **Artifacts** → download `app-release-smoke.apk` → unzip.
3. Copy `app-release-smoke.apk` to the phone (USB cable, or any file-transfer/cloud app).
4. On the phone, tap the file in the **Files / My Files** app. It will ask to allow
   installs from that source → allow. Install.
   - If your browser/computer has `adb`: `adb install -r app-release-smoke.apk` is easier.
5. ~20 quiet minutes and headphones-free surroundings for the recording tests.

**About this APK (read once):** it is the REAL app with R8 minification ON — the same
bytes CI proved decodes on the emulator. It is **debug-signed: for TESTING only, not for
Play.** It works completely offline; the app has no internet permission at all.

---

## H1 — microphone permission, denied permanently

| # | Step | What you should see (expected) | Observed / Pass-Fail |
|---|---|---|---|
| 1 | Open the app for the first time | The onboarding screen in Arabic, with a «اسمح بالوصول للميكروفون»-style button and the privacy note (no data collected) | |
| 2 | Tap **Don't allow** twice on the mic dialog | The app shows the *denied-permanently* card: an explanation and a button that opens the phone's **App settings** page for the app | |
| 3 | Tap that button | Android opens **Settings → Apps → iqraa → Permissions** (this exact page) | |
| 4 | Go back to the app without changing anything | The card is still there — the app never crashes, never asks again via the system dialog, and never shows a blank screen | |
| 5 | In Settings, grant Microphone, return to the app | The recitation screen is reachable — the app picks the grant up without a reinstall or force-stop | |

## H2 — true process death, session restored

| # | Step | What you should see (expected) | Observed / Pass-Fail |
|---|---|---|---|
| 1 | Start a recitation session and recite a few words until some words are colored | Colored/updated word states are visible | |
| 2 | Press **Home**, then swipe the app away from Recents (this is the true kill) | — | |
| 3 | Reopen the app from the launcher | The Home screen shows «**ملخص الجلسة السابقة**» (previous session summary) built from the saved checkpoint — NOT an empty new session, and not a crash | |
| 4 | Continue the same passage | The recitation screen continues from where the checkpoint left it (previously-correct words stay correct) | |

## H3 — real interruptions (call, focus, another app's mic)

| # | Step | What you should see (expected) | Observed / Pass-Fail |
|---|---|---|---|
| 1 | Mid-recitation, phone yourself (or have someone call you), answer ~10 s, hang up, return | The app is alive; a pause/release banner or a paused state appears during the call; recitation resumes cleanly afterwards | |
| 2 | Mid-recitation, open a voice-recorder app or the phone's assistant (something that grabs the mic), then come back | The app degrades honestly (paused / banner), recovers when the mic is free; no crash, no silent zombie recording | |
| 3 | Lock the screen mid-recitation, unlock | The session continues; no restart-from-zero of the word states | |

## H4 — phone performance (Q7) — run the script

This one is scripted so the numbers are real, not impressions. On a computer with `adb`
and the phone connected (USB debugging on):

```bash
# from the repo root; the phone has the app installed (steps above)
bash scripts/phone_perf.sh
```

The script measures — on YOUR hardware, labeled **REAL HARDWARE**:

- **cold start** of the main activity (`am start -W`, 3 runs);
- **model load / decode RTF / peak RSS** from the instrumented probes
  (`NativeSmokeInstrumentedTest`, the same fixture CI uses) — the real-device answer to
  the emulator's INDICATIVE RTF 0.030;
- **R8 decode on your phone**: `ReleaseSmokeInstrumentedTest` must log
  `decoded N words (R8 build)` — this closes the "release decodes on device" risk on
  REAL hardware, not just the emulator;
- **live 60-second recitation session**: peak RSS (`dumpsys meminfo`) and battery drain
  (`dumpsys batterystats`).

It writes **`eval/reports/phone-<device>.md`** — paste that file back (commit it or send
it) and Q7 is closed with the recorded numbers. If any step fails, keep the file — it
contains the exact error, and that is what we debug from.

---

## Filling in the results

- H1–H3: edit the "Observed / Pass-Fail" cells above (one line each is enough; note the
  phone model + Android version at the top of your edit).
- H4: `eval/reports/phone-<device>.md` from the script.
- Phase 3 is complete when H1–H4 are all PASS and recorded. Do not mark partial passes as
  done — an honest FAIL here is more valuable than a green lie (same rule as CI).
