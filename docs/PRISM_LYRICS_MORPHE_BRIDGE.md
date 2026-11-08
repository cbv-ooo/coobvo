# Prism Lyrics / Morphe Patch Investigation

Last updated: 2026-11-08. This is the implementation reference for a future Prism-only Morphe bridge. It is a source investigation and design, not a completed patch.

## Finding first: the official Morphe lyrics patch does not target Prism

The stable Morphe patches bundle inspected is **v2.1.0**, published 2026-11-06:

- Bundle/release: https://github.com/MorpheApp/morphe-patches/releases/tag/v2.1.0
- Patch source: [LyricsPatch.kt](https://github.com/MorpheApp/morphe-patches/blob/v2.1.0/patches/src/main/kotlin/app/morphe/patches/music/layout/lyrics/LyricsPatch.kt)
- Runtime manager: [LyricsManager.java](https://github.com/MorpheApp/morphe-patches/blob/v2.1.0/extensions/music/src/main/java/app/morphe/extension/music/patches/lyrics/LyricsManager.java)

The Morphe patch named **Third-party lyrics** declares compatibility with package `com.google.android.apps.youtube.music`. Its fingerprints and injected hooks target YouTube Music classes and its Litho/engagement-panel player UI.

Prism is a different app: package `com.prism.music`. Its player, metadata flow, lyrics repository, Compose renderer and settings are Prism's own implementation. The official Morphe YouTube Music patch cannot simply be applied to Prism, and enabling that patch in Morphe does not make a Prism API available.

No standalone `.mpp` file was present in the uploaded ZIP archives in this project. The v2.1.0 bundle/source was therefore used as the reference. The official release bundle is a patch collection, not an APK and not a runtime plugin: Morphe selects patches from the bundle, applies compatible bytecode/resource changes to an input APK, then outputs a newly built and signed APK. See [Morphe Desktop documentation](https://github.com/MorpheApp/morphe-desktop/blob/main/docs/documentation.md).

## What the Morphe YouTube Music lyrics patch actually contains

### Patch installation and hooks

The patch is called **Third-party lyrics**. It depends on Morphe's shared extension, settings/resources, Litho filtering, media-session hooks and YouTube Music video-information hooks.

It does not just add an independent floating lyrics window. It detects YouTube Music's lyrics engagement panel/player tab, installs a custom `FrameLayout` into the existing panel container, hides the built-in lyrics content while the patched panel is visible, and lets the built-in content remain underneath as fallback. It has special detection for accounts whose lyrics appear as a player tab and for panels without a normal heading.

Metadata and playback hooks feed the runtime manager:

- `MediaMetadata` provides track title, artist, album and duration.
- `PlaybackState` supplies position, play/pause and playback speed.
- YouTube Music video ID hooks help bind metadata and lyrics to the actual playing video.
- Separate session hooks support optional miniplayer/lock-screen paths.

The patch is version-sensitive. It uses bytecode fingerprints for YouTube Music's classes/methods; it is not a generic MediaSession lyrics adapter.

### Provider engine and matching

The Morphe v2.1.0 provider registry contains 22 provider IDs:

Apple, BiniLyrics, LrcRed, bLyrics, Lyricify, Unison, PetitLyrics, AMLL, QQ, SimpMusic, KuGou, Musixmatch, YTMusic, LRCLIB, NetEase, Luna, LunaBeat, Captions, Spotify, Deezer, RMM and Genius.

The default order enables a subset and disables others with a leading `-` in the saved comma-separated order. Users can change provider order and disable individual sources. Some sources require credentials/tokens or other user configuration; the list does not mean every provider is always available.

The engine does more than accept the first text response:

- Providers return a structured `Lyrics` result and can supply one or multiple candidates.
- Candidate results are scored against track title, artist, album/duration and other match evidence.
- Lyrics quality contributes to ranking: real word-timed lyrics rank above line-synced lyrics, which rank above plain text.
- Track-version/edition checks help avoid lyrics for a radio edit, remix or other recording.
- Per-track preferences remember the chosen result and a queue/fingerprint of alternatives; users can cycle/refetch without losing a still-valid displayed result.
- Custom title/artist lookup terms, metadata cleanup, text filters and timing offsets are supported.
- Old/in-flight responses are invalidated when the track changes so a previous song's lyrics do not overwrite the current one.
- Requests and parsing run off the UI thread. Provider failures are contained; the manager keeps state such as loading, loaded, not found and error.

### Data model and formats

The runtime does not reduce everything to a single string. Its model carries:

- line start/end time and text;
- individual word start/end time and text;
- whether a line is a duet/right-aligned voice or a background vocal;
- song section labels;
- optional per-word romanization and ruby readings;
- optional translations/romanizations aligned with lyrics;
- provider identity, sync state, raw format and related metadata.

Provider parsers include LRC, enhanced/word-timed formats such as KRC, QRC, YRC and TTML, plus plain text. A word-timed result stays distinct from line-timed/static results. Morphe includes validation and repairs for malformed word timings, and clamps a final word so it cannot spill into the next line.

### Rendering and animations

The Morphe panel is implemented by `LyricsPanelView` and `LyricsLineView`, both custom Android Views. It has a scrolling lyrics panel, current-line logic, word timing, source/status footer, toolbar, auto-scroll/manual-scroll handling and optional dynamic artwork background.

Its behavior includes:

- continuous playback-position-driven word highlighting;
- line alpha/scale changes;
- animated expansion/collapse of background vocals;
- duet alignment and right-to-left/script-aware word geometry;
- translation/romanization/ruby placement and reveal;
- section labels/dots and instrumental gaps;
- tap-to-seek, copy, refresh and optional keep-screen-on;
- optional artwork-driven/animated backdrop.

The renderer ticks at a rate derived from the display refresh rate or configured frame rate; when word-sync is disabled it slows the tick. It is not a static text overlay.

**Morphe does not have Prism's six named lyric presets.** It exposes many individual preferences and toggles, such as word sync, line scale, background split, dynamic background, text size, line visibility, centering, punctuation, translate/romanize/ruby controls and buttons. These are Morphe's own renderer settings.

### Morphe preferences and extras

The patch adds grouped preferences for:

- master enable and ordered lyric sources;
- translation provider/language;
- romanization provider/language and transliteration;
- ruby provider/style;
- cache clearing;
- text size, word sync, line scaling, split background vocals, section display, hiding played/unplayed lines, text cleanup and alignment;
- panel controls such as tap-to-seek, keep-screen-on, dynamic background and toolbar buttons;
- timing offset and optional SponsorBlock matching;
- optional media-session/miniplayer lyric output and formatting;
- custom metadata/text/credit regexes.

Translation, romanization and ruby are separate transformation pipelines from the original lyric fetch. Some settings need user-provided API tokens, dictionaries or endpoints. They must not be confused with the primary provider list.

## What Prism already owns

Prism's own source is the correct source of truth for Prism playback:

- [LyricsRepository](https://github.com/Yigder/Prism/blob/main/app/src/main/java/com/prism/music/data/lyrics/Lyrics.kt) owns its providers, parsers, matching, per-source database cache, per-track choices, custom search names, timing alignment and automatic quality fallback.
- [LyricsState and player clock](https://github.com/Yigder/Prism/blob/main/app/src/main/java/com/prism/music/ui/player/PlayerLyrics.kt) own the current song's loading/selected source state and the playback-following lyric position used by the Compose player.
- [Lyrics look model and presets](https://github.com/Yigder/Prism/blob/main/app/src/main/java/com/prism/music/data/prefs/Settings.kt) define Prism's visual preference model.
- [Lyrics renderer](https://github.com/Yigder/Prism/blob/main/app/src/main/java/com/prism/music/ui/player/LyricStyles.kt) draws the visual treatment using that model.
- [Lyrics customization UI](https://github.com/Yigder/Prism/blob/main/app/src/main/java/com/prism/music/ui/screens/LyricsYourWay.kt) exposes the presets and the independently configurable pieces.

Prism has six starting styles: **Prism, Karaoke, Bold Card, Glow, Typewriter and Calm**. Since Prism 1.8.0, the user can start from a style and then independently override animation, word-by-word behavior, card, halo and bold-line behavior. Its wider look model also contains colors, weight, spacing, uppercase, line positioning/ahead count, background, translation/romanization placement, shadow and other effects.

Prism's main data types already distinguish line timing from real word timing. Its own provider order, cache and per-track source selection must remain authoritative for Prism playback.

### What Prism exposes today—and what it does not

Prism's `AppContainer` owns a `LyricsRepository`, but the current lyric state used by the player is held in its player-side `LyricsState`. These are app-process objects, not a public cross-process service.

Prism also has an optional Android Auto/media-session lyric presentation. Its `LyricsOverlayPlayer` can temporarily replace the session's displayed title/artist/artwork with a lyric and a generated lyric card. That output is designed for the car/notification presentation; it is not a stable export of all lyric lines, word timestamps, selected provider and visual preferences.

The existing exported Prism `ArtworkProvider` serves artwork. It is not a lyrics API. The source inspected contains no equivalent exported lyrics provider/Binder contract for System UI to consume.

Therefore, coobvo cannot directly call Prism's Kotlin Compose renderer or inspect Prism's live Compose state across process boundaries. A local bridge must be added to Prism by a Prism-targeted patch.

## Proposed Prism-only Morphe bridge

### Responsibility split

**Prism remains the owner** of provider requests, selected provider, provider order, matching, quality ranking, per-track selection, cache, translation/romanization data and every lyrics preference.

**The Morphe patch adds only an opt-in export bridge** inside Prism. It should not add another provider cascade or fetch lyrics itself.

**coobvo/System UI consumes the exported state** and renders it with coobvo's Java Canvas media card. It does not use Compose or make provider requests for a Prism session.

### Recommended bridge contract

Use a small, versioned, local-only read interface (an exported `ContentProvider` with strict caller checks is a reasonable first implementation). Publish a cached snapshot and call `notifyChange` when track, lyrics, provider choice or appearance preferences change. The UI should not poll/network-fetch every frame.

The snapshot should carry:

1. **Session:** contract version, Prism package/session identity, track ID, title/artist/album, duration, active/loading/no-lyrics state.
2. **Clock:** position milliseconds, elapsed-realtime sample timestamp, playing state, playback speed and a seek/track generation. coobvo can interpolate between updates and reset on seek/pause/track change.
3. **Source and quality:** selected source name, actual timing quality (syllable/word/line/static), and whether data is approximate or truly word-timed.
4. **Lyrics:** ordered lines with start/end times, text and gap/duet/background flags; words with their own start/end times and text; optional aligned translation/romanization/ruby data.
5. **Prism appearance:** preset/style plus the resolved current values for motion, word-by-word mode, sung/other colors, text weight/scale/spacing, alignment/position/lines ahead, background/card, halo/shadow and extra-line placement.
6. **Invalidation:** monotonic generation/version so the consumer can discard stale snapshots and avoid applying one song's lyrics/preferences to another.

Protect the provider so ordinary apps cannot read it. The bridge is local-only; do not send lyrics or playback state to a web endpoint. Verify the Binder caller is System UI (and the expected UID/package) before returning content. Keep export disabled until the owner enables it; do not expose user credentials or provider API keys.

### Activation and overhead rules

- coobvo detects the active media session's package as `com.prism.music`.
- Only then does it bind/register the observer and read Prism's bridge.
- For Prism playback, coobvo must not call `LyricsManager`'s external providers. Prism already fetched and selected the lyrics.
- When Prism is no longer the active media session, coobvo unregisters, clears its snapshot and stops all Prism bridge work.
- If Prism is active but its bridge is unavailable/disabled, show no Prism lyrics rather than silently running a second LyricSync provider cascade for the same track.
- Other media players may continue using the existing opt-in LyricSync path if that remains desired.

### Renderer parity

The bridge should export Prism's resolved preferences, not Compose view instances or pixel screenshots. coobvo then reimplements the selected look's behavior in Java Canvas: word growth/fill/sweep/typewriter/calm, line emphasis, colors, spacing and background/effects supported by the card's available area.

Some Prism presentation choices (full-screen lyric-page scroll, large dynamic backdrop, multiple page controls) cannot be reproduced literally in a compact island card. Define a compact-card mapping for each Prism look, and document any unsupported property rather than pretending it was transferred. The renderer must allocate no per-frame objects and must remain safe if Prism sends missing, static or malformed timing data.

## Implementation sequence

1. Add and test a **Prism package compatibility target** in the Morphe patch source. Fingerprint the actual Prism version's classes/methods; do not reuse YouTube Music fingerprints.
2. Implement a read-only, versioned bridge and a Prism-side publisher for track, lyric, clock and resolved visual settings.
3. Add coobvo bridge consumption gated strictly by active package `com.prism.music`; suppress LyricSync network requests for that session.
4. Reimplement Prism's compact visual mapping in the Java Canvas card.
5. Test provider changes, track changes, seeks, pause/resume, no-lyrics/static/line/word-timed data, settings changes, bridge disable/uninstall and non-Prism media sessions.
6. Only call it complete after a patched Prism build, coobvo build and real S10e playback check demonstrate that the expected lyrics and selected look appear without duplicate network requests.

## Licensing and compatibility

Both Prism and Morphe source repositories are GPLv3. Any copied/adapted source must retain applicable copyright, license and notice obligations. Pin the patch to a Prism app version/signature and maintain fingerprints when Prism updates. The Morphe v2.1.0 YouTube Music patch is reference material, not the Prism target implementation.
