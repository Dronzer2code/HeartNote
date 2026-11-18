# SUBMISSION.md — DEV post draft

Draft of every section of the DEV challenge template (`PRD.md` § SUBMISSION REQUIREMENTS). Tag: `#presagechallenge`. Replace every `TODO` before publishing.

---

## What I Built

**Heartnote** — a voice note that carries your heartbeat.

People already pay for keepsakes that preserve a loved one's heartbeat, but those need a Doppler or stethoscope and days of waiting. "Send your heartbeat" phone apps make you cover the camera with a finger, so you can't talk while they measure: voice and heartbeat are never captured together.

Heartnote records a voice message while the front camera reads your pulse and breathing with the Presage SmartSpectra SDK — hands-free, from your face. When the note plays, a heartbeat sound and a phone vibration land on every beat you had *while you were saying those exact words*, and a particle heart beats along. The note exports as a standard `.m4a` that plays in WhatsApp or any messaging app.

Who it's for: grandparents recording for grandchildren, people living far from family, long-distance partners, anyone who wants a message that feels physically present.

## Demo

TODO: video link (under 2 minutes) and GIF.

Shot list (from `MVP.md`):
1. The problem in one line.
2. First launch → consent screen.
3. Record: face in the circle, "Finding your pulse…", live BPM + breathing waveform and the beating sphere, speak a short message.
4. Playback: dot rows filling as it plays, the phone vibrating on each beat (film it with a second camera), smile marker.
5. Hold mode: the particle heart beating in your hand.
6. Export → share to WhatsApp → play on another phone.
7. Close on the promise: no video stored, nothing uploaded by Heartnote, delete anytime.

## Code

TODO: public repository link.

Native Android, Kotlin 2.2, Jetpack Compose. Setup (API key in `local.properties`, build, permissions) is in the README.

## How It Responds to Human Signals

- **Pulse waveform → real beats.** SmartSpectra's arterial-pressure trace (~30 Hz) is resampled, detrended and peak-detected into individual beat times; secondary waves and double detections are removed, and short gaps are filled from the pulse rate. On a 60-second test recording this gave 85 beats (84.6 BPM) against the SDK's mean pulse rate of 83.7 BPM. Every beat becomes a synthesized lub-dub, a vibration, and a flash in the playback dot chart.
- **Live, while you speak:** a 2,400-particle sphere ripples on your detected beats. Samples arrive ~1.5 s late, so the sphere shows your real rhythm on a short fixed delay instead of faking real-time; the dot-matrix BPM is a 5-sample median of the live pulse rate.
- **Breathing** drives a live waveform and the sphere's slow swell.
- **Face presence** gates recording: no face, no recording; face gone for 3 s, recording discarded.
- **Expressions:** moments where `HAPPY` confidence stays high become smile markers on the playback timeline.
- **Honest labelling:** the heartbeat is synthesized at your measured beat times, and the app says so ("Timed to your measured pulse"). No health numbers are shown after recording.

## Prize Categories

- **Most Creative or Delightful Use** — a heartbeat you can feel in your hand, captured from the face while you talk, and sent as an ordinary voice note.
- **Best Thoughtful Real-World Use** — heartbeat keepsakes without clinical equipment, with consent-first, on-device-only data handling.

## Consent and Data Handling

Source: `TRD.md` § CONSENT AND DATA HANDLING.

- **Consent:** on first launch, a consent screen explains what the camera measures and what is stored. An optional tour follows (skippable; it uses only generated data and never records or plays a real person). Camera and microphone permissions are requested only after the user taps "I understand". Before every recording the user confirms "I am recording myself", and recording cannot start without a face in view.
- **Not stored:** camera frames or video (`imageOutputEnabled = false`; no frames are written anywhere).
- **Stored on the device only:** voice audio, the mixed `.m4a`, beat timestamps, breathing curve, pulse-rate samples and smile segments. App data is excluded from cloud backup and device transfer.
- **Sharing:** only when the user taps Share, and only the chosen file — a Heartnote video (`.mp4`) or audio (`.m4a`). Shared files contain the sender's voice, the heartbeat sound, the beat timing and display values so Heartnote can replay the vibration; they contain **no raw camera-derived signals** (no pulse waveform, no pulse-rate samples, no breathing, no expressions). The share sheet says so: "Shared files include your heartbeat timing so Heartnote can replay it."
- **Receiving:** import is always user-initiated (file picker, "Share → Heartnote", or "Open with"). Received notes are stored only on the recipient's phone and can be deleted at any time.
- **Retention:** until the user deletes the note; uninstalling removes all data.
- **Deletion:** Delete removes every file of that note.
- **Heartnote servers:** none, and no accounts. Heartnote sends no data anywhere; files travel only through apps the user chooses (WhatsApp, email, Nearby Share…).
- **SDK telemetry:** TODO — fill in from Presage's Telemetry and Privacy docs (the SDK has a `config.enableTelemetry` switch; the API key needs network access).
- **Optional AI services:** none.
- **Not a medical device;** no health interpretation is shown.

## Agent Session (optional)

TODO: link to a public agent session, or remove this section.

## Credits

Presage SmartSpectra SDK · Inter (SIL OFL) · Lucide icons (ISC) · UI inspired by <designer — TODO: Subarna to fill>.
