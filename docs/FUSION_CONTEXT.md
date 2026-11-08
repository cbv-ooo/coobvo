# Fusion Context

Last updated: 2026-11-08. This file is the project source of truth for architecture, scope, constraints and backlog. Record implementation progress in docs/FUSION_PROGRESS.md.

## Product

A Java 17 Xposed/LSPosed module based on this repository's Dynamic Island code. It renders one compact pill, two independent companion bubbles and expandable cards inside Samsung System UI on a Galaxy S10e running the owner's One UI 7 / Android 15 build.

For the S10e's right-side punch-hole, the compact pill belongs to the right of the camera cutout and both companion bubbles belong to its left. Bubble index 0 is nearest the pill; index 1 is farther out.

The fusion uses the original Dynamic Island event, hook, window and Canvas architecture. Other projects are references for behaviour and features only; do not import their separate overlay, service, dependency-injection or UI architecture.

## Working contract

- Work directly on main. Do not create branches or routine pull requests.
- Make focused commits that leave the project compilable. Increment versionCode before each new debug APK build and retain the established debug package identity and signing key.
- The only CI workflow is .github/workflows/debug-apk.yml. It runs ./gradlew --no-daemon assembleDebug and uploads the DynamicIsland-debug-apk artifact. No tests, lint, release builds, translation checks or extra workflows are part of CI.
- Read the latest Debug APK result after changes. A green result proves compilation, packaging and artifact upload only.
- Never claim S10e appearance, cutout alignment, touch behaviour or animation quality was verified without a real-device test.
- Do not spend turns repeatedly auditing completed files. Pick a backlog item, edit the small set of files it touches, commit, and use CI's first compiler error to guide corrections.
- Ask the owner only for real-device results, the S10e SystemUI.apk where hook names need verification, or signing material needed to replace the current debug identity.

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

The timer/stopwatch notification parser is now implemented in Java and connected to the existing OngoingActivity card builder; native Samsung Chronometer data remains preferred. Remaining candidates to compare/port are navigation parsing, richer live-activity parsing, notification cooldown/history, quick-launch shortcuts, idle widgets, network speed and diagnostics. Check whether a feature already exists before adding another source.

### LyricSync

The supplied LyricSync-master archive contains the Java model, provider cascade, matching utilities, cache and renderer logic. Its intended provider order is:

1. SpicyLyrics (requires a Spotify track ID and an optional user-supplied client key).
2. Kugou (KRC word timing, with LRC fallback).
3. Netease (YRC word timing, with LRC fallback).
4. LRCLIB (synced LRC, then plain/static text).

The model ranks SYLLABLE above LINE above STATIC. Keep real timed-word data distinct from approximate or line-only data. Preserve track matching, aliases, version rejection, interludes, short timeouts, deduplicated background requests and caching where practical. Do not port LyricSync's AI/chat, translation, transliteration, floating-window UI or separate media-notification service unless separately requested.

Lyrics lookup sends track metadata to external providers. It must be off by default, clearly state the privacy cost in settings, run off the UI thread, and proceed only when the hosting System UI process has network permission. Provider failures must never affect media playback or System UI stability.

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

The older dev.android.dynamicisland installation had a certificate mismatch with CI-signed updates. Debug builds now use the stable package ID dev.android.dynamicisland.coobvo, a stable debug-only keystore, and monotonically increasing version codes. This intentionally installs as a separate module beside the old package; it does not replace the old package. The owner must enable the new coobvo module for the com.android.systemui LSPosed scope and disable the old module. Future builds must reuse the same debug key and increment the version code.

Current accepted build at last update: version code 126 / version name 0.1.24-dev1. Debug APK run #46 passed compilation, packaging and upload; artifact: https://github.com/cbv-ooo/coobvo/actions/runs/42438935285/artifacts/13600931647.

## Backlog

1. Finish one shared Material 3 Expressive token source for corner radii, spatial/effects motion and semantic colours. Apply the same primitives to compact glyphs, bubbles, buttons and expanded cards. Keep the pill opaque black and the card fills opaque.
2. Finish Lottie coverage for useful compact and expanded icon state changes. Preserve static fallback, stillIcons and low-performance behaviour.
3. Port remaining Smart Island capabilities one at a time in Java: navigation, richer live activities, notification cooldown/history, quick-launch shortcuts, idle widgets, network speed and diagnostics. Timer/stopwatch classification has an initial parser connected to existing activity cards; improve only from concrete missing cases.
4. Complete LyricSync parity: robust TrackQuery/matching, SpicyLyrics when configured, Kugou KRC, Netease YRC, LRCLIB LRC/static, interludes and word-level highlighting only where real word timing exists. Keep privacy opt-in.
5. Prepare the owner's short S10e check: install/enable the new package in LSPosed; verify cutout awareness, pill/bubble positions, equal bubble spacing, independent tap/swipe, expanded notification cards, iMode motion, media controls and lyric timing.
6. Only after device results, fix any specific Samsung/ROM hook or geometry issue. Do not invent hook names.

## Device test target

Galaxy S10e (SM-G970F), right-side punch-hole, One UI 7 / Android 15 custom ROM. Exact System UI hook names must come from the owner's matching SystemUI.apk.
