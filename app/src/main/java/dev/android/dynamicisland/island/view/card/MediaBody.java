package dev.android.dynamicisland.island.view.card;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import dev.android.dynamicisland.island.graphics.Colors;
import dev.android.dynamicisland.island.graphics.Glyphs;
import dev.android.dynamicisland.island.lyrics.LyricsManager;
import dev.android.dynamicisland.island.lyrics.MediaLyrics;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.IslandView;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.island.view.media.MediaFx;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Music player card, drawn on top of the animated glow of {@link MediaFx}.
 *
 * <pre>
 * +----------------------------------------------+
 * | [art]  Title                          [app]  |   56dp cover art (flips on track change), title,
 * |        Artist                                |   artist and optional output device; app tile at
 * |        (o) Output device . 80%               |   the right
 * | 01:23 =======o---------------------- 03:45   |   elapsed time, seek bar with glowing head, total
 * |  (c1)    |&lt;    ( || )    &gt;|    (c2)         |   custom action, previous, play/pause, next, custom
 * +----------------------------------------------+
 * </pre>
 *
 * The seek bar can be dragged ({@link #onSeekBar}, {@link #fractionAt}); while dragging, the view keeps this
 * body in {@code owner.seekBody} and the dragged position is shown instead of the playback position.
 */
public final class MediaBody extends CardBody {
    private static final float ART_CORNER_DP = 12.0f;
    /** Gap between art and text, and between lines of text. */
    private static final float ART_TEXT_GAP_DP = 14.0f;
    private static final float TEXT_LINE_GAP_DP = 3.0f;
    private static final float TILE_CORNER_DP = 9.0f;
    /** Horizontal centres of the five controls as a fraction of the content width. */
    private static final float CUSTOM_LEFT_AT = 0.08f;
    private static final float PREV_AT = 0.3f;
    private static final float PLAY_AT = 0.5f;
    private static final float NEXT_AT = 0.7f;
    private static final float CUSTOM_RIGHT_AT = 0.92f;
    /** Size of the previous / next / custom buttons relative to the play button. */
    private static final float SIDE_BUTTON_RATIO = 0.84f;
    /** Slide-up distance of a row while it fades in. */
    private static final float ROW_SLIDE_DP = 8.0f;
    private static final float LYRICS_ROW_DP = 38.0f;
    private static final String NO_DURATION = "--:--";

    final float art;
    final StaticLayout artist;
    float barLeft;
    float barRight;
    final float controlH;
    final float controlsTop;
    /** "Device . 80%" line shown under the artist when playing on another output. */
    final StaticLayout output;
    private final Paint glowPaint;
    /** Lyrics use dedicated paints so their sizes/alignments cannot mutate the shared title layouts. */
    private final TextPaint lyricsCurrentPaint;
    private final TextPaint lyricsNextPaint;
    private final String lyricsTitle;
    private final String lyricsArtist;
    private final String lyricsKey;
    private final boolean prismSession;
    private final MediaLyrics prismLyrics;
    private final String prismLyricsStatus;
    private final int prismSungColor;
    private final int prismOtherColor;
    private final float prismOtherAlpha;
    private final float prismSizeScale;
    private final boolean prismUppercase;
    private final boolean prismBoldLines;
    private final boolean prismTwoLines;
    private final boolean prismWordByWord;
    private final String prismStyle;
    private final Paint.Align prismTextAlign;
    private final boolean lyricsEnabled;
    /** Prepared text is rebuilt only when the track or current line changes, never per frame. */
    private MediaLyrics cachedLyrics;
    private int cachedLyricIndex = Integer.MIN_VALUE;
    private String cachedCurrentLine;
    private String cachedNextLine;
    private boolean cachedCurrentLineFull;
    final float pad;
    final float seekTop;
    final float lyricsTop;
    /** Size of the app tile at the top right. */
    final float tile;
    final StaticLayout title;
    private final RectF glowRect;
    private long clockSecond=Long.MIN_VALUE,durationSecond=Long.MIN_VALUE;
    private String clockText,durationText;

    public MediaBody(IslandView owner, IslandItem item, float w) {
        super(owner, item, w);
        lyricsCurrentPaint = new TextPaint(owner.mediaTitlePaint());
        lyricsNextPaint = new TextPaint(owner.mediaArtistPaint());
        lyricsTitle = item.title == null ? "" : item.title.toString();
        lyricsArtist = item.subtitle == null ? "" : item.subtitle.toString();
        prismSession = "com.prism.music".equals(item.packageName);
        prismLyrics = item.mediaLyrics;
        prismLyricsStatus = item.mediaLyricsStatus == null ? "UNKNOWN" : item.mediaLyricsStatus;
        JSONObject prismLook = parsePrismLook(item.mediaLyricsLook);
        prismSungColor = prismColor(prismLook, "sungColor", "sungCustom", item.accent, Colors.ON_SURFACE);
        prismOtherColor = prismColor(prismLook, "otherColor", "otherCustom", item.accent, Colors.ON_SURFACE_VARIANT);
        prismOtherAlpha = clamp(prismLook == null ? 0.55f : (float) prismLook.optDouble("othersAlpha", 0.55d), 0.2f, 1.0f);
        prismSizeScale = clamp(prismLook == null ? 1.0f : (float) prismLook.optDouble("sizeScale", 1.0d), 0.7f, 1.4f);
        prismUppercase = prismLook != null && prismLook.optBoolean("uppercase", false);
        prismBoldLines = prismLook != null && ("BOLD".equalsIgnoreCase(prismLook.optString("weightNow", prismLook.optString("weight", "LIGHT")))
                || "CARD".equalsIgnoreCase(prismLook.optString("style", "")));
        prismTwoLines = prismLook != null && prismLook.optBoolean("lyricsStripTwoLines", false);
        prismWordByWord = prismLook != null && prismLook.optBoolean("wordByWordNow", false);
        prismStyle = prismLook == null ? "PRISM" : prismLook.optString("style", "PRISM");
        String align = prismLook == null ? "START" : prismLook.optString("lyricsStripAlign",
                prismLook.optString("lyricsAlign", "START"));
        prismTextAlign = "CENTER".equalsIgnoreCase(align) ? Paint.Align.CENTER : Paint.Align.LEFT;
        // Prism's bridge is the sole lyric source for Prism sessions; never run a second provider cascade.
        lyricsEnabled = prismSession ? !"UNKNOWN".equals(prismLyricsStatus) : ModuleSettings.mediaLyricsEnabled();
        lyricsKey = !prismSession && lyricsEnabled ? LyricsManager.keyFor(lyricsTitle, lyricsArtist) : "";
        pad = owner.dp(18.0f);
        art = owner.dp(56.0f);
        tile = owner.dp(32.0f);
        controlH = owner.dp(52.0f);
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowRect = new RectF();
        int textWidth = Math.max(1, Math.round(((((w - (pad * 2.0f)) - art) - owner.dp(ART_TEXT_GAP_DP)) - tile) - owner.dp(10.0f)));
        title = item.title == null ? null : IslandView.layout(item.title, owner.mediaTitlePaint(), textWidth, 1);
        CharSequence artistText = item.subtitle;
        if (artistText != null && item.mediaAlbum != null && item.mediaAlbum.length() > 0) {
            artistText = artistText + " · " + item.mediaAlbum;
        }
        artist = artistText != null ? IslandView.layout(artistText, owner.mediaArtistPaint(), textWidth, 1) : null;
        String outputText = null;
        if (item.mediaFlags != null && item.mediaFlags.length() > 0) {
            outputText = item.mediaFlags.toString();
        }
        if (item.mediaInfo != null && item.mediaInfo.length() > 0) {
            outputText = outputText == null ? item.mediaInfo.toString() : outputText + " · " + item.mediaInfo;
        }
        if (item.mediaOutput != null) {
            String device = item.mediaOutput.toString() + (item.mediaOutputBattery >= 0 ? " · " + item.mediaOutputBattery + "%" : "");
            outputText = outputText == null ? device : outputText + " · " + device;
        }
        output = outputText == null ? null
                : IslandView.layout(outputText, owner.smallPaint, Math.max(1, textWidth - Math.round(owner.dp(18.0f))), 1);
        seekTop = pad + art + owner.dp(16.0f);
        controlsTop = seekTop + owner.dp(14.0f) + owner.dp(10.0f);
        lyricsTop = controlsTop + controlH + owner.dp(5.0f);
        String totalText = item.mediaDuration > 0 ? IslandView.formatTimePadded(item.mediaDuration) : NO_DURATION;
        barLeft = pad + owner.smallPaint.measureText("00:00") + owner.dp(10.0f);
        barRight = ((w - pad) - owner.smallPaint.measureText(totalText)) - owner.dp(10.0f);
        float span = w - (pad * 2.0f);
        if (item.mediaCustom.size() > 0) {
            addCustom(item.mediaCustom.get(0), pad + (CUSTOM_LEFT_AT * span));
        }
        addControl(Glyphs.PREV, pad + (PREV_AT * span), item.mediaPrev, false);
        addControl(item.playing ? Glyphs.PAUSE : Glyphs.PLAY, pad + (PLAY_AT * span), item.mediaPlayPause, true);
        addControl(Glyphs.NEXT, pad + (NEXT_AT * span), item.mediaNext, false);
        if (item.mediaCustom.size() > 1) {
            addCustom(item.mediaCustom.get(1), pad + (CUSTOM_RIGHT_AT * span));
        }
        h = lyricsTop + (lyricsEnabled ? owner.dp(LYRICS_ROW_DP) : 0.0f) + owner.dp(12.0f);
        if (lyricsEnabled && !prismSession && item.title != null && item.subtitle != null) {
            LyricsManager.request(owner.getContext(), lyricsTitle, lyricsArtist,
                    item.mediaDuration, owner::postInvalidateOnAnimation);
        }
    }

    private static JSONObject parsePrismLook(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return new JSONObject(raw); } catch (Exception ignored) { return null; }
    }

    private static int prismColor(JSONObject look, String colorKey, String customKey, int accent, int fallback) {
        if (look == null) return fallback;
        String kind = look.optString(colorKey, "WHITE");
        if ("CUSTOM".equalsIgnoreCase(kind)) return look.optInt(customKey, fallback);
        if ("ACCENT".equalsIgnoreCase(kind) || "COVER".equalsIgnoreCase(kind)
                || "SPECTRUM".equalsIgnoreCase(kind)) return accent != 0 && accent != -1 ? accent : fallback;
        return fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Whether ({@code lx}, {@code ly}) in body coordinates is on the seek bar and the item can seek. */
    public boolean onSeekBar(float lx, float ly) {
        float barCenterY = seekTop + owner.dp(7.0f);
        return item.mediaSeek != null && item.mediaDuration > 0 && ly > barCenterY - owner.dp(18.0f) && ly < owner.dp(18.0f) + barCenterY && lx > barLeft - owner.dp(12.0f) && lx < barRight + owner.dp(12.0f);
    }

    /** Position on the seek bar (0..1) for a touch at {@code lx}. */
    public float fractionAt(float lx) {
        return IslandView.clamp01((lx - barLeft) / Math.max(1.0f, barRight - barLeft));
    }

    private void addControl(int glyph, float centerX, Runnable onTap, boolean circle) {
        Btn b = new Btn();
        b.glyph = glyph;
        b.circle = circle;
        b.bg = circle ? Colors.primaryContainer(item.accent) : Colors.CONTROL_FILL;
        b.run = onTap;
        float size = controlH;
        if (!circle) {
            size *= SIDE_BUTTON_RATIO;
        }
        b.r.set(centerX - (size / 2.0f), controlsTop + ((controlH - size) / 2.0f), (size / 2.0f) + centerX, controlsTop + ((controlH + size) / 2.0f));
        buttons.add(b);
    }

    /** Custom player action (shuffle, repeat, like ...): its icon, or its title when it has no icon. */
    private void addCustom(IslandItem.Action action, float centerX) {
        Btn btn = new Btn();
        btn.icon = action.icon;
        String label = null;
        if (action.icon == null && action.title != null) {
            label = action.title.toString();
        }
        btn.label = label;
        btn.circle = true;
        btn.bg = Colors.TRANSPARENT_MARK;
        btn.run = action.runnable;
        float size = controlH * SIDE_BUTTON_RATIO;
        btn.r.set(centerX - (size / 2.0f), controlsTop + ((controlH - size) / 2.0f), (size / 2.0f) + centerX, controlsTop + ((controlH + size) / 2.0f));
        buttons.add(btn);
    }

    @Override
    public void draw(Canvas c, float x, float y, float alpha, long elapsed) {
        long now = IslandView.now();
        MediaFx fx = owner.mediaFx(item);
        fx.advance(now);
        float topRowProgress = rowAlpha(elapsed, 0);
        float topAlpha = alpha * topRowProgress;
        float topDy = (1.0f - topRowProgress) * owner.dp(ROW_SLIDE_DP);
        fx.drawArt(c, x + pad, y + pad + topDy, art, topAlpha, owner.dp(ART_CORNER_DP), now);
        drawTexts(c, x, y, topDy, topAlpha);
        drawAppTile(c, x, y, topDy, topAlpha);
        float seekRowProgress = rowAlpha(elapsed, 1);
        drawSeekRow(c, x, y + seekTop + ((1.0f - seekRowProgress) * owner.dp(ROW_SLIDE_DP)) + owner.dp(7.0f), alpha * seekRowProgress, fx, now);
        float controlsRowProgress = rowAlpha(elapsed, 2);
        drawButtons(c, x, y + ((1.0f - controlsRowProgress) * owner.dp(ROW_SLIDE_DP)), alpha * controlsRowProgress);
        if (lyricsEnabled) {
            drawLyrics(c, x + pad, y + lyricsTop, w - pad * 2, alpha * controlsRowProgress,
                    fx.shownPosition(item, SystemClock.elapsedRealtime()));
        }
    }

    /** Current and next line of synced lyrics, using the playback clock shared with the seek row. */
    private void drawLyrics(Canvas canvas, float left, float top, float width, float alpha, long positionMs) {
        MediaLyrics data = prismSession ? prismLyrics : LyricsManager.getByKey(lyricsKey);
        TextPaint current = lyricsCurrentPaint;
        TextPaint next = lyricsNextPaint;
        current.setTextSize(owner.dp(12.0f * (prismSession ? prismSizeScale : 1.0f)));
        next.setTextSize(owner.dp(10.5f * (prismSession ? prismSizeScale : 1.0f)));
        current.setFakeBoldText(prismSession && prismBoldLines);
        next.setFakeBoldText(prismSession && prismBoldLines);
        current.setTextAlign(prismSession ? prismTextAlign : Paint.Align.LEFT);
        next.setTextAlign(prismSession ? prismTextAlign : Paint.Align.LEFT);
        Paint.FontMetrics fm = current.getFontMetrics();
        float baseline = top + owner.dp(5.0f) - fm.ascent;
        if (data == null || data.isEmpty()) {
            String status;
            if (prismSession) {
                if ("LOADING".equals(prismLyricsStatus)) status = "Loading Prism lyrics…";
                else if ("WAITING".equals(prismLyricsStatus)) status = "Open Prism to load lyrics";
                else status = "No lyrics available in Prism";
            } else {
                status = LyricsManager.isLoadingByKey(lyricsKey)
                        ? "Finding synced lyrics…" : "No synced lyrics found";
            }
            current.setColor(Colors.withAlpha(Colors.ON_SURFACE_VARIANT, 0.78f * alpha));
            canvas.drawText(status, left, baseline, current);
            return;
        }

        if (prismSession && data.type() == MediaLyrics.Type.STATIC) {
            current.setColor(Colors.withAlpha(Colors.ON_SURFACE_VARIANT, 0.78f * alpha));
            canvas.drawText("Prism lyrics are not synchronized", left, baseline, current);
            return;
        }

        int index = data.currentIndex(Math.max(0L, positionMs));
        if (data != cachedLyrics || index != cachedLyricIndex) {
            cachedLyrics = data;
            cachedLyricIndex = index;
            cachedCurrentLine = null;
            cachedNextLine = null;
            cachedCurrentLineFull = false;
            if (index >= 0) {
                MediaLyrics.Line line = data.line(index);
                String raw = line.text == null || line.text.isEmpty() ? "♪" : line.text;
                if (prismSession && prismUppercase) raw = raw.toUpperCase(Locale.ROOT);
                cachedCurrentLine = fitLyricText(raw, current, width);
                cachedCurrentLineFull = cachedCurrentLine.length() == raw.length();
                if (index + 1 < data.size()) {
                    String following = data.line(index + 1).text;
                    if (prismSession && prismUppercase && following != null) following = following.toUpperCase(Locale.ROOT);
                    cachedNextLine = fitLyricText(following == null || following.isEmpty() ? "♪" : following, next, width);
                }
            }
        }

        if (index < 0) {
            current.setColor(Colors.withAlpha(Colors.ON_SURFACE_VARIANT, 0.78f * alpha));
            canvas.drawText("♪", left, baseline, current);
            return;
        }

        int otherColor = prismSession ? prismOtherColor : Colors.ON_SURFACE_VARIANT;
        float otherAlpha = prismSession ? prismOtherAlpha : 0.78f;
        current.setColor(Colors.withAlpha(otherColor, otherAlpha * alpha));
        float anchorX = lyricAnchorX(left, width, prismSession ? prismTextAlign : Paint.Align.LEFT,
                prismSession && data.line(index).alignEnd);
        current.setTextAlign(prismSession && data.line(index).alignEnd ? Paint.Align.RIGHT
                : (prismSession ? prismTextAlign : Paint.Align.LEFT));
        canvas.drawText(cachedCurrentLine == null ? "♪" : cachedCurrentLine, anchorX, baseline, current);
        if (!prismSession || data.type() == MediaLyrics.Type.SYLLABLE) {
            drawActiveLyricWord(canvas, data, data.line(index), left, baseline, width, alpha, positionMs);
        } else if (prismSession && "CALM".equalsIgnoreCase(prismStyle)) {
            current.setColor(Colors.withAlpha(prismSungColor, alpha));
            canvas.drawText(cachedCurrentLine == null ? "♪" : cachedCurrentLine, anchorX, baseline, current);
        }
        if (cachedNextLine != null && (!prismSession || prismTwoLines)) {
            next.setColor(Colors.withAlpha(otherColor, otherAlpha * 0.70f * alpha));
            next.setTextAlign(prismTextAlignForLine(data, index + 1));
            float nextX = lyricAnchorX(left, width, next.getTextAlign(),
                    prismSession && data.line(index + 1).alignEnd);
            canvas.drawText(cachedNextLine, nextX, baseline + owner.dp(14.0f), next);
        }
    }

    private float lyricAnchorX(float left, float width, Paint.Align align, boolean alignEnd) {
        if (alignEnd) return left + width;
        if (align == Paint.Align.CENTER) return left + width * 0.5f;
        return left;
    }

    private Paint.Align prismTextAlignForLine(MediaLyrics data, int index) {
        if (prismSession && data != null && index >= 0 && index < data.size()
                && data.line(index).alignEnd) return Paint.Align.RIGHT;
        return prismSession ? prismTextAlign : Paint.Align.LEFT;
    }

    /** Ellipsize once per line change, not on every animation frame. */
    private static String fitLyricText(String text, TextPaint paint, float width) {
        if (text == null || text.isEmpty() || paint.measureText(text) <= width) return text == null ? "" : text;
        String ellipsis = "…";
        float available = Math.max(0.0f, width - paint.measureText(ellipsis));
        int end = text.length();
        while (end > 0 && paint.measureText(text, 0, end) > available) end--;
        return end <= 0 ? ellipsis : text.substring(0, end).trim() + ellipsis;
    }

    /** Highlights a timed word/syllable without allocating a prefix StringBuilder per frame. */
    private void drawActiveLyricWord(Canvas canvas, MediaLyrics data, MediaLyrics.Line line,
                                     float left, float baseline, float width, float alpha, long positionMs) {
        if (!cachedCurrentLineFull || line.words.isEmpty()) return;
        int active = data.currentWordIndex(line, Math.max(0L, positionMs));
        if (active < 0 || active >= line.words.size()) return;
        TextPaint paint = lyricsCurrentPaint;
        float prefixWidth = 0.0f;
        float lineWidth = 0.0f;
        for (MediaLyrics.Word value : line.words) {
            String text = prismSession && prismUppercase ? value.text.toUpperCase(Locale.ROOT) : value.text;
            lineWidth += paint.measureText(text);
        }
        for (int i = 0; i < active; i++) {
            String text = line.words.get(i).text;
            if (prismSession && prismUppercase) text = text.toUpperCase(Locale.ROOT);
            prefixWidth += paint.measureText(text);
        }
        String word = line.words.get(active).text;
        if (prismSession && prismUppercase) word = word.toUpperCase(Locale.ROOT);
        float startX = lyricAnchorX(left, width, prismSession ? prismTextAlign : Paint.Align.LEFT, line.alignEnd);
        float wordX;
        if (line.alignEnd) wordX = startX - lineWidth + prefixWidth;
        else if (prismSession && prismTextAlign == Paint.Align.CENTER) wordX = startX - lineWidth * 0.5f + prefixWidth;
        else wordX = startX + prefixWidth;
        float wordWidth = paint.measureText(word);
        if (wordWidth <= 0.0f || wordX < left || wordX + wordWidth > left + width) return;
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Colors.withAlpha(prismSession ? prismSungColor : Colors.ON_SURFACE, alpha));
        canvas.drawText(word, wordX, baseline, paint);
    }

    @Override public MediaFx materialMusic() { return owner.mediaFx(item); }

    @Override
    public void drawBackground(Canvas c, RectF frame, float alpha) {
        long now = IslandView.now();
        MediaFx fx = owner.mediaFx(item);
        fx.advance(now);
        int save = c.save();
        c.translate(frame.left, frame.top);
        c.scale(frame.width() / w, frame.height() / h);
        fx.drawGlow(c, 0.0f, 0.0f, w, h, alpha, now);
        c.restoreToCount(save);
    }

    /** Title, artist and output lines, vertically centred next to the art. */
    private void drawTexts(Canvas c, float x, float y, float dy, float alpha) {
        float textX = x + pad + art + owner.dp(ART_TEXT_GAP_DP);
        float gap = owner.dp(TEXT_LINE_GAP_DP);
        float textHeight = (title != null ? title.getHeight() : 0)
                + (artist != null ? gap + artist.getHeight() : 0.0f)
                + (output != null ? gap + output.getHeight() : 0.0f);
        float lineY = y + pad + dy + ((art - textHeight) / 2.0f);
        if (title != null) {
            owner.mediaTitlePaint().setColor(Colors.withAlpha(Colors.ON_SURFACE, alpha));
            IslandView.drawLayout(c, title, textX, lineY);
            lineY += title.getHeight() + gap;
        }
        if (artist != null) {
            owner.mediaArtistPaint().setColor(Colors.withAlpha(Colors.ON_SURFACE_VARIANT, 0.55f * alpha));
            IslandView.drawLayout(c, artist, textX, lineY);
            lineY += artist.getHeight() + gap;
        }
        if (output != null) {
            owner.smallPaint.setColor(Colors.withAlpha(Colors.TEXT_SECONDARY, alpha));
            float outputIconSize = owner.dp(13.0f);
            if (item.mediaOutputIcon != null) {
                owner.drawDrawable(c, item.mediaOutputIcon, false, Colors.withAlpha(Colors.WHITE, alpha), textX, lineY + ((output.getHeight() - outputIconSize) / 2.0f), outputIconSize, alpha, 0.0f);
            }
            IslandView.drawLayout(c, output, textX + owner.dp(18.0f), lineY);
        }
    }

    /** Small tile at the top right showing the player app (a glyph on a dark tile, or the app icon). */
    private void drawAppTile(Canvas c, float x, float y, float dy, float alpha) {
        if (item.appGlyph == null && item.appIcon == null) {
            return;
        }
        float tileLeft = ((x + w) - pad) - tile;
        float tileTop = y + pad + dy;
        if (item.appGlyph != null) {
            owner.tmpRect.set(tileLeft, tileTop, tile + tileLeft, tile + tileTop);
            owner.fillPaint.setColor(Colors.withAlpha(Colors.SURFACE_CONTAINER_LOW, alpha));
            c.drawRoundRect(owner.tmpRect, owner.dp(TILE_CORNER_DP), owner.dp(TILE_CORNER_DP), owner.fillPaint);
            float glyphSize = tile * 1.25f;
            owner.drawDrawable(c, item.appGlyph, false, Colors.WHITE, ((tile - glyphSize) / 2.0f) + tileLeft, ((tile - glyphSize) / 2.0f) + tileTop, glyphSize, alpha, 0.0f);
        } else {
            owner.drawDrawable(c, item.appIcon, true, 0, tileLeft, tileTop, tile, alpha, owner.dp(TILE_CORNER_DP));
        }
    }

    /**
     * Elapsed time, seek bar and total time, vertically centred on {@code centerY}. The bar thickens while dragged and
     * ends in a crisp highlight whose brightness follows the playback state.
     */
    private void drawSeekRow(Canvas c, float x, float centerY, float alpha, MediaFx fx, long now) {
        long position = fx.shownPosition(item, SystemClock.elapsedRealtime());
        long second=Math.max(0L,position)/1000;
        if(clockText==null||clockSecond!=second){clockSecond=second;clockText=IslandView.formatTimePadded(Math.max(0L,position));}
        long duration=item.mediaDuration>0 ? item.mediaDuration/1000 : -1;
        if(durationText==null||durationSecond!=duration){durationSecond=duration;durationText=item.mediaDuration>0 ? IslandView.formatTimePadded(item.mediaDuration) : NO_DURATION;}
        String current=clockText,total=durationText;
        owner.smallPaint.setColor(Colors.withAlpha(Colors.TEXT_SECONDARY, alpha));
        Paint.FontMetrics fm = owner.smallPaint.getFontMetrics();
        float baseline = centerY - ((fm.ascent + fm.descent) / 2.0f);
        boolean dragged = owner.seekBody == this;
        if (!dragged) {
            c.drawText(current, x + pad, baseline, owner.smallPaint);
        }
        float totalWidth = owner.smallPaint.measureText(total);
        c.drawText(total, ((x + w) - pad) - totalWidth, baseline, owner.smallPaint);
        float trackLeft = x + barLeft;
        float trackRight = x + barRight;
        if (dragged) {
            String draggedTime = IslandView.formatTimePadded((long) (Math.max(0L, item.mediaDuration) * owner.seekFraction));
            c.drawText(draggedTime, x + pad, baseline, owner.smallPaint);
        }
        float barHeight = owner.dp(dragged ? 7.0f : 3.5f);
        owner.tmpRect.set(trackLeft, centerY - (barHeight / 2.0f), trackRight, (barHeight / 2.0f) + centerY);
        owner.fillPaint.setColor(Colors.withAlpha(Colors.SEEK_TRACK, alpha));
        c.drawRoundRect(owner.tmpRect, barHeight / 2.0f, barHeight / 2.0f, owner.fillPaint);
        float fraction = 0.0f;
        if (dragged) {
            fraction = owner.seekFraction;
        } else if (item.mediaDuration > 0) {
            fraction = IslandView.clamp01(((float) position) / ((float) item.mediaDuration));
        }
        owner.tmpRect.right = Math.max(2.4f * barHeight, (trackRight - trackLeft) * fraction) + trackLeft;
        float head = owner.tmpRect.right;
        // pulses with the music while playing, dims when paused
        float glowAlpha = ((fx.liveness(now) * ((((float) Math.sin(fx.seekPhase)) * IslandView.PAUSED_SPEED) + 0.05f)) + 0.8f) * alpha;
        glowPaint.setColor(Colors.withAlpha(Colors.WHITE, 0.45f * glowAlpha));
        glowRect.set(Math.max(trackLeft, head - owner.dp(70.0f)), centerY - (barHeight / 2.0f), head, centerY + (barHeight / 2.0f));
        c.drawRoundRect(glowRect, barHeight / 2.0f, barHeight / 2.0f, glowPaint);
        glowPaint.setColor(Colors.withAlpha(Colors.WHITE, glowAlpha * 0.9f));
        c.drawCircle(head - (barHeight * 0.6f), centerY, 0.95f * barHeight, glowPaint);
        owner.fillPaint.setColor(Colors.withAlpha(Colors.SEEK_FILL, alpha));
        c.drawRoundRect(owner.tmpRect, barHeight / 2.0f, barHeight / 2.0f, owner.fillPaint);
    }
}
