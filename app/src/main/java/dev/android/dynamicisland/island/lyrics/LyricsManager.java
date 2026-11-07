package dev.android.dynamicisland.island.lyrics;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LyricSync provider cascade for the media card. Word-timed Kugou KRC and Netease YRC
 * are preferred; line-timed LRCLIB is the fallback. Requests are serialized off the UI
 * thread, deduplicated per track and cached including misses.
 */
public final class LyricsManager {
    private static final String LRCLIB="https://lrclib.net/api";
    private static final String KUGOU_SEARCH="https://krcs.kugou.com/search";
    private static final String KUGOU_DOWNLOAD="https://lyrics.kugou.com/download";
    private static final String NETEASE_SEARCH="https://music.163.com/api/search/get";
    private static final String NETEASE_LYRIC="https://music.163.com/api/song/lyric";
    private static final String USER_AGENT="coobvo (https://github.com/cbv-ooo/coobvo)";
    private static final String BROWSER_UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/132.0 Safari/537.36";
    private static final long READY_TTL_MS=12L*60L*60L*1000L;
    private static final long MISS_TTL_MS=3L*60L*1000L;
    private static final long MAX_RETRY_AFTER_MS=60L*1000L;
    private static final long MIN_REQUEST_INTERVAL_MS=350L;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"coobvo-lyrics");t.setDaemon(true);return t;
    });
    private static final Map<String,Entry> CACHE=new HashMap<>();
    private static final Map<String,List<Runnable>> WAITERS=new HashMap<>();
    private static volatile long retryAfterUptime,lastRequestUptime,lastNetworkRequestUptime;

    private LyricsManager() { }

    private static final class Entry {
        final MediaLyrics lyrics; final long expiresAt;
        Entry(MediaLyrics lyrics,long expiresAt){this.lyrics=lyrics;this.expiresAt=expiresAt;}
    }
    private static final class KugouCandidate {
        final String id,key,song,singer; final boolean krc; final double score;
        KugouCandidate(String id,String key,String song,String singer,boolean krc,double score){
            this.id=id;this.key=key;this.song=song;this.singer=singer;this.krc=krc;this.score=score;
        }
    }
    private static final class NeteaseCandidate {
        final long id; final double score;
        NeteaseCandidate(long id,double score){this.id=id;this.score=score;}
    }

    public static MediaLyrics get(String title,String artist) {
        String key=key(title,artist);
        synchronized(CACHE){
            Entry entry=CACHE.get(key);
            if(entry==null)return null;
            if(SystemClock.uptimeMillis()>=entry.expiresAt){CACHE.remove(key);return null;}
            return entry.lyrics;
        }
    }

    public static boolean isLoading(String title,String artist) {
        synchronized(CACHE){return WAITERS.containsKey(key(title,artist));}
    }

    public static void request(Context context,String title,String artist,long durationMs,Runnable completed) {
        if(context==null||blank(title)||blank(artist))return;
        String key=key(title,artist);
        synchronized(CACHE){
            Entry entry=CACHE.get(key);
            if(entry!=null&&SystemClock.uptimeMillis()<entry.expiresAt){post(completed);return;}
            if(entry!=null)CACHE.remove(key);
            List<Runnable> waiters=WAITERS.get(key);
            if(waiters!=null){if(completed!=null)waiters.add(completed);return;}
            waiters=new ArrayList<>();if(completed!=null)waiters.add(completed);WAITERS.put(key,waiters);
        }
        Context app=context.getApplicationContext()!=null?context.getApplicationContext():context;
        try{WORKER.execute(()->fetch(app,key,title.trim(),artist.trim(),durationMs));}
        catch(RuntimeException rejected){finish(key,null,MISS_TTL_MS);}
    }

    private static void fetch(Context context,String key,String title,String artist,long durationMs) {
        MediaLyrics result=null;
        try{
            long waitUntil=Math.max(retryAfterUptime,lastRequestUptime+MIN_REQUEST_INTERVAL_MS);
            long pause=waitUntil-SystemClock.uptimeMillis();
            if(pause>0)Thread.sleep(Math.min(pause,MAX_RETRY_AFTER_MS));
            lastRequestUptime=SystemClock.uptimeMillis();

            // Better word timing wins. Try independent catalogues even if one returns only LRC.
            result=fetchKugou(title,artist,durationMs);
            if(result==null||result.type()!=MediaLyrics.Type.SYLLABLE){
                MediaLyrics netease=fetchNetease(title,artist,durationMs);
                if(better(netease,result))result=netease;
            }
            if(result==null||result.type()!=MediaLyrics.Type.SYLLABLE){
                MediaLyrics lrclib=fetchLrclib(title,artist,durationMs);
                if(better(lrclib,result))result=lrclib;
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(Throwable ignored){/* Network/provider failures never interrupt media controls. */}
        finish(key,result,result==null?MISS_TTL_MS:READY_TTL_MS);
    }

    private static void finish(String key,MediaLyrics result,long ttl) {
        List<Runnable> callbacks;
        synchronized(CACHE){
            CACHE.put(key,new Entry(result,SystemClock.uptimeMillis()+ttl));
            while(CACHE.size()>48)CACHE.remove(CACHE.keySet().iterator().next());
            callbacks=WAITERS.remove(key);
        }
        if(callbacks!=null)for(Runnable callback:callbacks)post(callback);
    }

    private static void post(Runnable callback) {
        if(callback==null)return;
        try{new android.os.Handler(android.os.Looper.getMainLooper()).post(callback);}catch(Throwable ignored){}
    }

    private static MediaLyrics fetchKugou(String title,String artist,long durationMs) {
        try{
            List<String> terms=new ArrayList<>();
            terms.add(artist+" - "+title);terms.add(title);
            List<KugouCandidate> candidates=new ArrayList<>();
            for(String term:terms){
                Uri uri=Uri.parse(KUGOU_SEARCH).buildUpon()
                        .appendQueryParameter("ver","1").appendQueryParameter("man","yes")
                        .appendQueryParameter("client","mobi").appendQueryParameter("hash","")
                        .appendQueryParameter("keyword",term)
                        .appendQueryParameter("duration",durationMs>0?Long.toString(durationMs):"").build();
                JSONObject root=getObject(uri.toString(),"User-Agent",BROWSER_UA);
                JSONArray array=root==null?null:root.optJSONArray("candidates");
                if(array==null)continue;
                for(int i=0;i<array.length();i++){
                    JSONObject item=array.optJSONObject(i);if(item==null)continue;
                    String id=item.optString("id",""),access=item.optString("accesskey","");
                    String song=item.optString("song",""),singer=item.optString("singer","");
                    if(blank(id)||blank(access))continue;
                    double score=matchScore(title,artist,durationMs,song,singer,item.optLong("duration",0));
                    if(score>=0.45)candidates.add(new KugouCandidate(id,access,song,singer,item.optInt("krctype",0)==2,score));
                }
                if(!candidates.isEmpty())break;
            }
            candidates.sort((a,b)->Double.compare(b.score+(b.krc?.10:0),a.score+(a.krc?.10:0)));
            int limit=Math.min(3,candidates.size());
            for(int i=0;i<limit;i++){
                KugouCandidate candidate=candidates.get(i);
                Uri uri=Uri.parse(KUGOU_DOWNLOAD).buildUpon().appendQueryParameter("ver","1")
                        .appendQueryParameter("client","pc").appendQueryParameter("charset","utf8")
                        .appendQueryParameter("id",candidate.id).appendQueryParameter("accesskey",candidate.key)
                        .appendQueryParameter("fmt",candidate.krc?"krc":"lrc").build();
                JSONObject response=getObject(uri.toString(),BROWSER_UA);
                String encoded=response==null?"":response.optString("content","");
                if(encoded.isEmpty())continue;
                byte[] bytes=Base64.getMimeDecoder().decode(encoded);
                String text=candidate.krc?MediaLyrics.decryptKrc(bytes):new String(bytes,StandardCharsets.UTF_8);
                MediaLyrics lyrics=candidate.krc?MediaLyrics.parseKrc(text,"Kugou"):MediaLyrics.parseLrc(text,"Kugou");
                if(lyrics!=null&&!lyrics.isEmpty())return lyrics;
            }
        }catch(Throwable ignored){}
        return null;
    }

    private static MediaLyrics fetchNetease(String title,String artist,long durationMs) {
        try{
            List<String> terms=new ArrayList<>();terms.add(title+" "+artist);terms.add(title);
            List<NeteaseCandidate> candidates=new ArrayList<>();
            for(String term:terms){
                Uri uri=Uri.parse(NETEASE_SEARCH).buildUpon().appendQueryParameter("type","1")
                        .appendQueryParameter("limit","10").appendQueryParameter("offset","0")
                        .appendQueryParameter("s",term).build();
                JSONObject root=getObject(uri.toString(),"User-Agent",BROWSER_UA,"Referer","https://music.163.com");
                JSONObject result=root==null?null:root.optJSONObject("result");
                JSONArray songs=result==null?null:result.optJSONArray("songs");
                if(songs==null)continue;
                for(int i=0;i<songs.length();i++){
                    JSONObject song=songs.optJSONObject(i);if(song==null)continue;
                    long id=song.optLong("id",0);if(id<=0)continue;
                    JSONArray artists=song.optJSONArray("artists");
                    StringBuilder names=new StringBuilder();
                    if(artists!=null)for(int j=0;j<artists.length();j++){
                        JSONObject a=artists.optJSONObject(j);String n=a==null?"":a.optString("name","");
                        if(!n.isEmpty())names.append(names.length()==0?"":", ").append(n);
                    }
                    double score=matchScore(title,artist,durationMs,song.optString("name",""),names.toString(),song.optLong("duration",0));
                    if(score>=0.45)candidates.add(new NeteaseCandidate(id,score));
                }
                if(!candidates.isEmpty())break;
            }
            candidates.sort((a,b)->Double.compare(b.score,a.score));
            for(int i=0;i<Math.min(3,candidates.size());i++){
                Uri uri=Uri.parse(NETEASE_LYRIC).buildUpon().appendQueryParameter("id",Long.toString(candidates.get(i).id))
                        .appendQueryParameter("lv","1").appendQueryParameter("tv","-1")
                        .appendQueryParameter("rv","-1").appendQueryParameter("yv","1").build();
                JSONObject root=getObject(uri.toString(),BROWSER_UA,"Referer","https://music.163.com");
                if(root==null)continue;
                JSONObject yrc=root.optJSONObject("yrc");
                String wordText=yrc==null?"":yrc.optString("lyric","");
                MediaLyrics lyrics=MediaLyrics.parseYrc(wordText,"Netease");
                if(lyrics!=null&&!lyrics.isEmpty())return lyrics;
                JSONObject lrc=root.optJSONObject("lrc");
                String lineText=lrc==null?"":lrc.optString("lyric","");
                lyrics=MediaLyrics.parseLrc(lineText,"Netease");
                if(lyrics!=null&&!lyrics.isEmpty())return lyrics;
            }
        }catch(Throwable ignored){}
        return null;
    }

    private static MediaLyrics fetchLrclib(String title,String artist,long durationMs) {
        try{
            Uri exact=Uri.parse(LRCLIB+"/get").buildUpon()
                    .appendQueryParameter("track_name",title).appendQueryParameter("artist_name",artist)
                    .appendQueryParameter("duration",Long.toString(Math.max(0,durationMs/1000))).build();
            JSONObject object=getObject(exact.toString(),"User-Agent",USER_AGENT);
            MediaLyrics lyrics=parseLrclib(object);
            if(lyrics!=null)return lyrics;
            Uri search=Uri.parse(LRCLIB+"/search").buildUpon()
                    .appendQueryParameter("track_name",title).appendQueryParameter("artist_name",artist).build();
            JSONArray results=getArray(search.toString(),"User-Agent",USER_AGENT);
            JSONObject best=bestLrclibMatch(results,title,artist,durationMs);
            return parseLrclib(best);
        }catch(Throwable ignored){return null;}
    }

    private static MediaLyrics parseLrclib(JSONObject object) {
        if(object==null)return null;
        MediaLyrics lyrics=MediaLyrics.parseLrc(object.optString("syncedLyrics",""),"LRCLIB");
        if(lyrics!=null&&!lyrics.isEmpty())return lyrics;
        String plain=object.optString("plainLyrics","");
        if(blank(plain))return null;
        List<MediaLyrics.Line> lines=new ArrayList<>();long time=0;
        for(String raw:plain.split("\\r?\\n")){
            String line=raw.trim();if(line.isEmpty())continue;
            lines.add(new MediaLyrics.Line(time,time+5000,line));time+=5000;
        }
        return lines.isEmpty()?null:new MediaLyrics(MediaLyrics.Type.STATIC,"LRCLIB",lines);
    }

    private static JSONObject bestLrclibMatch(JSONArray results,String title,String artist,long durationMs) {
        if(results==null)return null;
        JSONObject best=null;double score=.45;
        for(int i=0;i<results.length();i++){
            JSONObject item=results.optJSONObject(i);if(item==null)continue;
            if(blank(item.optString("syncedLyrics",""))&&blank(item.optString("plainLyrics","")))continue;
            double s=matchScore(title,artist,durationMs,item.optString("trackName",""),
                    item.optString("artistName",""),Math.round(item.optDouble("duration",0)*1000));
            if(s>score){score=s;best=item;}
        }
        return best;
    }

    private static JSONObject getObject(String address,String... headers)throws Exception {
        Object value=get(address,headers);return value instanceof JSONObject?(JSONObject)value:null;
    }
    private static JSONArray getArray(String address,String... headers)throws Exception {
        Object value=get(address,headers);return value instanceof JSONArray?(JSONArray)value:null;
    }

    private static Object get(String address,String... headers)throws Exception {
        long pause=lastNetworkRequestUptime+MIN_REQUEST_INTERVAL_MS-SystemClock.uptimeMillis();
        if(pause>0)Thread.sleep(pause);
        lastNetworkRequestUptime=SystemClock.uptimeMillis();
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setRequestMethod("GET");connection.setConnectTimeout(4000);connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent",USER_AGENT);connection.setRequestProperty("Accept","application/json");
        for(int i=0;i+1<headers.length;i+=2)connection.setRequestProperty(headers[i],headers[i+1]);
        try{
            int code=connection.getResponseCode();
            if(code==429){
                String retry=connection.getHeaderField("Retry-After");long seconds=1;
                try{if(retry!=null)seconds=Long.parseLong(retry.trim());}catch(NumberFormatException ignored){}
                retryAfterUptime=SystemClock.uptimeMillis()+Math.max(1000,Math.min(MAX_RETRY_AFTER_MS,seconds*1000));
                return null;
            }
            if(code<200||code>=300)return null;
            try(InputStream in=connection.getInputStream();BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                StringBuilder body=new StringBuilder();char[] buffer=new char[4096];int read;
                while((read=reader.read(buffer))!=-1){if(body.length()+read>1_000_000)return null;body.append(buffer,0,read);}
                String json=body.toString().trim();
                if(json.startsWith("["))return new JSONArray(json);
                if(json.startsWith("{"))return new JSONObject(json);
                return null;
            }
        }finally{connection.disconnect();}
    }

    private static boolean better(MediaLyrics candidate,MediaLyrics current) {
        if(candidate==null||candidate.isEmpty())return false;
        if(current==null||current.isEmpty())return true;
        int cr=rank(candidate.type()),br=rank(current.type());
        return cr>br||(cr==br&&candidate.size()>current.size());
    }
    private static int rank(MediaLyrics.Type type) {
        return type==MediaLyrics.Type.SYLLABLE?3:type==MediaLyrics.Type.LINE?2:1;
    }

    private static double matchScore(String wantedTitle,String wantedArtist,long wantedDuration,
                                     String gotTitle,String gotArtist,long gotDuration) {
        double titleScore=similarity(wantedTitle,gotTitle);
        if(titleScore<0.25)return 0;
        double artistScore=blank(wantedArtist)||blank(gotArtist)?0.5:similarity(wantedArtist,gotArtist);
        if(!blank(wantedArtist)&&!blank(gotArtist)&&artistScore<0.25)return 0;
        double durationScore=wantedDuration<=0||gotDuration<=0?0.5:
                Math.max(0,1.0-Math.abs(wantedDuration-gotDuration)/15000.0);
        return titleScore*0.50+artistScore*0.35+durationScore*0.15;
    }
    private static double similarity(String a,String b) {
        String na=normalize(a),nb=normalize(b);
        if(na.isEmpty()||nb.isEmpty())return 0;
        if(na.equals(nb))return 1;
        if(na.contains(nb)||nb.contains(na))return .78;
        java.util.HashSet<String> left=new java.util.HashSet<>(),right=new java.util.HashSet<>();
        for(int i=0;i+1<na.length();i++)left.add(na.substring(i,i+2));
        for(int i=0;i+1<nb.length();i++)right.add(nb.substring(i,i+2));
        if(left.isEmpty()||right.isEmpty())return 0;
        int hits=0;for(String part:left)if(right.contains(part))hits++;
        return 2.0*hits/(left.size()+right.size());
    }
    private static String key(String title,String artist){return normalize(title)+"|"+normalize(artist);}
    private static String normalize(String text){return text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    private static boolean blank(String value){return value==null||value.trim().isEmpty();}
}
