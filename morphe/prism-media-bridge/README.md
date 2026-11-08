# Coobvo Prism Media Bridge patches

A third-party Morphe patch project for Prism Player. It exposes Prism-owned media/session information to coobvo Dynamic Island. It is not part of the Morphe project and is not an official Morphe patch.

## Data exported today

The patch adds a versioned local content URI to Prism's Media3 metadata extras. Prism's MediaItem ID is the original Song ID, so the route is content://com.prism.music.artwork/bridge/{songId}. It hooks only the /bridge branch of Prism's existing exported ArtworkProvider; ordinary artwork queries continue through Prism's original implementation.

The route returns a small JSON snapshot from Prism's in-memory state:

- Song ID and Prism Song flags.
- Stream source, container, codec, decoder, itag and content length.
- Stream bitrate, input sample rate, bit depth and channel count.
- Output encoding/sample rate, offload and audio session.
- Current bandwidth estimate and buffer duration.
- Loudness and normalization gain.
- Output device, spatializer and effects.
- Video resolution/codec, dropped frames and audio underruns.
- Playback position/duration/speed and queue index/count when Prism's active player service is reachable.
- Shuffle and repeat state when available.
- Current Prism LyricsState: provider, loading/waiting/settled state, sync flag, ordered line times, word times, background-vocal word times, alignment and gap markers. The patch keeps a weak reference from the real LyricsState setter hooks and refreshes its current getters when System UI polls, so a private plain `settled` property does not need a synthetic or guessed setter hook.
- Resolved Prism lyric-look settings and translation/romanization preference flags.

The patch does not add another metadata scraper, lyric provider or network request. Prism remains the source of truth. Standard title, artist, album, artwork, duration, playback state and transport actions continue through Android's MediaSession.

The current bridge does not yet export final translated or romanized lines produced by Prism's Compose helpers.

The owner's Prism 1.8.2.1 Morphe log identified a `setSettled(boolean)` fingerprint mismatch. That fingerprint is deliberately not used: the extension refreshes the live state's `settled` getter during each local bridge query. The resulting `.mpp` still needs to be applied to the exact Prism APK; bundle compilation is not proof of successful patching. Coobvo now maps a subset of Prism's appearance settings to Canvas (sung/non-sung colours, opacity, weight emphasis, size scale, uppercase, alignment and one/two-line strip choice), but does not reproduce every Prism style, animation, glow/shadow or background override. Unsynchronized/static lines are not assigned estimated timestamps.

## Build

The dedicated GitHub Actions workflow is .github/workflows/prism-morphe.yml. It uses Java 21 and Morphe's :patches:buildAndroid task. A successful run uploads coobvo-prism-media-bridge-mpp.

Latest recorded bundle: Morphe run #3 at https://github.com/cbv-ooo/coobvo/actions/runs/42446806609, artifact https://github.com/cbv-ooo/coobvo/actions/runs/42446806609/artifacts/13603559103.

Run the same build locally from this directory:

```sh
./gradlew :patches:buildAndroid --no-daemon
```

## Privacy and compatibility

The provider returns snapshots only to callers whose UID owns com.android.systemui. The URI is local to the device; other callers receive an empty cursor. The patch targets Prism 1.8.2.1 / versionCode 58; later Prism versions require fresh fingerprint validation.

Building the .mpp does not prove that the patch applies to a particular Prism APK. The original Prism APK still needs to be patched, installed and tested with coobvo on the owner's S10e.

Official references:
- Morphe patch template: https://github.com/MorpheApp/morphe-patches-template
- Morphe patcher: https://github.com/MorpheApp/morphe-patcher
- Prism source commit: https://github.com/Yigder/Prism/tree/1abbed19d374b84e6cda4e1c63db937d1f70befd