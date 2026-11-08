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
| Smart Island | **Incomplete** | Timer/stopwatch and navigation parsers are connected to the existing ongoing-activity card path. The Settings page's descriptive rows are not features. Remaining Smart Island behavior must become actual visible/usable Java features before it is listed as active. |
| iMode | **Source implementation only; device verification outstanding** | Confirm expanded activity/notification content and animation on the S10e. |
| LyricSync | **Partial provider implementation** | Current coobvo path covers Kugou KRC, NetEase YRC and LRCLIB LRC/static. It is not full LyricSync parity and is not a Prism integration. |
| Prism media bridge | **Source staged; not build/package verified** | Coobvo now reads the versioned Prism bridge marker, album/track flags and technical stream summary in its existing media card; LyricSync lookup is suppressed for Prism sessions. The Morphe source is staged under morphe/prism-media-bridge, but no .mpp or patched Prism APK has been built or tested. Lyrics/style export is still pending. |

## Current debug install identity

- Debug application ID: dev.android.dynamicisland.coobvo (release ID unchanged).
- Stable debug-only signing key: app/coobvo-debug.keystore.
- Current main source version: code 134 / 0.1.32-dev1.
- Last passing main build is code 133 / 0.1.31-dev1 at run #53 (commit 4a80e8929984e31ae69e69697a5ba311e6b7855e). Its debug APK artifact is [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42444559343/artifacts/13603027828). Version 134 is the documentation/version correction now queued for its own CI run.
- The owner removed the older debug installation and is now using coobvo. Do not instruct them to inspect old package settings unless a duplicate returns.

## Source implementation notes

- **Dual bubble geometry:** two companion layers have indexed drawing and independent touch paths. Index 0 is nearest the pill; index 1 is farther out. S10e right anchor places them to the left of the pill. Only cutout positioning and side selection are owner-confirmed.
- **Battery:** BatteryGauge geometry/tint is shared among compact battery, notification glyph and expanded battery/event rendering. This is not end-to-end battery feature completion.
- **M3 Expressive:** six motion tokens, opaque semantic colors, opaque card fills, artwork-derived media accent and a 32dp default expanded-card radius exist. A single coherent token system and complete compact/bubble/button coverage do not.
- **Smart Island:** `TimerStopwatchParser` and `NavigationParser` are wired to `OaCardBuilder`. They do not represent the full reference app. Descriptive Settings text must not be counted as feature delivery.
- **iMode:** expanded card bodies are drawn by the Canvas renderer; Director owns stepping. The current device appearance and animation have not been confirmed.
- **LyricSync:** current Java path is a narrow three-provider implementation with line/word models, background lookup, cache and media-card rendering. It is not used for Prism sessions in the new source path.
- **Prism media bridge:** coobvo's Java side has a local bridge reader, album/track flags and technical-info row. A Prism-specific Morphe patch source is staged, targeting Prism 1.8.2.1 / versionCode 58. It exposes Prism's in-memory NerdStats and player diagnostics through the existing artwork provider route. This patch source has not yet been compiled into an .mpp bundle or tested against the original Prism APK. The lyric model, source selection and resolved lyric-style preferences are not yet exported.

## CI evidence

- Runs #34–#53 passed compilation, APK packaging and artifact upload for their respective commits.
- Run #41 passed for commit 67304af64dd1009c814771ce94e4c31c5d9f3d9f; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42438321472/artifacts/13600168702). The redundant explicit iMode spring-step call was removed; Director.stepCards() is the single owner of iMode stepping.
- Run #44 passed for commit 7798e2f9da1d72bec2f87b47e991fb808c251eff; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42438606018/artifacts/13599659870).
- Run #46 passed for commit 599a65094994025a2ff5fe2cb1237ed8472b5f6c; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42438935285/artifacts/13600931647).
- Run #47 passed for commit d83c071201accd162348b6862b48711e273e7fa9; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439111297/artifacts/13600339730).
- Run #48 passed for commit d91d785d4a4d732f004c7b810e5ca0f99c3af704; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439210788/artifacts/13600454452).
- Run #49 passed for commit e7747fbb4040d1d695e181c2c9d4d36d0eabed55; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439401274/artifacts/13600594237).
- Run #50 passed for commit 2ddbc6f1feff15a8ac1067d6db9e253765606f72; artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439603938/artifacts/13600578084).
- **Run #51 passed** for commit `f3046b390d4b6f6a9b8fd70c0fd23c62564bdb44`, source version 131 / 0.1.29-dev1. Build and upload both succeeded. Artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42439738832/artifacts/13601441127).
- **Run #52 passed** for commit `d09ef5625096e998fe9abc67a474fc6e2ee7663f`, source version 132 / 0.1.30-dev1. This commit updated project status documents and build version; build and upload passed. Artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42442152143/artifacts/13602555320).
- **Run #53 passed** for commit `4a80e8929984e31ae69e69697a5ba311e6b7855e`, source version 133 / 0.1.31-dev1. The coobvo debug APK compiled, packaged and uploaded. Artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42444559343/artifacts/13603027828). This run did not build the separate Morphe patch project or verify a patched Prism APK.
- CI proves compilation, APK packaging and artifact upload only. It does not verify bubble touch response, expanded notifications, iMode animation, battery presentation, M3E consistency, Smart Island behavior or lyrics timing on the S10e.

## Remaining work

1. Finish and visibly expose both bubble contents, spacing and independent touch/animation behavior; preserve the owner-confirmed cutout/side result.
2. Complete the actual battery experiences, not just the shared gauge component.
3. Complete the M3 Expressive token and component system across the real compact/expanded UI.
4. Implement Smart Island features as actual cards/behaviors and real settings controls; remove or label descriptive-only entries.
5. Build/package the Prism-specific Morphe source into a real .mpp, validate fingerprints against Prism 1.8.2.1, then add Prism lyrics/style export and test the patched Prism + coobvo media card.
6. Decide whether more LyricSync work is needed for media apps other than Prism.

## Constraints

- No blur, frosted glass, backdrop capture, RenderEffect or transparency effects.
- No new dependencies in System UI/Xposed.
- Keep each hook isolated through the existing safe hook/reflection wrappers.
- Do not edit generated resources or Lottie Java by hand.
- Preserve legacy preference keys that keep existing settings readable.
