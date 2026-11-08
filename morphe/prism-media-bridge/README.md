# Coobvo Prism Media Bridge

A third-party Morphe patch project for Prism Player. It exposes Prism-owned media/session and lyrics state to coobvo Dynamic Island. It is not an official Morphe patch.

## Exported Prism state

The patch decorates Prism's Media3 Song and browse MediaItem paths with a versioned local content URI. Prism's original Song ID remains the bridge key. It intercepts only the `/bridge/{songId}` query on Prism's existing `ArtworkProvider`; all ordinary artwork queries stay with Prism.

The bounded in-memory JSON snapshot includes:

- Song ID, song flags and album metadata.
- Stream source/container/codec/decoder/itag/content length, bitrate, input sample rate, bit depth and channel count.
- Output encoding/sample rate, offload and audio session.
- Current bandwidth estimate and buffer duration.
- Loudness/normalization gain, output device, spatializer and effects.
- Video resolution/codec, dropped frames and audio underruns.
- Playback position/duration/speed, queue index/count where Prism's player service exposes it, shuffle and repeat state where available.
- The real Prism LyricsState: provider, loading/waiting/settled/unavailable state, original sync flag, line text and timestamps, word timing, background-vocal word timing, second-voice alignment and instrumental gaps.
- Actual romanized lines returned by Prism's `rememberRomanized` helper and actual translated lines returned by Prism's `rememberLyricTranslation` helper, when those helpers have produced them.
- The resolved LyricsLook preferences, romanization mode/per-song override, translation preference and applicable typography/font preferences.

The patch adds no second metadata scraper, lyric provider or network request. Prism remains the source of truth. Standard title, artist, album, artwork, duration, playback state, position and transport controls continue through Android's MediaSession.

## Exact fingerprint failure reported by the owner

The owner tested against Prism 1.8.2.1 / versionCode 58 and supplied this failure:

```
Failed to match the fingerprint: dev.coobvo.prismmedia.PrismRememberLyricsStateFingerprint
```

That fingerprint attempted to hook Prism's `rememberLyricsState` composable and **has been removed**. The current patch does not require that composable fingerprint. Instead, it retains a weak reference from Prism's real LyricsState setter hooks (`lyrics`, `source`, `loading` and `waiting`) and refreshes the object getters on a System UI bridge poll, including the `settled` property that has no reliable setter hook in this target.

The current patch also hooks the returns from Prism's romanization and translation helpers. It exports those real return values; it does not call a parallel provider. A successful Morphe build is not an APK fingerprint test. The remaining setter, helper, MediaItem and ArtworkProvider fingerprints must still be checked against the user's exact APK.

## Coobvo renderer

Prism sessions use the local bridge only; the independent LyricSync provider cascade is disabled for Prism. Other media apps can continue to use the separate LyricSync implementation.

The expanded media-card Canvas renderer currently maps Prism's current lyric/word timing and line-selection behavior, its five motion choices, the one/two-line current-text wrap setting, alignment, uppercase, weight, size, spacing/tracking, selected colors/alpha, extra placement/size/italic, backing vocals, gap marker, romanization/translation toggles and font selection. It uses a fallback system font if Prism's packaged/imported font is not accessible from System UI. It does not yet provide pixel-identical Compose rendering: Spectrum/Cover fills, glow/halo, full page/background appearance and page-only controls are not completely replicated. The card background remains opaque; blur/frosted surfaces are not part of this project. Unsynchronized/static lyrics are not assigned invented timing.

## Build

The dedicated workflow is `.github/workflows/prism-morphe.yml`, using Java 21 and Morphe's `:patches:buildAndroid` task.

Latest bundle version: `0.1.2`.

- Run [#42474506746](https://github.com/cbv-ooo/coobvo/actions/runs/42474506746) passed and uploaded [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42474506746/artifacts/13614952120).

Build this project locally from this directory:

```sh
./gradlew :patches:buildAndroid --no-daemon
```

## Privacy and compatibility

The provider returns a snapshot only to callers whose UID belongs to `com.android.systemui`; other callers receive an empty cursor. The route is local to the device. The source target is Prism 1.8.2.1 / versionCode 58; later Prism versions require fresh fingerprint validation.

Building the .mpp proves bundle generation only. The resulting bundle still needs to be applied to the original Prism APK, then installed with coobvo and tested on the owner's S10e.
