# Fusion Context

Last updated: 2026-11-08. This file is the project source of truth for architecture, scope, constraints and backlog. Record implementation progress in docs/FUSION_PROGRESS.md.

## Product

The target is a Java 17 Xposed/LSPosed Dynamic Island module for Samsung System UI on a Galaxy S10e running the owner's One UI 7 / Android 15 build. The intended UI is one compact pill, two independent companion bubbles and expandable cards.

For the S10e's right-side punch-hole, the compact pill belongs to the right of the camera cutout and both companion bubbles belong to its left. Bubble index 0 is nearest the pill; index 1 is farther out.

The fusion uses the original Dynamic Island event, hook, window and Canvas architecture. Other projects are references for behaviour and features only; do not import their separate overlay, service, dependency-injection or UI architecture.

## Working contract

- Work directly on main. Do not create branches or routine pull requests.
- Make focused commits that leave the project compilable. Increment versionCode before each new debug APK build and retain the established debug package identity and signing key.
- The only CI workflow is .github/workflows/debug-apk.yml. It runs ./gradlew --no-daemon assembleDebug and uploads the DynamicIsland-debug-apk artifact. No tests, lint, release builds, translation checks or extra workflows are part of CI.
- Read the latest Debug APK result after changes. A green result proves compilation, packaging and artifact upload only.
- Never claim S10e appearance, cutout alignment, touch behaviour or animation quality was verified without a real-device test.
- Progress must separate source implementation, build evidence and owner-confirmed device behavior. Do not ask the owner to read raw Java/Kotlin or infer completion from a Settings label; report what is actually visible/usable and what remains unverified.
- Ask the owner only for real-device observations, the S10e SystemUI.apk where hook names need verification, or signing material needed to replace the current debug identity.

## Architecture

All island and Xposed runtime code is Java and runs inside the System UI process. The settings app runs in the same APK but is a separate app UI.

Data flow:

Samsung System UI hooks
  -> island/source/* event sources
  -> IslandSources / IslandController
  -> Scene / Director
  -> Springs / Shapes / Metrics
  -> PillPainter / StackPainter / IMode (Canvas)
  -> TouchController / IslandWindow

Keep the original Dynamic Island runtime: Xposed entry and isolated hook groups, source/event facade, controller, window/placement, item ledger, Director, Scene, analytic Springs, Canvas painters, touch controller, diagnostics and crash protection.

Important files:
- island/view/IslandView.java: main Canvas view, Choreographer frame loop and input dispatch. Never replace this file wholesale.
- island/view/Director.java: state transitions and card stepping. When iMode is active, stepCards() delegates to IMode.step(); do not step it a second time.
- island/view/IMode.java: optional single-activity compact/expanded presentation.
- island/view/TouchController.java, Footprint.java, Shapes.java: touch geometry and both companion bubbles.
- island/IslandPlacement.java, island/view/IslandWindow.java: cutout geometry and overlay window.
- island/source/*, IslandSources.java: isolated event sources.
- island/view/card/*: Canvas card bodies, including media and timer.
- settings/ModuleSettings.java, SettingsActivity.java, SettingsSearchCatalog.java: persisted settings and settings UI.
- xposed/hooks/*: System UI hooks. Verify OEM/private method names against the owner's target SystemUI.apk; skip unknown hooks rather than guessing.

## Reference archives

### Smart Island

The supplied SmartIsland-main archive is the Kotlin/Compose application documented by analysis.md, codebase_analysis_report.md, ROADMAP.md and 5W2H.md. It has a notification listener/repository, parser utilities, system-event monitors, idle widgets, shortcuts, history and many card modes.

Take its parsing rules and local feature behaviour; reimplement them as small Java classes using this repository's existing hooks and event sources. Do not port Compose, Hilt, DataStore, AccessibilityService overlays, Shizuku, its service/window stack or its separate settings UI.

The timer/stopwatch and navigation notification parsers are implemented in Java and connected to the existing OngoingActivity card builder; native Samsung Chronometer data remains preferred. These two parser integrations do not constitute completion of Smart Island. Remaining candidates to compare/port are richer live-activity parsing, notification cooldown/history, quick-launch shortcuts, idle widgets, network speed and diagnostics. The Smart Island Settings page must not present descriptive text as proof that a feature is implemented or verified.

### LyricSync, Prism media and Morphe

The supplied LyricSync-master archive contains the Java model, provider cascade, matching utilities, cache and renderer logic. The current coobvo implementation is a limited provider path (Kugou KRC, NetEase YRC and LRCLIB LRC/static), not full LyricSync parity.

The Prism Morphe patch's primary job is to expose richer Prism-owned media data to Dynamic Island. Standard title, artist, album, artwork, duration, playback state and transport actions continue to come from Prism's existing MediaSession. The additional bridge exposes Prism's Song extras and its live stream diagnostics (source/container/codec/decoder, bitrate, sample/bit depth/channels, output encoding/device/effects, loudness, video data and player/queue state) through a versioned local-only route. A Java consumer formats this data into the existing media card. Prism remains the owner of its metadata, stream and lyrics lookup.

The official Morphe v2.1.0 "Third-party lyrics" patch targets YouTube Music (`com.google.android.apps.youtube.music`), not Prism (`com.prism.music`); its compatibility/fingerprints cannot be reused. A separate Prism target is staged under `morphe/prism-media-bridge/`. The coobvo consumer suppresses the independent LyricSync lookup for Prism sessions. The staged patch is not yet a built `.mpp` bundle or a tested patched Prism APK; lyric-line/style export is a later bridge phase, not yet implemented. Full details and evidence: docs/PRISM_LYRICS_MORPHE_BRIDGE.md.

For other media apps, any remaining LyricSync lookup stays opt-in, off by default, with explicit notice that track metadata is sent to external providers. It runs off the UI thread and proceeds only when the hosting System UI process has INTERNET permission. Provider failures must never affect media playback or System UI stability.

## Hard constraints

1. Java 17 only under app/src/main. No Kotlin or Compose in this module.
2. No blur, frosted glass, backdrop capture, RenderEffect, BlurMaskFilter or translucent/glass surfaces. The pill remains opaque black; cards use opaque fills.
3. No new runtime dependencies in System UI/Xposed code. Use Android framework APIs and the existing legacy Xposed API.
4. Every hook is isolated through the existing safe reflection/hook wrappers and its own failure handling.
5. No allocations in the per-frame draw/step path. Reuse Paint, Path, RectF and other scratch objects. Touch-event allocations are not frame-loop allocations.
6. UI/controller state is main-thread only. Network, disk and parsing work that can block must run on an executor and post results back.
7. Never package the compile-only android.view.CompositionSamplingListener framework stub.
8. Do not hand-edit generated R, embedded resources or island/graphics/LottieData.java; regenerate embedded Lottie data with tools/lottie/embed.py.
9. New user-visible strings belong in English res/values/strings.xml; translations can fall back to English.
10. Preserve legacy preference keys needed to load existing settings. Do not restore removed blur/glass features.
11. Use existing Lottie support for short icon state changes, with a static fallback and respect for stillIcons and low-performance settings.
12. Keep device-specific claims unverified until the owner tests on the S10e.

## Installation identity

The debug package is dev.android.dynamicisland.coobvo, separate from the old dev.android.dynamicisland identity, using app/coobvo-debug.keystore and the stable alias coobvoDebug. The owner has deleted the previous debug installation and is now using coobvo. The older dual-install situation is the likely explanation for the earlier duplicate full island; do not keep assuming both module installs remain active. If two full islands appear with only coobvo installed/enabled, investigate the current System UI window lifecycle.

Current source version in the pending Prism media integration commit: version code 133 / version name 0.1.31-dev1. The latest passing build before that commit is run #52 for commit d09ef5625096e998fe9abc67a474fc6e2ee7663f (version 132 / 0.1.30-dev1); CI passed compilation, packaging and upload. Artifact: https://github.com/cbv-ooo/coobvo/actions/runs/42442152143/artifacts/13602555320. The Prism bridge changes still require their own CI run.

## Backlog

1. Make the dual-bubble layout fully visible and usable: verify simultaneous display, equal spacing, independent touch and animation. Owner has already confirmed S10e cutout alignment and that bubbles move to the left of a right-anchored pill; do not generalize this to touch/animation completion.
2. Complete the whole battery feature, not merely shared gauge geometry/tint, and expose/verify every intended battery presentation and interaction.
3. Finish one coherent Material 3 Expressive system across compact glyphs, both bubbles, buttons, and expanded cards; shared tokens currently form a foundation, not a completed system.
4. Port remaining Smart Island capabilities as working Java event/card/settings behavior. The two parser ports alone are not completion.
5. Package and validate the staged Prism media Morphe patch, then expand its bridge to expose Prism lyrics and resolved lyric-look preferences. The coobvo-side media fields/consumer are source-staged; no .mpp or patched Prism APK is confirmed yet. Do not send Prism tracks through LyricSync's independent providers.
6. Complete LyricSync parity for non-Prism media sessions only if still needed.

## Owner-confirmed device facts

- S10e camera cutout alignment works.
- With the pill anchored on the right, the companion bubbles move to the left.

These are the only new device confirmations recorded in this update. Do not infer additional touch, animation, spacing or feature completion.

## Device test target

Galaxy S10e (SM-G970F), right-side punch-hole, One UI 7 / Android 15 custom ROM. Exact System UI hook names must come from the owner's matching SystemUI.apk.
