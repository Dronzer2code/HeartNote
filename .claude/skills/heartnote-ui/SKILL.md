---
name: heartnote-ui
description: Use for ANY Heartnote UI work — building, restyling, animating or fixing a Compose screen or component (Library, Record, Playback, Hold, Consent, particle sphere, particle heart, dot-matrix digits, dot-bar charts, beat-synced animation).
---

# Heartnote UI skill

## Source of truth

1. `docs/design/DESIGN.md` — tokens, components, every screen's layout and text, particle sphere maths, beat sync, particle heart. Follow it exactly.
2. `docs/design/ref-screens.webp` and `docs/design/ref-heart.png` — the visual target. Open them (Read tool) before working on the matching screen.
3. `docs/TRD.md` — SDK fields, latencies, beat extraction, haptics, ExoPlayer sync.

If DESIGN.md and a reference image disagree, DESIGN.md wins (it removes the references' medical wording on purpose).

## Procedure for every UI task

1. Read the DESIGN.md section for the screen/component, then view the reference image.
2. Build or reuse components from `ui/components/` — never restyle inline. Tokens only from `ui/theme/HeartnoteTokens.kt`; no hard-coded colours, sizes or strings in screens.
3. Write a `@Preview` with realistic fake data (note with ~85 beats, 42 s, two smile moments).
4. Check the preview with the `android-cli` skill (`android preview`), then on the device: `adb exec-out screencap -p > shot.png`. View the screenshot and compare it with the reference: order, proportions, colours, radii, dot sizes, nav arc. Fix mismatches before continuing. For HoldScreen, compare everything except the heart: `ref-heart.png` shows a realistic heart, but Heartnote's heart is `ParticleHeart` by design.
5. For animation: verify on the physical device (the emulator does not represent camera, haptics or frame rate). Turn on the debug FPS overlay; `ParticleSphere` and `ParticleHeart` MUST stay ≥ 50 fps. Both use the shared `ParticleEngine`; never duplicate particle code.
6. Run the forbidden-wording check from DESIGN.md § VALIDATION.
7. Commit with a message naming the screen/component, then update `docs/PROGRESS.md`.

## Compose rules for this project

- Drawing: `Canvas` / `drawWithCache`; particles via preallocated `FloatArray` + `drawPoints`; frame loop with `withFrameNanos` inside `LaunchedEffect`. No allocation inside the draw lambda.
- State: screens observe `StateFlow` from ViewModels with `collectAsStateWithLifecycle()`. Beat events come only from `BeatBus` (`SharedFlow<BeatEvent>`).
- Glow: `graphicsLayer { renderEffect = BlurEffect(...) }` only when `Build.VERSION.SDK_INT >= 31`.
- Insets: `Modifier.safeDrawingPadding()` on all screens except HoldScreen (immersive via `WindowInsetsControllerCompat`).
- Accessibility: every icon has a `contentDescription`; touch targets ≥ 48 dp; respect `ANIMATOR_DURATION_SCALE == 0` (reduced motion).

## Never

- Copy medical text from the references (ECG, Sinus Rhythm, AFib, Patient, Stressful, R-R, Signal Quality, health claims).
- Bundle the reference images or the reference heart render into the app.
- Show pulse numbers outside RecordScreen.
- Vibrate or play heartbeat sound while recording.
- Add a 3D engine or 3D asset (SceneView, Filament, `.glb`). The Hold screen heart is `ParticleHeart` only.
