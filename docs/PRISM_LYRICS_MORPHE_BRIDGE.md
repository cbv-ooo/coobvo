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

- Morphe bridge source version: `0.1.4` (`morphe/prism-media-bridge/gradle.properties`).
- Core hook change: [constructor registration](https://github.com/cbv-ooo/coobvo/commit/deba631384e4a92a4a9c1c484cbbe0542b7fc0b3) registers the real Prism `LyricsState` as soon as it is initialized. The extension retains a bounded set of strong references (maximum eight) and refreshes live state on each bridge query. Setter and romanization/translation helper hooks are optional accelerators rather than hard requirements.
- App-side diagnostics: the bridge now emits `lyricsBridgeStatus` values `NO_MEDIA_ID`, `NO_LYRICS_STATE` and `NO_LYRICS_SNAPSHOT` before Prism reports its own `LOADING`, `WAITING`, `AVAILABLE` or `UNAVAILABLE` status. The island displays those separate states instead of collapsing them into “no lyrics”. The client continues retrying the “state not registered yet” and “snapshot not captured yet” cases.
- The bridge's existing source-status correction keeps an empty-but-pending Prism `Lyrics` object in `LOADING`, `WAITING` or `UNKNOWN`, rather than prematurely reporting `UNAVAILABLE`. A non-empty lyric list remains `AVAILABLE` immediately; an empty result is terminal only after Prism reports `settled` or `unavailable`.
- The 0.1.3 media snapshot features remain: Prism's own `SongWaves.get(songId)` 100-point scan, elapsed/remaining-time mode, full-track-length toggle, title alignment/size/weight, cover-corner settings and scrubber style/thickness/color/thumb. These are partial preference mappings, not a 1:1 Prism page clone.
- **Morphe bundle build passed:** run [#28](https://github.com/cbv-ooo/coobvo/actions/runs/42487611825), artifact [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42487611825/artifacts/13620077914) (source version 0.1.4).
- **Debug APK build and selected tests passed:** run [#175](https://github.com/cbv-ooo/coobvo/actions/runs/42487474819), artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42487474819/artifacts/13619808689), versionCode 154 / `0.1.50-dev3`. The run includes `PrismMediaBridgeClientTest`, `PrismLyricsSnapshotTest`, `PrismLyricsPainterTest`, `PrismPlayerPreferencesTest`, and the selected SI/iMode/cutout tests.
- These CI results prove source compilation, selected test fixtures, `.mpp` bundle generation and APK upload. They **do not** prove that the required fingerprints match the owner's installed Prism 1.8.2.1 / versionCode 58 APK, that System UI can query its provider route on-device, or that lyrics display on the S10e. The exact `.mpp` application and runtime remain unverified.
## Still required

1. Apply the current `0.1.4` .mpp to the exact Prism 1.8.2.1 / versionCode 58 APK. Confirm each remaining fingerprint individually; the removed `PrismRememberLyricsStateFingerprint` must no longer be requested.
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

1. First establish that Morphe v0.1.3 actually applies to the exact Prism 1.8.2.1 / versionCode 58 APK. Verify each remaining fingerprint, then confirm that System UI can query the local `/bridge/{songId}` route.
2. Confirm that a real Prism song produces a non-empty lyrics snapshot. Exercise loading, waiting, unavailable, static/unsynced, line-synced and word-synced cases before working on visual parity.
3. Complete the lyrics strip: active line, upcoming lines, exact timing, second voices, backing vocals, instrumental gaps, translation and romanization, with the user's selected text settings.
4. Expand the media card only after the data contract is reliable. Map player layout, title, controls, cover and progress/volume settings individually. Do not mark a preference complete merely because Morphe serializes it.
5. Keep the full Prism page-only experience separate from the compact/expanded island card where its behavior does not translate directly. Preserve opaque surfaces and do not add blur/frosting.
6. Record three independent statuses for every item: exported by Morphe, consumed/rendered by Coobvo, and verified on the S10e.

The owner-provided checklist is the feature scope for future Prism bridge and media-card work. The recorded build evidence proves only build/test/package success; it does not prove application to the installed Prism APK or working lyrics on the device.
