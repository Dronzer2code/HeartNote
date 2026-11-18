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

## 2026-11-17 — Phase B: design system

Done:
- `HeartnoteTokens.kt` (HnColors / HnType / HnDims from DESIGN.md), Material theme mapped onto the tokens, window background `LightBg`.
- Inter 400/500/600 (latin subset, OFL) in `res/font`; Lucide icons (ISC) as `HeartnoteIcons`; both licenses in `assets/licenses/`.
- Components with `@Preview`s: `DotMatrixNumber`, `DotBarChart`, `TwoToneHeading`, `CircleIconButton`, `ArcNavBar`, `SlideActionPill`, `ProgressLine`. All previews rendered through Android Studio (`android studio render-compose-preview`) and checked against `ref-screens.webp`.

Known issues (Phase B):
- This Android CLI version has no `android preview`; previews are rendered via `android studio render-compose-preview` (needs Studio open on the project).

## 2026-11-17 — Phase C: beat bus, particle sphere, RecordScreen

Done:
- `LiveBeatScheduler` (6 s trace buffer, `BeatExtractor.detectBeats`, 1.8 s display delay, de-duplication, late-beat handling) with 2 unit tests that simulate the measured SDK delivery.
- `ParticleEngine` (shared: rotation/drag/fling, touch push, ripples, perspective, 4 depth buckets via native `drawPoints`, blur glow on API 31+, frame loop, debug FPS overlay + `HeartnoteFps` Logcat line every 5 s, reduced motion) and `ParticleSphere` (2,400 Fibonacci points, noise, breath, beat ripples, dark core).
- RecordScreen rebuilt to the middle reference with the DESIGN.md state table (Confirm checkbox → Preview → LockOn % → Recording timer → Flushing → Processing, Failed with Retry/Delete), circular desaturated camera preview in the sphere core fading to 0% on Recording, breathing waveform, lighting-tips sheet. Renders checked against the reference.

Remaining (needs the phone): ≥ 50 fps check (`adb logcat -s HeartnoteFps`), sphere pulses vs displayed BPM, camera at 0% alpha still feeding the SDK (fallback: `CAMERA_ALPHA_RECORDING = 0.12f`), every state reachable.

Known issues:
- Live display delay: at the spec's 1,800 ms about a third of beats arrive after their slot and pulse up to ~0.4 s late; 2,300 ms fixes it (test evidence in DESIGN.md § BEAT SYNC). Kept at spec value — decide after seeing it on the phone.

## 2026-11-17 — Phase D: Library and Playback

Done:
- `DotChartData` (peak-to-peak bpTrace per column, p95-normalised, 0.15–1) + 4 unit tests.
- LibraryScreen (left reference): app mark, two-tone heading, latest-note card with dot-matrix beat count and 9×36 dot chart, earlier-notes list, empty state, ArcNavBar (Privacy, Hold latest, Record, Share latest, About).
- PlaybackScreen (right reference): 8 s dot rows with playback progress, beat-flash column from BeatBus, smile markers + jump-to-first-smile heart, tap-to-seek, rename dialog, delete confirmation, export/share (F9, FileProvider `audio/mp4`), Bluetooth hint, ArcNavBar with Play/Pause + position label.
- Verified on the emulator (Android 16) with the developer's 60 s recording: unfinished-note recovery → AAC export (980 KB) → Library card (85 beats) → Playback with progress and ~2 haptic events per beat.
- Fixes found on the emulator: AAC encoder input-size crash; SecurityException reading a hidden vibration setting; disabled CircleIconButtons losing their circle; content scrolling under the nav (now fades).
- Privacy fix: cloud backup and device transfer disabled (template default would have uploaded notes).

Remaining: sound + vibration + dot flash sync by ear/hand on the phone; Phase E–F.

Known issues:
- Cold start took 12.7 s on the emulator, but the whole emulator was dropping frames; re-measure on the phone. If slow there, defer SDK configuration in `HeartnoteApp` until RecordScreen.
- Studio preview rendering broke after a component signature change (stale fast-preview classes); screens were checked on the emulator instead.

## 2026-11-17 — Phase E: consent, navigation, Hold

Done:
- ConsentScreen (F1) on first launch; camera + mic requested only after "I understand"; "Not now" stays with an explanation. Consent stored in SharedPreferences.
- Privacy sheet (TRD § CONSENT AND DATA HANDLING in plain words) and About sheet (credits, version) from the Library nav and shield button.
- Navigation: Consent → Library → Record → Playback → Hold, shared-axis X transitions (300 ms).
- `ParticleHeart` on the shared `ParticleEngine` (implicit-surface point cloud, lub/dub scale + ripples, ±25° sway, clamped drag with spring-back, reduced motion) + geometry unit tests.
- HoldScreen to `ref-heart.png` (radial glow with 15% lub flash, translucent back button, centred title, floating card with 2 s bpTrace and counting beats, dark pill nav), immersive + keep-screen-on, auto-plays with the same sync and haptics.
- Verified on the emulator: first launch → consent → Library; Hold plays, counts beats, heart at 44–51 fps on the (overloaded) emulator.

Remaining: Phase F; device checks for all animation and sync.

Known issues:
- Privacy copy says the SDK uses the internet for its licence key and points to Presage's policy; SDK telemetry details are still UNKNOWN (`sdk.config.enableTelemetry` exists — decide whether to set it false after reading Presage's docs).

## 2026-11-17 — Phase F: validation and submission prep

Done:
- DESIGN.md § VALIDATION: forbidden-word search over `strings.xml` → zero matches; no hard-coded user-visible strings; every icon button has a contentDescription (the 5 null ones are decorative inside labelled parents); touch targets ≥ 48 dp; edge-to-edge insets on every screen, Hold immersive.
- Emulator screenshots of Consent, Library (empty + with note), Playback (idle + playing), Record (permission state, sphere animating), Hold, Privacy and About sheets compared with the references.
- Release build: `assembleRelease` succeeds with R8; arm64-v8a only; 50 MB (debug 132 MB). The FPS overlay and Logcat line are behind `BuildConfig.DEBUG` (unreachable in release).
- `README.md` and `docs/SUBMISSION.md` (all DEV template sections) written.

### Must be done by hand
1. Reconnect the Android 15 phone and run the device checks below.
2. Fill `<designer — TODO: Subarna to fill>` in README.md and SUBMISSION.md.
3. SDK telemetry: read Presage's Telemetry and Privacy docs, fill the TODO in SUBMISSION.md and `TRD.md`, and decide whether to set `sdk.config.enableTelemetry = false`.
4. Record the demo video (shot list below) and add `docs/media/demo.gif`.
5. Publish the repo, then the DEV post with `#presagechallenge`.

### Device checks still open (the emulator can't do these)
- **Microphone**: the 2026-11-16 recording was all-zero audio. Check Settings › Privacy › Microphone access and that no other app holds the mic; the app now stops after 2 s of silence with an explanation.
- RecordScreen ≥ 50 fps (`adb logcat -s HeartnoteFps`), sphere pulses ≈ displayed BPM; decide on `LiveBeatScheduler.DISPLAY_DELAY_MS` (1,800 spec vs 2,300 measured-better).
- Camera at 0% alpha keeps feeding the SDK during Recording (fallback `CAMERA_ALPHA_RECORDING = 0.12f`).
- Heartbeat audible on the phone speaker (`HeartbeatSynth.SPEAKER_HARMONICS`), and sound, vibration and dot flash land together.
- Exported `.m4a` plays in WhatsApp on another phone.
- Cold-start time (12.7 s on the overloaded emulator).

### Demo video shot list (MVP.md § DEMO VIDEO SCRIPT, under 2 minutes)
1. One line of problem: heartbeat keepsakes need a stethoscope; finger-on-camera apps can't record your voice.
2. Open the app → consent screen (brief).
3. Record: face in the circle, "Finding your pulse…", live BPM + breathing line + beating sphere, speak a short message.
4. Playback: dot rows filling, a second camera filming the phone vibrating on each beat, smile marker.
5. Hold: the particle heart beating, the phone held close.
6. Export → share to WhatsApp → play on another phone.
7. Close: no video stored, nothing uploaded by Heartnote, delete anytime.

## 2026-11-18 — Step 1: onboarding (ONBOARDING.md)

Done:
- R-O1: there was no demo-note code. The "auto-created note" was the Library's unfinished-note recovery (Phase D), which silently processed leftover raw files — on the test phone that is the developer's own 2026-11-16 recording. Recovery removed: notes now appear only when the user records or imports them.
- R-O3: no bundled demo audio/signal files exist (`assets/` holds only licences; no `res/raw`).
- WelcomeChoiceScreen, 3-page TourScreen (generated 70 BPM data only, Skip on every page, Next/Start, page dots), "Feel a sample beat" (8 s HeartbeatSynth at 70 BPM via in-memory AudioTrack + haptics, label "Sample heartbeat — generated, not a real person", nothing saved), "Replay tour" in the About sheet, `tourCompleted` in SharedPreferences (DataStore isn't in the project).
- First launch: Consent → WelcomeChoice → Tour/Library. No permission is requested in the tour.

Pending — needs the developer:
- R-O2 migration: NOT implemented. No creation code or flag identifies a "demo" note, and the note on the phone is most likely the developer's real recording; ONBOARDING.md says to ask before deleting in that case.

Verified: build + all unit tests; WelcomeChoice and Tour rendered as Studio previews. The emulator was stopped by Claude Code for low memory and not restarted.

## 2026-11-18 — Step 2: payload + audio-only import (SHARE_AND_IMPORT.md)

Done:
- `HeartnotePayload` (gzip JSON, exactly the § PAYLOAD fields; unit tests check the field list and that no raw signals appear), `PayloadTrack` remux pass adding the `application/x-heartnote` MP4 metadata track, `ShareExports` (own notes: fresh `Heartnote for <title>.m4a` with payload; received notes: the original file).
- `ImportManager`: copy → payload track (else `BeatRecovery`, a stub that refuses until step 5) → `signals.json` (beats, smiles, dot columns only) + `meta.json` with `origin = "received"` → opens Playback.
- Entry points: Library Import action (outer-right nav slot, `ACTION_OPEN_DOCUMENT`), share target (`ACTION_SEND` video/audio/octet-stream) and "Open with" (`ACTION_VIEW` content mp4/m4a). Incoming files wait in `ImportRequests` until the Library (after consent) imports them.
- Library "Yours" / "Received" groups, "Received • date" subtitles, import progress/error banner. About moved into a merged "Privacy & about" sheet (with Replay tour and the new data-handling lines).
- Playback/Hold play the received file; labels "Timed to their measured pulse" / "Recovered from the shared file"; Hold mini trace drawn from beat times for received notes.

Needs the phone: the MediaMuxer metadata track round trip (write → WhatsApp Document → MediaExtractor read) is documented Android behaviour but untested here; WhatsApp-as-Document import test.

## 2026-11-18 — Step 3: amplified heartbeat

Done:
- `HeartbeatSynth.Preset.SPEAKER` (default for every new mix): 55/110/165 Hz lub (gains 1/0.5/0.25), 70/140/210 Hz dub at 0.6, plus an 8 ms Hann-windowed 800 Hz transient at 0.15; no 150 Hz low-pass (it would remove the transient). `Preset.CLASSIC` keeps the original TRD sound.
- Mixer: heartbeat × 0.6 (was 0.35) with a peak limiter (instant attack, 50 ms release) before the soft clip.
- `voice.wav` is kept after export (notes can be re-mixed/re-exported). Notes made earlier keep their mix.
- 2 new unit tests (transient + harmonics present; limiter keeps a loud voice under full scale). `testdata/private/heartbeat-preview.wav` regenerated with the new sound.

Needs the phone: tune `Mixer.HEARTBEAT_GAIN` by ear on the speaker with a real voice recording (the existing test recording's voice track is silent).

## 2026-11-18 — Step 4: video export (F13) + share sheet

Done:
- `ParticleEngine.renderAt` / `prepareAt`: deterministic frames from beat times (2 JVM tests: same time + beats → identical particles regardless of history).
- Media3 1.11.1 checked with javap: `CanvasOverlay(boolean)` + `onDraw(Canvas, long)`, `EditedMediaItem.Builder.setDurationUs/setFrameRate`, multi-sequence `Composition.Builder`, `Transformer.start/cancel/getProgress` all exist (Option A fits). **Option B chosen**: no new dependencies, exact frame timing, reuses the MediaCodec path already proven on the emulator.
- `HeartnoteVideoFrame` (720×1280: gradient + lub flash, particle heart, title, dot-matrix beats-played counter, footer + wordmark, 2 s end card; glow as a radial-gradient disc, no blur) — design checked via Studio previews of real frames.
- `HeartnoteVideoExporter`: H.264 Baseline (no B-frames), surface input + `lockHardwareCanvas`, PTS rewritten to frame × 33,333 µs, AAC copied from heartnote.m4a, then `PayloadTrack.write` remux adds the payload. Cancellable; cached as `share/Heartnote for <title>.mp4` until renamed.
- Share sheet (F9 revised): Video (default) / Audio only, progress + Cancel, "Couldn't make the video" + Retry (Audio only stays available), WhatsApp-Document tip and the disclosure line. Received notes skip the sheet (original file only).

Deviations: video bitrate 1.8 Mbps instead of 2 Mbps (2 Mbps + 128 kbps × 60 s = 15.96 MB, no headroom under the 16 MB target); cache path `share/Heartnote for <title>.mp4` instead of `notes/<id>/heartnote.mp4` so the recipient sees a meaningful file name. Hold screen's share button shares audio directly.

Needs the phone: encode a real note (speed, size ≤ 16 MB for 60 s, cancel), play in Google Photos and in WhatsApp (sent as media).
