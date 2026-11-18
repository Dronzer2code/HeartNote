# Heartnote

**A voice note that carries your heartbeat.**

Heartnote records a voice message while your phone's front camera reads your pulse and breathing with the [Presage SmartSpectra SDK](https://physiology.presagetech.com/). When the note plays back, a heartbeat sound and a phone vibration land on every beat you had *while you were saying those words*, and a particle heart beats along. The note exports as a normal audio file that plays in any messaging app.

Heartbeat and voice are captured together, hands-free, from the face — no finger on the camera, no stethoscope.

> Heartnote is not a medical device. It shows no health readings or interpretations.

![Heartnote demo](docs/media/demo.gif)
<!-- TODO: add docs/media/demo.gif (record → playback → hold). -->

Built for the [DEV Presage SmartSpectra SDK Challenge](https://dev.to/challenges/presage-2026-11-04).

## Requirements

| | |
|---|---|
| Phone | Android 9 (API 28) or later, arm64, front camera. Camera, SDK and vibration need a real phone; the emulator can't test them. |
| Build | Android Studio (2026.2 "Rabbit" or later) or the Gradle wrapper, JDK 17+ |
| Toolchain | Kotlin 2.2 (pinned by the SDK), AGP 9.5, compileSdk 37, targetSdk 36 |
| Presage | A SmartSpectra API key (free trial) |

## 1. Get a Presage API key

1. Create an account on the Presage developer portal: <https://physiology.presagetech.com/>.
2. Create an API key for the SmartSpectra SDK and copy it.

The free trial has usage limits, so short test recordings are best.

## 2. Add the key to `local.properties`

`local.properties` is git-ignored and never committed. Android Studio creates it with your `sdk.dir`; add one line:

```properties
PRESAGE_API_KEY=your_key_here
```

See [`local.properties.example`](local.properties.example). The build reads the key into `BuildConfig.PRESAGE_API_KEY`.

## 3. Build and run

```bash
./gradlew :app:installDebug          # build + install on the connected phone
./gradlew :app:testDebugUnitTest     # JVM unit tests (beat extraction, synth, charts, scheduler)
```

Or open the project in Android Studio and press Run with your phone connected (USB debugging on).

## Permissions

| Permission | Why | When |
|---|---|---|
| `CAMERA` | The SmartSpectra SDK reads pulse, breathing and expressions from the front camera. Frames are never saved. | Asked after you accept the first-launch consent screen. |
| `RECORD_AUDIO` | Records your voice note. | Same time as the camera. |
| `INTERNET` | The SDK checks its API key with Presage. Heartnote itself sends nothing. | Granted at install. |
| `VIBRATE` | Plays the heartbeat as vibration during playback. | Granted at install. |

No storage permission: notes live in the app's private storage and are excluded from cloud backup and device transfer.

## Sharing

Share a note as a **video** (plays in any chat: your voice, the heartbeat sound and the beating particle heart) or **audio only**. Both files carry the beat timing, so a recipient who opens the file in Heartnote — "Share → Heartnote", "Open with", or the Library's Import button — feels the heartbeat as vibration too. On WhatsApp, send it as a **Document** to keep that data; if it was sent as media, Heartnote recovers the beats from the heartbeat sound in the audio.

## How it responds to human signals

| Signal (SmartSpectra) | Heartnote |
|---|---|
| **Pulse waveform** (`arterialPressureTraceList`, ~30 Hz) | Beat times are detected from the waveform (resample → detrend → peak detection → gap filling), then a lub-dub sound and a vibration are placed on every beat. Live, the red particle sphere ripples on your detected beats (shown on a short fixed delay, because the SDK delivers samples ~1.5 s late). |
| **Pulse rate** (`pulseRateList`) | Live BPM in dot-matrix digits while recording (5-sample median); fills short gaps in the waveform; fallback beat source. |
| **Breathing** (`breathing.upperTraceList`) | Live breathing waveform and the sphere's gentle swell. |
| **Face presence** | Recording only starts with a face in view, and stops if the face is gone for 3 s. |
| **Expressions** (`HAPPY`) | Smile moments are marked on the playback timeline; tap the heart to jump to the first smile. |

The heartbeat you hear is *synthesized* at your measured beat times; it is not a stethoscope recording, and the app labels it that way.

## Privacy

- Camera frames and video are never stored.
- Stored on the phone only: the voice/heartbeat audio, beat times, breathing curve, pulse-rate samples and smile moments.
- Nothing is shared unless you tap Share, and then only the file you pick: a Heartnote video or audio. It contains your voice, the heartbeat sound and the beat timing (so Heartnote can replay the vibration) — no raw camera-derived signals.
- Received Heartnotes are imported only when you choose to, and stay on your phone.
- Delete removes every file of a note; uninstalling removes everything.
- Heartnote has no servers. The SmartSpectra SDK uses the internet to check the API key — see Presage's privacy policy.

## Project layout

```text
app/src/main/java/com/heartnote/app/
├── capture/   SignalCapture (SDK), AudioCapture (WAV), RecordingSession (shared timeline)
├── signal/    BeatExtractor, SmileDetector, SignalAligner
├── audio/     HeartbeatSynth, Mixer, AacExporter (.m4a)
├── haptics/   HeartbeatHaptics
├── data/      notes on disk, NoteProcessor (save pipeline)
├── share/     ShareHelper (FileProvider)
└── ui/        Consent, Library, Record, Playback, Hold; components incl. ParticleEngine
docs/          PRD, MVP, TRD, PROCESS_FLOW, design/DESIGN.md, PROGRESS, SUBMISSION
```

## Credits

- Pulse, breathing and expressions: **Presage SmartSpectra SDK** (Presage Technologies).
- Typeface: **Inter** by Rasmus Andersson — SIL Open Font License 1.1 (`app/src/main/assets/licenses/inter-OFL.txt`).
- Icons: **Lucide** — ISC License (`app/src/main/assets/licenses/lucide-ISC.txt`).
- UI inspired by <designer — TODO: Subarna to fill>. The reference images are not included in this repository or the app.

Made by Subarna Maity.
