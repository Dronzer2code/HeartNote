# MVP.md — Heartnote

## PURPOSE

Defines the minimum version of Heartnote that MUST be built and submitted before the challenge deadline.
Feature IDs (F1–F12) are defined in `PRD.md`. Implementation details are in `TRD.md`.

## CONTEXT

- Deadline: **2026-11-22, 11:59 PM PST** (2026-11-23, 1:29 PM IST). Work starts 2026-11-16.
- Single developer. Native Android, Kotlin, Jetpack Compose.
- Presage free trial has usage limits. Limit issues are raised on the Presage Discord.

## MVP SCOPE

| Feature | In MVP | Notes |
|---|---|---|
| F1 Consent onboarding | MUST | Required for the Consent and Data Handling section. |
| F2 Self-recording gate | MUST | Checkbox confirmation + `hasFace()` check. |
| F3 Pulse lock-on | MUST | Mitigates talking-induced signal noise. |
| F4 Record Heartnote | MUST | Core feature. |
| F5 Live signal display | MUST | Required for demo verification. |
| F6 Save note | MUST | |
| F7 Playback (sound + haptics + breath glow) | MUST | Core "aww" moment. |
| F8 Smile moments | SHOULD | Cut first if behind schedule. |
| F9 Export & share (.m4a) | MUST | Enables recipients without the app. |
| F10 Library & delete | MUST | Delete is required by data-handling claims. |
| F11 Hold mode | MAY | Only if day 5 finishes early. |
| F12 Video export (.mp4) | OUT → replaced by F13 | Superseded by F13 Heartnote video (in scope from 2026-11-18, `SHARE_AND_IMPORT.md`). |
| F13 Heartnote video, F14 Import | SHOULD (added 2026-11-18) | Restores vibration + visuals for recipients. |
| F15 Optional tour | SHOULD (added 2026-11-18) | `ONBOARDING.md`. |

## DAY PLAN

| Day | Date | Deliverable | Exit condition |
|---|---|---|---|
| 1 | 2026-11-16/17 | Project skeleton + SDK spike | Live pulse rate and breathing rate displayed in a Compose screen on the Android 15 device. Spike questions S1–S4 (`TRD.md` § UNRESOLVED) answered and written into `TRD.md`. |
| 2 | 2026-11-18 | F3, F4, F5 | 30-second recording produces a WAV file + raw signal log on a shared timeline. |
| 3 | 2026-11-19 | Beat extraction, F6, F7 | Saved note plays voice + heartbeat sound + vibration in sync. |
| 4 | 2026-11-20 | F9, F10, F8 | Exported `.m4a` plays in WhatsApp. Delete removes all note files. |
| 5 | 2026-11-21 | F1, F2, UI polish, F11 if time | Full journey works from fresh install. |
| 6 | 2026-11-22 | README, demo video, DEV post | Post published before 11:59 PM PST. |

## DEFINITION OF DONE

```text
[ ] Fresh install → consent → record → playback works on the Android 15 test device.
[ ] Live BPM and breathing rate visibly change on screen during recording.
[ ] Playback vibrates on each beat and plays the heartbeat sound under the voice.
[ ] Exported .m4a plays in WhatsApp on a device without Heartnote.
[ ] Delete removes the note directory completely.
[ ] No camera frames are written to disk.
[ ] API key is read from local.properties and is absent from git history.
[ ] README contains: requirements, API key setup, build steps, permissions explanation.
[ ] Consent and Data Handling section written (content source: TRD.md § CONSENT AND DATA HANDLING).
[ ] App contains no medical or diagnostic wording.
```

## DEMO VIDEO SCRIPT (TARGET: UNDER 2 MINUTES)

1. Problem in one line: heartbeat keepsakes need a stethoscope; finger-on-camera apps can't record your voice.
2. Open app → consent screen (brief).
3. Record: face in frame, "finding your pulse", live BPM + breathing waveform visible, speak a short message.
4. Playback: show the timeline, play the note, show the phone vibrating (second camera filming the phone), glow following breath.
5. Export → share to WhatsApp → play on another phone.
6. Close on the data-handling promise: no video stored, nothing uploaded by Heartnote, delete anytime.

## CUT ORDER IF BEHIND SCHEDULE

1. F11 Hold mode
2. F8 Smile moments
3. Breath glow in F7 (keep sound + haptics)
4. NEVER cut: F1, F2, F5, F7 sound + haptics, F9, F10.

## RELATED DOCUMENTS

- `PRD.md` — feature definitions and constraints.
- `TRD.md` — technical implementation and unresolved spike questions.
- `PROCESS_FLOW.md` — flows to implement.
