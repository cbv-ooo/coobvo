# Fusion Context

Last updated: 2026-11-08 (DI-first hierarchy). This file is the project source of truth for architecture, scope, constraints and backlog. Record implementation progress in docs/FUSION_PROGRESS.md.

## Product

The target is a Java 17 Xposed/LSPosed Dynamic Island module for Samsung System UI on a Galaxy S10e running the owner's One UI 7 / Android 15 build. The intended UI is one compact pill, two independent companion bubbles and expandable cards.

For the S10e's right-side punch-hole, the compact pill belongs to the right of the camera cutout and both companion bubbles belong to its left. Bubble index 0 is nearest the pill; index 1 is farther out.

The fusion uses the original Dynamic Island event, hook, window and Canvas architecture. Other projects are references for behaviour and features only; do not import their separate overlay, service, dependency-injection or UI architecture.

## Working contract

- Work directly on main. Do not create branches or routine pull requests.
- Make focused commits that leave the project compilable. Increment versionCode before each new debug APK build and retain the established debug package identity and signing key.
- CI has two workflows. .github/workflows/debug-apk.yml builds the app with Java 17, runs assembleDebug, executes NotificationCooldownManagerTest and NotificationHistoryStoreTest, then uploads the APK. .github/workflows/prism-morphe.yml uses Java 21 to run :patches:buildAndroid, verifies that an .mpp exists and uploads it. Neither workflow verifies runtime behaviour on the owner's phone.
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

**Hierarchy:** Dynamic Island is the foundation. If DI already handles something pretty well, do not re-port it from Smart Island. See docs/SMART_ISLAND_PORT_CONTEXT.md.

Timer/stopwatch and navigation parsers extend the existing OA path. Opt-in notification cooldown and opt-in notification history are SI gap fills (JVM tests in CI; history UI still needs S10e verification). Idle rotation (IdleSource) fills the empty always-visible pill only. Quick-launch (QuickLaunchSource) is opt-in card actions only — TouchController gestures are untouched.

Do **not** clone ProgressSource/RuleSource/EventSource for "richer transfers" or "system-event cooldown"; those DI sources already cover that territory pretty well. Full mapping: docs/SMART_ISLAND_PORTS.md.

### LyricSync, Prism media and Morphe

The supplied LyricSync-master archive contains the Java model, provider cascade, matching utilities, cache and renderer logic. The current coobvo implementation is a limited provider path (Kugou KRC, NetEase YRC and LRCLIB LRC/static), not full LyricSync parity.

The Prism Morphe patch exports Prism-owned media data for the expanded Dynamic Island card. Standard title, artist, album, artwork, duration, playback state and transport actions continue through Prism's MediaSession. The local versioned bridge also exposes Song extras, stream diagnostics, player/queue state and Prism's live LyricsState: source/status, original line/word/background-vocal timing, alignment/gaps, actual romanized/translated helper results, and resolved lyric-look/player preferences. For Prism 1.8.2.1, the patch retains a weak reference through the real lyrics/source/loading/waiting setters and refreshes live getters on System UI bridge queries. The old `PrismRememberLyricsStateFingerprint` for the `rememberLyricsState` composable has been removed. Coobvo's Java Canvas consumer renders the supported lyrics subset below the media controls. Prism remains the sole lyrics source for Prism sessions; the independent LyricSync provider cascade is suppressed.

The official Morphe v2.1.0 "Third-party lyrics" patch targets YouTube Music (`com.google.android.apps.youtube.music`), not Prism (`com.prism.music`); its fingerprints cannot be reused. The Prism bridge source is v0.1.3. Commit `b331a0598b45424bff47406ff03d5f5f7d6ce191` fixes premature `UNAVAILABLE` classification for an empty but still loading/waiting Lyrics object. The extension now exports Prism's 100-point `SongWaves` scan when available, and the app carries it through to the island's WAVE progress renderer. The player mapping now consumes title size/weight/alignment, time/remaining-time mode, cover corners (FULL cover only removes rounding), and scrubber style/color/thickness/thumb. Provider query errors/no rows now appear as `BRIDGE_UNAVAILABLE` and are retried instead of being confused with a genuine no-lyrics state. The island still does not reproduce Prism's complete Compose player page; RIBBONS animation, full spectrum/cover shaders, all cover shapes, title marquee, full layout/deck/buttons, crossfade shaping, volume bar, animated covers and page-only lyrics effects remain incomplete. Previous v0.1.2 CI runs prove only that older source built; the latest v0.1.3/app changes need their own passing workflow, and exact-APK fingerprint/provider/device validation remains outstanding. Full details: docs/PRISM_LYRICS_MORPHE_BRIDGE.md.

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

Current main source version: version code 153 / version name 0.1.50-dev2; current Morphe bridge source version 0.1.3. Grok's 2026-11-08 commit 3804a62 truncated Director.java, HeadsUpTakeover.java and PeekSource.java to one-line files. Those three files have been restored to their pre-corruption source; IMode.java was not part of that corrupting commit and was left unchanged. Director received a no-behavior cleanup and the debug version was advanced for the next APK. The latest debug run/artifact is not yet confirmed for version 153. The newly updated Prism .mpp remains separate and has not been applied to the exact Prism APK or verified on the S10e.

## Backlog

1. Make the dual-bubble layout fully visible and usable: verify simultaneous display, equal spacing, independent touch and animation. Owner has already confirmed S10e cutout alignment and that bubbles move to the left of a right-anchored pill; do not generalize this to touch/animation completion.
2. Complete the whole battery feature, not merely shared gauge geometry/tint, and expose/verify every intended battery presentation and interaction.
3. Finish one coherent Material 3 Expressive system across compact glyphs, both bubbles, buttons, and expanded cards; shared tokens currently form a foundation, not a completed system.
4. Smart Island gap fills for idle + quick-launch are in source; verify on S10e. Do **not** port richer live-activity/system-event cooldown clones — DI ProgressSource, RuleSource and EventSource already handle that pretty well. Optional: settings UI for quick_launch pin list. History UI still needs owner verification.
5. Apply Prism Morphe bridge v0.1.3 to the exact Prism 1.8.2.1 APK, validate every remaining fingerprint and the local `/bridge/{songId}` query, then test with coobvo on the S10e. Verify native lyrics, romanization/translation helper outputs, configured upcoming lines, player/lyrics preference mapping and compact-fold transition. Record visual differences still needing Canvas parity. Do not send Prism sessions through LyricSync's independent providers.
6. Complete LyricSync parity for non-Prism media sessions only if still needed.

## Owner-confirmed device facts

- S10e camera cutout alignment works.
- With the pill anchored on the right, the companion bubbles move to the left.

These are the only new device confirmations recorded in this update. Do not infer additional touch, animation, spacing or feature completion.

## Device test target

Galaxy S10e (SM-G970F), right-side punch-hole, One UI 7 / Android 15 custom ROM. Exact System UI hook names must come from the owner's matching SystemUI.apk.
