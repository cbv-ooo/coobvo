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

Instead, the patch hooks the actual `LyricsState` setters for `lyrics`, `source`, `loading` and `waiting`. Those calls retain a weak reference to the real state object, and each permitted local `/bridge/{songId}` query refreshes the snapshot from its getters, including the plain `settled` property. This is a source-level correction to the exact reported failure. It is not yet proof that all remaining fingerprints match the user's APK.

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

## Latest build evidence

Latest main revision: commit `59ff3585c427a4e422a5408b4c7d0ccd031e917e`; app `0.1.49-dev1` / versionCode `151`; Morphe bridge `0.1.2`.

- Debug APK run [#42474855696](https://github.com/cbv-ooo/coobvo/actions/runs/42474855696) passed the APK build, targeted test step and artifact upload for app source commit `cb76d6c75f102674cb6d6f94ecfb53ad2f2f29f9`. Artifact: [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42474855696/artifacts/13614564628).
- Latest Prism Morphe run [#42475138681](https://github.com/cbv-ooo/coobvo/actions/runs/42475138681) passed after expanding the exported player/lyrics settings. Artifact: [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42475138681/artifacts/13615111537).

These results prove that the app source compiles, the selected unit tests pass, and the Morphe bundle is generated. They do **not** prove that the new .mpp applies to the exact Prism 1.8.2.1 APK or that lyrics render correctly on the S10e.

## Still required

1. Apply the new `0.1.2` .mpp to the exact Prism 1.8.2.1 / versionCode 58 APK. Confirm each remaining fingerprint individually; the removed `PrismRememberLyricsStateFingerprint` must no longer be requested.
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
