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
- Prism's 100-point scanned song waveform from `SongWaves.get(songId)`, when the local scan exists; an unscanned song simply omits the array.
- The real Prism LyricsState: provider, loading/waiting/settled/unavailable state, original sync flag, line text and timestamps, word timing, background-vocal word timing, second-voice alignment and instrumental gaps.
- Actual romanized lines returned by Prism's `rememberRomanized` helper and actual translated lines returned by Prism's `rememberLyricTranslation` helper, when those helpers have produced them.
- The resolved LyricsLook preferences, romanization mode/per-song override, translation preference and applicable typography/font preferences.

The patch adds no second metadata scraper, lyric provider or network request. Prism remains the source of truth. Standard title, artist, album, artwork, duration, playback state, position and transport controls continue through Android's MediaSession.

## Exact fingerprint failure reported by the owner

The owner tested against Prism 1.8.2.1 / versionCode 58 and supplied this failure:

```
Failed to match the fingerprint: dev.coobvo.prismmedia.PrismRememberLyricsStateFingerprint
```

That fingerprint attempted to hook Prism's `rememberLyricsState` composable and **has been removed**. The current patch does not require that composable fingerprint. Instead, the patch hooks the primary `LyricsState` constructor (`<init>(LyricsRepository, Song, CoroutineScope)`) and registers the fully initialized state object before Prism starts its async lookup. The extension retains a bounded set of up to eight strong references keyed by Prism's original Song ID, then refreshes live getters (`lyrics`, `loading`, `waiting`, `settled` and computed `unavailable`) for each permitted System UI bridge poll. Setter hooks are optional accelerators; romanization and translation helper hooks are optional too, so a mismatch in one helper does not block base lyrics.

The current patch also hooks the returns from Prism's romanization and translation helpers. It exports those real return values; it does not call a parallel provider. A successful Morphe build is not an APK fingerprint test. The constructor, Song-to-MediaItem, browse MediaItem and ArtworkProvider fingerprints still need to be checked against the user's exact APK. Setter and romanization/translation helper hooks are optional.

## Coobvo renderer

Prism sessions use the local bridge only; the independent LyricSync provider cascade is disabled for Prism. Other media apps can continue to use the separate LyricSync implementation.

The expanded media-card Canvas renderer currently maps Prism's current lyric/word timing and line-selection behavior, its five motion choices, the one/two-line current-text wrap setting, alignment, uppercase, weight, size, spacing/tracking, selected colors/alpha, extra placement/size/italic, backing vocals, gap marker, romanization/translation toggles and font selection. The island strip uses a 17 dp base font size and 16 dp line spacing (before Prism's size/spacing multipliers); two previous lines remain visible with stronger opacity and measured row height. It uses a fallback system font if Prism's packaged/imported font is not accessible from System UI. The Prism player mapping consumes title size/weight/alignment, elapsed/remaining-time presentation, cover corners/FULL width, scrubber style/color/thickness/thumb and scanned WAVE progress data. STRIP, SCRUBBER, TRANSPORT and VOLUME now follow the saved `playerDeck` row order. The BUTTONS row remains unrendered until its app-level PlayerButton actions are bridged; SessionButtons from Android MediaSession stay beside transport and are not substituted for that row. It is not a 1:1 player page: RIBBONS animation, Spectrum/Cover shaders, all custom cover shapes, title marquee, crossfade shaping, volume touch control and animated cover remain incomplete. The lyrics renderer does not yet provide pixel-identical Compose rendering: glow/halo, full page/background appearance and page-only controls are not completely replicated. The card background remains opaque; blur/frosted surfaces are not part of this project. Unsynchronized/static lyrics are not assigned invented timing.

## Build

The dedicated workflow is `.github/workflows/prism-morphe.yml`, using Java 21 and Morphe's `:patches:buildAndroid` task.

Latest bundle source version: `0.1.5`.

- Morphe run [#42494491943](https://github.com/cbv-ooo/coobvo/actions/runs/42494491943) passed and uploaded [coobvo-prism-media-bridge-mpp](https://github.com/cbv-ooo/coobvo/actions/runs/42494491943/artifacts/13623146420).
- The latest app build and selected Prism tests passed in debug run [#42494609826](https://github.com/cbv-ooo/coobvo/actions/runs/42494609826); APK artifact [app-debug.apk](https://github.com/cbv-ooo/coobvo/actions/runs/42494609826/artifacts/13622891407).
- Both runs validate compilation/package generation and selected test fixtures only; neither validates applying the patch to the original Prism 1.8.2.1 APK or live lyrics on the S10e.

Build this project locally from this directory:

```sh
./gradlew :patches:buildAndroid --no-daemon
```

## Privacy and compatibility

The provider returns a snapshot only to callers whose UID belongs to `com.android.systemui`; other callers receive an empty cursor. The route is local to the device. The source target is Prism 1.8.2.1 / versionCode 58; later Prism versions require fresh fingerprint validation.

Building the .mpp proves bundle generation only. The resulting bundle still needs to be applied to the original Prism APK, then installed with coobvo and tested on the owner's S10e.
