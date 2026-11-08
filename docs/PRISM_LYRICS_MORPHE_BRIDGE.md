# Prism Media / Morphe Bridge

Last updated: 2026-11-08.

This document separates source implementation, build evidence and the remaining APK/device validation. A built Morphe patch bundle is not proof that it applies to a particular Prism APK.

## Purpose and ownership

The Prism Morphe patch exposes media/session facts that Prism already knows internally but does not put in ordinary Android MediaSession metadata. Standard title, artist, album, artwork, duration, playback state, position, speed and transport actions continue through the existing MediaSession path.

For lyrics, Prism remains the only source of truth. The patch snapshots Prism's existing player LyricsState. Coobvo does not perform a second lyric-provider search for Prism sessions. It must not estimate timestamps, invent word timings or silently fall back to LyricSync for Prism.

## Target source inspected

The source target inspected is Yigder/Prism commit 1abbed19d374b84e6cda4e1c63db937d1f70befd:

- Package: com.prism.music
- Version: 1.8.2.1, versionCode 58
- playback/MediaItems.kt: Song.toMediaItem() puts the original Song.id in Media3's mediaId and serializes the Song as prism.song metadata extras. This is the same ID used by Prism's lyric state.
- ui/player/PlayerLyrics.kt: LyricsState owns song, lyrics, source, loading, waiting, settled and unavailable.
- data/lyrics/Lyrics.kt: Lyrics owns provider source, sync flag and ordered LyricLine objects. Lines can carry start/end times, word start/end times, second-voice alignment, instrumental-gap markers and timed background vocals.
- data/prefs/Settings.kt: LyricsLookPrefs owns the selected style and independent visual overrides.
- ui/player/RomanLyrics.kt and the player translation helpers produce additional Compose-side romanized/translated lines. These are distinct from the stored base Lyrics object.

## Current Morphe patch

Source lives under morphe/prism-media-bridge/. It uses the official Morphe patch-template/patcher layout and the dedicated workflow .github/workflows/prism-morphe.yml.

The patch currently:

1. Decorates Prism's Song-to-MediaItem and browse MediaItem results with a versioned local bridge marker and URI while preserving original metadata.
2. Hooks Prism's existing ArtworkProvider.query() and intercepts only the /bridge/{songId} route. Other artwork routes are left alone.
3. Reads Prism's current in-memory stream diagnostics and player/queue state.
4. Hooks the return from Prism LyricsState setters for lyrics, source, loading, waiting and settled. After those state mutations it serializes the authoritative state for that song.
5. Exports a bounded, in-memory JSON snapshot to callers whose UID belongs to Android System UI. Other callers receive an empty cursor. The route is local and does not add a network lyric provider.

The lyric snapshot includes:

- Selected provider/source name when present.
- Loading, waiting, settled and unavailable state.
- The actual stored lyric source and sync flag.
- Ordered lines with original start/end timestamps and text.
- Original word-level timestamps and text.
- Background-vocal word timings.
- Second-voice alignment and instrumental-gap markers.
- Prism's resolved lyric-look preference values and translation/romanization preference flags.

No timestamp is synthesized. If Prism has only plain/unsynchronized lyrics, coobvo does not present them as timed karaoke. It indicates that Prism's lyrics are not synchronized instead.

## Not yet exported

The actual rendered translation and romanized line arrays produced by Prism's Compose helpers are not yet included. The current snapshot contains Prism's translation/romanization preference flags and target setting, but not the resulting extra line text/timing. The Canvas renderer also does not yet map every exported LyricsLookPrefs option into Prism's exact visual appearance. These are follow-up tasks, not completed features.

## Coobvo consumer

The Java consumer and model are located at:

- app/src/main/java/dev/android/dynamicisland/island/source/PrismMediaBridgeClient.java
- app/src/main/java/dev/android/dynamicisland/island/source/MediaSource.java
- app/src/main/java/dev/android/dynamicisland/island/model/IslandItem.java
- app/src/main/java/dev/android/dynamicisland/island/lyrics/MediaLyrics.java
- app/src/main/java/dev/android/dynamicisland/island/view/card/MediaBody.java

It reads the local URI on a worker thread, caches the snapshot briefly, and passes Prism's actual line/word times through without approximation. It polls again while Prism reports unknown/loading/waiting. It keys the route by Media3 media ID, which Prism's source confirms is the original Song.id.

For com.prism.music, the media card uses only this bridge snapshot. The independent LyricSync provider cascade is disabled for that session. Other media apps can continue using the separate opt-in LyricSync implementation.

The Canvas card currently renders synchronized line/word data using coobvo's renderer. It does not yet implement a complete visual clone of Prism's six styles, colours, layout, translations or romanization. If the bridge is absent, no Prism lyrics are fabricated or independently fetched.

## Build evidence

- Dedicated Morphe workflow: Prism Morphe Patch run #3 at https://github.com/cbv-ooo/coobvo/actions/runs/42446806609
- The run passed Morphe's :patches:buildAndroid task and produced coobvo-prism-media-bridge-mpp, artifact https://github.com/cbv-ooo/coobvo/actions/runs/42446806609/artifacts/13603559103
- Coobvo consumer source passed Debug APK run #61 for commit ef0633627ee3c939695dbbf59e5f8d424944fb48, artifact https://github.com/cbv-ooo/coobvo/actions/runs/42447445219/artifacts/13604575694
- These runs prove source compilation, patch bundle generation and app APK packaging. They do not prove bytecode fingerprints match the installed Prism APK, that Morphe successfully applies the bundle to that APK, or that the bridge works on the S10e.

## Remaining validation and work

1. Apply the generated .mpp to the exact Prism 1.8.2.1 / versionCode 58 APK and inspect every fingerprint result.
2. Install the patched Prism APK and coobvo debug APK together. Verify System UI-only access, current-song ID, queue changes, empty lyrics, no-lyrics and waiting/loading states.
3. Verify seeking, pause/resume, track changes, word timings, background vocals, second-voice lines and gaps on the S10e.
4. Decide whether Prism's rendered translation and romanized lines can be exported safely from the existing app model without invoking Compose UI cross-process.
5. Map exported LyricsLookPrefs to coobvo's Canvas renderer, keeping the Java/System UI process free of Prism dependencies and Compose.
6. Recheck fingerprints whenever the Prism target version changes.

## References

- Prism source: https://github.com/Yigder/Prism/tree/1abbed19d374b84e6cda4e1c63db937d1f70befd
- MediaItems.kt: https://github.com/Yigder/Prism/blob/1abbed19d374b84e6cda4e1c63db937d1f70befd/app/src/main/java/com/prism/music/playback/MediaItems.kt
- PlayerLyrics.kt: https://github.com/Yigder/Prism/blob/1abbed19d374b84e6cda4e1c63db937d1f70befd/app/src/main/java/com/prism/music/ui/player/PlayerLyrics.kt
- Lyrics.kt: https://github.com/Yigder/Prism/blob/1abbed19d374b84e6cda4e1c63db937d1f70befd/app/src/main/java/com/prism/music/data/lyrics/Lyrics.kt
- RomanLyrics.kt: https://github.com/Yigder/Prism/blob/1abbed19d374b84e6cda4e1c63db937d1f70befd/app/src/main/java/com/prism/music/ui/player/RomanLyrics.kt
- Morphe patch template: https://github.com/MorpheApp/morphe-patches-template
- Morphe patcher: https://github.com/MorpheApp/morphe-patcher