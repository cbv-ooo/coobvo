package dev.android.dynamicisland.island.lyrics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Immutable line- or word-synced lyrics used by the media card. All times are track-relative milliseconds. */
public final class MediaLyrics {
    public enum Type { SYLLABLE, LINE, STATIC }

    public static final class Word {
        public final long startMs;
        public final long endMs;
        public final String text;
        public final boolean partOfWord;
        public Word(long startMs, long endMs, String text) { this(startMs,endMs,text,false); }
        public Word(long startMs, long endMs, String text, boolean partOfWord) {
            this.startMs=startMs; this.endMs=endMs; this.text=text==null?"":text; this.partOfWord=partOfWord;
        }
    }

    public static final class Line {
        public final long startMs;
        public final long endMs;
        public final String text;
        public final List<Word> words;
        public final List<Word> background;
        public final boolean interlude;
        public final boolean alignEnd;
        /** Actual romanized line returned by Prism's own Romanizer, when enabled for this song. */
        public final String romanizedText;
        public final List<Word> romanizedWords;
        /** Actual translated line returned by Prism's own LyricsTranslator, when enabled. */
        public final String translatedText;
        public Line(long startMs,long endMs,String text) {
            this(startMs,endMs,text,Collections.emptyList(),false,false,Collections.emptyList());
        }
        public Line(long startMs,long endMs,String text,List<Word> words,boolean interlude) {
            this(startMs,endMs,text,words,interlude,false,Collections.emptyList());
        }
        public Line(long startMs,long endMs,String text,List<Word> words,boolean interlude,
                    boolean alignEnd,List<Word> background) {
            this(startMs,endMs,text,words,interlude,alignEnd,background,null,
                    Collections.emptyList(),null);
        }
        public Line(long startMs,long endMs,String text,List<Word> words,boolean interlude,
                    boolean alignEnd,List<Word> background,String romanizedText,
                    List<Word> romanizedWords,String translatedText) {
            this.startMs=startMs; this.endMs=endMs; this.text=text==null?"":text;
            this.words=Collections.unmodifiableList(new ArrayList<>(words));
            this.background=Collections.unmodifiableList(new ArrayList<>(background));
            this.interlude=interlude; this.alignEnd=alignEnd;
            this.romanizedText=blankToNull(romanizedText);
            this.romanizedWords=Collections.unmodifiableList(new ArrayList<>(romanizedWords));
            this.translatedText=blankToNull(translatedText);
        }
    }

    private static String blankToNull(String value) { return value==null || value.trim().isEmpty() ? null : value; }

    private static final Pattern LRC_TIME=Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern LINE_HEADER=Pattern.compile("^\\[(\\d+),(\\d+)]");
    private static final Pattern YRC_WORD=Pattern.compile("\\((\\d+),(\\d+),-?\\d+\\)([^()]*)");
    private static final Pattern KRC_WORD=Pattern.compile("<(\\d+),(\\d+),-?\\d+>([^<>]*)");
    private static final int[] KRC_KEY={0x40,0x47,0x61,0x77,0x5e,0x32,0x74,0x47,0x51,0x36,0x31,0x2d,0xce,0xd2,0x6e,0x69};
    private static final long INTERLUDE_GAP_MS=3000L;

    private final Type type;
    private final String provider;
    private final List<Line> lines;

    MediaLyrics(Type type,String provider,List<Line> lines) {
        this.type=type==null?Type.LINE:type;
        this.provider=provider==null?"":provider;
        this.lines=Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public Type type() { return type; }
    public String provider() { return provider; }
    public boolean isEmpty() { return lines.isEmpty(); }
    public int size() { return lines.size(); }
    public Line line(int index) { return lines.get(index); }

    /** Current line, or the next line before its first timestamp / inside a gap. */
    public int currentIndex(long positionMs) {
        if(lines.isEmpty()) return -1;
        if(positionMs<lines.get(0).startMs) return 0;
        for(int i=0;i<lines.size();i++) {
            Line line=lines.get(i);
            if(positionMs>=line.startMs && positionMs<line.endMs) return i;
            if(positionMs<line.startMs) return i;
        }
        return -1;
    }

    public int currentWordIndex(Line line,long positionMs) {
        if(line==null || line.words.isEmpty()) return -1;
        for(int i=0;i<line.words.size();i++) {
            Word word=line.words.get(i);
            if(positionMs>=word.startMs && positionMs<word.endMs) return i;
        }
        return -1;
    }

    /**
     * Imports Prism's already-resolved lyric model. Timestamps are copied verbatim; missing timestamps
     * stay missing rather than being estimated from line length or playback duration.
     */
    public static MediaLyrics fromPrismSnapshot(JSONObject snapshot) {
        if (snapshot == null) return null;
        JSONArray sourceLines = snapshot.optJSONArray("lines");
        if (sourceLines == null || sourceLines.length() == 0) return null;
        JSONArray romanizedLines = snapshot.optJSONArray("romanizedLines");
        JSONArray translatedLines = snapshot.optJSONArray("translatedLines");
        List<Line> parsed = new ArrayList<>(sourceLines.length());
        boolean hasWordTiming = false;
        for (int i = 0; i < sourceLines.length(); i++) {
            JSONObject source = sourceLines.optJSONObject(i);
            if (source == null) continue;
            long start = source.optLong("timeMs", -1L);
            long end = source.optLong("endMs", -1L);
            String text = source.optString("text", "");
            List<Word> words = prismWords(source.optJSONArray("words"));
            List<Word> background = prismWords(source.optJSONArray("background"));
            JSONObject romanized = romanizedLines == null ? null : romanizedLines.optJSONObject(i);
            String romanizedText = romanized == null ? null : romanized.optString("text", null);
            List<Word> romanizedWords = romanized == null ? Collections.emptyList()
                    : prismWords(romanized.optJSONArray("words"));
            String translatedText = null;
            if (translatedLines != null) {
                Object translated = translatedLines.opt(i);
                if (translated instanceof String) translatedText = (String) translated;
            }
            hasWordTiming |= !words.isEmpty();
            parsed.add(new Line(start, end, text, words, source.optBoolean("isGap"),
                    source.optBoolean("alignEnd"), background, romanizedText, romanizedWords, translatedText));
        }
        if (parsed.isEmpty()) return null;
        boolean synced = snapshot.optBoolean("synced", false);
        Type type = hasWordTiming ? Type.SYLLABLE : (synced ? Type.LINE : Type.STATIC);
        return new MediaLyrics(type, snapshot.optString("source", "Prism"), parsed);
    }

    private static List<Word> prismWords(JSONArray values) {
        if (values == null || values.length() == 0) return Collections.emptyList();
        List<Word> result = new ArrayList<>(values.length());
        for (int i = 0; i < values.length(); i++) {
            JSONObject word = values.optJSONObject(i);
            if (word == null) continue;
            result.add(new Word(word.optLong("startMs", -1L), word.optLong("endMs", -1L),
                    word.optString("text", "")));
        }
        return result;
    }

    static MediaLyrics parseLrc(String raw) { return parseLrc(raw,"LRCLIB"); }

    static MediaLyrics parseLrc(String raw,String provider) {
        if(raw==null || raw.trim().isEmpty()) return null;
        List<Line> parsed=new ArrayList<>();
        for(String rawLine:raw.replace("\uFEFF","").split("\\r?\\n")) {
            String source=rawLine.trim();
            Matcher matcher=LRC_TIME.matcher(source);
            List<Long> stamps=new ArrayList<>();
            int end=0;
            while(matcher.find() && matcher.start()==end) {
                long min=Long.parseLong(matcher.group(1));
                long sec=Long.parseLong(matcher.group(2));
                String fraction=matcher.group(3);
                long ms=fraction==null?0:Long.parseLong(fraction);
                if(fraction!=null && fraction.length()==1)ms*=100;
                else if(fraction!=null && fraction.length()==2)ms*=10;
                stamps.add((min*60+sec)*1000+ms);
                end=matcher.end();
            }
            String text=source.substring(end).trim();
            if(stamps.isEmpty()||text.isEmpty())continue;
            for(long start:stamps)parsed.add(new Line(start,start+5000,text));
        }
        if(parsed.isEmpty())return null;
        parsed.sort((a,c)->Long.compare(a.startMs,c.startMs));
        List<Line> fixed=new ArrayList<>(parsed.size()+4);
        for(int i=0;i<parsed.size();i++) {
            Line line=parsed.get(i);
            long end=i+1<parsed.size()?parsed.get(i+1).startMs:line.startMs+5000;
            end=Math.max(line.startMs+250,end);
            fixed.add(new Line(line.startMs,end,line.text,Collections.emptyList(),false));
        }
        addInterludes(fixed);
        return new MediaLyrics(Type.LINE,provider,fixed);
    }

    static MediaLyrics parseYrc(String raw,String provider) {
        return parseWordTimed(raw,YRC_WORD,false,provider);
    }

    static MediaLyrics parseKrc(String raw,String provider) {
        return parseWordTimed(raw,KRC_WORD,true,provider);
    }

    private static MediaLyrics parseWordTimed(String raw,Pattern wordPattern,boolean relative,String provider) {
        if(raw==null||raw.trim().isEmpty())return null;
        List<Line> parsed=new ArrayList<>();
        for(String rawLine:raw.split("\\r?\\n")) {
            String source=rawLine.trim();
            Matcher header=LINE_HEADER.matcher(source);
            if(!header.find())continue;
            long lineStart,lineDuration;
            try { lineStart=Long.parseLong(header.group(1));lineDuration=Long.parseLong(header.group(2)); }
            catch(NumberFormatException invalid){continue;}
            if(lineDuration<=0)continue;
            List<Word> words=new ArrayList<>();
            StringBuilder text=new StringBuilder();
            Matcher matcher=wordPattern.matcher(source.substring(header.end()));
            while(matcher.find()) {
                String token=matcher.group(3);
                if(token==null||token.isEmpty())continue;
                try {
                    long start=Long.parseLong(matcher.group(1))+(relative?lineStart:0);
                    long duration=Long.parseLong(matcher.group(2));
                    if(duration<=0)continue;
                    words.add(new Word(start,start+duration,token));
                    text.append(token);
                } catch(NumberFormatException ignored) { }
            }
            if(!words.isEmpty()&&text.length()>0)
                parsed.add(new Line(lineStart,lineStart+lineDuration,text.toString().trim(),words,false));
        }
        if(parsed.isEmpty())return null;
        parsed.sort((a,c)->Long.compare(a.startMs,c.startMs));
        addInterludes(parsed);
        return new MediaLyrics(Type.SYLLABLE,provider,parsed);
    }

    static String decryptKrc(byte[] raw) throws IOException {
        if(raw==null||raw.length<4)throw new IOException("KRC payload too short");
        int offset=raw[0]=='k'&&raw[1]=='r'&&raw[2]=='c'&&raw[3]=='1'?4:0;
        byte[] decoded=new byte[raw.length-offset];
        for(int i=0;i<decoded.length;i++)decoded[i]=(byte)(raw[i+offset]^KRC_KEY[i%KRC_KEY.length]);
        try(InflaterInputStream input=new InflaterInputStream(new ByteArrayInputStream(decoded));
            ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];
            for(int n;(n=input.read(buffer))!=-1;) {
                if(output.size()+n>2_000_000)throw new IOException("KRC payload too large");
                output.write(buffer,0,n);
            }
            return output.toString("UTF-8");
        }
    }

    private static List<Word> spreadWords(String text,long start,long end) {
        List<Word> out=new ArrayList<>();
        String[] tokens=text.trim().split("\\s+");
        if(tokens.length==0)return out;
        int chars=0;
        for(String token:tokens)chars+=token.length();
        if(chars<=0)return out;
        long duration=Math.max(end-start,500);
        long cursor=start;
        for(int i=0;i<tokens.length;i++) {
            long span=i==tokens.length-1?Math.max(end-cursor,100):duration*tokens[i].length()/chars;
            out.add(new Word(cursor,cursor+span,tokens[i]));
            cursor+=span;
        }
        return out;
    }

    private static void addInterludes(List<Line> target) {
        if(target.size()<2)return;
        List<Line> withGaps=new ArrayList<>(target.size()+4);
        Line first=target.get(0);
        if(first.startMs>=INTERLUDE_GAP_MS)withGaps.add(new Line(0,first.startMs,"",Collections.emptyList(),true));
        withGaps.add(first);
        for(int i=1;i<target.size();i++) {
            Line previous=target.get(i-1),current=target.get(i);
            if(current.startMs-previous.endMs>=INTERLUDE_GAP_MS)
                withGaps.add(new Line(previous.endMs,current.startMs,"",Collections.emptyList(),true));
            withGaps.add(current);
        }
        target.clear();
        target.addAll(withGaps);
    }
}
