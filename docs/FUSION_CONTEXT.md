# Fusion Context

## Base
Dynamic Island (this repo, Java/Xposed). Always upstream here.

## Goals
- Port missing Smart Island features
- Add compatible Prism Xposed hooks
- Remove all frosted/blur/glass/backdrop
- 2 independent small bubble islands + 1 compact main
- S10e right cutout: compact on right → both small bubbles left
- Lottie + Material 3 Expressive (opaque)
- APK only via ci/release workflows

## Done so far
- issue-triage workflow removed
- Recovered the complete `IslandView.java` implementation from the last complete in-repository revision; main's 443-line truncation is replaced on the working branch.
- `IslandPlacement` passes the selected pill anchor to `IslandView`: a right-anchored S10e pill puts the compact island on the right and both companion bubbles to its left.
- `Metrics` and `Shapes` retain indexed, side-aware bubble geometry (index 0 = first bubble, index 1 = second bubble farther out).
- `Scene` and `Director` now track `secondary2` with its own incoming/outgoing content layers and shared split/merge timing.
- `PillPainter` draws both companion islands; the first connector is side-aware, and the second bubble stays visually detached.
- `TouchController` hit-tests both bubbles; horizontal swipe on either bubble minimizes that bubble's own item.
- Both bubbles are included in adaptive outlines, geometry refresh, frame lifecycle, audio/waveform scheduling, and microphone-input selection.
- GitHub Actions CI run #26 passed: translation validation, release unit tests, `assembleRelease`, and the framework-stub packaging check.

## Still needed
1. Remove the frosted card material, compositor backdrop capture/sampling, content blur, and blur-based glow/effects; update the affected settings, tests, generated shader task, and documentation as one dependency-aware change.
2. Add opaque Material 3 Expressive surfaces and Lottie only where the Java/Xposed host can support them without importing Smart Island's Compose/Hilt/Accessibility overlay stack.
3. Port selected Smart Island capabilities as small Java modules (notification parsing, navigation, calls, timers, idle widgets, network speed, diagnostics), preserving this repository's event and rendering architecture.
4. Integrate LyricSync's Java lyric providers and SYLLABLE/LINE data into the existing media card; render current-line and word-level progress below the seek/control row, with an explicit fallback when word timing is unavailable.
5. Add compatible Prism Xposed hooks after its source/API is available; no Prism archive was attached in this task.
6. Verify both-bubble spacing, left-of-pill placement, gestures, and media/notification transitions on the Galaxy S10e / One UI 7 Android 15 target.
7. Keep PR #1 draft until the remaining feature work and device validation are reviewed. Build/sign APKs only through the repository's CI/release workflows.
