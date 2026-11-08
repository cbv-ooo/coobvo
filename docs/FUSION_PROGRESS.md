# Fusion Progress

Last updated: 2026-11-08

This file reports what is visible/usable, what is only present in source, and what the owner has confirmed. A green CI run is not a feature-completion check.

Smart Island detail: `docs/SMART_ISLAND_PORTS.md`, `docs/SMART_ISLAND_PORT_CONTEXT.md`.

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
| Smart Island | **Partial Java port + Phase 0 proof** | Timer/stopwatch, navigation, opt-in ordinary-notification cooldown and opt-in notification history are implemented. Phase 0 `DemoSource` (broadcast DEMO show 0|1) is wired with soft-fail start/collect. Cooldown and history store tests passed CI run #72. Idle widgets, shortcuts, richer live-activity parsing and system-event cooldown remain unported. Device behavior for history UI and Demo proof is not yet owner-confirmed. |
| iMode | **Source fixes implemented; device verification outstanding** | Returning cards remain opaque until their compact fold settles; expanded cards are black and use the pill's outline setting. Expanded-card width honours an explicit max-width preference, and automatic notification expansion is deferred when its sweep intersects the physical cutout. Verify on the S10e. |
| LyricSync | **Partial provider implementation** | Current coobvo path covers Kugou KRC, NetEase YRC and LRCLIB LRC/static. It is not full LyricSync parity and is not a Prism integration. |
| Prism media bridge | **Source updated; fresh CI/runtime verification pending; player parity partial** | Morphe bridge source is v0.1.3 and exports Prism's LyricsState, original line/word/background-vocal timing, helper-returned romanization/translation, player/lyrics settings and scanned song waveform data. Coobvo now distinguishes provider failure from a real no-lyrics result and retries a failed bridge query. The island maps upcoming lyric lines, selected lyric styles/timing, title/time/cover and scrubber settings, plus scanned WAVE bars. The latest code changes have not yet been confirmed by a new passing build or exact Prism 1.8.2.1 / versionCode 58 runtime test. Full page/player parity remains incomplete. See `docs/PRISM_LYRICS_MORPHE_BRIDGE.md`. |

## Current debug install identity

- Debug application ID: dev.android.dynamicisland.coobvo (release ID unchanged).
- Stable debug-only signing key: app/coobvo-debug.keystore.
- Current main source version: code 151 / `0.1.49-dev1`; current Morphe bridge version: `0.1.2`. Debug APK run [#42474855696](https://github.com/cbv-ooo/coobvo/actions/runs/42474855696) passed build, the targeted test step and artifact upload: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42474855696/artifacts/13614564628). Morphe run [#42474855738](https://github.com/cbv-ooo/coobvo/actions/runs/42474855738) passed bundle generation/verification/upload: [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42474855738/artifacts/13615121015). These prove source compilation, selected tests and packaging only; application to the exact Prism APK and S10e runtime behavior remain unverified.
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
- **Smart Island:** `TimerStopwatchParser` and `NavigationParser` are wired to `OaCardBuilder`. The ordinary notification burst cooldown is wired through `HeadsUpTakeover`, with an opt-in setting, three-postings/30-second threshold, three-minute quiet period and latest-still-active buffered delivery. Notification history records only ordinary cards actually shown by coobvo, via a local SQLite store behind a provider restricted to System UI writes and module-app reads/clears. It is off by default, keeps 500 entries for seven days, and excludes OTP-like, secret, call/alarm, transport/navigation and ongoing notifications. Both cooldown and history database tests passed in runs #72 and #73. **Phase 0 DemoSource** (`dev.android.dynamicisland.DEMO` broadcast, key `demo:proof`) is soft-fail wired in `IslandSources` for on-device proof without risking System UI. History UI and Demo on-device behaviour remain unverified on the S10e. Idle widgets, quick-launch shortcuts, richer live-activity parsing and system-event cooldown remain pending.
- **iMode:** expanded card bodies are drawn by the Canvas renderer; Director owns stepping. The current device appearance and animation have not been confirmed.
- **Cutout-aware notification expansion:** automatic notification takeover checks the actual/fallback camera-hole bounds against the configured expanded-card width and the compact-to-expanded vertical sweep. When they intersect, the notification remains in the compact island instead of auto-expanding; manual opening remains available. This is implemented in source, not yet owner-verified.
- **LyricSync:** current Java path is a narrow three-provider implementation with line/word models, background lookup, cache and media-card rendering. It is not used for Prism sessions in the new source path.
- **Prism media bridge:** current Morphe source is v0.1.3. The prior owner-reported `PrismRememberLyricsStateFingerprint` failure was addressed by removing that Compose fingerprint and capturing the actual LyricsState through its lyrics/source/loading/waiting setters, refreshing live getters on permitted `/bridge` queries, and hooking Prism's romanization/translation helper returns. The snapshot now also exports Prism's 100-point `SongWaves` scan when available. Coobvo consumes only this source for Prism sessions; LyricSync remains disabled for Prism. The Canvas lyrics renderer includes the current-line rule, word/line timing, five motion choices, upcoming-line preview (maximum three), selected visual preferences, backing vocals and extras. The player card maps title size/weight/alignment, time/remaining-time mode, cover corners/FULL-width choice, scrubber style/color/thickness/thumb, and scanned WAVE bars. These are partial mappings, not a 1:1 Prism player-page clone: RIBBONS animation, full spectrum/cover shaders, all custom cover shapes, title marquee, full layout/deck/button arrangement, crossfade shaping, volume bar, animated cover, full lyrics-page backgrounds and other page-only effects remain incomplete. New app and v0.1.3 bridge source commits are present, but there is no new passing CI result recorded for these latest commits. Previous v0.1.2 build artifacts are historical only. The exact .mpp still has not been applied to Prism 1.8.2.1, and all runtime/device behavior remains unverified.

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
4. Continue the Smart Island Java ports: quick-launch shortcuts, idle widgets, richer live-activity parsing and system-event cooldown. Notification history is implemented and unit-test-confirmed; verify its settings viewer on the S10e. Phase 0 DemoSource is in source — verify DEMO broadcast on device.
5. Apply the current Morphe bridge v0.1.3 source to the exact Prism 1.8.2.1 APK and confirm each remaining fingerprint. Install the patched Prism APK and current coobvo debug APK together; verify the `/bridge/{songId}` query from System UI, its song ID, player settings and waveform/lyrics data. Exercise loading, waiting, true unavailable, static/unsynced, line-synced and word-synced lyrics, then compare player title/time/cover/scrubber controls. Finish remaining RIBBONS/spectrum/cover/page mappings only where the feature belongs on the island. Track Morphe export, Coobvo rendering/build, and S10e verification as separate statuses. See `docs/PRISM_LYRICS_MORPHE_BRIDGE.md`.
6. Decide whether more LyricSync work is needed for media apps other than Prism.

## Constraints

- No blur, frosted glass, backdrop capture, RenderEffect or transparency effects.
- No new dependencies in System UI/Xposed.
- Keep each hook isolated through the existing safe hook/reflection wrappers.
- Do not edit generated resources or Lottie Java by hand.
- Preserve legacy preference keys that keep existing settings readable.
