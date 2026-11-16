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
│       │   └── NoteRepository.kt        # file storage, list, delete
│       └── share/ShareHelper.kt         # FileProvider + ACTION_SEND
├── app/src/main/res/xml/file_paths.xml  # FileProvider paths
├── docs/ (PRD.md, MVP.md, TRD.md, PROCESS_FLOW.md)
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
- No storage permissions. All files live in `context.filesDir`.
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
| `metrics.face.talkingList` | `DetectionStatus` | available; may help S5 (talking-noise mitigation) |
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
3. Peak detection: local maxima, minimum distance 333 ms (≤180 BPM), prominence ≥ 0.3 × rolling std (tunable).
4. Reject inter-beat intervals outside 333–1500 ms (40–180 BPM). Fill gaps using the rate fallback for that segment.

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
- Low-pass 150 Hz. Output 48 kHz mono float.

`Mixer.kt`: `mixed = voice + heartbeat × 0.35` (≈ −9 dB, tunable), soft-clip to [−1, 1], convert to PCM16.

## HAPTICS (`haptics/HeartbeatHaptics.kt`)

- Obtain `Vibrator` via `VibratorManager.defaultVibrator` (API 31+) or `getSystemService(Vibrator::class.java)`.
- API 31+ and `areAllPrimitivesSupported(PRIMITIVE_THUD)`: `VibrationEffect.startComposition().addPrimitive(PRIMITIVE_THUD, 1.0f).addPrimitive(PRIMITIVE_THUD, 0.5f, dubDelayMs).compose()`.
- API 30 or THUD unsupported but `PRIMITIVE_CLICK` supported: same composition using `PRIMITIVE_CLICK`.
- Otherwise: `VibrationEffect.createWaveform(longArrayOf(0, 40, dubDelayMs − 40, 25), intArrayOf(0, 255, 0, 140), -1)`; if `!hasAmplitudeControl()` use `createOneShot(40, DEFAULT_AMPLITUDE)`.
- API 33+: vibrate with `VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA)`.
- If system vibration is disabled, playback screen shows: "Turn on vibration to feel the heartbeat."

Sync: a coroutine polls `ExoPlayer.currentPosition` every 10 ms and fires each beat whose `tMs` was crossed since the previous poll. Seeking resets the beat cursor.

## EXPORT (`audio/AacExporter.kt`)

- Input: mixed PCM16 48 kHz mono.
- `MediaCodec` encoder `audio/mp4a-latm`, AAC-LC, 128 kbps → `MediaMuxer` `MUXER_OUTPUT_MPEG_4`.
- Output: `notes/<id>/heartnote.m4a`.
- Generated at save time (F6) so playback and share use the same file.
- `ShareHelper.kt`: `FileProvider.getUriForFile`, `Intent.ACTION_SEND`, type `audio/mp4`, `FLAG_GRANT_READ_URI_PERMISSION`.

## DATA MODEL (`data/Heartnote.kt`)

Storage root: `context.filesDir/notes/<id>/` where `<id>` is a UUID.

| File | Content |
|---|---|
| `voice.wav` | Raw voice. Deleted after successful export (DECISION). |
| `heartnote.m4a` | Mixed voice + heartbeat. Playback + share source. |
| `signals.json` | Signal track (schema below). |
| `meta.json` | Title, createdAt, durationMs. |

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
- Sharing: only when the user taps Share; only the `.m4a` file is shared. Signal data is never shared.
- Retention: until the user deletes the note. Uninstalling removes all data.
- Deletion: Delete removes every file of that note.
- Heartnote servers: none. Heartnote sends no data anywhere.
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
