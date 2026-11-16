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
    B -- Yes --> E[LibraryScreen F10]
    D -- Granted --> E
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
    Preview --> LockOn: user taps Start AND metrics.hasFace()
    LockOn --> Recording: metrics.hasCardio() AND pulse rate received
    LockOn --> Preview: 15 s timeout (show "Move to better light")
    Recording --> Processing: Stop tapped OR 60 s reached
    Recording --> Preview: face lost > 3 s (discard, show reason)
    Processing --> [*]: note saved → PlaybackScreen
    Processing --> Preview: processing error (show error)
```

Rules:
- Audio capture starts on entering `Recording`, not during `LockOn` (lock-on silence is not part of the note).
- The SDK runs from `Preview` through `Recording`. `sdk.stop()` is called on leaving `Recording` or when the screen stops.
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
    RS->>AC: stop()
    RS->>SC: sdk.stop()
    RS->>SA: align all samples to tMs
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
8. Delete `voice.wav` after step 7 succeeds.
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

## SHARE FLOW (F9)

1. User taps Share on `PlaybackScreen`.
2. `ShareHelper` creates a content URI for `notes/<id>/heartnote.m4a` via `FileProvider`.
3. `Intent.ACTION_SEND`, `type = "audio/mp4"`, read-URI permission granted.
4. The recipient receives a standard audio file. Haptics and breath glow are only available inside Heartnote.

## DATA LIFECYCLE

| Data | Created | Location | Leaves device? | Removed |
|---|---|---|---|---|
| Camera frames | During Preview/LockOn/Recording | SDK memory only | SDK behaviour UNKNOWN (see `TRD.md` § CONSENT AND DATA HANDLING) | Never written to disk |
| `voice.wav` | Recording | `filesDir/notes/<id>/` | No | After successful export, or on Delete |
| `signals.json`, `meta.json` | Processing | `filesDir/notes/<id>/` | No | On Delete / uninstall |
| `heartnote.m4a` | Processing | `filesDir/notes/<id>/` | Only via user-initiated Share | On Delete / uninstall (shared copies are outside app control) |

## RELATED DOCUMENTS

- `PRD.md` — features and constraints referenced above.
- `TRD.md` — component internals, algorithms, unresolved spike questions.
- `MVP.md` — which flows are required before the deadline.
