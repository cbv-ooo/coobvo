package dev.android.dynamicisland.island.view.card;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Validated view of Prism's player and lyrics-page settings exported by Morphe.
 * Raw values remain available in the bridge snapshot; this class maps the settings that have a
 * corresponding control or layout decision in Dynamic Island.
 */
final class PrismPlayerPreferences {
    static final String TIME_BOTH = "BOTH";
    static final String TIME_ELAPSED = "ELAPSED";
    static final String TIME_LEFT = "LEFT";
    static final String TIME_NONE = "NONE";

    final String playerTimes;
    final boolean showTrackLength;
    final String scrubberStyle;
    final String scrubberColor;
    final String scrubberThumb;
    final float scrubberThickness;
    final String titleAlign;
    final String titleSize;
    final String titleWeight;
    final String appTypeWeight;
    final float typeSpacingEm;
    final boolean titleScroll;
    final int artCornersDp;
    final String coverSize;
    final String coverShape;
    final String layout;
    final String background;
    final float backdropShow;
    final String transportStyle;
    final String buttonShape;
    final boolean buttonsPill;
    final List<String> deckParts;
    final List<String> playerButtons;
    final List<String> hiddenParts;
    final JSONObject source;
    final boolean lyricsOnPlayer;
    final boolean volumeMatchesProgress;
    final boolean hideVolumeBar;
    final String volumeControl;
    final boolean fullBleedArtwork;
    final boolean canvasEnabled;
    final boolean canvasFillsBackground;
    final boolean canvasOnCellular;
    final boolean artShrinkOnPause;
    final boolean vinylTonearm;
    final float playerBlur;
    final float meshSpeed;
    final float meshIntensity;
    final float backdropDim;
    final float gradientDepth;
    final boolean blendCovers;
    final boolean showVideoSwitch;
    final boolean hideStatusBarPlayer;
    final boolean playerHandle;
    final boolean playerNavBand;
    final List<String> sessionButtons;
    final boolean showPlayingFrom;
    final boolean showOutputDevice;
    final boolean showQuality;
    final boolean crossfadeMark;
    final boolean crossfadeShape;
    final String visualizer;
    final String visualizerColor;
    final float appVolume;

    private PrismPlayerPreferences(JSONObject player) {
        source = player;
        playerTimes = choice(player, "playerTimes", TIME_BOTH,
                TIME_BOTH, TIME_ELAPSED, TIME_LEFT, TIME_NONE);
        showTrackLength = player.optBoolean("showTrackLength", false);
        scrubberStyle = choice(player, "scrubberStyle", "HAIRLINE",
                "HAIRLINE", "BOLD", "THUMB", "WAVE", "RIBBONS");
        scrubberColor = choice(player, "scrubberColor", "SPECTRUM",
                "SPECTRUM", "ACCENT", "WHITE", "COVER");
        scrubberThumb = choice(player, "scrubberThumb", "AUTO",
                "AUTO", "NONE", "DOT", "LINE", "RING");
        scrubberThickness = clamp((float) player.optDouble("scrubberThickness", 1.0d), 0.6f, 2.0f);
        titleAlign = choice(player, "playerTitleAlign", "START", "START", "CENTER");
        titleSize = choice(player, "playerTitleSize", "MEDIUM", "SMALL", "MEDIUM", "LARGE");
        JSONObject type = player.optJSONObject("type");
        titleWeight = choice(type == null ? player : type, "playerTitleWeight", "LIGHT",
                "LIGHT", "REGULAR", "BOLD");
        appTypeWeight = choice(type == null ? player : type, "weight", "REGULAR",
                "LIGHT", "REGULAR", "BOLD");
        typeSpacingEm = clamp((float) player.optDouble("typeSpacingEm", 0.0d), -0.04f, 0.08f);
        titleScroll = player.optBoolean("playerTitleScroll", true);
        artCornersDp = Math.max(0, Math.min(32, player.optInt("playerArtCorners", 20)));
        coverSize = choice(player, "coverSize", "STANDARD", "STANDARD", "LARGE", "FULL");
        JSONObject shapes = player.optJSONObject("shapes");
        coverShape = choice(shapes == null ? player : shapes, "player",
                player.optString("coverShape", "ROUNDED"),
                "ROUNDED", "SQUARE", "CIRCLE", "SQUIRCLE", "FLOWER", "COOKIE", "ARCH", "SLANTED");
        JSONObject view = player.optJSONObject("view");
        JSONObject effectiveView = view == null ? player : view;
        layout = choice(player, "playerLayout", "STANDARD",
                "STANDARD", "COVER", "LYRICS", "MINIMAL", "VINYL", "CASSETTE");
        background = choice(player, "playerBackground", "MESH",
                "MESH", "BLUR", "APPEARANCE", "THEME", "BLACK", "WHITE", "GRADIENT");
        backdropShow = clamp((float) player.optDouble("playerBackdropShow", 0.35d), 0.0f, 1.0f);
        transportStyle = choice(player, "transportStyle", "BUTTON", "GLYPHS", "BUTTON", "OUTLINE");
        buttonsPill = effectiveView.optBoolean("buttonsPill", true);
        JSONObject shapesObject = player.optJSONObject("shapes");
        buttonShape = choice(shapesObject == null ? player : shapesObject, "button", "PILL",
                "PILL", "ROUNDED", "SQUARE");
        deckParts = strings(player.optJSONArray("playerDeck"),
                "STRIP", "SCRUBBER", "TRANSPORT", "VOLUME", "BUTTONS");
        playerButtons = strings(player.optJSONArray("playerButtons"),
                "LYRICS", "VIDEO", "QUEUE");
        hiddenParts = strings(player.optJSONArray("hiddenParts"));
        lyricsOnPlayer = player.optBoolean("lyricsOnPlayer", true);
        volumeMatchesProgress = player.optBoolean("volumeMatchesProgress", false);
        hideVolumeBar = player.optBoolean("hideVolumeBar", true);
        volumeControl = choice(player, "volumeControl", "PHONE", "PHONE", "PRISM");
        fullBleedArtwork = player.optBoolean("fullBleedArtwork", false);
        canvasEnabled = player.optBoolean("canvasEnabled", true);
        canvasFillsBackground = player.optBoolean("canvasFillsBackground", false);
        canvasOnCellular = player.optBoolean("canvasOnCellular", false);
        artShrinkOnPause = player.optBoolean("artShrinkOnPause", true);
        vinylTonearm = player.optBoolean("vinylTonearm", true);
        playerBlur = clamp((float) player.optDouble("playerBlur", 1.0d), 0.5f, 1.5f);
        meshSpeed = clamp((float) player.optDouble("meshSpeed", 0.0d), 0.0f, 1.0f);
        meshIntensity = clamp((float) player.optDouble("meshIntensity", 1.0d), 0.4f, 1.6f);
        backdropDim = clamp((float) player.optDouble("backdropDim", 0.35d), 0.0f, 1.0f);
        gradientDepth = clamp((float) effectiveView.optDouble("gradientDepth", 0.5d), 0.0f, 1.0f);
        blendCovers = effectiveView.optBoolean("blendCovers", false);
        showVideoSwitch = player.optBoolean("showVideoSwitch", true);
        hideStatusBarPlayer = player.optBoolean("hideStatusBarPlayer", false);
        playerHandle = player.optBoolean("playerHandle", true);
        playerNavBand = player.optBoolean("playerNavBand", true);
        sessionButtons = strings(player.optJSONArray("sessionButtons"),
                "LIKE", "SHUFFLE", "REPEAT", "CAR_LYRICS", "RADIO", "DISLIKE");
        showPlayingFrom = player.optBoolean("showPlayingFrom", true);
        showOutputDevice = player.optBoolean("showOutputDevice", true);
        showQuality = player.optBoolean("showQuality", false);
        Object mark = effectiveView.opt("crossfadeMark");
        crossfadeMark = mark instanceof Boolean ? (Boolean) mark
                : mark != null && !"OFF".equalsIgnoreCase(String.valueOf(mark))
                && !"NONE".equalsIgnoreCase(String.valueOf(mark));
        crossfadeShape = effectiveView.optBoolean("crossfadeShape", false);
        visualizer = choice(player, "visualizer", "OFF",
                "OFF", "BARS", "WAVE", "RING", "PULSE", "GLOW");
        visualizerColor = choice(player, "visualizerColor", "SPECTRUM",
                "SPECTRUM", "ACCENT", "WHITE", "COVER");
        appVolume = clamp((float) player.optDouble("appVolume", 1.0d), 0.0f, 1.0f);
    }

    static PrismPlayerPreferences fromLyricsLook(String raw) {
        if (raw == null || raw.trim().isEmpty()) return defaults();
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject player = root.optJSONObject("playerPreferences");
            // Older bridge snapshots flattened player fields into the root.
            return new PrismPlayerPreferences(player == null ? root : player);
        } catch (Exception ignored) {
            return defaults();
        }
    }

    static PrismPlayerPreferences defaults() {
        return new PrismPlayerPreferences(new JSONObject());
    }

    boolean showElapsedTime() {
        return TIME_BOTH.equals(playerTimes) || TIME_ELAPSED.equals(playerTimes);
    }

    boolean showRightTime() {
        return TIME_BOTH.equals(playerTimes) || TIME_LEFT.equals(playerTimes);
    }

    boolean showRemainingTime() {
        return showRightTime() && !showTrackLength;
    }

    boolean hasDeckPart(String part) {
        return deckParts.contains(part) && !hiddenParts.contains(part);
    }

    /** Mirrors Prism 1.8.2.1 AppSettings.shows(PlayerPart), including independent switches. */
    boolean showsPart(String part) {
        switch (part) {
            case "HANDLE": return playerHandle;
            case "FROM_ALBUM": return showPlayingFrom && !hiddenParts.contains(part);
            case "LYRIC_LINE": return lyricsOnPlayer && !hiddenParts.contains(part);
            case "TIMES": return !TIME_NONE.equals(playerTimes);
            case "QUALITY": return showQuality;
            case "BEST_PART": return source.optBoolean("bestPartButton", true);
            case "SHUFFLE_REPEAT": return source.optBoolean("playerShuffleRepeat", true);
            case "VOLUME": return showVolumeBar();
            case "OUTPUT": return showOutputDevice;
            case "NAV_BAND": return playerNavBand;
            case "CROSSFADE_MARK": return crossfadeMark;
            case "BLEND_COVERS": return blendCovers;
            default: return !hiddenParts.contains(part);
        }
    }

    boolean showsTimes() {
        return showsPart("TIMES");
    }

    /**
     * Returns Prism's visible player rows in the exact user-selected order.
     * VOLUME has the additional Prism/phone visibility rule; unknown future rows
     * are ignored rather than being assigned an invented island layout.
     */
    List<String> orderedDeckParts() {
        ArrayList<String> ordered = new ArrayList<>(deckParts.size());
        for (String part : deckParts) {
            if (hiddenParts.contains(part)) continue;
            switch (part) {
                case "STRIP":
                case "SCRUBBER":
                case "TRANSPORT":
                case "BUTTONS":
                    ordered.add(part);
                    break;
                case "VOLUME":
                    if (showVolumeBar()) ordered.add(part);
                    break;
                default:
                    // Prism may add a deck row in a later release; do not guess its rendering.
                    break;
            }
        }
        return Collections.unmodifiableList(ordered);
    }

    boolean hasPlayerButton(String button) {
        return playerButtons.contains(button)
                && (!"VIDEO".equals(button) || showVideoSwitch);
    }

    boolean hasSessionButton(String button) {
        return sessionButtons.contains(button);
    }

    List<String> visiblePlayerButtons() {
        if (showVideoSwitch) return playerButtons;
        ArrayList<String> visible = new ArrayList<>(playerButtons);
        visible.remove("VIDEO");
        return Collections.unmodifiableList(visible);
    }

    boolean showVolumeBar() {
        // Prism always keeps its own volume control visible when volumeControl is PRISM.
        // The hidden-parts list still takes precedence, just as it does in Prism settings.
        return hasDeckPart("VOLUME") && (!hideVolumeBar || "PRISM".equals(volumeControl));
    }

    float volumeLevel(float phoneVolume) {
        return "PRISM".equals(volumeControl) ? appVolume : clamp(phoneVolume, 0.0f, 1.0f);
    }

    int titleFontWeight() {
        switch (titleWeight) {
            case "REGULAR": return 400;
            case "BOLD": return 700;
            default:
                switch (appTypeWeight) {
                    case "LIGHT": return 300;
                    case "BOLD": return 700;
                    default: return 400;
                }
        }
    }

    int artistFontWeight() {
        if ("BOLD".equals(titleWeight)) return 600;
        return "BOLD".equals(appTypeWeight) ? 600 : 400;
    }

    String effectiveThumb() {
        if (!"AUTO".equals(scrubberThumb)) return scrubberThumb;
        return "THUMB".equals(scrubberStyle) || "RIBBONS".equals(scrubberStyle) ? "DOT" : "NONE";
    }

    float titleScale() {
        switch (titleSize) {
            case "SMALL": return 0.82f;
            case "LARGE": return 1.2f;
            default: return 1.0f;
        }
    }

    private static List<String> strings(JSONArray array, String... fallback) {
        if (array == null) return Collections.unmodifiableList(Arrays.asList(fallback));
        ArrayList<String> values = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty() && !values.contains(value)) values.add(value);
        }
        return Collections.unmodifiableList(values);
    }

    private static String choice(JSONObject source, String key, String fallback, String... allowed) {
        String value = source.optString(key, fallback);
        for (String candidate : allowed) {
            if (candidate.equalsIgnoreCase(value)) return candidate;
        }
        return fallback;
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
