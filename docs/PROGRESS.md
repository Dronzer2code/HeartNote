# PROGRESS.md — Heartnote

Dated log of what is done, what remains, and known issues. Newest entry last.

## 2026-11-16 — MVP Days 1–2

Done:
- Project skeleton, SmartSpectra 3.5.0 integration, spike S1–S5 answered (see `TRD.md` § UNRESOLVED).
- F3 lock-on, F4 recording (48 kHz WAV), F5 live pulse + breathing waveform, 2.5 s SDK tail flush.
- `signals.json` with all raw aligned samples. A 60 s device recording checked offline: bpTrace has no gaps, and rough peak detection gives 84.6 BPM against an 83.7 BPM mean pulse rate.

Remaining: MVP Days 3–6 and the full UI (Phases A–F).

## 2026-11-17 — Orientation

Done:
- Moved `DESIGN.md` and references to `docs/design/`, `CLAUDE.md` to the repo root (paths used by DESIGN.md and the heartnote-ui skill).
- Git-ignored `testdata/private/` (developer recordings) and `docs/design/ref-*` (third-party reference images, not redistributed).

Known issues:
- Test phone not connected at session start; device checks run whenever it is attached.

## 2026-11-17 — Phase A: pipeline (MVP Day 3)

Done:
- `BeatExtractor` + `SmileDetector` with 12 synthetic-fixture unit tests; real 60 s recording gives 85 beats (84.6 vs 83.7 BPM).
- `HeartbeatSynth`, `Mixer`, `AacExporter` (+5 unit tests); `NoteProcessor` F6 pipeline with Retry/Delete.
- `NotePlayer` (ExoPlayer, 10 ms beat cursor → `BeatBus`), `HeartbeatHaptics` with one app-wide playback-only subscriber, interim `PlaybackScreen`, navigation Record → Playback.
- `testdata/private/heartbeat-preview.wav`: heartbeat rendered from the real recording for a listening check.

Remaining: device verification of save → playback → haptics; Phases B–F.

Known issues:
- **The developer's 60 s recording has an all-zero voice.wav.** Android was silencing the mic (likely the Microphone access privacy toggle, or another app holding the mic). The app now detects this within 2 s and tells the user; the cause on the test phone is still to be confirmed.
- Not yet verified on device: AAC export, playback sync, haptics, heartbeat audibility on the phone speaker (`SPEAKER_HARMONICS`).
