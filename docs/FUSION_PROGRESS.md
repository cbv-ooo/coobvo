# Fusion Progress

Last updated: 2026-11-08

## Repository and working rules
- Repository: cbv-ooo/coobvo; all work is directly on main.
- Dynamic Island's Java/Xposed implementation remains the base. System UI code is Java/Canvas only.
- The only workflow is .github/workflows/debug-apk.yml: ./gradlew --no-daemon assembleDebug, then upload artifact DynamicIsland-debug-apk. It does not run tests, lint, translations or release tasks.
- A green CI run means compile/package/upload only. Galaxy S10e rendering, cutout placement, gestures and animation remain unverified until the owner tests the APK.

## Current debug install identity
- Debug application ID: dev.android.dynamicisland.coobvo (release ID unchanged).
- Stable debug-only signing key: app/coobvo-debug.keystore.
- Current main version: code 129 / 0.1.27-dev1.
- This is a separate package from the older dev.android.dynamicisland installation, deliberately avoiding the existing certificate mismatch. Disable the old LSPosed module and enable the new coobvo module for com.android.systemui before testing. Future debug builds must keep the same package ID/key and increment version code.

## Implemented on main
- Two companion bubbles have independent scene layers, drawing, indexed geometry and touch handling. Index 0 is nearest the pill; index 1 sits farther out. The S10e right anchor places both bubbles left of the compact pill.
- The second bubble uses the same edge-to-edge spacing rule as the first. It is included in the footprint touch region and TouchController has an independent downOnBubble2 path. Real touch confirmation is still required.
- S10e cutout placement checks display insets and the framework resource-approximation API, with a model fallback. Alignment is not device-verified.
- iMode uses its own Canvas renderer and now draws the expanded card bodies as well as its compact activity nodes. Its animation springs are advanced by Director.stepCards() via the existing iMode delegation; do not add a second IMode.step() call in IslandView.
- Battery icons share BatteryGauge geometry and semantic charge tint across compact battery UI, notification glyphs and expanded battery/event cards.
- Existing Material 3 Expressive work includes six motion tokens in graphics/Motion, opaque semantic colour roles in graphics/Colors, opaque surface-container cards, artwork-derived primary-container media controls, shared battery glyph rendering and a 32dp default expanded-card radius. Consolidation into a single expressive token source and complete compact glyph coverage remain unfinished.
- Smart Island settings distinguish capabilities available in the Java runtime from features not yet ported. A Java `TimerStopwatchParser` classifies clock-app, timer, alarm and stopwatch notifications, extracts elapsed/remaining values and paused state, and feeds the existing `OaCardBuilder`/`TimerBody` path when a native Chronometer view is unavailable; the native Samsung Chronometer remains preferred. A Java `NavigationParser` recognizes supported map apps and navigation-category guidance, excludes known delivery/ride apps, parses maneuver/distance/trip text, and maps it into existing live card fields. It reads notification extras and falls back to Samsung `mPrimaryInfo`/`mSecondaryInfo`. Map-app behaviour remains unverified on-device.
- LyricSync has a background provider cascade for Kugou KRC, Netease YRC and LRCLIB LRC/static text, shared line/word models, cache, a media-card lyric row and current timed-word highlighting where source word timings exist. This pass also precomputes the normalized track key and caches ellipsized current/next line text on line changes, avoiding key normalization and prefix-string construction during drawing. SpicyLyrics requires an optional client key/Spotify track ID and is not yet connected. Provider matching and exact source parity still need work.
- Lyrics lookup is opt-in and defaults off. The settings summary discloses that track title/artist are sent to external lyric providers. The runtime checks the hosting process's INTERNET permission before making requests.
- The Smart Island and LyricSync archives were inspected directly; only Java behaviour/models are being ported into the existing Dynamic Island architecture.

## Material 3 Expressive progress
- Shared spatial/effects spring tokens are applied to the island, cards, iMode and Director transitions.
- Opaque semantic colour roles cover surface containers, on-surface, on-surface-variant, on-primary-container and outline-variant.
- Card fills are opaque; media accent controls use an opaque primary-container role.
- Module-owned glyphs and embedded Lottie paths use shared tint and rounded stroke caps/joins. Original app notification icons and album art remain unchanged.
- Expanded-card default corner radius is 32dp; the custom roundness preference still overrides it.
- Remaining: centralize shape/motion/colour constants and apply consistent M3 Expressive sizing to compact glyphs, buttons and bubble content.

## CI evidence
- Runs #34–#49 passed compile, APK packaging and artifact upload for their respective commits.
- Run #41 passed for commit 67304af64dd1009c814771ce94e4c31c5d9f3d9f; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42438321472/artifacts/13600168702). The redundant explicit iMode spring-step call was removed; Director.stepCards() is the single owner of iMode stepping.
- Run #44 passed for commit 7798e2f9da1d72bec2f87b47e991fb808c251eff; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42438606018/artifacts/13599659870). It includes the timer/stopwatch parser and stable debug install identity.
- Run #46 passed for commit 599a65094994025a2ff5fe2cb1237ed8472b5f6c; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42438935285/artifacts/13600931647). It adds the initial Java navigation parser.
- Run #47 passed for commit d83c071201accd162348b6862b48711e273e7fa9; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439111297/artifacts/13600339730). It adds Samsung ongoing-activity primary/secondary text fallback for navigation.
- Run #48 passed for commit d91d785d4a4d732f004c7b810e5ca0f99c3af704; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439210788/artifacts/13600454452). It records the accepted navigation change and increments the debug version.
- Run #49 passed for commit e7747fbb4040d1d695e181c2c9d4d36d0eabed55; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439401274/artifacts/13600594237). It includes the LyricSync draw-path cache improvements and version 129.
- Earlier runs #26–#33 failed on malformed literal newline escapes in a Java import and the manifest. Those were corrected in fc54f66; subsequent runs #34–#49 are green.
- CI proves compilation, APK packaging and upload only. It does not verify installation over the old package, LSPosed activation, camera alignment, bubble touch response, animation quality or lyrics against a live media session.

## Remaining work
1. Validate the current debug package and LSPosed scope on the phone; confirm the installer upgrades the coobvo package in place across builds.
2. Test the second bubble's real touch target and spacing, camera-hole placement, expanded notifications and compact/expanded iMode animations on the S10e.
3. Port remaining Smart Island features in Java: richer live-activity parsing, notification cooldown/history, quick-launch shortcuts, idle widgets, network speed and diagnostics.
4. Consolidate Material 3 Expressive tokens and finish consistent compact/expanded icon components.
5. Complete LyricSync provider parity and track matching while keeping network lookup disabled by default.

## Constraints
- No blur, frosted glass, backdrop capture, RenderEffect or transparency effects.
- No new dependencies in System UI/Xposed.
- Keep each hook isolated through the existing safe hook/reflection wrappers.
- Do not edit generated resources or Lottie Java by hand.
- Preserve legacy preference keys that keep existing settings readable.
