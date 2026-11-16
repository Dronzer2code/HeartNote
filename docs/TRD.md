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

Observed properties (sample uses `.observe(lifecycleOwner) { }`; concrete type UNKNOWN, assumed LiveData — verify in spike S1):

| Property | Use |
|---|---|
| `sdk.processingStatus` | `IDLE`, `STARTING`, `RUNNING`, `STOPPING`, `ERROR` → drives RecordScreen state |
| `sdk.validationStatus` | `code.name` shown as user hint (e.g., face not found) |
| `sdk.error` | Error banner |
| `sdk.metrics` | Signal data |

Metric fields used:

| Field | Type of item | Heartnote use |
|---|---|---|
| `metrics.hasFace()` | Boolean | F2 gate |
| `metrics.hasCardio()` | Boolean | F3 lock-on complete |
| `metrics.cardio.pulseRateList` | `.value`, `.timestamp` | F5 live BPM; beat fallback |
| `metrics.cardio.arterialPressureTraceList` | `.value`, `.timestamp` | Primary beat source (availability UNKNOWN — spike S2) |
| `metrics.breathing.rateList` | `.value` | F5 live breathing rate |
| `metrics.breathing.upperTraceList` | `.value`, `.timestamp` (assumed) | F7 breath glow |
| `metrics.face.expressionList[].scoresList` | `.type: ExpressionType`, `.confidence` | F8 (`ExpressionType.HAPPY`) |
| `metrics.cardio.hrvList` | `.rmssd` | NOT USED (C5) |

Imports: `MetricType` from `com.presagetech.smartspectra.proto.MetricTypesProto`; `ExpressionType` from `com.presagetech.smartspectra.proto.MetricsProto`.

Compose integration: `CameraPreview.kt` creates `PreviewView` inside `AndroidView(factory = { PreviewView(it) })` and passes `surfaceProvider` to `SignalCapture` before `sdk.start()`.

Alternative: SDK headless mode (`android/docs/headless-mode.md`, caller-owned camera input). Not used unless the `PreviewView` path fails in Compose. Content of that doc UNKNOWN.

## AUDIO CAPTURE (`capture/AudioCapture.kt`)

- `AudioRecord`, source `MediaRecorder.AudioSource.MIC`, 48 000 Hz, mono, PCM 16-bit.
- Written to `notes/<id>/voice.wav` during recording.
- `audioStartNs = SystemClock.elapsedRealtimeNanos()` captured when the first buffer is read.
- Max note duration: 60 s (DECISION, changeable). Limits trial usage and file size.

## TIMELINE ALIGNMENT (`signal/SignalAligner.kt`)

All stored signal times are **milliseconds from audio start** (`tMs`).

Approach (pending spike S3):
1. On each `sdk.metrics` emission, record `arrivalNs = SystemClock.elapsedRealtimeNanos()`.
2. For the newest sample in the emission, compute `offset = arrivalNs − sdkTimestampToNs(sample.timestamp)`.
3. Use the minimum observed offset across the session (least latency) as the session offset.
4. `tMs = (sdkTimestampToNs(ts) + offset − audioStartNs) / 1e6`.
5. Deduplicate samples by SDK timestamp (lists MAY be cumulative or batched — UNKNOWN, spike S4).

## BEAT EXTRACTION (`signal/BeatExtractor.kt`)

Primary source: `arterialPressureTraceList`.
1. Resample to uniform 30 Hz (linear interpolation).
2. Detrend: subtract 1.0 s centered moving average.
3. Peak detection: local maxima, minimum distance 333 ms (≤180 BPM), prominence ≥ 0.3 × rolling std (tunable).
4. Reject inter-beat intervals outside 333–1500 ms (40–180 BPM). Fill gaps using the rate fallback for that segment.

Fallback source: `pulseRateList`.
- Integrate phase: `t[n+1] = t[n] + 60000 / bpmAt(t[n])`, where `bpmAt` linearly interpolates pulse rate samples.

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
  "beatSource": "trace",
  "beatsMs": [812, 1640, 2455],
  "breathing": [{ "tMs": 0, "v": 0.12 }],
  "pulseRate": [{ "tMs": 1000, "bpm": 74.2 }],
  "smiles": [{ "startMs": 12000, "endMs": 14500 }]
}
```

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
| S1 | Is `sdk.metrics` LiveData, Flow, or another type? How to collect it in Compose? | Adapter code only. |
| S2 | Is `arterialPressureTraceList` populated on the free trial key? (A third-party SDK mirror lists the relative blood-pressure waveform as access-on-request.) | Use `pulseRateList` fallback; `beatSource = "rate"`. |
| S3 | Units and epoch of `.timestamp` (ms / µs; session-relative / wall clock / monotonic)? | Alignment formula changes. |
| S4 | Do metric lists contain only new samples per emission or cumulative history? | Dedupe strategy changes. |
| S5 | Does the pulse signal stay stable while the user talks? | Lengthen F3 lock-on; smooth rate; show "speak naturally, keep your face steady". |
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
