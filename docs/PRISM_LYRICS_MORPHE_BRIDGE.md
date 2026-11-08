# Prism Media / Morphe Bridge

Last updated: 2026-11-08.

This document records the exact Prism target, patch hooks, coobvo rendering path, build evidence and the remaining APK/device checks. **Building an `.mpp` does not prove that it applies to the installed Prism APK.**

## Ownership and target

Prism is the sole source of truth for Prism-session media and lyrics. Coobvo must not run LyricSync or another provider cascade for `com.prism.music`, must not make a second lyrics search, and must not invent provider text or timestamps.

The source inspected is [Yigder/Prism commit 1abbed19d374b84e6cda4e1c63db937d1f70befd](https://github.com/Yigder/Prism/tree/1abbed19d374b84e6cda4e1c63db937d1f70befd):

- Package: `com.prism.music`
- Version: `1.8.2.1`, versionCode `58`
- Prism's Media3 `Song.toMediaItem()` uses the original `Song.id` as `mediaId` and also serializes `prism.song` in metadata extras.
- `LyricsState` owns the selected song, stored lyrics, provider, loading/waiting/settled/unavailable state.
- Prism's `Lyrics` / `LyricLine` model contains original line and word timing, background vocal word timing, second-voice alignment and instrumental gaps.
- Prism separately computes the actual romanized lines and translations in its player Compose helpers. These are not the same fields as the stored base `Lyrics` object.
- `LyricsLookPrefs` and player settings control the lyric style, animation, type, colours, font, size, spacing, romanization, translation, backing vocals and other effects.

## Morphe patch and the owner's fingerprint failure

Source lives in `morphe/prism-media-bridge/`; its build is `.github/workflows/prism-morphe.yml`.

The owner supplied this actual Morphe error from Prism 1.8.2.1:

```
Failed to match the fingerprint: dev.coobvo.prismmedia.PrismRememberLyricsStateFingerprint
```

That fingerprint targeted Prism's `rememberLyricsState` Compose helper. It did not match the installed APK, so it has been **removed**. The current patch does not require `PrismRememberLyricsStateFingerprint` or hook that composable.

The patch now hooks the primary `LyricsState` constructor (`<init>(LyricsRepository, Song, CoroutineScope)`) and registers the fully initialized state object before Prism starts its asynchronous lookup. The extension keeps a bounded set of up to eight strong state references, keyed by Prism's original `Song.id`, and each permitted local `/bridge/{songId}` query refreshes the live getters, including `lyrics`, `loading`, `waiting`, `settled` and computed `unavailable`. This removes the previous dependency on a setter having already run and avoids losing the object when its player Compose page is recomposed or disposed. The four setter hooks are optional accelerators; the romanization and translation helper hooks are also optional, so a mismatch in one of them no longer prevents the base lyrics bridge from being installed. The constructor, MediaItem and ArtworkProvider fingerprints remain required for the core route.

The patch also hooks the return values of Prism's existing `rememberRomanized` and `rememberLyricTranslation` helpers. It exports the actual arrays those helpers return; it does not call their provider engines again or synthesize replacement lines.

The patch:

1. Decorates Prism's Song-to-MediaItem and browse MediaItem results with a versioned local bridge URI while preserving the original data.
2. Intercepts only the `/bridge/{songId}` route in Prism's existing `ArtworkProvider.query()`; ordinary artwork requests continue through Prism.
3. Exposes Prism's in-memory stream diagnostics and player/queue state where available.
4. Snapshots the real LyricsState, its provider/status and original lyric data, plus the actual romanized/translated helper results when Prism has produced them.
5. Exports the resolved lyric-look preferences, song-specific romanization override, and the active Prism player/lyrics settings snapshot. The settings snapshot includes player layout/deck/buttons/hidden parts, title and time presentation, visualizer, background/backdrop options, mini-player and immersive-player switches, lyrics-on-player/source controls, source order/disabled sources, mobile-data behavior, translation/romanization switches, lyrics font and alignment, and relevant behavior toggles. Enum values and ordered lists are serialized as names/arrays so the receiver does not need Prism classes.
6. Returns snapshots only to callers belonging to Android System UI. The URI is local; this patch adds no external lyric endpoint or network provider.

**Important boundary:** exporting a setting does not mean every corresponding Prism player-page feature is rendered in coobvo. The expanded card consumes the lyric-strip subset. Player page layout, deck arrangement, visualizer, backdrop and immersive-page options are exported for explicit future mapping/diagnostics; they must not be claimed as visually reproduced until implemented and tested.

## Coobvo consumer and expanded-card rendering

The Java consumer is in:

- `app/src/main/java/dev/android/dynamicisland/island/source/PrismMediaBridgeClient.java`
- `app/src/main/java/dev/android/dynamicisland/island/source/MediaSource.java`
- `app/src/main/java/dev/android/dynamicisland/island/lyrics/MediaLyrics.java`
- `app/src/main/java/dev/android/dynamicisland/island/view/card/MediaBody.java`
- `app/src/main/java/dev/android/dynamicisland/island/view/card/PrismLyricsPainter.java`

The consumer normally reads the versioned URI from media metadata. It also falls back to Prism's actual MediaSession `mediaId` if the Android compat metadata conversion drops Prism's custom extras. The original Song ID is used as the bridge route key.

For a Prism session, the expanded media card reads only the bridge result; the independent LyricSync lookup is suppressed. Other media apps retain their separate LyricSync path.

The current Canvas mapping includes:

- Prism's current-line selection rule (latest line whose timestamp has passed), with the line/word timestamps copied from Prism.
- Prism's line-to-word timing formula for line-synced lyrics only when its selected `wordByWordNow` preference asks for it. Unsynchronized/static lyrics are not assigned karaoke timing.
- The five lyric motion choices: GROW/PRISM, FILL/KARAOKE, SWEEP, TYPEWRITER and NONE/CALM, plus word-by-word behavior.
- Current-line wrap limit (one or two lines), alignment, uppercase, weight, size scale, spacing and text tracking.
- Prism's sung/other/extra colour selection, alpha, extra placement/size/italic, timed background vocals and gap markers.
- Actual romanized/translated lines returned by Prism's helpers, including UNDER/INSTEAD romanization behavior and the translation toggle.
- Prism's lyrics-font choice, app-font choice and a guarded attempt to load its bundled or imported font, with a system-font fallback if the System UI process cannot access it.

The expanded card is not the Prism lyrics page. Page-only controls such as `ahead`, `position` and the full-screen/page background are not applied to the inline media-card row. Some appearance details are still approximations rather than pixel-identical Compose rendering: for example, Spectrum/Cover-derived fills are represented through the media accent, and halo/glow and every background/style detail are not yet reproduced exactly. The project also deliberately keeps card surfaces opaque; no frosted or blurred card background is added. Do not describe the renderer as fully 1:1 yet.

## Previous build evidence (Morphe bridge v0.1.2)

The following records the last documented v0.1.2 builds; it is historical, not build evidence for v0.1.3.

- Debug APK run [#42474855696](https://github.com/cbv-ooo/coobvo/actions/runs/42474855696) passed the APK build, targeted test step and artifact upload for app source commit `cb76d6c75f102674cb6d6f94ecfb53ad2f2f29f9`. Artifact: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42474855696/artifacts/13614564628).
- Latest Prism Morphe run [#42475138681](https://github.com/cbv-ooo/coobvo/actions/runs/42475138681) passed after expanding the exported player/lyrics settings. Artifact: [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42475138681/artifacts/13615111537).

These results prove that the app source compiles, the selected unit tests pass, and the Morphe bundle is generated. They do **not** prove that the new .mpp applies to the exact Prism 1.8.2.1 APK or that lyrics render correctly on the S10e.

## Current Prism bridge update (2026-11-08)

- Morphe bridge source version: `0.1.5` (`morphe/prism-media-bridge/gradle.properties`).
- Core hook change: [constructor registration](https://github.com/cbv-ooo/coobvo/commit/deba631384e4a92a4a9c1c484cbbe0542b7fc0b3) registers the real Prism `LyricsState` as soon as it is initialized. The extension retains a bounded set of strong references (maximum eight) and refreshes live state on each bridge query. Setter and romanization/translation helper hooks are optional accelerators rather than hard requirements.
- App-side diagnostics: the bridge now emits `lyricsBridgeStatus` values `NO_MEDIA_ID`, `NO_LYRICS_STATE` and `NO_LYRICS_SNAPSHOT` before Prism reports its own `LOADING`, `WAITING`, `AVAILABLE` or `UNAVAILABLE` status. The island displays those separate states instead of collapsing them into “no lyrics”. The client continues retrying the “state not registered yet” and “snapshot not captured yet” cases.
- The bridge's existing source-status correction keeps an empty-but-pending Prism `Lyrics` object in `LOADING`, `WAITING` or `UNKNOWN`, rather than prematurely reporting `UNAVAILABLE`. A non-empty lyric list remains `AVAILABLE` immediately; an empty result is terminal only after Prism reports `settled` or `unavailable`.
- The 0.1.3 media snapshot features remain: Prism's own `SongWaves.get(songId)` 100-point scan, elapsed/remaining-time mode, full-track-length toggle, title alignment/size/weight, cover-corner settings and scrubber style/thickness/color/thumb. These are partial preference mappings, not a 1:1 Prism page clone.
- **Morphe bundle build passed:** run [#42493182714](https://github.com/cbv-ooo/coobvo/actions/runs/42493182714), artifact [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42493182714/artifacts/13622755240) (source version 0.1.5).
- **Latest Debug APK build and selected tests passed:** run [#42494314858](https://github.com/cbv-ooo/coobvo/actions/runs/42494314858), artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42494314858/artifacts/13622562710), source commit `4b3fbe0b8101f89e6ac57aee31b044f664ded6c9`. The run includes `PrismMediaBridgeClientTest`, `PrismLyricsSnapshotTest`, `PrismLyricsPainterTest`, `PrismPlayerPreferencesTest` (including deck-order cases), and selected SI/iMode/cutout tests.
- These CI results prove source compilation, selected test fixtures, `.mpp` bundle generation and APK upload. They **do not** prove that the required fingerprints match the owner's installed Prism 1.8.2.1 / versionCode 58 APK, that System UI can query its provider route on-device, or that lyrics display on the S10e. The exact `.mpp` application and runtime remain unverified.
## Still required

1. Apply the current `0.1.5` .mpp to the exact Prism 1.8.2.1 / versionCode 58 APK. Confirm each remaining fingerprint individually; the removed `PrismRememberLyricsStateFingerprint` must no longer be requested.
2. Install the patched Prism APK and coobvo debug APK together. Confirm the /bridge route is queried by System UI and the correct Song ID is returned.
3. Verify that the actual stored lyric lines, romanization and translation helper results reach the expanded card. Exercise loading, waiting, unavailable, static/unsynced, line-synced and word-synced cases.
4. Verify pause/resume, seeking, track changes, word timing, overlapping/second-voice lines, background vocals and gaps.
5. Compare the available Prism look controls against the expanded card, record remaining visual differences, and finish any needed style/font/color mapping.
6. Verify expanded-to-compact animation, lyrics placement and all rendering on the owner's Galaxy S10e.

## References

- [Prism source commit](https://github.com/Yigder/Prism/tree/1abbed19d374b84e6cda4e1c63db937d1f70befd)
- [MediaItems.kt](https://github.com/Yigder/Prism/blob/1abbed19d374b84e6cda4e1c63db937d1f70befd/app/src/main/java/com/prism/music/playback/MediaItems.kt)
- [PlayerLyrics.kt](https://github.com/Yigder/Prism/blob/1abbed19d374b84e6cda4e1c63db937d1f70befd/app/src/main/java/com/prism/music/ui/player/PlayerLyrics.kt)
- [Lyrics.kt](https://github.com/Yigder/Prism/blob/1abbed19d374b84e6cda4e1c63db937d1f70befd/app/src/main/java/com/prism/music/data/lyrics/Lyrics.kt)
- [RomanLyrics.kt](https://github.com/Yigder/Prism/blob/1abbed19d374b84e6cda4e1c63db937d1f70befd/app/src/main/java/com/prism/music/ui/player/RomanLyrics.kt)
- [Morphe patch template](https://github.com/MorpheApp/morphe-patches-template)
- [Morphe patcher](https://github.com/MorpheApp/morphe-patcher)

## Owner-requested Prism lyrics and player parity scope

Owner-provided requirements recorded on 2026-11-08. This is a requirements and implementation-status record, **not a claim that these features are complete**.

### Lyrics preferences

| Requested control or behavior | Current bridge / island status |
|---|---|
| Start from / lyric position | Prism's `ahead` and `position` values are included in the preference snapshot. The inline island strip does not yet reproduce the full-page start/position behavior. |
| Animation | Prism style/motion preferences are exported. The Canvas strip maps the five lyric motion modes; visual parity still needs device comparison. |
| Word-by-word on every line | The bridge exports Prism's real word timing and the word-by-word preference. The renderer uses real timing where present and only applies the existing Prism-derived line-timing formula when Prism's preference enables it. Static/unsynced lyrics must not receive invented karaoke timing. |
| Text alignment, size, weight and line spacing | The relevant lyric-look values are exported and a subset is mapped by the Canvas strip. Verify each control against Prism's live settings. |
| Sung-line and other-line colors; dim-to-bright behavior | Sung/other color, alpha and motion-related values are exported and partly rendered. Custom, accent, cover and spectrum colors still need parity checks. |
| Focus blur | The preference value is exported where Prism provides it. Coobvo's standing constraint prohibits blur/frosted/backdrop effects, so this setting must not introduce a blurred or translucent island surface. |
| Lines to come | Implemented in the Canvas strip for static and synced lyrics. It reads Prism's `ahead` setting and shows upcoming lines; the narrow island card caps the preview to three lines. Confirm line count, spacing and clipping on the S10e. |
| Background behind lyrics and effects | Relevant background/look values are exported, but the expanded card does not reproduce Prism's full lyrics-page background/effects. The island card remains opaque. |
| Background vocals and instrumental breaks | Prism's background-vocal timing, alignment and gap data are exported. The painter already has backing-vocal and gap-marker handling; verify overlap and timing on-device. |
| Translation and romanized lines | The bridge hooks Prism's own translation and romanization helper results. It must use those actual results, never a second lookup or generated substitute. |
| Translation/romanization size and color | Related extra-text size/color/placement settings are included in the look snapshot and partly mapped. Verify separate translation and romanization presentation. |

### Player preferences

| Requested control or behavior | Current bridge / island status |
|---|---|
| Expanded-card layout; player contents; content under title; button row | Player layout/deck/button/hidden-part settings are exported. The island still uses its own fixed Canvas card structure; these settings are not yet a complete layout adaptation. |
| Player background and button appearance | Background and player control settings are exported in part. Prism's full player appearance is not reproduced in the island. |
| Progress-bar style/color, playhead knob and scanned waveform | Prism's scrubber style, color, thickness and thumb are exported and consumed; the scanned 100-point WAVE outline is now exported and rendered when present, with a plain-line fallback for unscanned tracks. Spectrum/Cover colors are still approximations, and Prism's RIBBONS animation, crossfade-shaped progress and volume bar are not yet reproduced. |
| Cover corners, edge-to-edge cover, animated cover and fill-player behavior | Player corner radius and circle/square/rounded equivalents are mapped. FULL cover size currently maps to square corners only; the fixed island artwork slot is not widened. Prism's other custom shapes, animated cover and full-bleed page fill are not reproduced. |
| Title size, weight and alignment; scrolling; artist name; times under progress; visualizer | Title size/weight/alignment and Prism's time/remaining-time selection are consumed. Title marquee scrolling and the full player visualizer/page are not yet reproduced; Prism's visualizer data remains available through the existing snapshot. |

### Required implementation order and acceptance criteria

1. First establish that Morphe v0.1.5 actually applies to the exact Prism 1.8.2.1 / versionCode 58 APK. Verify each remaining fingerprint, then confirm that System UI can query the local `/bridge/{songId}` route.
2. Confirm that a real Prism song produces a non-empty lyrics snapshot. Exercise loading, waiting, unavailable, static/unsynced, line-synced and word-synced cases before working on visual parity.
3. Complete the lyrics strip: active line, upcoming lines, exact timing, second voices, backing vocals, instrumental gaps, translation and romanization, with the user's selected text settings.
4. Expand the media card only after the data contract is reliable. Map player layout, title, controls, cover and progress/volume settings individually. Do not mark a preference complete merely because Morphe serializes it.
5. Keep the full Prism page-only experience separate from the compact/expanded island card where its behavior does not translate directly. Preserve opaque surfaces and do not add blur/frosting.
6. Record three independent statuses for every item: exported by Morphe, consumed/rendered by Coobvo, and verified on the S10e.

The owner-provided checklist is the feature scope for future Prism bridge and media-card work. The recorded build evidence proves only build/test/package success; it does not prove application to the installed Prism APK or working lyrics on the device.

## Latest implementation addendum (2026-11-08)

This addendum supersedes the earlier preference-status rows above where they conflict. The implementation is on `main`; CI build/test evidence and exact-device application remain separate verification steps.

### Background lyrics lookup

- The Morphe patch now hooks Prism's PlaybackService.onMediaItemTransition(MediaItem, reason) rather than relying on the player Activity or the Compose lyrics screen.
- The hook calls the extension's requestBackgroundLyrics(service, mediaItem). It converts the new MediaItem back to Prism's own Song, obtains the existing application LyricsRepository and CoroutineScope, constructs Prism's LyricsState, then calls Prism's loadAuto(savedOnly) method.
- savedOnly follows the same mobile-data rule as Prism's player UI: when the connection is metered and lyricsOnMobileData is disabled, it checks stored lyrics only and does not start a network search. Otherwise it uses Prism's normal provider selection, source order and saved fallback.
- Song IDs are deduplicated. The hook is a no-op for null media items and episodes, and failures are caught so they cannot interrupt playback.
- This is source-level implementation only until the Morphe workflow passes and the new patch is applied to the exact Prism 1.8.2.1 / versionCode 58 APK. The phone test must confirm that a track transition fetches lyrics with Prism fully closed.

### Lyrics strip changes

- Synced lyrics now retain the two lines before the active line. The newest past line moves upward as the next active line fades/slides in; the second past line remains dimmer. The transition is 340 ms.
- The island strip base font size is now 17 dp (before Prism `sizeScale` and `textScale`), with 20 dp baseline spacing multiplied by Prism's line-spacing setting. The two history lines receive stronger opacity (newest 0.48–0.70, older 0.36), and measured card height reserves space for the current line, both history lines, enabled previews/extras and the no-preferences fallback.
- The painter applies the configured focus blur to dimmed past/upcoming text and supports the exported lyric strip backing choices: DARK, COLOR (including a two-color gradient), COVER_COLOR and an accent-tinted approximation for BLUR/CANVAS.
- Prism's actual stored timed lyrics and helper-produced romanized/translated lines remain authoritative. Static lyrics do not receive fabricated word timing.
- The strip background for BLUR/CANVAS is only an accent-colour approximation. The bridge does not currently export Prism's live blurred/animated Canvas bitmap, so this is not pixel-identical to Prism. No frosted or translucent card surface is added.

### Player settings now consumed by the island

The preference parser reads the nested playerPreferences object and its view, type and shapes members, with a fallback for older flattened snapshots. The expanded media card now consumes:

- Layout/deck visibility for the lyrics strip, progress row, transport row, optional volume row and custom-button row. MINIMAL suppresses the extra output line; COVER/VINYL/CASSETTE and the selected cover size increase the artwork slot.
- Cover corner radius/shape equivalents and the selected standard/large/full cover size. Full cover is represented by a wider artwork slot, not a true full-bleed, edge-to-edge video surface.
- Title alignment, size, weight and automatic marquee scrolling; artist text and optional playing-from/quality metadata.
- Elapsed/remaining/track-length display, scrubber thickness/style/color and knob style. Prism's scanned 100-point waveform is used for WAVE when available; otherwise the normal progress bar remains.
- Transport style (glyph/button/outlined play), custom-button shape, and the grouped bottom-row capsule preference.
- Optional volume bar and the volume-matches-progress style. Visibility follows Prism's `volumeControl` override, and the fill uses Prism's `appVolume` when Prism owns volume; phone mode uses the existing session-volume snapshot.
- The crossfade-start mark and the separate crossfade-bar blend/taper toggle, when Prism supplies a planned crossfade interval.
- Prism's sampled visualizer bands when the setting is enabled. The current island rendering is a compact bar visualization rather than Prism's full page-specific BARS/WAVE/RING/PULSE/GLOW layouts.

### Explicitly not full parity yet

The playerDeck preference now controls the order of the supported island rows (STRIP, SCRUBBER, TRANSPORT and VOLUME), and seek/button hit targets stay aligned with moved rows. Prism's BUTTONS deck row is deliberately not rendered yet: it is the separate app-level PlayerActionRow, not the Android MediaSession custom actions. Those SessionButtons remain beside transport. Prism's `playerButtons` order is exported, but its app-level Lyrics/Video/Queue/Sleep/Like/etc. callbacks are not yet bridged. Canvas/animated cover playback, full-bleed player fill, every custom cover silhouette, Prism's animated RIBBONS/spectrum shader, and the full visualizer styles remain incomplete. The volume row follows the selected Prism/phone value for display, but is still not wired to a touch volume controller.

### Verification state

- Morphe bridge bundle v0.1.5 passed run [#42495119390](https://github.com/cbv-ooo/coobvo/actions/runs/42495119390), with artifact [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42495119390/artifacts/13622709484).
- Debug APK build and selected unit tests passed on source commit `62ad50b48db32fe782319b03d5bbb330eaad7522` in run [#42494944291](https://github.com/cbv-ooo/coobvo/actions/runs/42494944291). Artifact: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42494944291/artifacts/13622773402). The run includes `PrismMediaBridgeClientTest`, `PrismLyricsSnapshotTest`, `PrismLyricsPainterTest` (including larger text, 20 dp spacing, history and fallback-height checks) and `PrismPlayerPreferencesTest`, plus selected SI/iMode/cutout tests.
- These results prove bundle generation, app compilation and selected test fixtures. They do **not** prove that the patch applies to the exact installed Prism 1.8.2.1 / versionCode 58 APK or that its provider route and island rendering work on the Galaxy S10e.
- Real-device verification is still required for background lookup with Prism closed, live preference changes during playback, 3-line lyrics visibility/transitions, and S10e card sizing.

## Follow-up implementation and patcher correction (2026-11-08)

### Morphe patcher failure on Prism 1.8.2.1

The reported InlineSmaliCompiler.kt:68 / Collection is empty failure was caused by the injected background-hook instruction using the short invoke form:

    invoke-static { p0, p1 }, ...

On Prism 1.8.2.1's large PlaybackService.onMediaItemTransition register frame, the parameter registers can exceed v15. Morphe's inline Smali compiler rejected that short-form instruction and then failed while resolving an empty compiled instruction list.

The hook now uses the register-range form:

    invoke-static/range { p0 .. p1 }, ...

The receiver and MediaItem are consecutive one-word parameters, so the range form is valid regardless of their final register numbers. The updated Morphe .mpp workflow passed for commit 6ee387687b9f10d2bb4eee7d01815ab5d939df2f:
https://github.com/cbv-ooo/coobvo/actions/runs/42496315980

This verifies bundle generation for the patch; it does not by itself verify applying the patch to the exact APK or background lookup on the phone.

### Additional lyrics preference wiring

- lyricsScale is now multiplied with both the preset sizeScale and the application textScale for the active line, dimmed lines, upcoming lines, translation and romanized text.
- Instrumental/interlude lines now call the shared gap-marker renderer, honoring Prism's selected gapMark style (dots or music note) instead of always displaying a hard-coded note label.
- The PlayerPart.LYRIC_LINE visibility rule is now applied together with the selected STRIP deck row. Hiding the lyric-line part therefore removes the strip, even when STRIP remains selected in the deck.
- The strip's BLUR background uses the current media-session cover blurred by Android's RenderEffect on API 31+. Older Android releases use a dimmed cover fallback.
- The strip's CANVAS choice uses the still artwork supplied by MediaSession. Prism's live Canvas/video frame is not available through the current media-session artwork contract, so animated Canvas remains a limitation.

### Additional player preference wiring

- Title weight now follows Prism's actual LIGHT/REGULAR/BOLD PlayerTitleWeight values. LIGHT inherits the global app TypeLook.weight; global type letter spacing is also applied.
- The player parser uses Prism's actual defaults for the button capsule, navigation-band setting and default app-level button selection.
- The expanded card respects the separate player parts for cover, title, artist, previous/next, progress, times, lyric line, volume, output, quality, handle and crossfade marker.
- MINIMAL layout omits previous/next and the two session-action flank buttons, leaving play/pause.
- VINYL uses a circular sleeve with optional grooves and tonearm. CASSETTE uses a cassette-style cover treatment. Pause-shrink and the selected cover silhouette are applied.
- Player BLUR uses the current cover as its blurred background on API 31+. Mesh speed/intensity, gradient depth and backdrop dimming are applied where those modes are selected.
- Phone-vs-Prism app volume source is respected by the volume row; it remains a display-only row and does not currently accept touch input.

### Remaining boundaries (do not mark as complete)

- Prism's PlayerButton / PlayerActionRow is a separate app-level callback system. Its selected buttons and order are exported, but the island does not yet render or invoke those app-level callbacks. Android SessionButtons are different and remain the media-session transport actions. Do not substitute them for PlayerButton.
- DeckPart.BUTTONS, PlayerPart.BUTTONS, BEST_PART and SHUFFLE_REPEAT still need a proper app-action bridge before the selected app-level button row can be considered functional.
- blendCovers requires the next track's cover image; the bridge currently exports crossfade timing/IDs but not the next cover bitmap. The crossfade bar shape and start marker can be displayed, but the two-cover dissolve is incomplete.
- fullBleedArtwork currently enlarges the cover slot; it is not a true edge-to-edge full-screen player surface. hideStatusBarPlayer and playerNavBand belong to Prism's full-screen player window, not the Dynamic Island card.
- canvasEnabled / canvasFillsBackground use the still artwork snapshot as a background fallback. They do not reproduce Prism's animated Canvas playback.
- Device checks remain required for background loading with Prism closed, live preference changes, three-line lyric animation, and card sizing on the Galaxy S10e.

### Verification

The /range patcher correction has a successful Morphe bundle-generation workflow as linked above. The app-source changes in this follow-up need a fresh Debug APK build and unit-test run on the commit containing those changes. Do not infer app build success from the Morphe workflow.

## Debug APK regression verification (2026-11-09)

- The latest failed Debug APK runs, [#42499130028](https://github.com/cbv-ooo/coobvo/actions/runs/42499130028) and [#42499471470](https://github.com/cbv-ooo/coobvo/actions/runs/42499471470), both compiled the APK successfully but stopped at `PrismLyricsPainterTest.globalLyricsScaleMultipliesPresetAndAppTextScale`. The test used Android `org.json.JSONObject` without the Robolectric runner used by the neighboring Prism bridge, lyrics snapshot and player-preference tests.
- `PrismLyricsPainterTest` now uses `RobolectricTestRunner` with `@Config(sdk = 35)`, matching those existing tests. The production scaling formula was not weakened or changed: the active lyrics size still multiplies Prism preset `sizeScale`, application `textScale` and global `lyricsScale`.
- **Debug APK and selected regression tests passed:** [run #42501239625](https://github.com/cbv-ooo/coobvo/actions/runs/42501239625), commit `23301950800c9df24870062eb3ad62b0f0bdde17`. The run completed `assembleDebug`, the selected Smart Island/Prism/iMotion/cutout unit-test task, and artifact upload. The `app-debug.apk` artifact is [available from the run](https://github.com/cbv-ooo/coobvo/actions/runs/42501239625/artifacts/13624704268).
- This validates the current source build and selected automated tests. It does **not** replace applying the Morphe `.mpp` to the exact Prism 1.8.2.1 / versionCode 58 APK, testing background lyrics lookup with Prism closed, or verifying the updated lyrics/player preferences on the Galaxy S10e.
