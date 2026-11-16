# CLAUDE.md — Heartnote agent entry point

## PURPOSE

Entry point for any coding agent working in this repository. Read this file first, then the documents listed under DOCUMENTS before changing code.

## CONTEXT

Heartnote is a native Android app (Kotlin, Jetpack Compose) for the DEV Presage SmartSpectra SDK Challenge. It records a voice note while the SmartSpectra SDK reads the speaker's pulse and breathing from the front camera, then replays the voice with a heartbeat sound and vibration timed to the measured pulse. Deadline: 2026-11-22, 11:59 PM PST. Individual entry by Subarna Maity.

## DOCUMENTS

| File | Read when |
|---|---|
| `docs/PRD.md` | Deciding what a feature must do; checking constraints C1–C6. |
| `docs/MVP.md` | Choosing what to build next; checking the day plan and definition of done. |
| `docs/TRD.md` | Implementing anything; exact APIs, file names, algorithms, unresolved questions. |
| `docs/PROCESS_FLOW.md` | Implementing screen state, pipelines, or data lifecycle. |

## RULES

- MUST follow component and file names in `docs/TRD.md` § PROJECT STRUCTURE.
- MUST NOT add medical, diagnostic, or health-interpretation wording anywhere (UI strings, README, comments shown to users).
- MUST NOT write camera frames to disk or enable `sdk.config.imageOutputEnabled`.
- MUST NOT commit the Presage API key. Read it from `local.properties` → `BuildConfig.PRESAGE_API_KEY`.
- MUST NOT add network calls, analytics, or crash reporting.
- MUST apply edge-to-edge insets padding on every screen (targetSdk 36).
- When a `docs/TRD.md` § UNRESOLVED item is answered by testing, update that file with the answer.
- When behaviour is unknown, ask the developer instead of guessing SDK APIs. SmartSpectra API names in `docs/TRD.md` are the only verified ones.

## BUILD AND TEST

- Build: `./gradlew :app:assembleDebug`
- Camera, SDK, and haptics MUST be tested on the physical Android 15 device; the emulator cannot validate them.
- Use the `android-cli` skill (plugin `android-cli@android-cli-plugin`) for device management, UI inspection, and `android preview` of Compose screens.
