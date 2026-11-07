# Fusion Progress

Last updated: 2026-11-07

## Repository and branch
- Base repository: `cbv-ooo/coobvo` (Dynamic Island Java/Xposed).
- Working branch: `work/fusion-recovery-20261107`.
- Draft pull request: [#1 — Recover IslandView and implement dual-bubble S10e placement](https://github.com/cbv-ooo/coobvo/pull/1).
- Base: `main`; no changes have been merged into main.

## Recovery
The `main` version of `IslandView.java` was truncated to a 443-line recovery stub. The branch restores the complete 1,404-line source from commit `1b478a5d31f4384fb56d2748a1ba518261bf7dd1`, then reapplies the side-aware geometry setter/call site.

## Dual-bubble implementation
- A right-anchored pill now places the compact island right and both companion bubbles on the left of the S10e cutout.
- The second companion has a separate model slot and incoming/outgoing content layers.
- Both companion shapes are painted and outlined; the first uses a side-aware metaball connector, while the second remains detached.
- Both shapes have independent touch hit targets and a left-swipe minimizes the selected bubble's item.
- Lifecycle cleanup, frame scheduling, audio/waveform activity, outline contrast, and geometry sampling bounds account for the second bubble.

## Validation
- GitHub Actions CI run #26 succeeded for commit `d1bd5d2fce6396e7c8ef49f24c29cc75428515fb`.
- Passed: translation validation, script unit tests, `testReleaseUnitTest`, `assembleRelease`, and the check that compile-only framework stubs are not packaged.
- This is automated build/test evidence, not a visual or touch test on the actual Galaxy S10e.

## Source integrations still outstanding
- Smart Island: reference source is Kotlin/Compose with an Accessibility overlay, Hilt, DataStore, and a broad service architecture. Port capabilities selectively into the Java/Xposed host; do not transplant its UI framework.
- LyricSync: its Java provider cascade and lyric models are suitable references. The next integration is a compact Java service that fetches SYLLABLE/LINE lyrics from the playback track and updates the existing media card's lyric row in sync with media position.
- Prism: no Prism source archive was present in this task. Wait for its source/API rather than inventing hooks.
- Blur/material: remove FrostedSurface/backdrop capture, sampling, foreground blur and blur-mask effects, along with related preferences/tests/AGSL generation. Replace with opaque surfaces.

## Release discipline
No APK has been published or signed by this branch. The PR remains draft. Signed APK output must use the repository release workflow and configured signing secrets; do not distribute an unsigned CI intermediate as an installable release.
