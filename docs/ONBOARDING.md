# ONBOARDING.md — Heartnote

## PURPOSE

Defines the first-launch experience. It replaces the automatic demo recording with an optional, skippable tour.
Visual rules come from `docs/design/DESIGN.md`. Consent content comes from `docs/PRD.md` F1.

## CONTEXT

- Current behaviour (found 2026-11-18): on first launch the app adds a pre-recorded demo note (audio + heartbeat) to the library without asking.
- Required behaviour (developer decision): nothing pre-recorded is ever forced on the user. Users may take a short tour or skip it.

## REQUIREMENTS

- R-O1: Remove the code that creates the demo note on first launch.
- R-O2: One-time migration on app update: if the auto-created demo note exists in the library, delete it and all its files. Identify it by the flag or id used by the current creation code (inspect the code). If it can't be identified with certainty, ask the developer before deleting anything.
- R-O3: Remove the bundled demo audio and signal files from `assets/`/`res/raw` if nothing else uses them.
- R-O4: First-launch order: ConsentScreen (F1) → WelcomeChoice → (Tour or Library).
- R-O5: The tour can be skipped from every page.
- R-O6: The tour is replayable from the privacy/about sheet: `Replay tour`.
- R-O7: The tour MUST NOT create a note, record anything, or play any real person's voice or heartbeat.
- R-O8: Persist `tourCompleted` (completed OR skipped) in DataStore Preferences (or SharedPreferences if DataStore isn't already in the project). The WelcomeChoice is shown only while it's false.

## SCREENS

### WelcomeChoice (`ui/onboarding/WelcomeChoiceScreen.kt`)

- `LightBg`.
- `TwoToneHeading("Welcome to", "Heartnote")`.
- 13 sp `InkGrey` line: `Voice notes that carry your real heartbeat.`
- Primary black pill (56 dp): `Take the 30-second tour`.
- Secondary text button: `Skip`. Skip sets `tourCompleted = true` and goes to Library.

### Tour (`ui/onboarding/TourScreen.kt`)

A horizontal pager with 3 pages, a dot page indicator, and a `Skip` text button top-right on every page. Each page uses existing components with fake, generated data:

| Page | Visual | Heading (two-tone) | Body |
|---|---|---|---|
| 1 Record | Small `ParticleSphere` (200 dp) pulsing at a generated 70 BPM | `Look and` / `speak` | `The camera reads your pulse from your face while you record. No video is saved.` |
| 2 Feel | `DotBarChart` row filling up + a vibration on each generated beat | `They hear and` / `feel it` | `Your voice plays with a heartbeat timed to yours, and the phone beats in their hand.` |
| 3 Share | `ParticleHeart` (200 dp) beating at 70 BPM | `Send it` / `anywhere` | `Share as a video that plays in any chat. Open it in Heartnote to feel the heartbeat.` |

- Page 3 buttons: primary `Start` (sets `tourCompleted = true` → Library); secondary `Feel a sample beat`.
- `Feel a sample beat`: 8 s of a heartbeat generated at runtime by `HeartbeatSynth` at 70 BPM with matching haptics. No voice. Label while playing: `Sample heartbeat — generated, not a real person`. Nothing is saved.
- Vibration in the tour only happens on page 2 and in `Feel a sample beat`, and only if system vibration is on.
- Reduced motion: no pager auto-animation; visuals show their static state.

## CONSTRAINTS

- MUST NOT ship any recorded human voice or heartbeat in the APK.
- MUST NOT request camera or microphone permission during the tour. Permissions are requested only after Consent is accepted, at the first recording.
- Strings in `strings.xml`; all components from DESIGN.md.

## VALIDATION

```text
[ ] Fresh install: Consent → WelcomeChoice → Skip → Library is empty.
[ ] Fresh install: Consent → Tour → Start → Library is empty.
[ ] Updating an install that had the demo note: the demo note is gone; user notes are untouched.
[ ] Replay tour works from the about/privacy sheet.
[ ] No bundled audio/signal demo files remain in the APK (check with Build > Analyze APK).
```

## RELATED DOCUMENTS

- `docs/PRD.md` — add F15 Optional tour; update F1 flow.
- `docs/PROCESS_FLOW.md` — update the User Journey diagram (Consent → WelcomeChoice → Tour/Library).
- `docs/design/DESIGN.md` — add WelcomeChoice and Tour to the Screen Map.
