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
- The normal stack and iMode paths now both paint expanded cards. iMode previously returned through its own renderer without calling the card painter, leaving compact activity nodes visible but not the expanded notification body.
- The second companion bubble now uses the same edge-to-edge gap as the first. The overlay touch-region calculation includes the second bubble, so its hit target can reach the existing index-1 gesture handling.
- S10e detection now checks the overlay DisplayCutout, then calls the framework resource-approximation API using its actual current signature, then the older overload. If neither returns geometry on an SM-G970/beyond0 device, the model fallback is used. Camera alignment still needs confirmation on the physical S10e.
- Battery rendering is shared through `BatteryGauge`: compact pill, battery notifications, expanded battery cards and battery event glyphs now use the same gauge geometry and semantic charge tint.
- Expanded cards use a single opaque semantic surface-container fill with solid outline-variant edges. No frosted or backdrop rendering is used.
- Module-owned Canvas glyphs and embedded Lottie shapes share one monochrome tint and rounded stroke caps/joins. Original app-provided notification logos and album artwork are intentionally preserved.
- The settings hub exposes Smart Island, Dynamic App Settings and Material 3 Expressive. The Smart Island page reports currently available capabilities, gives real settings controls, and separates those from the Smart Island reference features still not ported.
- LyricSync has an initial working LRCLIB adapter: one serialized background worker, cached track queries, synced-LRC parsing, and current/next lyric lines below the media controls. Plain unsynced text is not presented as synchronized lyrics. KRC/YRC syllable timing and the other LyricSync providers are not yet integrated.
- Prism integration and UI references have been removed completely from the current app. No Prism hooks are merged.
- Debug installs now use application ID `dev.android.dynamicisland.coobvo`, version code 120 / version name `0.1.18-dev1`, and a stable debug-only signing key. This intentionally installs beside the older `dev.android.dynamicisland` package rather than attempting an update signed by a different certificate. Release package identity and release signing configuration remain unchanged. Future Debug APKs must keep incrementing `versionCode` and reuse this same debug signer.

## Material 3 Expressive progress
- Added the six shared spring tokens to `graphics/Motion`: spatial fast/default/slow and effects fast/default/slow.
- Applied the named tokens to shared island springs, card springs, i-Mode nodes/pages and Director pill/fade/stack transitions.
- Added opaque semantic colour roles in `graphics/Colors`: surface, surface containers, on-surface, on-surface-variant, on-primary-container and outline-variant.
- Card fills use fully opaque surface-container roles; the media play control uses an opaque primary container derived from artwork accent.
- Battery icons now use the same shared gauge path and semantic tint across compact, notification and expanded presentations.
- Module glyphs and Lottie vector paths use semantic tinting with rounded terminals/joins. Third-party icons and album artwork are preserved.
- Increased the default expanded-card corner radius from 28 dp to 32 dp. The custom roundness setting still overrides it.
- The existing `Shapes`/`NotchShape` geometry remains the basis for pill and card transitions; no Compose/AndroidX UI dependency was added.

## CI evidence
- Runs #18–#22 passed for the prior card renderer, solid M3 Expressive surface/icon pass, settings hub, Prism status-page deletion precursor and fallback pictogram updates.
- This corrective pass includes stable debug identity/signing, iMode card drawing, dual-bubble spacing/touch routing, shared battery rendering, the correct S10e framework cutout call, Smart Island settings, and the initial LyricSync LRCLIB integration. The latest Debug APK run is the acceptance check for those changes; update this section with the exact run and artifact after it succeeds.
- A successful workflow proves compilation, packaging and artifact upload only. It does not verify installation on the Galaxy S10e, camera alignment, touch behaviour or live lyric timing on-device.

## Remaining backlog
1. Verify the new debug package installs and upgrades in place across subsequent builds; enable the new `dev.android.dynamicisland.coobvo` module and System UI scope in LSPosed.
2. Verify expanded notifications, iMode animations, both bubble touch targets/spacing and automatic camera-hole alignment on the Galaxy S10e.
3. Port remaining Smart Island features: notification history, rotating idle widgets and dedicated quick-launch controls.
4. Extend LyricSync with KRC/YRC word timing and additional providers, retaining silent network failure and throttled background requests.
5. Finish remaining module-owned icon geometry with shared Material 3 Expressive primitives; keep app artwork and supplied notification logos intact.

## Constraints
- No blur, frosted glass, backdrop capture, `RenderEffect`, or transparent surfaces.
- No new dependencies without a strong reason.
- Keep hooks isolated through `SafeHook`/`Refl` and individual failure handling.
- Generated resources and generated Java files must not be edited by hand.
- Preserve legacy preference keys `material_style` and `transition_blur` for old settings files.
