package dev.android.dynamicisland.island.view.card;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import dev.android.dynamicisland.island.graphics.Colors;
import dev.android.dynamicisland.island.lyrics.MediaLyrics;
import dev.android.dynamicisland.island.view.IslandView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    static float rowHeightDp(JSONObject look, MediaLyrics lyrics) {
        if (look == null) return DEFAULT_ROW_DP;
        int mainRows = look.optBoolean("lyricsStripTwoLines", false) ? 2 : 1;
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
                + extraRows * 10.0f * extraSize + (backing ? 10.0f * backingSize : 0.0f);
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
            else message = "Prism lyrics not available yet";
            drawPlain(canvas, mainPaint, message, left, top + owner.dp(4.0f) - mainPaint.getFontMetrics().ascent,
                    width, Paint.Align.LEFT, alpha, Colors.withAlpha(Colors.ON_SURFACE_VARIANT, 0.78f * alpha));
            return;
        }

        boolean synced = data.type() != MediaLyrics.Type.STATIC;
        long now = Math.max(0L, positionMs);
        if (!synced) {
            state.reset();
            drawStatus(canvas, owner, look, mainPaint, "♫  Open Prism lyrics", left, top, width, alpha);
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
        float mainBaseline = top + owner.dp(4.0f) - fm.ascent;
        float slide = owner.dp(5.0f) * (1.0f - transition);
        if (state.previousText != null && transition < 1.0f) {
            drawPlain(canvas, mainPaint, state.previousText,
                    anchor(left, width, lineAlign, line.alignEnd), mainBaseline - slide,
                    left, width, lineAlign, 1.0f,
                    Colors.withAlpha(Colors.WHITE, alpha * (1.0f - transition)));
        }
        mainBaseline += slide;

        String translated = look.optBoolean("lyricsTranslate", false) ? line.translatedText : null;
        if (translated != null && translated.trim().equalsIgnoreCase(line.text.trim())) translated = null;
        String romanUnder = "UNDER".equals(mode) ? line.romanizedText : null;
        boolean extraAbove = "ABOVE".equalsIgnoreCase(look.optString("extraPlace", "BELOW"));
        float extraSize = clamp((float) look.optDouble("extraSize", 1.0d), 0.7f, 1.4f);
        float extraStep = owner.dp(9.5f * extraSize);
        int aboveCount = extraAbove ? count(romanUnder, translated) : 0;
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
        drawTimedLine(canvas, owner, mainPaint, mainText, words, line.startMs, line.endMs,
                left, width, mainBaseline, lineAlign, line.alignEnd, now, synced, motion,
                sungColor, otherColor, othersAlpha, alpha * transition, look);

        // Prism's two-line setting limits wrapping of this current line; it does not show the next lyric.
        float afterBaseline = mainBaseline + (look.optBoolean("lyricsStripTwoLines", false) ? lineHeight * 2.0f : lineHeight);

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
            extraPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
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
        }
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

        if (motion == 0 && activeWidth > 0.0f) { // Prism GROW's active word gains weight while it is sung.
            String activeText = "";
            float cursor = 0.0f;
            for (MediaLyrics.Word word : words) {
                String part = shown(word.text, look);
                if (Math.abs(cursor - activeStart) < 0.5f) { activeText = part; break; }
                cursor += paint.measureText(part);
            }
            if (!activeText.isEmpty()) {
                Paint.Align oldAlign = paint.getTextAlign();
                float oldSize = paint.getTextSize();
                boolean oldBold = paint.isFakeBoldText();
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setTextSize(oldSize * 1.035f);
                paint.setFakeBoldText(true);
                paint.setColor(Colors.withAlpha(sungColor, alpha));
                float wordX = rtl ? textLeft + fullWidth - activeStart - activeWidth : textLeft + activeStart;
                canvas.drawText(activeText, wordX, baseline, paint);
                paint.setTextSize(oldSize);
                paint.setFakeBoldText(oldBold);
                paint.setTextAlign(oldAlign);
            }
        }
    }

    private static void drawExtra(Canvas canvas, IslandView owner, TextPaint paint, String text,
            float left, float width, float baseline, Paint.Align align, float alpha, int color,
            JSONObject look, float sizeScale, float opacity, boolean alignEnd) {
        if (text == null || text.isEmpty()) return;
        float oldSize = paint.getTextSize();
        Typeface oldTypeface = paint.getTypeface();
        Paint.Align oldAlign = paint.getTextAlign();
        paint.setTextSize(owner.dp(12.0f * sizeScale * 0.52f));
        paint.setTypeface(Typeface.create("sans-serif", look.optBoolean("extraItalic", false)
                ? Typeface.ITALIC : Typeface.NORMAL));
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
        paint.setTextSize(owner.dp(12.0f * size));
        paint.setTypeface(typeface(look.optString("weightNow", look.optString("weight", "LIGHT"))));
        paint.setFakeBoldText("CARD".equalsIgnoreCase(look.optString("style", "")));
        paint.setTextAlign(align(look));
    }

    private static void configureExtraPaint(IslandView owner, TextPaint paint, JSONObject look) {
        paint.setTextSize(owner.dp(6.2f * clamp((float) look.optDouble("extraSize", 1.0d), 0.7f, 1.4f)));
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setFakeBoldText(false);
    }

    private static Typeface typeface(String weight) {
        if ("THIN".equalsIgnoreCase(weight)) return Typeface.create("sans-serif-thin", Typeface.NORMAL);
        if ("LIGHT".equalsIgnoreCase(weight)) return Typeface.create("sans-serif-light", Typeface.NORMAL);
        if ("MEDIUM".equalsIgnoreCase(weight)) return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        if ("BOLD".equalsIgnoreCase(weight)) return Typeface.create("sans-serif", Typeface.BOLD);
        return Typeface.create("sans-serif", Typeface.NORMAL);
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
