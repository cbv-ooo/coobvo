package dev.android.dynamicisland.island.view.card;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.Layout;
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
import java.util.Collections;

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
    /** Extra lines are reserved for SpicyLyrics/other providers that return backing or alternate text. */
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
    private final JSONObject prismLook;
    private final PrismPlayerPreferences prismPlayer;
    private final TextPaint playerTitlePaint;
    private final TextPaint playerArtistPaint;
    private final float prismLyricsRowDp;
    private final PrismLyricsPainter.State prismLyricState = new PrismLyricsPainter.State();
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
    private String cachedBackgroundLine;
    private String cachedRomanizedLine;
    private String cachedTranslatedLine;
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
        prismLook = parsePrismLook(item.mediaLyricsLook);
        prismPlayer = prismSession ? PrismPlayerPreferences.fromLyricsLook(item.mediaLyricsLook)
                : PrismPlayerPreferences.defaults();
        playerTitlePaint = new TextPaint(owner.mediaTitlePaint());
        playerArtistPaint = new TextPaint(owner.mediaArtistPaint());
        if (prismSession) {
            float titleScale = prismPlayer.titleScale();
            playerTitlePaint.setTextSize(playerTitlePaint.getTextSize() * titleScale);
            playerArtistPaint.setTextSize(playerArtistPaint.getTextSize() * titleScale);
            if ("BOLD".equals(prismPlayer.titleWeight)) {
                playerTitlePaint.setFakeBoldText(true);
                playerArtistPaint.setFakeBoldText(true);
            }
        }
        prismLyricsRowDp = PrismLyricsPainter.rowHeightDp(prismLook, prismLyrics);
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
        // Prism lyrics belong to Prism's media session; always reserve the row while the bridge is loading too.
        lyricsEnabled = prismSession || ModuleSettings.mediaLyricsEnabled();
        lyricsKey = !prismSession && lyricsEnabled ? LyricsManager.keyFor(lyricsTitle, lyricsArtist) : "";
        pad = owner.dp(18.0f);
        art = owner.dp(56.0f);
        tile = owner.dp(32.0f);
        controlH = owner.dp(52.0f);
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowRect = new RectF();
        int textWidth = Math.max(1, Math.round(((((w - (pad * 2.0f)) - art) - owner.dp(ART_TEXT_GAP_DP)) - tile) - owner.dp(10.0f)));
        Layout.Alignment titleAlignment = prismSession && "CENTER".equals(prismPlayer.titleAlign)
                ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL;
        title = item.title == null ? null
                : IslandView.layout(item.title, playerTitlePaint, textWidth, 1, titleAlignment);
        CharSequence artistText = item.subtitle;
        if (artistText != null && item.mediaAlbum != null && item.mediaAlbum.length() > 0) {
            artistText = artistText + " · " + item.mediaAlbum;
        }
        artist = artistText != null
                ? IslandView.layout(artistText, playerArtistPaint, textWidth, 1, titleAlignment) : null;
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
        boolean showElapsed = !prismSession || prismPlayer.showElapsedTime();
        boolean showRightTime = !prismSession || prismPlayer.showRightTime();
        barLeft = pad + (showElapsed ? owner.smallPaint.measureText("00:00") + owner.dp(10.0f) : 0.0f);
        String rightWidthText = prismSession && prismPlayer.showRemainingTime() ? "−00:00" : totalText;
        barRight = ((w - pad) - (showRightTime
                ? owner.smallPaint.measureText(rightWidthText) + owner.dp(10.0f) : 0.0f));
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
        h = lyricsTop + (lyricsEnabled ? owner.dp(prismSession ? prismLyricsRowDp : LYRICS_ROW_DP) : 0.0f) + owner.dp(12.0f);
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
        fx.drawArt(c, x + pad, y + pad + topDy, art, topAlpha, artCornerRadius(), now);
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
        if (prismSession) {
            PrismLyricsPainter.draw(canvas, owner, prismLyrics, prismLook, prismLyricState, lyricsCurrentPaint, lyricsNextPaint,
                    prismLyricsStatus, left, top, width, alpha, positionMs, item.accent);
            return;
        }
        MediaLyrics data = LyricsManager.getByKey(lyricsKey);
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

        if (data.type() == MediaLyrics.Type.STATIC) {
            String staticText = data.line(0).text;
            current.setColor(Colors.withAlpha(Colors.WHITE, alpha));
            canvas.drawText(fitLyricText(staticText, current, width), left, baseline, current);
            return;
        }

        int index = data.currentIndex(Math.max(0L, positionMs));
        if (data != cachedLyrics || index != cachedLyricIndex) {
            cachedLyrics = data;
            cachedLyricIndex = index;
            cachedCurrentLine = null;
            cachedNextLine = null;
            cachedBackgroundLine = null;
            cachedRomanizedLine = null;
            cachedTranslatedLine = null;
            cachedCurrentLineFull = false;
            if (index >= 0) {
                MediaLyrics.Line line = data.line(index);
                String raw = line.text == null || line.text.isEmpty() ? "♪" : line.text;
                if (prismSession && prismUppercase) raw = raw.toUpperCase(Locale.ROOT);
                cachedCurrentLine = fitLyricText(raw, current, width);
                cachedCurrentLineFull = cachedCurrentLine.length() == raw.length();
                cachedBackgroundLine = mediaWordsText(line.background);
                cachedRomanizedLine = blankToNull(line.romanizedText);
                cachedTranslatedLine = blankToNull(line.translatedText);
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
        boolean hasLyricExtras = !prismSession && (cachedBackgroundLine != null
                || cachedRomanizedLine != null || cachedTranslatedLine != null);
        if (cachedNextLine != null && (!prismSession || prismTwoLines) && !hasLyricExtras) {
            next.setColor(Colors.withAlpha(otherColor, otherAlpha * 0.70f * alpha));
            next.setTextAlign(prismTextAlignForLine(data, index + 1));
            float nextX = lyricAnchorX(left, width, next.getTextAlign(),
                    prismSession && data.line(index + 1).alignEnd);
            canvas.drawText(cachedNextLine, nextX, baseline + owner.dp(14.0f), next);
        }
        if (hasLyricExtras) {
            MediaLyrics.Line line = data.line(index);
            float extraBaseline = baseline + owner.dp(14.0f);
            float extraColorAlpha = 0.78f * alpha;
            if (cachedBackgroundLine != null) {
                drawLyricAuxiliary(canvas, next, cachedBackgroundLine, line.background,
                        left, width, extraBaseline, positionMs, alpha, owner.dp(8.0f),
                        Colors.withAlpha(Colors.ON_SURFACE_VARIANT, extraColorAlpha));
                extraBaseline += owner.dp(9.5f);
            }
            if (cachedRomanizedLine != null) {
                drawLyricAuxiliary(canvas, next, cachedRomanizedLine, line.romanizedWords,
                        left, width, extraBaseline, positionMs, alpha, owner.dp(8.5f),
                        Colors.withAlpha(Colors.ON_SURFACE_VARIANT, extraColorAlpha));
                extraBaseline += owner.dp(9.5f);
            }
            if (cachedTranslatedLine != null) {
                drawLyricAuxiliary(canvas, next, cachedTranslatedLine, Collections.emptyList(),
                        left, width, extraBaseline, positionMs, alpha, owner.dp(8.5f),
                        Colors.withAlpha(Colors.ON_SURFACE_VARIANT, extraColorAlpha));
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static String mediaWordsText(java.util.List<MediaLyrics.Word> words) {
        if (words == null || words.isEmpty()) return null;
        StringBuilder text = new StringBuilder();
        for (MediaLyrics.Word word : words) text.append(word.text);
        return blankToNull(text.toString());
    }

    private void drawLyricAuxiliary(Canvas canvas, TextPaint paint, String text,
            java.util.List<MediaLyrics.Word> words, float left, float width, float baseline,
            long positionMs, float alpha, float size, int color) {
        if (text == null || text.isEmpty()) return;
        float oldSize = paint.getTextSize();
        Paint.Align oldAlign = paint.getTextAlign();
        boolean oldBold = paint.isFakeBoldText();
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(false);
        String fitted = fitLyricText(text, paint, width);
        paint.setColor(color);
        canvas.drawText(fitted, left, baseline, paint);
        if (words != null && !words.isEmpty() && fitted.length() == text.length()) {
            float prefix = 0.0f;
            for (MediaLyrics.Word word : words) {
                if (positionMs >= word.endMs) {
                    prefix += paint.measureText(word.text);
                    continue;
                }
                if (positionMs >= word.startMs && positionMs < word.endMs) {
                    paint.setColor(Colors.withAlpha(Colors.WHITE, alpha));
                    canvas.drawText(word.text, left + prefix, baseline, paint);
                }
                break;
            }
        }
        paint.setTextSize(oldSize);
        paint.setTextAlign(oldAlign);
        paint.setFakeBoldText(oldBold);
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
            playerTitlePaint.setColor(Colors.withAlpha(Colors.ON_SURFACE, alpha));
            IslandView.drawLayout(c, title, textX, lineY);
            lineY += title.getHeight() + gap;
        }
        if (artist != null) {
            playerArtistPaint.setColor(Colors.withAlpha(Colors.ON_SURFACE_VARIANT, 0.55f * alpha));
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
        long position = Math.max(0L, fx.shownPosition(item, SystemClock.elapsedRealtime()));
        long second = position / 1000L;
        if (clockText == null || clockSecond != second) {
            clockSecond = second;
            clockText = IslandView.formatTimePadded(position);
        }
        long durationSeconds = item.mediaDuration > 0 ? item.mediaDuration / 1000L : -1L;
        if (durationText == null || durationSecond != durationSeconds) {
            durationSecond = durationSeconds;
            durationText = item.mediaDuration > 0
                    ? IslandView.formatTimePadded(item.mediaDuration) : NO_DURATION;
        }

        boolean dragged = owner.seekBody == this;
        boolean showElapsed = !prismSession || prismPlayer.showElapsedTime();
        boolean showRightTime = !prismSession || prismPlayer.showRightTime();
        String current = clockText;
        String right = durationText;
        if (prismSession && prismPlayer.showRemainingTime() && item.mediaDuration > 0) {
            right = prismPlayer.showTrackLength ? durationText
                    : "−" + IslandView.formatTimePadded(Math.max(0L, item.mediaDuration - position));
        }
        if (dragged && item.mediaDuration > 0) {
            long draggedPosition = (long) (item.mediaDuration * owner.seekFraction);
            current = IslandView.formatTimePadded(draggedPosition);
            if (prismSession && prismPlayer.showRemainingTime() && !prismPlayer.showTrackLength) {
                right = "−" + IslandView.formatTimePadded(Math.max(0L, item.mediaDuration - draggedPosition));
            }
        }

        owner.smallPaint.setColor(Colors.withAlpha(Colors.TEXT_SECONDARY, alpha));
        Paint.FontMetrics fm = owner.smallPaint.getFontMetrics();
        float baseline = centerY - ((fm.ascent + fm.descent) / 2.0f);
        if (showElapsed) c.drawText(current, x + pad, baseline, owner.smallPaint);
        if (showRightTime) {
            float rightWidth = owner.smallPaint.measureText(right);
            c.drawText(right, ((x + w) - pad) - rightWidth, baseline, owner.smallPaint);
        }

        float trackLeft = x + barLeft;
        float trackRight = x + barRight;
        float fraction = dragged ? IslandView.clamp01(owner.seekFraction)
                : (item.mediaDuration > 0
                ? IslandView.clamp01((float) position / (float) item.mediaDuration) : 0.0f);
        float scale = prismSession ? prismPlayer.scrubberThickness : 1.0f;
        float idleDp = 3.0f;
        if (prismSession) {
            switch (prismPlayer.scrubberStyle) {
                case "HAIRLINE": idleDp = 2.0f; break;
                case "BOLD": idleDp = 6.0f; break;
                case "THUMB": idleDp = 2.0f; break;
                case "WAVE":
                case "RIBBONS":
                default: idleDp = 3.0f; break; // Prism's waveform is unavailable until its scan export is bridged.
            }
        }
        float barHeight = owner.dp((dragged ? Math.max(idleDp * 1.45f, 4.5f) : idleDp) * scale);
        float radius = barHeight / 2.0f;
        owner.tmpRect.set(trackLeft, centerY - radius, trackRight, centerY + radius);
        int activeColor = prismSession ? prismScrubberColor() : Colors.SEEK_FILL;
        int trackColor = prismSession ? Colors.withAlpha(activeColor, 0.22f) : Colors.SEEK_TRACK;
        owner.fillPaint.setColor(Colors.withAlpha(trackColor, alpha));
        c.drawRoundRect(owner.tmpRect, radius, radius, owner.fillPaint);

        float trackWidth = Math.max(0.0f, trackRight - trackLeft);
        float playedWidth = trackWidth * fraction;
        owner.tmpRect.right = trackLeft + playedWidth;
        owner.fillPaint.setColor(Colors.withAlpha(activeColor, alpha));
        c.drawRoundRect(owner.tmpRect, radius, radius, owner.fillPaint);

        String thumb = prismSession ? prismPlayer.effectiveThumb() : "DOT";
        if (!"NONE".equals(thumb) && item.mediaDuration > 0 && trackWidth > 0.0f) {
            float head = trackLeft + playedWidth;
            float glowAlpha = ((fx.liveness(now) * ((((float) Math.sin(fx.seekPhase))
                    * IslandView.PAUSED_SPEED) + 0.05f)) + 0.8f) * alpha;
            glowPaint.setStyle(Paint.Style.FILL);
            if ("LINE".equals(thumb)) {
                float halfHeight = Math.max(owner.dp(7.0f), barHeight * 1.8f);
                glowPaint.setColor(Colors.withAlpha(Colors.WHITE, glowAlpha));
                glowRect.set(head - owner.dp(1.5f), centerY - halfHeight,
                        head + owner.dp(1.5f), centerY + halfHeight);
                c.drawRoundRect(glowRect, owner.dp(1.5f), owner.dp(1.5f), glowPaint);
            } else if ("RING".equals(thumb)) {
                float thumbRadius = owner.dp(4.5f);
                glowPaint.setColor(Colors.withAlpha(Colors.BLACK, alpha));
                c.drawCircle(head, centerY, thumbRadius, glowPaint);
                glowPaint.setStyle(Paint.Style.STROKE);
                glowPaint.setStrokeWidth(owner.dp(2.0f));
                glowPaint.setColor(Colors.withAlpha(Colors.WHITE, alpha));
                c.drawCircle(head, centerY, thumbRadius, glowPaint);
                glowPaint.setStyle(Paint.Style.FILL);
            } else {
                float thumbRadius = Math.max(owner.dp(3.0f), barHeight * 1.25f);
                glowPaint.setColor(Colors.withAlpha(Colors.WHITE, glowAlpha));
                c.drawCircle(head, centerY, thumbRadius, glowPaint);
            }
        }
    }

    private int prismScrubberColor() {
        switch (prismPlayer.scrubberColor) {
            case "WHITE":
                return Colors.WHITE;
            case "ACCENT":
            case "COVER":
                return item.accent != 0 && item.accent != -1
                        ? Colors.readableOnBlack(item.accent) : Colors.WHITE;
            case "SPECTRUM":
            default:
                // Coobvo currently has the artwork accent, not Prism's full animated spectrum shader.
                return Colors.WHITE;
        }
    }

    private float artCornerRadius() {
        if (!prismSession) return owner.dp(ART_CORNER_DP);
        if ("CIRCLE".equals(prismPlayer.coverShape)) return art / 2.0f;
        if ("SQUARE".equals(prismPlayer.coverShape)) return 0.0f;
        return Math.min(art / 2.0f, owner.dp(prismPlayer.artCornersDp));
    }
}
