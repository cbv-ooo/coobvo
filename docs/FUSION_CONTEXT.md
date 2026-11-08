# Fusion Context

Last updated: 2026-11-08. This file is the project source of truth for architecture, scope, constraints and backlog. Record implementation progress in docs/FUSION_PROGRESS.md.

## Product

The target is a Java 17 Xposed/LSPosed Dynamic Island module for Samsung System UI on a Galaxy S10e running the owner's One UI 7 / Android 15 build. The intended UI is one compact pill, two independent companion bubbles and expandable cards.

For the S10e's right-side punch-hole, the compact pill belongs to the right of the camera cutout and both companion bubbles belong to its left. Bubble index 0 is nearest the pill; index 1 is farther out.

The fusion uses the original Dynamic Island event, hook, window and Canvas architecture. Other projects are references for behaviour and features only; do not import their separate overlay, service, dependency-injection or UI architecture.

## Working contract

- Work directly on main. Do not create branches or routine pull requests.
- Make focused commits that leave the project compilable. Increment versionCode before each new debug APK build and retain the established debug package identity and signing key.
- CI has two workflows. .github/workflows/debug-apk.yml builds the app with Java 17, runs assembleDebug, executes the focused NotificationCooldownManagerTest JVM test, then uploads the APK. .github/workflows/prism-morphe.yml uses Java 21 to run :patches:buildAndroid, verifies that an .mpp exists and uploads it. Neither workflow verifies runtime behaviour on the owner's phone.
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

Timer/stopwatch and navigation parsers are implemented in Java and connected to the existing OngoingActivity card builder; native Samsung Chronometer data remains preferred. An opt-in per-app ordinary-notification cooldown is also implemented in NotificationCooldownManager and HeadsUpTakeover: third posting within 30 seconds is allowed, then later ordinary island cards are held for three minutes and only the latest still-active one is delivered after expiry. Targeted JVM tests pass in CI. OTP, calls, alarms, media, navigation, progress/transfer and recognized download/cloud/store notifications are excluded. Notification history, quick-launch shortcuts, idle widgets, richer live activities, system-event cooldown and other optional widgets remain candidates. Full mapping: docs/SMART_ISLAND_PORTS.md.

### LyricSync, Prism media and Morphe

The supplied LyricSync-master archive contains the Java model, provider cascade, matching utilities, cache and renderer logic. The current coobvo implementation is a limited provider path (Kugou KRC, NetEase YRC and LRCLIB LRC/static), not full LyricSync parity.

The Prism Morphe patch's primary job is to expose richer Prism-owned media data to Dynamic Island. Standard title, artist, album, artwork, duration, playback state and transport actions continue through Prism's existing MediaSession. The local versioned bridge additionally exposes Prism's Song extras, stream diagnostics and player/queue state. It now also snapshots Prism's native LyricsState: source/status, stored lyric lines and exact line/word/background-vocal timings, alignment/gap markers, plus resolved lyric-look preferences. A Java consumer formats and renders this in the existing Canvas card. Prism remains the sole owner of lyric retrieval; coobvo suppresses its separate LyricSync lookup for Prism sessions.

The official Morphe v2.1.0 "Third-party lyrics" patch targets YouTube Music (`com.google.android.apps.youtube.music`), not Prism (`com.prism.music`); its compatibility/fingerprints cannot be reused. A separate Prism patch now builds as an .mpp through the dedicated workflow; Morphe run #3 succeeded and produced artifact 13603559103. Its source hooks Prism 1.8.2.1's LyricsState setters and publishes exact stored line/word timing data through the local route. The coobvo consumer reads this without invoking LyricSync. The bundle has not yet been applied to the exact Prism APK, and actual translated/romanized lines plus full Canvas style mapping remain unfinished. Full details: docs/PRISM_LYRICS_MORPHE_BRIDGE.md.

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

Current main source version: version code 136 / version name 0.1.34-dev1. Debug APK run #62 passed compilation, the targeted NotificationCooldownManagerTest, packaging and artifact upload: https://github.com/cbv-ooo/coobvo/actions/runs/42447577459/artifacts/13604291827. The Morphe patch was separately built in run #3 and uploaded at https://github.com/cbv-ooo/coobvo/actions/runs/42446806609/artifacts/13603559103. The patch has not yet been applied to the exact Prism APK or device-tested. Debug APK run #63 is pending for the latest documentation commit.

## Backlog

1. Make the dual-bubble layout fully visible and usable: verify simultaneous display, equal spacing, independent touch and animation. Owner has already confirmed S10e cutout alignment and that bubbles move to the left of a right-anchored pill; do not generalize this to touch/animation completion.
2. Complete the whole battery feature, not merely shared gauge geometry/tint, and expose/verify every intended battery presentation and interaction.
3. Finish one coherent Material 3 Expressive system across compact glyphs, both bubbles, buttons, and expanded cards; shared tokens currently form a foundation, not a completed system.
4. Continue the remaining Smart Island Java ports: notification history, shortcuts, idle widgets, richer live activity parsing and system-event cooldown. Do not count descriptive Settings rows as implementations.
5. Apply the generated Prism Morphe .mpp to the exact Prism 1.8.2.1 APK, validate fingerprints and test with coobvo on the S10e. Finish translation/romanization line export and Canvas mapping of Prism lyric-look preferences. Do not send Prism sessions through LyricSync's independent providers.
6. Complete LyricSync parity for non-Prism media sessions only if still needed.

## Owner-confirmed device facts

- S10e camera cutout alignment works.
- With the pill anchored on the right, the companion bubbles move to the left.

These are the only new device confirmations recorded in this update. Do not infer additional touch, animation, spacing or feature completion.

## Device test target

Galaxy S10e (SM-G970F), right-side punch-hole, One UI 7 / Android 15 custom ROM. Exact System UI hook names must come from the owner's matching SystemUI.apk.
