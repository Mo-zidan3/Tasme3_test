# PRIVACY

- No INTERNET permission in the merged release manifest (CI-enforced).
- RECORD_AUDIO is the only runtime permission; rationale string per §10.7.
- Audio stays in memory (ring buffer ≤30 s); never persisted, never
  transmitted. Session history stores counts only (no audio, no transcripts).
- android:allowBackup=false; restrictive dataExtractionRules (manifest, §12.3).
- Play Data safety: no data collected.
