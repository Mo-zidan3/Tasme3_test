# Stage B merge runbook (continue from here)

Stacked PRs, merge IN ORDER (each merge → CI re-runs on main with instrumented 27/35):
1. PR #4  feat/t7-pipeline        (real pipeline)   — run 45300215711 GREEN (build+release+secrets-scan; ktlint 0; coverage 93.1/91.0); MERGED
2. PR #5  feat/t8-mvp-features    (F7/F8/Summary/Resync/§11) — after merging #4, REBASE
   #5 onto main (its branch already sits on T7's tip) and let CI run before merging.
3. PR #6  feat/t9-ui-tests        (Compose tests + WCAG + manual script) — base is #5's branch (feat/t8-mvp-features).
4. PR #7  feat/t11-app-shaped-eval (app-shaped §13 harness + CI replay) — base is #6's branch (feat/t9-ui-tests).
   (V-044 correction: the earlier runbook line said "#9's branch" — there is no #9; the
   chain is #4 → #5 → #6 → #7, verified against the API before any merge.)
After each merge, confirm the MAIN run (5 jobs incl. instrumented 27/35) is green from the
run's output — never from the workflow file. All Stage-B branches are mine; rebasing +
force-pushing them is safe (never force-push main or Mo's commits).

Known-honest items:
- Phase 3 is NOT DONE until Mo executes manual steps H1–H4 on hardware (docs/TESTING.md).
- The T11 tracker-replay report lands in the build job log of PR #7's run
  (scripts/t11_replay.sh → tools:eval over 12 committed hypothesis files).
- Arabic strings remain UNREVIEWED until Mo signs off.
- detekt ceiling 200 and ktlint baseline discipline unchanged (I5).

Sandbox artifacts (NOT in git): Mo's 6 clips live in /tmp/my-project/upload/ + /tmp/clips/
(16 kHz WAVs); the T11 emitter script reads /tmp/real_clips_manifest.json — regenerate via
the commands in git history (commit messages of eval/data/PROVENANCE.md + t11 script).
