# Prism Media / Morphe Bridge

Last updated: 2026-11-08. This is the source and implementation handoff for a Prism-targeted Morphe patch. The current code is staged source, not a built .mpp bundle or device-verified integration.

## Correct purpose

The patch's primary purpose is to let coobvo Dynamic Island extract **more Prism-owned media/session information** than Android's ordinary MediaSession metadata provides. Lyrics are a later part of that broader bridge, not its only purpose.

Standard MediaSession information remains the existing path: title, artist, album, artwork, duration, playback state, seek position, speed, play/pause, previous/next and custom actions.

The additional Prism bridge targets information already held inside Prism:

- Prism Song identity and flags: track ID, album ID, live, video, explicit, episode, upload/local and podcast identity.
- Stream source and resolved stream: container, codec, decoder, itag, stream bitrate and content length.
- Audio input/output: sample rate, bit depth, channels, output encoding/sample rate, audio session, offload and bandwidth.
- Playback/audio processing: current buffer, loudness measurement, normalization gain, spatializer and effects.
- Output and video diagnostics: output device, video resolution/codec, dropped frames and underruns.
- Player state: position, buffered position, duration, playback speed, current queue index/count, shuffle and repeat.
- Later lyrics phase: Prism's selected provider, sync quality, complete line/word timing model and its resolved lyric-look preferences.

The bridge must only export fields Prism actually knows. It does not perform another metadata lookup or another lyric-provider cascade.

## Morphe finding: official lyrics patch is not Prism-compatible

The stable Morphe patches bundle inspected is **v2.1.0**, published 2026-11-06:

- Bundle/release: https://github.com/MorpheApp/morphe-patches/releases/tag/v2.1.0
- Patch source: https://github.com/MorpheApp/morphe-patches/blob/v2.1.0/patches/src/main/kotlin/app/morphe/patches/music/layout/lyrics/LyricsPatch.kt
- Runtime manager: https://github.com/MorpheApp/morphe-patches/blob/v2.1.0/extensions/music/src/main/java/app/morphe/extension/music/patches/lyrics/LyricsManager.java

The Morphe patch named **Third-party lyrics** declares compatibility with YouTube Music package `com.google.android.apps.youtube.music`. Its fingerprints and injected hooks target YouTube Music classes and its Litho/engagement-panel player UI.

Prism is a different app: package `com.prism.music`. Its player, Media3 metadata flow, lyrics repository, Compose renderer and settings are its own implementation. The official Morphe YouTube Music patch cannot simply be applied to Prism.

No .mpp file was present in the ZIP archives supplied in this project. The official v2.1.0 source and bundle were used as architectural references. Morphe patch bundles are input patch collections, not an APK/runtime plugin: Morphe applies compatible changes to a chosen input APK and outputs a newly patched/signed APK.

## Verified Prism source integration points

Source inspected from `Yigder/Prism` main at commit `1abbed19d374b84e6cda4e1c63db937d1f70befd`:

- App ID `com.prism.music`, source versionName `1.8.2.1`, versionCode `58`.
- `playback/MediaItems.kt`: `Song.toMediaItem(videoId)` serializes the Prism Song as JSON under `prism.song`, with optional `prism.video`; Media3 metadata includes title, artist, album title and artwork.
- `data/model/Models.kt`: Song contains ID, title, artist list, album reference, duration, thumbnail, video, explicit, live, episode, podcast ID and upload flags. Local identity can be distinguished by its `local:` ID prefix.
- `playback/NerdStats.kt`: Prism keeps a StateFlow snapshot with video ID, source, itag, container, codec, decoder, stream bitrate, sample rate, bit depth, channels, content length, client, output encoding/sample rate, offload, bandwidth, buffer, loudness, normalization gain, audio session, output device, spatializer, effects, video resolution/codec, dropped frames and underruns.
- `AppContainer.kt`: `PrismApp` owns the app container; the container exposes `nerdStats`, `player` and `queue`.
- `playback/PlayerConnection.kt`: its public `player` property returns the active ExoPlayer when bound.
- `playback/PlaybackService.kt`: the active playback service owns the ExoPlayer and updates NerdStats from Media3's AnalyticsListener.
- `playback/MediaTree.kt`: Prism already declares an exported `ArtworkProvider` at `content://com.prism.music.artwork`; its `query(Uri,...)` currently returns null and can host a dedicated `/bridge` read route without adding a second provider.

These objects are not a public API today. The Morphe patch adds a narrow local read route; it does not attempt to call Prism's Kotlin/Compose UI from System UI.

## Current staged implementation

### Coobvo consumer

The coobvo Java side is staged in:

- `app/src/main/java/dev/android/dynamicisland/island/source/PrismMediaBridgeClient.java`
- `app/src/main/java/dev/android/dynamicisland/island/source/MediaSource.java`
- `app/src/main/java/dev/android/dynamicisland/island/model/IslandItem.java`
- `app/src/main/java/dev/android/dynamicisland/island/view/card/MediaBody.java`

It reads the versioned marker/URI from Prism's standard media description extras, queries the local bridge on a worker thread, caches the small response and formats codec/container, bit depth, sample rate, channels and stream bitrate into the existing media card's output-information row. The album title and flags can also be shown. Existing artwork, transport controls and playback clock remain on the standard MediaSession path.

The consumer does not add a dependency to System UI. If the Prism patch is absent, its endpoint returns nothing and the normal media card continues to work. LyricSync provider lookup is disabled for `com.prism.music`; Prism must remain its own source of lyrics rather than causing duplicate external requests.

### Morphe patch source

The source is staged under `morphe/prism-media-bridge/`:

- Prism package/version compatibility target: `com.prism.music`, `1.8.2.1`.
- Fingerprint for Prism's `Song.toMediaItem`: injects a versioned local bridge URI into Media3 metadata extras while preserving the original extras.
- Fingerprint for Prism's existing `ArtworkProvider.query`: intercepts only the `/bridge` route, leaving all existing artwork routes untouched.
- Runtime extension: snapshots Prism's NerdStats and player state into JSON.
- Access control: only calls from the System UI package UID receive the snapshot; other callers receive an empty cursor.
- No network access, provider fetching, or duplicate lyrics search is performed by the extension.

The patch uses Morphe's official patch-template layout and patcher APIs. This is a staged patch project inside coobvo; it is **not yet packaged as a usable .mpp bundle**. Its standalone Gradle wrapper/build publication must be completed, then the patch must be built against the exact Prism 1.8.2.1 APK and its fingerprints checked. No patched Prism APK has been produced or device-tested yet.

## Next patch phases

1. Finish a reproducible standalone Morphe build setup and create the .mpp bundle.
2. Validate the two bytecode fingerprints against the original Prism 1.8.2.1 APK. Never assume source-level names prove that an APK's bytecode matches.
3. Test marker propagation through Media3's MediaSession/MediaDescription and test that only System UI can read the local snapshot.
4. Expand/validate extra track facts and stream diagnostics from Prism's actual runtime values.
5. Add a versioned lyric snapshot publisher from Prism's current LyricsState: loading/status, selected provider, sync quality, ordered lines, word timings, translation/romanization and timing generation.
6. Export Prism's resolved lyric preset and appearance preferences (six starting styles plus independent overrides) without exporting API credentials or unrelated settings.
7. Reimplement the compact-card mappings in coobvo's Java Canvas renderer. Do not try to transplant Compose Views across process boundaries.
8. Test track changes, seeking, pause/resume, stream transitions, queue changes, empty/unsupported fields, disabled/uninstalled bridge, and non-Prism sessions on the S10e.

## Lyrics architecture (later phase)

Prism's own source remains authoritative:

- `LyricsRepository` owns provider requests, matching, cache and per-track provider choices.
- `LyricsState` owns the active player UI's selected/loading/found lyric state.
- `LyricsLookPrefs` and its six starting styles (Prism, Karaoke, Bold Card, Glow, Typewriter, Calm) own the resolved visual treatment.
- The existing Android Auto/media-session lyrics overlay changes displayed title/artwork; it is not a full lyric-data API.

The later Morphe patch phase should publish a snapshot of that state. coobvo must never fetch separate third-party lyrics for active Prism playback. If the bridge is disabled/unavailable, show no Prism lyrics rather than issuing a second lookup.

## Official references

- Morphe patch template: https://github.com/MorpheApp/morphe-patches-template
- Morphe patcher: https://github.com/MorpheApp/morphe-patcher
- Prism source: https://github.com/Yigder/Prism
- Prism package manifest/provider declaration: https://github.com/Yigder/Prism/blob/main/app/src/main/AndroidManifest.xml
- Prism MediaItems: https://github.com/Yigder/Prism/blob/main/app/src/main/java/com/prism/music/playback/MediaItems.kt
- Prism Song model: https://github.com/Yigder/Prism/blob/main/app/src/main/java/com/prism/music/data/model/Models.kt
- Prism NerdStats: https://github.com/Yigder/Prism/blob/main/app/src/main/java/com/prism/music/playback/NerdStats.kt
- Prism playback service: https://github.com/Yigder/Prism/blob/main/app/src/main/java/com/prism/music/playback/PlaybackService.kt
