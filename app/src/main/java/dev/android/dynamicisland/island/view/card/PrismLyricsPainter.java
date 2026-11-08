package dev.android.dynamicisland.island.view.card;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import dev.android.dynamicisland.island.graphics.Colors;
import dev.android.dynamicisland.island.lyrics.MediaLyrics;
import dev.android.dynamicisland.island.view.IslandView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * Canvas translation of the parts of Prism's LyricsLook used by its current-player lyric strip.
 *
 * All lyric text and provider timings are supplied by PrismMediaBridge. The only generated word
 * timings are the same line-to-word timings Prism itself creates in LyricLine.withWords() when
 * the selected look has wordByWord enabled for a line-timed source.
 */
final class PrismLyricsPainter {
    private static final Pattern WORDS = Pattern.compile("\\S+\\s*");
    private static final long TYPE_IN_MS = 140L;
    private static final float DEFAULT_ROW_DP = 38.0f;
    private static final ConcurrentHashMap<String, Typeface> TYPEFACE_CACHE = new ConcurrentHashMap<>();

    private PrismLyricsPainter() {}

    /** Per-card lyric line transition state; Prism uses a 340 ms fade/vertical slide. */
    static final class State {
        int index = Integer.MIN_VALUE;
        String text;
        String previousText;
        long changedAt;
        float update(int nextIndex, String nextText, long now) {
            if (index == Integer.MIN_VALUE) {
                index = nextIndex; text = nextText; previousText = null; changedAt = now;
                return 1.0f;
            }
            if (index != nextIndex || !same(text, nextText)) {
                previousText = text; index = nextIndex; text = nextText; changedAt = now;
            }
            if (previousText == null) return 1.0f;
            float progress = clamp((now - changedAt) / 340.0f, 0.0f, 1.0f);
            if (progress >= 1.0f) previousText = null;
            return progress;
        }
        void reset() { index = Integer.MIN_VALUE; text = null; previousText = null; changedAt = 0L; }
        private static boolean same(String a, String b) { return a == null ? b == null : a.equals(b); }
    }

    private static final class Segment {
        final String text; final int start; final int end; final boolean clipped;
        Segment(String text, int start, int end, boolean clipped) {
            this.text = text; this.start = start; this.end = end; this.clipped = clipped;
        }
    }

    /** Prism's ALL option is allowed, but the narrow island strip caps its preview to three readable future lines. */
    static int aheadLineCount(JSONObject look) {
        if (look == null) return 0;
        int count = look.optInt("aheadLines", 1);
        if (count == Integer.MAX_VALUE) return 3;
        return Math.max(0, Math.min(3, count));
    }

    static float rowHeightDp(JSONObject look, MediaLyrics lyrics) {
        if (look == null) return DEFAULT_ROW_DP;
        int mainRows = look.optBoolean("lyricsStripTwoLines", false) ? 2 : 1;
        int futureRows = aheadLineCount(look);
        String romanize = romanizeMode(look);
        int extraRows = "UNDER".equals(romanize) ? 1 : 0;
        if (look.optBoolean("lyricsTranslate", false)) extraRows++;
        boolean backing = false;
        if (lyrics != null) {
            for (int i = 0; i < lyrics.size(); i++) {
                if (!lyrics.line(i).background.isEmpty()) { backing = true; break; }
            }
        }
        float spacing = clamp((float) look.optDouble("spacing", 1.0d), 0.6f, 1.8f);
        float extraSize = clamp((float) look.optDouble("extraSize", 1.0d), 0.7f, 1.4f);
        float backingSize = clamp((float) look.optDouble("backingSize", 0.6d), 0.4f, 0.9f);
        float height = 6.0f + mainRows * 14.0f * spacing
                + extraRows * 10.0f * extraSize + (backing ? 10.0f * backingSize : 0.0f)
                + futureRows * 12.5f * spacing;
        // LyricsPosition is designed for the full lyrics page. On the compact card it becomes a
        // small vertical offset rather than an unbounded page-layout transformation.
        height += 12.0f * clamp((float) look.optDouble("positionFraction", 0.0d), 0.0f, 0.42f);
        return Math.max(DEFAULT_ROW_DP, height);
    }

    static void draw(Canvas canvas, IslandView owner, MediaLyrics data, JSONObject look, State state,
                     TextPaint mainPaint, TextPaint extraPaint, String status,
                     float left, float top, float width, float alpha, long positionMs, int accent) {
        if (look == null) look = new JSONObject();
        configureMainPaint(owner, mainPaint, look);
        configureExtraPaint(owner, extraPaint, look);

        if (data == null || data.isEmpty()) {
            state.reset();
            String message;
            if ("LOADING".equals(status)) message = "Loading Prism lyrics…";
            else if ("WAITING".equals(status)) message = "Open Prism to load lyrics";
            else if ("UNAVAILABLE".equals(status)) message = "No lyrics available in Prism";
            else if ("BRIDGE_UNAVAILABLE".equals(status)) message = "Prism bridge not connected";
            else message = "Prism lyrics not available yet";
            drawPlain(canvas, mainPaint, message, left, top + owner.dp(4.0f) - mainPaint.getFontMetrics().ascent,
                    width, Paint.Align.LEFT, alpha, Colors.withAlpha(Colors.ON_SURFACE_VARIANT, 0.78f * alpha));
            return;
        }

        boolean synced = data.type() != MediaLyrics.Type.STATIC;
        long now = Math.max(0L, positionMs);
        if (!synced) {
            // Static lyrics have no usable timestamps. Show their real text and upcoming lines,
            // but do not manufacture a playhead, karaoke timing or automatic line progression.
            state.reset();
            drawStaticLyrics(canvas, owner, data, look, mainPaint, extraPaint,
                    left, top, width, alpha, accent);
            return;
        }
        int index = data.prismCurrentIndex(now);
        if (index < 0) {
            state.reset();
            drawStatus(canvas, owner, look, mainPaint, "♫  Intro", left, top, width, alpha);
            return;
        }

        MediaLyrics.Line line = data.line(index);
        if (line.interlude || line.text.trim().isEmpty()) {
            state.reset();
            drawStatus(canvas, owner, look, mainPaint, "♫  Instrumental", left, top, width, alpha);
            return;
        }

        String mode = romanizeMode(look);
        boolean romanInstead = "INSTEAD".equals(mode) && line.romanizedText != null;
        String mainText = romanInstead ? line.romanizedText : line.text;
        mainText = shown(mainText, look);
        float transition = state.update(index, mainText, SystemClock.elapsedRealtime());
        List<MediaLyrics.Word> words = romanInstead && !line.romanizedWords.isEmpty()
                ? line.romanizedWords : line.words;
        boolean wordByWord = look.optBoolean("wordByWordNow", false);
        if (synced && wordByWord && words.isEmpty()) {
            words = synthesizeWords(mainText, line.startMs, line.endMs);
        }

        Paint.Align configuredAlign = align(look);
        Paint.Align lineAlign = line.alignEnd ? Paint.Align.RIGHT
                : (configuredAlign == Paint.Align.LEFT && isRtl(mainText) ? Paint.Align.RIGHT : configuredAlign);
        float baseSize = owner.dp(12.0f * clamp((float) look.optDouble("sizeScale", 1.0d), 0.7f, 1.4f));
        mainPaint.setTextSize(baseSize);
        Paint.FontMetrics fm = mainPaint.getFontMetrics();
        float lineHeight = owner.dp(14.0f * spacing(look));
        int motion = motion(look);
        int sungColor = lyricColor(look, "sungColor", "sungCustom", accent, Colors.WHITE);
        int otherColor = lyricColor(look, "otherColor", "otherCustom", accent, Colors.ON_SURFACE_VARIANT);
        int extraColor = lyricColor(look, "extraColor", "otherCustom", accent, Colors.ON_SURFACE_VARIANT);
        float othersAlpha = clamp((float) look.optDouble("othersAlpha", 0.55d), 0.2f, 1.0f);
        String translated = look.optBoolean("lyricsTranslate", false) ? line.translatedText : null;
        if (translated != null && translated.trim().equalsIgnoreCase(line.text.trim())) translated = null;
        String romanUnder = "UNDER".equals(mode) ? line.romanizedText : null;
        boolean extraAbove = "ABOVE".equalsIgnoreCase(look.optString("extraPlace", "BELOW"));
        float extraSize = clamp((float) look.optDouble("extraSize", 1.0d), 0.7f, 1.4f);
        float extraStep = owner.dp(9.5f * extraSize);
        int aboveCount = extraAbove ? count(romanUnder, translated) : 0;
        // Reserve the same space Prism uses for ABOVE translations/romanization so they remain
        // inside the lyric row instead of being clipped into the controls row above.
        float positionOffset = owner.dp(12.0f
                * clamp((float) look.optDouble("positionFraction", 0.0d), 0.0f, 0.42f));
        float mainBaseline = top + positionOffset + owner.dp(4.0f) - fm.ascent
                + (extraAbove ? extraStep * aboveCount : 0.0f);
        float slide = owner.dp(5.0f) * (1.0f - transition);
        if (state.previousText != null && transition < 1.0f) {
            drawPlain(canvas, mainPaint, state.previousText,
                    anchor(left, width, lineAlign, line.alignEnd, state.previousText), mainBaseline - slide,
                    width, lineAlign, alpha * (1.0f - transition),
                    Colors.withAlpha(Colors.WHITE, alpha * (1.0f - transition)));
        }
        mainBaseline += slide;
        if (aboveCount > 0) {
            float y = mainBaseline - extraStep * aboveCount;
            if (romanUnder != null) {
                drawExtra(canvas, owner, extraPaint, romanUnder, left, width, y, lineAlign,
                        alpha * transition, extraColor, look, extraSize, 0.85f, line.alignEnd);
                y += extraStep;
            }
            if (translated != null) {
                drawExtra(canvas, owner, extraPaint, translated, left, width, y, lineAlign,
                        alpha * transition, extraColor, look, extraSize, 0.8f, line.alignEnd);
            }
        }

        setShadow(owner, mainPaint, look, sungColor);
        boolean twoRows = look.optBoolean("lyricsStripTwoLines", false);
        int rowLimit = twoRows ? 2 : 1;
        List<Segment> segments = wrapCurrentLine(mainText, mainPaint, width, rowLimit);
        float firstRowBaseline = mainBaseline + lineHeight * (rowLimit - segments.size());
        for (Segment segment : segments) {
            List<MediaLyrics.Word> segmentWords = segment.clipped ? Collections.emptyList()
                    : wordsForRange(words, segment.start, segment.end, segment.text, look);
            drawTimedLine(canvas, owner, mainPaint, segment.text, segmentWords, line.startMs, line.endMs,
                    left, width, firstRowBaseline, lineAlign, line.alignEnd, now, synced, motion,
                    sungColor, otherColor, othersAlpha, alpha * transition, look);
            firstRowBaseline += lineHeight;
        }

        // The two-line switch wraps this current line; extras begin below the full reserved lyric area.
        float afterBaseline = mainBaseline + rowLimit * lineHeight;

        if (!extraAbove) {
            if (romanUnder != null) {
                drawExtra(canvas, owner, extraPaint, romanUnder, left, width, afterBaseline, lineAlign,
                        alpha * transition, extraColor, look, extraSize, 0.85f, line.alignEnd);
                afterBaseline += extraStep;
            }
        }

        if (!line.background.isEmpty()) {
            String backing = wordText(line.background, look.optBoolean("uppercase", false));
            float backingSize = clamp((float) look.optDouble("backingSize", 0.6d), 0.4f, 0.9f);
            extraPaint.setTextSize(baseSize * backingSize);
            extraPaint.setTypeface(typeface(owner, look, "REGULAR", false));
            extraPaint.setTextAlign(lineAlign);
            setShadow(owner, extraPaint, look, sungColor);
            drawTimedLine(canvas, owner, extraPaint, backing, line.background, line.startMs,
                    line.endMs, left, width, afterBaseline, lineAlign, line.alignEnd, now,
                    synced, motion == 3 ? 3 : 2, sungColor, otherColor,
                    Math.min(0.8f, othersAlpha * 0.8f), alpha * transition, look);
            clearShadow(extraPaint);
            afterBaseline += owner.dp(9.0f * backingSize);
        }

        if (!extraAbove && translated != null) {
            drawExtra(canvas, owner, extraPaint, translated, left, width, afterBaseline, lineAlign,
                    alpha * transition, extraColor, look, extraSize, 0.8f, line.alignEnd);
            afterBaseline += extraStep;
        }

        drawUpcomingLines(canvas, owner, data, index + 1, look, mainPaint, left, width,
                afterBaseline + owner.dp(2.0f), lineHeight, baseSize, alpha,
                otherColor, othersAlpha, mode);
    }

    private static void drawStaticLyrics(Canvas canvas, IslandView owner, MediaLyrics data,
            JSONObject look, TextPaint mainPaint, TextPaint extraPaint,
            float left, float top, float width, float alpha, int accent) {
        if (data == null || data.isEmpty()) return;
        int ahead = aheadLineCount(look);
        int mainRows = look.optBoolean("lyricsStripTwoLines", false) ? 2 : 1;
        float spacing = spacing(look);
        float scale = clamp((float) look.optDouble("sizeScale", 1.0d), 0.7f, 1.4f);
        float textScale = clamp((float) look.optDouble("textScale", 1.0d), 0.7f, 1.4f);
        float lineHeight = owner.dp(14.0f * spacing);
        int sungColor = lyricColor(look, "sungColor", "sungCustom", accent, Colors.WHITE);
        int otherColor = lyricColor(look, "otherColor", "otherCustom", accent, Colors.ON_SURFACE_VARIANT);
        float othersAlpha = clamp((float) look.optDouble("othersAlpha", 0.55d), 0.2f, 1.0f);
        Paint.Align configuredAlign = align(look);
        MediaLyrics.Line first = data.line(0);
        String text = shown(first.text, look);
        mainPaint.setTextSize(owner.dp(12.0f * scale * textScale));
        mainPaint.setColor(Colors.withAlpha(sungColor, alpha));
        mainPaint.setTextAlign(first.alignEnd ? Paint.Align.RIGHT : configuredAlign);
        float baseline = firstBaseline(owner, mainPaint,
                top + owner.dp(12.0f * (float) look.optDouble("positionFraction", 0.0d)));
        List<Segment> segments = wrapCurrentLine(text, mainPaint, width, mainRows);
        float y = baseline + lineHeight * Math.max(0, mainRows - segments.size());
        for (Segment segment : segments) {
            drawPlain(canvas, mainPaint, segment.text,
                    anchor(left, width, configuredAlign, first.alignEnd, segment.text), y,
                    width, first.alignEnd ? Paint.Align.RIGHT : configuredAlign,
                    alpha, Colors.withAlpha(sungColor, alpha));
            y += lineHeight;
        }
        float after = baseline + lineHeight * mainRows;
        String mode = romanizeMode(look);
        String roman = "UNDER".equals(mode) ? first.romanizedText : null;
        String translated = look.optBoolean("lyricsTranslate", false) ? first.translatedText : null;
        int extraColor = lyricColor(look, "extraColor", "otherCustom", accent, Colors.ON_SURFACE_VARIANT);
        float extraSize = clamp((float) look.optDouble("extraSize", 1.0d), 0.7f, 1.4f);
        if ("ABOVE".equalsIgnoreCase(look.optString("extraPlace", "BELOW"))) {
            // Static content still honours above/below; reserve space before the main row when above.
            float extraBaseline = top + owner.dp(4.0f);
            if (roman != null) drawExtra(canvas, owner, extraPaint, roman, left, width, extraBaseline,
                    configuredAlign, alpha, extraColor, look, extraSize, 0.85f, first.alignEnd);
            if (translated != null) drawExtra(canvas, owner, extraPaint, translated, left, width,
                    extraBaseline + owner.dp(9.5f * extraSize), configuredAlign, alpha,
                    extraColor, look, extraSize, 0.8f, first.alignEnd);
        } else {
            if (roman != null) {
                drawExtra(canvas, owner, extraPaint, roman, left, width, after, configuredAlign,
                        alpha, extraColor, look, extraSize, 0.85f, first.alignEnd);
                after += owner.dp(9.5f * extraSize);
            }
            if (translated != null) {
                drawExtra(canvas, owner, extraPaint, translated, left, width, after, configuredAlign,
                        alpha, extraColor, look, extraSize, 0.8f, first.alignEnd);
                after += owner.dp(9.5f * extraSize);
            }
        }
        drawUpcomingLines(canvas, owner, data, 1, look, mainPaint, left, width,
                after + owner.dp(2.0f), lineHeight, owner.dp(12.0f * scale * textScale),
                alpha, otherColor, othersAlpha, mode, ahead);
    }

    private static void drawUpcomingLines(Canvas canvas, IslandView owner, MediaLyrics data,
            int firstIndex, JSONObject look, TextPaint paint, float left, float width,
            float top, float lineHeight, float baseSize, float alpha, int otherColor,
            float othersAlpha, String romanizeMode) {
        drawUpcomingLines(canvas, owner, data, firstIndex, look, paint, left, width, top,
                lineHeight, baseSize, alpha, otherColor, othersAlpha, romanizeMode,
                aheadLineCount(look));
    }

    private static void drawUpcomingLines(Canvas canvas, IslandView owner, MediaLyrics data,
            int firstIndex, JSONObject look, TextPaint paint, float left, float width,
            float top, float lineHeight, float baseSize, float alpha, int otherColor,
            float othersAlpha, String romanizeMode, int requestedCount) {
        if (data == null || requestedCount <= 0 || firstIndex < 0 || firstIndex >= data.size()) return;
        float oldSize = paint.getTextSize();
        Typeface oldTypeface = paint.getTypeface();
        Paint.Align oldAlign = paint.getTextAlign();
        boolean oldBold = paint.isFakeBoldText();
        paint.setTextSize(baseSize * 0.88f);
        paint.setFakeBoldText(false);
        float baseline = top + owner.dp(3.0f) - paint.getFontMetrics().ascent;
        float fade = othersAlpha;
        int drawn = 0;
        for (int i = firstIndex; i < data.size() && drawn < requestedCount; i++) {
            MediaLyrics.Line next = data.line(i);
            if (next.interlude || next.text == null || next.text.trim().isEmpty()) {
                drawGapMark(canvas, owner, look, paint, left, baseline - owner.dp(10.0f),
                        width, alpha * fade, next.startMs >= 0L ? next.startMs : 0L, next);
            } else {
                String nextText = "INSTEAD".equals(romanizeMode) && next.romanizedText != null
                        ? next.romanizedText : next.text;
                nextText = shown(nextText, look);
                Paint.Align lineAlign = next.alignEnd ? Paint.Align.RIGHT
                        : (align(look) == Paint.Align.LEFT && isRtl(nextText) ? Paint.Align.RIGHT : align(look));
                paint.setTextAlign(lineAlign);
                paint.setColor(Colors.withAlpha(otherColor, alpha * fade));
                String fitted = fitLine(nextText, paint, width);
                drawPlain(canvas, paint, fitted, anchor(left, width, lineAlign, next.alignEnd, fitted),
                        baseline, width, lineAlign, alpha * fade,
                        Colors.withAlpha(otherColor, alpha * fade));
            }
            baseline += lineHeight;
            fade *= 0.78f;
            drawn++;
        }
        paint.setTextSize(oldSize);
        paint.setTypeface(oldTypeface);
        paint.setTextAlign(oldAlign);
        paint.setFakeBoldText(oldBold);
    }

    private static String fitLine(String text, TextPaint paint, float width) {
        if (text == null || text.isEmpty() || paint.measureText(text) <= width) return text == null ? "" : text;
        String ellipsis = "…";
        float allowed = Math.max(0.0f, width - paint.measureText(ellipsis));
        int end = text.length();
        while (end > 0 && paint.measureText(text, 0, end) > allowed) end--;
        return end <= 0 ? ellipsis : text.substring(0, end).trim() + ellipsis;
    }

    private static List<Segment> wrapCurrentLine(String text, TextPaint paint, float width, int maxRows) {
        List<Segment> result = new ArrayList<>(maxRows);
        if (text == null || text.isEmpty()) return result;
        int start = 0;
        while (start < text.length() && result.size() < maxRows) {
            int count = paint.breakText(text, start, text.length(), true, width, null);
            if (count <= 0) count = 1;
            int end = Math.min(text.length(), start + count);
            if (end < text.length()) {
                int whitespace = -1;
                for (int i = start; i < end; i++) if (Character.isWhitespace(text.charAt(i))) whitespace = i;
                if (whitespace > start) end = whitespace + 1;
            }
            if (end <= start) end = Math.min(text.length(), start + 1);
            result.add(new Segment(text.substring(start, end), start, end, false));
            start = end;
        }
        if (start < text.length() && !result.isEmpty()) {
            Segment last = result.remove(result.size() - 1);
            String remaining = text.substring(last.start);
            String ellipsis = "…";
            int end = remaining.length();
            while (end > 0 && paint.measureText(remaining, 0, end) + paint.measureText(ellipsis) > width) end--;
            String fitted = (end <= 0 ? "" : remaining.substring(0, end).trim()) + ellipsis;
            result.add(new Segment(fitted, last.start, text.length(), true));
        }
        return result;
    }

    private static List<MediaLyrics.Word> wordsForRange(List<MediaLyrics.Word> words, int start,
            int end, String text, JSONObject look) {
        if (words == null || words.isEmpty()) return Collections.emptyList();
        List<MediaLyrics.Word> selected = new ArrayList<>();
        StringBuilder joined = new StringBuilder();
        int offset = 0;
        for (MediaLyrics.Word word : words) {
            int next = offset + word.text.length();
            if (offset >= start && next <= end) {
                selected.add(word);
                joined.append(shown(word.text, look));
            } else if (offset < end && next > start) {
                return Collections.emptyList(); // a timed word crosses the visual wrap boundary
            }
            offset = next;
        }
        return joined.toString().equals(text) ? selected : Collections.emptyList();
    }

    /** Exact port of Prism LyricLine.withWords() for line-timed sources when its look asks for word motion. */
    static List<MediaLyrics.Word> synthesizeWords(String text, long startMs, long endMs) {
        if (text == null || text.trim().isEmpty() || endMs <= startMs) return Collections.emptyList();
        Matcher matcher = WORDS.matcher(text);
        List<String> tokens = new ArrayList<>();
        int letters = 0;
        while (matcher.find()) {
            String token = matcher.group();
            tokens.add(token);
            letters += token.trim().length();
        }
        if (tokens.isEmpty()) return Collections.emptyList();
        letters = Math.max(1, letters);
        long duration = endMs - startMs;
        long span = Math.min(duration, Math.min(letters * 75L + 500L, duration * 9L / 10L));
        span = Math.max(200L, span);
        long at = startMs;
        List<MediaLyrics.Word> result = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            long part = span * token.trim().length() / letters;
            result.add(new MediaLyrics.Word(at, at + part, token));
            at += part;
        }
        return result;
    }

    private static void drawTimedLine(Canvas canvas, IslandView owner, TextPaint paint, String text,
            List<MediaLyrics.Word> words, long lineStart, long lineEnd, float left, float width,
            float baseline, Paint.Align align, boolean alignEnd, long now, boolean synced, int motion,
            int sungColor, int otherColor, float othersAlpha, float alpha, JSONObject look) {
        if (text == null || text.isEmpty()) return;
        boolean hasTiming = synced && words != null && !words.isEmpty();
        if (!hasTiming || motion == 4) {
            int color = sungColor;
            drawPlain(canvas, paint, text, anchor(left, width, align, alignEnd, text), baseline,
                    width, align, alpha, Colors.withAlpha(color, alpha));
            return;
        }

        float textWidth = paint.measureText(text);
        float wordsWidth = 0.0f;
        for (MediaLyrics.Word word : words) wordsWidth += paint.measureText(shown(word.text, look));
        // Prism's word timings describe the rendered text. Refuse to paint a misleading sweep if a
        // provider's word list does not line up with the line text (e.g. partial/translated words).
        if (textWidth <= 0.0f || Math.abs(wordsWidth - textWidth) > Math.max(owner.dp(3.0f), textWidth * 0.06f)) {
            drawPlain(canvas, paint, text, anchor(left, width, align, alignEnd, text), baseline,
                    width, align, alpha, Colors.withAlpha(Colors.WHITE, alpha));
            return;
        }

        float fullWidth = paint.measureText(text);
        float textLeft = alignedLeft(left, width, fullWidth, align, alignEnd);
        boolean rtl = isRtl(text);
        if (motion == 3) { // Prism Typewriter: only show words once their native or Prism-derived timestamps begin.
            float cursor = textLeft;
            Paint.Align old = paint.getTextAlign();
            paint.setTextAlign(Paint.Align.LEFT);
            for (MediaLyrics.Word word : words) {
                String part = shown(word.text, look);
                float wordWidth = paint.measureText(part);
                if (now >= word.startMs) {
                    float wordAlpha = clamp((now - word.startMs) / (float) TYPE_IN_MS, 0.0f, 1.0f);
                    paint.setColor(Colors.withAlpha(sungColor, alpha * wordAlpha));
                    canvas.drawText(part, cursor, baseline, paint);
                }
                cursor += wordWidth;
            }
            paint.setTextAlign(old);
            return;
        }

        int dimColor = Colors.WHITE;
        float dimAlpha = motion == 1 ? 0.92f : 0.38f;
        drawPlain(canvas, paint, text, anchor(left, width, align, alignEnd, text), baseline,
                width, align, alpha, Colors.withAlpha(dimColor, dimAlpha * alpha));

        float sungWidth = 0.0f;
        float activeStart = 0.0f, activeWidth = 0.0f;
        for (MediaLyrics.Word word : words) {
            String part = shown(word.text, look);
            float wordWidth = paint.measureText(part);
            if (now >= word.endMs) {
                sungWidth += wordWidth;
            } else if (now >= word.startMs) {
                activeStart = sungWidth;
                activeWidth = wordWidth;
                float fraction = clamp((now - word.startMs) / (float) Math.max(1L, word.endMs - word.startMs), 0.0f, 1.0f);
                sungWidth += wordWidth * fraction;
                break;
            } else {
                break;
            }
        }

        if (sungWidth <= 0.0f) return;
        float clipLeft = rtl ? textLeft + fullWidth - sungWidth : textLeft;
        float clipRight = rtl ? textLeft + fullWidth : textLeft + sungWidth;
        int save = canvas.save();
        canvas.clipRect(Math.max(left, clipLeft), baseline + paint.getFontMetrics().ascent,
                Math.min(left + width, clipRight), baseline + paint.getFontMetrics().descent);
        paint.setColor(Colors.withAlpha(sungColor, alpha));
        canvas.drawText(text, anchor(left, width, align, alignEnd, text), baseline, paint);
        canvas.restoreToCount(save);

    }

    private static void drawExtra(Canvas canvas, IslandView owner, TextPaint paint, String text,
            float left, float width, float baseline, Paint.Align align, float alpha, int color,
            JSONObject look, float sizeScale, float opacity, boolean alignEnd) {
        if (text == null || text.isEmpty()) return;
        float oldSize = paint.getTextSize();
        Typeface oldTypeface = paint.getTypeface();
        Paint.Align oldAlign = paint.getTextAlign();
        paint.setTextSize(owner.dp(12.0f * sizeScale * 0.52f));
        paint.setTypeface(typeface(owner, look, "REGULAR", look.optBoolean("extraItalic", false)));
        paint.setTextAlign(alignEnd ? Paint.Align.RIGHT : align);
        setShadow(owner, paint, look, color);
        drawPlain(canvas, paint, text, anchor(left, width, paint.measureText(text), align, alignEnd),
                baseline, width, alignEnd ? Paint.Align.RIGHT : align, alpha, Colors.withAlpha(color, alpha * opacity));
        clearShadow(paint);
        paint.setTextSize(oldSize);
        paint.setTypeface(oldTypeface);
        paint.setTextAlign(oldAlign);
    }

    private static void drawStatus(Canvas canvas, IslandView owner, JSONObject look, TextPaint paint,
            String message, float left, float top, float width, float alpha) {
        Paint.Align textAlign = align(look);
        paint.setTextSize(owner.dp(12.0f));
        paint.setTextAlign(textAlign);
        paint.setColor(Colors.withAlpha(Colors.ON_SURFACE_VARIANT, 0.78f * alpha));
        float x = textAlign == Paint.Align.CENTER ? left + width * 0.5f : left;
        canvas.drawText(message, x, firstBaseline(owner, paint, top), paint);
    }

    private static void drawGapMark(Canvas canvas, IslandView owner, JSONObject look, TextPaint paint,
            float left, float top, float width, float alpha, long now, MediaLyrics.Line line) {
        boolean note = "NOTE".equalsIgnoreCase(look.optString("gapMark", "DOTS"));
        String mark = note ? "♫" : "·   ·   ·";
        float baseline = top + owner.dp(4.0f) - paint.getFontMetrics().ascent;
        Paint.Align align = align(look);
        float progress = line == null || line.endMs <= line.startMs ? 0.5f
                : clamp((now - line.startMs) / (float) (line.endMs - line.startMs), 0.0f, 1.0f);
        paint.setTextAlign(align);
        if (note) {
            paint.setTextSize(owner.dp(14.0f));
            paint.setColor(Colors.withAlpha(Colors.WHITE, alpha * (0.45f + 0.55f * progress)));
            canvas.drawText(mark, anchor(left, width, align, false, mark), baseline, paint);
        } else {
            paint.setTextSize(owner.dp(11.0f));
            float span = 3.0f;
            for (int i = 0; i < 3; i++) {
                float brightness = clamp(progress * span - i, 0.0f, 1.0f);
                paint.setColor(Colors.withAlpha(Colors.WHITE, alpha * (0.35f + 0.65f * brightness)));
                float cx = left + width * 0.5f + owner.dp((i - 1) * 8.0f);
                canvas.drawCircle(cx, baseline - owner.dp(4.0f), owner.dp(1.8f), paint);
            }
        }
    }

    private static void drawPlain(Canvas canvas, TextPaint paint, String text, float x,
            float baseline, float width, Paint.Align align, float alpha, int color) {
        if (text == null || text.isEmpty()) return;
        paint.setColor(color);
        paint.setTextAlign(align);
        canvas.drawText(text, x, baseline, paint);
    }

    private static void configureMainPaint(IslandView owner, TextPaint paint, JSONObject look) {
        float size = clamp((float) look.optDouble("sizeScale", 1.0d), 0.7f, 1.4f);
        float textScale = clamp((float) look.optDouble("textScale", 1.0d), 0.7f, 1.4f);
        paint.setTextSize(owner.dp(12.0f * size * textScale));
        paint.setTypeface(typeface(owner, look,
                look.optString("weightNow", look.optString("weight", "LIGHT")), false));
        paint.setFakeBoldText(look.optBoolean("boldLinesNow", "CARD".equalsIgnoreCase(look.optString("style", ""))));
        paint.setLetterSpacing((float) look.optDouble("typeSpacingEm", 0.0d));
        paint.setTextAlign(align(look));
        paint.clearShadowLayer();
    }

    private static void configureExtraPaint(IslandView owner, TextPaint paint, JSONObject look) {
        float textScale = clamp((float) look.optDouble("textScale", 1.0d), 0.7f, 1.4f);
        paint.setTextSize(owner.dp(6.2f * clamp((float) look.optDouble("extraSize", 1.0d), 0.7f, 1.4f) * textScale));
        paint.setTypeface(typeface(owner, look, "REGULAR", false));
        paint.setLetterSpacing((float) look.optDouble("typeSpacingEm", 0.0d));
        paint.setFakeBoldText(false);
    }

    /**
     * Resolves Prism's selected Lyrics font from Prism's own resources/files. The System UI process
     * never imports Prism classes or Compose; it only loads the exact packaged Typeface where the
     * OS permits the media-session caller to read it. A safe system fallback is used otherwise.
     */
    private static Typeface typeface(IslandView owner, JSONObject look, String weightName, boolean italic) {
        String lyricChoice = look.optString("lyricsFont", "");
        String choice = lyricChoice.isEmpty() ? look.optString("fontChoice", "PRISM") : lyricChoice;
        String slot = lyricChoice.isEmpty() ? "app.font" : "lyrics.font";
        long stamp = look.optLong("fontStamp", 0L);
        int weight = fontWeight(weightName);
        String key = choice + "|" + slot + "|" + stamp + "|" + weight + "|" + italic;
        Typeface cached = TYPEFACE_CACHE.get(key);
        if (cached != null) return cached;

        Typeface base = Typeface.DEFAULT;
        try {
            Context prism = owner.getContext().createPackageContext("com.prism.music", 0);
            if ("CUSTOM".equalsIgnoreCase(choice)) {
                File file = new File(new File(prism.getFilesDir(), "fonts"), slot);
                if (file.isFile() && file.canRead()) base = Typeface.createFromFile(file);
            } else if (!"SYSTEM".equalsIgnoreCase(choice)) {
                String resource = switch (choice.toUpperCase(java.util.Locale.ROOT)) {
                    case "CLEAN" -> "manrope";
                    case "GEOMETRIC" -> "plus_jakarta_sans";
                    case "AIRY" -> "urbanist";
                    case "ROUNDED" -> "nunito";
                    case "SERIF" -> "fraunces";
                    case "MONO" -> "geist_mono";
                    case "LEGIBLE" -> "atkinson_hyperlegible_next";
                    case "DYSLEXIC" -> weight >= 600 ? "opendyslexic_bold" : "opendyslexic_regular";
                    default -> "outfit";
                };
                int id = prism.getResources().getIdentifier(resource, "font", "com.prism.music");
                if (id != 0) base = prism.getResources().getFont(id);
            }
        } catch (Throwable ignored) {
            // The bridge must keep working if Prism's resource context or imported font isn't readable.
        }
        Typeface resolved = Typeface.create(base, weight, italic);
        if (TYPEFACE_CACHE.size() > 48) TYPEFACE_CACHE.clear();
        TYPEFACE_CACHE.put(key, resolved);
        return resolved;
    }

    private static int fontWeight(String value) {
        if ("THIN".equalsIgnoreCase(value)) return 200;
        if ("LIGHT".equalsIgnoreCase(value)) return 300;
        if ("MEDIUM".equalsIgnoreCase(value)) return 500;
        if ("BOLD".equalsIgnoreCase(value)) return 700;
        return 400;
    }

    private static int motion(JSONObject look) {
        String value = look.optString("motionNow", "");
        if (value.isEmpty()) {
            String style = look.optString("style", "PRISM");
            if ("KARAOKE".equalsIgnoreCase(style)) value = "FILL";
            else if ("CARD".equalsIgnoreCase(style) || "GLOW".equalsIgnoreCase(style)) value = "SWEEP";
            else if ("TYPEWRITER".equalsIgnoreCase(style)) value = "TYPEWRITER";
            else if ("CALM".equalsIgnoreCase(style)) value = "NONE";
            else value = "GROW";
        }
        if ("FILL".equalsIgnoreCase(value)) return 1;
        if ("SWEEP".equalsIgnoreCase(value)) return 2;
        if ("TYPEWRITER".equalsIgnoreCase(value)) return 3;
        if ("NONE".equalsIgnoreCase(value)) return 4;
        return 0; // GROW
    }

    private static String romanizeMode(JSONObject look) {
        return look.optString("romanizeModeForSong", look.optString("romanize", "OFF")).toUpperCase();
    }

    private static String mainText(MediaLyrics.Line line, JSONObject look, String mode) {
        if ("INSTEAD".equals(mode) && line.romanizedText != null) return line.romanizedText;
        return line.text;
    }

    private static Paint.Align align(JSONObject look) {
        return "CENTER".equalsIgnoreCase(look.optString("lyricsStripAlign", "START"))
                ? Paint.Align.CENTER : Paint.Align.LEFT;
    }

    private static float firstBaseline(IslandView owner, TextPaint paint, float top) {
        return top + owner.dp(4.0f) - paint.getFontMetrics().ascent;
    }

    private static float anchor(float left, float width, Paint.Align align, boolean alignEnd, String text) {
        Paint.Align actual = alignEnd ? Paint.Align.RIGHT
                : (align == Paint.Align.LEFT && isRtl(text) ? Paint.Align.RIGHT : align);
        if (actual == Paint.Align.CENTER) return left + width * 0.5f;
        if (actual == Paint.Align.RIGHT) return left + width;
        return left;
    }

    private static float anchor(float left, float width, float textWidth, Paint.Align align, boolean alignEnd) {
        Paint.Align actual = alignEnd ? Paint.Align.RIGHT : align;
        if (actual == Paint.Align.CENTER) return left + (width + textWidth) * 0.5f;
        if (actual == Paint.Align.RIGHT) return left + width;
        return left;
    }

    private static float alignedLeft(float left, float width, float textWidth, Paint.Align align, boolean alignEnd) {
        Paint.Align actual = alignEnd ? Paint.Align.RIGHT : align;
        if (actual == Paint.Align.CENTER) return left + (width - textWidth) * 0.5f;
        if (actual == Paint.Align.RIGHT) return left + width - textWidth;
        return left;
    }

    private static boolean isRtl(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            byte direction = Character.getDirectionality(text.charAt(i));
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
        }
        return false;
    }

    private static String shown(String text, JSONObject look) {
        if (text == null) return "";
        if (!look.optBoolean("uppercase", false)) return text;
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        return upper.length() == text.length() ? upper : text;
    }

    private static String wordText(List<MediaLyrics.Word> words, boolean uppercase) {
        StringBuilder result = new StringBuilder();
        for (MediaLyrics.Word word : words) {
            String text = uppercase ? word.text.toUpperCase(java.util.Locale.ROOT) : word.text;
            result.append(text);
        }
        return result.toString();
    }

    private static int lyricColor(JSONObject look, String key, String customKey, int accent, int fallback) {
        String kind = look.optString(key, "WHITE");
        if ("CUSTOM".equalsIgnoreCase(kind)) return look.optInt(customKey, fallback);
        if ("ACCENT".equalsIgnoreCase(kind) || "COVER".equalsIgnoreCase(kind)
                || "SPECTRUM".equalsIgnoreCase(kind)) return accent != 0 && accent != -1 ? accent : fallback;
        return "WHITE".equalsIgnoreCase(kind) ? Colors.WHITE : fallback;
    }

    private static void setShadow(IslandView owner, TextPaint paint, JSONObject look, int color) {
        if (look.optBoolean("haloNow", false)) paint.setShadowLayer(owner.dp(2.5f), 0f, 0f, color);
        else if (look.optBoolean("shadow", false)) paint.setShadowLayer(owner.dp(1.5f), 0f, owner.dp(0.5f), Colors.withAlpha(Colors.BLACK, 0.8f));
        else paint.clearShadowLayer();
    }

    private static void clearShadow(TextPaint paint) {
        paint.clearShadowLayer();
    }

    private static float spacing(JSONObject look) {
        return clamp((float) look.optDouble("spacing", 1.0d), 0.6f, 1.8f);
    }

    private static int count(String a, String b) {
        return (a == null ? 0 : 1) + (b == null ? 0 : 1);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
