package dev.android.dynamicisland.island.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable line-synced lyrics used by the media card. Times are track-relative milliseconds. */
public final class MediaLyrics {
    public static final class Line {
        public final long startMs;
        public final long endMs;
        public final String text;
        public Line(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text == null ? "" : text;
        }
    }

    private final List<Line> lines;

    MediaLyrics(List<Line> lines) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public boolean isEmpty() { return lines.isEmpty(); }
    public int size() { return lines.size(); }
    public Line line(int index) { return lines.get(index); }

    /** Current line, or the next line before the first timestamp. */
    public int currentIndex(long positionMs) {
        if (lines.isEmpty()) return -1;
        if (positionMs < lines.get(0).startMs) return 0;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (positionMs < line.startMs) return i;
            if (positionMs >= line.startMs && positionMs < line.endMs) return i;
        }
        return -1;
    }

    static MediaLyrics parseLrc(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        java.util.regex.Pattern time = java.util.regex.Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
        List<Line> parsed = new ArrayList<>();
        for (String rawLine : raw.replace("\uFEFF", "").split("\\r?\\n")) {
            java.util.regex.Matcher matcher = time.matcher(rawLine.trim());
            List<Long> stamps = new ArrayList<>();
            int end = 0;
            while (matcher.find() && matcher.start() == end) {
                long min = Long.parseLong(matcher.group(1));
                long sec = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long ms = fraction == null ? 0 : Long.parseLong(fraction);
                if (fraction != null && fraction.length() == 1) ms *= 100;
                else if (fraction != null && fraction.length() == 2) ms *= 10;
                stamps.add((min * 60 + sec) * 1000 + ms);
                end = matcher.end();
            }
            String text = rawLine.trim().substring(end).trim();
            if (text.isEmpty()) continue; // metadata and instrumental markers do not render
            for (long stamp : stamps) parsed.add(new Line(stamp, stamp + 5000, text));
        }
        if (parsed.isEmpty()) return null;
        parsed.sort((a,b) -> Long.compare(a.startMs,b.startMs));
        List<Line> fixed = new ArrayList<>(parsed.size());
        for (int i=0;i<parsed.size();i++) {
            Line line=parsed.get(i);
            long end=i+1<parsed.size()?parsed.get(i+1).startMs:line.startMs+5000;
            fixed.add(new Line(line.startMs,Math.max(line.startMs+250,end),line.text));
        }
        return new MediaLyrics(fixed);
    }
}
