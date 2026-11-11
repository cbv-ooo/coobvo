# Fusion Progress

Last updated: 2026-11-11

This file reports what is visible/usable, what is only present in source, and what the owner has confirmed. A green CI run is not a feature-completion check.

Smart Island detail: `docs/SMART_ISLAND_PORTS.md`, `docs/SMART_ISLAND_PORT_CONTEXT.md`.

## Evidence labels

- **Owner-confirmed:** the owner has observed the behavior on the S10e.
- **Build-confirmed:** the exact main commit compiled, packaged and uploaded. This says nothing about System UI runtime behavior.
- **Implemented, not device-verified:** source code exists and is connected, but the owner has not confirmed the visible result.
- **Incomplete:** only a foundation, subset or descriptive placeholder exists. Do not present it as finished.

## Current runtime blocker — settings app does not launch

- **Owner-reported:** the installed coobvo settings app still will not launch on the Galaxy S10e. The owner therefore cannot inspect or confirm whether the new Prism appearance options are available, customizable or toggleable.
- The pre-recovery versionCode 155 / `0.1.50-dev4` build passed Robolectric settings startup/page construction but did not resolve the owner-reported S10e launch issue. Prism player/lyrics controls remain unverified.
- Static inspection found an existing Robolectric smoke test, `SettingsRenderTest.everyPageBuildsAndLaysOut`, that launches `SettingsActivity` and builds its settings pages. That test and `SettingsUxTest` are now included in the Debug APK workflow.
- **Debug APK run #224 passed** for versionCode 155 / `0.1.50-dev4`; it verified settings Activity initialization under Robolectric, not S10e launch.
- **Debug APK run #243 passed** for commit `4341495b49078655995db9627a38374c7034cb42`, versionCode 157 / `0.1.50-dev6`. It compiled, passed `AppDiagnosticsTest` (including preference-change capture), `SettingsRenderTest`, `SettingsUxTest` and the selected Prism/SI/iMotion/cutout tests, and uploaded [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42976961388/artifacts/13835622659). This remains CI/Robolectric evidence, not a real-device launch confirmation.
- Version 157 also records all changed settings preference keys centrally, including custom choice rows and switches. The prior S10e launch failure still has **no confirmed root cause**; the launcher recovery route itself awaits an owner test.
- Until version 157 is opened successfully on the S10e, all newly mapped Prism preference controls and toggles remain **not verified**.

## In-app diagnostics and crash recovery — build-confirmed, device-unverified

- The settings APK now installs `CoobvoApplication` before its Activities and records app-process uncaught exceptions to private internal storage: `files/diagnostics/last-crash.txt`. A bounded rolling `debug-log.txt` stores recent startup, page-build, settings-toggle, search and other diagnostic events/errors.
- The launcher checks a crash-pending marker. After an app crash, the next open routes to a small standalone recovery screen so the main settings UI is not immediately retried. The user can inspect/copy the report, save `coobvo-debug-report.txt` through Android's document picker, report a feature that appears broken without throwing an exception, or acknowledge the crash and continue.
- The exported report combines the settings-app crash/log with the existing separate System UI / LSPosed diagnostics. Reports remain on-device; there is no automatic upload. Review the text before sharing because a manually entered problem description and exception details are included.
- Silent feature failures cannot be detected reliably just from the app process; use **Report a feature problem** to add the affected area and observed behavior to the local report. Known caught page-builder, app-picker and search errors are also recorded automatically.
- Debug run [#243](https://github.com/cbv-ooo/coobvo/actions/runs/42976961388) passed build, selected unit tests and APK upload for versionCode 157 / `0.1.50-dev6`. The build also tests preference-key logging. App-side crash capture and recovery still require a real-device exercise.

## Current owner-confirmed results

- **Settings app launch — owner-reported failure on version 155; version 157 diagnostics/recovery build awaits owner test.** The new launcher routes to a standalone diagnostics screen after an uncaught app crash. CI tests cover the normal route, the pending-crash route and diagnostics screen construction. Only an S10e test can confirm the launch and recovery behavior.


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
| Prism media bridge | **Bridge source updated; CI build/tests passed; exact Prism runtime unverified** | Morphe source v0.1.4 registers Prism's `LyricsState` at construction, retains a bounded set of strong references and refreshes live properties on `/bridge/{songId}` polls; setter and romanization/translation helper hooks are optional. The payload now distinguishes missing media ID, missing registered state and missing captured snapshot. Morphe run #28 built the `.mpp`; debug run #175 (versionCode 154 / `0.1.50-dev3`) passed app compilation, selected Prism parser/painter/player-preference tests and SI/iMode/cutout tests, then uploaded the APK. Exact patch application to Prism 1.8.2.1 / versionCode 58, provider access and lyrics rendering on the S10e remain unverified. Player/lyrics preference parity remains partial. See `docs/PRISM_LYRICS_MORPHE_BRIDGE.md`.

## Current debug install identity

- Debug application ID: dev.android.dynamicisland.coobvo (release ID unchanged).
- Stable debug-only signing key: app/coobvo-debug.keystore.
- Current main source version: code 157 / `0.1.50-dev6`; current Morphe bridge source version: `0.1.5`. **Debug APK run #243 passed** for commit `4341495b49078655995db9627a38374c7034cb42`: `assembleDebug`, AppDiagnostics/recovery tests (including a preference-change logging test), settings startup/page tests, selected Prism/SI/iMotion/cutout tests and APK upload. Artifact: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42976961388/artifacts/13835622659) (2,388,471 bytes; SHA-256 `b266a28a1e74dc964bab41b665bbd15693d412835be9283e406289b41f6b0b36`). The app's real crash behavior and launcher recovery still need S10e confirmation. Earlier evidence: **Debug APK run #175 passed** for commit `5ff55fd9034d0d1c2326b2aab5d07afa81a4180b`, including `assembleDebug`, selected Prism parser/painter/player-preference tests, SI/iMode/cutout tests and APK upload. Artifact: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42487474819/artifacts/13619808689) (2,346,491 bytes; SHA-256 `1e99b67dd058007ac93335ea30f5f97a0e51c631c61269a04b79202a802b5f49`). Morphe run #28 built and uploaded the version-0.1.4 patch: [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42487611825/artifacts/13620077914). These CI results do not prove exact-Prism APK fingerprint matching or S10e runtime behavior.
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
- **Prism media bridge:** current Morphe source is v0.1.4. The prior `PrismRememberLyricsStateFingerprint` failure remains handled by removing that Compose-helper fingerprint. The bridge now registers Prism's fully initialized `LyricsState` through its constructor fingerprint, retains up to eight strong references keyed by original song ID, and refreshes live properties on `/bridge/{songId}` queries. Setter and romanization/translation helper hooks are optional accelerators. The snapshot distinguishes `NO_MEDIA_ID`, `NO_LYRICS_STATE` and `NO_LYRICS_SNAPSHOT` from Prism's own loading/waiting/available/unavailable states. The extension continues to export Prism's real `LyricsState`, line/word/background-vocal timing, helper-returned romanization/translation, player/lyrics settings and scanned song waveform. Coobvo consumes only this source for Prism sessions; LyricSync remains disabled for Prism. The Canvas lyrics renderer and player preference mapping remain partial, not a 1:1 Prism page clone. Morphe run #28 built `.mpp` v0.1.4, and debug run #175 passed the selected Prism parser/painter/preferences tests plus SI/iMode/cutout tests and uploaded version 154 / `0.1.50-dev3`. The exact `.mpp` still has not been applied to Prism 1.8.2.1, and provider access/live lyrics/S10e behavior remain unverified.

## Director / Smart Island source audit (2026-11-08)

- Grok's commit `3804a62ba7acba936e48982e1fd8148a0bc2afed` replaced the full contents of `Director.java`, `HeadsUpTakeover.java` and `PeekSource.java` with one-line files while attempting a sticky-peek / auto-collapse change. This was source truncation, not a normal Java compile defect.
- The current `HeadsUpTakeover.java` and `PeekSource.java` were verified byte-for-byte against the parent of that corrupting commit. `Director.java` was restored to the same pre-corruption source; its empty restore-loop residue was removed and the card-removal branch indentation normalized.
- `IMode.java` was not part of the corrupting commit and is unchanged from that commit's parent. Its methods used by `Director` (`update`, `step`, `open`, `visibleItems`, `relayout`, `dispose`, `finishSleeping`) are present. No Smart Island feature code was changed during this repair.
- The intended sticky-peek change in the corrupting commit was lost when the affected files were restored; do not treat that commit as an implemented feature.
- The full pre-corruption Director at commit `cb76d6c75f102674cb6d6f94ecfb53ad2f2f29f9` was included in successful debug run [#42474855696](https://github.com/cbv-ooo/coobvo/actions/runs/42474855696), which finished before the truncating commit. This confirms that the full Director implementation compiled before Grok's later overwrite; it does not verify the new version-153 revision.
- Director source repair: [`be6a32a`](https://github.com/cbv-ooo/coobvo/commit/be6a32abf82155d0f40d67f5d53b137173112fd8). Debug build identity: `0.1.50-dev2` / versionCode `153`; CrashGuard marker synchronized. Build result and artifact are still pending verification.

## CI evidence

- **Prism bridge update:** Morphe run [#28](https://github.com/cbv-ooo/coobvo/actions/runs/42487611825) passed for bridge source v0.1.4 and uploaded [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42487611825/artifacts/13620077914). Debug run [#175](https://github.com/cbv-ooo/coobvo/actions/runs/42487474819) passed for app version 154 / `0.1.50-dev3`, including Prism bridge, lyrics snapshot/painter/player preference tests and SI/iMode/cutout tests; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42487474819/artifacts/13619808689). This proves CI build and unit fixtures only; exact Prism APK application and S10e runtime remain unverified.
- **Run #164 passed** for app versionCode 153 / `0.1.50-dev2` at commit `07501181bd3ada2b12206c460d2c50857b08d13b`. It ran `assembleDebug`, restored `PrismPlayerPreferencesTest` to the required selected test command, passed the SI/iMode/cutout test step, and uploaded [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42485840083/artifacts/13619441473).
- The three failures in run #161 were test-harness failures: `PrismPlayerPreferencesTest` used plain JUnit while Android JSON framework methods were running as default-value stubs (`returnDefaultValues = true`). The neighboring `PrismMediaBridgeClientTest` already used `RobolectricTestRunner` at SDK 35. The player-preference test now uses the same runner/configuration, and CI coverage is re-enabled. The parser, Morphe export and SI/iMode implementation were not changed to hide the assertions. Run #162 was the temporary test-exclusion workaround and is superseded by #164.
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
