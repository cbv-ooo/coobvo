package dev.coobvo.prismmedia;

import android.content.ContentProvider;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemClock;

import org.json.JSONObject;

import java.lang.reflect.Method;

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
            extras.putString(BRIDGE_URI, CONTENT_URI);

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
        if (provider == null || uri == null || !"bridge".equals(uri.getLastPathSegment())) return null;

        MatrixCursor denied = new MatrixCursor(new String[]{SNAPSHOT_COLUMN});
        try {
            Context context = provider.getContext();
            if (context == null || !isSystemUiCaller(context)) return denied;

            Object app = context.getApplicationContext();
            Object container = invokeNoArgs(app, "getContainer");
            if (container == null) return denied;
            Object stateFlow = invokeNoArgs(container, "getNerdStats");
            Object stats = invokeNoArgs(stateFlow, "getValue");
            if (stats == null) return denied;

            JSONObject snapshot = new JSONObject();
            snapshot.put("version", 1);
            snapshot.put("updatedAtElapsedRealtime", SystemClock.elapsedRealtime());

            copy(snapshot, stats,
                    "videoId", "source", "itag", "container", "codec", "decoder",
                    "streamBitrateKbps", "sampleRate", "bitDepth", "channels", "contentLength",
                    "client", "outputEncoding", "outputSampleRate", "offload", "bandwidthKbps",
                    "bufferMs", "loudnessDb", "loudnessMeasured", "normalizationGainDb",
                    "audioSessionId", "outputDevice", "spatializer", "effects",
                    "videoResolution", "videoCodec", "droppedFrames", "underruns");

            copyPlayerState(snapshot, container);
            MatrixCursor result = new MatrixCursor(new String[]{SNAPSHOT_COLUMN});
            result.addRow(new Object[]{snapshot.toString()});
            return result;
        } catch (Throwable ignored) {
            return denied;
        }
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
