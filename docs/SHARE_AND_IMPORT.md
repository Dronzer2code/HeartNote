# SHARE_AND_IMPORT.md — Heartnote

## PURPOSE

Defines how a Heartnote leaves the sender's phone and how a recipient gets the full experience (voice + heartbeat sound + vibration + visuals) back.
This replaces the audio-only share described as F9 in `docs/PRD.md` and adds two features: F13 (Heartnote video) and F14 (Import).
Visual rules come from `docs/design/DESIGN.md`. Pipeline components come from `docs/TRD.md`.

## CONTEXT

- Problem found in device testing (2026-11-18): the shared `.m4a` keeps the voice and the heartbeat sound, but the recipient gets no vibration and no visuals. Vibration only exists inside the Heartnote app, so the core experience is lost when a note is shared.
- An account system was considered and REJECTED, because it would:
  - break PRD R3 (no login), R4 (on-device data) and C6 (no network on the recipient side);
  - require a backend that stores people's heartbeat and voice;
  - force judges to sign up;
  - not fit the deadline (2026-11-22, 11:59 PM PST).
- Chosen approach: **one shareable file that works everywhere AND carries the heartbeat data.**
  - Anywhere (WhatsApp, gallery, any player): it plays as a normal video with the voice, an amplified heartbeat sound, and the beating particle heart on screen.
  - Opened in Heartnote: the app reads the heartbeat data from inside the file and plays the full experience, including vibration.

## FACTS THIS DESIGN RELIES ON

| Fact | Consequence |
|---|---|
| Android `MediaMuxer` supports a metadata track in MP4 (API 26+). The MIME type must start with `application/`; the payload format is app-defined; `MediaExtractor` reads it back. | Heartbeat data can travel inside the `.mp4` (and inside the `.m4a`, which is also an MP4 container). |
| WhatsApp re-encodes videos sent as media and strips metadata. Files sent as a **Document** are delivered unchanged. | Two delivery paths must both work (see § RECEIVE PATHS). |
| Phone speakers reproduce little below ~100 Hz. The current lub (55 Hz) is barely audible on a phone speaker. | The shared audio needs a "speaker preset" heartbeat with harmonics (§ AMPLIFIED HEARTBEAT). |

## FEATURES

| ID | Feature |
|---|---|
| F9 (revised) | Share sheet with two choices: **Video** (recommended) and **Audio only**. |
| F13 | Heartnote video export: `.mp4` with a rendered beating-heart video track, a mixed audio track and an embedded heartbeat payload. |
| F14 | Import: open a received Heartnote file (`.mp4` / `.m4a`) in the app and replay it with the full experience. |

## PAYLOAD (`share/HeartnotePayload.kt`)

Embedded in every exported `.mp4` and `.m4a`.

- Metadata track MIME: `application/x-heartnote`.
- One sample at presentation time 0 µs.
- Content: UTF-8 JSON, gzip-compressed.

```json
{
  "format": "heartnote",
  "version": 1,
  "title": "Ma's birthday",
  "createdAt": "2026-11-18T14:20:00+05:30",
  "durationMs": 42000,
  "beatSource": "trace",
  "beatsMs": [812, 1640, 2455],
  "smiles": [{ "startMs": 12000, "endMs": 14500 }],
  "dotColumns": [0.42, 0.88, 0.31]
}
```

- `dotColumns`: precomputed dot-chart column values (0–1, 200 ms per column) using the `DotChartData` rule from DESIGN.md, so the recipient sees the same dot rows without receiving the raw trace.
- Data minimisation (MUST): the payload MUST NOT contain `bpTrace`, `pulseRate`, breathing data, or expression scores. Beat times, smiles and display columns are enough to reproduce the experience.

## AMPLIFIED HEARTBEAT (`audio/HeartbeatSynth.kt` change)

Add a `SPEAKER` preset and use it for every new mix (in-app playback and shared files use the same mixed audio):
- Lub: 55 Hz fundamental + 110 Hz (gain 0.5) + 165 Hz (gain 0.25) harmonics, decay τ 40 ms, plus an 8 ms soft transient band-limited around 800 Hz at gain 0.15 so the thump is audible on phone speakers.
- Dub: same structure at 70 / 140 / 210 Hz, overall gain 0.6 of the lub.
- Mix gain: heartbeat × 0.6 (was 0.35) with a soft limiter. The voice MUST stay clearly intelligible; tune the gain by ear on the phone speaker.
- Keep `voice.wav` after export from now on (reverses the TRD decision to delete it), so notes can be re-mixed and re-exported.
- Notes made before this change keep their existing mix.

## VIDEO EXPORT (`export/HeartnoteVideoExporter.kt`)

Output: `notes/<id>/heartnote.mp4`, generated on demand when the user picks Video, cached until the note is renamed.

### Frame content (720 × 1280, 30 fps)

The HoldScreen look from DESIGN.md, rendered offscreen:
- radial red gradient background (with the brightness flash on each lub);
- `ParticleHeart` centred, beating on `beatsMs` (LUB + DUB);
- note title at the top;
- beat counter (`DotMatrixNumber`) with caption `beats`;
- footer for the whole video: `Open in Heartnote to feel this heartbeat` + small Heartnote wordmark;
- last 2 s: end card with the same footer line, larger.

### Rendering requirements

- `ParticleEngine` MUST gain a deterministic entry point: `renderAt(timeMs: Long, beatsMs: LongArray, drawScope)`. Ripples and scale are computed from beat times relative to `timeMs`, not from `BeatBus` events or the wall clock. Same seed → same frames. Live screens keep using `BeatBus`.
- No blur `RenderEffect` offscreen. The glow is drawn as soft radial-gradient discs.

### Encoding approach

Verify these APIs in the actual library sources/jars before coding, as was done for the SmartSpectra AAR.

- **Option A (preferred): Media3 Transformer.**
  1. Background: an image item with the frame duration set to `durationMs` at 30 fps.
  2. Overlay: `androidx.media3.effect.CanvasOverlay` drawing each frame via `renderAt(presentationTimeUs / 1000)`.
  3. Audio: a second sequence with `heartnote.m4a`.
  4. H.264 video, AAC audio.
- **Option B (fallback): MediaCodec + MediaMuxer directly.**
  1. H.264 encoder (`video/avc`, surface input, 2 Mbps, 1 s I-frame interval, no B-frames).
  2. Each frame drawn with `Surface.lockHardwareCanvas()`.
  3. Presentation times rewritten to `frameIndex × 33,333 µs` when draining the encoder.
  4. AAC samples copied from `heartnote.m4a` via `MediaExtractor`.
- **In both options, finish with a remux pass:** `MediaExtractor` → `MediaMuxer`, copying the video and audio tracks and adding the `application/x-heartnote` payload track. Transformer cannot write custom metadata tracks.
- Target size: ≤ 16 MB for a 60 s note (2 Mbps video + 128 kbps audio).
- Export runs on `Dispatchers.Default` with a progress indicator. The user can cancel. On failure: show `Couldn't make the video` + Retry, and keep Audio only available.

### Audio-only export

The existing `.m4a` export gets the same payload track added by the remux pass. An audio file sent as a document therefore also imports with the full experience.

## SHARE SHEET (F9 revised)

Bottom sheet opened from the PlaybackScreen share/download button. Styled per DESIGN.md: white sheet, 28 dp radius, Inter.

| Option | Text | Action |
|---|---|---|
| Video (default, first) | `Video` / `Plays anywhere. Opens in Heartnote with the heartbeat you can feel.` | Export `.mp4` → `ACTION_SEND`, type `video/mp4` |
| Audio only | `Audio only` / `Voice and heartbeat sound.` | Export `.m4a` → `ACTION_SEND`, type `audio/mp4` |

- Tip line under the options (12 sp `InkGrey`): `On WhatsApp, send it as a Document so it keeps the full heartbeat.`
- Disclosure line (MUST): `Shared files include your heartbeat timing so Heartnote can replay it.`

## RECEIVE PATHS

| Path | What arrives | How Heartnote gets the beats |
|---|---|---|
| Sent as Document (WhatsApp), Telegram file, Drive, Gmail, Nearby Share, Bluetooth | Original file, payload intact | Read the payload track (exact beats, smiles, dot columns) |
| Sent as WhatsApp media (re-encoded) | Video + re-encoded audio; payload stripped | **Beat recovery from the audio** (§ BEAT RECOVERY) |

## BEAT RECOVERY (`share/BeatRecovery.kt`)

Used only when no payload track is found. The heartbeat sound mixed into the audio is a known signal, so it can be found again after compression.

1. Decode the audio track (`MediaExtractor` + `MediaCodec`) to mono PCM, then downsample to 8 kHz.
2. Band-pass 40–220 Hz.
3. Matched filter: normalised cross-correlation with the SPEAKER-preset lub template band-passed the same way. Older notes used a 55 Hz-only lub, which still correlates in this band.
4. Peak picking: minimum spacing 333 ms; adaptive threshold = rolling median + 4 × MAD over 4 s windows.
5. Keep a candidate only if a dub-shaped peak follows at `clamp(0.3 × RR, 200, 350) ms` ± 40 ms. This rejects voice energy.
6. Keep intervals within 333–1500 ms (40–180 BPM).
7. Result: `beatsMs`, `beatSource = "recovered"`, `smiles = []`, `dotColumns` = 1.0 for columns containing a beat, 0.25 otherwise.
8. If fewer than 10 beats are recovered or the implied BPM is outside 40–180: refuse the import with `This file doesn't contain a heartbeat Heartnote can read.`

Validation (MUST, on device): export a note, send it to yourself via WhatsApp **as media**, re-import it, and compare recovered vs original beats. Target: ≥ 90% of original beats matched within ±60 ms. Record the result in `docs/TRD.md`.

## IMPORT (F14)

### Entry points

1. LibraryScreen: `Import` action (icon in the ArcNavBar outer-right slot, replacing About; About moves to the privacy sheet). Opens the system picker with `ACTION_OPEN_DOCUMENT` for `video/mp4`, `audio/mp4`, `audio/*`, `application/octet-stream`. No storage permission is needed.
2. Android share target: `ACTION_SEND` intent filter for `video/*`, `audio/*`, `application/octet-stream`. Heartnote then appears when the user taps Share on a WhatsApp video.
3. "Open with": `ACTION_VIEW` intent filter for `video/mp4` and `audio/mp4` (content URIs).

### Import pipeline (`share/ImportManager.kt`)

1. Copy the incoming URI to `notes/<id>/received.<ext>`. Show a progress indicator.
2. Look for an `application/x-heartnote` track → parse the payload, or else run § BEAT RECOVERY.
3. Write `signals.json` (from the payload or recovered beats) and `meta.json` with `origin = "received"`, title = payload title, or `Received Heartnote` if there is no payload.
4. Playback source: the audio track of the received file. ExoPlayer plays `.mp4`/`.m4a` directly; the video track is ignored in the app, because Heartnote shows its own screens.
5. Open PlaybackScreen for the new note.

### Received notes in the UI

- LibraryScreen has two groups: `Yours` and `Received`. A received note's card subtitle reads `Received • <date>`.
- PlaybackScreen and HoldScreen behave identically for received notes: sound, vibration, dot rows, smile markers and the particle heart.
- Subtitle variants:
  - `Timed to their measured pulse` when `beatSource` is `trace`;
  - `Recovered from the shared file` when `beatSource` is `recovered`.
- Received notes can be renamed and deleted. They can be re-shared only as the original received file (no re-mixing; there is no voice-only track).

## CONSENT AND DATA HANDLING ADDITIONS

Add to the DEV post section and the privacy sheet:
- Shared files contain the sender's voice, the heartbeat sound, the beat timing, and display values. They contain no raw camera-derived signals (no trace, no pulse-rate samples, no breathing, no expressions).
- Import is always user-initiated. Received notes are stored only on the recipient's phone and can be deleted at any time.
- Heartnote still has no servers and no accounts, and sends nothing over the network.

## CONSTRAINTS

- MUST NOT add accounts, login, servers, or cloud storage.
- MUST NOT put raw signals in the payload (see § PAYLOAD).
- MUST NOT auto-import: import happens only after an explicit user action (picker, share target, or open-with).
- The video MUST NOT show pulse numbers (PRD C1).
- The video MUST NOT reproduce the reference images' medical wording (DESIGN.md).

## VALIDATION

```text
[ ] Video plays in Google Photos and in a WhatsApp chat (sent as media) with voice + audible heartbeat on the phone speaker.
[ ] Video sent as WhatsApp Document → imported in Heartnote → beatSource "trace", vibration matches the sender's playback.
[ ] Video sent as WhatsApp media → imported → beatSource "recovered", ≥ 90% beats within ±60 ms (result written to TRD.md).
[ ] Audio only (.m4a) sent as Document → imports with payload.
[ ] Random non-Heartnote video → import refused with the readable-heartbeat message.
[ ] 60 s note video ≤ 16 MB; export finishes and can be cancelled.
[ ] Payload JSON contains only the fields in § PAYLOAD.
[ ] Share sheet shows the disclosure line.
```

## DEPENDENCIES

- `ParticleEngine` / `ParticleHeart` (DESIGN.md) — needs the new `renderAt` deterministic entry point.
- `HeartbeatSynth`, `Mixer`, `AacExporter` (TRD.md).
- Media3 Transformer + Media3 Effect (Option A) — same Media3 version as the existing ExoPlayer dependency. Kotlin 2.2 compatibility to be checked (Media3 is Java, low risk).

## RELATED DOCUMENTS

- `docs/PRD.md` — update F9, add F13, F14, add constraint C7 "No accounts or servers".
- `docs/TRD.md` — update the export section, voice.wav retention, the payload format, and the beat-recovery result.
- `docs/PROCESS_FLOW.md` — add the Share flow (two options) and an Import flow.
- `docs/design/DESIGN.md` — add the share sheet, video frame layout, Received group, and Import nav slot.
- `docs/ONBOARDING.md` — first-launch tour (separate change).
