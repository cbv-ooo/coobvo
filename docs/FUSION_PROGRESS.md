# Fusion Progress

Last updated: 2026-11-08

## Repository and working rules
- Repository: `cbv-ooo/coobvo`.
- Work directly on `main`; do not create feature branches.
- Keep the Dynamic Island Java/Xposed implementation as the base. System UI code stays Java/Canvas; no Compose, Kotlin UI, or new UI dependencies.
- The only workflow is `.github/workflows/debug-apk.yml`. It runs `./gradlew --no-daemon assembleDebug` and uploads `DynamicIsland-debug-apk`; it does not run tests, lint, translation checks, signing, or release steps.
- Use CI only as compile/package evidence. Device appearance and touch behaviour remain unverified until tested on the Galaxy S10e.

## Implemented on main
- PR #1 was merged into `main` on 2026-11-07 (merge commit `72fcb47644094309ca58b2a99c93b3dad9ab0740`).
- The S10e layout anchors the compact pill to the right of the camera cutout and places both companion bubbles to the left.
- The second bubble has its own model slot, content layers, drawing, outline, hit target, swipe-to-minimize behaviour, lifecycle and audio handling.
- Frosted/blur/backdrop rendering was removed; card and transition surfaces are opaque. Legacy preference keys remain only for compatibility.
- The workflow was reduced to the single Debug APK build and artifact upload.

## Current work
- Added the six Material 3 Expressive spring tokens to `graphics/Motion`: spatial fast/default/slow and effects fast/default/slow.
- Added a `Spring(initial, Motion)` constructor and applied the named tokens to the shared island and card springs.
- Pending verification: GitHub Actions run #7 for commit `46e599255cdb217750d427aa2ba0037b30f0e5b6` was in progress at the last check. Do not call this change build-verified until that run succeeds.

## Remaining backlog
1. Continue Material 3 Expressive in Java/Canvas: role-based opaque colour tokens, larger rounded shapes and state morphing, then apply shared tokens consistently without introducing transparency or blur effects.
2. Add embedded Lottie animations only where the existing Java host supports them; generate data through `tools/lottie/*.py`.
3. Port selected Smart Island capabilities as independent Java source modules, each behind its own setting. Port logic only, not Compose/Hilt/DataStore/Accessibility overlay infrastructure.
4. Integrate LyricSync's Java providers and SYLLABLE/LINE lyrics into the media card below its seek/control row, synced to playback position with silent network failures and graceful timing fallback.
5. Prism hooks are blocked until the user provides the Prism source/API. Do not invent hook targets or APIs.
6. Ask the user to verify bubble spacing, placement, gestures and transitions on the S10e. No agent has performed this device test.

## Constraints
- No blur, frosted glass, backdrop capture, `RenderEffect`, or transparent surfaces.
- No new dependencies without a strong reason.
- Keep hooks isolated through `SafeHook`/`Refl` and individual failure handling.
- Generated resources and generated Java files must not be edited by hand.
- Preserve legacy preference keys `material_style` and `transition_blur` for old settings files.
