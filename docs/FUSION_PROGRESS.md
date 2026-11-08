# Fusion Progress

Last updated: 2026-11-08

This file reports what is visible/usable, what is only present in source, and what the owner has confirmed. A green CI run is not a feature-completion check.

## Evidence labels

- **Owner-confirmed:** the owner has observed the behavior on the S10e.
- **Build-confirmed:** the exact main commit compiled, packaged and uploaded. This says nothing about System UI runtime behavior.
- **Implemented, not device-verified:** source code exists and is connected, but the owner has not confirmed the visible result.
- **Incomplete:** only a foundation, subset or descriptive placeholder exists. Do not present it as finished.

## Current owner-confirmed results

- **S10e cutout alignment — owner-confirmed.** The owner reports the island is positioned relative to the camera cutout.
- **Right-anchor bubble side — owner-confirmed.** When the pill is on the right, the bubbles move to the left.
- **Duplicate full island — likely install-identity issue cleaned up.** The older debug installation was deleted and the owner is now using coobvo. The duplicate was likely caused by both debug identities being enabled; do not assume that old copy remains. A second full island after this cleanup would need a fresh current-runtime investigation.

These confirmations do not verify both bubbles visibly render together, exact spacing, independent touch, or animation.

## Feature status

| Feature | Honest status | What remains before completion |
|---|---|---|
| Cutout positioning | **Owner-confirmed for the S10e** | Keep this verified result; revisit only if a regression appears. |
| Bubble sidedness | **Owner-confirmed for right anchor** | Verify both bubbles together, gap, tap/swipe targets and animation. |
| Full Dual Bubbles feature | **Partially implemented; not fully verified** | Real-device confirmation of simultaneous rendering, spacing, independent input and lifecycle/animation. |
| Battery feature | **Incomplete** | Current source work shares gauge geometry and semantic tint across several views. That is one implementation detail, not the complete battery feature, all battery states, cards, actions and presentation. |
| Material 3 Expressive | **Foundation only; incomplete** | Centralize and apply a consistent system across pill, both bubbles, compact glyphs, buttons, cards and motion; then inspect actual UI. |
| Smart Island | **Partial Java port implemented** | Timer/stopwatch, navigation, opt-in ordinary-notification cooldown and opt-in notification history are implemented. Both cooldown and history store tests passed CI run #72. Idle widgets, shortcuts, richer live-activity parsing and system-event cooldown remain unported. Device behavior for the new history UI is not yet owner-confirmed. |
| iMode | **Source implementation only; device verification outstanding** | Confirm expanded activity/notification content and animation on the S10e. |
| LyricSync | **Partial provider implementation** | Current coobvo path covers Kugou KRC, NetEase YRC and LRCLIB LRC/static. It is not full LyricSync parity and is not a Prism integration. |
| Prism media bridge | **Morphe .mpp build-confirmed; APK/device validation pending** | Morphe run #3 generated a downloadable .mpp. The Prism LyricsState snapshot is implemented in patch source and consumed by coobvo's Java media card; it exports exact stored line/word/background-vocal timings and resolved lyric preferences. Translation/romanization output arrays and full Canvas style mapping remain unfinished. The .mpp has not yet been applied to the exact Prism APK or tested on the S10e. |

## Current debug install identity

- Debug application ID: dev.android.dynamicisland.coobvo (release ID unchanged).
- Stable debug-only signing key: app/coobvo-debug.keystore.
- Current main source version: code 141 / 0.1.39-dev1.
- Debug APK run #62 passed for commit 6ba861405271d4b6c5909f77aade4768267cc9b8, including the targeted NotificationCooldownManagerTest and APK artifact upload. Artifact: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42447577459/artifacts/13604291827).
- Debug APK run #63 passed after the Smart Island and Prism documentation update; artifact: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42447644666/artifacts/13604103064).
- Debug APK run #67 passed for version 138 / 0.1.36-dev1, including app compilation, the cooldown unit test and APK upload: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42447914833/artifacts/13604711073).
- Debug APK run #72 passed for version 141 / 0.1.39-dev1. It compiled and packaged the app, passed both NotificationCooldownManagerTest and NotificationHistoryStoreTest, and uploaded [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42448675663/artifacts/13603839599).
- Debug APK run #73 passed on the documentation-updated main commit with the same version-141 source, both tests and APK upload: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42448805521/artifacts/13603934433).
- The owner removed the older debug installation and is now using coobvo. Do not instruct them to inspect old package settings unless a duplicate returns.

## Source implementation notes

- **Dual bubble geometry:** two companion layers have indexed drawing and independent touch paths. Index 0 is nearest the pill; index 1 is farther out. S10e right anchor places them to the left of the pill. Only cutout positioning and side selection are owner-confirmed.
- **Battery:** BatteryGauge geometry/tint is shared among compact battery, notification glyph and expanded battery/event rendering. This is not end-to-end battery feature completion.
- **M3 Expressive:** six motion tokens, opaque semantic colors, opaque card fills, artwork-derived media accent and a 32dp default expanded-card radius exist. A single coherent token system and complete compact/bubble/button coverage do not.
- **Smart Island:** `TimerStopwatchParser` and `NavigationParser` are wired to `OaCardBuilder`. The ordinary notification burst cooldown is wired through `HeadsUpTakeover`, with an opt-in setting, three-postings/30-second threshold, three-minute quiet period and latest-still-active buffered delivery. Notification history records only ordinary cards actually shown by coobvo, via a local SQLite store behind a provider restricted to System UI writes and module-app reads/clears. It is off by default, keeps 500 entries for seven days, and excludes OTP-like, secret, call/alarm, transport/navigation and ongoing notifications. Both cooldown and history database tests passed in runs #72 and #73. History UI/device behaviour remains unverified on the S10e. Idle widgets, quick-launch shortcuts, richer live-activity parsing and system-event cooldown remain pending.
- **iMode:** expanded card bodies are drawn by the Canvas renderer; Director owns stepping. The current device appearance and animation have not been confirmed.
- **LyricSync:** current Java path is a narrow three-provider implementation with line/word models, background lookup, cache and media-card rendering. It is not used for Prism sessions in the new source path.
- **Prism media bridge:** the Morphe project builds and Morphe run #3 produced artifact `coobvo-prism-media-bridge-mpp`. The patch hooks Prism 1.8.2.1 / versionCode 58, exposes its in-memory stream/player diagnostics and snapshots its native LyricsState (source/status, exact line/word/background-word times, alignment/gap markers and resolved lyric-look preferences). Coobvo consumes the local snapshot only for Prism sessions; LyricSync's separate provider cascade is disabled for Prism. The bridge does not yet export Prism's rendered translated/romanized lines. Canvas now applies a subset of Prism's exported look settings: sung/non-sung/custom colours, other-line opacity, weight emphasis, text size scale, uppercase, strip alignment and one/two-line choice. Full motion, glow/shadow and every background/style behaviour remain unfinished. The bundle has not been applied to the exact Prism APK or device-tested.

## CI evidence

- Debug APK runs #70 and #71 were failed test iterations while introducing notification history; the API-35 test configuration and retention guard were corrected. Runs #72 and #73 then passed both cooldown and history test classes, packaging and upload.
- Run #41 passed for commit 67304af64dd1009c814771ce94e4c31c5d9f3d9f; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42438321472/artifacts/13600168702). The redundant explicit iMode spring-step call was removed; Director.stepCards() is the single owner of iMode stepping.
- Run #44 passed for commit 7798e2f9da1d72bec2f87b47e991fb808c251eff; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42438606018/artifacts/13599659870).
- Run #46 passed for commit 599a65094994025a2ff5fe2cb1237ed8472b5f6c; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42438935285/artifacts/13600931647).
- Run #47 passed for commit d83c071201accd162348b6862b48711e273e7fa9; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439111297/artifacts/13600339730).
- Run #48 passed for commit d91d785d4a4d732f004c7b810e5ca0f99c3af704; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439210788/artifacts/13600454452).
- Run #49 passed for commit e7747fbb4040d1d695e181c2c9d4d36d0eabed55; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439401274/artifacts/13600594237).
- Run #50 passed for commit 2ddbc6f1feff15a8ac1067d6db9e253765606f72; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439603938/artifacts/13600578084).
- **Run #51 passed** for commit `f3046b390d4b6f6a9b8fd70c0fd23c62564bdb44`, source version 131 / 0.1.29-dev1. Build and upload both succeeded. Artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439738832/artifacts/13601441127).
- **Run #52 passed** for commit `d09ef5625096e998fe9abc67a474fc6e2ee7663f`, source version 132 / 0.1.30-dev1. This commit updated project status documents and build version; build and upload passed. Artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42442152143/artifacts/13602555320).
- **Run #61 passed** for commit `ef0633627ee3c939695dbbf59e5f8d424944fb48`, source version 135 / 0.1.33-dev1. Debug APK artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42447445219/artifacts/13604575694).
- **Run #62 passed** for commit `6ba861405271d4b6c5909f77aade4768267cc9b8`, source version 136 / 0.1.34-dev1. The run compiled and packaged the app, ran `NotificationCooldownManagerTest` successfully, and uploaded [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42447577459/artifacts/13604291827).
- **Morphe Patch run #3 passed** and generated [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42446806609/artifacts/13603559103).
- **Morphe Patch run #4 passed** and generated [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42447644674/artifacts/13604825171).
- **Morphe Patch run #5 passed** and generated the latest [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42448038049/artifacts/13604122347). These runs prove bundle generation, not application to the original Prism APK.
- **Run #66 passed** for the first Prism Canvas look mapping revision (version 137 / 0.1.35-dev1), including the cooldown test and APK artifact upload: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42447859677/artifacts/13603688819).
- **Run #67 passed** for version 138 / 0.1.36-dev1, including corrected Prism strip defaults, real-word-timing-only highlighting, cooldown test and APK upload: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42447914833/artifacts/13604711073).
- CI proves compilation, APK packaging and artifact upload only. It does not verify bubble touch response, expanded notifications, iMode animation, battery presentation, M3E consistency, Smart Island behavior or lyrics timing on the S10e.

## Remaining work

1. Finish and visibly expose both bubble contents, spacing and independent touch/animation behavior; preserve the owner-confirmed cutout/side result.
2. Complete the actual battery experiences, not just the shared gauge component.
3. Complete the M3 Expressive token and component system across the real compact/expanded UI.
4. Continue the Smart Island Java ports: quick-launch shortcuts, idle widgets, richer live-activity parsing and system-event cooldown. Notification history is implemented and unit-test-confirmed; verify its settings viewer on the S10e.
5. Apply the generated Prism .mpp to the exact Prism 1.8.2.1 APK, validate the bytecode fingerprints, install it with coobvo, then verify media diagnostics and native synchronized lyrics on the S10e. Finish translation/romanization export and Canvas mapping of Prism look preferences.
6. Decide whether more LyricSync work is needed for media apps other than Prism.

## Constraints

- No blur, frosted glass, backdrop capture, RenderEffect or transparency effects.
- No new dependencies in System UI/Xposed.
- Keep each hook isolated through the existing safe hook/reflection wrappers.
- Do not edit generated resources or Lottie Java by hand.
- Preserve legacy preference keys that keep existing settings readable.
