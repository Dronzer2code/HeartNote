# PROCESS_FLOW.md — Heartnote

## PURPOSE

Defines how Heartnote works at runtime: the user journey, the recording pipeline, the playback pipeline, and the data lifecycle.
Feature IDs (F*) and constraints (C*) are defined in `PRD.md`. Component names match `TRD.md` § PROJECT STRUCTURE.

## USER JOURNEY

```mermaid
flowchart TD
    A[App launch] --> B{Consent accepted before?}
    B -- No --> C[ConsentScreen F1]
    C -- Decline --> X[Explain app cannot record; stay on ConsentScreen]
    C -- Accept --> D[Request CAMERA + RECORD_AUDIO]
    B -- Yes --> W{Tour completed or skipped?}
    D -- Granted or denied --> W
    W -- No --> WC[WelcomeChoice F15]
    WC -- Take the tour --> T[Tour: 3 pages, generated data only]
    WC -- Skip --> E[LibraryScreen F10]
    T -- Start or Skip --> E
    W -- Yes --> E
    D -- Denied --> Y[Show settings hint; recording disabled]
    E -- New note --> F[RecordScreen]
    F --> G[Self-recording confirmation F2]
    G --> H[Pulse lock-on F3]
    H --> I[Recording with live signals F4 F5]
    I -- Stop or 60 s --> J[Processing + save F6]
    J --> K[PlaybackScreen F7 F8]
    K -- Share --> L[Android share sheet F9]
    K -- Delete --> M[Confirm → delete note dir] --> E
    E -- Tap note --> K
```

## RECORD SCREEN STATE MACHINE

```mermaid
stateDiagram-v2
    [*] --> Confirm
    Confirm --> Preview: user confirms "I am recording myself"
    Preview --> LockOn: user taps Start AND a face emission within the last 2000 ms
    LockOn --> Recording: first pulseRate sample received after LockOn entry
    LockOn --> Preview: 30 s timeout (show "Move to better light")
    Recording --> Flushing: Stop tapped OR 60 s reached
    Recording --> Flushing: face lost > 8 s, SDK error, or app backgrounded, with ≥ 10 s recorded (note kept)
    Recording --> Preview: same, with < 10 s recorded (discard, show reason)
    Flushing --> Processing: 2.5 s SDK tail flush elapsed
    Processing --> [*]: note saved → PlaybackScreen
    Processing --> Preview: processing error (show error)
```

Rules:
- Audio capture starts on entering `Recording`, not during `LockOn` (lock-on silence is not part of the note).
- Audio capture stops IMMEDIATELY on entering `Flushing` (this fixes `durationMs`). The SDK keeps running for 2.5 s to flush delayed samples (measured medians: bpTrace 1.5 s, pulseRate 2.0 s behind wall clock), then `sdk.stop()`. UI during `Flushing`: "Finishing your heartbeat…".
- Metrics emissions are split by type (face, cardio, breathing arrive as separate messages), so "face present" = a face-bearing emission within the last 2000 ms (was 1000 ms; widened 2026-11-19 because a slow camera spaces face emissions out). While recording with no face, the status line says "Keep your face in view of the camera."
- The screen is kept on for the whole Record screen (`view.keepScreenOn`); otherwise the screen timeout backgrounds the app mid-note.
- The SDK runs from `Preview` through `Flushing`. `sdk.stop()` is called on leaving `Flushing` or when the screen stops.
- `sdk.processingStatus == ERROR` from any state → show `sdk.error`, return to `Preview`.

## RECORDING PIPELINE

```mermaid
sequenceDiagram
    participant UI as RecordScreen
    participant RS as RecordingSession
    participant SC as SignalCapture (SDK)
    participant AC as AudioCapture
    participant SA as SignalAligner
    UI->>RS: start()
    RS->>SC: sdk.start() (already running from Preview)
    RS->>AC: start() → audioStartNs
    loop every SDK emission
        SC-->>RS: metrics + arrivalNs
        RS->>SA: raw samples (pulse rate, trace, breathing, expressions)
        RS-->>UI: latest BPM, breathing rate, waveform points (F5)
    end
    loop every audio buffer
        AC->>AC: append PCM to voice.wav
    end
    UI->>RS: stop()
    RS->>AC: stop() immediately (fixes durationMs)
    Note over RS,SC: Flushing — SDK runs 2.5 s more to flush delayed samples
    RS->>SC: sdk.stop()
    RS->>SA: align all samples to tMs (tMs = timestamp_us/1000 − audioStartEpochMs)
```

## PROCESSING PIPELINE (F6)

Runs on `Dispatchers.Default` after `Recording` ends. Order is fixed:

1. `SignalAligner` → aligned samples in `tMs`.
2. `BeatExtractor` → `beatsMs`, `beatSource`.
3. `SmileDetector` → `smiles` (F8; skipped if cut).
4. Write `signals.json`, `meta.json`.
5. `HeartbeatSynth` → heartbeat PCM for `durationMs`.
6. `Mixer` → mixed PCM (voice + heartbeat).
7. `AacExporter` → `heartnote.m4a`.
8. ~~Delete `voice.wav`~~ — since 2026-11-18 `voice.wav` is kept so notes can be re-mixed (SHARE_AND_IMPORT.md).
9. Navigate to `PlaybackScreen(id)`.

Failure at any step: keep `voice.wav` and `signals.json` if written, show "Could not finish this note" with Retry (re-runs from step 5) and Delete.

## PLAYBACK PIPELINE (F7)

```mermaid
sequenceDiagram
    participant UI as PlaybackScreen
    participant P as ExoPlayer
    participant H as HeartbeatHaptics
    UI->>P: prepare(heartnote.m4a), play()
    loop every 10 ms while playing
        UI->>P: currentPosition
        UI->>H: fire beats with tMs in (lastPos, currentPos]
        UI->>UI: glow alpha = breathing value at currentPos
    end
    UI->>P: seekTo(x)
    UI->>UI: reset beat cursor to first beat ≥ x
```

- Heartbeat sound is already inside `heartnote.m4a`; haptics are the only runtime-synchronized output.
- Smile markers (F8) are drawn on the seek bar from `smiles`.
- No pulse numbers are displayed during playback (C1).

## SHARE FLOW (F9, original — superseded by the 2026-11-18 share flow below)

1. User taps Share on `PlaybackScreen`.
2. `ShareHelper` creates a content URI for `notes/<id>/heartnote.m4a` via `FileProvider`.
3. `Intent.ACTION_SEND`, `type = "audio/mp4"`, read-URI permission granted.
4. The recipient receives a standard audio file. Haptics and breath glow are only available inside Heartnote — since 2026-11-18 the file carries a payload, so a recipient who imports it into Heartnote gets them too (F14).

## SHARE FLOW (F9 revised, F13) — 2026-11-18

```mermaid
flowchart TD
    P[PlaybackScreen: Share / Download] --> R{Received note?}
    R -- Yes --> O[Share the original received file]
    R -- No --> S[Share sheet: Video / Audio only + disclosure line]
    S -- Video --> V{Cached video for this title?}
    V -- Yes --> SV[ACTION_SEND video/mp4]
    V -- No --> X[Render frames + encode H.264 + copy AAC + payload remux, cancellable]
    X -- Done --> SV
    X -- Failed --> F[Couldn't make the video + Retry; Audio only still available]
    S -- Audio only --> A[Remux heartnote.m4a + payload → ACTION_SEND audio/mp4]
```

## IMPORT FLOW (F14)

```mermaid
flowchart TD
    I1[Library: Import picker] --> Q
    I2[Share → Heartnote / Open with] --> RQ[ImportRequests: wait for the Library, after consent] --> Q
    Q[Copy to notes/id/received.ext] --> H{Audio track?}
    H -- No --> N[Refuse: no readable heartbeat]
    H -- Yes --> PL{Payload track?}
    PL -- Yes --> W[signals.json from payload + meta origin=received]
    PL -- No --> BR[BeatRecovery from the audio]
    BR -- ≥ 10 beats, 40–180 BPM --> W2[signals.json beatSource=recovered + meta]
    BR -- otherwise --> N
    W --> PB[PlaybackScreen: sound + vibration + dot rows + heart]
    W2 --> PB
```

## DATA LIFECYCLE

| Data | Created | Location | Leaves device? | Removed |
|---|---|---|---|---|
| Camera frames | During Preview/LockOn/Recording | SDK memory only | SDK behaviour UNKNOWN (see `TRD.md` § CONSENT AND DATA HANDLING) | Never written to disk |
| `voice.wav` | Recording | `filesDir/notes/<id>/` | No | On Delete / uninstall (kept for re-mixing) |
| `share/*.m4a`, `share/*.mp4` | When the user shares | `filesDir/notes/<id>/share/` | Only via user-initiated Share (voice, heartbeat sound, beat timing; no raw signals) | Rebuilt on rename; on Delete / uninstall |
| `received.<ext>` | Import | `filesDir/notes/<id>/` | No (re-share only on user action) | On Delete / uninstall |
| `signals.json`, `meta.json` | Processing | `filesDir/notes/<id>/` | No | On Delete / uninstall |
| `heartnote.m4a` | Processing | `filesDir/notes/<id>/` | Only via user-initiated Share | On Delete / uninstall (shared copies are outside app control) |

## RELATED DOCUMENTS

- `PRD.md` — features and constraints referenced above.
- `TRD.md` — component internals, algorithms, unresolved spike questions.
- `MVP.md` — which flows are required before the deadline.
