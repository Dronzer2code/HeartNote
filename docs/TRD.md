# TRD.md — Heartnote

## PURPOSE

Defines HOW Heartnote is technically implemented.
Product requirements (R*, C*, F*) are defined in `PRD.md`. Build order is in `MVP.md`. Runtime flows are in `PROCESS_FLOW.md`.

## STACK

| Item | Value | Source / status |
|---|---|---|
| Language | Kotlin 2.2.x | Required by SmartSpectra Android SDK |
| UI | Jetpack Compose (Material 3) | Project decision |
| minSdk | 28 | Required by SDK |
| compileSdk | 36.1 or later | Required by SDK |
| targetSdk | 36 | Project decision |
| Android Gradle Plugin | 8.10.1 or later (AGP 9.x if Gradle 9) | Required by SDK |
| SmartSpectra SDK | `com.presagetech:smartspectra:3.5.0` | Presage docs `android/docs/option-1-api-key.md` |
| Camera preview | `androidx.camera:camera-view:1.6.0` (`PreviewView`) | Presage docs |
| Other SDK-sample deps | `androidx.activity:activity-ktx:1.8.1`, `androidx.core:core-ktx:1.18.0`, `androidx.lifecycle:lifecycle-runtime-ktx:2.10.0` | Presage docs |
| Audio playback | `androidx.media3:media3-exoplayer` (latest stable) | Project decision |
| Serialization | `kotlinx-serialization-json` | Project decision |
| Test device | Android 15 phone (model UNKNOWN) | Developer |

Repositories (`settings.gradle.kts`): `google()`, `mavenCentral()`. `repositoriesMode = FAIL_ON_PROJECT_REPOS`. A snapshot repo is needed only for `-SNAPSHOT` SDK versions (not used).

## PROJECT STRUCTURE

Package: `com.heartnote.app` (MAY be changed before first commit; MUST be consistent afterwards).

```text
heartnote/
├── app/src/main/
│   ├── AndroidManifest.xml
│   └── java/com/heartnote/app/
│       ├── MainActivity.kt              # single activity, hosts Compose NavHost
│       ├── HeartnoteApp.kt              # Application class, SDK config
│       ├── ui/
│       │   ├── nav/HeartnoteNavGraph.kt
│       │   ├── onboarding/ConsentScreen.kt        # F1
│       │   ├── library/LibraryScreen.kt           # F10
│       │   ├── record/RecordScreen.kt             # F2, F3, F4, F5
│       │   ├── record/RecordViewModel.kt
│       │   ├── playback/PlaybackScreen.kt         # F7, F8, F9, F11
│       │   ├── playback/PlaybackViewModel.kt
│       │   ├── components/CameraPreview.kt        # AndroidView(PreviewView)
│       │   ├── components/SignalWaveform.kt       # Canvas waveform
│       │   └── theme/
│       ├── capture/
│       │   ├── SignalCapture.kt         # wraps SmartSpectraSdk.shared
│       │   ├── AudioCapture.kt          # AudioRecord → WAV
│       │   └── RecordingSession.kt      # owns shared timeline, start/stop
│       ├── signal/
│       │   ├── BeatExtractor.kt         # trace → beat timestamps; rate fallback
│       │   ├── SignalAligner.kt         # SDK timestamps → audio timeline
│       │   └── SmileDetector.kt         # F8
│       ├── audio/
│       │   ├── HeartbeatSynth.kt        # PCM lub-dub generation
│       │   ├── Mixer.kt                 # voice + heartbeat → mixed PCM
│       │   └── AacExporter.kt           # MediaCodec + MediaMuxer → .m4a
│       ├── haptics/HeartbeatHaptics.kt  # per-beat vibration
│       ├── data/
│       │   ├── Heartnote.kt             # data classes
│       │   ├── NoteRepository.kt        # file storage, list, delete
│       │   └── NoteProcessor.kt         # F6 processing pipeline (PROCESS_FLOW steps 1–8)
│       ├── ui/beat/BeatBus.kt           # SharedFlow<BeatEvent> (DESIGN.md § BEAT SYNC)
│       ├── ui/playback/NotePlayer.kt    # ExoPlayer + 10 ms beat cursor, shared with HoldScreen
│       ├── share/ShareHelper.kt         # FileProvider + ACTION_SEND
│       ├── share/HeartnotePayload.kt    # gzip JSON payload (SHARE_AND_IMPORT.md § PAYLOAD)
│       ├── share/PayloadTrack.kt        # remux pass: adds/reads the application/x-heartnote track
│       ├── share/ShareExports.kt        # share files (audio with payload, cached video)
│       ├── share/ImportManager.kt       # F14 import + ImportRequests (share target / open-with)
│       ├── share/BeatRecovery.kt        # decode for recovery; BeatRecoveryCore.kt = algorithm
│       ├── export/HeartnoteVideoFrame.kt     # one 720×1280 video frame
│       ├── export/HeartnoteVideoExporter.kt  # F13 encoder (MediaCodec Option B)
│       ├── ui/onboarding/WelcomeChoiceScreen.kt, TourScreen.kt, GeneratedBeats.kt  # F15
├── app/src/main/res/xml/file_paths.xml  # FileProvider paths
├── docs/ (PRD.md, MVP.md, TRD.md, PROCESS_FLOW.md, ONBOARDING.md, SHARE_AND_IMPORT.md, PROGRESS.md, SUBMISSION.md, design/DESIGN.md)
├── CLAUDE.md
└── README.md
```

## MANIFEST

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />      <!-- SDK API key auth -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.VIBRATE" />
```

- `CAMERA`, `RECORD_AUDIO`: runtime permissions. Request only after F1 consent is accepted.
- `VIBRATE`, `INTERNET`: normal permissions, granted at install.
- Register a `FileProvider` (`${applicationId}.fileprovider`) for F9 sharing.
- No storage permissions on API 29+. All note files live in `context.filesDir`. Exception (2026-11-18): "Save to phone" uses MediaStore.Downloads (no permission) on API 29+ and `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` on API 28, requested only when saving. `<queries>` lists `com.whatsapp` for the "Open WhatsApp" button.
- `android:allowBackup="false"` and `data_extraction_rules.xml` exclude everything from cloud backup and device transfer (FIXED 2026-11-17: the template had backup on, which would have copied notes to the user's Google Drive, contradicting R4 and the consent copy).
- No foreground service. Recording and playback happen only while the app is visible.

## API KEY HANDLING (C4)

- Key stored in `local.properties` as `PRESAGE_API_KEY=...` (git-ignored).
- Exposed via `buildConfigField("String", "PRESAGE_API_KEY", ...)` in `app/build.gradle.kts`.
- `local.properties.example` committed with a placeholder.
- README documents how judges obtain their own key from Presage.
- OAuth option (`android/docs/option-2-oauth.md`) is NOT used in MVP.

## SDK INTEGRATION (`capture/SignalCapture.kt`)

Known API (from Presage `option-1-api-key.md`):

```kotlin
val sdk = SmartSpectraSdk.shared
sdk.config.apiKey = BuildConfig.PRESAGE_API_KEY
sdk.config.imageOutputEnabled = false                 // C3: no bitmap frames
sdk.useCamera(CameraSelection.Front)
sdk.config.previewSurfaceProvider = previewView.surfaceProvider
sdk.config.requestedMetrics =
    SmartSpectraConfig.breathingMetrics +
    SmartSpectraConfig.cardioMetrics +
    listOf(MetricType.EXPRESSIONS)
// lifecycleScope.launch { sdk.start() } / sdk.stop()
```

Observed properties (VERIFIED 2026-11-16 by decompiling smartspectra-3.5.0.aar `classes.jar` with javap):

| Property | Type | Use |
|---|---|---|
| `sdk.processingStatus` | `LiveData<ProcessingStatus>` (`IDLE`, `STARTING`, `RUNNING`, `STOPPING`, `ERROR`) | drives RecordScreen state |
| `sdk.validationStatus` | `LiveData<ValidationStatus>`; has `.code: ValidationCode` and `.hint: String` (ready-made user hint) | show `.hint` (e.g., face not found) |
| `sdk.error` | `LiveData<SmartSpectraError>`; has `.code`, `.message`, `.retryable` | Error banner |
| `sdk.metrics` | `LiveData<MetricsProto.Metrics>` | Signal data |
| `sdk.metricsFlow` | `SharedFlow<MetricsProto.Metrics>` — same emissions as `sdk.metrics` | preferred collection path in coroutines/Compose |

Additional verified API: `sdk.start()`/`sdk.stop()`/`sdk.reset()` are `suspend` functions; `SmartSpectraSdk.availableCameras(context)` (suspend); `sdk.checkDeviceReadiness()`; `sdk.version: String`; `CameraSelection.Front/Back/Default/ById(id)`; `SmartSpectraConfig` companion lists `breathingMetrics`, `cardioMetrics`, `faceMetrics`, `micromotionMetrics`, `edaMetrics`; `sdk.config.enableTelemetry: Boolean` exists (relevant to CONSENT AND DATA HANDLING — document or disable before submission). The SDK self-initializes via androidx.startup (`SmartSpectraInitializer`); no manual context init needed.

Emission behaviour (MEASURED 2026-11-16 on the Android 15 device):

- Emissions are split by type: face, cardio and breathing arrive in SEPARATE `Metrics` messages, so `hasFace()`/`hasCardio()`/`hasBreathing()` describe only that emission. "Face present" MUST be defined as: a face-bearing emission arrived within the last 1000 ms. F3 lock-on complete = first `pulseRate` sample received after entering LockOn. LockOn timeout: 20 s.
- Delivery latency (arrival − sample timestamp, medians): `arterialPressureTrace` 1.5 s, `pulseRate` 2.0 s, `breathing.upperTrace` 0.2 s.
- **SDK tail flush rule**: on Stop, stop `AudioCapture` immediately (fixes `durationMs`), keep the SDK running for 2.5 s to flush delayed samples, then `sdk.stop()`. UI during the flush: "Finishing your heartbeat…" (see `PROCESS_FLOW.md` Flushing state).

Live display rules (F5):

- Pulse BPM: median of the last 5 `pulseRate` values (S5 jump mitigation).
- Breathing: live waveform drawn from `breathing.upperTraceList` (`SignalWaveform.kt`). `breathing.rateList` is sparse and noisy (7–10 samples per session, values 4–24 observed), so the breathing-rate NUMBER is shown only as small secondary text, and only when a sample arrived within the last 10 s.

Metric fields used (VERIFIED against decompiled proto classes, except where noted):

| Field | Type of item | Heartnote use |
|---|---|---|
| `metrics.hasFace()` | Boolean | F2 gate |
| `metrics.hasCardio()` | Boolean | F3 lock-on complete |
| `metrics.cardio.pulseRateList` | `MeasurementWithConfidence`: `.value: Float`, `.timestamp: Long`, `.confidence: Float`, `.stable: Boolean` | F5 live BPM; beat fallback |
| `metrics.cardio.arterialPressureTraceList` | `MeasurementWithConfidence` (same fields) | Primary beat source (populated-on-trial UNKNOWN — spike S2) |
| `metrics.breathing.rateList` | `MeasurementWithConfidence` | F5 live breathing rate |
| `metrics.breathing.upperTraceList` | `Measurement`: `.value: Float`, `.timestamp: Long`, `.stable: Boolean` | F7 breath glow |
| `metrics.face.expressionList[].scoresList` | `ExpressionScore`: `.type: ExpressionType`, `.confidence: Float` | F8 (`ExpressionType.HAPPY` — enum value name still to confirm on device) |
| `metrics.face.talkingList` | `DetectionStatus` (`.detected`, `.stable`, `.timestamp`) | may help S5. Empty unless `MetricType.TALKING` is explicitly in `requestedMetrics` (observed 2026-11-16) |
| `metrics.cardio.hrvList` | `Hrv` | NOT USED (C5) |

`MetricType` enum values include `PULSE_RATE`, `ARTERIAL_PRESSURE_TRACE`, `BREATHING_RATE`, `EXPRESSIONS`, `TALKING`, `HRV`, `PULSE_RATE_STRICT`, `BREATHING_RATE_STRICT`.
Imports: `MetricType` from `com.presagetech.smartspectra.proto.MetricTypesProto`; `ExpressionType` from `com.presagetech.smartspectra.proto.MetricsProto`.

Compose integration: `CameraPreview.kt` creates `PreviewView` inside `AndroidView(factory = { PreviewView(it) })` and passes `surfaceProvider` to `SignalCapture` before `sdk.start()`.

Alternative: SDK headless mode (`android/docs/headless-mode.md`, caller-owned camera input). Not used unless the `PreviewView` path fails in Compose. Content of that doc UNKNOWN.

## AUDIO CAPTURE (`capture/AudioCapture.kt`)

- `AudioRecord`, source `MediaRecorder.AudioSource.MIC`, 48 000 Hz, mono, PCM 16-bit.
- Written to `notes/<id>/voice.wav` during recording.
- `audioStartEpochMs = System.currentTimeMillis()` captured when the first buffer is read (SDK timestamps are epoch µs, so this is the shared-timeline anchor — see § TIMELINE ALIGNMENT).
- Max note duration: 60 s (DECISION, changeable). Limits trial usage and file size.

## TIMELINE ALIGNMENT (`signal/SignalAligner.kt`)

All stored signal times are **milliseconds from audio start** (`tMs`).

Algorithm (REPLACED 2026-11-16 after S3 answered — SDK timestamps are Unix epoch µs, same clock as `System.currentTimeMillis()`, so no offset estimation is needed):
1. `audioStartEpochMs = System.currentTimeMillis()` captured at the first `AudioRecord` buffer read.
2. `tMs = timestamp_us / 1000 − audioStartEpochMs`.
3. Keep trace samples with `tMs < 0` (pre-recording context improves peak detection at the start), but drop BEATS with `tMs < 0` or `tMs > durationMs`.
4. Deduplicate samples by SDK timestamp as a guard only (S4: lists are incremental; duplicates not observed).

## BEAT EXTRACTION (`signal/BeatExtractor.kt`)

Primary source: `arterialPressureTraceList`.
1. Resample to uniform 30 Hz (linear interpolation).
2. Detrend: subtract 1.0 s centered moving average.
3. Peak detection: local maxima, minimum distance 333 ms (≤180 BPM), prominence ≥ 0.3 × rolling 4 s std (tunable). Peaks lower than 0.3 × the 80th-percentile peak height within ±3 s are dropped (secondary/dicrotic waves; the median is NOT a safe reference because secondary waves can be half of all candidates).
4. Trace usability: the trace is used only if ≥ 60% of beat intervals are within ±35% of the interval implied by the (median-filtered) pulse rate; otherwise `beatSource = "rate"`. Pure noise fails this check.
5. A peak arriving sooner than 0.55 × the expected interval is a double detection and is dropped. An interval longer than min(1500 ms, 1.6 × expected) is a missed beat: the missing beats (round(gap / expected) − 1) are inserted evenly from the rate fallback; same for the note's start and end. Any inserted beat inside the note → `beatSource = "mixed"`.

Validated 2026-11-17: synthetic fixture (unit tests, `BeatExtractorTest`) detects every beat within ±40 ms incl. jitter, drift, noise and dicrotic waves. Developer's 60 s device recording: 85 beats = 84.6 BPM vs 83.7 BPM mean pulse rate, all intervals 518–896 ms. `BeatExtractor.detectBeats()` (steps 1–3 + double-detection removal, no gap filling) is reused live by `LiveBeatScheduler`.

Fallback source: `pulseRateList`.
- Apply a 5-sample median filter to pulse rate values first (S5: ~20 BPM jumps observed around face movement/loss).
- Integrate phase: `t[n+1] = t[n] + 60000 / bpmAt(t[n])`, where `bpmAt` linearly interpolates the filtered pulse rate samples.

Each note stores `beatSource = "trace" | "rate" | "mixed"`. UI label (R6):
- `trace`: "Heartbeat timed to your measured pulse"
- `rate`/`mixed`: "Heartbeat paced by your live pulse"

## HEARTBEAT SYNTHESIS (`audio/HeartbeatSynth.kt`)

Per beat at time `t`, with interval `RR` to the next beat:
- **Lub**: 55 Hz sine × exponential decay (τ = 40 ms), duration 120 ms, gain 1.0.
- **Dub**: 70 Hz sine × exponential decay (τ = 30 ms), duration 90 ms, gain 0.6, at `t + clamp(0.3 × RR, 200 ms, 350 ms)`.
- Low-pass 150 Hz (one-pole). Output 48 kHz mono float.
- UPDATED 2026-11-18 (SHARE_AND_IMPORT.md § AMPLIFIED HEARTBEAT): every new mix uses `Preset.SPEAKER` — lub 55 Hz + 110 Hz (0.5) + 165 Hz (0.25), τ 40 ms, plus an 8 ms Hann-windowed 800 Hz transient at 0.15; dub 70/140/210 Hz at 0.6 of the lub. No 150 Hz low-pass in this preset (it would remove the transient). `Preset.CLASSIC` is the original pure-sine sound above. Notes made earlier keep their mix. MUST be tuned by ear on the phone speaker.

`Mixer.kt` (REPLACED 2026-11-18, the heartbeat is the dominant sound): voice normalised to **−24 dBFS RMS** (gated over 50 ms frames above −50 dBFS so pauses don't count; at most +30 dB; a silent voice is untouched), then **voice gain** (default −9 dB, tuning range −18…0); heartbeat (SPEAKER preset, peak 1.0) scaled to a **−3 dBFS peak** × **heartbeat gain** (default 0 dB, range −12…+6); sum through a peak limiter (instant attack, 50 ms release) and a soft clip at **−1 dBFS**; PCM16. `MixSettings.current` holds the gains; only debug builds can override it (Mix tuning screen). The same mix is used for in-app playback, the `.m4a` and the `.mp4`. Final values: PENDING — chosen by ear on the phone speaker.

Silent microphone (found 2026-11-17): the developer's first 60 s device recording produced an all-zero `voice.wav` (2,892,480 samples, every one 0). Android delivers digital silence, not an error, when the mic privacy toggle is off or another app holds the mic. `AudioCapture.peakAbs` tracks the loudest sample; RecordViewModel discards the recording after 2 s of exact silence and shows `record_error_mic_silent`. Root cause on the test device: UNKNOWN until the phone is reconnected.

## HAPTICS (`haptics/HeartbeatHaptics.kt`)

- Obtain `Vibrator` via `VibratorManager.defaultVibrator` (API 31+) or `getSystemService(Vibrator::class.java)`.
- REVISED 2026-11-18 (device test: too weak). `HapticStrength` setting Soft / Medium / **Strong (default)**, in the Privacy, settings & about sheet with a test button:
  - Strong, with amplitude control: ONE waveform per beat on the LUB event, timings `[0, 70, dubGap − 70, 45]` ms, amplitudes `[0, 255, 0, 200]`, no repeat; `dubGap` = `clamp(0.3 × RR, 200, 350)` carried on the LUB `BeatEvent.dubDelayMs`; the DUB event is skipped. Without amplitude control: `createOneShot(70, DEFAULT_AMPLITUDE)` on LUB and a 45 ms one-shot on DUB.
  - Medium: the same shape at 180 / 140.
  - Soft: the 2026-11-17 behaviour — one THUD/CLICK primitive per event (LUB 1.0, DUB 0.5).
  - A single app-wide subscriber in `HeartnoteApp` plays them, and only for events with a `noteTimeMs` (playback); live recording beats carry `null` and never vibrate.
  - Vibration usage (API 33+): `USAGE_MEDIA` (default) or `USAGE_TOUCH`, switchable in the debug Mix tuning screen. **Result: PENDING** — compare on the phone (also on Silent) and record which feels stronger and isn't muted.
  - Hint "Turn on vibration to feel the heartbeat." + "Open settings" (`Settings.ACTION_SOUND_SETTINGS`) when there is no vibrator, the ringer is on Silent, or (TOUCH) touch feedback is off. The media-vibration intensity cannot be read by apps (hidden setting).
  - Debug trace: tag `HeartnoteHaptics` logs the import result, the note's beats on load, every fired beat and every vibrator call (Fix 3). Cause of "no vibration on imported notes": PENDING — run `ImportedNoteBeatsTest` and the logs on the phone.
- API 31+ and `areAllPrimitivesSupported(PRIMITIVE_THUD)`: `VibrationEffect.startComposition().addPrimitive(PRIMITIVE_THUD, 1.0f).addPrimitive(PRIMITIVE_THUD, 0.5f, dubDelayMs).compose()`.
- API 30 or THUD unsupported but `PRIMITIVE_CLICK` supported: same composition using `PRIMITIVE_CLICK`.
- Otherwise: `VibrationEffect.createWaveform(longArrayOf(0, 40, dubDelayMs − 40, 25), intArrayOf(0, 255, 0, 140), -1)`; if `!hasAmplitudeControl()` use `createOneShot(40, DEFAULT_AMPLITUDE)`.
- API 33+: vibrate with `VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA)`.
- If system vibration is disabled, playback screen shows: "Turn on vibration to feel the heartbeat."
  - IMPLEMENTED as `!vibrator.hasVibrator()` only (2026-11-17). The user's media-vibration switch is a hidden setting; reading it throws `SecurityException` on Android 12+ (found on the emulator), so "vibration switched off" cannot be detected by an app.

Sync: a coroutine polls `ExoPlayer.currentPosition` every 10 ms and fires each beat whose `tMs` was crossed since the previous poll. Seeking resets the beat cursor.

## EXPORT (`audio/AacExporter.kt`)

- Input: mixed PCM16 48 kHz mono.
- `MediaCodec` encoder `audio/mp4a-latm`, AAC-LC, 128 kbps → `MediaMuxer` `MUXER_OUTPUT_MPEG_4`.
- Output: `notes/<id>/heartnote.m4a`.
- One AAC frame (1,024 samples = 2,048 bytes) per `queueInputBuffer`. FOUND 2026-11-17 on the emulator: `c2.android.aac.encoder` advertises `input.buffers.max-size = 2048` and fails with UNKNOWN_ERROR after ~2 s of audio when given full 16 KB buffers. Written to `heartnote.m4a.part`, renamed on success, deleted on failure.
- Verified on the emulator (Android 16): the developer's 60 s recording exports to a 980 KB `.m4a`, plays in ExoPlayer, and fires ~2 haptic events per beat.
- Unfinished notes (raw `voice.wav` + `signals.json`, no `heartnote.m4a` — app killed during Processing, or the Failed screen was left) are completed by the Library on its next start, one attempt per note per process.
- Generated at save time (F6) so playback and share use the same file.
- `ShareHelper.kt`: `FileProvider.getUriForFile`, `Intent.ACTION_SEND`, type `audio/mp4` or `video/mp4`, `FLAG_GRANT_READ_URI_PERMISSION`.

### Sharing and import (2026-11-18, SHARE_AND_IMPORT.md)

- Shared files are built at share time in `notes/<id>/share/`: `Heartnote for <title>.m4a` (remuxed `heartnote.m4a` + payload track) and `Heartnote for <title>.mp4` (F13, cached until rename). The in-app `heartnote.m4a` is never modified. Received notes are re-shared only as their original file.
- Payload: `application/x-heartnote` MP4 metadata track, one sample at 0 µs, gzip JSON with exactly `format, version, title, createdAt, durationMs, beatSource, beatsMs, smiles, dotColumns` (unit-tested; no trace, pulse rate, breathing or expressions). `dotColumns` = 200 ms DotChartData columns.
- Video (F13): Option B — H.264 Baseline (no B-frames), 720×1280, 30 fps, 1 s I-frame interval, surface input drawn with `lockHardwareCanvas`, PTS rewritten to frame × 33,333 µs, AAC copied from `heartnote.m4a`, then the payload remux. Media3 1.11.1 Option A APIs were verified with javap (`CanvasOverlay(boolean)`/`onDraw(Canvas, long)`, `EditedMediaItem.Builder.setDurationUs/setFrameRate`, multi-sequence `Composition`) and fit, but B was chosen: no new dependencies and exact, deterministic frame timing. Video bit rate 1.8 Mbps (spec 2 Mbps) for headroom under 16 MB per 60 s. Frames come from `ParticleEngine.renderAt` (deterministic, unit-tested).
- Import (F14): `ImportManager` copies the file to `notes/<id>/received.<ext>`, reads the payload track or runs `BeatRecovery`, writes `signals.json` (beats, smiles, `dotColumns`; empty `raw`) and `meta.json` (`origin = "received"`, `receivedFile`). Entry points: Library Import action (`ACTION_OPEN_DOCUMENT`), `ACTION_SEND` (video/*, audio/*, octet-stream), `ACTION_VIEW` (content video/mp4, audio/mp4). Incoming intents wait in `ImportRequests` until the Library (after consent) imports them.
- Beat recovery (no payload): decode → mono 8 kHz → band-pass **40–100 Hz** (spec: 40–220 Hz; at 220 Hz voice fundamentals correlate like beats — beats p10 NCC 0.39 vs voice-only max 0.46 — at 100 Hz beats p10 0.77 vs voice max 0.49) → NCC with the SPEAKER lub/dub templates (run at 2 kHz after the band-pass) → threshold max(0.6, median + 4·MAD over 4 s) → 333 ms spacing → dub within clamp(0.3·RR, 200, 350) ± 40 ms → rhythm-guided gap search (relaxed floor near expected beat times) → RR 333–1500 ms → refuse if < 10 beats or BPM outside 40–180. `beatSource = "recovered"`, `dotColumns` 1.0 at beat columns else 0.25.
- **Beat-recovery match rate:** JVM proxy (speech-like voice at the real mix gain, 8 kHz band limit + noise, `BeatRecoveryTest`): 100% of beats within ±60 ms, 0 extra, at 62/78/96 BPM, with a deep (≥85 Hz) voice, and for CLASSIC notes; voice-only audio refused. **On-device WhatsApp-as-media result: UNKNOWN — not yet measured** (needs the phone).
- MediaMuxer metadata-track write + MediaExtractor read-back: documented Android behaviour (API 26+), **not yet verified on a device**.

## DATA MODEL (`data/Heartnote.kt`)

Storage root: `context.filesDir/notes/<id>/` where `<id>` is a UUID.

| File | Content |
|---|---|
| `voice.wav` | Raw voice. KEPT after export from 2026-11-18 (reverses the earlier delete decision) so notes can be re-mixed and re-exported. |
| `heartnote.m4a` | Mixed voice + heartbeat. Playback source; shared copies are built from it. |
| `share/` | Files built for sharing (`Heartnote for <title>.m4a` / `.mp4`, both with payload). |
| `received.<ext>` | Received notes only: the imported file, played as-is. |
| `signals.json` | Signal track (schema below). |
| `meta.json` | id, title, createdAt, durationMs, plus beatCount, beatSource, smileCount so the Library never parses `signals.json`. Default title `you` (heading reads "Heartnote for you"). |

```json
{
  "version": 1,
  "durationMs": 42000,
  "audioStartEpochMs": 1794567890123,
  "beatSource": "trace",
  "beatsMs": [812, 1640, 2455],
  "breathing": [{ "tMs": 0, "v": 0.12 }],
  "pulseRate": [{ "tMs": 1000, "bpm": 74.2 }],
  "smiles": [{ "startMs": 12000, "endMs": 14500 }],
  "raw": {
    "bpTrace": [{ "tMs": -850, "v": 0.51, "c": 0.93, "stable": true }],
    "pulseRate": [{ "tMs": 1000, "v": 74.2, "c": 0.88 }],
    "brUpperTrace": [{ "tMs": 0, "v": 0.12 }],
    "brRate": [{ "tMs": 5000, "v": 14.0, "c": 0.7 }],
    "expressions": [{ "tMs": 1200, "type": "HAPPY", "c": 0.71 }],
    "talking": [{ "tMs": 1200, "detected": true }]
  }
}
```

`raw` (added Day 2) stores ALL aligned samples unfiltered — including trace samples with `tMs < 0` — so beat peaks can be verified offline. `beatSource`, `beatsMs`, `breathing`, `pulseRate`, `smiles` are filled by the Day 3 processing pipeline; until then they are null/empty.

`pulseRate` is stored for beat regeneration only. It is NOT displayed during playback (C1).

`NoteRepository.delete(id)` MUST recursively delete `notes/<id>/`.

## SMILE DETECTION (`signal/SmileDetector.kt`)

A smile moment is a segment where `HAPPY` confidence ≥ 0.6 for ≥ 500 ms (thresholds tunable). Adjacent segments < 1 s apart are merged.

## ANDROID 15 NOTES

- Apps targeting SDK 35+ are edge-to-edge by default. Every screen MUST apply `Modifier.safeDrawingPadding()` or equivalent `WindowInsets` padding.
- Recording and playback run only in the foreground. Stop `sdk` and `AudioRecord` in `onStop`.

## CONSENT AND DATA HANDLING

Source for the DEV post's required section and for `ConsentScreen.kt` copy.

- Camera consent: requested only after the user accepts the F1 explanation. The user confirms they are recording themselves before every recording (F2).
- Not stored: camera frames or video (`imageOutputEnabled = false`; no frame writes).
- Stored on-device only: voice audio, mixed `.m4a`, beat timestamps, breathing curve, pulse rate samples, smile segments.
- Sharing: only when the user taps Share; only the chosen file (`.mp4` video or `.m4a` audio) is shared. Shared files contain the sender's voice, the heartbeat sound, the beat timing and display values (payload), and no raw camera-derived signals (no trace, no pulse-rate samples, no breathing, no expressions).
- Import: always user-initiated (picker, share target or "Open with"). Received notes are stored only on the recipient's phone and can be deleted at any time.
- Retention: until the user deletes the note. Uninstalling removes all data.
- Deletion: Delete removes every file of that note.
- Heartnote servers: none; no accounts. Heartnote sends no data anywhere.
- SDK telemetry: UNKNOWN. MUST be filled from Presage's Telemetry and Privacy docs before submission. The API key requires network access.
- Optional AI services: none.
- Not a medical device; no health interpretation is shown.

## UNRESOLVED (DAY 1 SPIKE)

| ID | Question | Impact if negative |
|---|---|---|
| S1 | ANSWERED 2026-11-16 (decompiled AAR): `sdk.metrics` is `LiveData<MetricsProto.Metrics>` AND `sdk.metricsFlow` is `SharedFlow<MetricsProto.Metrics>`. Collect `metricsFlow` in a coroutine (ViewModel `viewModelScope`), expose `StateFlow` to Compose — implemented in `RecordViewModel`. | — |
| S2 | ANSWERED 2026-11-16 (device run): `arterialPressureTraceList` IS populated on the trial key. ~29.8 Hz, delivered in ~1 s chunks of 30 samples, contiguous (33 ms gaps). The first chunk can contain up to 330 samples of backlog. Primary beat source = `"trace"`. | — |
| S3 | ANSWERED 2026-11-16 (device run): all `.timestamp` values are Unix epoch MICROSECONDS (wall clock). `tMs = timestamp_us / 1000 − audioStartEpochMs` (see § TIMELINE ALIGNMENT). | — |
| S4 | ANSWERED 2026-11-16 (device run): lists are INCREMENTAL — `n == new` in all 2,816 observed emissions. Keep timestamp dedupe as a guard only. | — |
| S5 | PARTIALLY ANSWERED 2026-11-16 (device run): BPM stayed within 78–89 in a clean 46 s session; jumps of ~20 BPM appeared around face movement/loss. Mitigation: 5-sample median filter on pulse rate (live display and beat fallback) and keep the RR-interval rejection in `BeatExtractor`. (`metrics.face.talkingList` + `TALKING` metric type remain available if talking itself proves noisy.) | — |
| S6 | Free-trial usage limits per key (minutes/sessions)? | Limit test runs; ask on Presage Discord. |

Answers MUST be written back into this file, replacing UNKNOWN entries.

## DEVELOPMENT TOOLING (AGENTS)

- IDE: Android Studio Rabbit 2 (stable). Claude is attached via Bring Your Own Agent: **Settings > Tools > AI > Agents** → install **Claude Agent** → sign in. Alternative: JetBrains Marketplace plugin **Claude Code [Beta]** (terminal-backed, interactive diff viewer).
- Claude Code plugin: `android-cli` from the official marketplace `android/android-cli-plugin`:
  ```text
  claude plugin marketplace add android/android-cli-plugin
  claude plugin install android-cli@android-cli-plugin
  ```
  Then install Android CLI and run `android init`. The `android-cli` skill lets the agent create projects, manage virtual devices, inspect live UI, render Compose previews (`android preview`), and search the Android Knowledge Base.
- Official Android skills: `android skills list`, then `android skills add <name>` for skills covering Jetpack Compose, CameraX, and media/audio. Exact skill names UNKNOWN until `android skills list` is run.
- Install the plugin only from the exact source `android/android-cli-plugin`.
- Real-device testing is REQUIRED for camera, SDK, and haptics. The emulator cannot validate F3–F7.

## RELATED DOCUMENTS

- `PRD.md` — requirements (R*), constraints (C*), features (F*).
- `MVP.md` — build order and definition of done.
- `PROCESS_FLOW.md` — sequence of the components defined here.
