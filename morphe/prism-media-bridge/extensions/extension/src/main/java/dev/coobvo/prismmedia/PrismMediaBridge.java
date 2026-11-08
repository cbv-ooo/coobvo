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

            JSONObject lyrics = requestedSongId.isEmpty() ? null : LYRICS_BY_SONG.get(requestedSongId);
            if (lyrics != null) snapshot.put("prismLyrics", new JSONObject(lyrics.toString()));

            MatrixCursor result = new MatrixCursor(new String[]{SNAPSHOT_COLUMN});
            result.addRow(new Object[]{snapshot.toString()});
            return result;
        } catch (Throwable ignored) {
            return denied;
        }
    }

    /**
     * Called after Prism's own LyricsState setters. The state already contains the provider result
     * and its actual word/line timestamps; this method only copies that in-memory data.
     */
    public static void captureLyricsState(Object state) {
        if (state == null) return;
        try {
            Object song = property(state, "song");
            Object idValue = property(song, "id");
            if (idValue == null) return;
            String songId = String.valueOf(idValue);
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

            JSONObject look = readLyricsLook();
            if (look != null) snapshot.put("look", look);
            LYRICS_BY_SONG.put(songId, snapshot);
            if (LYRICS_BY_SONG.size() > 64) {
                for (String oldId : LYRICS_BY_SONG.keySet()) {
                    if (!songId.equals(oldId)) {
                        LYRICS_BY_SONG.remove(oldId);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Optional bridge only: never interfere with Prism's own lyric state or playback.
        }
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
    private static JSONObject readLyricsLook() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object app = thread.getMethod("currentApplication").invoke(null);
            Object container = invokeNoArgs(app, "getContainer");
            Object settingsRepository = property(container, "settings");
            Object settings = property(settingsRepository, "current");
            Object look = property(settings, "lyricsLook");
            if (look == null) return null;

            JSONObject result = new JSONObject();
            String[] lookFields = {
                    "style", "blurAmount", "spacing", "othersAlpha", "sungColor", "sungCustom",
                    "otherColor", "otherCustom", "weight", "backingSize", "shadow", "uppercase",
                    "ahead", "position", "extraSize", "extraColor", "extraItalic", "extraPlace",
                    "background", "immersiveBackground", "bgColor", "bgColor2", "everywhere",
                    "gapMark", "motion", "wordByWord", "card", "halo", "boldLines", "motionNow",
                    "wordByWordNow", "cardNow", "haloNow", "boldLinesNow", "weightNow",
                    "sizeScale", "pageBackground", "onCard"
            };
            for (String field : lookFields) putProperty(result, field, look, field);

            String[] settingsFields = {
                    "lyricsAlign", "lyricsStripAlign", "lyricsStripTwoLines",
                    "romanize", "romanizeAll", "lyricsTranslate", "lyricsTranslateTo"
            };
            for (String field : settingsFields) putProperty(result, field, settings, field);
            return result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void putProperty(JSONObject target, String key, Object source, String property) throws Exception {
        Object value = property(source, property);
        if (value instanceof Enum<?>) value = ((Enum<?>) value).name();
        if (value != null) target.put(key, value);
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

    private static void put(JSONObject target, String key, Object value) throws Exception {
        if (value != null) target.put(key, value);
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
