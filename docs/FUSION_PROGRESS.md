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
- The S10e layout anchors the compact pill to the right of the camera cutout and places both companion bubbles to the left. The latest code now tries the framework cutout, Samsung's framework resource approximation and a model-specific S10e fallback when One UI exposes no cutout. The estimate still requires real-device validation.
- The second bubble has its own model slot, content layers, drawing, outline, hit target, swipe-to-minimize behaviour, lifecycle and audio handling. The latest `Director.update` also fills free bubble slots from the most recently minimized items.
- Fixed the expanded-card render path: `IslandView.onDraw` now iterates the active card stack and calls `StackPainter.drawCard`. Previously the card painter existed but the runtime view never called it, explaining why opening a notification could show no expanded card.
- Expanded cards now use a single opaque semantic surface-container fill with solid outline-variant edges. The vertical gradient rim and black-to-card morph were removed; no frosted or backdrop rendering is used.
- Module-owned Canvas glyphs and embedded Lottie shapes now share one monochrome tint and rounded stroke caps/joins. App-provided notification logos and album artwork remain original content rather than being destructively redrawn.
- The settings home now exposes direct entries for Smart Island status, Dynamic App Settings, Material 3 Expressive and Prism status. A settings live-preview sample displays two companion bubbles to the left of a right-anchored pill.
- Smart Island has not been wholesale imported: its Compose/Hilt/overlay/service architecture is incompatible with the Java/Canvas System UI runtime. The current Smart Island page states which reference features still need individual Java ports.
- Prism has not been merged. The uploaded archives do not contain the intended Prism source. A public repository named `hongducdev/prism` exists, but it has not been assumed to be the requested source; review its specific hook targets and AGPL-3.0 licensing before any code is adapted.
- The workflow remains limited to the Debug APK build and artifact upload.

## Material 3 Expressive progress
- Added the six shared spring tokens to `graphics/Motion`: spatial fast/default/slow and effects fast/default/slow, derived from the supplied stiffness/response table.
- Applied the named tokens to the shared island springs, card springs, i-Mode nodes/pages and the Director's pill, fade and stack-release transitions.
- Added opaque semantic colour roles in `graphics/Colors`: surface, surface containers, on-surface, on-surface-variant, on-primary-container and outline-variant.
- Card fills now use a fully opaque surface-container role. The media play control uses an opaque primary container derived from the current artwork accent; its glyph uses the on-primary-container role.
- The media app tile uses an opaque surface-container role; title and artist text use semantic on-surface roles.
- Increased the default expanded-card corner radius from 28 dp to 32 dp. The user's custom card-roundness setting still overrides the default.
- Module glyphs and Lottie vector paths use semantic tinting with rounded terminals/joins. Original third-party app logos and media artwork are intentionally preserved.
- The existing `Shapes`/`NotchShape` path geometry and card/pill state morphing remain the basis for shape transitions; no AndroidX/Compose/UI dependency was added.

## CI evidence
- `Debug APK` run #17 succeeded for documentation commit `50de53b2d7f2b67fba909ea3f22f2a5a807bfe7f`.
- Run #18 succeeded for `90b97b1433f23d745cf52cac066bb88e309b7c36` (expanded-card render path, two bubble slots and S10e cutout fallback).
- Run #19 succeeded for `64010ff0e6745719cb32a8544b6423cf65d96a58` (opaque M3 Expressive card surfaces and rounded monochrome glyph/Lottie strokes).
- Run #20 succeeded for `8ddeaf6db97a8b048ceab1f077583b74ea37a005` (settings hub, integration-status pages and dual-bubble preview). The Debug APK build and artifact upload passed. Artifact: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42401969214/artifacts/13584809625).
- A successful workflow proves compilation, packaging and artifact upload only. It does not verify the S10e's visible cutout alignment, second-bubble gestures/position or notification card rendering on-device.

## Remaining backlog
1. Verify the expanded notification card, camera anchor and both bubbles on the Galaxy S10e, including right-side anchoring and gestures.
2. Port selected Smart Island pure-logic features as independent Java source modules, each behind its own setting. Do not copy Compose/Hilt/DataStore/Accessibility overlay infrastructure.
3. Integrate LyricSync's Java providers and SYLLABLE/LINE lyrics into the media card below its seek/control row, synced to playback position with silent network failures and graceful timing fallback.
4. Confirm the intended Prism source/API, then review only the compatible hook features and their licence terms before porting.
5. Continue Material 3 Expressive adoption for remaining module-owned icons/control surfaces, keeping all surfaces opaque and gradient-free where they are used as icons or card fills.

## Constraints
- No blur, frosted glass, backdrop capture, `RenderEffect`, or transparent surfaces.
- No new dependencies without a strong reason.
- Keep hooks isolated through `SafeHook`/`Refl` and individual failure handling.
- Generated resources and generated Java files must not be edited by hand.
- Preserve legacy preference keys `material_style` and `transition_blur` for old settings files.
