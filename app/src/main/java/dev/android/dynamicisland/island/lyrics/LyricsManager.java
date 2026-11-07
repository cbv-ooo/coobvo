package dev.android.dynamicisland.island.lyrics;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small LRCLIB adapter for the media card. Network work is serialized off the UI thread,
 * synced LRC is preferred, and both positive and negative responses are cached.
 */
public final class LyricsManager {
    private static final String API = "https://lrclib.net/api";
    private static final String USER_AGENT = "coobvo (https://github.com/cbv-ooo/coobvo)";
    private static final long READY_TTL_MS = 12L * 60L * 60L * 1000L;
    private static final long MISS_TTL_MS = 3L * 60L * 1000L;
    private static final long MAX_RETRY_AFTER_MS = 60L * 1000L;
    /** Serial requests are additionally spaced to avoid bursts while tracks change quickly. */
    private static final long MIN_REQUEST_INTERVAL_MS = 350L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "coobvo-lyrics");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static final Map<String, List<Runnable>> WAITERS = new HashMap<>();
    private static volatile long retryAfterUptime;
    private static volatile long lastRequestUptime;

    private LyricsManager() { }

    private static final class Entry {
        final MediaLyrics lyrics;
        final long expiresAt;
        Entry(MediaLyrics lyrics, long expiresAt) { this.lyrics=lyrics; this.expiresAt=expiresAt; }
    }

    public static MediaLyrics get(String title, String artist) {
        String key=key(title,artist);
        synchronized (CACHE) {
            Entry entry=CACHE.get(key);
            if(entry==null) return null;
            if(android.os.SystemClock.uptimeMillis()>=entry.expiresAt) {
                CACHE.remove(key);
                return null;
            }
            return entry.lyrics;
        }
    }

    public static boolean isLoading(String title, String artist) {
        synchronized (CACHE) { return WAITERS.containsKey(key(title,artist)); }
    }

    public static void request(Context context, String title, String artist, long durationMs, Runnable completed) {
        if(context==null || blank(title) || blank(artist)) return;
        String key=key(title,artist);
        synchronized (CACHE) {
            Entry entry=CACHE.get(key);
            if(entry!=null && android.os.SystemClock.uptimeMillis()<entry.expiresAt) {
                if(completed!=null) completed.run();
                return;
            }
            if(entry!=null) CACHE.remove(key);
            List<Runnable> waiters=WAITERS.get(key);
            if(waiters!=null) {
                if(completed!=null) waiters.add(completed);
                return;
            }
            waiters=new ArrayList<>();
            if(completed!=null) waiters.add(completed);
            WAITERS.put(key,waiters);
        }
        Context app=context.getApplicationContext()!=null?context.getApplicationContext():context;
        WORKER.execute(()->fetch(app,key,title.trim(),artist.trim(),durationMs));
    }

    private static void fetch(Context context,String key,String title,String artist,long durationMs) {
        MediaLyrics result=null;
        long ttl=MISS_TTL_MS;
        try {
            long now=android.os.SystemClock.uptimeMillis();
            long earliest=Math.max(retryAfterUptime,lastRequestUptime+MIN_REQUEST_INTERVAL_MS);
            long pause=earliest-now;
            if(pause>0) Thread.sleep(Math.min(pause,MAX_RETRY_AFTER_MS));
            lastRequestUptime=android.os.SystemClock.uptimeMillis();
            String exact=Uri.parse(API+"/get").buildUpon()
                    .appendQueryParameter("track_name",title)
                    .appendQueryParameter("artist_name",artist)
                    .appendQueryParameter("duration",Long.toString(Math.max(0,durationMs/1000)))
                    .build().toString();
            JSONObject object=getObject(exact);
            result=parseObject(object);
            if(result==null) {
                String search=Uri.parse(API+"/search").buildUpon()
                        .appendQueryParameter("track_name",title)
                        .appendQueryParameter("artist_name",artist)
                        .build().toString();
                JSONArray results=getArray(search);
                JSONObject best=bestMatch(results,title,artist);
                result=parseObject(best);
            }
            if(result!=null) ttl=READY_TTL_MS;
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch(Throwable ignored) {
            // Music UI must remain usable when the network or provider is unavailable.
        }
        List<Runnable> callbacks;
        synchronized(CACHE) {
            CACHE.put(key,new Entry(result,android.os.SystemClock.uptimeMillis()+ttl));
            while(CACHE.size()>48) CACHE.remove(CACHE.keySet().iterator().next());
            callbacks=WAITERS.remove(key);
        }
        if(callbacks!=null) for(Runnable callback:callbacks) {
            try { new android.os.Handler(android.os.Looper.getMainLooper()).post(callback); }
            catch(Throwable ignored) { }
        }
    }

    private static JSONObject getObject(String url) throws Exception {
        Object value=get(url);
        return value instanceof JSONObject?(JSONObject)value:null;
    }

    private static JSONArray getArray(String url) throws Exception {
        Object value=get(url);
        return value instanceof JSONArray?(JSONArray)value:null;
    }

    private static Object get(String address) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent",USER_AGENT);
        connection.setRequestProperty("Accept","application/json");
        try {
            int code=connection.getResponseCode();
            if(code==429) {
                String retry=connection.getHeaderField("Retry-After");
                long seconds=1;
                try { if(retry!=null) seconds=Long.parseLong(retry.trim()); } catch(NumberFormatException ignored) { }
                retryAfterUptime=android.os.SystemClock.uptimeMillis()+Math.max(1000,Math.min(MAX_RETRY_AFTER_MS,seconds*1000));
                return null;
            }
            if(code<200 || code>=300) return null;
            try(InputStream in=connection.getInputStream();
                BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
                StringBuilder body=new StringBuilder();
                char[] buffer=new char[4096];
                int read;
                while((read=reader.read(buffer))!=-1) {
                    if(body.length()+read>1_000_000) return null;
                    body.append(buffer,0,read);
                }
                String json=body.toString().trim();
                if(json.startsWith("[")) return new JSONArray(json);
                if(json.startsWith("{")) return new JSONObject(json);
                return null;
            }
        } finally { connection.disconnect(); }
    }

    private static MediaLyrics parseObject(JSONObject object) {
        if(object==null) return null;
        String synced=object.optString("syncedLyrics","");
        return MediaLyrics.parseLrc(synced);
    }

    private static JSONObject bestMatch(JSONArray array,String title,String artist) {
        if(array==null) return null;
        String wantedTitle=normalize(title),wantedArtist=normalize(artist);
        JSONObject best=null; int bestScore=0;
        for(int i=0;i<array.length();i++) {
            JSONObject candidate=array.optJSONObject(i);
            if(candidate==null || candidate.optString("syncedLyrics","").trim().isEmpty()) continue;
            String gotTitle=normalize(candidate.optString("trackName",""));
            String gotArtist=normalize(candidate.optString("artistName",""));
            if(gotTitle.isEmpty()||gotArtist.isEmpty()) continue;
            int score=(gotTitle.equals(wantedTitle)?3:gotTitle.contains(wantedTitle)||wantedTitle.contains(gotTitle)?1:0)
                    +(gotArtist.equals(wantedArtist)?3:gotArtist.contains(wantedArtist)||wantedArtist.contains(gotArtist)?1:0);
            if(score>bestScore) { bestScore=score;best=candidate; }
        }
        return bestScore>=4?best:null;
    }

    private static String key(String title,String artist) {
        return normalize(title)+"|"+normalize(artist);
    }
    private static String normalize(String text) {
        return text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");
    }
    private static boolean blank(String value) { return value==null||value.trim().isEmpty(); }
}
