package dev.android.dynamicisland.island.source;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import dev.android.dynamicisland.island.lyrics.MediaLyrics;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Reads Prism's optional, local-only Morphe bridge. Calls are made off the System UI thread.
 * The snapshot contains Prism-owned diagnostics and, when available, Prism's own lyric state.
 */
public final class PrismMediaBridgeClient {
    private static final String VERSION_KEY = "coobvo.prism.bridge.version";
    private static final String URI_KEY = "coobvo.prism.bridge.uri";
    private static final String SNAPSHOT_COLUMN = "snapshot";
    private static final long CACHE_MS = 35L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CoobvoPrismMedia");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, Cached> CACHE = new LinkedHashMap<String, Cached>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > 32;
        }
    };

    private PrismMediaBridgeClient() {}

    /**
     * Media3 carries prism.song in MediaMetadata.extras, but Android's bridged MediaMetadata does not
     * consistently expose custom extras through MediaDescription on every System UI build. The
     * media-session mediaId is still Prism's Song.id, so it is the authoritative URI fallback.
     */
    static Bundle ensureBridgeExtras(Bundle source, String mediaId) {
        Bundle result = source == null ? new Bundle() : new Bundle(source);
        String uri = result.getString(URI_KEY);
        if (uri == null || uri.isEmpty()) {
            if (mediaId == null || mediaId.isEmpty()) return null;
            result.putString(URI_KEY, "content://com.prism.music.artwork/bridge/" + Uri.encode(mediaId));
        }
        result.putInt(VERSION_KEY, Math.max(1, result.getInt(VERSION_KEY, 0)));
        return result;
    }

    public static final class Snapshot {
        public final String info;
        public final MediaLyrics lyrics;
        public final String lyricsStatus;
        public final String lyricsLook;
        public final String rawJson;
        public final float[] visualizerBands;
        public final float visualizerLevel;
        public final long crossfadeStartMs;
        public final long crossfadeLengthMs;
        public final String crossfadeSongId;
        public final String crossfadeFromId;
        public final String crossfadeToId;
        public final float crossfadeProgress;
        public final float playerVolume;
        private final boolean lyricsExtrasPending;

        public boolean needsLyricsRefresh() {
            return "UNKNOWN".equals(lyricsStatus) || "LOADING".equals(lyricsStatus)
                    || "WAITING".equals(lyricsStatus) || lyricsExtrasPending;
        }

        /** Prism computes romanization/translation after LyricsState has already become AVAILABLE. */
        public boolean needsLyricsExtrasRefresh() {
            return lyricsExtrasPending;
        }

        private Snapshot(String info, MediaLyrics lyrics, String lyricsStatus, String lyricsLook,
                         String rawJson, boolean lyricsExtrasPending, JSONObject data) {
            this.info = info;
            this.lyrics = lyrics;
            this.lyricsStatus = lyricsStatus == null ? "UNKNOWN" : lyricsStatus;
            this.lyricsLook = lyricsLook;
            this.rawJson = rawJson;
            this.lyricsExtrasPending = lyricsExtrasPending;
            this.visualizerBands = readBands(data.optJSONArray("visualizerBands"));
            this.visualizerLevel = clamp(data.optDouble("visualizerLevel", 0.0d));
            this.crossfadeStartMs = data.optLong("crossfadeStartMs", -1L);
            this.crossfadeLengthMs = data.optLong("crossfadeLengthMs", 0L);
            this.crossfadeSongId = emptyToNull(data.optString("crossfadeSongId", null));
            this.crossfadeFromId = emptyToNull(data.optString("crossfadeFromId", null));
            this.crossfadeToId = emptyToNull(data.optString("crossfadeToId", null));
            this.crossfadeProgress = clamp(data.optDouble("crossfadeProgress", 0.0d));
            this.playerVolume = clamp(data.optDouble("playerVolume", 1.0d));
        }
    }

    public static void request(Context context, Bundle extras, String trackKey, Consumer<Snapshot> callback) {
        if (context == null || extras == null || callback == null) return;
        final String bridgeUri;
        try {
            if (extras.getInt(VERSION_KEY, 0) < 1) return;
            bridgeUri = extras.getString(URI_KEY);
        } catch (RuntimeException ignored) {
            return;
        }
        if (bridgeUri == null || bridgeUri.isEmpty()) return;

        final String key = trackKey == null ? bridgeUri : trackKey;
        final long now = SystemClock.elapsedRealtime();
        synchronized (CACHE) {
            Cached cached = CACHE.get(key);
            if (cached != null && now - cached.time < CACHE_MS) {
                Snapshot value = cached.snapshot;
                if (value != null) MAIN.post(() -> callback.accept(value));
                return;
            }
        }

        final Context appContext = context.getApplicationContext();
        final Context app = appContext != null ? appContext : context;
        IO.execute(() -> {
            Snapshot snapshot = read(app, bridgeUri);
            synchronized (CACHE) {
                CACHE.put(key, new Cached(snapshot, SystemClock.elapsedRealtime()));
            }
            if (snapshot != null) MAIN.post(() -> callback.accept(snapshot));
        });
    }

    private static Snapshot read(Context context, String bridgeUri) {
        try (Cursor cursor = context.getContentResolver().query(
                Uri.parse(bridgeUri), new String[]{SNAPSHOT_COLUMN}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            int column = cursor.getColumnIndex(SNAPSHOT_COLUMN);
            if (column < 0) return null;
            String json = cursor.getString(column);
            if (json == null || json.isEmpty()) return null;
            JSONObject data = new JSONObject(json);
            JSONObject prismLyrics = data.optJSONObject("prismLyrics");
            MediaLyrics lyrics = null;
            String status = "UNKNOWN";
            String look = null;
            boolean extrasPending = false;
            JSONObject lyricLook = null;
            if (prismLyrics != null) {
                status = prismLyrics.optString("status", "UNKNOWN");
                lyrics = MediaLyrics.fromPrismSnapshot(prismLyrics.optJSONObject("lyrics"));
                lyricLook = prismLyrics.optJSONObject("look");
            }
            // Player settings are exported even before LyricsState has a populated lyric object.
            if (lyricLook == null) lyricLook = data.optJSONObject("prismSettings");
            if (lyricLook != null) {
                look = lyricLook.toString();
                boolean wantsRomanized = lyricLook.optBoolean("romanizeEnabledForSong", false);
                boolean wantsTranslated = lyricLook.optBoolean("lyricsTranslate", false);
                extrasPending = (wantsRomanized && (lyrics == null || !lyrics.hasRomanizedLines()))
                        || (wantsTranslated && (lyrics == null || !lyrics.hasTranslatedLines()));
            }
            // The extension's elapsed timestamp changes on every poll. Exclude it so callers can
            // tell an actual lyric/preference change from an unchanged bridge response.
            JSONObject stable = new JSONObject(data.toString());
            stable.remove("updatedAtElapsedRealtime");
            return new Snapshot(format(data), lyrics, status, look, stable.toString(), extrasPending, data);
        } catch (Throwable ignored) {
            // The bridge is optional. Missing/incompatible patch must never break media cards.
            return null;
        }
    }

    private static String format(JSONObject data) {
        String codec = data.optString("codec", "");
        String format = displayCodec(codec);
        if (format.isEmpty()) format = displayContainer(data.optString("container", ""));
        StringBuilder result = new StringBuilder(48);
        append(result, format);

        int bitDepth = data.optInt("bitDepth", 0);
        if (bitDepth > 0) append(result, bitDepth + "-bit");

        int sampleRate = data.optInt("sampleRate", 0);
        if (sampleRate > 0) {
            String rate = sampleRate % 1000 == 0
                    ? Integer.toString(sampleRate / 1000)
                    : String.format(Locale.US, "%.1f", sampleRate / 1000.0d);
            append(result, rate + " kHz");
        }

        int channels = data.optInt("channels", 0);
        if (channels == 1) append(result, "Mono");
        else if (channels == 2) append(result, "Stereo");
        else if (channels > 2) append(result, channels + " ch");

        int bitrate = data.optInt("streamBitrateKbps", 0);
        if (bitrate > 0) append(result, bitrate + " kbps");
        return result.length() == 0 ? null : result.toString();
    }

    private static String displayCodec(String codec) {
        if (codec == null || codec.isEmpty()) return "";
        String value = codec.toLowerCase(Locale.ROOT);
        if (value.contains("flac")) return "FLAC";
        if (value.contains("opus")) return "Opus";
        if (value.contains("vorbis")) return "Vorbis";
        if (value.contains("mp4a") || value.contains("aac")) return "AAC";
        if (value.contains("alac")) return "ALAC";
        if (value.contains("mp3") || value.contains("mpeg")) return "MP3";
        if (value.contains("pcm")) return "PCM";
        if (value.contains("e-ac-3") || value.contains("eac3")) return "E-AC-3";
        if (value.contains("ac-3") || value.contains("ac3")) return "AC-3";
        return codec.toUpperCase(Locale.ROOT);
    }

    private static String displayContainer(String container) {
        if (container == null || container.isEmpty()) return "";
        int slash = container.lastIndexOf('/');
        String value = slash >= 0 ? container.substring(slash + 1) : container;
        value = value.replace("x-", "").replace("vnd.", "");
        return value.toUpperCase(Locale.ROOT);
    }

    private static void append(StringBuilder target, String value) {
        if (value == null || value.isEmpty()) return;
        if (target.length() > 0) target.append(" · ");
        target.append(value);
    }

    private static float[] readBands(JSONArray values) {
        if (values == null || values.length() == 0) return null;
        int count = Math.min(64, values.length());
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            double value = values.optDouble(i, 0.0d);
            result[i] = clamp(value);
        }
        return result;
    }

    private static float clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0f;
        return (float) Math.max(0.0d, Math.min(1.0d, value));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static final class Cached {
        final Snapshot snapshot;
        final long time;
        Cached(Snapshot snapshot, long time) {
            this.snapshot = snapshot;
            this.time = time;
        }
    }
}
