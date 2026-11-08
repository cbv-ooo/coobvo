package dev.coobvo.prismmedia;

import android.content.ContentProvider;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime extension injected into Prism. It only republishes values Prism already holds in memory;
 * it performs no network work and does not start a second metadata or lyrics provider.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class PrismMediaBridge {
    private static final String PACKAGE_SYSTEM_UI = "com.android.systemui";
    private static final String BRIDGE_VERSION = "coobvo.prism.bridge.version";
    private static final String BRIDGE_URI = "coobvo.prism.bridge.uri";
    private static final String CONTENT_URI = "content://com.prism.music.artwork/bridge";
    private static final String SNAPSHOT_COLUMN = "snapshot";
    private static final Map<String, JSONObject> LYRICS_BY_SONG = new ConcurrentHashMap<>();
    /** Weak handles let /bridge reads refresh plain state fields that have no stable setter hook. */
    private static final Map<String, WeakReference<Object>> LYRICS_STATE_BY_SONG = new ConcurrentHashMap<>();
    /** Results returned by Prism's own Compose helpers; null means the listener has it disabled/not ready. */
    private static final Map<String, JSONArray> ROMANIZED_LINES_BY_SONG = new ConcurrentHashMap<>();
    private static final Map<String, JSONObject> TRANSLATION_BY_SONG = new ConcurrentHashMap<>();

    private PrismMediaBridge() {}

    /** Injection point at the return of Song.toMediaItem. */
    public static Object decorateMediaItem(Object mediaItem) {
        if (mediaItem == null) return null;
        try {
            Object metadata = invokeNoArgs(mediaItem, "getMediaMetadata");
            if (metadata == null) return mediaItem;

            Object oldExtras = invokeNoArgs(metadata, "getExtras");
            Bundle extras = oldExtras instanceof Bundle ? new Bundle((Bundle) oldExtras) : new Bundle();
            extras.putInt(BRIDGE_VERSION, 1);
            Object mediaIdValue = invokeNoArgs(mediaItem, "getMediaId");
            String mediaId = mediaIdValue == null ? "" : String.valueOf(mediaIdValue);
            String bridgeUri = CONTENT_URI + (mediaId.isEmpty() ? "" : "/" + Uri.encode(mediaId));
            extras.putString(BRIDGE_URI, bridgeUri);

            Object metadataBuilder = invokeNoArgs(metadata, "buildUpon");
            invokeOneArg(metadataBuilder, "setExtras", extras);
            Object updatedMetadata = invokeNoArgs(metadataBuilder, "build");

            Object itemBuilder = invokeNoArgs(mediaItem, "buildUpon");
            invokeOneArg(itemBuilder, "setMediaMetadata", updatedMetadata);
            Object updatedItem = invokeNoArgs(itemBuilder, "build");
            return updatedItem == null ? mediaItem : updatedItem;
        } catch (Throwable ignored) {
            // A Prism update that changes Media3's builder API must not stop playback.
            return mediaItem;
        }
    }

    /**
     * Called first by the existing ArtworkProvider.query override.
     * Null means "not our route"; a /bridge request always gets an empty or populated cursor.
     */
    public static Cursor queryBridge(ContentProvider provider, Uri uri) {
        if (provider == null || uri == null) return null;
        List<String> path = uri.getPathSegments();
        if (path == null || path.isEmpty() || !"bridge".equals(path.get(0))) return null;
        String requestedSongId = path.size() > 1 ? path.get(1) : "";

        MatrixCursor denied = new MatrixCursor(new String[]{SNAPSHOT_COLUMN});
        try {
            Context context = provider.getContext();
            if (context == null || !isSystemUiCaller(context)) return denied;

            Object app = context.getApplicationContext();
            Object container = invokeNoArgs(app, "getContainer");
            JSONObject snapshot = new JSONObject();
            snapshot.put("version", 2);
            snapshot.put("updatedAtElapsedRealtime", SystemClock.elapsedRealtime());
            snapshot.put("requestedSongId", requestedSongId);

            if (container != null) {
                Object stateFlow = invokeNoArgs(container, "getNerdStats");
                Object stats = invokeNoArgs(stateFlow, "getValue");
                if (stats != null) {
                    copy(snapshot, stats,
                            "videoId", "source", "itag", "container", "codec", "decoder",
                            "streamBitrateKbps", "sampleRate", "bitDepth", "channels", "contentLength",
                            "client", "outputEncoding", "outputSampleRate", "offload", "bandwidthKbps",
                            "bufferMs", "loudnessDb", "loudnessMeasured", "normalizationGainDb",
                            "audioSessionId", "outputDevice", "spatializer", "effects",
                            "videoResolution", "videoCodec", "droppedFrames", "underruns");
                }
                copyPlayerState(snapshot, container);
            }

            JSONObject lyrics = null;
            if (!requestedSongId.isEmpty()) {
                // Prism 1.8.2.1 updates `settled` as a private plain property. Its setter is not
                // a reliable bytecode join point, so sample the live LyricsState on each poll.
                WeakReference<Object> stateReference = LYRICS_STATE_BY_SONG.get(requestedSongId);
                Object liveState = stateReference == null ? null : stateReference.get();
                if (liveState != null) captureLyricsState(liveState);
                lyrics = LYRICS_BY_SONG.get(requestedSongId);
            }
            if (lyrics != null) snapshot.put("prismLyrics", new JSONObject(lyrics.toString()));

            MatrixCursor result = new MatrixCursor(new String[]{SNAPSHOT_COLUMN});
            result.addRow(new Object[]{snapshot.toString()});
            return result;
        } catch (Throwable ignored) {
            return denied;
        }
    }

    /**
     * Called after Prism's LyricsState mutations and when System UI polls the local bridge.
     * The state already contains Prism's provider result and exact word/line timestamps; this
     * method only takes a read-only snapshot of those values.
     */
    public static void captureLyricsState(Object state) {
        if (state == null) return;
        try {
            String songId = songId(state);
            if (songId.isEmpty()) return;
            LYRICS_STATE_BY_SONG.put(songId, new WeakReference<>(state));
            Object lyrics = property(state, "lyrics");
            boolean loading = asBoolean(property(state, "loading"));
            boolean waiting = asBoolean(property(state, "waiting"));
            boolean settled = asBoolean(property(state, "settled"));
            boolean unavailable = asBoolean(property(state, "unavailable"));

            JSONObject snapshot = new JSONObject();
            snapshot.put("songId", songId);
            snapshot.put("loading", loading);
            snapshot.put("waiting", waiting);
            snapshot.put("settled", settled);
            snapshot.put("unavailable", unavailable);

            if (lyrics != null) {
                JSONObject exported = serializeLyrics(lyrics);
                JSONArray romanized = ROMANIZED_LINES_BY_SONG.get(songId);
                if (romanized != null) exported.put("romanizedLines", new JSONArray(romanized.toString()));
                JSONObject translation = TRANSLATION_BY_SONG.get(songId);
                if (translation != null) {
                    exported.put("translatedLines", new JSONArray(translation.optJSONArray("lines").toString()));
                    if (translation.has("from")) exported.put("translationFrom", translation.opt("from"));
                    if (translation.has("to")) exported.put("translationTo", translation.opt("to"));
                }
                snapshot.put("lyrics", exported);
                snapshot.put("status", exported.optJSONArray("lines") != null
                        && exported.optJSONArray("lines").length() > 0 ? "AVAILABLE" : "UNAVAILABLE");
            } else if (loading) {
                snapshot.put("status", "LOADING");
            } else if (waiting) {
                snapshot.put("status", "WAITING");
            } else if (settled || unavailable) {
                snapshot.put("status", "UNAVAILABLE");
            } else {
                snapshot.put("status", "UNKNOWN");
            }

            JSONObject look = readLyricsLook(songId);
            if (look != null) snapshot.put("look", look);
            LYRICS_BY_SONG.put(songId, snapshot);
            if (LYRICS_BY_SONG.size() > 64) {
                for (String oldId : LYRICS_BY_SONG.keySet()) {
                    if (!songId.equals(oldId)) {
                        LYRICS_BY_SONG.remove(oldId);
                        LYRICS_STATE_BY_SONG.remove(oldId);
                        ROMANIZED_LINES_BY_SONG.remove(oldId);
                        TRANSLATION_BY_SONG.remove(oldId);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Optional bridge only: never interfere with Prism's own lyric state or playback.
        }
    }

    /** Captures the exact List<LyricLine?> returned by Prism's rememberRomanized() composable. */
    public static void captureRomanizedLyrics(Object state, Object lines) {
        if (state == null) return;
        try {
            String songId = songId(state);
            if (songId.isEmpty()) return;
            if (lines == null) ROMANIZED_LINES_BY_SONG.remove(songId);
            else ROMANIZED_LINES_BY_SONG.put(songId, serializeLyricLines(lines));
            captureLyricsState(state);
        } catch (Throwable ignored) {
            // Romanization is optional and must never interfere with Prism's player.
        }
    }

    /** Captures the exact translated line list returned by Prism's rememberLyricTranslation() composable. */
    public static void captureTranslatedLyrics(Object state, Object translation) {
        if (state == null) return;
        try {
            String songId = songId(state);
            if (songId.isEmpty()) return;
            if (translation == null) {
                TRANSLATION_BY_SONG.remove(songId);
            } else {
                JSONObject snapshot = new JSONObject();
                putProperty(snapshot, "from", translation, "from");
                putProperty(snapshot, "to", translation, "to");
                snapshot.put("lines", serializeStrings(property(translation, "translations")));
                TRANSLATION_BY_SONG.put(songId, snapshot);
            }
            captureLyricsState(state);
        } catch (Throwable ignored) {
            // Translation is optional and must never interfere with Prism's player.
        }
    }

    private static String songId(Object state) throws Exception {
        Object song = property(state, "song");
        Object idValue = property(song, "id");
        return idValue == null ? "" : String.valueOf(idValue);
    }

    private static JSONArray serializeStrings(Object values) throws Exception {
        JSONArray result = new JSONArray();
        if (values instanceof Iterable<?>) {
            for (Object value : (Iterable<?>) values) result.put(value == null ? JSONObject.NULL : String.valueOf(value));
        }
        return result;
    }

    private static JSONArray serializeLyricLines(Object values) throws Exception {
        JSONArray result = new JSONArray();
        if (values instanceof Iterable<?>) {
            for (Object line : (Iterable<?>) values) {
                result.put(line == null ? JSONObject.NULL : serializeLyricLine(line));
            }
        }
        return result;
    }

    private static JSONObject serializeLyricLine(Object line) throws Exception {
        JSONObject row = new JSONObject();
        putProperty(row, "timeMs", line, "timeMs");
        putProperty(row, "endMs", line, "endMs");
        putProperty(row, "text", line, "text");
        putProperty(row, "alignEnd", line, "alignEnd");
        putProperty(row, "isGap", line, "isGap");
        row.put("words", serializeWords(property(line, "words")));
        row.put("background", serializeWords(property(line, "background")));
        return row;
    }

    private static JSONObject serializeLyrics(Object lyrics) throws Exception {
        JSONObject result = new JSONObject();
        putProperty(result, "source", lyrics, "source");
        putProperty(result, "synced", lyrics, "synced");
        Object linesValue = property(lyrics, "lines");
        JSONArray lines = new JSONArray();
        if (linesValue instanceof Iterable<?>) {
            for (Object line : (Iterable<?>) linesValue) {
                if (line == null) continue;
                JSONObject row = new JSONObject();
                putProperty(row, "timeMs", line, "timeMs");
                putProperty(row, "endMs", line, "endMs");
                putProperty(row, "text", line, "text");
                putProperty(row, "alignEnd", line, "alignEnd");
                putProperty(row, "isGap", line, "isGap");
                row.put("words", serializeWords(property(line, "words")));
                row.put("background", serializeWords(property(line, "background")));
                lines.put(row);
            }
        }
        result.put("lines", lines);
        return result;
    }

    private static JSONArray serializeWords(Object wordsValue) throws Exception {
        JSONArray words = new JSONArray();
        if (wordsValue instanceof Iterable<?>) {
            for (Object word : (Iterable<?>) wordsValue) {
                if (word == null) continue;
                JSONObject value = new JSONObject();
                putProperty(value, "startMs", word, "startMs");
                putProperty(value, "endMs", word, "endMs");
                putProperty(value, "text", word, "text");
                words.put(value);
            }
        }
        return words;
    }

    /** Copy Prism's resolved lyric preset, including independent overrides, not just its style label. */
    /**
     * Reads Prism's effective, already-resolved player and lyrics preferences. This is deliberately
     * a snapshot reader: it calls Prism's own settings getters and serializes enums/ordered lists,
     * never a second preferences store. The System UI bridge polls this on each song snapshot so
     * changes made while a song is playing are picked up without restarting either app.
     */
    private static JSONObject readLyricsLook(String songId) {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object app = thread.getMethod("currentApplication").invoke(null);
            Object container = invokeNoArgs(app, "getContainer");
            Object settingsRepository = property(container, "settings");
            Object settings = property(settingsRepository, "current");
            Object look = property(settings, "lyricsLook");
            if (settings == null || look == null) return null;

            JSONObject result = new JSONObject();
            result.put("settingsSchemaVersion", 3);

            // LyricsLookPrefs: base template ("Start from"), per-part overrides, and the resolved
            // values Prism actually draws. Keep these names flat for the Canvas consumer.
            String[] lookFields = {
                    "style", "blurAmount", "spacing", "othersAlpha", "sungColor", "sungCustom",
                    "otherColor", "otherCustom", "weight", "backingSize", "shadow", "uppercase",
                    "ahead", "position", "extraSize", "extraColor", "extraItalic", "extraPlace",
                    "background", "immersiveBackground", "bgColor", "bgColor2", "everywhere",
                    "gapMark", "motion", "wordByWord", "card", "halo", "boldLines",
                    "motionNow", "wordByWordNow", "cardNow", "haloNow", "boldLinesNow",
                    "weightNow", "sizeScale", "pageBackground", "onCard", "changedFromStart"
            };
            JSONObject lyrics = copyProperties(look, lookFields);
            Object ahead = property(look, "ahead");
            Object position = property(look, "position");
            put(lyrics, "aheadLines", property(ahead, "lines"));
            put(lyrics, "positionFraction", property(position, "fraction"));

            // AppSettings properties used by the phone's lyric strip, translation, romanization,
            // font loader and background choices. These are independent of the full-screen page UI.
            String[] lyricsSettingsFields = {
                    "lyricsOnPlayer", "lyricsAlign", "lyricsStripAlign", "lyricsStripTwoLines",
                    "lyricsProgressLine", "lyricsBlur", "lyricsTranslate", "lyricsTranslateTo",
                    "lyricsSourceButton", "lyricsOnMobileData", "lyricsScale", "lyricsFont",
                    "lyricsFontName", "romanize", "romanizeAll", "textScale", "fontChoice",
                    "appFontName", "fontStamp", "lyricsOrder", "lyricsOff", "preferSynced",
                    "skipCensored"
            };
            copyProperties(lyrics, settings, lyricsSettingsFields);

            // The player has a separate, richer settings surface. Serialize the exact AppSettings
            // property names rather than aliases guessed from an older Prism version.
            String[] playerFields = {
                    "playerLayout", "playerDeck", "playerButtons", "sessionButtons",
                    "playerTitleAlign", "playerTitleSize", "playerTitleScroll", "playerTimes",
                    "playerBackground", "playerBackdropShow", "playerArtCorners", "coverSize",
                    "transportStyle", "scrubberStyle", "scrubberColor", "scrubberThickness",
                    "scrubberThumb", "volumeMatchesProgress", "hideVolumeBar", "volumeControl",
                    "appVolume", "showPlayingFrom", "showOutputDevice", "showQuality",
                    "showTrackLength", "showVideoSwitch", "playerShuffleRepeat", "playerNavBand",
                    "playerHandle", "lyricsOnPlayer", "lyricsStripAlign", "lyricsStripTwoLines",
                    "fullBleedArtwork", "videoFullBleed", "canvasEnabled", "canvasFillsBackground",
                    "canvasOnCellular", "artShrinkOnPause", "immersivePlayer", "immersiveArt",
                    "immersiveLyrics", "immersiveControls", "lyricsProgressLine", "showNerdStats",
                    "miniPlayerStyle", "miniPlayerJoined", "miniProgress", "miniSkipButtons",
                    "miniPrevButton", "backdropDim", "backdropMotion", "backdrops", "textScale",
                    "fontChoice", "appFontName", "fontStamp", "lyricsFont", "lyricsFontName",
                    "playerBlur", "meshSpeed", "meshIntensity", "bestPartButton",
                    "mix", "mixSeconds", "mixAuto", "mixBeatMatch", "mixKeepAlbums",
                    "showPlayingFrom"
            };
            JSONObject player = copyProperties(settings, playerFields);

            // Nested values in Prism 1.8.2.1. The earlier bridge looked for several of these on
            // AppSettings itself, where they do not exist (hidden parts, crossfade, type and shapes).
            Object view = property(settings, "view");
            String[] viewFields = {
                    "panelPlayer", "immersiveVideo", "bleedFit", "coverFlight", "coverFlightBack",
                    "videoControlsHide", "videoButtonsHide", "crossfadeMark", "crossfadeShape",
                    "blendCovers", "buttonsPill", "parts", "gradientDepth", "backdropFade",
                    "bleedRoomy", "playingInPageColor"
            };
            JSONObject viewJson = copyProperties(view, viewFields);
            Object parts = property(view, "parts");
            copyProperties(player, parts, "hidden", "menuHidden", "menuTiles");
            put(player, "hiddenParts", property(parts, "hidden"));
            player.put("view", viewJson);

            Object type = property(settings, "type");
            JSONObject typeJson = copyProperties(type, "weight", "spacing", "titleFont", "playerTitleWeight", "classic");
            Object typeSpacing = property(property(type, "spacing"), "em");
            put(lyrics, "typeSpacingEm", typeSpacing);
            put(player, "typeSpacingEm", typeSpacing);
            put(player, "playerTitleWeight", property(type, "playerTitleWeight"));
            put(player, "titleFont", property(type, "titleFont"));
            player.put("type", typeJson);

            Object shapes = property(settings, "shapes");
            JSONObject shapesJson = copyProperties(shapes, "cornerScale", "songs", "albums", "artists", "player", "button", "playOutlined", "tabTint", "classicControls");
            put(player, "coverShape", property(shapes, "player"));
            put(player, "buttonShape", property(shapes, "button"));
            player.put("shapes", shapesJson);

            Object sound = property(settings, "sound");
            JSONObject soundJson = copyProperties(sound, "crossfadeCurve", "normalizeTarget", "normalizeAlbums",
                    "musicSpeed", "musicPitch", "skipSilenceEpisodes", "pauseFade");
            player.put("sound", soundJson);
            put(player, "crossfadeCurve", property(sound, "crossfadeCurve"));

            Object behavior = property(settings, "behavior");
            JSONObject behaviorJson = copyProperties(behavior, "panelSwipes", "scrollShowsControls",
                    "playerRemembersPage", "gesturesEverywhere", "pauseOnMute", "lockPortrait");
            player.put("behavior", behaviorJson);

            // Preserve the existing flat names consumed by PrismLyricsPainter/MediaBody. The nested
            // objects are the complete, explicit settings contract for future card-layout work.
            copyJson(lyrics, result);
            copyJson(player, result);
            result.put("lyricsPreferences", lyrics);
            result.put("playerPreferences", player);

            // Romanization is a global setting with a per-song override in Prism's Romanizer.
            Object romanizer = property(container, "romanizer");
            Object overridesFlow = property(romanizer, "overrides");
            Object overrideValues = property(overridesFlow, "value");
            Object songOverride = overrideValues instanceof Map<?, ?> ? ((Map<?, ?>) overrideValues).get(songId) : null;
            if (songOverride instanceof Boolean) result.put("romanizeSongOverride", songOverride);
            String globalRomanize = enumName(property(settings, "romanize"), "OFF");
            String effectiveRomanize = globalRomanize;
            if (Boolean.FALSE.equals(songOverride)) effectiveRomanize = "OFF";
            else if (Boolean.TRUE.equals(songOverride) && "OFF".equals(globalRomanize)) effectiveRomanize = "UNDER";
            result.put("romanizeModeForSong", effectiveRomanize);
            result.put("romanizeEnabledForSong", !"OFF".equals(effectiveRomanize));
            lyrics.put("romanizeSongOverride", songOverride == null ? JSONObject.NULL : songOverride);
            lyrics.put("romanizeModeForSong", effectiveRomanize);
            lyrics.put("romanizeEnabledForSong", !"OFF".equals(effectiveRomanize));
            return result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JSONObject copyProperties(Object source, String... fields) throws Exception {
        JSONObject result = new JSONObject();
        copyProperties(result, source, fields);
        return result;
    }

    private static void copyProperties(JSONObject target, Object source, String... fields) throws Exception {
        if (source == null || target == null || fields == null) return;
        for (String field : fields) putProperty(target, field, source, field);
    }

    private static void copyJson(JSONObject source, JSONObject target) throws Exception {
        if (source == null || target == null) return;
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            target.put(key, source.get(key));
        }
    }

    private static void put(JSONObject target, String key, Object value) throws Exception {
        if (target != null && value != null) target.put(key, jsonValue(value));
    }

    private static void putProperty(JSONObject target, String key, Object source, String property) throws Exception {
        Object value = property(source, property);
        if (value != null) target.put(key, jsonValue(value));
    }

    /** Convert Prism enums and ordered setting lists without leaking implementation objects. */
    private static Object jsonValue(Object value) throws Exception {
        if (value == null) return JSONObject.NULL;
        if (value instanceof Enum<?>) return ((Enum<?>) value).name();
        if (value instanceof Iterable<?>) {
            JSONArray result = new JSONArray();
            for (Object item : (Iterable<?>) value) result.put(jsonValue(item));
            return result;
        }
        if (value instanceof Map<?, ?>) {
            JSONObject result = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), jsonValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof JSONObject || value instanceof JSONArray) return value;
        return String.valueOf(value);
    }

    private static Object property(Object target, String name) throws Exception {
        if (target == null || name == null || name.isEmpty()) return null;
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        String[] candidates = {"get" + capitalized, "is" + capitalized, name};
        for (String candidate : candidates) {
            try {
                return invokeNoArgs(target, candidate);
            } catch (NoSuchMethodException ignored) {
                // Kotlin Boolean properties may have an isX getter; try the next spelling.
            }
        }
        return null;
    }

    private static String enumName(Object value, String fallback) {
        return value instanceof Enum<?> ? ((Enum<?>) value).name() : (value == null ? fallback : String.valueOf(value));
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isSystemUiCaller(Context context) {
        try {
            int uid = Binder.getCallingUid();
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages == null) return false;
            for (String packageName : packages) {
                if (PACKAGE_SYSTEM_UI.equals(packageName)) return true;
            }
        } catch (Throwable ignored) {
            // Deny on any identity lookup failure.
        }
        return false;
    }

    private static void copy(JSONObject target, Object source, String... properties) {
        for (String property : properties) {
            try {
                Object value = invokeNoArgs(source, "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
                if (value != null) target.put(property, value);
            } catch (Throwable ignored) {
                // Optional across Prism versions; unknown fields are simply omitted.
            }
        }
    }

    /**
     * When Prism's PlayerConnection is bound, read its public ExoPlayer property for state that is
     * not duplicated by Android's MediaSession. All fields are optional.
     */
    private static void copyPlayerState(JSONObject target, Object container) {
        try {
            Object connection = invokeNoArgs(container, "getPlayer");
            Object player = invokeNoArgs(connection, "getPlayer");
            if (player == null) return;

            put(target, "playbackPositionMs", invokeNoArgs(player, "getCurrentPosition"));
            put(target, "bufferedPositionMs", invokeNoArgs(player, "getBufferedPosition"));
            put(target, "durationMs", invokeNoArgs(player, "getDuration"));
            put(target, "isPlaying", invokeNoArgs(player, "isPlaying"));
            put(target, "mediaItemCount", invokeNoArgs(player, "getMediaItemCount"));
            put(target, "mediaItemIndex", invokeNoArgs(player, "getCurrentMediaItemIndex"));
            put(target, "shuffle", invokeNoArgs(player, "getShuffleModeEnabled"));
            put(target, "repeatMode", invokeNoArgs(player, "getRepeatMode"));

            Object parameters = invokeNoArgs(player, "getPlaybackParameters");
            put(target, "playbackSpeed", invokeNoArgs(parameters, "getSpeed"));
        } catch (Throwable ignored) {
            // The app may expose diagnostics while its UI-facing controller is not bound.
        }
    }



    private static Object invokeNoArgs(Object target, String name) throws Exception {
        if (target == null) return null;
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invokeOneArg(Object target, String name, Object value) throws Exception {
        if (target == null || value == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0].isAssignableFrom(value.getClass())) {
                method.setAccessible(true);
                return method.invoke(target, value);
            }
        }
        throw new NoSuchMethodException(name);
    }
}
