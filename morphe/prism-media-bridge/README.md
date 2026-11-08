# Coobvo Prism Media Bridge patches

A third-party Morphe patch project for Prism Player. It exposes Prism-owned media/session information to coobvo Dynamic Island. It is not part of the Morphe project and is not an official Morphe patch.

## Data exported today

The patch adds a versioned marker and local content URI to Prism's Media3 metadata extras, then hooks Prism's existing exported artwork provider at the `/bridge` query route. The route returns a small JSON snapshot from Prism's in-memory state:

- Stream source, container, codec and decoder.
- Stream bitrate, input sample rate, bit depth and channel count.
- Output encoding/sample rate, offload and audio session.
- Current bandwidth estimate and buffer duration.
- Loudness and normalization gain.
- Output device, spatializer and effects.
- Video resolution/codec, dropped frames and audio underruns.
- Playback position/duration/speed and queue index/count when Prism's active player service is reachable.
- Shuffle and repeat state when available.

The patch does not add another metadata scraper, lyric provider or network request. Prism remains the source of truth. Standard title, artist, album, artwork, duration, playback state and transport actions continue through Android's MediaSession.

## Build

This source is built in place from the coobvo repository. The dedicated GitHub Actions workflow uses the official Morphe Gradle plugin/template layout, Java 21 and the `:patches:buildAndroid` task. A successful run uploads the generated `.mpp` bundle as the artifact `coobvo-prism-media-bridge-mpp`.

Run the same build locally from this directory:

```sh
./gradlew :patches:buildAndroid --no-daemon
```

## Privacy and compatibility

The provider returns snapshots only to callers whose UID owns `com.android.systemui`. The URI is local to the device; other callers receive an empty cursor. The patch targets Prism `1.8.2.1` / versionCode `58`; later Prism versions require fresh fingerprint validation. Building the `.mpp` does not prove that the patch applies to a particular Prism APK: the original APK must still be patched and tested.

Official references:
- Morphe patch template: https://github.com/MorpheApp/morphe-patches-template
- Morphe patcher: https://github.com/MorpheApp/morphe-patcher
- Prism: https://github.com/Yigder/Prism
