# Coobvo Prism Media Bridge patches

A third-party Morphe patch project for Prism Player. It exposes Prism's own playback and stream diagnostics to coobvo Dynamic Island. It is built with Morphe tooling; it is not part of the Morphe project and is not an official Morphe patch.

## Data exported

The patch adds a versioned marker and local content URI to Prism's Media3 metadata extras, then hooks Prism's already-exported artwork provider at the `/bridge` query route. The route returns a small JSON snapshot from Prism's existing in-memory state:

- Stream source, container, codec and decoder.
- Stream bitrate, input sample rate, bit depth and channel count.
- Output encoding/sample rate, offload and audio session.
- Current bandwidth estimate and buffer duration.
- Loudness and normalization gain.
- Output device, spatializer and effects.
- Video resolution/codec, dropped frames and audio underruns.
- Playback position/duration/speed and queue index/count when Prism's active player service is reachable from its existing application container.
- Shuffle and repeat state when available.

The patch does not add another lyrics provider, metadata scraper or network request. Prism remains the source of truth for stream and playback data. Its existing MediaSession continues to carry standard title, artist, album, artwork, duration, play state and transport actions.

## Privacy and compatibility

The provider returns a snapshot only to callers whose UID owns `com.android.systemui`. The URI is local to the device. Other callers receive an empty cursor. The patch targets the exact Prism source version `1.8.2.1` / versionCode `58`; it must be re-fingerprinted and tested for later Prism builds. A real Prism APK must be patched and installed to verify DEX fingerprints and provider behavior.

## Build

This source is staged inside coobvo so the integration can be reviewed beside its consumer. It is not yet a standalone Morphe patch source. The Morphe template expects a separate repository with its own wrapper and release metadata. Copy these project files into a repository created from the official template, then use the template's documented `./gradlew buildAndroid` task to generate the `.mpp` bundle.

Official references:
- Morphe patch template: https://github.com/MorpheApp/morphe-patches-template
- Morphe patcher: https://github.com/MorpheApp/morphe-patcher
- Prism: https://github.com/Yigder/Prism

This is source-stage only. No `.mpp` bundle has been built, and no patched Prism APK has been tested on the Galaxy S10e.
