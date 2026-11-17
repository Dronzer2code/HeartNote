# DESIGN.md — Heartnote UI

## PURPOSE

Defines the exact visual design, components, animation, and beat-sync behaviour of every Heartnote screen.
An agent implementing or changing any UI MUST follow this file. Product rules are in `docs/PRD.md`; architecture in `docs/TRD.md`; flows in `docs/PROCESS_FLOW.md`.

## CONTEXT

- Heartnote: native Android app (Kotlin 2.2, Jetpack Compose, minSdk 28, targetSdk 36) that records a voice note while the Presage SmartSpectra SDK reads the speaker's pulse from the front camera, then replays the voice with a heartbeat sound + vibration timed to the measured beats.
- The developer supplied two reference images. They are the visual target:
  - `docs/design/ref-screens.webp` — three phone screens (left: light dashboard, middle: dark recording screen with red particle sphere, right: light result screen with dotted bar rows).
  - `docs/design/ref-heart.png` — dark-red screen with a realistic 3D heart and floating cards. Only its background, layout, floating card, and bottom nav are the target. Its realistic heart is NOT a target: Heartnote draws the heart with particles (§ HOLD SCREEN HEART).
- The reference images come from a health/ECG concept. Their **layout, spacing, colour, typography, and motion style MUST be reproduced**. Their **medical wording MUST NOT be reproduced** (challenge rule: no diagnosis or medical claims). Replacement text is defined per screen below.
- The reference images and the 3D heart render inside them are third-party work. They MUST NOT be bundled in the APK as assets. Credit the original designer in the README and the DEV post (designer name: UNKNOWN — developer must find and fill in).

## SCREEN MAP

| Reference | Heartnote screen | File | Feature IDs |
|---|---|---|---|
| `ref-screens.webp` left | LibraryScreen | `ui/library/LibraryScreen.kt` | F10 |
| `ref-screens.webp` middle | RecordScreen | `ui/record/RecordScreen.kt` | F2, F3, F4, F5 |
| `ref-screens.webp` right | PlaybackScreen | `ui/playback/PlaybackScreen.kt` | F7, F8, F9 |
| `ref-heart.png` | HoldScreen | `ui/hold/HoldScreen.kt` | F11 |
| none (derived style) | ConsentScreen | `ui/onboarding/ConsentScreen.kt` | F1 |

## DESIGN TOKENS

File: `ui/theme/HeartnoteTokens.kt`. Values were sampled from the reference images.

### Colour

| Token | Hex | Use |
|---|---|---|
| `LightBg` | `#F0F0F0` | Light screen background (Library, Playback, Consent) |
| `CardWhite` | `#FFFFFF` | Cards, white circle buttons on light screens |
| `CardTint` | `#F8F8F8` | Card inner areas, inactive pill segments |
| `InkBlack` | `#1C1C1C` | Primary text, black dot-matrix digits, black centre nav button |
| `InkGrey` | `#888888` | Grey first line of two-tone headings, secondary text |
| `InkGreyLight` | `#A0A0A0` | Axis labels (`0s`, `8s`), captions |
| `DotIdle` | `#DADADA` | Inactive dots in dot-bar charts |
| `DotRed` | `#D8121A` | Active dots on light screens |
| `SignalRed` | `#FF0606` | Progress bar, stop button, particles (dark screens) |
| `DarkBg` | `#000000` | RecordScreen background |
| `DarkSurface` | `#181818` | Circle buttons and pill on dark screens |
| `DarkTrack` | `#282828` | Progress track on dark screens |
| `OnDark` | `#F8F8F8` | Text and dot digits on dark screens |
| `HoldGlow` | `#F72102` | HoldScreen top radial glow centre |
| `HoldDeep` | `#300000` | HoldScreen gradient middle |
| `HeartRed` | `#E2021B` | ParticleHeart particles, HoldScreen accents |

### Typography

- Font family: **Inter** (SIL OFL, bundled in `res/font/`: Regular 400, Medium 500, SemiBold 600). It is the closest open match to the reference grotesk; exact reference font is UNKNOWN.
- Two-tone heading: 34 sp, line height 38 sp, letter spacing −0.6 sp, SemiBold. Line 1 in `InkGrey`, line 2 in `InkBlack`.
- Screen title (top bar centre): 15 sp Medium.
- Card title: 17 sp SemiBold. Card subtitle: 12 sp Regular `InkGreyLight`.
- Body / status text: 16 sp Medium (primary), 13 sp Regular (secondary).
- Numbers that represent signals MUST use `DotMatrixNumber` (never a font).

### Shape and spacing

- Screen horizontal padding: 20 dp. Card corner radius: 28 dp. Card padding: 18 dp.
- Circle icon buttons: 52 dp, icon 22 dp, outlined icons (Lucide-style, 1.75 dp stroke).
- Centre nav button: 64 dp, `InkBlack` fill, white icon.
- Pill shapes: fully rounded (`RoundedCornerShape(50)`).
- Elevation: none. Separation is by colour only (cards are white on `LightBg`).

## COMPONENTS

All in `ui/components/`. Each MUST have a `@Preview` with representative data.

| Component | Spec |
|---|---|
| `DotMatrixNumber(value: String, dotColor, digitHeight)` | Draws digits 0–9 and `-` on a 5×7 dot grid per glyph, glyph gap = 1 column. Dot diameter = 0.78 × cell. Only "on" dots are drawn (no ghost dots). Sizes: Library 56 dp tall, Record 72 dp tall (white), Playback 26 dp tall. Changing values cross-fade per dot over 120 ms. |
| `DotBarChart(values: FloatArray, progress: Float?, rows, highlightIndex)` | Grid of dots: `rows` rows × `values.size` columns, dot 4.5 dp, gap 2.5 dp. Every dot is drawn in `DotIdle`; in each column the bottom `round(value × rows)` dots are drawn in `DotRed`. Dots are clipped to a stadium shape (rounded rectangle, corner radius = height/2), matching the reference. If `progress` is set, columns after the progress point use `DotRed` at 35% alpha. `highlightIndex` column scales its dots to 1.25× for 160 ms (beat flash). |
| `TwoToneHeading(grey: String, black: String)` | Two lines per the typography spec. |
| `CircleIconButton(icon, onClick, dark: Boolean)` | 52 dp circle; light: `CardWhite` + `InkBlack` icon; dark: `DarkSurface` + `OnDark` icon. |
| `ArcNavBar(items, centre, label)` | Reproduces the reference bottom nav: five circles on a shallow upward arc. Outer two circles sit 18 dp lower than inner two; the centre 64 dp black button sits highest with a 13 sp label under it. |
| `SlideActionPill(state, onAction)` | Dark pill 64 dp tall, 180 dp wide, `DarkSurface`. Left: 52 dp `SignalRed` circle with white icon (mic = start, rounded square = stop). Centre: label (`Start` / `Stop`). Right: three chevrons `› › ›` in `InkGrey`, animated left-to-right shimmer every 1.6 s. Tap OR swipe right triggers `onAction`. |
| `ProgressLine(fraction, label)` | 4 dp track `DarkTrack`, fill `SignalRed`, 175 dp wide, label below in 15 sp `OnDark`. |
| `ParticleEngine` | Shared particle code used by both particle components. Defined in § PARTICLE ENGINE. |
| `ParticleSphere(beatBus, breath, interactive)` | Defined in § PARTICLE SPHERE. |
| `ParticleHeart(beatBus)` | Defined in § HOLD SCREEN HEART. |

## SCREENS

### LibraryScreen (reference: left screen)

Layout top to bottom:
1. Top row: left = 44 dp circle containing the app mark (red dot on white) + two text lines `Heartnote` (15 sp SemiBold) / `Your voice, your rhythm` (12 sp `InkGrey`). Right = `CircleIconButton(shield)` → privacy sheet.
2. 28 dp gap, `TwoToneHeading("Send your", "heartbeat")`.
3. Latest-note card (white, radius 28): title = note title, subtitle = `Today • Nov 17, 2026` format. Top-right `CircleIconButton(history)` → scrolls to the full list. Right-aligned `DotMatrixNumber` = beat count of the note, caption row: `Heartbeats` (left) and `beats` (right) in 13 sp `InkGrey`. Bottom: `DotBarChart` 9 rows × 36 columns of the note's beat envelope (see § DATA FOR DOT CHARTS).
4. Below: compact list of older notes (white rows, radius 20, title + date + duration).
5. Empty state (no notes): card shows `DotMatrixNumber("--")` and text `Record your first Heartnote`.
6. `ArcNavBar`: outer-left = Privacy (shield), inner-left = Hold latest (hand-heart icon), centre = Record (pulse icon, label `Record`), inner-right = Share latest, outer-right = About (info icon).

Reference text that MUST be replaced: `Alex Smith`, `Patient`, `Check Your / Heart's Health`, `ECG`, `Heart Rate`, `bpm`, `Dashboard`.

### RecordScreen (reference: middle screen)

Background `DarkBg`. Layout top to bottom:
1. Top bar: `CircleIconButton(back, dark)` left, title `Record Heartnote` centre (`OnDark`), `CircleIconButton(lightbulb, dark)` right → lighting tips sheet.
2. Subtitle 12 sp `InkGrey`: `Front camera • 30 fps`.
3. `ParticleSphere` in a 320 dp square, centred. Inside its dark core: `DotMatrixNumber` (72 dp, `OnDark`) = live pulse BPM, `bpm` 15 sp under it, and in 10 sp `InkGrey` under that: `live signal — not a medical reading`.
4. Status text (two lines, centred): primary 18 sp `OnDark`, secondary 14 sp `InkGrey`. Values per state in the table below.
5. `ProgressLine` + label.
6. `SlideActionPill`.
7. Breathing: a 1-line 24 dp tall breathing waveform from `brUpperTrace`, `InkGrey` 1.5 dp stroke, above the progress line. Breathing-rate number is NOT shown as a large value (signal is sparse).

| Record state (`PROCESS_FLOW.md`) | Primary | Secondary | Progress | Pill |
|---|---|---|---|---|
| Confirm | `Recording yourself?` | `Only the person on camera can be recorded.` (+ checkbox) | hidden | `Start` disabled until checked |
| Preview | `Look at the camera.` | `Centre your face in the circle.` | hidden | `Start` |
| LockOn | `Look at the camera.` | `Finding your pulse…` | lock-on % (0–100 over 20 s timeout, jumps to 100 at first pulse) | `Stop` (cancels) |
| Recording | `Speak now.` | `Recording • 0:23 / 1:00` | elapsed / 60 s | `Stop` |
| Flushing | `Hold on.` | `Finishing your heartbeat…` | full, pulsing | disabled |
| Processing | `Saving.` | `Making your Heartnote…` | indeterminate | disabled |

Camera preview: the SDK's `PreviewView` is rendered INSIDE the sphere core, clipped to a circle (diameter 0.62 × sphere), desaturated (ColorMatrix saturation 0) at 45% alpha during Confirm/Preview/LockOn so the user can centre their face. On entering Recording it fades to 0% alpha over 600 ms (the view stays attached so the SDK keeps receiving frames). Whether the SDK accepts a 0-alpha surface is UNKNOWN — verify on device; if capture stops, keep it at 12% alpha instead.

Reference text that MUST be replaced: `Record ECG`, `25 mm/sec • 10 mm/mV`, `Touch the watch.`, `Measuring...`.

### PlaybackScreen (reference: right screen)

Background `LightBg`.
1. Top bar: `CircleIconButton(back)` left, title = `Heartnote` centre, `CircleIconButton(download)` right → export/share (F9).
2. `TwoToneHeading("Heartnote for", <note title>)`. Tapping the heading opens rename. Top-right of heading: small `DotRed` heart icon shown only if the note has smile moments; tapping it jumps to the first smile.
3. Card (white): title `Heartbeat`, subtitle = `Timed to your measured pulse • 0:42` (or `Paced by your live pulse • 0:42` when `beatSource != "trace"`). Right: `DotMatrixNumber` (26 dp) = beat count, caption `beats`.
4. Inside the card: three `DotBarChart` rows (9 rows each), each covering 8 s of the note (`0s`/`8s`, `8s`/`16s`, `16s`/`24s` axis labels in 11 sp `InkGreyLight`). Notes longer than 24 s: rows continue (scrollable), each row = 8 s. `progress` = playback position. The column containing the current beat flashes (`highlightIndex`) on every beat.
5. Smile moments: a 6 dp `DotRed` heart marker above the dot row at each smile start.
6. Bluetooth hint (when output is Bluetooth): 12 sp `InkGrey` line `Feels best on the phone speaker`.
7. `ArcNavBar`: outer-left = Delete (trash), inner-left = Share, centre = Play/Pause (label = `0:12 / 0:42`), inner-right = Hold (hand-heart icon → HoldScreen), outer-right = Rename (pencil).

No BPM or other pulse number is displayed on this screen (PRD C1).

Reference text that MUST be replaced: `Sinus Rhythm`, `Recording state`, `Stressful`, `Normal ECG`, `Regular Sinus Rhythm • No AFib`, `bpm`.

### HoldScreen (reference: `ref-heart.png`)

Full-screen, immersive (system bars hidden), keep screen on.
1. Background: radial gradient centred at top (x 50%, y 0%): `HoldGlow` → `HoldDeep` at 45% → `DarkBg` at 100%. Gradient brightness increases 15% for 180 ms on each lub.
2. Top: `CircleIconButton(back, dark)` with 40% white overlay, title = note title (22 sp SemiBold `OnDark`), subtitle `Hold your phone close` (13 sp, 70% `OnDark`).
3. Centre: beating heart, 70% of screen width (see § HOLD SCREEN HEART).
4. Floating white card overlapping the heart's right side (as in reference): radius 20, 150×120 dp, title `Heartbeat` 12 sp, a 2-second scrolling mini trace of the recorded `bpTrace` in `DotRed`, and `DotMatrixNumber` beat counter (counts up as beats play) with caption `beats`.
5. Bottom: dark pill nav (`DarkSurface`) with 4 icons; the active one is a white circle with black icon (as in reference): Back, Play/Pause (active), Replay, Share.
6. Plays the note with sound + haptics, same sync as PlaybackScreen.

The reference's four info cards (`R-R Interval`, `Signal Quality`, `Status`, `R-R Variability`) and `ECG Results / Monitor heart health` MUST NOT be reproduced in any form.

### ConsentScreen (no reference; derived style)

`LightBg`, `TwoToneHeading("Your heartbeat", "stays yours.")`, then three white rows (radius 20) each with a `CircleIconButton`-style icon: (camera) `The camera reads your pulse from your face. No video is ever saved.`; (phone) `Notes stay on this phone. Nothing is uploaded by Heartnote.`; (trash) `Delete any note at any time.` Footer 12 sp `InkGrey`: `Heartnote is not a medical device.` Primary button: full-width black pill 56 dp `I understand`; text button `Not now`.

## PARTICLE ENGINE

File: `ui/components/particles/ParticleEngine.kt`. Built in the same phase as `ParticleSphere`, so `ParticleHeart` can reuse it without refactoring.

Shared responsibilities (one implementation, used by both components):
- Point storage: base positions in preallocated `FloatArray`s; per-frame displaced positions in a second preallocated buffer. No per-frame allocation.
- Coordinate system: X right, Y up, Z toward the viewer; unit scale, centred on the origin.
- Rotation state (yaw/pitch), drag handling, fling decay.
- Touch push (radial displacement near the finger, spring back).
- Beat ripples: a list of active ripples (start time, amplitude, kind) fed from `BeatBus`; each component supplies its own ripple-delay function.
- Perspective projection, depth-based size/alpha, 4 depth-bucket `drawPoints` calls, blur glow on API 31+.
- Frame loop (`withFrameNanos`), debug FPS overlay, reduced-motion handling.

Component-specific inputs: point set, colour, displacement function (noise/breath/ripple rules), idle motion, overlays.

## PARTICLE SPHERE

File: `ui/components/ParticleSphere.kt` (uses `ParticleEngine`). Visual target: the red dissolving particle ball in the middle reference screen.

- Particles: 2,400 points on a Fibonacci sphere (unit direction vectors).
- Radius per particle per frame:
  `r = R × (1 + 0.07·noise(dir, t) + 0.03·breath + beatImpulse(dir, t) + touchPush)`
  - `R` = 0.36 × component size.
  - `noise`: sum of three sinusoids of (dir.x, dir.y, dir.z, t) with frequencies 1.7, 2.9, 4.3 and speeds 0.35, 0.55, 0.8 rad/s → slow organic wobble that makes the silhouette look torn/ragged like the reference.
  - `breath`: latest normalised `brUpperTrace` value (−1..1), smoothed with 300 ms low-pass.
  - `beatImpulse`: each `BeatEvent` adds an expanding ripple. For particle angle θ from the camera-facing pole: `A × exp(−(t − t0 − θ/π × 0.25 s) / 0.12 s)` for t after the ripple reaches it, else 0. `A` = 0.13 for LUB, 0.065 for DUB.
- Rotation: 6°/s around Y idle. Drag rotates the sphere (1° per dp); fling continues with decay 0.92/frame.
- Touch interaction: particles whose projected position is within 56 dp of the finger are pushed radially outward (max 0.12 R), springing back over 350 ms after release.
- Projection: perspective, camera distance 3 R. Point size 1.2–2.8 px and alpha 0.2–1.0 by depth (front = larger, brighter).
- Colour: `SignalRed`. Draw in 4 depth buckets with one `drawPoints` call each (`PointMode.Points`, round cap).
- Glow: on API 31+, draw a second copy at 35% alpha with `Modifier.graphicsLayer { renderEffect = BlurEffect(14.dp, 14.dp) }` behind the sharp layer. API 28–30: no glow.
- Core: radial gradient from `DarkBg` (centre, alpha 0.9) to transparent at 0.75 R, drawn over particles, so the digits stay readable (reference has a dark centre).
- Frame loop: `withFrameNanos`. Target 60 fps on the test device; MUST NOT drop below 50 fps (verify with a debug FPS overlay).
- Reduced motion: if `Settings.Global.ANIMATOR_DURATION_SCALE == 0`, disable rotation and noise; keep beat pulses as a single uniform scale (1.0 → 1.04).

## BEAT SYNC

Single source of beat events: `ui/beat/BeatBus.kt` exposes `SharedFlow<BeatEvent>` where `BeatEvent(kind: LUB | DUB, strength: Float, noteTimeMs: Long?)`. All visuals (sphere, dot charts, heart, gradient) and haptics subscribe to it.

### Live (RecordScreen) — `ui/beat/LiveBeatScheduler.kt`

Measured SDK latency (see `TRD.md`): `bpTrace` arrives ~1.5 s after the beat, in ~1 s chunks of ~30 samples. True real-time beats are impossible, so the sphere shows the user's real rhythm with a fixed delay:
1. Keep a rolling 6 s buffer of `bpTrace` samples (epoch µs, value).
2. On each new chunk, run the same peak detection as `BeatExtractor` on the buffer; emit only peaks newer than the last emitted peak.
3. Schedule each new beat at `beatEpochMs + DISPLAY_DELAY_MS` (constant 1,800 ms) on the wall clock: LUB at that time, DUB at `+ clamp(0.3 × RR, 200, 350) ms`.
4. Before the first detected peak: no beat events; sphere shows breath + noise only.
5. BPM digits: 5-sample median of `pulseRate`; `--` until the first value.
6. NO haptics and NO heartbeat sound during recording (vibration and speaker output would corrupt the camera and mic signals).

### Playback (PlaybackScreen, HoldScreen)

Beats come from `signals.json` `beatsMs`, driven by `ExoPlayer.currentPosition` polling (10 ms), as in `TRD.md`. Each beat emits LUB then DUB on `BeatBus`; haptics subscribe to the same events.

## DATA FOR DOT CHARTS

`ui/playback/DotChartData.kt`:
- Column value = peak-to-peak amplitude of `bpTrace` within the column's time window, normalised to the note's 95th percentile, clamped 0.15–1.0. This gives the spiky beat pattern seen in the reference.
- Library card: 36 columns spanning the whole note.
- Playback rows: 40 columns per 8 s row (200 ms per column).
- `highlightIndex` = column containing the most recent beat.

## HOLD SCREEN HEART

DECISION (developer, 2026-11-17): no 3D model. The heart is `ParticleHeart` only. MUST NOT add SceneView, Filament, or any `.glb`/3D asset.

File: `ui/components/ParticleHeart.kt` (uses `ParticleEngine`). Visual target: a recognisable heart silhouette made of glowing red particles, in the same style as the RecordScreen sphere.

- Shape: implicit surface `F(x, y, z) = (x² + 9/4·y² + z² − 1)³ − x²·z³ − 9/80·y²·z³`, where in this equation z is vertical and y is depth.
  - Axis mapping into engine coordinates: engine X = x, engine Y (up) = z, engine Z (depth) = y.
- Point generation (once, on `Dispatchers.Default`, cached for the process lifetime): sample random points uniformly in the box x ∈ [−1.5, 1.5], y ∈ [−1, 1], z ∈ [−1.5, 1.5]; keep a point when `F(p) ≤ 0` AND `F(1.04·p) > 0` (inside the heart, within 4% of its surface). Stop at 2,600 points. Then recentre on the bounding-box centre and scale so the largest dimension = 2.0.
- Size: heart height = 0.70 × screen width (HoldScreen item 3).
- Colour `HeartRed`; depth size/alpha and glow from the engine.
- Beat animation:
  - LUB: whole-heart scale 1.00 → 1.07 (90 ms ease-out) → 1.00 (220 ms ease-in), plus a ripple with radial displacement amplitude 0.06 that starts at the centre and reaches the surface after 200 ms (delay = normalised distance from centre × 0.20 s), decay time constant 0.12 s.
  - DUB: scale 1.00 → 1.035 (70 ms) → 1.00 (180 ms), ripple amplitude 0.03.
- Idle motion: gentle sway of ±25° yaw (sine, 6 s period) instead of a full spin, so the heart is never seen edge-on and always reads as a heart. Noise wobble amplitude 0.03 (less than the sphere's 0.07, so the shape stays crisp). No breath input.
- Interaction: drag rotates (yaw clamped to ±60°, springs back to the sway after release); touch push as in the engine.
- No dark core overlay (that is RecordScreen-only).
- Reduced motion: no sway and no noise; beats are a uniform scale only.

## CONSTRAINTS

- MUST NOT copy any medical text from the references (lists per screen above).
- MUST NOT ship the reference images or the reference heart render in the APK.
- MUST NOT display pulse numbers outside RecordScreen.
- MUST NOT vibrate or play heartbeat sound while recording.
- MUST apply edge-to-edge insets on every screen (HoldScreen: immersive).
- All user-visible strings MUST be in `res/values/strings.xml`.
- Touch targets ≥ 48 dp; text contrast ≥ 4.5:1 for body text (the `InkGrey` heading line is decorative and exempt).
- Every icon button MUST have a `contentDescription`.

## VALIDATION

For each screen:
1. Render the Compose `@Preview` (or `android preview`) and a device screenshot (`adb exec-out screencap -p > screen.png`).
2. Compare side by side with the matching reference region: layout order, proportions, colours, corner radii, dot sizes, nav arc. Fix any visible mismatch before moving on. HoldScreen: compare background, layout, floating card and nav only; the heart is particles by design.
3. RecordScreen: sphere runs ≥ 50 fps; sphere pulses at a steady rhythm matching the displayed BPM (count pulses for 15 s × 4 ≈ BPM ± 6). HoldScreen: ParticleHeart runs ≥ 50 fps and reads as a heart at every point of its sway.
4. PlaybackScreen/HoldScreen: vibration, heartbeat sound, dot flash, and heart scale happen together (no visible offset).
5. Search the codebase for forbidden words: `ECG`, `Sinus`, `AFib`, `Patient`, `Stressful`, `Normal`, `R-R`, `diagnos`, `health` (user-visible strings only). Zero matches allowed.

## RELATED DOCUMENTS

- `docs/PRD.md` — features F1–F12, constraints C1–C6.
- `docs/TRD.md` — SDK fields, latencies, BeatExtractor, haptics, ExoPlayer sync.
- `docs/PROCESS_FLOW.md` — RecordScreen state machine used in the state table.
- `.claude/skills/heartnote-ui/SKILL.md` — working procedure for UI tasks.
