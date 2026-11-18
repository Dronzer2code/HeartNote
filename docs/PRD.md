# PRD.md — Heartnote

## PURPOSE

Defines WHAT Heartnote is, WHY it is being built, and WHAT it must do.
Technical implementation is in `TRD.md`. Minimum build scope is in `MVP.md`. User and system flows are in `PROCESS_FLOW.md`.

## CONTEXT

- Heartnote is an entry for the **DEV Presage SmartSpectra SDK Challenge** (https://dev.to/challenges/presage-2026-11-04).
- Challenge theme: build a working app with the Presage SmartSpectra SDK that responds meaningfully to real-time human signals captured through a camera (pulse, breathing rate, HRV, expressions).
- Submission deadline: **2026-11-22, 11:59 PM PST** (2026-11-23, 1:29 PM IST).
- Entry type: **individual** (one entry per participant; team entries are not allowed). Author: Subarna Maity.
- Platform: native Android, Kotlin, Jetpack Compose. Developer's test device runs Android 15.

## PROBLEM

1. People already pay for keepsakes that preserve a loved one's heartbeat (hospice "heartbeat bears", heartbeat songs). These require a Doppler or digital stethoscope, trained staff, and often take 1–2 weeks to produce.
2. Existing "send your heartbeat" phone apps (e.g., Heartbits, Feel: Heartbeat, Bond Pulse) require covering the rear camera with a finger. The user cannot talk while measuring, so voice and heartbeat are never captured together. Users have publicly reported that one such app's heartbeat is not real.
3. Voice notes are the dominant personal-message format (WhatsApp: ~7 billion voice messages per day, 2022 figure) but carry no physical presence of the sender.

## PRODUCT DEFINITION

Heartnote records a voice message while the front camera reads the speaker's pulse and breathing through the SmartSpectra SDK. The saved note replays the speaker's voice with a heartbeat sound timed to their measured pulse, a phone vibration on every beat, and a visual glow that follows their breathing. Notes can be exported as a standard audio file that plays in any messaging app.

Core differentiator: heartbeat and voice are captured **simultaneously, hands-free, from the face**, so the rhythm the listener feels is the rhythm the speaker had while saying those exact words.

## TARGET USERS

| User | Need |
|---|---|
| Sender | Wants to send or leave a message that feels physically present. Examples: grandparents recording for grandchildren, people living away from family, long-distance partners, families creating a keepsake. |
| Recipient | Receives the exported file or plays the note on the sender's device. MAY not have Heartnote installed. |

## FEATURES

Feature IDs are referenced by `MVP.md`, `TRD.md`, and `PROCESS_FLOW.md`.

| ID | Feature | Description |
|---|---|---|
| F1 | Consent onboarding | First-launch screens explaining what the camera measures, what is stored, what is not stored, and that the app is not a medical device. User MUST accept before camera access is requested. Flow: Consent → WelcomeChoice (F15) → Tour or Library. Nothing pre-recorded is ever added to the library. |
| F2 | Self-recording gate | Before each recording, the user confirms they are recording themselves. Recording MUST NOT start unless a face is detected by the SDK. |
| F3 | Pulse lock-on | Before speech begins, a short still phase waits until the SDK returns cardio data. UI shows "Hold still — finding your pulse". |
| F4 | Record Heartnote | Records microphone audio and SmartSpectra signals (pulse, breathing, expressions) on one shared timeline. |
| F5 | Live signal display | During recording, shows live pulse rate (BPM), breathing rate, and a moving waveform. Required so judges can see the live signal in the demo video. |
| F6 | Save note | Stores audio + extracted beat timestamps + breathing curve + smile moments locally. Video frames are never stored. |
| F7 | Playback | Plays voice + synthesized heartbeat sound at the recorded beat times, fires a vibration on each beat, animates a glow following the breathing curve. |
| F8 | Smile moments | Marks timeline segments where the SDK's `HAPPY` expression confidence was high. Shown as markers on the playback timeline. |
| F9 | Export & share (revised 2026-11-18) | Share sheet with two choices: **Video** (recommended; F13) and **Audio only** (`.m4a`). Both files carry a heartbeat-timing payload so Heartnote can replay the full experience (`SHARE_AND_IMPORT.md`). |
| F10 | Library & delete | Lists saved notes. Delete removes all files belonging to a note. |
| F11 | Hold mode | Full-screen, screen-dimmed playback intended for holding the phone or placing it on a pillow. Post-MVP. |
| F12 | Video export | Superseded by F13. |
| F13 | Heartnote video | `.mp4` with a rendered beating particle heart, the mixed audio and an embedded heartbeat payload; plays anywhere (`SHARE_AND_IMPORT.md`). |
| F14 | Import | Open a received Heartnote `.mp4`/`.m4a` (picker, share target, "Open with") and replay it with sound, vibration and visuals. Beats come from the payload, or are recovered from the audio when a messenger stripped it. |
| F15 | Optional tour | After consent, a WelcomeChoice offers a skippable 3-page tour with generated data only; replayable from Privacy & about (`ONBOARDING.md`). |

## REQUIREMENTS

- R1: The app MUST use at least one live SmartSpectra signal (challenge rule). Heartnote uses pulse, breathing, and expressions.
- R2: The app MUST show the live signal on screen during recording (F5).
- R3: The app MUST work without login. No accounts.
- R4: The app MUST store all user data on-device only.
- R5: The exported file MUST play in standard Android apps and messaging apps without Heartnote installed.
- R6: The app MUST clearly label the heartbeat sound as synthesized at the speaker's measured beat times, not a stethoscope recording.
- R7: The public repository MUST contain setup instructions sufficient for a judge to build and run the app.

## CONSTRAINTS

- C1: MUST NOT make diagnosis, treatment, or medical decision-making claims (challenge rule). MUST NOT display health interpretations (e.g., "your heart rate is high"). Live BPM is shown only as a raw signal during recording, labeled "live signal — not a medical reading".
- C2: MUST NOT enable covert or non-consensual monitoring (challenge rule). Only the person in front of the camera, who has confirmed F2, may be recorded.
- C3: MUST NOT store camera frames or video.
- C4: MUST NOT commit the Presage API key to the public repository.
- C5: MUST NOT use HRV in any user-facing feature (avoids health-interpretation risk).
- C6: Recipient-side features MUST NOT require network access.
- C7: No accounts, login, servers or cloud storage. Sharing is file-based; import is always user-initiated.

## NON-GOALS

- Cloud sync, accounts, contacts, or in-app messaging.
- iOS or web versions.
- Recording other people, infants, or unconscious persons.
- Any health tracking, history charts, or trends.

## COMPETITIVE CONTEXT (CHALLENGE)

As of 2026-11-16, no entry under the DEV tag `#presagechallenge` (approx. 34 unique posts, titles reviewed) involves voice messages, keepsakes, or sharing a heartbeat with loved ones. Crowded themes in that challenge: breathing guides, horror games, generative art/gardens, hands-free communication, interview/presentation coaching. New entries can appear until the deadline.

## SUCCESS CRITERIA

Judging criteria (from the challenge page):
1. Use of underlying technology
2. Usability and user experience
3. Originality and creativity

Ties are broken by positive reactions on the DEV post.

Prize categories to enter: **Most Creative or Delightful Use** and **Best Thoughtful Real-World Use**.

## SUBMISSION REQUIREMENTS

DEV post with tag `#presagechallenge` using the challenge template sections:
- What I Built
- Demo (video MUST show live pulse/breathing values changing on screen and the playback vibrating)
- Code (public repo link)
- How It Responds to Human Signals
- Prize Categories
- Consent and Data Handling (REQUIRED; content defined in `TRD.md` § CONSENT AND DATA HANDLING)
- Agent Session (optional; MUST be set to public if included)

## RELATED DOCUMENTS

- `MVP.md` — which features from this PRD are built before the deadline.
- `TRD.md` — implementation of each feature.
- `PROCESS_FLOW.md` — user journey and data pipeline.
