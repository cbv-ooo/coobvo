package dev.android.dynamicisland.island.view.card;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import dev.android.dynamicisland.island.graphics.Colors;
import dev.android.dynamicisland.island.graphics.Glyphs;
import dev.android.dynamicisland.island.graphics.Fonts;
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
    private final RenderNode backdropRenderNode;
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
    private final CharSequence originalPlayerTitle;
    private final float playerTitleWidth;
    private final boolean prismShowProgress;
    private final boolean prismShowTransport;
    private final boolean prismShowVolume;
    private final boolean prismShowCover;
    private final boolean prismShowTitle;
    private final boolean prismShowArtist;
    private final boolean prismShowPreviousNext;
    private final boolean prismShowHandle;
    private final float prismVolumeCenterY;
    private final int prismSeekRow;
    private final int prismVolumeRow;
    private final int prismTransportRow;
    private final int prismLyricsRow;
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
        prismShowProgress = !prismSession
                || (prismPlayer.hasDeckPart("SCRUBBER") && prismPlayer.showsPart("PROGRESS"));
        prismShowTransport = !prismSession || prismPlayer.hasDeckPart("TRANSPORT");
        // Prism's expanded card uses the full island mesh; it has no separate cover tile or volume row.
        prismShowVolume = false;
        prismShowCover = !prismSession && prismPlayer.showsPart("COVER");
        prismShowTitle = !prismSession || prismPlayer.showsPart("TITLE");
        prismShowArtist = !prismSession || prismPlayer.showsPart("ARTIST");
        prismShowPreviousNext = !prismSession
                || (prismPlayer.showsPart("PREV_NEXT") && !"MINIMAL".equals(prismPlayer.layout));
        prismShowHandle = prismSession && prismPlayer.showsPart("HANDLE");
        playerTitlePaint = new TextPaint(owner.mediaTitlePaint());
        originalPlayerTitle = item.title;
        playerArtistPaint = new TextPaint(owner.mediaArtistPaint());
        if (prismSession) {
            playerTitlePaint.setTextSize(playerTitlePaint.getTextSize() * prismPlayer.titleScale());
            playerTitlePaint.setLetterSpacing(prismPlayer.typeSpacingEm);
            playerArtistPaint.setLetterSpacing(prismPlayer.typeSpacingEm);
            playerTitlePaint.setTypeface(Fonts.of(prismPlayer.titleFontWeight()));
            playerArtistPaint.setTypeface(Fonts.of(prismPlayer.artistFontWeight()));
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
        lyricsEnabled = prismSession
                ? prismPlayer.showsPart("LYRIC_LINE")
                : ModuleSettings.mediaLyricsEnabled();
        lyricsKey = !prismSession && lyricsEnabled ? LyricsManager.keyFor(lyricsTitle, lyricsArtist) : "";
        pad = owner.dp(18.0f);
        float coverDp = prismShowCover ? 56.0f : 0.0f;
        if (prismSession && prismShowCover) {
            // CoverSize.extra in Prism is an amount on each side of the standard 56dp cover.
            if ("LARGE".equals(prismPlayer.coverSize)) coverDp = 92.0f;
            boolean vinylOrCassette = "VINYL".equals(prismPlayer.layout)
                    || "CASSETTE".equals(prismPlayer.layout);
            if ("FULL".equals(prismPlayer.coverSize)
                    || (prismPlayer.fullBleedArtwork && !vinylOrCassette)) coverDp = 116.0f;
            if ("LYRICS".equals(prismPlayer.layout) || "MINIMAL".equals(prismPlayer.layout)) coverDp = 44.0f;
            if ("COVER".equals(prismPlayer.layout) || vinylOrCassette) coverDp = Math.max(coverDp, 92.0f);
        }
        art = owner.dp(coverDp);
        tile = owner.dp(32.0f);
        controlH = owner.dp(52.0f);
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backdropRenderNode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? new RenderNode("CoobvoPrismPlayerBackdrop") : null;
        glowRect = new RectF();
        int textWidth = Math.max(1, Math.round(prismSession
                ? w - pad * 2.0f
                : w - pad * 2.0f - art - owner.dp(ART_TEXT_GAP_DP) - tile - owner.dp(10.0f)));
        playerTitleWidth = textWidth;
        Layout.Alignment titleAlignment = prismSession
                ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL;
        title = !prismShowTitle || item.title == null ? null
                : IslandView.layout(item.title, playerTitlePaint, textWidth, 1, titleAlignment);
        CharSequence artistText = prismShowArtist ? item.subtitle : null;
        if (artistText != null && prismPlayer.showPlayingFrom
                && prismPlayer.showsPart("FROM_ALBUM")
                && item.mediaAlbum != null && item.mediaAlbum.length() > 0) {
            artistText = artistText + " · " + item.mediaAlbum;
        }
        artist = artistText != null
                ? IslandView.layout(artistText, playerArtistPaint, textWidth, 1, titleAlignment) : null;
        String outputText = null;
        if (!prismSession) {
            if (item.mediaFlags != null && item.mediaFlags.length() > 0) {
                outputText = item.mediaFlags.toString();
            }
        }
        if (!prismSession && item.mediaInfo != null && item.mediaInfo.length() > 0) {
            outputText = outputText == null ? item.mediaInfo.toString() : outputText + " · " + item.mediaInfo;
        }
        if (!prismSession && item.mediaOutput != null) {
            String device = item.mediaOutput.toString() + (item.mediaOutputBattery >= 0 ? " · " + item.mediaOutputBattery + "%" : "");
            outputText = outputText == null ? device : outputText + " · " + device;
        }
        if (prismSession && "MINIMAL".equals(prismPlayer.layout)) outputText = null;
        output = outputText == null ? null
                : IslandView.layout(outputText, owner.smallPaint, Math.max(1, textWidth - Math.round(owner.dp(18.0f))), 1);
        float headerHeight = (title == null ? 0.0f : title.getHeight())
                + (artist == null ? 0.0f : (title == null ? 0.0f : owner.dp(TEXT_LINE_GAP_DP)) + artist.getHeight())
                + (output == null ? 0.0f : owner.dp(TEXT_LINE_GAP_DP) + output.getHeight());
        float deckStart = pad + (prismSession
                ? Math.max(owner.dp(24.0f), headerHeight) + owner.dp(12.0f)
                : art + owner.dp(16.0f));
        if (prismSession) {
            float cursor = deckStart;
            float seekTopValue = -1.0f;
            float volumeCenterValue = -1.0f;
            float transportTopValue = -1.0f;
            float lyricsTopValue = -1.0f;
            int seekRowValue = -1;
            int volumeRowValue = -1;
            int transportRowValue = -1;
            int lyricsRowValue = -1;
            int rowIndex = 1;
            boolean placedRow = false;
            for (String part : prismPlayer.orderedDeckParts()) {
                float rowHeight = 0.0f;
                switch (part) {
                    case "STRIP":
                        if (lyricsEnabled) {
                            rowHeight = owner.dp(prismLyricsRowDp);
                            lyricsTopValue = cursor;
                            lyricsRowValue = rowIndex;
                        }
                        break;
                    case "SCRUBBER":
                        if (prismShowProgress) {
                            rowHeight = owner.dp(24.0f);
                            seekTopValue = cursor;
                            seekRowValue = rowIndex;
                        }
                        break;
                    case "TRANSPORT":
                        if (prismShowTransport) {
                            rowHeight = controlH;
                            transportTopValue = cursor;
                            transportRowValue = rowIndex;
                        }
                        break;
                    case "BUTTONS":
                        // This is Prism's separate PlayerActionRow, not MediaSession custom actions.
                        // It stays unallocated until those app-level callbacks are bridged.
                        break;
                    default:
                        break;
                }
                if (rowHeight <= 0.0f) continue;
                if (placedRow) cursor += owner.dp(5.0f);
                // The first row starts at deckStart; later rows get their own gap.
                if ("STRIP".equals(part) && lyricsEnabled) lyricsTopValue = cursor;
                else if ("SCRUBBER".equals(part) && prismShowProgress) seekTopValue = cursor;
                else if ("TRANSPORT".equals(part) && prismShowTransport) transportTopValue = cursor;
                cursor += rowHeight;
                placedRow = true;
                rowIndex++;
            }
            seekTop = seekTopValue;
            prismVolumeCenterY = volumeCenterValue;
            controlsTop = transportTopValue;
            lyricsTop = lyricsTopValue;
            prismSeekRow = seekRowValue;
            prismVolumeRow = volumeRowValue;
            prismTransportRow = transportRowValue;
            prismLyricsRow = lyricsRowValue;
            h = cursor + owner.dp(12.0f);
        } else {
            seekTop = deckStart;
            prismVolumeCenterY = -1.0f;
            float deckGap = owner.dp(24.0f);
            controlsTop = seekTop + deckGap + owner.dp(3.0f);
            lyricsTop = controlsTop + controlH + owner.dp(5.0f);
            prismSeekRow = -1;
            prismVolumeRow = -1;
            prismTransportRow = -1;
            prismLyricsRow = -1;
            h = lyricsTop + (lyricsEnabled ? owner.dp(LYRICS_ROW_DP) : 0.0f) + owner.dp(12.0f);
        }
        String totalText = item.mediaDuration > 0 ? IslandView.formatTimePadded(item.mediaDuration) : NO_DURATION;
        boolean showElapsed = !prismSession
                || (prismPlayer.showsTimes() && prismPlayer.showElapsedTime());
        boolean showRightTime = !prismSession
                || (prismPlayer.showsTimes() && prismPlayer.showRightTime());
        barLeft = pad + (showElapsed ? owner.smallPaint.measureText("00:00") + owner.dp(10.0f) : 0.0f);
        String rightWidthText = prismSession && prismPlayer.showRemainingTime() ? "−00:00" : totalText;
        barRight = ((w - pad) - (showRightTime
                ? owner.smallPaint.measureText(rightWidthText) + owner.dp(10.0f) : 0.0f));
        float span = w - (pad * 2.0f);
        if (prismShowTransport && controlsTop >= 0.0f) {
            // MediaSession custom actions (Prism's SessionButtons) flank transport;
            // they are not the app's separate PlayerButton row.
            boolean minimalLayout = prismSession && "MINIMAL".equals(prismPlayer.layout);
            if (!minimalLayout && item.mediaCustom.size() > 0) {
                addCustom(item.mediaCustom.get(0), pad + (CUSTOM_LEFT_AT * span), controlsTop);
            }
            if (prismShowPreviousNext) {
                addControl(Glyphs.PREV, pad + (PREV_AT * span), item.mediaPrev, false, controlsTop);
            }
            addControl(item.playing ? Glyphs.PAUSE : Glyphs.PLAY,
                    pad + (PLAY_AT * span), item.mediaPlayPause, true, controlsTop);
            if (prismShowPreviousNext) {
                addControl(Glyphs.NEXT, pad + (NEXT_AT * span), item.mediaNext, false, controlsTop);
            }
            if (!minimalLayout && item.mediaCustom.size() > 1) {
                addCustom(item.mediaCustom.get(1), pad + (CUSTOM_RIGHT_AT * span), controlsTop);
            }
        }
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
        if (seekTop < 0.0f) return false;
        float barCenterY = seekTop + owner.dp(7.0f);
        return item.mediaSeek != null && item.mediaDuration > 0
                && ly > barCenterY - owner.dp(18.0f)
                && ly < barCenterY + owner.dp(18.0f)
                && lx > barLeft - owner.dp(12.0f)
                && lx < barRight + owner.dp(12.0f);
    }

    /** Position on the seek bar (0..1) for a touch at {@code lx}. */
    public float fractionAt(float lx) {
        return IslandView.clamp01((lx - barLeft) / Math.max(1.0f, barRight - barLeft));
    }

    private void addControl(int glyph, float centerX, Runnable onTap, boolean circle, float rowTop) {
        Btn b = new Btn();
        b.glyph = glyph;
        boolean glyphOnly = prismSession && "GLYPHS".equals(prismPlayer.transportStyle);
        boolean outlinedPlay = prismSession && "OUTLINE".equals(prismPlayer.transportStyle)
                && (glyph == Glyphs.PLAY || glyph == Glyphs.PAUSE);
        b.circle = circle && !glyphOnly;
        b.outline = outlinedPlay;
        b.bg = outlinedPlay ? Colors.TRANSPARENT_MARK
                : (b.circle ? Colors.primaryContainer(item.accent) : Colors.CONTROL_FILL);
        b.run = onTap;
        float size = controlH;
        if (!circle) size *= SIDE_BUTTON_RATIO;
        b.r.set(centerX - (size / 2.0f), rowTop + ((controlH - size) / 2.0f),
                centerX + (size / 2.0f), rowTop + ((controlH + size) / 2.0f));
        buttons.add(b);
    }

    /** Android MediaSession custom action (e.g. Prism SessionButton), kept with transport controls. */
    private void addCustom(IslandItem.Action action, float centerX, float rowTop) {
        Btn btn = new Btn();
        btn.icon = action.icon;
        if (action.icon == null && action.title != null) btn.label = action.title.toString();
        if (prismSession && "SQUARE".equals(prismPlayer.buttonShape)) {
            btn.circle = false;
            btn.cornerRadiusDp = 0.0f;
            btn.bg = Colors.TRANSPARENT_MARK;
        } else if (prismSession && "ROUNDED".equals(prismPlayer.buttonShape)) {
            btn.circle = false;
            btn.cornerRadiusDp = 8.0f;
            btn.bg = Colors.TRANSPARENT_MARK;
        } else {
            btn.circle = true;
            btn.bg = Colors.TRANSPARENT_MARK;
        }
        btn.run = action.runnable;
        float size = controlH * SIDE_BUTTON_RATIO;
        btn.r.set(centerX - (size / 2.0f), rowTop + ((controlH - size) / 2.0f),
                centerX + (size / 2.0f), rowTop + ((controlH + size) / 2.0f));
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
        if (prismShowCover) drawArtwork(c, fx, x + pad, y + pad + topDy, topAlpha, now);
        if (prismShowHandle) drawPlayerHandle(c, x, y + topDy, topAlpha);
        drawVisualizer(c, x, y + pad + topDy, topAlpha);
        drawTexts(c, x, y, topDy, topAlpha);
        if (!prismSession) drawAppTile(c, x, y, topDy, topAlpha);

        if (!prismSession) {
            float seekRowProgress = rowAlpha(elapsed, 1);
            if (prismShowProgress) {
                drawSeekRow(c, x, y + seekTop + ((1.0f - seekRowProgress) * owner.dp(ROW_SLIDE_DP))
                        + owner.dp(7.0f), alpha * seekRowProgress, fx, now);
            }
            float controlsRowProgress = rowAlpha(elapsed, 2);
            drawButtons(c, x, y + ((1.0f - controlsRowProgress) * owner.dp(ROW_SLIDE_DP)),
                    alpha * controlsRowProgress);
            if (lyricsEnabled) {
                drawLyrics(c, x + pad, y + lyricsTop, w - pad * 2,
                        alpha * controlsRowProgress,
                        fx.shownPosition(item, SystemClock.elapsedRealtime()));
            }
            return;
        }

        if (prismSeekRow >= 0) {
            float rowAlpha = rowAlpha(elapsed, prismSeekRow);
            drawSeekRow(c, x, y + seekTop + ((1.0f - rowAlpha) * owner.dp(ROW_SLIDE_DP))
                    + owner.dp(7.0f), alpha * rowAlpha, fx, now);
        }
        if (prismTransportRow >= 0) {
            float rowAlpha = rowAlpha(elapsed, prismTransportRow);
            drawButtons(c, x, y + ((1.0f - rowAlpha) * owner.dp(ROW_SLIDE_DP)),
                    alpha * rowAlpha);
        }
        if (lyricsEnabled && prismLyricsRow >= 0) {
            float rowAlpha = rowAlpha(elapsed, prismLyricsRow);
            drawLyrics(c, x + pad, y + lyricsTop + ((1.0f - rowAlpha) * owner.dp(ROW_SLIDE_DP)),
                    w - pad * 2, alpha * rowAlpha,
                    fx.shownPosition(item, SystemClock.elapsedRealtime()));
        }
    }

    /** Current and next line of synced lyrics, using the playback clock shared with the seek row. */
    private void drawLyrics(Canvas canvas, float left, float top, float width, float alpha, long positionMs) {
        if (prismSession) {
            PrismLyricsPainter.draw(canvas, owner, prismLyrics, prismLook, prismLyricState, lyricsCurrentPaint, lyricsNextPaint,
                    prismLyricsStatus, left, top, width, alpha, positionMs, item.accent,
                    owner.mediaFx(item), SystemClock.elapsedRealtime());
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
        if (prismSession) {
            // The original Dynamic Island mesh remains the full-card surface. MediaFx blends
            // its previous and incoming artwork palettes as Prism changes tracks.
            float visible = alpha * (0.35f + 0.65f * prismPlayer.backdropShow);
            float intensity = Math.min(1.0f, Math.max(0.0f, prismPlayer.meshIntensity));
            fx.drawGlow(c, 0.0f, 0.0f, w, h, visible * intensity, now, prismPlayer.meshSpeed);
        } else {
            fx.drawGlow(c, 0.0f, 0.0f, w, h, alpha, now, 1.0f);
        }
        if (prismSession && "GLOW".equals(prismPlayer.visualizer)
                && item.mediaVisualizerLevel > 0.0f && item.accent != 0 && item.accent != -1) {
            owner.tmpRect.set(0.0f, 0.0f, w, h);
            owner.fillPaint.setColor(Colors.withAlpha(item.accent,
                    alpha * Math.min(0.18f, item.mediaVisualizerLevel * 0.18f)));
            c.drawRoundRect(owner.tmpRect, owner.dp(24.0f), owner.dp(24.0f), owner.fillPaint);
        }
        c.restoreToCount(save);
    }

    private void drawTexts(Canvas c, float x, float y, float dy, float alpha) {
        float textX = x + pad + (prismSession ? 0.0f : art + owner.dp(ART_TEXT_GAP_DP));
        float gap = owner.dp(TEXT_LINE_GAP_DP);
        float textHeight = (title != null ? title.getHeight() : 0)
                + (artist != null ? gap + artist.getHeight() : 0.0f)
                + (output != null ? gap + output.getHeight() : 0.0f);
        float textSlotHeight = Math.max(art, textHeight);
        float lineY = y + pad + dy + ((textSlotHeight - textHeight) / 2.0f);
        if (title != null) {
            playerTitlePaint.setColor(Colors.withAlpha(
                prismSession && "WHITE".equals(prismPlayer.background)
                        ? Colors.BLACK : Colors.ON_SURFACE, alpha));
            drawPlayerTitle(c, textX, lineY, alpha);
            lineY += title.getHeight() + gap;
        }
        if (artist != null) {
            playerArtistPaint.setColor(Colors.withAlpha(
                    prismSession && "WHITE".equals(prismPlayer.background)
                            ? Colors.BLACK : Colors.ON_SURFACE_VARIANT,
                    (prismSession && "WHITE".equals(prismPlayer.background) ? 0.72f : 0.55f) * alpha));
            IslandView.drawLayout(c, artist, textX, lineY);
            lineY += artist.getHeight() + gap;
        }
        if (output != null) {
            owner.smallPaint.setColor(Colors.withAlpha(
                    prismSession && "WHITE".equals(prismPlayer.background)
                            ? Colors.BLACK : Colors.TEXT_SECONDARY, alpha));
            float outputIconSize = owner.dp(13.0f);
            if (item.mediaOutputIcon != null) {
                owner.drawDrawable(c, item.mediaOutputIcon, false, Colors.withAlpha(Colors.WHITE, alpha), textX, lineY + ((output.getHeight() - outputIconSize) / 2.0f), outputIconSize, alpha, 0.0f);
            }
            IslandView.drawLayout(c, output, textX + owner.dp(18.0f), lineY);
        }
    }

    /** Small tile at the top right showing the player app (a glyph on a dark tile, or the app icon). */
    private void drawAppTile(Canvas c, float x, float y, float dy, float alpha) {
        if (prismSession || (item.appGlyph == null && item.appIcon == null)) {
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

        if (!prismSession) {
            drawLegacySeekRow(c, x, centerY, alpha, fx, now, position, clockText, durationText);
            return;
        }

        boolean dragged = owner.seekBody == this;
        boolean showElapsed = prismPlayer.showsTimes() && prismPlayer.showElapsedTime();
        boolean showRightTime = prismPlayer.showsTimes() && prismPlayer.showRightTime();
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
        boolean waveStyle = "WAVE".equals(prismPlayer.scrubberStyle);
        float[] songWaveform = waveStyle ? item.mediaSongWaveform : null;
        boolean hasSongWaveform = songWaveform != null && songWaveform.length == 100;
        float scale = prismSession ? prismPlayer.scrubberThickness : 1.0f;
        float idleDp = 3.0f;
        if (prismSession) {
            switch (prismPlayer.scrubberStyle) {
                case "HAIRLINE": idleDp = 2.0f; break;
                case "BOLD": idleDp = 6.0f; break;
                case "THUMB": idleDp = 2.0f; break;
                case "WAVE": idleDp = hasSongWaveform ? 14.0f : 3.0f; scale = 1.0f; break;
                case "RIBBONS":
                default: idleDp = 3.0f; break;
            }
        }
        float barHeight = owner.dp((dragged ? Math.max(idleDp * 1.45f, 4.5f) : idleDp) * scale);
        float radius = barHeight / 2.0f;
        int activeColor = prismSession ? prismScrubberColor() : Colors.SEEK_FILL;
        float trackWidth = Math.max(0.0f, trackRight - trackLeft);
        float playedWidth = trackWidth * fraction;
        if (hasSongWaveform) {
            // Matches Prism's WAVE style: 100 scan-derived bars, played portion bright and remainder subdued.
            float step = trackWidth / songWaveform.length;
            float barWidth = Math.max(owner.dp(1.0f), step * 0.6f);
            for (int i = 0; i < songWaveform.length; i++) {
                float value = Math.max(0.0f, Math.min(1.0f, songWaveform[i]));
                float height = barHeight * (0.18f + 0.82f * value);
                float left = trackLeft + (step * i) + ((step - barWidth) / 2.0f);
                float top = centerY - (height / 2.0f);
                owner.tmpRect.set(left, top, left + barWidth, top + height);
                boolean played = (step * i) + (step / 2.0f) <= playedWidth;
                owner.fillPaint.setColor(Colors.withAlpha(
                        played ? activeColor : Colors.withAlpha(Colors.WHITE, 0.22f), alpha));
                c.drawRoundRect(owner.tmpRect, barWidth / 2.0f, barWidth / 2.0f, owner.fillPaint);
            }
        } else {
            owner.tmpRect.set(trackLeft, centerY - radius, trackRight, centerY + radius);
            int trackColor = prismSession ? Colors.withAlpha(activeColor, 0.22f) : Colors.SEEK_TRACK;
            owner.fillPaint.setColor(Colors.withAlpha(trackColor, alpha));
            c.drawRoundRect(owner.tmpRect, radius, radius, owner.fillPaint);
            owner.tmpRect.right = trackLeft + playedWidth;
            owner.fillPaint.setColor(Colors.withAlpha(activeColor, alpha));
            c.drawRoundRect(owner.tmpRect, radius, radius, owner.fillPaint);
        }

        if (prismSession && item.mediaDuration > 0 && trackWidth > 0.0f
                && item.mediaCrossfadeStartMs >= 0L && item.mediaCrossfadeLengthMs > 0L
                && prismPlayer.showsPart("CROSSFADE_MARK")) {
            float crossfadeStart = IslandView.clamp01(
                    (float) item.mediaCrossfadeStartMs / (float) item.mediaDuration);
            float crossfadeEnd = IslandView.clamp01(
                    (float) (item.mediaCrossfadeStartMs + item.mediaCrossfadeLengthMs) / (float) item.mediaDuration);
            float markX = trackLeft + trackWidth * crossfadeStart;
            float progress = IslandView.clamp01(item.mediaCrossfadeProgress);
            float blendX = trackLeft + trackWidth
                    * crossfadeProgressFraction(item.mediaCrossfadeStartMs,
                            item.mediaCrossfadeLengthMs, progress, item.mediaDuration);
            if (prismPlayer.crossfadeShape && crossfadeEnd > crossfadeStart) {
                float halfHeight = Math.max(owner.dp(4.0f), radius * 2.0f);
                int nextColor = item.accent != 0 && item.accent != -1 ? item.accent : Colors.WHITE;
                glowPaint.setStyle(Paint.Style.FILL);
                glowRect.set(markX, centerY - halfHeight,
                        trackLeft + trackWidth * crossfadeEnd, centerY + halfHeight);
                glowPaint.setShader(new LinearGradient(glowRect.left, centerY, glowRect.right, centerY,
                        new int[]{Colors.withAlpha(activeColor, alpha * 0.12f),
                                Colors.withAlpha(nextColor, alpha * 0.36f),
                                Colors.withAlpha(activeColor, alpha * 0.12f)},
                        null, Shader.TileMode.CLAMP));
                c.drawRoundRect(glowRect, radius, radius, glowPaint);
                glowPaint.setShader(null);
                // Fill only the completed part of the crossfade window; the rest remains a quiet preview.
                if (progress > 0.0f && blendX > markX) {
                    glowRect.right = Math.min(glowRect.right, blendX);
                    glowPaint.setColor(Colors.withAlpha(nextColor, alpha * 0.34f));
                    c.drawRoundRect(glowRect, radius, radius, glowPaint);
                }
            }
            if (prismPlayer.crossfadeMark) {
                glowPaint.setStyle(Paint.Style.STROKE);
                glowPaint.setStrokeWidth(owner.dp(1.5f));
                glowPaint.setColor(Colors.withAlpha(Colors.WHITE, alpha * 0.88f));
                c.drawLine(markX, centerY - owner.dp(5.0f), markX, centerY + owner.dp(5.0f), glowPaint);
                glowPaint.setStyle(Paint.Style.FILL);
            }
            if (progress > 0.0f && progress < 1.0f && blendX > markX) {
                glowPaint.setStyle(Paint.Style.STROKE);
                glowPaint.setStrokeWidth(owner.dp(2.0f));
                glowPaint.setColor(Colors.withAlpha(nextMarkerColor(item), alpha));
                c.drawLine(blendX, centerY - owner.dp(7.0f), blendX, centerY + owner.dp(7.0f), glowPaint);
                glowPaint.setStyle(Paint.Style.FILL);
            }
        }

        if (prismSession && prismPlayer.showsPart("BEST_PART")
                && item.mediaDuration > 0 && trackWidth > 0.0f
                && item.mediaBestPartStartMs > 0L && item.mediaBestPartStartMs < item.mediaDuration) {
            float bestFraction = IslandView.clamp01(
                    (float) item.mediaBestPartStartMs / (float) item.mediaDuration);
            float bestX = trackLeft + trackWidth * bestFraction;
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(owner.dp(2.0f));
            glowPaint.setColor(Colors.withAlpha(0xFFFFC65C, alpha * 0.96f));
            c.drawLine(bestX, centerY - owner.dp(7.0f), bestX, centerY + owner.dp(7.0f), glowPaint);
            glowPaint.setStyle(Paint.Style.FILL);
        }

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

    /** Current point within Prism's actual crossfade window (not an estimate from track duration). */
    static float crossfadeProgressFraction(long startMs, long lengthMs, float progress, long durationMs) {
        if (startMs < 0L || lengthMs <= 0L || durationMs <= 0L) return 0.0f;
        float start = IslandView.clamp01((float) startMs / (float) durationMs);
        float end = IslandView.clamp01((float) (startMs + lengthMs) / (float) durationMs);
        return start + (end - start) * IslandView.clamp01(progress);
    }

    private static int nextMarkerColor(IslandItem item) {
        return item.accent != 0 && item.accent != -1 ? item.accent : Colors.WHITE;
    }

    /** Keep every non-Prism player on its original island seek-bar appearance. */
    private void drawLegacySeekRow(Canvas c, float x, float centerY, float alpha, MediaFx fx, long now,
                                   long position, String current, String total) {
        owner.smallPaint.setColor(Colors.withAlpha(Colors.TEXT_SECONDARY, alpha));
        Paint.FontMetrics fm = owner.smallPaint.getFontMetrics();
        float baseline = centerY - ((fm.ascent + fm.descent) / 2.0f);
        boolean dragged = owner.seekBody == this;
        if (!dragged) c.drawText(current, x + pad, baseline, owner.smallPaint);
        float totalWidth = owner.smallPaint.measureText(total);
        c.drawText(total, ((x + w) - pad) - totalWidth, baseline, owner.smallPaint);
        float trackLeft = x + barLeft;
        float trackRight = x + barRight;
        if (dragged) {
            String draggedTime = IslandView.formatTimePadded(
                    (long) (Math.max(0L, item.mediaDuration) * owner.seekFraction));
            c.drawText(draggedTime, x + pad, baseline, owner.smallPaint);
        }
        float barHeight = owner.dp(dragged ? 7.0f : 3.5f);
        owner.tmpRect.set(trackLeft, centerY - (barHeight / 2.0f),
                trackRight, (barHeight / 2.0f) + centerY);
        owner.fillPaint.setColor(Colors.withAlpha(Colors.SEEK_TRACK, alpha));
        c.drawRoundRect(owner.tmpRect, barHeight / 2.0f, barHeight / 2.0f, owner.fillPaint);
        float fraction = 0.0f;
        if (dragged) fraction = owner.seekFraction;
        else if (item.mediaDuration > 0) fraction = IslandView.clamp01((float) position / (float) item.mediaDuration);
        owner.tmpRect.right = Math.max(2.4f * barHeight, (trackRight - trackLeft) * fraction) + trackLeft;
        float head = owner.tmpRect.right;
        float glowAlpha = ((fx.liveness(now) * ((((float) Math.sin(fx.seekPhase))
                * IslandView.PAUSED_SPEED) + 0.05f)) + 0.8f) * alpha;
        glowPaint.setColor(Colors.withAlpha(Colors.WHITE, 0.45f * glowAlpha));
        glowRect.set(Math.max(trackLeft, head - owner.dp(70.0f)), centerY - (barHeight / 2.0f),
                head, centerY + (barHeight / 2.0f));
        c.drawRoundRect(glowRect, barHeight / 2.0f, barHeight / 2.0f, glowPaint);
        glowPaint.setColor(Colors.withAlpha(Colors.WHITE, glowAlpha * 0.9f));
        c.drawCircle(head - (barHeight * 0.6f), centerY, 0.95f * barHeight, glowPaint);
        owner.fillPaint.setColor(Colors.withAlpha(Colors.SEEK_FILL, alpha));
        c.drawRoundRect(owner.tmpRect, barHeight / 2.0f, barHeight / 2.0f, owner.fillPaint);
    }

    /** Scrolls a long Prism title through the available one-line viewport when that preference is enabled. */
    private void drawPlayerTitle(Canvas canvas, float x, float y, float alpha) {
        if (!prismSession || !prismPlayer.titleScroll || originalPlayerTitle == null) {
            IslandView.drawLayout(canvas, title, x, y);
            return;
        }
        String text = originalPlayerTitle.toString();
        float measured = playerTitlePaint.measureText(text);
        if (measured <= playerTitleWidth) {
            IslandView.drawLayout(canvas, title, x, y);
            return;
        }
        float overflow = measured - playerTitleWidth;
        long travelMs = Math.max(1400L, (long) (overflow * 26.0f));
        long pauseMs = 850L;
        long cycle = (travelMs * 2L) + (pauseMs * 2L);
        long phase = SystemClock.uptimeMillis() % cycle;
        float offset;
        if (phase < pauseMs) offset = 0.0f;
        else if (phase < pauseMs + travelMs) {
            offset = overflow * (phase - pauseMs) / (float) travelMs;
        } else if (phase < pauseMs + travelMs + pauseMs) offset = overflow;
        else offset = overflow * (1.0f - (phase - pauseMs - travelMs - pauseMs) / (float) travelMs);
        int save = canvas.save();
        canvas.clipRect(x, y, x + playerTitleWidth, y + title.getHeight());
        playerTitlePaint.setColor(Colors.withAlpha(
                prismSession && "WHITE".equals(prismPlayer.background)
                        ? Colors.BLACK : Colors.ON_SURFACE, alpha));
        canvas.drawText(text, x - offset, y - playerTitlePaint.getFontMetrics().ascent, playerTitlePaint);
        canvas.restoreToCount(save);
        owner.postInvalidateOnAnimation();
    }

    /** Prism's optional volume deck row; it mirrors the progress-bar weight/color choice. */
    private void drawVolumeRow(Canvas canvas, float x, float centerY, float alpha) {
        float left = x + pad;
        float right = x + w - pad;
        if (right <= left) return;
        float thickness = owner.dp(prismPlayer.volumeMatchesProgress
                ? ("BOLD".equals(prismPlayer.scrubberStyle) ? 4.0f : 2.5f) : 2.0f);
        float radius = thickness / 2.0f;
        owner.tmpRect.set(left, centerY - radius, right, centerY + radius);
        owner.fillPaint.setColor(Colors.withAlpha(Colors.WHITE, alpha * 0.18f));
        canvas.drawRoundRect(owner.tmpRect, radius, radius, owner.fillPaint);
        owner.tmpRect.right = left + (right - left) * prismPlayer.volumeLevel(item.mediaPlayerVolume);
        int color = prismPlayer.volumeMatchesProgress ? prismScrubberColor()
                : (item.accent != 0 && item.accent != -1 ? item.accent : Colors.WHITE);
        owner.fillPaint.setColor(Colors.withAlpha(color, alpha));
        canvas.drawRoundRect(owner.tmpRect, radius, radius, owner.fillPaint);
    }

    /** Prism's own sampled spectrum is drawn subtly behind the top player row. */
    private void drawVisualizer(Canvas canvas, float x, float top, float alpha) {
        if (!prismSession || art <= 0.0f || "OFF".equals(prismPlayer.visualizer)
                || item.mediaVisualizerBands == null || item.mediaVisualizerBands.length == 0) return;
        float start = x + pad + art + owner.dp(ART_TEXT_GAP_DP);
        float end = x + w - pad - tile - owner.dp(8.0f);
        float available = end - start;
        if (available <= owner.dp(24.0f)) return;
        float center = top + art - owner.dp(3.0f);
        int count = Math.min(28, item.mediaVisualizerBands.length);
        float step = available / count;
        float barWidth = Math.max(owner.dp(1.0f), step * 0.45f);
        int color = "ACCENT".equals(prismPlayer.visualizerColor)
                || "COVER".equals(prismPlayer.visualizerColor)
                ? (item.accent != 0 && item.accent != -1 ? item.accent : Colors.WHITE)
                : Colors.WHITE;
        String mode = prismPlayer.visualizer;
        if ("RING".equals(mode)) {
            float cx = x + pad + art / 2.0f;
            float cy = top + art / 2.0f;
            float baseRadius = art * 0.54f;
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(owner.dp(1.5f));
            glowPaint.setColor(Colors.withAlpha(color, alpha * 0.62f));
            for (int i = 0; i < count; i++) {
                float angle = (float) ((Math.PI * 2.0d * i / count) - (Math.PI / 2.0d));
                float value = Math.max(0.0f, Math.min(1.0f, item.mediaVisualizerBands[i]));
                float inner = baseRadius;
                float outer = inner + owner.dp(2.0f + value * 6.0f);
                canvas.drawLine(cx + (float) Math.cos(angle) * inner,
                        cy + (float) Math.sin(angle) * inner,
                        cx + (float) Math.cos(angle) * outer,
                        cy + (float) Math.sin(angle) * outer, glowPaint);
            }
            glowPaint.setStyle(Paint.Style.FILL);
            return;
        }
        if ("WAVE".equals(mode)) {
            Path wave = new Path();
            for (int i = 0; i < count; i++) {
                float value = Math.max(0.0f, Math.min(1.0f, item.mediaVisualizerBands[i]));
                float px = start + step * i;
                float py = center - owner.dp(2.0f + value * 7.0f);
                if (i == 0) wave.moveTo(px, py); else wave.lineTo(px, py);
            }
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(owner.dp(1.6f));
            glowPaint.setStrokeCap(Paint.Cap.ROUND);
            glowPaint.setColor(Colors.withAlpha(color, alpha * 0.42f));
            canvas.drawPath(wave, glowPaint);
            glowPaint.setStyle(Paint.Style.FILL);
            return;
        }
        for (int i = 0; i < count; i++) {
            float value = Math.max(0.0f, Math.min(1.0f, item.mediaVisualizerBands[i]));
            if ("PULSE".equals(mode)) value = Math.max(value, item.mediaVisualizerLevel);
            float height = owner.dp(2.0f) + owner.dp(7.0f) * value;
            float barLeft = start + step * i + (step - barWidth) / 2.0f;
            glowRect.set(barLeft, center - height, barLeft + barWidth, center);
            glowPaint.setStyle(Paint.Style.FILL);
            glowPaint.setColor(Colors.withAlpha(color, alpha * 0.24f));
            canvas.drawRoundRect(glowRect, barWidth / 2.0f, barWidth / 2.0f, glowPaint);
        }
    }

    private void drawPlayerHandle(Canvas canvas, float x, float y, float alpha) {
        float width = owner.dp(28.0f);
        float height = owner.dp(3.0f);
        float left = x + (w - width) * 0.5f;
        owner.tmpRect.set(left, y + owner.dp(2.0f), left + width, y + owner.dp(2.0f) + height);
        owner.fillPaint.setColor(Colors.withAlpha(Colors.WHITE, alpha * 0.46f));
        canvas.drawRoundRect(owner.tmpRect, height / 2.0f, height / 2.0f, owner.fillPaint);
    }

    /** Crop a square media-session cover into the expanded card's rectangular backdrop. */
    private void drawCoverFill(Canvas canvas, MediaFx fx, float alpha, long now) {
        float size = Math.max(w, h);
        float left = (w - size) * 0.5f;
        float top = (h - size) * 0.5f;
        fx.drawArt(canvas, left, top, size, alpha, 0.0f, now);
    }

    /** Uses the current artwork as a dimmed, softly blurred backing for Prism's BLUR mode. */
    private void drawBlurredArtworkBackground(Canvas canvas, MediaFx fx, float alpha, long now) {
        float visible = alpha * prismPlayer.backdropShow;
        if (visible <= 0.001f) return;
        boolean blurred = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && backdropRenderNode != null;
        if (blurred) {
            int widthPx = Math.max(1, Math.round(w));
            int heightPx = Math.max(1, Math.round(h));
            backdropRenderNode.setPosition(0, 0, widthPx, heightPx);
            Canvas recording = backdropRenderNode.beginRecording(widthPx, heightPx);
            drawCoverFill(recording, fx, visible, now);
            backdropRenderNode.endRecording();
            float radius = owner.dp(18.0f * prismPlayer.playerBlur);
            backdropRenderNode.setRenderEffect(RenderEffect.createBlurEffect(
                    radius, radius, Shader.TileMode.CLAMP));
            canvas.drawRenderNode(backdropRenderNode);
            backdropRenderNode.setRenderEffect(null);
        } else {
            // Pre-Android 12 fallback: still use the cover as the backing, but do not allocate
            // an expensive software blur in the System UI process.
            drawCoverFill(canvas, fx, visible * 0.32f, now);
        }
        owner.tmpRect.set(0.0f, 0.0f, w, h);
        owner.fillPaint.setColor(Colors.withAlpha(Colors.BLACK, alpha * prismPlayer.backdropDim));
        canvas.drawRect(owner.tmpRect, owner.fillPaint);
    }

    /** Draws the album artwork using Prism's selected size, pause shrink and cover silhouette. */
    private void drawArtwork(Canvas canvas, MediaFx fx, float left, float top, float alpha, long now) {
        float scale = prismSession && !item.playing && prismPlayer.artShrinkOnPause ? 0.92f : 1.0f;
        float size = art * scale;
        float x = left + (art - size) * 0.5f;
        float y = top + (art - size) * 0.5f;
        String shape = prismSession
                ? ("VINYL".equals(prismPlayer.layout) ? "CIRCLE" : prismPlayer.coverShape)
                : "ROUNDED";
        if (prismSession && ("SQUIRCLE".equals(shape) || "ARCH".equals(shape)
                || "SLANTED".equals(shape) || "FLOWER".equals(shape) || "COOKIE".equals(shape))) {
            int save = canvas.save();
            Path path = coverShapePath(left, top, art, shape);
            canvas.clipPath(path);
            fx.drawArt(canvas, x, y, size, alpha, 0.0f, now);
            canvas.restoreToCount(save);
        } else {
            fx.drawArt(canvas, x, y, size, alpha, artCornerRadius(), now);
        }
        if (prismSession && "VINYL".equals(prismPlayer.layout) && prismPlayer.vinylTonearm) {
            drawVinylTonearm(canvas, left, top, size, alpha, now);
        } else if (prismSession && "CASSETTE".equals(prismPlayer.layout)) {
            drawCassetteDetails(canvas, left, top, size, alpha);
        }
    }

    private Path coverShapePath(float left, float top, float size, String shape) {
        Path path = new Path();
        float right = left + size;
        float bottom = top + size;
        if ("ARCH".equals(shape)) {
            path.moveTo(left, bottom);
            path.lineTo(left, top + size * 0.42f);
            path.quadTo(left + size * 0.5f, top - size * 0.12f,
                    right, top + size * 0.42f);
            path.lineTo(right, bottom);
            path.close();
        } else if ("SLANTED".equals(shape)) {
            float cut = size * 0.18f;
            path.moveTo(left + cut, top);
            path.lineTo(right, top);
            path.lineTo(right - cut, bottom);
            path.lineTo(left, bottom);
            path.close();
        } else {
            int points = "FLOWER".equals(shape) ? 12 : 16;
            float cx = left + size * 0.5f;
            float cy = top + size * 0.5f;
            float base = size * 0.5f;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0d * i / points) - Math.PI / 2.0d;
                float modulation = "FLOWER".equals(shape)
                        ? (i % 2 == 0 ? 1.0f : 0.82f)
                        : (i % 4 < 2 ? 1.0f : 0.94f);
                float px = cx + (float) Math.cos(angle) * base * modulation;
                float py = cy + (float) Math.sin(angle) * base * modulation;
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            path.close();
        }
        return path;
    }

    private void drawCassetteDetails(Canvas canvas, float left, float top, float size, float alpha) {
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(owner.dp(1.2f));
        glowPaint.setColor(Colors.withAlpha(Colors.WHITE, alpha * 0.62f));
        owner.tmpRect.set(left + size * 0.08f, top + size * 0.08f,
                left + size * 0.92f, top + size * 0.92f);
        canvas.drawRoundRect(owner.tmpRect, owner.dp(7.0f), owner.dp(7.0f), glowPaint);
        owner.tmpRect.set(left + size * 0.17f, top + size * 0.15f,
                left + size * 0.83f, top + size * 0.38f);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(Colors.withAlpha(Colors.BLACK, alpha * 0.34f));
        canvas.drawRoundRect(owner.tmpRect, owner.dp(3.0f), owner.dp(3.0f), glowPaint);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setColor(Colors.withAlpha(Colors.WHITE, alpha * 0.70f));
        float cy = top + size * 0.67f;
        float radius = size * 0.14f;
        float leftCx = left + size * 0.34f;
        float rightCx = left + size * 0.66f;
        canvas.drawCircle(leftCx, cy, radius, glowPaint);
        canvas.drawCircle(rightCx, cy, radius, glowPaint);
        canvas.drawCircle(leftCx, cy, radius * 0.36f, glowPaint);
        canvas.drawCircle(rightCx, cy, radius * 0.36f, glowPaint);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(Colors.withAlpha(Colors.WHITE, alpha * 0.58f));
        canvas.drawCircle(left + size * 0.5f, top + size * 0.50f, owner.dp(2.0f), glowPaint);
    }

    /** Small record groove/ring and a moving tonearm accent for the VINYL layout. */
    private void drawVinylTonearm(Canvas canvas, float left, float top, float size,
            float alpha, long now) {
        float cx = left + size * 0.5f;
        float cy = top + size * 0.5f;
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(owner.dp(0.8f));
        glowPaint.setColor(Colors.withAlpha(Colors.WHITE, alpha * 0.28f));
        canvas.drawCircle(cx, cy, size * 0.38f, glowPaint);
        canvas.drawCircle(cx, cy, size * 0.31f, glowPaint);
        float progress = item.mediaDuration > 0
                ? IslandView.clamp01((float) owner.mediaFx(item)
                        .shownPosition(item, SystemClock.elapsedRealtime()) / item.mediaDuration) : 0.0f;
        float angle = (float) (Math.PI * (0.15d + progress * 0.22d));
        float armStartX = left + size * 0.88f;
        float armStartY = top + size * 0.14f;
        float armEndX = cx + (float) Math.cos(angle) * size * 0.30f;
        float armEndY = cy + (float) Math.sin(angle) * size * 0.30f;
        glowPaint.setStrokeWidth(owner.dp(1.5f));
        glowPaint.setColor(Colors.withAlpha(Colors.WHITE, alpha * 0.82f));
        canvas.drawLine(armStartX, armStartY, armEndX, armEndY, glowPaint);
        glowPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(armStartX, armStartY, owner.dp(2.2f), glowPaint);
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
        if ("SQUARE".equals(prismPlayer.coverShape)) return 0.0f;
        if ("CIRCLE".equals(prismPlayer.coverShape) || "VINYL".equals(prismPlayer.layout)) return art / 2.0f;
        return Math.min(art / 2.0f, owner.dp(prismPlayer.artCornersDp));
    }
}
